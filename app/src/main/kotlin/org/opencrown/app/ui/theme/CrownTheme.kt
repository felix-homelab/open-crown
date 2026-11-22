package org.opencrown.app.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.animation.ValueAnimator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

val LocalCrownColors = staticCompositionLocalOf { CrownColors.Light }
val LocalCrownTypography = staticCompositionLocalOf { CrownTypography.Default }
val LocalCrownSpacing = staticCompositionLocalOf { CrownSpacing.Default }
val LocalCrownShapes = staticCompositionLocalOf { CrownShapes.Default }
val LocalCrownMotion = staticCompositionLocalOf { CrownMotion.Default }
val LocalCrownDurations = staticCompositionLocalOf { CrownDurations.Default }
val LocalCrownHaptics = staticCompositionLocalOf { CrownHaptics.NoOp }
val LocalCrownReducedMotion = staticCompositionLocalOf { false }

/**
 * Read-only access point for the design tokens.
 *
 * Components read [CrownTheme.colors], [CrownTheme.type] and the other properties inside a
 * composition. The values are supplied by [CrownTheme]; the object exists so call sites do not
 * have to import a composition local for every token family.
 */
object CrownTheme {
    val colors: CrownColors
        @Composable get() = LocalCrownColors.current

    val type: CrownTypography
        @Composable get() = LocalCrownTypography.current

    val spacing: CrownSpacing
        @Composable get() = LocalCrownSpacing.current

    val shapes: CrownShapes
        @Composable get() = LocalCrownShapes.current

    val motion: CrownMotion
        @Composable get() = LocalCrownMotion.current

    val durations: CrownDurations
        @Composable get() = LocalCrownDurations.current

    val haptics: CrownHaptics
        @Composable get() = LocalCrownHaptics.current

    val reducedMotion: Boolean
        @Composable get() = LocalCrownReducedMotion.current
}

private fun CrownTypography.toMaterial(): Typography = Typography(
    displayLarge = largeTitle,
    displayMedium = title1,
    displaySmall = title2,
    headlineLarge = title1,
    headlineMedium = title2,
    headlineSmall = title3,
    titleLarge = title3,
    titleMedium = headline,
    titleSmall = subheadline,
    bodyLarge = body,
    bodyMedium = callout,
    bodySmall = footnote,
    labelLarge = headline,
    labelMedium = subheadline,
    labelSmall = caption2,
)

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var animatorsEnabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return !animatorsEnabled
}

@Composable
private fun rememberHaptics(): CrownHaptics {
    val compose: HapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(compose, view) {
        CrownHaptics(
            composeFeedback = { type -> compose.performHapticFeedback(type) },
            viewFeedback = { constant -> view.performHapticFeedback(constant) },
        )
    }
}

@Composable
fun CrownTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) CrownColors.Dark else CrownColors.Light
    val type = CrownTypography.Default
    val spacing = CrownSpacing.Default
    val shapes = CrownShapes.Default
    val reducedMotion = rememberReducedMotion()
    val motion = remember(reducedMotion) { CrownMotion(reducedMotion = reducedMotion) }
    val durations = remember(reducedMotion) { CrownDurations(reducedMotion = reducedMotion) }
    val haptics = rememberHaptics()

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.tint,
            onPrimary = colors.onTint,
            background = colors.groupedBackground,
            onBackground = colors.label,
            surface = colors.cardBackground,
            onSurface = colors.label,
            surfaceVariant = colors.fill,
            onSurfaceVariant = colors.secondaryLabel,
            error = colors.destructive,
            onError = Color.White,
            outline = colors.separator,
        )
    } else {
        lightColorScheme(
            primary = colors.tint,
            onPrimary = colors.onTint,
            background = colors.groupedBackground,
            onBackground = colors.label,
            surface = colors.cardBackground,
            onSurface = colors.label,
            surfaceVariant = colors.fill,
            onSurfaceVariant = colors.secondaryLabel,
            error = colors.destructive,
            onError = Color.White,
            outline = colors.separator,
        )
    }

    CompositionLocalProvider(
        LocalCrownColors provides colors,
        LocalCrownTypography provides type,
        LocalCrownSpacing provides spacing,
        LocalCrownShapes provides shapes,
        LocalCrownMotion provides motion,
        LocalCrownDurations provides durations,
        LocalCrownHaptics provides haptics,
        LocalCrownReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = type.toMaterial(),
            content = content,
        )
    }
}
