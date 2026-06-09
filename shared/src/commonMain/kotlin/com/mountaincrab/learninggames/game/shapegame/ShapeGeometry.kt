package com.mountaincrab.learninggames.game.shapegame

import com.mountaincrab.learninggames.geometry.PathCommand
import com.mountaincrab.learninggames.geometry.arc
import com.mountaincrab.learninggames.geometry.closePath
import com.mountaincrab.learninggames.geometry.cubicTo
import com.mountaincrab.learninggames.geometry.ellipse
import com.mountaincrab.learninggames.geometry.lineTo
import com.mountaincrab.learninggames.geometry.moveTo
import com.mountaincrab.learninggames.geometry.rect
import kotlin.js.JsExport
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Builds the outline of each [ShapeType], centered within a `w`×`h` box, as
 * renderer-neutral [PathCommand]s. The same commands are interpreted into a Compose
 * `Path` on Android and a `Path2D` on the web, used both for the empty outline
 * (stroked) and the filled piece.
 */
fun pathCommandsFor(type: ShapeType, w: Float, h: Float): Array<PathCommand> {
    val cx = w / 2f
    val cy = h / 2f
    val r = min(w, h) / 2f
    val cmds = ArrayList<PathCommand>()
    when (type) {
        ShapeType.CIRCLE -> cmds += ellipse(cx, cy, r, r)
        ShapeType.SQUARE -> {
            val s = r * 1.6f
            cmds += rect(cx - s / 2, cy - s / 2, s, s)
        }
        ShapeType.RECTANGLE -> cmds += rect(cx - w * 0.42f, cy - h * 0.28f, w * 0.84f, h * 0.56f)
        ShapeType.TRIANGLE -> polygon(cmds, cx, cy, r, sides = 3, rotationDeg = -90f)
        ShapeType.PENTAGON -> polygon(cmds, cx, cy, r, sides = 5, rotationDeg = -90f)
        ShapeType.DIAMOND -> {
            cmds += moveTo(cx, cy - r)
            cmds += lineTo(cx + r * 0.8f, cy)
            cmds += lineTo(cx, cy + r)
            cmds += lineTo(cx - r * 0.8f, cy)
            cmds += closePath()
        }
        ShapeType.DOME -> {
            // Flat-bottomed arch: semicircle top on a short rectangular base.
            val left = cx - r
            val right = cx + r
            val baseTop = cy + r * 0.4f
            cmds += moveTo(left, baseTop)
            cmds += lineTo(left, cy)
            cmds += arc(cx, cy, r, startDeg = 180f, sweepDeg = 180f)
            cmds += lineTo(right, baseTop)
            cmds += closePath()
        }
        ShapeType.STAR -> star(cmds, cx, cy, outer = r, inner = r * 0.45f, points = 5, rotationDeg = -90f)
        ShapeType.HEXAGON -> polygon(cmds, cx, cy, r, sides = 6, rotationDeg = 0f)
        ShapeType.OVAL -> cmds += ellipse(cx, cy, r, r * 0.68f)
        ShapeType.HEART -> {
            cmds += moveTo(cx, cy + r * 0.8f)
            cmds += cubicTo(cx - r * 1.1f, cy - r * 0.2f, cx - r * 0.5f, cy - r * 1.0f, cx, cy - r * 0.3f)
            cmds += cubicTo(cx + r * 0.5f, cy - r * 1.0f, cx + r * 1.1f, cy - r * 0.2f, cx, cy + r * 0.8f)
            cmds += closePath()
        }
        ShapeType.CROSS -> {
            val a = r * 0.34f   // arm half-width
            val b = r * 0.95f   // arm half-length
            cmds += moveTo(cx - a, cy - b)
            cmds += lineTo(cx + a, cy - b)
            cmds += lineTo(cx + a, cy - a)
            cmds += lineTo(cx + b, cy - a)
            cmds += lineTo(cx + b, cy + a)
            cmds += lineTo(cx + a, cy + a)
            cmds += lineTo(cx + a, cy + b)
            cmds += lineTo(cx - a, cy + b)
            cmds += lineTo(cx - a, cy + a)
            cmds += lineTo(cx - b, cy + a)
            cmds += lineTo(cx - b, cy - a)
            cmds += lineTo(cx - a, cy - a)
            cmds += closePath()
        }
    }
    return cmds.toTypedArray()
}

/** [pathCommandsFor] keyed by shape name, for the web side (enums aren't exportable). */
@JsExport
fun shapePathCommands(typeName: String, w: Float, h: Float): Array<PathCommand> =
    pathCommandsFor(ShapeType.valueOf(typeName), w, h)

private fun polygon(cmds: MutableList<PathCommand>, cx: Float, cy: Float, r: Float, sides: Int, rotationDeg: Float) {
    val rot = rotationDeg * PI.toFloat() / 180f
    for (i in 0 until sides) {
        val a = rot + 2f * PI.toFloat() * i / sides
        val x = cx + r * cos(a)
        val y = cy + r * sin(a)
        cmds += if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    cmds += closePath()
}

private fun star(
    cmds: MutableList<PathCommand>,
    cx: Float,
    cy: Float,
    outer: Float,
    inner: Float,
    points: Int,
    rotationDeg: Float,
) {
    val rot = rotationDeg * PI.toFloat() / 180f
    val steps = points * 2
    for (i in 0 until steps) {
        val rad = if (i % 2 == 0) outer else inner
        val a = rot + PI.toFloat() * i / points
        val x = cx + rad * cos(a)
        val y = cy + rad * sin(a)
        cmds += if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    cmds += closePath()
}
