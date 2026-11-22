package org.opencrown.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.opencrown.app.data.FeatureRegistry
import org.opencrown.app.data.FeatureStatus
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.model.Observation
import org.opencrown.app.ui.components.CrownButton
import org.opencrown.app.ui.components.CrownButtonStyle
import org.opencrown.app.ui.components.CrownGlyph
import org.opencrown.app.ui.components.CrownIcon
import org.opencrown.app.ui.components.CrownInsetGroup
import org.opencrown.app.ui.components.CrownNote
import org.opencrown.app.ui.components.CrownRow
import org.opencrown.app.ui.components.CrownScaffold
import org.opencrown.app.ui.components.CrownSectionHeader
import org.opencrown.app.ui.components.CrownStatusPill
import org.opencrown.app.ui.components.CrownTone
import org.opencrown.app.ui.navigation.Destination
import org.opencrown.app.ui.state.CheckpointRow
import org.opencrown.app.ui.state.CheckpointState
import org.opencrown.app.ui.state.QuickAction
import org.opencrown.app.ui.state.ShellState
import org.opencrown.app.ui.theme.CrownTheme

@Composable
fun HomeScreen(
    snapshot: CrownSnapshot?,
    registry: FeatureRegistry?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigate: (Destination) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    val model = ShellState.home(snapshot, registry)
    CrownScaffold(
        title = "Watch",
        subtitle = model.subtitle,
        actions = {
            org.opencrown.app.ui.components.CrownIconButton(
                glyph = CrownGlyph.REFRESH,
                contentDescription = "Refresh observations",
                onClick = onRefresh,
                tint = CrownTheme.colors.tint,
            )
            org.opencrown.app.ui.components.CrownIconButton(
                glyph = CrownGlyph.SETTINGS,
                contentDescription = "Settings",
                onClick = { onNavigate(Destination.Settings) },
                tint = CrownTheme.colors.tint,
            )
        },
        bottomBar = {},
    ) {
        item(key = "hero") {
            WatchHero(
                snapshot = snapshot,
                model = model,
                onPair = { onNavigate(Destination.Discovery) },
                onDiagnostics = { onNavigate(Destination.Diagnostics) },
            )
        }
        item(key = "setup-header") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("Setup")
        }
        item(key = "setup") {
            CrownInsetGroup(
                footer = "Each row is a real checkpoint. A row without evidence is waiting, not complete.",
            ) {
                model.checkpoints.forEachIndexed { index, checkpoint ->
                    CheckpointRowView(
                        checkpoint = checkpoint,
                        showDivider = index != model.checkpoints.lastIndex,
                    )
                }
            }
        }
        item(key = "privilege-header") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("This phone")
        }
        item(key = "privilege") {
            CrownInsetGroup(
                footer = "The app cannot consume the init-domain relay. Diagnostics records the measured reason.",
            ) {
                PrivilegeRow(
                    title = "Payload profile",
                    observation = snapshot?.privilege?.payloadCompatible,
                    onClick = { onNavigate(Destination.Diagnostics) },
                )
                PrivilegeRow(
                    title = "Per-boot relay",
                    observation = snapshot?.privilege?.relayMarker,
                    onClick = { onNavigate(Destination.Diagnostics) },
                )
                PrivilegeRow(
                    title = "Agent channel",
                    observation = snapshot?.privilege?.broker?.availability,
                    showDivider = false,
                    onClick = { onNavigate(Destination.Diagnostics) },
                )
            }
        }
        item(key = "explore-header") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("Explore")
        }
        item(key = "explore") {
            CrownInsetGroup {
                model.actions.forEachIndexed { index, action ->
                    ExploreRow(
                        action = action,
                        showDivider = index != model.actions.lastIndex,
                        onNavigate = onNavigate,
                    )
                }
            }
        }
        item(key = "registry") {
            Spacer(Modifier.height(24.dp))
            RegistrySummary(registry = registry, onOpen = { onNavigate(Destination.Evidence) })
        }
        item(key = "refresh") {
            Spacer(Modifier.height(20.dp))
            CrownButton(
                text = if (refreshing) "Reading phone\u2026" else "Refresh observations",
                onClick = onRefresh,
                enabled = !refreshing,
                style = CrownButtonStyle.TINTED,
            )
        }
        item(key = "honesty") {
            Spacer(Modifier.height(20.dp))
            CrownNote(
                message = "Nothing on this screen is generated. Watch values appear only when a watch reports them; unavailable states explain why.",
                tone = CrownTone.NEUTRAL,
            )
        }
    }
}

@Composable
private fun WatchHero(
    snapshot: CrownSnapshot?,
    model: org.opencrown.app.ui.state.HomeModel,
    onPair: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val colors = CrownTheme.colors
    val spacing = CrownTheme.spacing
    val hasPair = model.watchName.valueOrNull != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CrownTheme.shapes.card)
            .background(colors.cardBackground)
            .padding(spacing.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (hasPair) colors.tint.copy(alpha = 0.12f) else colors.fill),
                contentAlignment = Alignment.Center,
            ) {
                CrownIcon(
                    CrownGlyph.WATCH,
                    tint = if (hasPair) colors.tint else colors.secondaryLabel,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.watchName.valueOrNull ?: "No watch paired",
                    style = CrownTheme.type.title3,
                    color = colors.label,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        hasPair -> "Pairing record restored from this phone"
                        snapshot == null -> "Reading this phone"
                        !snapshot.bluetooth.scanPermission -> "Nearby devices permission is required"
                        snapshot.bluetooth.enabled.valueOrNull != true -> "Bluetooth is off"
                        else -> "This phone is ready to discover a nearby watch"
                    },
                    style = CrownTheme.type.footnote,
                    color = colors.secondaryLabel,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PairPills(hasPair)
        }
        Spacer(Modifier.height(12.dp))
        val linkDetail = model.link.valueOrNull?.name
            ?: (model.link as? Observation.Absent)?.detail
            ?: "No link observation"
        Text(
            text = linkDetail,
            style = CrownTheme.type.footnote,
            color = colors.secondaryLabel,
        )
        Spacer(Modifier.height(16.dp))
        if (hasPair) {
            CrownButton(
                text = "Connection diagnostics",
                onClick = onDiagnostics,
                style = CrownButtonStyle.TINTED,
            )
        } else {
            CrownButton(text = "Pair a watch", onClick = onPair)
        }
    }
}

@Composable
private fun PairPills(hasPair: Boolean) {
    if (hasPair) {
        CrownStatusPill("Paired", CrownTone.INFO)
        CrownStatusPill("Not connected", CrownTone.NEUTRAL)
    } else {
        CrownStatusPill("Not paired", CrownTone.NEUTRAL)
    }
}

@Composable
private fun CheckpointRowView(checkpoint: CheckpointRow, showDivider: Boolean) {
    val (label, tone) = when (checkpoint.state) {
        CheckpointState.DONE -> "Done" to CrownTone.SUCCESS
        CheckpointState.ACTIVE -> "In progress" to CrownTone.INFO
        CheckpointState.PENDING -> "Waiting" to CrownTone.NEUTRAL
        CheckpointState.BLOCKED -> "Blocked" to CrownTone.CRITICAL
        CheckpointState.FAILED -> "Failed" to CrownTone.CRITICAL
    }
    CrownRow(
        title = checkpoint.title,
        subtitle = checkpoint.detail,
        showDivider = showDivider,
        trailing = { CrownStatusPill(label, tone) },
    )
}

@Composable
private fun PrivilegeRow(
    title: String,
    observation: Observation<String>?,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    val value = observation?.valueOrNull
        ?: (observation as? Observation.Absent)?.detail
        ?: "Not probed"
    CrownRow(
        title = title,
        subtitle = value,
        showDivider = showDivider,
        onClick = onClick,
        trailing = {
            CrownIcon(
                CrownGlyph.CHEVRON_RIGHT,
                tint = CrownTheme.colors.tertiaryLabel,
                modifier = Modifier.size(15.dp),
            )
        },
    )
}

@Composable
private fun ExploreRow(
    action: QuickAction,
    showDivider: Boolean,
    onNavigate: (Destination) -> Unit,
) {
    val destination = when (action.id) {
        "discover" -> Destination.Discovery
        "faces" -> Destination.Faces
        "notifications" -> Destination.Notifications
        "health" -> Destination.Health
        "apps" -> Destination.Apps
        else -> null
    }
    CrownRow(
        title = action.title,
        subtitle = if (action.enabled) action.subtitle else action.reason ?: action.subtitle,
        showDivider = showDivider,
        onClick = if (action.enabled && destination != null) {
            { onNavigate(destination) }
        } else {
            null
        },
        trailing = {
            if (action.enabled) {
                CrownIcon(
                    CrownGlyph.CHEVRON_RIGHT,
                    tint = CrownTheme.colors.tertiaryLabel,
                    modifier = Modifier.size(15.dp),
                )
            } else {
                CrownStatusPill("Unavailable", CrownTone.NEUTRAL)
            }
        },
    )
}

@Composable
private fun RegistrySummary(registry: FeatureRegistry?, onOpen: () -> Unit) {
    val colors = CrownTheme.colors
    val counts = registry?.counts
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CrownTheme.shapes.card)
            .background(colors.cardBackground)
            .padding(CrownTheme.spacing.cardPadding),
    ) {
        Text(text = "Capability registry", style = CrownTheme.type.headline, color = colors.label)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (counts == null) {
                "The registry asset could not be read."
            } else {
                counts.total.toString() + " capabilities tracked \u00b7 " +
                    counts[FeatureStatus.BLOCKED] + " blocked \u00b7 " +
                    counts[FeatureStatus.VERIFIED] + " verified"
            },
            style = CrownTheme.type.footnote,
            color = colors.secondaryLabel,
        )
        Spacer(Modifier.height(12.dp))
        CrownButton(
            text = "Open evidence",
            onClick = onOpen,
            style = CrownButtonStyle.TINTED,
        )
    }
}