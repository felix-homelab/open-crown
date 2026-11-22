package org.opencrown.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencrown.app.model.AvailabilityReason
import org.opencrown.app.model.DataOrigin
import org.opencrown.app.model.Observation
import org.opencrown.app.model.Verification
import org.opencrown.ffi.DiscoveryBridge

/**
 * An over-the-air observation. It is deliberately not a watch, a connection, or
 * a pairing candidate. The token and selectable flag come from the Rust core.
 */
data class NearbyAdvertisement(
    val token: String,
    val displayName: String?,
    val rssi: Int,
    val appleManufacturerType: Int?,
    val payloadHex: String,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val seenCount: Int,
    val selectable: Boolean,
    val productType: String?,
    val osVersion: String?,
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

private data class Display(
    val address: String,
    val type: Int?,
    val payloadHex: String,
    val firstSeenMillis: Long,
    var seenCount: Int,
)

/**
 * Unprivileged BLE observation.
 *
 * Android supplies the radio events and the display-only payload bytes. The
 * Rust [DiscoveryBridge] owns the candidate inventory, TTL, opaque tokens and
 * selection validation, so Kotlin cannot invent a candidate or promote an
 * advertisement into a watch.
 */
class BleDiscoveryRepository(
    context: Context,
    private val bridge: DiscoveryBridge?,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private val displayedByToken = mutableMapOf<String, Display>()
    private val tokenByAddress = mutableMapOf<String, String>()
    private var scanning = false
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            record(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::record)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            ticker?.cancel()
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
        val bridge = bridge ?: run {
            _state.value = DiscoveryState.Unavailable(
                reason = AvailabilityReason.ERROR,
                detail = "The Rust discovery core is unavailable.",
            )
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
        return try {
            bridge.startScan()
            synchronized(this) {
                displayedByToken.clear()
                tokenByAddress.clear()
            }
            scanner = leScanner
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0)
                .build()
            // Apple's company id narrows the radio traffic; FE25 semantics stay
            // with the Rust core's metadata decoder once one exists.
            val filters = listOf(
                ScanFilter.Builder()
                    .setManufacturerData(APPLE_COMPANY_ID, byteArrayOf(), byteArrayOf())
                    .build()
            )
            leScanner.startScan(filters, settings, callback)
            scanning = true
            _state.value = DiscoveryState.Scanning(emptyList())
            ticker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    refresh()
                }
            }
            true
        } catch (e: SecurityException) {
            _state.value = DiscoveryState.PermissionRequired
            false
        } catch (e: Exception) {
            _state.value = DiscoveryState.Unavailable(
                reason = AvailabilityReason.ERROR,
                detail = "The scanner could not start: " + e.javaClass.simpleName,
            )
            false
        }
    }

    @SuppressLint("MissingPermission") // guarded below; stopScan can still throw on revocation
    fun stop() {
        ticker?.cancel()
        ticker = null
        if (hasScanPermission()) {
            runCatching { scanner?.stopScan(callback) }
        }
        scanning = false
        runCatching { bridge?.stopScan() }
        synchronized(this) {
            displayedByToken.clear()
            tokenByAddress.clear()
        }
        _state.value = DiscoveryState.Idle
    }

    fun clear() {
        if (!scanning) return
        runCatching {
            bridge?.stopScan()
            bridge?.startScan()
        }
        synchronized(this) {
            displayedByToken.clear()
            tokenByAddress.clear()
        }
        _state.value = DiscoveryState.Scanning(emptyList())
    }

    private fun hasScanPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED

    @Synchronized
    private fun record(result: ScanResult) {
        val record = result.scanRecord
        val manufacturer = record?.getManufacturerSpecificData(APPLE_COMPANY_ID) ?: return
        val type = if (manufacturer.isNotEmpty()) manufacturer[0].toInt() and 0xFF else null
        val payload = manufacturer.drop(1).take(48)
        val address = runCatching { result.device.address }.getOrNull() ?: return
        val bridge = bridge ?: return
        val now = System.currentTimeMillis()
        val name = record.deviceName?.takeIf { it.isNotBlank() }
        val token = try {
            bridge.observe(
                addressHex = address.replace(":", ""),
                rssi = result.rssi,
                name = name,
                productType = null,
                osVersion = null,
                metadataHex = null,
                nowMs = now.toULong(),
            )
        } catch (e: Exception) {
            return
        }
        val previous = tokenByAddress[address]
        val previousDisplay = previous?.let { displayedByToken[it] }
        if (previous != null && previous != token) {
            displayedByToken.remove(previous)
        }
        displayedByToken[token] = Display(
            address = address,
            type = type,
            payloadHex = payload.joinToString("") { "%02x".format(it) },
            firstSeenMillis = previousDisplay?.firstSeenMillis ?: now,
            seenCount = (previousDisplay?.seenCount ?: 0) + 1,
        )
        tokenByAddress[address] = token
        refreshLocked(now)
    }

    @Synchronized
    private fun refresh() {
        refreshLocked(System.currentTimeMillis())
    }

    private fun refreshLocked(now: Long) {
        if (!scanning) return
        val bridge = bridge ?: return
        val candidates = try {
            bridge.candidates(now.toULong())
        } catch (e: Exception) {
            return
        }
        val advertisements = candidates.mapNotNull { candidate ->
            val display = displayedByToken[candidate.token] ?: return@mapNotNull null
            NearbyAdvertisement(
                token = candidate.token,
                displayName = candidate.name,
                rssi = candidate.rssi,
                appleManufacturerType = display.type,
                payloadHex = display.payloadHex,
                firstSeenMillis = display.firstSeenMillis,
                lastSeenMillis = candidate.lastSeenMs.toLong(),
                seenCount = display.seenCount,
                selectable = candidate.selectable,
                productType = candidate.productType,
                osVersion = candidate.osVersion,
            )
        }.sortedByDescending { it.rssi }
        _state.value = DiscoveryState.Scanning(advertisements)
    }

    companion object {
        /** Bluetooth SIG company identifier for Apple, Inc. Public constant. */
        const val APPLE_COMPANY_ID = 0x004C
    }
}