package com.mountaincrab.learninggames.game.shapegame

import com.mountaincrab.learninggames.geometry.Vec2
import kotlin.js.JsExport
import kotlin.math.min

/** Geometry for the upside-down hat, the shape outlines and the piece tray,
 * computed once from the stage size (px). The hat's mouth (the hole the shapes
 * come from) sits at the top; the closed crown points down. */
@JsExport
class HatLayout internal constructor(
    val cx: Float,
    val openingCenter: Vec2,
    val openingW: Float,
    val openingH: Float,
    val brimW: Float,
    val brimH: Float,
    val bodyTopY: Float,
    val bodyBottomY: Float,
    val bodyTopHalf: Float,
    val bodyBotHalf: Float,
    val bandY: Float,
    val bandH: Float,
    val slotCenters: Array<Vec2>,
    val slotSize: Float,
    val trayHomes: Array<Vec2>,
    val pieceSize: Float,
    val wandScale: Float,
)

private const val GRID_ROWS = 4
private const val GRID_COLS = 3

@JsExport
fun computeHatLayout(w: Float, h: Float): HatLayout {
    val cx = w * 0.34f
    val m = min(w, h)
    val slotSize = m * 0.10f
    val pieceSize = m * 0.12f

    // Upside-down hat: wide mouth at the top, narrowing to the closed crown below.
    val bodyTopY = h * 0.22f
    val bodyBottomY = h * 0.86f
    val bodyTopHalf = w * 0.21f
    val bodyBotHalf = w * 0.165f

    // 4x3 grid of outlines, kept inside the narrowest (bottom) part of the body.
    val gridTop = h * 0.34f
    val gridBottom = h * 0.78f
    val gridHalf = bodyBotHalf * 0.74f
    val centers = Array(GRID_ROWS * GRID_COLS) { i ->
        val r = i / GRID_COLS
        val c = i % GRID_COLS
        val fx = c.toFloat() / (GRID_COLS - 1)
        val fy = r.toFloat() / (GRID_ROWS - 1)
        Vec2(
            (cx - gridHalf) + (gridHalf * 2f) * fx,
            gridTop + (gridBottom - gridTop) * fy,
        )
    }

    // Tray: three pieces stacked vertically on the right, centred.
    val trayX = w * 0.86f
    val gap = pieceSize * 1.55f
    val firstCy = h * 0.5f - gap
    val homes = Array(ShapeGameState.TRAY_SIZE) { i ->
        Vec2(trayX - pieceSize / 2f, (firstCy + gap * i) - pieceSize / 2f)
    }

    return HatLayout(
        cx = cx,
        openingCenter = Vec2(cx, bodyTopY),
        openingW = bodyTopHalf * 2f * 0.86f,
        openingH = h * 0.07f,
        brimW = bodyTopHalf * 2f * 1.25f,
        brimH = h * 0.085f,
        bodyTopY = bodyTopY,
        bodyBottomY = bodyBottomY,
        bodyTopHalf = bodyTopHalf,
        bodyBotHalf = bodyBotHalf,
        bandY = bodyTopY + (bodyBottomY - bodyTopY) * 0.14f,
        bandH = h * 0.05f,
        slotCenters = centers,
        slotSize = slotSize,
        trayHomes = homes,
        pieceSize = pieceSize,
        wandScale = min(h * 0.45f, w * 0.22f),
    )
}
