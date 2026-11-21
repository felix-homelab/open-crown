package org.opencrown.app

import org.opencrown.ffi.BundleInfo
import org.opencrown.ffi.SupportedDevice
import org.opencrown.ffi.deviceStatus as ffiDeviceStatus
import org.opencrown.ffi.inspectBundle as ffiInspectBundle
import org.opencrown.ffi.parseSupportedDevices as ffiParseSupportedDevices
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
}
