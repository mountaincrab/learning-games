package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mountaincrab.learninggames.geometry.OP_ARC
import com.mountaincrab.learninggames.geometry.OP_CLOSE
import com.mountaincrab.learninggames.geometry.OP_CUBIC
import com.mountaincrab.learninggames.geometry.OP_ELLIPSE
import com.mountaincrab.learninggames.geometry.OP_LINE
import com.mountaincrab.learninggames.geometry.OP_MOVE
import com.mountaincrab.learninggames.geometry.OP_RECT
import kotlin.math.min

/**
 * Interprets the shared, renderer-neutral shape geometry (see `ShapeGeometry` in the
 * `shared` module) into Compose [Path]s. The same path is used for the empty outline
 * (stroked) and the filled piece.
 */
object ShapeRenderer {

    fun pathFor(type: ShapeType, size: Size): Path {
        val path = Path()
        for (cmd in pathCommandsFor(type, size.width, size.height)) {
            when (cmd.op) {
                OP_MOVE -> path.moveTo(cmd.a, cmd.b)
                OP_LINE -> path.lineTo(cmd.a, cmd.b)
                OP_CUBIC -> path.cubicTo(cmd.a, cmd.b, cmd.c, cmd.d, cmd.e, cmd.f)
                OP_ELLIPSE -> path.addOval(
                    Rect(Offset(cmd.a - cmd.c, cmd.b - cmd.d), Size(cmd.c * 2, cmd.d * 2))
                )
                OP_ARC -> path.arcTo(
                    rect = Rect(Offset(cmd.a - cmd.c, cmd.b - cmd.c), Size(cmd.c * 2, cmd.c * 2)),
                    startAngleDegrees = cmd.d,
                    sweepAngleDegrees = cmd.e,
                    forceMoveTo = false,
                )
                OP_RECT -> path.addRect(Rect(Offset(cmd.a, cmd.b), Size(cmd.c, cmd.d)))
                OP_CLOSE -> path.close()
            }
        }
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
