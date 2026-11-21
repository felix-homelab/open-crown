package org.opencrown.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencrown.app.consent.ConsentRequest
import org.opencrown.app.consent.ConsentScreen
import org.opencrown.app.consent.ConsentState

/**
 * The skeleton entry point: protocol revision, device support, installed
 * bundles, and the consent gate for a selected bundle.
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

@Composable
private fun AppContent(filesDir: File, readAsset: (String) -> String) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<InstalledBundle?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Open Crown", style = MaterialTheme.typography.headlineSmall)

        val revision = remember { runCatching { RustBridge.protocolVersion() } }
        Text(
            revision.fold(
                onSuccess = { "Protocol revision $it" },
                onFailure = { "FFI unavailable: " + it.message },
            )
        )

        val json = remember { runCatching { readAsset("supported-devices.json") }.getOrNull() }
        if (json == null) {
            Text("Device list unavailable")
            return@Column
        }

        val status = remember(json) {
            runCatching {
                RustBridge.deviceStatus(
                    json,
                    DeviceInfo.fromBuild().model,
                    DeviceInfo.fromBuild().kernelRelease,
                    DeviceInfo.fromBuild().abi,
                    DeviceInfo.fromBuild().sdk,
                )
            }.getOrDefault("unknown")
        }
        Text("This device: $status")

        val bundle = selected
        if (bundle == null) {
            Text("Supported devices", style = MaterialTheme.typography.titleMedium)
            val devices = remember(json) {
                runCatching { RustBridge.supportedDevices(json) }.getOrDefault(emptyList())
            }
            devices.forEach { device ->
                Text(device.model + " / " + device.kernelBuild + " — " + device.status)
            }

            Text("Payload bundles", style = MaterialTheme.typography.titleMedium)
            val bundles = remember {
                runCatching { BundleScanner(File(filesDir, "payloads")).scan() }
                    .getOrDefault(emptyList())
            }
            if (bundles.isEmpty()) {
                Text("No bundles in payloads/")
            } else {
                bundles.forEach { installed ->
                    Text(
                        installed.info.id + " " + installed.info.version +
                            " — " + installed.info.family,
                        modifier = Modifier.clickable {
                            selected = installed
                            message = null
                        },
                    )
                }
            }
        } else {
            Text(
                "Back",
                modifier = Modifier.clickable { selected = null },
            )
            val request = ConsentRequest(
                payloadId = bundle.info.id,
                version = bundle.info.version,
                publisher = "local bundle",
                family = bundle.info.family,
                capabilities = bundle.info.capabilities,
                persistence = bundle.info.persistence,
                rebootRisk = "the session ends at reboot",
                deviceStatus = status,
            )
            var consent by remember(bundle) { mutableStateOf(ConsentState(request)) }

            ConsentScreen(
                state = consent,
                onStateChange = { consent = it },
                onAccept = { _ ->
                    scope.launch {
                        message = runCatching {
                            val prepared = withContext(Dispatchers.IO) {
                                PayloadPreparer.prepare(
                                    bundle.dir,
                                    DeviceInfo.fromBuild(),
                                    File(filesDir, "staged"),
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
