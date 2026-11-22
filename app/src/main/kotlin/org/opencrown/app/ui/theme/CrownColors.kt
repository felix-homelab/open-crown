package org.opencrown.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for the Open Crown design system.
 *
 * Components never reach for raw palette values; they read a token from [CrownTheme.colors].
 * Light and dark palettes are both first class. Text is intentionally off-black / off-white so
 * large surfaces stay soft, while the accents remain restrained and functional.
 *
 * @property isDark whether this palette belongs to the dark appearance. Components use it for
 *   tonal decisions that cannot be expressed by one token (for example tinted pill foregrounds).
 */
@Immutable
data class CrownColors(
    val isDark: Boolean = false,
    /** Primary text, icons and high-emphasis glyphs. */
    val label: Color = Color(0xFF1D1D1F),
    /** Supporting copy, values and secondary glyphs. */
    val secondaryLabel: Color = Color(0xFF6E6E73),
    /** De-emphasized glyphs and placeholders. */
    val tertiaryLabel: Color = Color(0xFF8E8E93),
    /** Disabled / decorative glyphs and fields. */
    val quaternaryLabel: Color = Color(0xFFC7C7CC),
    /** Hairline separators inside cards. */
    val separator: Color = Color(0xFFD1D1D6),
    /** Page-level background behind inset groups. */
    val groupedBackground: Color = Color(0xFFF2F2F7),
    /** Card and sheet surface. */
    val cardBackground: Color = Color(0xFFFFFFFF),
    /** Navigation bar fill that fades in as a large title collapses. */
    val barBackground: Color = Color(0xFFF7F7FA),
    /** Interactive accent. */
    val tint: Color = Color(0xFF007AFF),
    /** Foreground on a filled tint background. */
    val onTint: Color = Color(0xFFFFFFFF),
    val destructive: Color = Color(0xFFFF3B30),
    /** Destructive text on a light surface; the fill token stays Apple-red. */
    val destructiveText: Color = Color(0xFFC4271C),
    val success: Color = Color(0xFF34C759),
    val warning: Color = Color(0xFFFF9500),
    val info: Color = Color(0xFF007AFF),
    /** Control and disabled fills. */
    val fill: Color = Color(0xFFE9E9ED),
    /** A slightly stronger fill for selected or grouped secondary surfaces. */
    val fillStrong: Color = Color(0xFFDCDCE1),
    /** Transient highlight behind a pressed row or control. */
    val rowHighlight: Color = Color(0x0A000000),
    /** Off track for the switch. */
    val switchTrack: Color = Color(0xFFE5E5EA),
    /** Track behind a segmented control. */
    val segmentedTrack: Color = Color(0x1A767680),
    /** Dimming behind a modal sheet. */
    val scrim: Color = Color(0x55000000),
    /** Soft shadow color used for thumbnails and floating controls. */
    val shadow: Color = Color(0x1A000000),
    /** Shimmer placeholders. */
    val skeletonBase: Color = Color(0xFFECECF0),
    val skeletonHighlight: Color = Color(0xFFF7F7FA),
) {
    companion object {
        /** Default light appearance. */
        val Light: CrownColors = CrownColors()

        /** Default dark appearance. */
        val Dark: CrownColors = CrownColors(
            isDark = true,
            label = Color(0xFFF5F5F7),
            secondaryLabel = Color(0xFFA1A1A6),
            tertiaryLabel = Color(0xFF8E8E93),
            quaternaryLabel = Color(0xFF545458),
            separator = Color(0xFF38383A),
            groupedBackground = Color(0xFF000000),
            cardBackground = Color(0xFF1C1C1E),
            barBackground = Color(0xFF0D0D0F),
            tint = Color(0xFF0A84FF),
            onTint = Color(0xFFFFFFFF),
            destructive = Color(0xFFFF453A),
            destructiveText = Color(0xFFFF6961),
            success = Color(0xFF30D158),
            warning = Color(0xFFFF9F0A),
            info = Color(0xFF0A84FF),
            fill = Color(0xFF2C2C2E),
            fillStrong = Color(0xFF3A3A3C),
            rowHighlight = Color(0x14FFFFFF),
            switchTrack = Color(0xFF39393D),
            segmentedTrack = Color(0x33767680),
            scrim = Color(0x99000000),
            shadow = Color(0x66000000),
            skeletonBase = Color(0xFF25252A),
            skeletonHighlight = Color(0xFF323238),
        )
    }
}