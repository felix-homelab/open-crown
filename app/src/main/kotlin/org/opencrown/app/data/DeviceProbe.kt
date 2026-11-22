package org.opencrown.app.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import org.opencrown.app.BuildConfig
import android.provider.Settings
import android.system.ErrnoException
import android.system.Os
import androidx.core.content.ContextCompat
import org.opencrown.app.model.AvailabilityReason
import org.opencrown.app.model.BluetoothSnapshot
import org.opencrown.app.model.BrokerSnapshot
import org.opencrown.app.model.DataOrigin
import org.opencrown.app.model.Observation
import org.opencrown.app.model.PhoneFacts
import org.opencrown.app.model.Verification
import org.opencrown.app.payload.Compatibility
import org.opencrown.app.payload.PayloadManifest
import java.io.IOException

/** Reads the app's own SELinux context. It is a diagnostic, not a capability claim. */
object AppSecurityProbe {
    fun selinuxContext(): String = runCatching {
        java.io.File("/proc/self/attr/current").readText().trim().trim('\u0000')
    }.getOrDefault("unreadable")
}

/** Reads the facts that decide whether the privileged path can even be offered. */
object PhoneFactsReader {
    fun read(context: Context): PhoneFacts {
        val bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrDefault(-1)
        return PhoneFacts(
            model = Build.MODEL,
            device = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE,
            androidSdk = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            kernel = System.getProperty("os.version") ?: "unknown",
            bootCount = bootCount,
        )
    }
}

/**
 * Tests the only thing the app can honestly test about the relay: whether a
 * socket connection from this app's own SELinux domain is accepted.
 *
 * No bytes are written. A successful connect is NOT an authenticated session
 * and is never shown as one.
 */
class AgentLinkProbe(private val socketName: String = BrokerSnapshot.DEFAULT_SOCKET) {
    fun probe(): BrokerSnapshot {
        val now = System.currentTimeMillis()
        val socket = LocalSocket()
        return try {
            socket.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            BrokerSnapshot(
                availability = Observation.present(
                    value = "Socket accepted a connection from this app",
                    origin = DataOrigin.ANDROID_PLATFORM,
                    verification = Verification.REPORTED,
                    note = "Reachability only. No handshake, token or command was exchanged.",
                    nowMillis = now,
                ),
                socketName = socketName,
                lastAttemptMillis = now,
            )
        } catch (e: IOException) {
            val errno = generateSequence(e as Throwable?) { it.cause }
                .filterIsInstance<ErrnoException>()
                .firstOrNull()
            val message = e.message.orEmpty()
            val denied = errno?.errno == android.system.OsConstants.EACCES ||
                errno?.errno == android.system.OsConstants.EPERM ||
                message.contains("permission denied", ignoreCase = true) ||
                message.contains("operation not permitted", ignoreCase = true)
            val refused = errno?.errno == android.system.OsConstants.ECONNREFUSED ||
                errno?.errno == android.system.OsConstants.ENOENT ||
                message.contains("connection refused", ignoreCase = true) ||
                message.contains("no such file", ignoreCase = true)
            val detail = when {
                denied -> "SELinux or file permissions denied the connection (EACCES/EPERM)"
                refused -> "No process is listening on the agent socket (ECONNREFUSED)"
                else -> "Connection failed: " + e.javaClass.simpleName +
                    (if (message.isNotBlank()) " (" + message.take(80) + ")" else "")
            }
            BrokerSnapshot(
                availability = Observation.absent(
                    reason = if (refused) {
                        AvailabilityReason.AGENT_UNAVAILABLE
                    } else {
                        AvailabilityReason.BROKER_UNAVAILABLE
                    },
                    detail = detail,
                    origin = DataOrigin.ANDROID_PLATFORM,
                    nowMillis = now,
                ),
                socketName = socketName,
                lastAttemptMillis = now,
            )
        } catch (e: RuntimeException) {
            BrokerSnapshot(
                availability = Observation.absent(
                    reason = AvailabilityReason.ERROR,
                    detail = "Socket probe failed: ${e.javaClass.simpleName}",
                    origin = DataOrigin.ANDROID_PLATFORM,
                    nowMillis = now,
                ),
                socketName = socketName,
                lastAttemptMillis = now,
            )
        } finally {
            runCatching { socket.close() }
        }
    }
}

/** Reads the per-boot relay marker without assuming it means a usable agent. */
object RelayMarkerProbe {
    fun probe(): Observation<String> {
        val now = System.currentTimeMillis()
        return try {
            Os.stat("/dev/.dfi")
            Observation.present(
                value = "Init-domain relay marker is present",
                origin = DataOrigin.ANDROID_PLATFORM,
                verification = Verification.REPORTED,
                note = "Presence proves the chain ran. It does not prove the app can use it.",
                nowMillis = now,
            )
        } catch (e: ErrnoException) {
            when (e.errno) {
                android.system.OsConstants.EACCES, android.system.OsConstants.EPERM ->
                    Observation.present(
                        value = "Relay marker exists but is not readable by this app",
                        origin = DataOrigin.ANDROID_PLATFORM,
                        verification = Verification.REPORTED,
                        note = "Expected for the init-domain marker.",
                        nowMillis = now,
                    )
                android.system.OsConstants.ENOENT ->
                    Observation.absent(
                        reason = AvailabilityReason.PRIVILEGE_UNAVAILABLE,
                        detail = "No relay marker on this boot; the privileged chain has not run.",
                        origin = DataOrigin.ANDROID_PLATFORM,
                        nowMillis = now,
                    )
                else ->
                    Observation.absent(
                        reason = AvailabilityReason.ERROR,
                        detail = "Relay marker probe failed (errno ${e.errno}).",
                        origin = DataOrigin.ANDROID_PLATFORM,
                        nowMillis = now,
                    )
            }
        }
    }
}

/** Payload compatibility, as a value the UI can explain rather than a boolean. */
object PayloadProbe {
    fun probe(context: Context): Observation<String> {
        val now = System.currentTimeMillis()
        if (!BuildConfig.PRIVILEGED_CHAIN) {
            return Observation.absent(
                reason = AvailabilityReason.UNSUPPORTED,
                detail = "The quarantined privileged payload is not included in this build.",
                origin = DataOrigin.LOCAL_STORE,
                nowMillis = now,
            )
        }
        val manifest = runCatching { PayloadManifest.load(context) }.getOrElse {
            return Observation.absent(
                reason = AvailabilityReason.ERROR,
                detail = "The payload manifest could not be read.",
                origin = DataOrigin.LOCAL_STORE,
                nowMillis = now,
            )
        }
        val match = manifest.match(PayloadManifest.currentDevice())
        return when (match.compatibility) {
            Compatibility.VERIFIED -> Observation.present(
                value = "${match.deviceLabel}: ${match.reason}",
                origin = DataOrigin.LOCAL_STORE,
                verification = Verification.CONFIRMED,
                note = "Exact device and kernel row.",
                nowMillis = now,
            )
            Compatibility.COMPATIBLE -> Observation.present(
                value = match.reason,
                origin = DataOrigin.LOCAL_STORE,
                verification = Verification.INFERRED,
                note = "Specification match only. Not a verified target.",
                nowMillis = now,
            )
            Compatibility.UNSUPPORTED -> Observation.absent(
                reason = AvailabilityReason.UNSUPPORTED,
                detail = match.reason,
                origin = DataOrigin.LOCAL_STORE,
                nowMillis = now,
            )
        }
    }
}

object BluetoothProbe {
    fun probe(context: Context): BluetoothSnapshot {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        val now = System.currentTimeMillis()
        val enabled = if (adapter == null) {
            Observation.absent(
                reason = AvailabilityReason.UNSUPPORTED,
                detail = "This phone has no Bluetooth adapter.",
                origin = DataOrigin.ANDROID_PLATFORM,
                nowMillis = now,
            )
        } else {
            try {
                Observation.present(
                    value = adapter.isEnabled,
                    origin = DataOrigin.ANDROID_PLATFORM,
                    verification = Verification.CONFIRMED,
                    nowMillis = now,
                )
            } catch (e: SecurityException) {
                Observation.absent(
                    reason = AvailabilityReason.PERMISSION_DENIED,
                    detail = "Bluetooth state requires the Nearby devices permission.",
                    origin = DataOrigin.ANDROID_PLATFORM,
                    nowMillis = now,
                )
            }
        }
        return BluetoothSnapshot(
            adapterAvailable = adapter != null,
            enabled = enabled,
            scanPermission = granted(context, Manifest.permission.BLUETOOTH_SCAN),
            connectPermission = granted(context, Manifest.permission.BLUETOOTH_CONNECT),
        )
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}