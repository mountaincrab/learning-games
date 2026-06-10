package com.mountaincrab.learninggames.geometry

import kotlin.js.JsExport

/** A 2D point/vector in stage pixels. The platforms convert to their own types
 * (Compose `Offset` on Android, plain `{x, y}` usage on the web canvas). */
@JsExport
class Vec2(val x: Float, val y: Float)

/**
 * One renderer-neutral path step. Shapes are described as a list of these and
 * interpreted into a Compose `Path` on Android and a `Path2D` on the web, so the
 * geometry itself lives in one place.
 *
 * Meaning of [a]..[f] per [op]:
 * - [OP_MOVE]/[OP_LINE]: `a,b` = x,y
 * - [OP_CUBIC]: `a,b` / `c,d` = control points, `e,f` = end point
 * - [OP_ELLIPSE]: `a,b` = centre, `c,d` = x/y radius (a full closed ellipse)
 * - [OP_ARC]: `a,b` = centre, `c` = radius, `d` = start angle°, `e` = sweep°
 * - [OP_RECT]: `a,b` = top-left, `c,d` = width,height
 * - [OP_CLOSE]: no args
 */
@JsExport
class PathCommand internal constructor(
    val op: String,
    val a: Float = 0f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 0f,
    val e: Float = 0f,
    val f: Float = 0f,
)

@JsExport
const val OP_MOVE = "move"

@JsExport
const val OP_LINE = "line"

@JsExport
const val OP_CUBIC = "cubic"

@JsExport
const val OP_ELLIPSE = "ellipse"

@JsExport
const val OP_ARC = "arc"

@JsExport
const val OP_RECT = "rect"

@JsExport
const val OP_CLOSE = "close"

internal fun moveTo(x: Float, y: Float) = PathCommand(OP_MOVE, x, y)
internal fun lineTo(x: Float, y: Float) = PathCommand(OP_LINE, x, y)
internal fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
    PathCommand(OP_CUBIC, x1, y1, x2, y2, x3, y3)
internal fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) = PathCommand(OP_ELLIPSE, cx, cy, rx, ry)
internal fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) =
    PathCommand(OP_ARC, cx, cy, r, startDeg, sweepDeg)
internal fun rect(x: Float, y: Float, w: Float, h: Float) = PathCommand(OP_RECT, x, y, w, h)
internal fun closePath() = PathCommand(OP_CLOSE)
