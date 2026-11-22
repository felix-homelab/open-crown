package org.opencrown.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opencrown.app.ui.theme.CrownTheme

enum class CrownButtonStyle { FILLED, TINTED, PLAIN, DESTRUCTIVE }

@Composable
fun CrownButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CrownButtonStyle = CrownButtonStyle.FILLED,
    enabled: Boolean = true,
    leadingGlyph: CrownGlyph? = null,
) {
    val colors = CrownTheme.colors
    val haptics = CrownTheme.haptics
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = CrownTheme.motion.press,
        label = "button-press",
    )
    val background = when (style) {
        CrownButtonStyle.FILLED, CrownButtonStyle.DESTRUCTIVE ->
            if (style == CrownButtonStyle.DESTRUCTIVE) colors.destructive else colors.tint
        CrownButtonStyle.TINTED -> colors.tint.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
        CrownButtonStyle.PLAIN -> Color.Transparent
    }
    val content = when (style) {
        CrownButtonStyle.FILLED -> colors.onTint
        CrownButtonStyle.DESTRUCTIVE -> Color.White
        CrownButtonStyle.TINTED, CrownButtonStyle.PLAIN -> colors.tint
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .height(50.dp)
            .clip(CrownTheme.shapes.control)
            .background(if (enabled) background else background.copy(alpha = 0.38f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.tap()
                onClick()
            }
            .clearAndSetSemantics {
                this.contentDescription = text
                this.role = Role.Button
                if (enabled) {
                    onClick(label = "Activate") {
                        onClick()
                        true
                    }
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (leadingGlyph != null) {
                CrownIcon(
                    leadingGlyph,
                    tint = if (enabled) content else content.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = CrownTheme.type.headline,
                color = if (enabled) content else content.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun CrownIconButton(
    glyph: CrownGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val resolvedTint = tint ?: CrownTheme.colors.tint
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CrownIcon(glyph, tint = resolvedTint, contentDescription = null)
    }
}

@Composable
fun CrownSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CrownTheme.colors
    val haptics = CrownTheme.haptics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CrownTheme.shapes.segment)
            .background(colors.segmentedTrack)
            .padding(2.dp)
            .semantics { selectableGroup() },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (selected) colors.cardBackground else Color.Transparent,
                animationSpec = tween(if (CrownTheme.reducedMotion) 1 else 180),
                label = "segment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(CrownTheme.shapes.segment)
                    .background(background)
                    .clickable(
                        role = Role.Tab,
                        onClick = {
                            if (index != selectedIndex) {
                                haptics.selection()
                                onSelect(index)
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = CrownTheme.type.footnote.copy(
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                        else androidx.compose.ui.text.font.FontWeight.Normal,
                    ),
                    color = if (selected) colors.label else colors.secondaryLabel,
                )
            }
        }
    }
}

@Composable
fun CrownEmptyState(
    glyph: CrownGlyph,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = CrownTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            CrownIcon(glyph, tint = colors.secondaryLabel, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = CrownTheme.type.title3, color = colors.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = CrownTheme.type.footnote,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            CrownButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.width(220.dp),
                style = CrownButtonStyle.TINTED,
            )
        }
    }
}

@Composable
fun CrownSkeleton(
    width: Dp? = null,
    height: Dp = 14.dp,
    shape: androidx.compose.ui.graphics.Shape? = null,
) {
    val colors = CrownTheme.colors
    val resolvedShape = shape ?: CrownTheme.shapes.skeleton
    val reduced = CrownTheme.reducedMotion
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced) 1 else 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-shimmer",
    )
    val widthModifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    Box(
        Modifier
            .then(widthModifier)
            .height(height)
            .clip(resolvedShape)
            .background(
                if (reduced) {
                    androidx.compose.ui.graphics.SolidColor(colors.skeletonBase)
                } else {
                    Brush.linearGradient(
                        colors = listOf(colors.skeletonBase, colors.skeletonHighlight, colors.skeletonBase),
                        start = Offset(0f, 0f),
                        end = Offset(600f * shimmer, 0f),
                    )
                }
            )
    )
}

@Composable
fun CrownProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = CrownTheme.colors
    val clamped = progress.coerceIn(0f, 1f)
    Box(modifier = modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = size.minDimension * 0.07f, cap = StrokeCap.Round)
            drawArc(
                color = colors.fill,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke.width / 2, stroke.width / 2),
                size = Size(size.width - stroke.width, size.height - stroke.width),
                style = stroke,
            )
            drawArc(
                color = colors.tint,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = Offset(stroke.width / 2, stroke.width / 2),
                size = Size(size.width - stroke.width, size.height - stroke.width),
                style = stroke,
            )
        }
        if (label != null) {
            Text(text = label, style = CrownTheme.type.title2, color = colors.label)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrownSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CrownTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        contentColor = colors.label,
        shape = CrownTheme.shapes.sheet,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(38.dp)
                    .height(5.dp)
                    .clip(CrownTheme.shapes.pill)
                    .background(colors.tertiaryLabel.copy(alpha = 0.45f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(text = title, style = CrownTheme.type.title3, color = colors.label)
            Spacer(Modifier.height(12.dp))
            content()
            if (primaryLabel != null && onPrimary != null) {
                Spacer(Modifier.height(20.dp))
                CrownButton(text = primaryLabel, onClick = onPrimary)
            }
            if (secondaryLabel != null && onSecondary != null) {
                Spacer(Modifier.height(8.dp))
                CrownButton(
                    text = secondaryLabel,
                    onClick = onSecondary,
                    style = CrownButtonStyle.PLAIN,
                )
            }
        }
    }
}