package org.opencrown.app

import org.opencrown.ffi.protocolVersion as ffiProtocolVersion

/**
 * The app's single entry point into the Rust core.
 *
 * UI code never imports generated bindings directly, so the boundary stays one
 * file and the failure mode is a value, not a crash.
 */
object RustBridge {
    fun protocolVersion(): UInt = ffiProtocolVersion()
}
