package org.opencrown.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opencrown.app.ui.theme.CrownTheme

/**
 * The Open Crown mark: a side-on digital crown. Original geometry, retained
 * from the first app shell; it is not an Apple Watch silhouette or Apple logo.
 */
@Composable
fun CrownBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    tint: Color? = null,
) {
    val color = tint ?: CrownTheme.colors.label
    Canvas(modifier.size(size)) {
        val cx = this.size.width * 0.45f
        val cy = this.size.height * 0.50f
        val outer = this.size.width * 0.29f
        val stemTop = cy - this.size.height * 0.055f
        drawRoundRect(
            color = color,
            topLeft = Offset(cx + outer - this.size.width * 0.025f, stemTop),
            size = Size(this.size.width * 0.20f, this.size.height * 0.11f),
            cornerRadius = CornerRadius(this.size.height * 0.055f),
        )
        drawCircle(
            color = color,
            radius = outer,
            center = Offset(cx, cy),
            style = Stroke(width = this.size.width * 0.052f),
        )
        drawCircle(
            color = color,
            radius = outer * 0.57f,
            center = Offset(cx, cy),
            style = Stroke(width = this.size.width * 0.042f),
        )
        val tickInner = outer * 0.66f
        val tickOuter = outer * 0.94f
        for (i in 0 until 12) {
            val angle = i * Math.PI / 6.0
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            drawLine(
                color = color,
                start = Offset(cx + cos * tickInner, cy + sin * tickInner),
                end = Offset(cx + cos * tickInner, cy + sin * tickOuter),
                strokeWidth = this.size.width * 0.028f,
                cap = StrokeCap.Round,
            )
        }
    }
}
