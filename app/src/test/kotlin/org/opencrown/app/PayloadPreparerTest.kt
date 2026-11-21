package org.opencrown.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class PayloadPreparerTest {
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
    fun prepares_a_copied_bundle() {
        val repo = File(System.getProperty("user.dir") ?: ".").resolve("..").canonicalFile
        val root = Files.createTempDirectory("prepare").toFile()
        try {
            val bundle = File(root, "bundle")
            File(repo, "catalog/examples/signed").copyRecursively(bundle, overwrite = true)
            val work = File(root, "work")

            val prepared = PayloadPreparer.prepare(
                bundle,
                DeviceInfo("EXAMPLE-1", "example-6.1.0", "arm64-v8a", 36u, "EXAMPLE"),
                work,
            )

            assertEquals("org.opencrown.payload.example", prepared.id)
            assertEquals(64, prepared.token.length)
            assertEquals(32, prepared.bootId.length)
            assertEquals(true, File(prepared.entryPath).exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
