package org.opencrown.app

import java.io.File
import org.opencrown.ffi.BundleInfo

/** A bundle directory paired with the manifest summary Rust parsed from it. */
data class InstalledBundle(val dir: File, val info: BundleInfo)

/**
 * Lists bundle directories under the app's private payloads directory.
 *
 * Directories without a manifest, or with a manifest that fails validation, are
 * skipped: a broken bundle is not offered to the user.
 */
class BundleScanner(private val root: File) {
    fun scan(): List<InstalledBundle> {
        val directories = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return directories
            .mapNotNull { dir ->
                if (!File(dir, "manifest.json").isFile) {
                    null
                } else {
                    runCatching {
                        InstalledBundle(dir, RustBridge.inspectBundle(dir.path))
                    }.getOrNull()
                }
            }
            .sortedBy { it.info.id }
    }
}
