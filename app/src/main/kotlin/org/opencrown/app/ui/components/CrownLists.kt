package org.opencrown.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opencrown.app.ui.theme.CrownTheme

/** Grouped inset list with an optional header and footer, as used by Settings. */
@Composable
fun CrownInsetGroup(
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CrownTheme.colors
    val spacing = CrownTheme.spacing
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = CrownTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = spacing.cardPadding, bottom = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CrownTheme.shapes.group)
                .background(colors.cardBackground),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                style = CrownTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = spacing.cardPadding, top = 7.dp, end = spacing.cardPadding),
            )
        }
    }
}

/** One row in a [CrownInsetGroup]. Callers set showDivider=false on the last row. */
@Composable
fun CrownRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingGlyph: CrownGlyph? = null,
    leadingTint: Color? = null,
    destructive: Boolean = false,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = CrownTheme.colors
    val spacing = CrownTheme.spacing
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .heightIn(min = spacing.rowMinHeight)
                .padding(horizontal = spacing.cardPadding, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingGlyph != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (destructive) colors.destructive else leadingTint ?: colors.tint),
                    contentAlignment = Alignment.Center,
                ) {
                    CrownIcon(leadingGlyph, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = CrownTheme.type.body,
                    color = if (destructive) colors.destructive else colors.label,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = CrownTheme.type.footnote,
                        color = colors.secondaryLabel,
                    )
                }
            }
            trailing()
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (leadingGlyph != null) spacing.cardPadding + 40.dp else spacing.cardPadding)
                    .height(0.5.dp)
                    .background(colors.separator)
            )
        }
    }
}

@Composable
fun CrownSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    val colors = CrownTheme.colors
    CrownRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        showDivider = showDivider,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.tint,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = colors.switchTrack,
                    uncheckedBorderColor = Color.Transparent,
                    disabledCheckedTrackColor = colors.tint.copy(alpha = 0.4f),
                ),
            )
        },
    )
}

@Composable
fun CrownValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    leadingGlyph: CrownGlyph? = null,
    showDivider: Boolean = true,
) {
    CrownRow(
        title = title,
        modifier = modifier,
        leadingGlyph = leadingGlyph,
        showDivider = showDivider,
        trailing = {
            Text(
                text = value,
                style = CrownTheme.type.body,
                color = CrownTheme.colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
fun CrownDisclosureRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leadingGlyph: CrownGlyph? = null,
    showDivider: Boolean = true,
) {
    CrownRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        leadingGlyph = leadingGlyph,
        showDivider = showDivider,
        onClick = onClick,
        trailing = {
            if (value != null) {
                Text(
                    text = value,
                    style = CrownTheme.type.body,
                    color = CrownTheme.colors.secondaryLabel,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
            }
            CrownIcon(
                CrownGlyph.CHEVRON_RIGHT,
                tint = CrownTheme.colors.tertiaryLabel,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

/** A free-standing card for explanations, summaries and empty states. */
@Composable
fun CrownCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CrownTheme.shapes.card)
            .background(CrownTheme.colors.cardBackground)
            .padding(CrownTheme.spacing.cardPadding),
        content = content,
    )
}

@Composable
fun CrownSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = CrownTheme.type.footnote,
        color = CrownTheme.colors.secondaryLabel,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = CrownTheme.spacing.cardPadding, bottom = 6.dp, top = 4.dp),
    )
}

@Composable
fun CrownDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = if (inset) CrownTheme.spacing.cardPadding else 0.dp)
            .height(0.5.dp)
            .background(CrownTheme.colors.separator)
    )
}

/** A compact status capsule. Colour is never the only signal. */
enum class CrownTone { NEUTRAL, INFO, SUCCESS, WARNING, CRITICAL }

@Composable
fun CrownStatusPill(
    text: String,
    tone: CrownTone = CrownTone.NEUTRAL,
    modifier: Modifier = Modifier,
) {
    val colors = CrownTheme.colors
    val color = when (tone) {
        CrownTone.NEUTRAL -> colors.secondaryLabel
        CrownTone.INFO -> colors.tint
        CrownTone.SUCCESS -> colors.success
        CrownTone.WARNING -> colors.warning
        CrownTone.CRITICAL -> colors.destructive
    }
    Row(
        modifier = modifier
            .clip(CrownTheme.shapes.pill)
            .background(color.copy(alpha = if (colors.isDark) 0.22f else 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CrownTheme.shapes.pill)
                .background(color)
        )
        Text(text = text, style = CrownTheme.type.caption2, color = color)
    }
}

/** A labelled diagnostic line: value in monospace, provenance below. */
@Composable
fun CrownDiagnosticRow(
    label: String,
    value: String,
    detail: String? = null,
    tone: CrownTone = CrownTone.NEUTRAL,
    showDivider: Boolean = true,
) {
    val colors = CrownTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = label,
                style = CrownTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = CrownTheme.type.caption1.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = when (tone) {
                    CrownTone.CRITICAL -> colors.destructive
                    CrownTone.WARNING -> colors.warning
                    CrownTone.SUCCESS -> colors.success
                    else -> colors.label
                },
                modifier = Modifier.weight(1.4f),
            )
        }
        if (detail != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = detail,
                style = CrownTheme.type.caption1,
                color = colors.tertiaryLabel,
            )
        }
        if (showDivider) {
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator)
            )
        }
    }
}