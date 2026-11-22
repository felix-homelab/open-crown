package org.opencrown.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.opencrown.app.data.AppSecurityProbe
import org.opencrown.app.data.ChainState
import org.opencrown.app.data.CoreStatus
import org.opencrown.app.diagnostics.DiagnosticsExporter
import org.opencrown.app.data.FeatureRegistry
import org.opencrown.app.data.FeatureStatus
import org.opencrown.app.model.AvailabilityReason
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.model.Observation
import org.opencrown.app.model.Verification
import org.opencrown.app.ui.components.CrownButton
import org.opencrown.app.ui.components.CrownButtonStyle
import org.opencrown.app.ui.components.CrownDiagnosticRow
import org.opencrown.app.ui.components.CrownInsetGroup
import org.opencrown.app.ui.components.CrownNote
import org.opencrown.app.ui.components.CrownScaffold
import org.opencrown.app.ui.components.CrownSectionHeader
import org.opencrown.app.ui.components.CrownSheet
import org.opencrown.app.ui.components.CrownTone
import org.opencrown.app.ui.theme.CrownTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    snapshot: CrownSnapshot?,
    registry: FeatureRegistry?,
    registryError: String?,
    appVersion: String,
    refreshing: Boolean,
    chainState: ChainState,
    coreStatus: CoreStatus,
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onPrepareBoot: () -> Unit,
    onBack: () -> Unit,
) {
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current
    var consent by remember { mutableStateOf(false) }
    CrownScaffold(
        title = "Diagnostics",
        subtitle = snapshot?.let { "Sampled at " + time.format(Date(it.generatedAtMillis)) } ?: "No sample yet",
        onBack = onBack,
    ) {
        item(key = "intro") {
            CrownNote(
                title = "Measured, not inferred",
                message = "Each value names its source and confidence. A socket connection is reachability, not an authenticated session. The serial number and pairing keys are never shown.",
                tone = CrownTone.INFO,
            )
        }
        if (snapshot == null) {
            item(key = "empty") {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "The first probe has not completed.",
                    style = CrownTheme.type.footnote,
                    color = CrownTheme.colors.secondaryLabel,
                )
            }
        } else {
            diagnosticsItems(
                snapshot = snapshot,
                registry = registry,
                registryError = registryError,
                appVersion = appVersion,
                refreshing = refreshing,
                chainState = chainState,
                coreStatus = coreStatus,
                onRefresh = onRefresh,
                onRequestPermissions = onRequestPermissions,
                onPrepareBoot = { consent = true },
                onBack = onBack,
                time = time,
                context = context,
            )
        }
    }
    if (consent) {
        CrownSheet(
            title = "Prepare this boot?",
            onDismiss = { consent = false },
            primaryLabel = "Run the payload for this boot",
            onPrimary = {
                consent = false
                onPrepareBoot()
            },
            secondaryLabel = "Cancel",
            onSecondary = { consent = false },
        ) {
            androidx.compose.material3.Text(
                text = "This runs the quarantined page-cache payload from the app. It patches init's logging path for the current boot and can destabilise the phone. The app still cannot connect to the resulting init-domain relay, and the patch disappears at reboot. No watch data, Apple account material or pairing key is touched.",
                style = CrownTheme.type.footnote,
                color = CrownTheme.colors.secondaryLabel,
            )
        }
    }
}

private fun LazyListScope.diagnosticsItems(
    snapshot: CrownSnapshot,
    registry: FeatureRegistry?,
    registryError: String?,
    appVersion: String,
    refreshing: Boolean,
    chainState: ChainState,
    coreStatus: CoreStatus,
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onPrepareBoot: () -> Unit,
    onBack: () -> Unit,
    time: SimpleDateFormat,
    context: Context,
) {
    item(key = "core-header") {
        Spacer(Modifier.height(24.dp))
        CrownSectionHeader("Rust core")
    }
    item(key = "core") {
        CrownInsetGroup(
            footer = "The protocol core is pure Rust behind UniFFI. A missing native library is reported, not replaced with a default.",
        ) {
            CrownDiagnosticRow(
                "UniFFI",
                if (coreStatus.available) "loaded" else "unavailable",
                coreStatus.detail,
                if (coreStatus.available) CrownTone.SUCCESS else CrownTone.CRITICAL,
            )
            CrownDiagnosticRow(
                "Protocol revision",
                coreStatus.protocolVersion?.toString() ?: "unknown",
                "0 means no spec has been verified on hardware",
            )
            val rules = coreStatus.rules
            if (rules == null) {
                CrownDiagnosticRow(
                    "Rules",
                    "unavailable",
                    "The core did not return its limits.",
                    CrownTone.CRITICAL,
                    showDivider = false,
                )
            } else {
                CrownDiagnosticRow(
                    "Discovery rules",
                    rules.discoveryTtlMs.toString() + " ms · " + rules.discoveryMaxCandidates + " max",
                    "TTL and inventory bound from opencrown-core",
                )
                CrownDiagnosticRow(
                    "IDS rules",
                    rules.idsMaxPending.toString() + " pending · backoff " +
                        rules.idsBackoffSecs.joinToString("/") + " s",
                    "Correlation bound and reconnect policy from opencrown-core",
                )
                CrownDiagnosticRow(
                    "Face rules",
                    (rules.faceMaxArchiveBytes / 1024UL / 1024UL).toString() + " MiB · " +
                        (rules.faceReadbackTimeoutMs / 1000UL) + " s read-back",
                    "Package and read-back bounds from opencrown-core",
                )
                CrownDiagnosticRow(
                    "Setup checkpoints",
                    rules.setupCheckpointFirst.toString() + "–" + rules.setupCheckpointLast,
                    "Durable ordinal range from opencrown-core",
                    showDivider = false,
                )
            }
        }
    }
    item(key = "chain-header") {
        Spacer(Modifier.height(24.dp))
        CrownSectionHeader("Per-boot relay")
    }
    item(key = "chain") {
        CrownInsetGroup(
            footer = "The chain is research payload code with an open provenance review. It is not part of the clean-room product.",
        ) {
            CrownDiagnosticRow(
                "Chain state",
                when (chainState) {
                    ChainState.Idle -> "not run"
                    ChainState.Running -> "running"
                    ChainState.Patched -> "patch applied"
                    is ChainState.Failed -> "failed"
                    is ChainState.Unavailable -> "unavailable"
                },
                when (chainState) {
                    is ChainState.Failed -> chainState.detail
                    is ChainState.Unavailable -> chainState.detail
                    ChainState.Patched -> "The relay activates on the next init log. It is still unreachable from untrusted_app."
                    ChainState.Running -> "Writing the page-cache patch."
                    ChainState.Idle -> "No exploit has been run in this process."
                },
                when (chainState) {
                    ChainState.Patched -> CrownTone.WARNING
                    is ChainState.Failed, is ChainState.Unavailable -> CrownTone.CRITICAL
                    ChainState.Running -> CrownTone.INFO
                    ChainState.Idle -> CrownTone.NEUTRAL
                },
            )
            CrownDiagnosticRow(
                "Relay socket",
                snapshot.privilege.broker.socketName,
                "Reachability is probed on every refresh; no bytes are sent.",
            )
        }
        Spacer(Modifier.height(12.dp))
        CrownButton(
            text = when (chainState) {
                ChainState.Running -> "Preparing\u2026"
                ChainState.Patched -> "Re-run for this boot"
                else -> "Prepare this boot\u2026"
            },
            onClick = onPrepareBoot,
            enabled = chainState != ChainState.Running,
            style = CrownButtonStyle.TINTED,
        )
    }
    item(key = "device-header") {
        Spacer(Modifier.height(24.dp))
        CrownSectionHeader("Device")
    }
    item(key = "device") {
        CrownInsetGroup(footer = "Fingerprint and serial are deliberately omitted.") {
            CrownDiagnosticRow("Model", snapshot.phone.model)
            CrownDiagnosticRow("Device", snapshot.phone.device)
            CrownDiagnosticRow("Android", snapshot.phone.androidRelease + " (SDK " + snapshot.phone.androidSdk + ")")
            CrownDiagnosticRow("ABI", snapshot.phone.abi)
            CrownDiagnosticRow("Kernel", snapshot.phone.kernel)
            CrownDiagnosticRow("Boot count", snapshot.phone.bootCount.toString())
            CrownDiagnosticRow("App SELinux context", AppSecurityProbe.selinuxContext())
            CrownDiagnosticRow("App version", appVersion)
        }
    }
    item(key = "bt-header") {
        Spacer(Modifier.height(24.dp))
        CrownSectionHeader("Bluetooth")
    }
    item(key = "bt") {
        CrownInsetGroup {
            CrownDiagnosticRow("Adapter", if (snapshot.bluetooth.adapterAvailable) "present" else "absent")
            CrownDiagnosticRow(
                "Enabled",
                observationValue(snapshot.bluetooth.enabled),
                observationDetail(snapshot.bluetooth.enabled),
            )
            CrownDiagnosticRow(
                "Nearby devices (scan)",
                if (snapshot.bluetooth.scanPermission) "granted" else "not granted",
                tone = if (snapshot.bluetooth.scanPermission) CrownTone.SUCCESS else CrownTone.WARNING,
            )
            CrownDiagnosticRow(
                "Nearby devices (connect)",
                if (snapshot.bluetooth.connectPermission) "granted" else "not granted",
                tone = if (snapshot.bluetooth.connectPermission) CrownTone.SUCCESS else CrownTone.WARNING,
            )
        }
    }
    item(key = "priv-header") {
        Spacer(Modifier.height(24.dp))
        CrownSectionHeader("Per-boot privilege")
    }
    item(key = "priv") {
        CrownInsetGroup(
            footer = "No command is sent by this probe. The relay's own behaviour is documented in docs/evidence/broker-probe-a52.md.",
        ) {
            CrownDiagnosticRow(
                "Payload profile",
                observationValue(snapshot.privilege.payloadCompatible),
                observationDetail(snapshot.privilege.payloadCompatible),
                tone = observationTone(snapshot.privilege.payloadCompatible),
            )
            CrownDiagnosticRow(
                "Relay marker",
                observationValue(snapshot.privilege.relayMarker),
                observationDetail(snapshot.privilege.relayMarker),
                tone = observationTone(snapshot.privilege.relayMarker),
            )
            CrownDiagnosticRow(
                "Agent socket",
                snapshot.privilege.broker.socketName,
                "Probe: connect only, no bytes exchanged",
            )
            CrownDiagnosticRow(
                "Agent reachability",
                observationValue(snapshot.privilege.broker.availability),
                observationDetail(snapshot.privilege.broker.availability),
                tone = observationTone(snapshot.privilege.broker.availability),
            )
            CrownDiagnosticRow(
                "Last agent probe",
                if (snapshot.privilege.broker.lastAttemptMillis == 0L) "never" else time.format(Date(snapshot.privilege.broker.lastAttemptMillis)),
            )
        }
    }
    item(key = "pair-header") {
        Spacer(Modifier.height(24.dp))
        CrownSectionHeader("Pair and link")
    }
    item(key = "pair") {
        CrownInsetGroup {
            CrownDiagnosticRow("Sealed pair records", snapshot.pairRecords.size.toString())
            CrownDiagnosticRow(
                "Active pair",
                if (snapshot.activePair.valueOrNull != null) "present" else "none",
                observationDetail(snapshot.activePair),
            )
            CrownDiagnosticRow(
                "Link",
                observationValue(snapshot.link),
                observationDetail(snapshot.link),
                tone = observationTone(snapshot.link),
            )
        }
    }
    item(key = "registry-header") {
        Spacer(Modifier.height(24.dp))
        CrownSectionHeader("Registry")
    }
    item(key = "registry") {
        CrownInsetGroup {
            if (registryError != null) {
                CrownDiagnosticRow("Registry asset", "unreadable", registryError, CrownTone.CRITICAL)
            } else if (registry == null) {
                CrownDiagnosticRow("Registry asset", "not loaded", tone = CrownTone.WARNING)
            } else {
                CrownDiagnosticRow("Registry asset", "readable", tone = CrownTone.SUCCESS)
                CrownDiagnosticRow("Updated", registry.updated)
                CrownDiagnosticRow("Rows", registry.counts.total.toString())
                CrownDiagnosticRow("Verified", registry.counts[FeatureStatus.VERIFIED].toString())
                CrownDiagnosticRow("Blocked", registry.counts[FeatureStatus.BLOCKED].toString())
            }
        }
    }
    item(key = "actions") {
        Spacer(Modifier.height(22.dp))
        CrownButton(
            text = if (refreshing) "Probing\u2026" else "Probe again",
            onClick = onRefresh,
            enabled = !refreshing,
        )
        Spacer(Modifier.height(8.dp))
        if (!snapshot.bluetooth.scanPermission) {
            CrownButton(
                text = "Grant nearby devices permission",
                onClick = onRequestPermissions,
                style = CrownButtonStyle.TINTED,
            )
            Spacer(Modifier.height(8.dp))
        }
        CrownButton(
            text = "Export diagnostics",
            onClick = {
                val now = System.currentTimeMillis()
                val report = DiagnosticsExporter.buildReport(
                    nowMillis = now,
                    appVersion = appVersion,
                    snapshot = snapshot,
                    core = coreStatus,
                    registry = registry,
                )
                val file = DiagnosticsExporter.write(context, report, now)
                val chooser = Intent.createChooser(
                    DiagnosticsExporter.shareIntent(context, file),
                    "Export diagnostics",
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(chooser) }
            },
            style = CrownButtonStyle.TINTED,
        )
        Spacer(Modifier.height(8.dp))
        CrownButton(
            text = "Close",
            onClick = onBack,
            style = CrownButtonStyle.PLAIN,
        )
    }
}

private fun observationValue(observation: Observation<*>?): String = when (observation) {
    null -> "unknown"
    is Observation.Present -> observation.value?.toString() ?: "present"
    is Observation.Absent -> observation.reason.name.lowercase(Locale.ROOT)
}

private fun observationDetail(observation: Observation<*>?): String? {
    if (observation == null) return null
    val provenance = observation.provenance
    val origin = provenance.origin.name.lowercase(Locale.ROOT).replace('_', ' ')
    val confidence = provenance.verification.name.lowercase(Locale.ROOT)
    val whenText = if (provenance.observedAtMillis == 0L) {
        "never"
    } else {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(provenance.observedAtMillis))
    }
    val base = origin + " \u00b7 " + confidence + " \u00b7 " + whenText
    val detail = when (observation) {
        is Observation.Absent -> observation.detail
        is Observation.Present -> provenance.note
    }
    return if (detail == null) base else base + " \u00b7 " + detail
}

private fun observationTone(observation: Observation<*>?): CrownTone = when {
    observation == null -> CrownTone.NEUTRAL
    observation is Observation.Absent -> when (observation.reason) {
        AvailabilityReason.NOT_PAIRED,
        AvailabilityReason.NEVER_OBSERVED,
        AvailabilityReason.TRANSPORT_DOWN,
        AvailabilityReason.AGENT_UNAVAILABLE,
        AvailabilityReason.BLUETOOTH_OFF,
        -> CrownTone.WARNING
        else -> CrownTone.CRITICAL
    }
    observation.provenance.verification == Verification.CONFIRMED -> CrownTone.SUCCESS
    else -> CrownTone.INFO
}