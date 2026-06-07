package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Builds [Path]s for each [ShapeType], centered within a box of the given [size].
 * The same path is used for the empty outline (stroked) and the filled piece.
 */
object ShapeRenderer {

    fun pathFor(type: ShapeType, size: Size): Path {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f
        return when (type) {
            ShapeType.CIRCLE -> Path().apply {
                addOval(Rect(Offset(cx - r, cy - r), Size(r * 2, r * 2)))
            }
            ShapeType.SQUARE -> Path().apply {
                val s = r * 1.6f
                addRect(Rect(Offset(cx - s / 2, cy - s / 2), Size(s, s)))
            }
            ShapeType.RECTANGLE -> Path().apply {
                addRect(Rect(Offset(cx - w * 0.42f, cy - h * 0.28f), Size(w * 0.84f, h * 0.56f)))
            }
            ShapeType.TRIANGLE -> polygon(cx, cy, r, sides = 3, rotationDeg = -90f)
            ShapeType.PENTAGON -> polygon(cx, cy, r, sides = 5, rotationDeg = -90f)
            ShapeType.DIAMOND -> Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.8f, cy)
                lineTo(cx, cy + r)
                lineTo(cx - r * 0.8f, cy)
                close()
            }
            ShapeType.DOME -> Path().apply {
                // Flat-bottomed arch: semicircle top on a short rectangular base.
                val left = cx - r
                val right = cx + r
                val baseTop = cy + r * 0.4f
                moveTo(left, baseTop)
                lineTo(left, cy)
                arcTo(
                    rect = Rect(Offset(left, cy - r), Size(r * 2, r * 2)),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                lineTo(right, baseTop)
                close()
            }
            ShapeType.STAR -> star(cx, cy, outer = r, inner = r * 0.45f, points = 5, rotationDeg = -90f)
        }
    }

    private fun polygon(cx: Float, cy: Float, r: Float, sides: Int, rotationDeg: Float): Path {
        val path = Path()
        val rot = rotationDeg * PI.toFloat() / 180f
        for (i in 0 until sides) {
            val a = rot + 2f * PI.toFloat() * i / sides
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun star(cx: Float, cy: Float, outer: Float, inner: Float, points: Int, rotationDeg: Float): Path {
        val path = Path()
        val rot = rotationDeg * PI.toFloat() / 180f
        val steps = points * 2
        for (i in 0 until steps) {
            val rad = if (i % 2 == 0) outer else inner
            val a = rot + PI.toFloat() * i / points
            val x = cx + rad * cos(a)
            val y = cy + rad * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** Draws an empty target outline. */
    fun DrawScope.drawOutline(type: ShapeType, size: Size, color: Color, strokeWidth: Float) {
        drawPath(pathFor(type, size), color = color, style = Stroke(width = strokeWidth))
    }

    /** Draws a solid filled shape (a draggable piece or a matched slot). */
    fun DrawScope.drawFilled(type: ShapeType, size: Size, color: Color) {
        val path = pathFor(type, size)
        drawPath(path, color = color)
        drawPath(path, color = Color.Black.copy(alpha = 0.18f), style = Stroke(width = size.minDimension * 0.04f))
    }

    private val Size.minDimension: Float get() = min(width, height)
}
