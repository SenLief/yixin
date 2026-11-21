package cn.yixin.todo

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal enum class Glyph { Brand, Stack, Draw, Palette, Sliders, Edit, Expand, Clock, Play, Archive, Calendar, Pin, PinOff, Close, Up }

@Composable
internal fun HomeGlyph(glyph: Glyph, color: Color, modifier: Modifier, description: String? = null) {
    val paper = LocalYixinColors.current.background
    val accessible = if (description == null) modifier else modifier.semantics { contentDescription = description }
    Canvas(accessible) {
        val stroke = Stroke(width = 1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun outline(build: Path.() -> Unit) = drawPath(Path().apply(build), color, style = stroke)
            fun line(x: Float, y: Float, toX: Float, toY: Float) =
                drawLine(color, Offset(x, y), Offset(toX, toY), stroke.width, StrokeCap.Round)
            when (glyph) {
                Glyph.Brand -> {
                    val corner = CornerRadius(3f)
                    val bounds = Size(15f, 19f)
                    rotate(-12f, Offset(10f, 12f)) {
                        drawRoundRect(color, Offset(3f, 2.5f), bounds, corner, style = Stroke(1.05f))
                    }
                    rotate(7f, Offset(13f, 13f)) {
                        drawRoundRect(paper, Offset(6.5f, 3.7f), bounds, corner)
                        drawRoundRect(color, Offset(6.5f, 3.7f), bounds, corner, style = Stroke(1.05f))
                    }
                }
                Glyph.Stack -> outline {
                    moveTo(2f, 9f)
                    lineTo(12f, 3.5f)
                    lineTo(22f, 9f)
                    lineTo(12f, 14.5f)
                    close()
                    moveTo(2f, 14.5f)
                    lineTo(12f, 20f)
                    lineTo(22f, 14.5f)
                }
                Glyph.Draw -> {
                    outline {
                        moveTo(11f, 2f)
                        lineTo(13.4f, 8.6f)
                        lineTo(20f, 11f)
                        lineTo(13.4f, 13.4f)
                        lineTo(11f, 20f)
                        lineTo(8.6f, 13.4f)
                        lineTo(2f, 11f)
                        lineTo(8.6f, 8.6f)
                        close()
                    }
                    line(19f, 2f, 19f, 8f)
                    line(16f, 5f, 22f, 5f)
                    line(19f, 16f, 19f, 22f)
                    line(16f, 19f, 22f, 19f)
                }
                Glyph.Palette -> {
                    outline {
                        moveTo(12f, 21f)
                        cubicTo(6.6f, 21f, 3f, 17f, 3f, 12f)
                        cubicTo(3f, 6.5f, 7f, 3f, 12f, 3f)
                        cubicTo(17.5f, 3f, 21f, 6.5f, 21f, 11f)
                        cubicTo(21f, 14f, 18.9f, 16f, 16.4f, 16f)
                        lineTo(15.1f, 16f)
                        cubicTo(13f, 16f, 12.8f, 17.5f, 13.7f, 18.6f)
                        cubicTo(14.6f, 19.9f, 13.7f, 21f, 12f, 21f)
                        close()
                    }
                    listOf(Offset(8f, 9.5f), Offset(12f, 7f), Offset(16.5f, 9f), Offset(7f, 14f))
                        .forEach { drawCircle(color, 1.05f, it) }
                }
                Glyph.Sliders -> {
                    line(3f, 7f, 10f, 7f)
                    line(10f, 4.5f, 10f, 9.5f)
                    line(10f, 7f, 21f, 7f)
                    line(3f, 17f, 15f, 17f)
                    line(15f, 14.5f, 15f, 19.5f)
                    line(15f, 17f, 21f, 17f)
                }
                Glyph.Edit -> {
                    outline {
                        moveTo(4f, 20f)
                        lineTo(5.2f, 15.3f)
                        lineTo(16.4f, 4.1f)
                        quadraticTo(17.7f, 2.8f, 19.1f, 4.1f)
                        lineTo(20f, 5f)
                        quadraticTo(21.3f, 6.4f, 20f, 7.7f)
                        lineTo(8.8f, 19f)
                        close()
                    }
                    line(14.7f, 5.8f, 18.3f, 9.4f)
                }
                Glyph.Expand -> {
                    line(6.5f, 17.5f, 17.5f, 6.5f)
                    outline {
                        moveTo(8f, 6.5f)
                        lineTo(17.5f, 6.5f)
                        lineTo(17.5f, 16f)
                    }
                }
                Glyph.Clock -> {
                    drawCircle(color, 8.5f, Offset(12f, 12f), style = stroke)
                    outline {
                        moveTo(12f, 7f)
                        lineTo(12f, 12f)
                        lineTo(15f, 14f)
                    }
                }
                Glyph.Play -> outline { moveTo(7f, 4.5f)
                lineTo(19f, 12f)
                lineTo(7f, 19.5f)
                close() }
                Glyph.Archive -> {
                    drawRoundRect(color, Offset(3f, 4.5f), Size(18f, 4.5f), CornerRadius(1.25f), style = stroke)
                    outline {
                        moveTo(5f, 9f)
                        lineTo(5f, 19f)
                        quadraticTo(5f, 20f, 6f, 20f)
                        lineTo(18f, 20f)
                        quadraticTo(19f, 20f, 19f, 19f)
                        lineTo(19f, 9f)
                    }
                    line(10f, 13f, 14f, 13f)
                }
                Glyph.Calendar -> {
                    drawRoundRect(color, Offset(3.5f, 5f), Size(17f, 16f), CornerRadius(2f), style = stroke)
                    line(8f, 3f, 8f, 7f)
                    line(16f, 3f, 16f, 7f)
                    line(3.5f, 9.5f, 20.5f, 9.5f)
                    drawCircle(color, 1f, Offset(8.5f, 14f))
                    drawCircle(color, 1f, Offset(12f, 14f))
                    drawCircle(color, 1f, Offset(15.5f, 14f))
                }
                Glyph.Close -> {
                    line(5f, 5f, 19f, 19f)
                    line(19f, 5f, 5f, 19f)
                }
                Glyph.Up -> {
                    line(12f, 20f, 12f, 4f)
                    outline {
                        moveTo(6f, 10f)
                        lineTo(12f, 4f)
                        lineTo(18f, 10f)
                    }
                }
                Glyph.PinOff -> {
                    outline {
                        moveTo(9f, 3f)
                        lineTo(16f, 3f)
                        lineTo(15f, 10f)
                        lineTo(18f, 14f)
                        moveTo(7f, 9f)
                        lineTo(6f, 14f)
                        lineTo(12f, 14f)
                        lineTo(12f, 21f)
                    }
                    line(3f, 3f, 21f, 21f)
                }
                Glyph.Pin -> {
                    outline {
                        moveTo(8f, 3f)
                        lineTo(16f, 3f)
                        lineTo(15f, 10f)
                        lineTo(18f, 14f)
                        lineTo(6f, 14f)
                        lineTo(9f, 10f)
                        close()
                    }
                    line(12f, 14f, 12f, 21f)
                }
            }
        }
    }
}
