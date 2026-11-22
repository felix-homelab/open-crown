package org.opencrown.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable

/**
 * Spring tokens for direct manipulation.
 *
 * The physical specs are deliberately quick and only lightly bouncy: touch feedback should feel
 * connected to the finger, not theatrical. When [reducedMotion] is true every spec collapses to a
 * snap so components that draw their own animation still honor the system animation setting.
 */
@Immutable
class CrownMotion(
    val reducedMotion: Boolean,
    val press: FiniteAnimationSpec<Float>,
    val selection: FiniteAnimationSpec<Float>,
    val emphasis: FiniteAnimationSpec<Float>,
    val progress: FiniteAnimationSpec<Float>,
) {
    /** Builds the standard spring set, or snaps when [reducedMotion] is requested. */
    constructor(reducedMotion: Boolean = false) : this(
        reducedMotion = reducedMotion,
        press = if (reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.72f, stiffness = 720f)
        },
        selection = if (reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.84f, stiffness = 420f)
        },
        emphasis = if (reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.80f, stiffness = 260f)
        },
        progress = if (reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = 1f, stiffness = 180f)
        },
    )

    companion object {
        /** Motion tokens using normal animation settings. */
        val Default: CrownMotion = CrownMotion()
    }
}

/**
 * Duration tokens for fade, color and layout transitions.
 *
 * All values become 1ms under reduced motion. Infinite effects such as the skeleton shimmer read
 * [CrownMotion.reducedMotion] and stop entirely rather than relying on a scaled duration.
 */
@Immutable
class CrownDurations(
    val reducedMotion: Boolean,
    val quick: Int,
    val standard: Int,
    val emphasized: Int,
    val shimmer: Int,
) {
    /** Builds the standard duration set, or near-zero values when reduced motion is requested. */
    constructor(reducedMotion: Boolean = false) : this(
        reducedMotion = reducedMotion,
        quick = if (reducedMotion) 1 else 120,
        standard = if (reducedMotion) 1 else 220,
        emphasized = if (reducedMotion) 1 else 340,
        shimmer = if (reducedMotion) 1 else 1500,
    )

    companion object {
        /** Duration tokens using normal animation settings. */
        val Default: CrownDurations = CrownDurations()
    }
}
