package org.opencrown.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The readiness path across the generated bindings. Rust covers every reason;
 * this suite proves the FFI surface and the app's JSON assets carry the same
 * verdicts into the JVM.
 */
class ReadinessTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun locateLibrary() {
            val workingDir = System.getProperty("user.dir") ?: "."
            val libDir = File(workingDir).resolve("../target/debug").canonicalFile
            System.setProperty("jna.library.path", libDir.path)
        }
    }

    private val repo = File(System.getProperty("user.dir") ?: ".").resolve("..").canonicalFile
    private val example = File(repo, "catalog/examples/signed")
    private val id = "org.opencrown.payload.example"

    private fun device(
        model: String = "EXAMPLE-1",
        kernel: String = "example-6.1.0",
        sdk: UInt = 36u,
    ) = DeviceInfo(model, kernel, "arm64-v8a", sdk, "EXAMPLE")

    private fun index(): String =
        """
        {
          "schema": 1,
          "updated": "2026-11-21",
          "sources": [
            {"id": "example-source", "url": "https://example.invalid/payload", "license": "MIT"}
          ],
          "payloads": [
            {
              "id": "$id",
              "name": "Example",
              "family": "page-cache",
              "status": "available",
              "source": "example-source",
              "android": ">=31",
              "abi": "arm64-v8a",
              "targets": [
                {"model": "EXAMPLE-1", "kernel_build": "example-6.1.0"}
              ]
            }
          ]
        }
        """.trimIndent()

    private fun pinned(): String =
        CatalogKeys.parse(File(repo, "catalog/catalog-keys.json").readText()).first()

    private fun emptyBundles(): File = Files.createTempDirectory("readiness-empty").toFile()

    private fun readyBundles(): File {
        val root = Files.createTempDirectory("readiness-ready").toFile()
        example.copyRecursively(File(root, id), overwrite = true)
        return root
    }

    private fun readiness(
        bundles: File,
        device: DeviceInfo = device(),
        agentVersion: String = "0.1.0",
        key: String = pinned(),
        revocations: String = """{"schema":1,"revoked":[]}""",
    ) = RustBridge.readiness(
        index(),
        bundles.path,
        device,
        agentVersion,
        listOf(key),
        revocations,
    )

    @Test
    fun signedExampleIsReady() {
        val root = readyBundles()
        try {
            val result = readiness(root)
            assertEquals(1, result.size)
            assertEquals("ready", result[0].reason)
            assertEquals("1.0.0", result[0].bundleVersion)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingBundleIsNoBundle() {
        val root = emptyBundles()
        try {
            assertEquals("no_bundle", readiness(root)[0].reason)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun wrongPinnedKeyIsBadSignature() {
        val root = readyBundles()
        try {
            val result = readiness(root, key = "0".repeat(64))
            assertEquals("bad_signature", result[0].reason)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun revokedBundleIsRevoked() {
        val root = readyBundles()
        try {
            val revocations =
                """{"schema":1,"revoked":[{"id":"$id","version":"1.0.0"}]}"""
            assertEquals("revoked", readiness(root, revocations = revocations)[0].reason)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun a52DoesNotMatchTheExampleTarget() {
        val root = emptyBundles()
        try {
            val result = readiness(
                root,
                device = device(
                    "SM-A528B",
                    "5.4.254-qgki-28575149-abA528BXXSBGYI3",
                    34u,
                ),
            )
            assertEquals("no_match", result[0].reason)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun oldAgentIsTooOld() {
        val root = readyBundles()
        try {
            assertEquals("agent_too_old", readiness(root, agentVersion = "0.0.9")[0].reason)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun probeRecognisesTheA52AsExample() {
        val json = File(repo, "catalog/supported-devices.json").readText()
        val probe = RustBridge.probe(
            device("SM-A528B", "5.4.254-qgki-28575149-abA528BXXSBGYI3", 34u),
            json,
        )
        assertEquals("example", probe.supportedStatus)
        assertEquals(64, probe.digest.length)
        assertTrue(probe.buildFingerprint.isEmpty())
    }
}
