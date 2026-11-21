package org.opencrown.app

/**
 * The catalog public keys the app pins (ADR 0012).
 *
 * The key file is JSON so it can grow a rotation window, but parsing is a
 * deliberately small extraction: the app only needs the lowercase hex keys. A
 * missing or malformed key is a hard error, never a silent "trust the bundle".
 */
object CatalogKeys {
    private val KEY = Regex("\"public_key\"\\s*:\\s*\"([0-9a-fA-F]{64})\"")

    fun parse(json: String): List<String> {
        val keys = KEY.findAll(json).map { it.groupValues[1].lowercase() }.toList()
        require(keys.isNotEmpty()) { "catalog key file declares no 32-byte hex key" }
        require(keys.distinct().size == keys.size) { "catalog key file contains duplicates" }
        return keys
    }
}
