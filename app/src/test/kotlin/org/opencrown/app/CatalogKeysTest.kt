package org.opencrown.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogKeysTest {
    private val key = "9b0b1a24457e16d971f24efd6f58228e408f3f91878a601a6ec7b1713ae1082e"

    @Test
    fun readsPinnedKey() {
        val json = """{"schema":1,"keys":[{"id":"example","public_key":"$key"}]}"""
        assertEquals(listOf(key), CatalogKeys.parse(json))
    }

    @Test
    fun normalisesUppercase() {
        val json = """{"public_key":"${key.uppercase()}"}"""
        assertEquals(listOf(key), CatalogKeys.parse(json))
    }

    @Test
    fun emptyKeyFileIsAnError() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogKeys.parse("""{"schema":1,"keys":[]}""")
        }
    }

    @Test
    fun shortKeyIsAnError() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogKeys.parse("""{"public_key":"abcd"}""")
        }
    }
}
