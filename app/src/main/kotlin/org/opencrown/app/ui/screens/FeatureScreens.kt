package org.opencrown.app.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.opencrown.app.data.FeatureRegistry
import org.opencrown.app.data.FeatureRow
import org.opencrown.app.data.FeatureStatus
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.model.Observation
import org.opencrown.app.ui.components.CrownBrandMark
import org.opencrown.app.ui.components.CrownButton
import org.opencrown.app.ui.components.CrownButtonStyle
import org.opencrown.app.ui.components.CrownDisclosureRow
import org.opencrown.app.ui.components.CrownEmptyState
import org.opencrown.app.ui.components.CrownGlyph
import org.opencrown.app.ui.components.CrownInsetGroup
import org.opencrown.app.ui.components.CrownNote
import org.opencrown.app.ui.components.CrownRow
import org.opencrown.app.ui.components.CrownScaffold
import org.opencrown.app.ui.components.CrownSectionHeader
import org.opencrown.app.ui.components.CrownSheet
import org.opencrown.app.ui.components.CrownStatusPill
import org.opencrown.app.ui.components.CrownTone
import org.opencrown.app.ui.components.CrownValueRow
import org.opencrown.app.ui.navigation.Destination
import org.opencrown.app.ui.theme.CrownTheme

internal fun FeatureStatus.tone(): CrownTone = when (this) {
    FeatureStatus.VERIFIED -> CrownTone.SUCCESS
    FeatureStatus.IMPLEMENTED_UNVERIFIED -> CrownTone.INFO
    FeatureStatus.BLOCKED -> CrownTone.CRITICAL
    FeatureStatus.OUT_OF_SCOPE -> CrownTone.WARNING
    FeatureStatus.UNIMPLEMENTED, FeatureStatus.UNKNOWN -> CrownTone.NEUTRAL
}

internal fun FeatureStatus.shortLabel(): String = when (this) {
    FeatureStatus.VERIFIED -> "Verified"
    FeatureStatus.IMPLEMENTED_UNVERIFIED -> "Built"
    FeatureStatus.BLOCKED -> "Blocked"
    FeatureStatus.OUT_OF_SCOPE -> "Out of scope"
    FeatureStatus.UNIMPLEMENTED -> "Not built"
    FeatureStatus.UNKNOWN -> "Unknown"
}

internal fun unavailableReason(snapshot: CrownSnapshot?): String {
    val link = snapshot?.link
    return when (link) {
        null -> "The phone has not been read yet."
        is Observation.Absent -> link.detail
        is Observation.Present -> "A secure link is not reported."
    }
}

@Composable
internal fun FeatureRowsGroup(
    rows: List<FeatureRow>,
    onOpenFeature: (String) -> Unit,
    footer: String? = null,
) {
    if (rows.isEmpty()) {
        CrownNote(message = "No registry rows in this section.", tone = CrownTone.NEUTRAL)
        return
    }
    CrownInsetGroup(footer = footer) {
        rows.forEachIndexed { index, row ->
            FeatureRowView(row, index != rows.lastIndex, onOpenFeature)
        }
    }
}

@Composable
internal fun FeatureRowView(row: FeatureRow, showDivider: Boolean, onOpen: (String) -> Unit) {
    CrownRow(
        title = row.title,
        subtitle = row.blocker?.let { id -> "Blocker " + id } ?: row.iphoneBehavior,
        showDivider = showDivider,
        onClick = { onOpen(row.id) },
        trailing = { CrownStatusPill(row.status.shortLabel(), row.status.tone()) },
    )
}

@Composable
fun FacesScreen(
    snapshot: CrownSnapshot?,
    registry: FeatureRegistry?,
    onBack: () -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    val rows = registry?.rows?.filter { it.id.startsWith("faces.") } ?: emptyList()
    CrownScaffold(
        title = "Watch faces",
        subtitle = "Inventory, edit and selection",
        onBack = onBack,
    ) {
        item(key = "hero") {
            CrownEmptyState(
                glyph = CrownGlyph.FACE,
                title = "No face observation",
                message = unavailableReason(snapshot) + " A face list is never shown from a cache.",
            )
            Spacer(Modifier.height(20.dp))
        }
        item(key = "note") {
            CrownNote(
                title = "Original rendering only",
                message = "Apple-rendered face artwork is not redistributed. A preview must be rendered from device-reported data by an original renderer.",
                tone = CrownTone.INFO,
            )
        }
        if (rows.isNotEmpty()) {
            item(key = "header") {
                Spacer(Modifier.height(24.dp))
                CrownSectionHeader("Face capabilities · " + rows.size)
            }
            item(key = "rows") {
                FeatureRowsGroup(rows, onOpenFeature)
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    snapshot: CrownSnapshot?,
    registry: FeatureRegistry?,
    onBack: () -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    val rows = registry?.rows?.filter { it.id.startsWith("notifications.") } ?: emptyList()
    CrownScaffold(
        title = "Notifications",
        subtitle = "Mirroring and replies",
        onBack = onBack,
    ) {
        item(key = "note") {
            CrownNote(
                title = "No notification session",
                message = unavailableReason(snapshot) + " Android notification access is not requested in this build.",
                tone = CrownTone.WARNING,
            )
            Spacer(Modifier.height(20.dp))
        }
        item(key = "permissions") {
            CrownInsetGroup(title = "Prerequisites", footer = "Both rows are required before a single notification can be mirrored.") {
                CrownRow(
                    title = "Notification access",
                    subtitle = "Not requested yet",
                    showDivider = true,
                    trailing = { CrownStatusPill("Not built", CrownTone.NEUTRAL) },
                )
                CrownRow(
                    title = "Secure watch link",
                    subtitle = unavailableReason(snapshot),
                    showDivider = false,
                    trailing = { CrownStatusPill("Blocked", CrownTone.CRITICAL) },
                )
            }
        }
        if (rows.isNotEmpty()) {
            item(key = "header") {
                Spacer(Modifier.height(24.dp))
                CrownSectionHeader("Notification capabilities · " + rows.size)
            }
            item(key = "rows") { FeatureRowsGroup(rows, onOpenFeature) }
        }
    }
}

@Composable
fun HealthScreen(
    snapshot: CrownSnapshot?,
    registry: FeatureRegistry?,
    onBack: () -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    val rows = registry?.rows?.filter { it.id.startsWith("health.") } ?: emptyList()
    CrownScaffold(
        title = "Health",
        subtitle = "Activity, heart and wellbeing",
        onBack = onBack,
    ) {
        item(key = "hero") {
            CrownEmptyState(
                glyph = CrownGlyph.HEART,
                title = "No health data",
                message = unavailableReason(snapshot) + " Health samples are never generated or estimated by the app.",
            )
            Spacer(Modifier.height(16.dp))
        }
        item(key = "privacy") {
            CrownNote(
                title = "Local first",
                message = "Health data stays on the phone unless the user exports it. No Apple account is used, and no sample is uploaded.",
                tone = CrownTone.INFO,
            )
        }
        if (rows.isNotEmpty()) {
            item(key = "header") {
                Spacer(Modifier.height(24.dp))
                CrownSectionHeader("Health capabilities · " + rows.size)
            }
            item(key = "rows") { FeatureRowsGroup(rows, onOpenFeature) }
        }
    }
}

@Composable
fun AppsScreen(
    snapshot: CrownSnapshot?,
    registry: FeatureRegistry?,
    onBack: () -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    val rows = registry?.rows?.filter { it.id.startsWith("apps.") } ?: emptyList()
    CrownScaffold(
        title = "Apps & software",
        subtitle = "Watch apps and watchOS",
        onBack = onBack,
    ) {
        item(key = "hero") {
            CrownEmptyState(
                glyph = CrownGlyph.APPS,
                title = "No app inventory",
                message = unavailableReason(snapshot) + " Installed apps cannot be listed without a live session.",
            )
            Spacer(Modifier.height(16.dp))
        }
        item(key = "note") {
            CrownNote(
                title = "Signing is Apple's",
                message = "Installing, updating and purchasing watch apps needs Apple's code signing and account services. Those rows are blocked and remain visible.",
                tone = CrownTone.WARNING,
            )
        }
        if (rows.isNotEmpty()) {
            item(key = "header") {
                Spacer(Modifier.height(24.dp))
                CrownSectionHeader("App capabilities · " + rows.size)
            }
            item(key = "rows") { FeatureRowsGroup(rows, onOpenFeature) }
        }
    }
}

@Composable
fun SettingsScreen(
    snapshot: CrownSnapshot?,
    registry: FeatureRegistry?,
    appVersion: String,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    onOpenFeature: (String) -> Unit,
    onResetOnboarding: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    val verified = registry?.counts?.get(FeatureStatus.VERIFIED) ?: 0
    CrownScaffold(title = "Settings", subtitle = "Open Crown", onBack = onBack) {
        item(key = "watch") {
            CrownInsetGroup(title = "Watch") {
                CrownDisclosureRow("Status and connection", onClick = { onNavigate(Destination.Home) })
                CrownDisclosureRow("Watch faces", onClick = { onNavigate(Destination.Faces) })
                CrownDisclosureRow("Notifications", onClick = { onNavigate(Destination.Notifications) })
                CrownDisclosureRow("Health and activity", onClick = { onNavigate(Destination.Health) })
                CrownDisclosureRow(
                    "Apps and software",
                    onClick = { onNavigate(Destination.Apps) },
                    showDivider = false,
                )
            }
        }
        item(key = "phone") {
            Spacer(Modifier.height(24.dp))
            CrownInsetGroup(title = "This phone") {
                CrownDisclosureRow(
                    "Diagnostics",
                    subtitle = "Real probes, measurements and failures",
                    onClick = { onNavigate(Destination.Diagnostics) },
                )
                CrownDisclosureRow(
                    "Evidence and blockers",
                    subtitle = verified.toString() + " verified capabilities",
                    onClick = { onNavigate(Destination.Evidence) },
                    showDivider = false,
                )
            }
        }
        item(key = "access") {
            Spacer(Modifier.height(24.dp))
            CrownInsetGroup(title = "Accessibility") {
                CrownDisclosureRow(
                    "Display and motion",
                    onClick = { onNavigate(Destination.Accessibility) },
                    showDivider = false,
                )
            }
        }
        item(key = "about") {
            Spacer(Modifier.height(24.dp))
            CrownInsetGroup(title = "About") {
                CrownDisclosureRow("About Open Crown", onClick = { onNavigate(Destination.About) }, showDivider = false)
            }
        }
        item(key = "data") {
            Spacer(Modifier.height(24.dp))
            CrownInsetGroup(
                title = "Data",
                footer = "Resetting onboarding does not delete pair records. Pair records are only removed by an explicit unpair.",
            ) {
                CrownRow(
                    title = "Reset onboarding",
                    destructive = true,
                    onClick = { confirmReset = true },
                    showDivider = false,
                )
            }
        }
        item(key = "version") {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Open Crown " + appVersion + " · registry updated " + (registry?.updated ?: "unknown"),
                style = CrownTheme.type.caption1,
                color = CrownTheme.colors.tertiaryLabel,
            )
        }
    }
    if (confirmReset) {
        CrownSheet(
            title = "Reset onboarding?",
            onDismiss = { confirmReset = false },
            primaryLabel = "Reset and start over",
            onPrimary = {
                confirmReset = false
                onResetOnboarding()
            },
            secondaryLabel = "Cancel",
            onSecondary = { confirmReset = false },
        ) {
            Text(
                text = "The welcome screens will be shown again. Pairing records are not deleted.",
                style = CrownTheme.type.footnote,
                color = CrownTheme.colors.secondaryLabel,
            )
        }
    }
}

@Composable
fun AccessibilityScreen(onBack: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    val reduced = CrownTheme.reducedMotion
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val context = LocalContext.current
    CrownScaffold(
        title = "Display & motion",
        subtitle = "Accessibility in Open Crown",
        onBack = onBack,
    ) {
        item(key = "values") {
            CrownInsetGroup(
                title = "Current system settings",
                footer = "These values come from Android. Open Crown does not override them.",
            ) {
                CrownValueRow("Text size", "%.0f%%".format(fontScale * 100f))
                CrownValueRow("Motion", if (reduced) "Reduced" else "Full")
                CrownValueRow("Appearance", if (dark) "Dark" else "Light", showDivider = false)
            }
        }
        item(key = "note") {
            Spacer(Modifier.height(20.dp))
            CrownNote(
                title = "How the app adapts",
                message = "Text uses scalable sp units, rows wrap instead of clipping, icons that carry meaning have content descriptions, switches expose toggle state and every touch target is at least 44dp. Reduced motion shortens transitions and stops the skeleton shimmer.",
                tone = CrownTone.INFO,
            )
        }
        item(key = "open") {
            Spacer(Modifier.height(20.dp))
            CrownButton(
                text = "Open system accessibility settings",
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
                style = CrownButtonStyle.TINTED,
            )
        }
    }
}

@Composable
fun AboutScreen(
    registry: FeatureRegistry?,
    appVersion: String,
    snapshot: CrownSnapshot?,
    onBack: () -> Unit,
    onOpenEvidence: () -> Unit,
    onResetOnboarding: () -> Unit,
) {
    val colors = CrownTheme.colors
    CrownScaffold(title = "About", subtitle = "Open Crown " + appVersion, onBack = onBack) {
        item(key = "brand") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CrownBrandMark(size = 58.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = "Open Crown", style = CrownTheme.type.title2, color = colors.label)
                    Text(
                        text = "An independent Android companion for a watch you own.",
                        style = CrownTheme.type.footnote,
                        color = colors.secondaryLabel,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            CrownNote(
                title = "No Apple affiliation",
                message = "Open Crown is not affiliated with, endorsed by, or connected to Apple. It does not include Apple logos, fonts, SF Symbols, watch-face artwork or other Apple-owned assets.",
                tone = CrownTone.NEUTRAL,
            )
        }
        item(key = "privacy") {
            Spacer(Modifier.height(24.dp))
            CrownInsetGroup(title = "Privacy") {
                CrownRow(
                    title = "Local by default",
                    subtitle = "No analytics, no telemetry and no Apple account credential is stored.",
                    showDivider = true,
                )
                CrownRow(
                    title = "Sealed pairing metadata",
                    subtitle = "Pair records are encrypted with Android Keystore.",
                    showDivider = true,
                )
                CrownRow(
                    title = "No device identifiers in logs",
                    subtitle = "Diagnostics shows model and kernel, never the serial or a pairing key.",
                    showDivider = false,
                )
            }
        }
        item(key = "registry") {
            Spacer(Modifier.height(24.dp))
            CrownInsetGroup(title = "Evidence") {
                CrownRow(
                    title = "Feature registry",
                    subtitle = (registry?.counts?.total ?: 0).toString() + " capabilities, each verified or explained",
                    onClick = onOpenEvidence,
                )
                CrownRow(
                    title = "Clean-room provenance",
                    subtitle = "Two-lane process; reference never ships",
                    showDivider = false,
                )
            }
        }
        item(key = "licence") {
            Spacer(Modifier.height(24.dp))
            CrownNote(
                title = "Licence",
                message = "Open Crown is MIT licensed. The in-tree page-cache payload is quarantined and has an open provenance review; it is not clean-room product code.",
                tone = CrownTone.WARNING,
            )
        }
        item(key = "reset") {
            Spacer(Modifier.height(24.dp))
            CrownButton(
                text = "Show onboarding again",
                onClick = onResetOnboarding,
                style = CrownButtonStyle.PLAIN,
            )
        }
    }
}