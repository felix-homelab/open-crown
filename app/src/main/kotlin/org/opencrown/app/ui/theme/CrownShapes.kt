package org.opencrown.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Corner-radius tokens.
 *
 * Cards and grouped lists share a 12dp radius so a screen reads as one material. Controls use the
 * same family, while sheets lift their top corners and pills stay fully round.
 */
@Immutable
data class CrownShapes(
    val card: Shape = RoundedCornerShape(12.dp),
    val group: Shape = RoundedCornerShape(12.dp),
    val row: Shape = RoundedCornerShape(10.dp),
    val control: Shape = RoundedCornerShape(12.dp),
    val segment: Shape = RoundedCornerShape(9.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    val pill: Shape = CircleShape,
    val skeleton: Shape = RoundedCornerShape(6.dp),
    val thumb: Shape = RoundedCornerShape(7.dp),
) {
    companion object {
        /** Default radius set. */
        val Default: CrownShapes = CrownShapes()
    }
}
