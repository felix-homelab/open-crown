package org.opencrown.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type ramp for Open Crown.
 *
 * Every size is expressed in `sp`, so the platform font scale and per-app Dynamic Type setting
 * apply automatically. Line heights are generous enough for multi-line body copy, and tracking
 * tightens as type gets larger so display text keeps its compact, precise rhythm.
 */
@Immutable
data class CrownTypography(
    val largeTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.4).sp,
    ),
    val title1: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    ),
    val title2: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    val title3: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    val callout: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp,
    ),
    val subheadline: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    val footnote: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    val caption1: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    val caption2: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.1.sp,
    ),
) {
    companion object {
        /** Default ramp used when no theme override is supplied. */
        val Default: CrownTypography = CrownTypography()
    }
}

/**
 * Static type ramp for code that cannot read a composition local (previews, measurement).
 * Inside UI prefer [CrownTheme.typography], which can be overridden by the app theme.
 */
val CrownType: CrownTypography = CrownTypography.Default

/** Named entries in the [CrownTypography] ramp. */
enum class CrownTextRole {
    LargeTitle,
    Title1,
    Title2,
    Title3,
    Headline,
    Body,
    Callout,
    Subheadline,
    Footnote,
    Caption1,
    Caption2,
}

/** Resolves a [CrownTextRole] against this ramp. */
fun CrownTypography.style(role: CrownTextRole): TextStyle = when (role) {
    CrownTextRole.LargeTitle -> largeTitle
    CrownTextRole.Title1 -> title1
    CrownTextRole.Title2 -> title2
    CrownTextRole.Title3 -> title3
    CrownTextRole.Headline -> headline
    CrownTextRole.Body -> body
    CrownTextRole.Callout -> callout
    CrownTextRole.Subheadline -> subheadline
    CrownTextRole.Footnote -> footnote
    CrownTextRole.Caption1 -> caption1
    CrownTextRole.Caption2 -> caption2
}

/** Returns this style with [weight] applied, keeping size, tracking and line height intact. */
fun TextStyle.crownEmphasis(weight: FontWeight = FontWeight.SemiBold): TextStyle =
    copy(fontWeight = weight)
