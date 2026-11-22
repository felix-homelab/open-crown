package org.opencrown.app.diagnostics

import android.content.Context
import android.net.Uri
import org.opencrown.app.data.CoreStatus
import org.opencrown.app.data.FeatureRegistry
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.model.Observation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a redacted, user-facing diagnostics report.
 *
 * Deliberately excluded: serial numbers, device identifiers, pairing keys, Apple
 * account material, notification content and health samples. The report is
 * written only to the app cache and shared only when the user chooses a target.
 */
object DiagnosticsExporter {
    const val REDACTION_NOTE =
        "Redacted: serial number, device identifiers, pairing keys, Apple account material, notification content and health data."

    fun buildReport(
        nowMillis: Long,
        appVersion: String,
        snapshot: CrownSnapshot?,
        core: CoreStatus,
        registry: FeatureRegistry?,
    ): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(nowMillis))
        val builder = StringBuilder()
        builder.appendLine("Open Crown diagnostics")
        builder.appendLine("generated: $time")
        builder.appendLine("app: $appVersion")
        builder.appendLine(REDACTION_NOTE)
        builder.appendLine()
        if (snapshot == null) {
            builder.appendLine("device: no probe sample")
        } else {
            builder.appendLine("[device]")
            builder.appendLine("model: " + snapshot.phone.model)
            builder.appendLine("device: " + snapshot.phone.device)
            builder.appendLine("android: " + snapshot.phone.androidRelease + " (SDK " + snapshot.phone.androidSdk + ")")
            builder.appendLine("abi: " + snapshot.phone.abi)
            builder.appendLine("kernel: " + snapshot.phone.kernel)
            builder.appendLine("boot-count: " + snapshot.phone.bootCount)
            builder.appendLine("selinux: " + org.opencrown.app.data.AppSecurityProbe.selinuxContext())
            builder.appendLine()
            builder.appendLine("[bluetooth]")
            builder.appendLine("adapter: " + if (snapshot.bluetooth.adapterAvailable) "present" else "absent")
            builder.appendLine("enabled: " + observation(snapshot.bluetooth.enabled))
            builder.appendLine("scan-permission: " + snapshot.bluetooth.scanPermission)
            builder.appendLine("connect-permission: " + snapshot.bluetooth.connectPermission)
            builder.appendLine()
            builder.appendLine("[privilege]")
            builder.appendLine("payload: " + observation(snapshot.privilege.payloadCompatible))
            builder.appendLine("relay-marker: " + observation(snapshot.privilege.relayMarker))
            builder.appendLine("agent-socket: " + snapshot.privilege.broker.socketName)
            builder.appendLine("agent: " + observation(snapshot.privilege.broker.availability))
            builder.appendLine()
            builder.appendLine("[pair-and-link]")
            builder.appendLine("pair-records: " + snapshot.pairRecords.size)
            builder.appendLine("active-pair: " + observation(snapshot.activePair))
            builder.appendLine("link: " + observation(snapshot.link))
        }
        builder.appendLine()
        builder.appendLine("[rust-core]")
        builder.appendLine("available: " + core.available)
        builder.appendLine("protocol-revision: " + (core.protocolVersion?.toString() ?: "unknown"))
        builder.appendLine("detail: " + core.detail)
        core.rules?.let { rules ->
            builder.appendLine("discovery-ttl-ms: " + rules.discoveryTtlMs)
            builder.appendLine("discovery-max-candidates: " + rules.discoveryMaxCandidates)
            builder.appendLine("ids-max-pending: " + rules.idsMaxPending)
            builder.appendLine("ids-backoff-secs: " + rules.idsBackoffSecs.joinToString("/"))
            builder.appendLine("face-readback-timeout-ms: " + rules.faceReadbackTimeoutMs)
            builder.appendLine("face-max-archive-bytes: " + rules.faceMaxArchiveBytes)
            builder.appendLine("setup-checkpoints: " + rules.setupCheckpointFirst + "-" + rules.setupCheckpointLast)
        }
        builder.appendLine()
        builder.appendLine("[registry]")
        if (registry == null) {
            builder.appendLine("unavailable")
        } else {
            builder.appendLine("updated: " + registry.updated)
            builder.appendLine("total: " + registry.counts.total)
            registry.counts.byStatus.toSortedMap(compareBy { it.name }).forEach { (status, count) ->
                builder.appendLine(status.wire + ": " + count)
            }
        }
        return builder.toString()
    }

    fun write(context: Context, report: String, nowMillis: Long): File {
        val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val name = "opencrown-diagnostics-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMillis)) + ".txt"
        val file = File(directory, name)
        file.writeText(report)
        return file
    }

    fun shareIntent(context: Context, file: File): android.content.Intent {
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun observation(observation: Observation<*>?): String {
        if (observation == null) return "unknown"
        val value = when (observation) {
            is Observation.Present -> observation.value?.toString() ?: "present"
            is Observation.Absent -> observation.reason.name.lowercase(Locale.ROOT) + " · " + observation.detail
        }
        val provenance = observation.provenance
        return value + " [" + provenance.origin.name.lowercase(Locale.ROOT) + "/" +
            provenance.verification.name.lowercase(Locale.ROOT) + "]"
    }
}
