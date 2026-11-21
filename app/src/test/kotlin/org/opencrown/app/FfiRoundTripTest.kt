package org.opencrown.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import org.opencrown.ffi.FfiException
import org.opencrown.ffi.protocolVersion
import org.opencrown.ffi.requireVerifiedProtocol

/**
 * The Phase 1 exit criterion: Kotlin calls Rust, and an error crosses back.
 *
 * The library is the host build in target/debug; on CI this job runs on the same
 * platform that compiled it.
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
}
