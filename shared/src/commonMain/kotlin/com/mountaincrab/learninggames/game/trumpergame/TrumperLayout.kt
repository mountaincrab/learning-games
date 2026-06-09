package com.mountaincrab.learninggames.game.trumpergame

import com.mountaincrab.learninggames.geometry.Vec2
import kotlin.js.JsExport
import kotlin.math.min

/** How much a bloated tummy swells relative to the resting body radius. */
@JsExport
const val BLOAT_SCALE = 1.7f

/** Per-character geometry, computed once from the stage size (px). [bases] are the
 * resting body centres (at scale 1); the feet stay planted while the tummy swells
 * upward. */
@JsExport
class TrumperLayout internal constructor(
    val bases: Array<Vec2>,
    val bodyR: Float,
    val groundY: Float,
)

@JsExport
fun computeTrumperLayout(w: Float, h: Float): TrumperLayout {
    val columnW = w / TrumperGameState.COUNT
    val bodyR = min(columnW * 0.30f, h * 0.18f)
    val groundY = h * 0.80f
    val bases = Array(TrumperGameState.COUNT) { i ->
        Vec2(columnW * (i + 0.5f), groundY - bodyR)
    }
    return TrumperLayout(bases = bases, bodyR = bodyR, groundY = groundY)
}

/** Current drawn body centre for a character, given its swell [scale]; the feet stay
 * planted on the ground while the belly grows upward. */
@JsExport
fun trumperBodyCenter(base: Vec2, bodyR: Float, scale: Float): Vec2 =
    Vec2(base.x, (base.y + bodyR) - bodyR * scale)
