package org.opencrown.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRegistryTest {
    private val fixture = """
        {
          "schema": 1,
          "updated": "2026-11-22",
          "categories": ["Foundation"],
          "blockers": {
            "B-X": {"dependency": "A named dependency", "reason": "Because it is external."}
          },
          "features": [
            {
              "id": "built.row",
              "category": "Foundation",
              "title": "Built row",
              "iphone_behavior": "Does the thing.",
              "status": "implemented_unverified",
              "blocker": null,
              "phase": 1,
              "notes": "",
              "evidence": []
            },
            {
              "id": "blocked.row",
              "category": "Foundation",
              "title": "Blocked row",
              "iphone_behavior": "Cannot do the thing.",
              "status": "blocked",
              "blocker": "B-X",
              "phase": 2,
              "notes": "Needs the dependency.",
              "evidence": ["docs/x.md"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun nullBlockerIsNotTheStringNull() {
        val loaded = FeatureRegistry.parse(fixture) as FeatureRegistry.LoadResult.Loaded
        val row = loaded.registry.rows.first { it.id == "built.row" }
        assertNull(row.blocker)
        assertEquals(FeatureStatus.IMPLEMENTED_UNVERIFIED, row.status)
    }

    @Test
    fun blockedRowKeepsBlockerMetadata() {
        val loaded = FeatureRegistry.parse(fixture) as FeatureRegistry.LoadResult.Loaded
        val registry = loaded.registry
        val row = registry.rows.first { it.id == "blocked.row" }
        assertEquals("B-X", row.blocker)
        assertEquals("A named dependency", registry.blockers["B-X"]?.dependency)
        assertEquals(1, registry.counts[FeatureStatus.BLOCKED])
        assertEquals(2, registry.counts.total)
    }

    @Test
    fun malformedAssetIsUnavailableInsteadOfEmptySuccess() {
        val result = FeatureRegistry.parse("{ not json")
        assertTrue(result is FeatureRegistry.LoadResult.Unavailable)
    }
}
