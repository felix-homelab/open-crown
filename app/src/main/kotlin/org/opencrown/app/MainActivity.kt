package org.opencrown.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencrown.app.consent.ConsentRequest
import org.opencrown.app.consent.ConsentScreen
import org.opencrown.app.consent.ConsentState
import org.opencrown.ffi.DeviceProbe
import org.opencrown.ffi.PayloadStatus

/**
 * M1 entry point: probe the connected device and list every indexed payload
 * with its readiness reason. Nothing on this screen runs a payload, requests
 * root, stages a file, or mints a token.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    AppContent(filesDir) { name ->
                        assets.open(name).bufferedReader().use { it.readText() }
                    }
                }
            }
        }
    }
}

private data class Catalog(
    val supportedDevices: String,
    val payloadIndex: String,
    val pinnedKeys: List<String>,
    val revocations: String,
)

private data class ProbeOutcome(
    val probe: DeviceProbe?,
    val readiness: List<PayloadStatus>,
    val error: String?,
)

@Composable
private fun AppContent(filesDir: File, readAsset: (String) -> String) {
    val scope = rememberCoroutineScope()
    val device = remember { DeviceInfo.fromBuild() }
    val catalog = remember {
        runCatching {
            Catalog(
                supportedDevices = readAsset("supported-devices.json"),
                payloadIndex = readAsset("payload-index.json"),
                pinnedKeys = CatalogKeys.parse(readAsset("catalog-keys.json")),
                revocations = readAsset("revocations.json"),
            )
        }
    }

    val outcome = remember(catalog) {
        catalog.fold(
            onSuccess = { loaded ->
                runCatching {
                    val probe = RustBridge.probe(device, loaded.supportedDevices)
                    val readiness = RustBridge.readiness(
                        indexJson = loaded.payloadIndex,
                        bundlesDir = File(filesDir, "payloads").path,
                        device = device,
                        agentVersion = AGENT_VERSION,
                        pinnedKeysHex = loaded.pinnedKeys,
                        revocationsJson = loaded.revocations,
                    )
                    ProbeOutcome(probe, readiness, null)
                }.getOrElse { error ->
                    ProbeOutcome(null, emptyList(), error.message ?: error.toString())
                }
            },
            onFailure = { error ->
                ProbeOutcome(null, emptyList(), error.message ?: error.toString())
            },
        )
    }

    LaunchedEffect(outcome) {
        outcome.probe?.let { probe ->
            Log.i(
                "OpenCrown",
                "probe model=${probe.model} kernel=${probe.kernelRelease} abi=${probe.abi} " +
                    "sdk=${probe.androidSdk} build=${probe.buildId} status=${probe.supportedStatus} " +
                    "digest=${probe.digest}",
            )
        }
        outcome.readiness.forEach { status ->
            Log.i(
                "OpenCrown",
                "readiness id=${status.id} reason=${status.reason} status=${status.indexStatus} " +
                    "detail=${status.detail}",
            )
        }
        outcome.error?.let { Log.e("OpenCrown", "probe error: $it") }
    }

    var selected by remember { mutableStateOf<InstalledBundle?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Open Crown", style = MaterialTheme.typography.headlineSmall)
        Text(
            "M1 probe — no payload is run on this screen",
            style = MaterialTheme.typography.bodySmall,
        )
        val revision = remember { runCatching { RustBridge.protocolVersion() } }
        Text(
            revision.fold(
                onSuccess = { "Protocol revision $it" },
                onFailure = { "FFI unavailable: " + it.message },
            ),
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        outcome.probe?.let { probe -> ProbeSection(probe) }
        outcome.error?.let { error ->
            Text("Probe failed: $error", color = MaterialTheme.colorScheme.error)
        }

        if (outcome.probe != null) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            PayloadSection(outcome.readiness)
        }

        if (selected == null) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            InstalledBundles(filesDir, outcome.probe?.supportedStatus ?: "unknown") { installed ->
                selected = installed
                message = null
            }
        } else {
            val bundle = selected ?: return@Column
            Text(
                "Back",
                modifier = Modifier.clickable { selected = null },
                color = MaterialTheme.colorScheme.primary,
            )
            val request = ConsentRequest(
                payloadId = bundle.info.id,
                version = bundle.info.version,
                publisher = "local bundle",
                family = bundle.info.family,
                capabilities = bundle.info.capabilities,
                persistence = bundle.info.persistence,
                rebootRisk = "the session ends at reboot",
                deviceStatus = outcome.probe?.supportedStatus ?: "unknown",
            )
            var consent by remember(bundle) { mutableStateOf(ConsentState(request)) }

            ConsentScreen(
                state = consent,
                onStateChange = { consent = it },
                onAccept = {
                    scope.launch {
                        message = runCatching {
                            val key = catalog.getOrThrow().pinnedKeys.first()
                            val prepared = withContext(Dispatchers.IO) {
                                PayloadPreparer.prepare(
                                    bundle.dir,
                                    device,
                                    File(filesDir, "staged"),
                                    key,
                                    catalog.getOrThrow().revocations,
                                )
                            }
                            "Prepared " + prepared.id + " (" + prepared.token.length +
                                " char token, boot " + prepared.bootId.substring(0, 8) + ")"
                        }.getOrElse { "Prepare failed: " + it.message }
                    }
                },
            )
            message?.let { Text(it) }
        }
    }
}

@Composable
private fun ProbeSection(probe: DeviceProbe) {
    val statusColor = when (probe.supportedStatus) {
        "supported" -> Color(0xFF2E7D32)
        "unknown" -> MaterialTheme.colorScheme.error
        else -> Color(0xFFB26A00)
    }
    Text("Device probe", style = MaterialTheme.typography.titleMedium)
    ProbeRow("Model", probe.model)
    ProbeRow("Kernel release", probe.kernelRelease)
    ProbeRow("ABI", probe.abi)
    ProbeRow("SDK", probe.androidSdk.toString())
    ProbeRow("Build id", probe.buildId)
    ProbeRow("Fingerprint", probe.buildFingerprint)
    ProbeRow("Device status", probe.supportedStatus, statusColor)
    ProbeRow("Fingerprint digest", probe.digest)
    Text(
        when (probe.supportedStatus) {
            "example" -> "Recognised as an example device. No payload exists for this build."
            "supported" -> "Recognised on the supported list. Payload readiness is below."
            "retired" -> "This build is retired; no payload should be offered."
            else -> "Unknown device. Nothing is offered for this build."
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PayloadSection(statuses: List<PayloadStatus>) {
    Text("Payload index", style = MaterialTheme.typography.titleMedium)
    val ready = statuses.count { it.reason == "ready" }
    Text(
        "$ready ready / ${statuses.size} not ready — READY requires a matching device, " +
            "a bundle, a pinned signature, valid artifacts, no revocation and an agent version.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (statuses.isEmpty()) {
        Text("No payloads indexed.")
    }
    statuses.forEach { status ->
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                status.reason.uppercase(),
                color = if (status.reason == "ready") Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "  " + statusLabel(status.reason),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(status.id, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(
            "family=" + status.family + " · index=" + status.indexStatus +
                " · bundle=" + (status.bundleVersion ?: "none"),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(status.detail, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InstalledBundles(
    filesDir: File,
    deviceStatus: String,
    onSelect: (InstalledBundle) -> Unit,
) {
    Text("Installed bundles", style = MaterialTheme.typography.titleMedium)
    val bundles = remember(filesDir) {
        runCatching { BundleScanner(File(filesDir, "payloads")).scan() }
            .getOrDefault(emptyList())
    }
    if (bundles.isEmpty()) {
        Text("No bundles in payloads/. The consent gate is shown for a local bundle only.")
    } else {
        bundles.forEach { installed ->
            Text(
                installed.info.id + " " + installed.info.version +
                    " — " + installed.info.family + " (device: " + deviceStatus + ")",
                modifier = Modifier.clickable { onSelect(installed) },
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ProbeRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label + ": ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}

private fun statusLabel(reason: String): String = when (reason) {
    "ready" -> "ready"
    "no_match" -> "not ready — no exact device target"
    "not_available" -> "not ready — index status is pending or retired"
    "no_bundle" -> "not ready — no bundle"
    "invalid_manifest" -> "not ready — invalid manifest"
    "bad_signature" -> "not ready — signature does not verify"
    "artifact_mismatch" -> "not ready — artifact size/hash mismatch"
    "revoked" -> "not ready — revoked"
    "agent_too_old" -> "not ready — agent too old"
    else -> "not ready — " + reason
}

private const val AGENT_VERSION = "0.1.0"
