package org.opencrown.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sealed at-rest pairing records.
 *
 * Only non-secret pairing metadata is stored here today. The type exists now so
 * that link keys can later be sealed in the same envelope rather than being
 * bolted onto SharedPreferences. The AES key lives in Android Keystore and is
 * not exportable.
 */
interface PairRecordStore {
    fun load(): List<org.opencrown.app.model.PairRecord>
    fun save(record: org.opencrown.app.model.PairRecord)
    fun delete(id: String)
    fun clear()
}

class KeystorePairRecordStore(context: Context) : PairRecordStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): List<org.opencrown.app.model.PairRecord> {
        val payload = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val json = String(unseal(payload), Charsets.UTF_8)
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        org.opencrown.app.model.PairRecord(
                            id = o.getString("id"),
                            watch = org.opencrown.app.model.WatchIdentity(
                                stableId = o.getString("watchId"),
                                displayName = o.getString("watchName"),
                                model = o.optString("model").ifBlank { null },
                                osVersion = o.optString("osVersion").ifBlank { null },
                                advertisedName = null,
                            ),
                            epoch = o.getLong("epoch"),
                            createdAtMillis = o.getLong("createdAt"),
                            lastSeenAtMillis = o.getLong("lastSeenAt"),
                            phase = runCatching {
                                org.opencrown.app.model.PairPhase.valueOf(o.getString("phase"))
                            }.getOrDefault(org.opencrown.app.model.PairPhase.UNPAIRED),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // A corrupt or foreign envelope is treated as absent, but it is not
            // silently deleted: the caller surfaces the condition in diagnostics.
            emptyList()
        }
    }

    override fun save(record: org.opencrown.app.model.PairRecord) {
        val current = load().associateBy { it.id }.toMutableMap()
        current[record.id] = record
        write(current.values.toList())
    }

    override fun delete(id: String) {
        write(load().filterNot { it.id == id })
    }

    override fun clear() {
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    private fun write(records: List<org.opencrown.app.model.PairRecord>) {
        val array = JSONArray()
        for (r in records) {
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("watchId", r.watch.stableId)
                    .put("watchName", r.watch.displayName)
                    .put("model", r.watch.model ?: "")
                    .put("osVersion", r.watch.osVersion ?: "")
                    .put("epoch", r.epoch)
                    .put("createdAt", r.createdAtMillis)
                    .put("lastSeenAt", r.lastSeenAtMillis)
                    .put("phase", r.phase.name)
            )
        }
        prefs.edit().putString(KEY_RECORDS, seal(array.toString().toByteArray(Charsets.UTF_8))).apply()
    }

    private fun seal(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(body, Base64.NO_WRAP)
    }

    private fun unseal(encoded: String): ByteArray {
        val parts = encoded.split(":", limit = 2)
        require(parts.size == 2) { "sealed record has no IV" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val body = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "org.opencrown.pair-records.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS = "org.opencrown.pairs"
        const val KEY_RECORDS = "records.v1"
    }
}
