package org.opencrown.app

import android.os.Build
import java.io.File
import org.opencrown.ffi.PreparedBundle
import org.opencrown.ffi.preparePayload

/** The device facts the runtime matches against, separated from Build for tests. */
data class DeviceInfo(
    val model: String,
    val kernelRelease: String,
    val abi: String,
    val sdk: UInt,
    val buildId: String,
) {
    companion object {
        fun fromBuild(): DeviceInfo = DeviceInfo(
            model = Build.MODEL,
            kernelRelease = System.getProperty("os.version") ?: "unknown",
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            sdk = Build.VERSION.SDK_INT.toUInt(),
            buildId = Build.ID,
        )
    }
}

/** Verifies, stages and mints a session for a selected bundle. */
object PayloadPreparer {
    fun prepare(
        bundleDir: File,
        device: DeviceInfo,
        workDir: File,
        revocationsJson: String = "",
    ): PreparedBundle {
        val signature = File(bundleDir, "manifest.sig").readBytes()
        val publicKey = hexToBytes(File(bundleDir, "public_key.hex").readText().trim())
        return preparePayload(
            bundleDir.path,
            workDir.path,
            device.model,
            device.kernelRelease,
            device.abi,
            device.sdk,
            device.buildId,
            signature,
            publicKey,
            revocationsJson,
        )
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
