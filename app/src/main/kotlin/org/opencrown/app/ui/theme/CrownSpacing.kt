package org.opencrown.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout tokens on an 8pt grid.
 *
 * `xs` (4dp) is the only half-step and exists for optical alignment of glyphs inside fixed
 * controls. Screen margins, card padding, row minimum heights and touch targets are semantic
 * tokens so every component agrees on the rhythm.
 */
@Immutable
data class CrownSpacing(
    val hairline: Dp = 0.5.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val screenInset: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val rowMinHeight: Dp = 44.dp,
    val touchTarget: Dp = 44.dp,
    val buttonHeight: Dp = 50.dp,
    val iconSize: Dp = 24.dp,
    val leadingIconSize: Dp = 22.dp,
    val chevronSize: Dp = 16.dp,
) {
    companion object {
        /** Default spacing set. */
        val Default: CrownSpacing = CrownSpacing()
    }
}
