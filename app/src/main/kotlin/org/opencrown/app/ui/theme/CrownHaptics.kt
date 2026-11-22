package org.opencrown.app.ui.theme

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Haptic vocabulary for Open Crown.
 *
 * The helper combines Compose's [androidx.compose.ui.hapticfeedback.HapticFeedback] with the
 * richer [android.view.View] constants, so components can express a tap, a selection tick, a
 * toggle, success, warning and error without requesting the vibration permission. All feedback is
 * requested through the view hierarchy and therefore follows the user's system haptics setting.
 */
@Stable
class CrownHaptics internal constructor(
    private val composeFeedback: (HapticFeedbackType) -> Unit,
    private val viewFeedback: (Int) -> Boolean,
) {
    /** A light confirmation for ordinary taps. */
    fun tap() {
        if (!viewFeedback(HapticFeedbackConstants.KEYBOARD_TAP)) {
            composeFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** A precise tick for moving between segments or values. */
    fun selection() {
        if (!viewFeedback(HapticFeedbackConstants.CLOCK_TICK)) {
            composeFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** A toggle confirmation that distinguishes the new on/off state. */
    fun toggle(on: Boolean) {
        composeFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    }

    /** A firm confirmation for a completed action. */
    fun success() {
        if (!viewFeedback(HapticFeedbackConstants.CONFIRM)) {
            composeFeedback(HapticFeedbackType.Confirm)
        }
    }

    /** A non-fatal warning. */
    fun warning() {
        if (!viewFeedback(HapticFeedbackConstants.LONG_PRESS)) {
            composeFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** A rejected or failed action. */
    fun error() {
        if (!viewFeedback(HapticFeedbackConstants.REJECT)) {
            composeFeedback(HapticFeedbackType.Reject)
        }
    }

    internal companion object {
        /** No-op fallback used outside a [CrownTheme]. */
        val NoOp: CrownHaptics = CrownHaptics(
            composeFeedback = {},
            viewFeedback = { false },
        )
    }
}
