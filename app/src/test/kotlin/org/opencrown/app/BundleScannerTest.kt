package org.opencrown.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class BundleScannerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun locateLibrary() {
            val workingDir = System.getProperty("user.dir") ?: "."
            val libDir = File(workingDir).resolve("../target/debug").canonicalFile
            System.setProperty("jna.library.path", libDir.path)
        }
    }

    @Test
    fun scans_installed_bundles_and_skips_empty_directories() {
        val repo = File(System.getProperty("user.dir") ?: ".").resolve("..").canonicalFile
        val root = Files.createTempDirectory("bundles").toFile()
        try {
            File(repo, "catalog/examples/signed").copyRecursively(
                File(root, "example"),
                overwrite = true,
            )
            File(root, "empty").mkdirs()

            val bundles = BundleScanner(root).scan()
            assertEquals(1, bundles.size)
            assertEquals("org.opencrown.payload.example", bundles[0].info.id)
            assertEquals("1.0.0", bundles[0].info.version)
        } finally {
            root.deleteRecursively()
        }
    }
}
