package org.opencrown.app.data

import org.opencrown.ffi.CoreRules

/**
 * The Kotlin side of the Rust seam.
 *
 * The UniFFI binding is generated from ffi/src/lib.rs and compiled into the app.
 * A missing or incompatible native library is reported as an unavailable state,
 * never hidden behind a default protocol version.
 */
data class CoreStatus(
    val available: Boolean,
    val protocolVersion: Int?,
    val rules: CoreRules?,
    val detail: String,
)

object RustCore {
    fun probe(): CoreStatus = try {
        val version = org.opencrown.ffi.protocolVersion().toInt()
        val rules = org.opencrown.ffi.coreRules()
        CoreStatus(
            available = true,
            protocolVersion = version,
            rules = rules,
            detail = "UniFFI loaded; protocol revision " + version,
        )
    } catch (error: Throwable) {
        CoreStatus(
            available = false,
            protocolVersion = null,
            rules = null,
            detail = "UniFFI unavailable: " + error.javaClass.simpleName +
                (error.message?.take(80)?.let { ": " + it } ?: ""),
        )
    }
}
