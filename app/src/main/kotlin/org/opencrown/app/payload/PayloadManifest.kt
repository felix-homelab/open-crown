package org.opencrown.app.payload

import android.content.Context
import android.os.Build
import org.json.JSONObject

enum class Compatibility { VERIFIED, COMPATIBLE, UNSUPPORTED }

data class DeviceFacts(
    val model: String,
    val device: String,
    val androidSdk: Int,
    val androidRelease: String,
    val abi: String,
    val kernel: String,
    val fingerprint: String,
)

data class MatchResult(
    val compatibility: Compatibility,
    val reason: String,
    val deviceLabel: String,
)

data class SupportedDevice(
    val model: String,
    val device: String,
    val soc: String?,
    val kernelRelease: String?,
    val build: String?,
    val androidSdk: Int?,
    val status: String,
    val notes: String?,
)

data class PayloadManifest(
    val id: String,
    val name: String,
    val version: String,
    val family: String,
    val entry: String,
    val entrySize: Int,
    val abis: List<String>,
    val androidMinSdk: Int,
    val kernelFamily: String,
    val kernelPattern: Regex,
    val verifiedKernel: String?,
    val targetPath: String?,
    val targetSymbol: String?,
    val minSymbolSize: Int,
    val drivers: List<String>,
    val devices: List<SupportedDevice>,
    val notes: String?,
) {
    fun payloadBytes(context: Context): ByteArray {
        return context.assets.open("payloads/$id/$entry").use { it.readBytes() }
    }

    fun match(facts: DeviceFacts): MatchResult {
        val exact = devices.firstOrNull { device ->
            device.model.equals(facts.model, ignoreCase = true) &&
                (device.device.isEmpty() || device.device.equals(facts.device, ignoreCase = true)) &&
                (device.build.isNullOrEmpty() || facts.fingerprint.contains(device.build)) &&
                (device.kernelRelease.isNullOrEmpty() || facts.kernel.equals(device.kernelRelease))
        }
        if (exact != null && exact.status == "verified") {
            return MatchResult(
                Compatibility.VERIFIED,
                "${exact.model} / ${exact.kernelRelease ?: facts.kernel} is a verified target",
                exact.model,
            )
        }

        if (facts.abi !in abis) {
            return MatchResult(Compatibility.UNSUPPORTED, "ABI ${facts.abi} is not supported", facts.model)
        }
        if (facts.androidSdk < androidMinSdk) {
            return MatchResult(Compatibility.UNSUPPORTED, "Android SDK ${facts.androidSdk} is below $androidMinSdk", facts.model)
        }
        if (!kernelPattern.matches(facts.kernel)) {
            return MatchResult(Compatibility.UNSUPPORTED, "kernel ${facts.kernel} is outside the supported family", facts.model)
        }
        return MatchResult(
            Compatibility.COMPATIBLE,
            "specification matches, but ${facts.model} has no verified device row",
            facts.model,
        )
    }

    companion object {
        const val ASSET_MANIFEST = "payloads/org.opencrown.payload.pagecache-relay/manifest.json"

        fun load(context: Context): PayloadManifest {
            val root = JSONObject(context.assets.open(ASSET_MANIFEST).bufferedReader().use { it.readText() })
            val requirements = root.getJSONObject("requirements")
            val targets = requirements.optJSONArray("targets")
            val target = if (targets != null && targets.length() > 0) targets.getJSONObject(0) else null
            val devices = buildList {
                val array = root.optJSONArray("devices") ?: return@buildList
                for (i in 0 until array.length()) {
                    val d = array.getJSONObject(i)
                    add(
                        SupportedDevice(
                            model = d.getString("model"),
                            device = d.optString("device", ""),
                            soc = d.optString("soc").ifBlank { null },
                            kernelRelease = d.optString("kernel_release").ifBlank { null },
                            build = d.optString("build").ifBlank { null },
                            androidSdk = d.optInt("android_sdk", 0).takeIf { it > 0 },
                            status = d.optString("status", "candidate"),
                            notes = d.optString("notes").ifBlank { null },
                        )
                    )
                }
            }
            val drivers = buildList {
                val array = requirements.optJSONArray("drivers") ?: return@buildList
                for (i in 0 until array.length()) add(array.getString(i))
            }
            return PayloadManifest(
                id = root.getString("id"),
                name = root.getString("name"),
                version = root.getString("version"),
                family = root.getString("family"),
                entry = root.getString("entry"),
                entrySize = root.optInt("entry_size", 0),
                abis = requirements.getJSONArray("abis").toStringList(),
                androidMinSdk = requirements.getInt("android_min_sdk"),
                kernelFamily = requirements.optString("kernel_family", ""),
                kernelPattern = Regex(requirements.getString("kernel_pattern")),
                verifiedKernel = requirements.optString("verified_kernel").ifBlank { null },
                targetPath = target?.optString("path")?.ifBlank { null },
                targetSymbol = target?.optString("symbol")?.ifBlank { null },
                minSymbolSize = target?.optInt("min_symbol_size", 0) ?: 0,
                drivers = drivers,
                devices = devices,
                notes = root.optString("notes").ifBlank { null },
            )
        }

        fun currentDevice(): DeviceFacts = DeviceFacts(
            model = Build.MODEL,
            device = Build.DEVICE,
            androidSdk = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            kernel = System.getProperty("os.version") ?: "unknown",
            fingerprint = Build.FINGERPRINT,
        )

        private fun org.json.JSONArray.toStringList(): List<String> =
            (0 until length()).map { getString(it) }
    }
}
