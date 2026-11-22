package org.opencrown.app.data

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

/** Availability vocabulary, kept in lockstep with docs/cleanroom/BLOCKERS.md. */
enum class FeatureStatus(val wire: String, val label: String, val group: FeatureGroup) {
    VERIFIED("verified", "Verified", FeatureGroup.WORKING),
    IMPLEMENTED_UNVERIFIED("implemented_unverified", "Built, unverified", FeatureGroup.WORKING),
    BLOCKED("blocked", "Blocked", FeatureGroup.BLOCKED),
    OUT_OF_SCOPE("out_of_scope", "Out of scope", FeatureGroup.BLOCKED),
    UNIMPLEMENTED("unimplemented", "Not built", FeatureGroup.PLANNED),
    UNKNOWN("unknown", "Unknown", FeatureGroup.PLANNED),
    ;

    companion object {
        fun fromWire(value: String?): FeatureStatus =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

enum class FeatureGroup { WORKING, PLANNED, BLOCKED }

data class FeatureRow(
    val id: String,
    val category: String,
    val title: String,
    val iphoneBehavior: String,
    val status: FeatureStatus,
    val blocker: String?,
    val phase: Int,
    val notes: String,
    val evidence: List<String>,
)

data class BlockerInfo(
    val id: String,
    val dependency: String,
    val reason: String,
)

data class RegistryCounts(
    val byStatus: Map<FeatureStatus, Int>,
    val total: Int,
) {
    operator fun get(status: FeatureStatus): Int = byStatus[status] ?: 0
}

/**
 * The shipped feature registry, read from a build-generated asset.
 *
 * The JSON is generated from tools/feature-registry/build.py so the app and the
 * docs cannot drift. A missing or malformed asset is an explicit [Unavailable]
 * result, never an empty "all good" screen.
 */
class FeatureRegistry private constructor(
    val rows: List<FeatureRow>,
    val categories: List<String>,
    val blockers: Map<String, BlockerInfo>,
    val updated: String,
) {
    val counts: RegistryCounts = RegistryCounts(rows.groupingBy { it.status }.eachCount(), rows.size)

    fun byCategory(): Map<String, List<FeatureRow>> =
        rows.groupBy { it.category }.toSortedMap(compareBy { categories.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE })

    fun rowsFor(status: FeatureStatus): List<FeatureRow> = rows.filter { it.status == status }

    fun search(query: String): List<FeatureRow> {
        val q = query.trim()
        if (q.isEmpty()) return rows
        return rows.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.id.contains(q, ignoreCase = true) ||
                it.iphoneBehavior.contains(q, ignoreCase = true) ||
                (it.blocker?.contains(q, ignoreCase = true) == true)
        }
    }

    sealed interface LoadResult {
        data class Loaded(val registry: FeatureRegistry) : LoadResult
        data class Unavailable(val detail: String) : LoadResult
    }

    companion object {
        const val ASSET_PATH = "cleanroom/feature-registry.json"

        fun load(context: Context): LoadResult = try {
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            parse(text)
        } catch (e: Exception) {
            LoadResult.Unavailable("Registry asset could not be read: " + e.javaClass.simpleName)
        }

        /** Pure parser, used by the asset loader and by JVM tests. */
        fun parse(text: String): LoadResult = try {
            val root = JSONObject(text)
            val features = root.getJSONArray("features")
            val rows = buildList {
                for (i in 0 until features.length()) {
                    val o = features.getJSONObject(i)
                    val evidence = o.optJSONArray("evidence")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                    add(
                        FeatureRow(
                            id = o.getString("id"),
                            category = o.getString("category"),
                            title = o.getString("title"),
                            iphoneBehavior = o.optString("iphone_behavior", ""),
                            status = FeatureStatus.fromWire(o.optString("status")),
                            blocker = if (o.isNull("blocker")) {
                                null
                            } else {
                                o.optString("blocker").ifBlank { null }
                            },
                            phase = o.optInt("phase", 0),
                            notes = o.optString("notes", ""),
                            evidence = evidence,
                        )
                    )
                }
            }
            val blockers = buildMap {
                val obj = root.optJSONObject("blockers")
                if (obj != null) {
                    for (id in obj.keys()) {
                        val entry = obj.optJSONObject(id) ?: continue
                        put(
                            id,
                            BlockerInfo(
                                id = id,
                                dependency = entry.optString("dependency", "Unspecified dependency"),
                                reason = entry.optString("reason", "See docs/cleanroom/BLOCKERS.md."),
                            ),
                        )
                    }
                }
            }
            val categories = buildList {
                val arr = root.optJSONArray("categories")
                if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
            }.ifEmpty { rows.map { it.category }.distinct() }
            LoadResult.Loaded(FeatureRegistry(rows, categories, blockers, root.optString("updated", "unknown")))
        } catch (e: JSONException) {
            LoadResult.Unavailable("Registry asset is malformed: ${e.message}")
        } catch (e: Exception) {
            LoadResult.Unavailable("Registry asset could not be read: ${e.javaClass.simpleName}")
        }
    }
}