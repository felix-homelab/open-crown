package org.opencrown.app

import org.opencrown.ffi.BundleInfo
import org.opencrown.ffi.DeviceProbe
import org.opencrown.ffi.PayloadStatus
import org.opencrown.ffi.SupportedDevice
import org.opencrown.ffi.deviceStatus as ffiDeviceStatus
import org.opencrown.ffi.evaluateReadiness as ffiEvaluateReadiness
import org.opencrown.ffi.inspectBundle as ffiInspectBundle
import org.opencrown.ffi.parseSupportedDevices as ffiParseSupportedDevices
import org.opencrown.ffi.probeDevice as ffiProbeDevice
import org.opencrown.ffi.protocolVersion as ffiProtocolVersion

/**
 * The app's single entry point into the Rust core.
 *
 * UI code never imports generated bindings directly, so the boundary stays one
 * file and failures are values, not crashes.
 */
object RustBridge {
    fun protocolVersion(): UInt = ffiProtocolVersion()

    fun supportedDevices(json: String): List<SupportedDevice> = ffiParseSupportedDevices(json)

    fun inspectBundle(dir: String): BundleInfo = ffiInspectBundle(dir)

    fun deviceStatus(
        json: String,
        model: String,
        kernelRelease: String,
        abi: String,
        androidSdk: UInt,
    ): String = ffiDeviceStatus(json, model, kernelRelease, abi, androidSdk)

    fun probe(device: DeviceInfo, supportedDevicesJson: String): DeviceProbe = ffiProbeDevice(
        device.model,
        device.kernelRelease,
        device.abi,
        device.sdk,
        device.buildId,
        device.fingerprint,
        supportedDevicesJson,
    )

    @Suppress("LongParameterList")
    fun readiness(
        indexJson: String,
        bundlesDir: String,
        device: DeviceInfo,
        agentVersion: String,
        pinnedKeysHex: List<String>,
        revocationsJson: String,
    ): List<PayloadStatus> = ffiEvaluateReadiness(
        indexJson,
        bundlesDir,
        device.model,
        device.kernelRelease,
        device.abi,
        device.sdk,
        device.buildId,
        agentVersion,
        pinnedKeysHex,
        revocationsJson,
    )
}
