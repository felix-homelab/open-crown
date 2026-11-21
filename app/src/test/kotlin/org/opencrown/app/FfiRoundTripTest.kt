package org.opencrown.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.opencrown.ffi.FfiException
import org.opencrown.ffi.preparePayload
import org.opencrown.ffi.protocolVersion
import org.opencrown.ffi.requireVerifiedProtocol

/**
 * The Phase 1 exit criterion, plus the runtime path the app will call:
 * Kotlin calls Rust, an error crosses back, and a signed bundle is verified and
 * staged through the generated bindings.
 */
class FfiRoundTripTest {
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
    fun protocolVersionCrossesTheBoundary() {
        assertEquals(0u, protocolVersion())
    }

    @Test
    fun errorTypeCrossesTheBoundary() {
        assertThrows(FfiException.NotReady::class.java) {
            requireVerifiedProtocol(false)
        }
    }

    @Test
    fun runtimePreparesTheCommittedBundle() {
        val repo = File(System.getProperty("user.dir") ?: ".").resolve("..").canonicalFile
        val bundle = File(repo, "catalog/examples/signed")
        val work = Files.createTempDirectory("opencrown-ffi").toFile()
        try {
            val prepared = preparePayload(
                bundle.path,
                work.path,
                "EXAMPLE-1",
                "example-6.1.0",
                "arm64-v8a",
                36u,
                "EXAMPLE",
                File(bundle, "manifest.sig").readBytes(),
                hexToBytes(File(bundle, "public_key.hex").readText().trim()),
                "",
            )
            assertEquals("org.opencrown.payload.example", prepared.id)
            assertEquals("1.0.0", prepared.version)
            assertEquals(64, prepared.token.length)
            assertTrue(File(prepared.entryPath).exists())
            assertTrue(File(prepared.agentPath).exists())
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun revokedBundleCrossesTheBoundaryAsAnError() {
        val repo = File(System.getProperty("user.dir") ?: ".").resolve("..").canonicalFile
        val bundle = File(repo, "catalog/examples/signed")
        val work = Files.createTempDirectory("opencrown-ffi-revoked").toFile()
        val revocations =
            """{"schema":1,"revoked":[{"id":"org.opencrown.payload.example","version":"1.0.0"}]}"""
        try {
            assertThrows(FfiException.Payload::class.java) {
                preparePayload(
                    bundle.path,
                    work.path,
                    "EXAMPLE-1",
                    "example-6.1.0",
                    "arm64-v8a",
                    36u,
                    "EXAMPLE",
                    File(bundle, "manifest.sig").readBytes(),
                    hexToBytes(File(bundle, "public_key.hex").readText().trim()),
                    revocations,
                )
            }
        } finally {
            work.deleteRecursively()
        }
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
