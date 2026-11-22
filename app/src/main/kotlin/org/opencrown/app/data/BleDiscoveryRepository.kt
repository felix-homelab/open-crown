package org.opencrown.app.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencrown.app.model.AvailabilityReason
import org.opencrown.app.model.DataOrigin
import org.opencrown.app.model.Observation
import org.opencrown.app.model.Verification

/**
 * An over-the-air observation. It is deliberately not a watch, a connection, or
 * a pairing candidate: it is an advertisement that has been seen.
 */
data class NearbyAdvertisement(
    /** Internal routing identifier. Never shown in the UI and never logged. */
    val transportAddress: String,
    val displayName: String?,
    val rssi: Int,
    val appleManufacturerType: Int?,
    val payloadHex: String,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val seenCount: Int,
) {
    val observation: Observation<NearbyAdvertisement>
        get() = Observation.present(
            value = this,
            origin = DataOrigin.ADVERTISEMENT,
            verification = Verification.REPORTED,
            note = "Advertisement only. No connection exists.",
            nowMillis = lastSeenMillis,
        )
}

sealed interface DiscoveryState {
    data object Idle : DiscoveryState
    data object PermissionRequired : DiscoveryState
    data object BluetoothOff : DiscoveryState
    data object Unsupported : DiscoveryState
    data class Scanning(val advertisements: List<NearbyAdvertisement>) : DiscoveryState
    data class Unavailable(val reason: AvailabilityReason, val detail: String) : DiscoveryState
}

/**
 * Unprivileged BLE observation.
 *
 * This is real Android scanning: it proves what is on the air, and nothing
 * more. The Apple manufacturer payload is retained as bytes so a later Lane A
 * spec, not a guess, can decide what a type means.
 */
class BleDiscoveryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private val seen = LinkedHashMap<String, NearbyAdvertisement>()
    private var scanning = false
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            record(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::record)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _state.value = DiscoveryState.Unavailable(
                reason = AvailabilityReason.ERROR,
                detail = "The platform rejected the scan (code " + errorCode + ").",
            )
        }
    }

    fun start(): Boolean {
        if (scanning) return true
        if (!hasScanPermission()) {
            _state.value = DiscoveryState.PermissionRequired
            return false
        }
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            _state.value = DiscoveryState.Unsupported
            return false
        }
        if (!adapter.isEnabled) {
            _state.value = DiscoveryState.BluetoothOff
            return false
        }
        val leScanner = try {
            adapter.bluetoothLeScanner
        } catch (e: SecurityException) {
            null
        }
        if (leScanner == null) {
            _state.value = DiscoveryState.Unavailable(
                reason = AvailabilityReason.UNSUPPORTED,
                detail = "Bluetooth LE scanning is not available to this app.",
            )
            return false
        }
        scanner = leScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()
        // No name filter: a watch in pairing mode may not include a local name.
        // Apple's manufacturer id narrows the radio traffic; the interesting
        // payload semantics are decided later from evidence.
        val filters = listOf(
            ScanFilter.Builder()
                .setManufacturerData(APPLE_COMPANY_ID, byteArrayOf(), byteArrayOf())
                .build()
        )
        return try {
            leScanner.startScan(filters, settings, callback)
            scanning = true
            _state.value = DiscoveryState.Scanning(seen.values.toList())
            true
        } catch (e: SecurityException) {
            _state.value = DiscoveryState.PermissionRequired
            false
        } catch (e: IllegalStateException) {
            _state.value = DiscoveryState.Unavailable(
                reason = AvailabilityReason.ERROR,
                detail = "The scanner could not start: " + e.javaClass.simpleName,
            )
            false
        }
    }

    fun stop() {
        if (!scanning) {
            if (_state.value is DiscoveryState.Scanning) {
                _state.value = DiscoveryState.Idle
            }
            return
        }
        runCatching { scanner?.stopScan(callback) }
        scanning = false
        _state.value = if (seen.isEmpty()) DiscoveryState.Idle else DiscoveryState.Scanning(seen.values.toList())
    }

    fun clear() {
        seen.clear()
        if (_state.value is DiscoveryState.Scanning) {
            _state.value = DiscoveryState.Scanning(emptyList())
        }
    }

    private fun hasScanPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED

    private fun record(result: ScanResult) {
        val record = result.scanRecord
        val manufacturer = record?.getManufacturerSpecificData(APPLE_COMPANY_ID)
        if (manufacturer == null) return
        val type = if (manufacturer.isNotEmpty()) manufacturer[0].toInt() and 0xFF else null
        val payload = manufacturer.drop(1).take(48)
        val address = runCatching { result.device.address }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val existing = seen[address]
        val name = record.deviceName?.takeIf { it.isNotBlank() }
        seen[address] = NearbyAdvertisement(
            transportAddress = address,
            displayName = existing?.displayName ?: name,
            rssi = result.rssi,
            appleManufacturerType = type,
            payloadHex = payload.joinToString("") { "%02x".format(it) },
            firstSeenMillis = existing?.firstSeenMillis ?: now,
            lastSeenMillis = now,
            seenCount = (existing?.seenCount ?: 0) + 1,
        )
        // Keep the newest first so the list does not shuffle on every packet.
        if (seen.size > MAX_ENTRIES) {
            val oldest = seen.entries.minByOrNull { it.value.lastSeenMillis }?.key
            if (oldest != null) seen.remove(oldest)
        }
        if (_state.value is DiscoveryState.Scanning) {
            _state.value = DiscoveryState.Scanning(seen.values.sortedByDescending { it.rssi })
        }
    }

    companion object {
        /** Bluetooth SIG company identifier for Apple, Inc. Public constant. */
        const val APPLE_COMPANY_ID = 0x004C
        private const val MAX_ENTRIES = 64
    }
}
