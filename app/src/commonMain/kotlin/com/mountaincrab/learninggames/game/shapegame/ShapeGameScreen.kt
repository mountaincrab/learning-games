package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mountaincrab.learninggames.game.shapegame.ShapeRenderer.drawFilled
import com.mountaincrab.learninggames.game.shapegame.ShapeRenderer.drawOutline
import com.mountaincrab.learninggames.ui.components.BackButton
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Geometry for the upside-down hat, the shape outlines and the piece tray,
 * computed once from the stage size (px). The hat's mouth (the hole the shapes
 * come from) sits at the top; the closed crown points down. */
private data class HatLayout(
    val cx: Float,
    val openingCenter: Offset,
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
    val slotCenters: List<Offset>,
    val slotSize: Float,
    val trayHomes: List<Offset>,
    val pieceSize: Float,
    val wandScale: Float,
)

private const val GRID_ROWS = 4
private const val GRID_COLS = 3

private fun computeHatLayout(w: Float, h: Float): HatLayout {
    val cx = w * 0.34f
    val m = minOf(w, h)
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
    val centers = ArrayList<Offset>(GRID_ROWS * GRID_COLS)
    for (i in 0 until GRID_ROWS * GRID_COLS) {
        val r = i / GRID_COLS
        val c = i % GRID_COLS
        val fx = c.toFloat() / (GRID_COLS - 1)
        val fy = r.toFloat() / (GRID_ROWS - 1)
        centers += Offset(
            (cx - gridHalf) + (gridHalf * 2f) * fx,
            gridTop + (gridBottom - gridTop) * fy,
        )
    }

    // Tray: three pieces stacked vertically on the right, centred.
    val trayX = w * 0.86f
    val gap = pieceSize * 1.55f
    val firstCy = h * 0.5f - gap
    val homes = (0 until ShapeGameState.TRAY_SIZE).map { i ->
        Offset(trayX - pieceSize / 2f, (firstCy + gap * i) - pieceSize / 2f)
    }

    return HatLayout(
        cx = cx,
        openingCenter = Offset(cx, bodyTopY),
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
        wandScale = minOf(h * 0.45f, w * 0.22f),
    )
}

@Composable
fun ShapeGameScreen(onBack: () -> Unit) {
    val state = remember { ShapeGameState() }
    val audio = rememberGameAudio()

    // Gentle background music while the game screen is on-stage.
    LaunchedEffect(Unit) { audio.startMusic() }
    DisposableEffect(Unit) { onDispose { audio.stopMusic() } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF3A1C5A), Color(0xFF1B0E2E)))
            )
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val layout = remember(w, h) { computeHatLayout(w, h) }
        val scope = rememberCoroutineScope()

        // Wand sweep over the hole, played after each correct match.
        var wandWaving by remember { mutableStateOf(false) }
        val wandProgress = remember { Animatable(0f) }

        // ---- Hat, outlines, matched fills, wand ----
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCurtains()
            drawHat(layout)
            // Shape outlines / matched fills.
            state.slots.forEachIndexed { i, slot ->
                val center = layout.slotCenters[i]
                val s = layout.slotSize
                translate(center.x - s / 2f, center.y - s / 2f) {
                    if (state.isFilled(slot.id)) {
                        drawFilled(slot.type, Size(s, s), slot.type.fillColor)
                    } else {
                        drawOutline(slot.type, Size(s, s), Color.White.copy(alpha = 0.85f), s * 0.045f)
                    }
                }
            }
            // Wand sweep + sparkles over the hat's mouth.
            if (wandWaving) {
                drawWand(layout.openingCenter, layout.wandScale, wandProgress.value)
            }
        }

        // ---- Draggable tray pieces (on top of the canvas) ----
        val sizeDp = with(density) { layout.pieceSize.toDp() }
        state.tray.forEachIndexed { index, piece ->
            if (piece != null) {
                key(index) {
                    val home = layout.trayHomes[index]
                    val pieceOffset = remember { Animatable(home, Offset.VectorConverter) }
                    val pop = remember { Animatable(1f) }
                    // A fresh piece pops into the tray.
                    LaunchedEffect(piece.pieceId) {
                        pieceOffset.snapTo(home)
                        pop.snapTo(0.4f)
                        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(pieceOffset.value.x.roundToInt(), pieceOffset.value.y.roundToInt()) }
                            .size(sizeDp)
                            .zIndex(1f)
                            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                            .pointerInput(piece.pieceId, layout) {
                                detectDragGestures(
                                    onDrag = { change, drag ->
                                        change.consume()
                                        scope.launch { pieceOffset.snapTo(pieceOffset.value + drag) }
                                    },
                                    onDragEnd = {
                                        val pieceCenter = pieceOffset.value +
                                            Offset(layout.pieceSize / 2f, layout.pieceSize / 2f)
                                        val threshold = layout.slotSize * 0.9f
                                        // Nearest empty slot within threshold; tryDrop validates type.
                                        val nearest = state.slots
                                            .filter { it.id !in state.filledIds }
                                            .map { it to layout.slotCenters[state.slots.indexOf(it)] }
                                            .filter { hypot(pieceCenter.x - it.second.x, pieceCenter.y - it.second.y) < threshold }
                                            .minByOrNull { hypot(pieceCenter.x - it.second.x, pieceCenter.y - it.second.y) }
                                            ?.first
                                        if (nearest != null && state.tryDrop(nearest.id, piece.pieceId)) {
                                            audio.playMagicChime()
                                            state.refillTray()
                                            scope.launch {
                                                wandWaving = true
                                                wandProgress.snapTo(0f)
                                                wandProgress.animateTo(1f, tween(1300))
                                                wandWaving = false
                                            }
                                        } else {
                                            scope.launch { pieceOffset.animateTo(home) }
                                        }
                                    },
                                )
                            }
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawFilled(piece.type, size, piece.type.fillColor)
                        }
                    }
                }
            }
        }

        // ---- Back button ----
        BackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )

        // ---- Win overlay ----
        if (state.isWon) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Card {
                    Box(modifier = Modifier.padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "All done! 🎉",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.size(16.dp))
                            Button(onClick = { state.reset() }) { Text("Play again") }
                        }
                    }
                }
            }
        }
    }
}

// --- Canvas drawing helpers ---------------------------------------------------

private fun DrawScope.drawCurtains() {
    val curtain = Color(0xFF8E2434)
    // top valance
    drawRect(curtain, topLeft = Offset(0f, 0f), size = Size(size.width, size.height * 0.10f))
    // side drapes
    drawRect(curtain, topLeft = Offset(0f, 0f), size = Size(size.width * 0.08f, size.height))
    drawRect(curtain, topLeft = Offset(size.width * 0.92f, 0f), size = Size(size.width * 0.08f, size.height))
}

private fun DrawScope.drawHat(l: HatLayout) {
    val body = Color(0xFF7B3FA0)
    val bodyDark = Color(0xFF5E2C7E)
    val band = Color(0xFFFFD23F)
    val stand = Color(0xFF2BB6A8)
    val hole = Color(0xFF1E0F33)

    // Little stand the up-turned crown balances on.
    drawOval(
        color = stand,
        topLeft = Offset(l.cx - l.bodyBotHalf * 1.5f, l.bodyBottomY - l.brimH * 0.15f),
        size = Size(l.bodyBotHalf * 3f, l.brimH * 0.8f),
    )

    // Brim disc at the top (drawn behind the body so only the wings show).
    drawOval(
        color = bodyDark,
        topLeft = Offset(l.cx - l.brimW / 2f, l.bodyTopY - l.brimH / 2f),
        size = Size(l.brimW, l.brimH),
    )

    // Body (trapezoid): wide at the top, narrowing to the crown.
    val bodyPath = Path().apply {
        moveTo(l.cx - l.bodyTopHalf, l.bodyTopY)
        lineTo(l.cx + l.bodyTopHalf, l.bodyTopY)
        lineTo(l.cx + l.bodyBotHalf, l.bodyBottomY)
        lineTo(l.cx - l.bodyBotHalf, l.bodyBottomY)
        close()
    }
    drawPath(bodyPath, color = body)

    // Rounded closed crown at the bottom.
    drawOval(
        color = bodyDark,
        topLeft = Offset(l.cx - l.bodyBotHalf, l.bodyBottomY - l.brimH * 0.45f),
        size = Size(l.bodyBotHalf * 2f, l.brimH * 0.9f),
    )

    // Hat band near the mouth (width follows the body taper).
    val bandF = (l.bandY - l.bodyTopY) / (l.bodyBottomY - l.bodyTopY)
    val bandHalf = l.bodyTopHalf + (l.bodyBotHalf - l.bodyTopHalf) * bandF
    drawRect(
        color = band,
        topLeft = Offset(l.cx - bandHalf, l.bandY),
        size = Size(bandHalf * 2f, l.bandH),
    )

    // The mouth (the hole the shapes come from), on top of everything.
    drawOval(
        color = hole,
        topLeft = Offset(l.cx - l.openingW / 2f, l.openingCenter.y - l.openingH / 2f),
        size = Size(l.openingW, l.openingH),
    )
    // Soft inner-rim highlight for depth.
    drawOval(
        color = Color.White.copy(alpha = 0.06f),
        topLeft = Offset(l.cx - l.openingW / 2f, l.openingCenter.y - l.openingH * 0.62f),
        size = Size(l.openingW, l.openingH * 0.6f),
    )
}

private fun DrawScope.drawWand(opening: Offset, scale: Float, progress: Float) {
    // Gentle back-and-forth sweep just above the hole (~1.5 waves).
    val sweep = sin(progress * PI.toFloat() * 3f)
    val span = scale * 0.42f
    val tip = Offset(opening.x + sweep * span, opening.y - scale * 0.22f)
    // Handle trails down to the lower-right, toward the unseen magician.
    val handleEnd = Offset(tip.x + scale * 0.55f, tip.y + scale * 0.95f)

    // Stick (dark core + lighter highlight).
    drawLine(Color(0xFF3A220E), handleEnd, tip, strokeWidth = scale * 0.05f)
    drawLine(Color(0xFF6B4A24), handleEnd, tip, strokeWidth = scale * 0.022f)

    // Glowing star tip.
    val tipR = scale * 0.12f
    drawCircle(Color(0xFFFFF3B0).copy(alpha = 0.30f), tipR * 2.2f, tip)
    drawCircle(Color(0xFFFFF3B0).copy(alpha = 0.45f), tipR * 1.4f, tip)
    drawStarTip(tip, tipR)

    // Sparkles drifting down out of the mouth.
    val seeds = floatArrayOf(-0.55f, -0.2f, 0.1f, 0.4f, 0.0f)
    for ((k, fx) in seeds.withIndex()) {
        val ph = (progress * 1.5f + k * 0.19f) % 1f
        val alpha = (1f - ph) * (1f - abs(sweep) * 0.4f)
        val sx = opening.x + fx * scale * 0.4f
        val sy = opening.y + ph * scale * 0.5f
        drawCircle(
            color = Color(0xFFFFF3B0).copy(alpha = alpha.coerceIn(0f, 1f) * 0.8f),
            radius = scale * 0.02f * (1.2f - ph),
            center = Offset(sx, sy),
        )
    }
}

private fun DrawScope.drawStarTip(c: Offset, r: Float) {
    val inner = r * 0.45f
    val path = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else inner
        val a = -PI.toFloat() / 2f + PI.toFloat() * i / 5f
        val x = c.x + rad * cos(a)
        val y = c.y + rad * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, Color(0xFFFFE066))
    drawPath(path, Color.White, style = Stroke(width = r * 0.14f))
}
