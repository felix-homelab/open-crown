package org.opencrown.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.opencrown.app.data.FeatureRegistry
import org.opencrown.app.data.FeatureRow
import org.opencrown.app.data.FeatureStatus
import org.opencrown.app.ui.components.CrownButton
import org.opencrown.app.ui.components.CrownButtonStyle
import org.opencrown.app.ui.components.CrownCard
import org.opencrown.app.ui.components.CrownEmptyState
import org.opencrown.app.ui.components.CrownGlyph
import org.opencrown.app.ui.components.CrownInsetGroup
import org.opencrown.app.ui.components.CrownNote
import org.opencrown.app.ui.components.CrownScaffold
import org.opencrown.app.ui.components.CrownSearchField
import org.opencrown.app.ui.components.CrownSectionHeader
import org.opencrown.app.ui.components.CrownSegmentedControl
import org.opencrown.app.ui.components.CrownStatusPill
import org.opencrown.app.ui.components.CrownTone
import org.opencrown.app.ui.theme.CrownTheme

private enum class EvidenceFilter(val label: String) {
    ALL("All"),
    BUILT("Built"),
    BLOCKED("Blocked"),
    PLANNED("Planned"),
}

@Composable
fun EvidenceScreen(
    registry: FeatureRegistry?,
    error: String?,
    onBack: () -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filterIndex by remember { mutableIntStateOf(0) }
    val filter = EvidenceFilter.entries[filterIndex]
    val filtered = remember(query, filterIndex, registry) {
        registry?.search(query)?.filter { row ->
            when (filter) {
                EvidenceFilter.ALL -> true
                EvidenceFilter.BUILT -> row.status == FeatureStatus.VERIFIED ||
                    row.status == FeatureStatus.IMPLEMENTED_UNVERIFIED
                EvidenceFilter.BLOCKED -> row.status == FeatureStatus.BLOCKED ||
                    row.status == FeatureStatus.OUT_OF_SCOPE
                EvidenceFilter.PLANNED -> row.status == FeatureStatus.UNIMPLEMENTED ||
                    row.status == FeatureStatus.UNKNOWN
            }
        } ?: emptyList()
    }
    CrownScaffold(
        title = "Evidence",
        subtitle = "Every capability and its real state",
        onBack = onBack,
    ) {
        if (registry == null) {
            item(key = "error") {
                CrownEmptyState(
                    glyph = CrownGlyph.INFO,
                    title = "Registry unavailable",
                    message = error ?: "The feature registry asset could not be read.",
                )
            }
        } else {
        item(key = "search") {
            CrownSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search capabilities, ids or blockers",
            )
            Spacer(Modifier.height(12.dp))
            CrownSegmentedControl(
                options = EvidenceFilter.entries.map { it.label },
                selectedIndex = filterIndex,
                onSelect = { filterIndex = it },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = filtered.size.toString() + " of " + registry.counts.total +
                    " rows \u00b7 registry updated " + registry.updated,
                style = CrownTheme.type.caption1,
                color = CrownTheme.colors.tertiaryLabel,
            )
            Spacer(Modifier.height(18.dp))
            CrownNote(
                message = "Verified means exercised on physical hardware. Built means code and tests exist but the hardware gate is not met. Blocked rows name their dependency; planned rows are not claims.",
                tone = CrownTone.NEUTRAL,
            )
        }
        if (filtered.isEmpty()) {
            item(key = "no-results") {
                CrownEmptyState(
                    glyph = CrownGlyph.MAGNIFY,
                    title = "No matching rows",
                    message = "Try a different search or filter.",
                )
            }
        } else {
            evidenceItems(registry, filtered, onOpenFeature)
        }
        }
    }
}

private fun LazyListScope.evidenceItems(
    registry: FeatureRegistry,
    filtered: List<FeatureRow>,
    onOpenFeature: (String) -> Unit,
) {
    val grouped = filtered.groupBy { it.category }
    registry.categories.forEach { category ->
        val rows = grouped[category] ?: return@forEach
        item(key = "header-" + category) {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader(category + " \u00b7 " + rows.size)
        }
        item(key = "group-" + category) {
            CrownInsetGroup {
                rows.forEachIndexed { index, row ->
                    FeatureRowView(row, index != rows.lastIndex, onOpenFeature)
                }
            }
        }
    }
}

@Composable
fun FeatureDetailScreen(
    featureId: String,
    registry: FeatureRegistry?,
    onBack: () -> Unit,
) {
    val row = registry?.rows?.firstOrNull { it.id == featureId }
    if (row == null) {
        CrownScaffold(title = "Capability", onBack = onBack) {
            item(key = "missing") {
                CrownEmptyState(
                    glyph = CrownGlyph.INFO,
                    title = "Row not found",
                    message = featureId + " is not in the registry asset.",
                )
            }
        }
        return
    }
    val blocker = row.blocker?.let { registry.blockers[it] }
    CrownScaffold(
        title = row.title,
        subtitle = row.category,
        onBack = onBack,
    ) {
        item(key = "status") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CrownTheme.shapes.card)
                    .background(CrownTheme.colors.cardBackground)
                    .padding(16.dp),
            ) {
                CrownStatusPill(row.status.shortLabel(), row.status.tone())
                Spacer(Modifier.height(10.dp))
                Text(
                    text = row.iphoneBehavior,
                    style = CrownTheme.type.body,
                    color = CrownTheme.colors.label,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Registry id " + row.id + " \u00b7 phase " + row.phase,
                    style = CrownTheme.type.caption1,
                    color = CrownTheme.colors.tertiaryLabel,
                )
            }
        }
        if (blocker != null) {
            item(key = "blocker") {
                Spacer(Modifier.height(24.dp))
                CrownSectionHeader("Blocker")
                CrownCard {
                    Text(
                        text = blocker.id,
                        style = CrownTheme.type.headline,
                        color = CrownTheme.colors.destructive,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = blocker.dependency,
                        style = CrownTheme.type.footnote,
                        color = CrownTheme.colors.label,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = blocker.reason,
                        style = CrownTheme.type.footnote,
                        color = CrownTheme.colors.secondaryLabel,
                    )
                }
            }
        }
        if (row.notes.isNotBlank()) {
            item(key = "notes") {
                Spacer(Modifier.height(24.dp))
                CrownSectionHeader("Notes")
                CrownNote(message = row.notes, tone = CrownTone.NEUTRAL)
            }
        }
        if (row.evidence.isNotEmpty()) {
            item(key = "evidence") {
                Spacer(Modifier.height(24.dp))
                CrownSectionHeader("Evidence")
                CrownInsetGroup {
                    row.evidence.forEachIndexed { index, path ->
                        org.opencrown.app.ui.components.CrownRow(
                            title = path,
                            showDivider = index != row.evidence.lastIndex,
                        )
                    }
                }
            }
        }
        item(key = "gate") {
            Spacer(Modifier.height(24.dp))
            CrownNote(
                title = "Completion gate",
                message = "A row becomes verified only after independent implementation, protocol tests, a physical run on the named watch and phone, an observed UI state, recovery, and updated evidence.",
                tone = CrownTone.INFO,
            )
        }
    }
}