package org.opencrown.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opencrown.app.ui.theme.LocalCrownColors

/**
 * The original Open Crown line-icon set.
 *
 * Every glyph is drawn from primitives in a 24x24 coordinate space. No SF
 * Symbol, Apple icon, or third-party path is used.
 */
enum class CrownGlyph {
    CROWN,
    WATCH,
    BLUETOOTH,
    BELL,
    FACE,
    HEART,
    APPS,
    SETTINGS,
    INFO,
    LOCK,
    WIFI,
    BATTERY,
    CHEVRON_RIGHT,
    CHEVRON_LEFT,
    CHECK,
    XMARK,
    SHIELD,
    SPARKLE,
    ANTENNA,
    MAGNIFY,
    REFRESH,
}

@Composable
fun CrownIcon(
    glyph: CrownGlyph,
    modifier: Modifier = Modifier,
    tint: Color = LocalCrownColors.current.label,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier = modifier.size(24.dp).then(semanticsModifier)) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, o(x1, y1), o(x2, y2), strokeWidth = 1.8f * unit, cap = StrokeCap.Round)
        }
        when (glyph) {
            CrownGlyph.CROWN -> {
                val path = Path().apply {
                    moveTo(4 * unit, 9 * unit)
                    lineTo(7 * unit, 17.5f * unit)
                    lineTo(12 * unit, 11.5f * unit)
                    lineTo(17 * unit, 17.5f * unit)
                    lineTo(20 * unit, 9 * unit)
                }
                drawPath(path, tint, style = stroke)
                line(6.5f, 20.5f, 17.5f, 20.5f)
            }
            CrownGlyph.WATCH -> {
                drawRoundRect(
                    color = tint,
                    topLeft = o(7f, 4f),
                    size = Size(10f * unit, 16f * unit),
                    cornerRadius = CornerRadius(3f * unit),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = o(10.5f, 1.5f),
                    size = Size(3f * unit, 2.5f * unit),
                    cornerRadius = CornerRadius(1f * unit),
                    style = stroke,
                )
                drawCircle(tint, radius = 3f * unit, center = o(12f, 12f), style = stroke)
                line(12f, 12f, 14f, 12f)
                line(12f, 12f, 12f, 10f)
            }
            CrownGlyph.BLUETOOTH -> {
                line(12f, 3f, 12f, 21f)
                line(12f, 3f, 17f, 8f)
                line(17f, 8f, 7f, 13f)
                line(7f, 11f, 17f, 16f)
                line(17f, 16f, 12f, 21f)
            }
            CrownGlyph.BELL -> {
                val path = Path().apply {
                    moveTo(6 * unit, 17 * unit)
                    lineTo(8 * unit, 14.5f * unit)
                    lineTo(8 * unit, 10.5f * unit)
                    cubicTo(8 * unit, 6.5f * unit, 9.8f * unit, 5 * unit, 12 * unit, 5 * unit)
                    cubicTo(14.2f * unit, 5 * unit, 16 * unit, 6.5f * unit, 16 * unit, 10.5f * unit)
                    lineTo(16 * unit, 14.5f * unit)
                    lineTo(18 * unit, 17 * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawCircle(tint, radius = 1.2f * unit, center = o(12f, 19.2f))
            }
            CrownGlyph.FACE -> {
                drawCircle(tint, radius = 8.5f * unit, center = o(12f, 12f), style = stroke)
                line(12f, 12f, 12f, 7.5f)
                line(12f, 12f, 15.5f, 13.5f)
                drawCircle(tint, radius = 0.9f * unit, center = o(12f, 12f))
            }
            CrownGlyph.HEART -> {
                val path = Path().apply {
                    moveTo(12 * unit, 20 * unit)
                    cubicTo(4 * unit, 14.5f * unit, 3.5f * unit, 9.5f * unit, 6.5f * unit, 6.8f * unit)
                    cubicTo(8.8f * unit, 4.8f * unit, 11.2f * unit, 6.2f * unit, 12 * unit, 8 * unit)
                    cubicTo(12.8f * unit, 6.2f * unit, 15.2f * unit, 4.8f * unit, 17.5f * unit, 6.8f * unit)
                    cubicTo(20.5f * unit, 9.5f * unit, 20 * unit, 14.5f * unit, 12 * unit, 20 * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            CrownGlyph.APPS -> {
                listOf(4f to 4f, 14f to 4f, 4f to 14f, 14f to 14f).forEach { (x, y) ->
                    drawRoundRect(
                        color = tint,
                        topLeft = o(x, y),
                        size = Size(6f * unit, 6f * unit),
                        cornerRadius = CornerRadius(1.6f * unit),
                        style = stroke,
                    )
                }
            }
            CrownGlyph.SETTINGS -> {
                drawCircle(tint, radius = 5.4f * unit, center = o(12f, 12f), style = stroke)
                drawCircle(tint, radius = 1.4f * unit, center = o(12f, 12f), style = stroke)
                for (i in 0 until 8) {
                    val angle = Math.toRadians(i * 45.0)
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    line(12f + cos * 5.4f, 12f + sin * 5.4f, 12f + cos * 8.4f, 12f + sin * 8.4f)
                }
            }
            CrownGlyph.INFO -> {
                drawCircle(tint, radius = 8.5f * unit, center = o(12f, 12f), style = stroke)
                line(12f, 11f, 12f, 16.5f)
                drawCircle(tint, radius = 0.9f * unit, center = o(12f, 7.8f))
            }
            CrownGlyph.LOCK -> {
                drawRoundRect(
                    color = tint,
                    topLeft = o(5.5f, 10.5f),
                    size = Size(13f * unit, 10f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = stroke,
                )
                val shackle = Path().apply {
                    moveTo(8.5f * unit, 10.5f * unit)
                    lineTo(8.5f * unit, 8f * unit)
                    cubicTo(8.5f * unit, 4.5f * unit, 15.5f * unit, 4.5f * unit, 15.5f * unit, 8f * unit)
                    lineTo(15.5f * unit, 10.5f * unit)
                }
                drawPath(shackle, tint, style = stroke)
                line(12f, 14f, 12f, 17f)
            }
            CrownGlyph.WIFI -> {
                for (i in 0 until 3) {
                    val r = (3.5f + i * 3.2f) * unit
                    drawArc(
                        color = tint,
                        startAngle = 225f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(12 * unit - r, 12 * unit - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = 1.8f * unit, cap = StrokeCap.Round),
                    )
                }
                drawCircle(tint, radius = 1f * unit, center = o(12f, 16.5f))
            }
            CrownGlyph.BATTERY -> {
                drawRoundRect(
                    color = tint,
                    topLeft = o(3f, 8f),
                    size = Size(17f * unit, 8f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = o(20.4f, 10.5f),
                    size = Size(1.6f * unit, 3f * unit),
                    cornerRadius = CornerRadius(0.6f * unit),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = o(5f, 10f),
                    size = Size(9f * unit, 4f * unit),
                    cornerRadius = CornerRadius(0.8f * unit),
                )
            }
            CrownGlyph.CHEVRON_RIGHT -> {
                line(9f, 5f, 16f, 12f)
                line(16f, 12f, 9f, 19f)
            }
            CrownGlyph.CHEVRON_LEFT -> {
                line(15f, 5f, 8f, 12f)
                line(8f, 12f, 15f, 19f)
            }
            CrownGlyph.CHECK -> {
                line(5f, 12.5f, 10f, 17.5f)
                line(10f, 17.5f, 19f, 7f)
            }
            CrownGlyph.XMARK -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            CrownGlyph.SHIELD -> {
                val path = Path().apply {
                    moveTo(12 * unit, 3.5f * unit)
                    lineTo(20 * unit, 6.5f * unit)
                    lineTo(20 * unit, 12 * unit)
                    cubicTo(20 * unit, 16.5f * unit, 16.5f * unit, 19.5f * unit, 12 * unit, 21f * unit)
                    cubicTo(7.5f * unit, 19.5f * unit, 4 * unit, 16.5f * unit, 4 * unit, 12 * unit)
                    lineTo(4 * unit, 6.5f * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
                line(8.5f, 12f, 11f, 14.5f)
                line(11f, 14.5f, 15.5f, 9.5f)
            }
            CrownGlyph.SPARKLE -> {
                val path = Path().apply {
                    moveTo(12 * unit, 3 * unit)
                    lineTo(13.8f * unit, 10.2f * unit)
                    lineTo(21 * unit, 12 * unit)
                    lineTo(13.8f * unit, 13.8f * unit)
                    lineTo(12 * unit, 21 * unit)
                    lineTo(10.2f * unit, 13.8f * unit)
                    lineTo(3 * unit, 12 * unit)
                    lineTo(10.2f * unit, 10.2f * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            CrownGlyph.MAGNIFY -> {
                drawCircle(tint, radius = 6.2f * unit, center = o(10.5f, 10.5f), style = stroke)
                line(15.2f, 15.2f, 20f, 20f)
            }
            CrownGlyph.REFRESH -> {
                drawArc(
                    color = tint,
                    startAngle = 40f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = o(4f, 4f),
                    size = Size(16f * unit, 16f * unit),
                    style = stroke,
                )
                val arrow = Path().apply {
                    moveTo(16.5f * unit, 3.5f * unit)
                    lineTo(20f * unit, 6.5f * unit)
                    lineTo(16f * unit, 8.5f * unit)
                }
                drawPath(arrow, tint, style = stroke)
            }
            CrownGlyph.ANTENNA -> {
                drawCircle(tint, radius = 1.6f * unit, center = o(12f, 12f))
                for (i in 0 until 2) {
                    val r = (4.5f + i * 3.2f) * unit
                    drawArc(
                        color = tint,
                        startAngle = 300f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(12 * unit - r, 12 * unit - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = 1.8f * unit, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = tint,
                        startAngle = 120f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(12 * unit - r, 12 * unit - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = 1.8f * unit, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

/** Shared drawing helper for feature previews. */
internal fun DrawScope.crownLine(color: Color, unit: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), 1.8f * unit, StrokeCap.Round)
}