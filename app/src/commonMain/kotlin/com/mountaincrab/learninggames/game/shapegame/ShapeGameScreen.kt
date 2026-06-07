package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
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
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Geometry for the hat + shape outlines, computed once from the stage size (px). */
private data class HatLayout(
    val cx: Float,
    val bodyTopY: Float,
    val bodyBottomY: Float,
    val bodyTopHalf: Float,
    val bodyBotHalf: Float,
    val brimY: Float,
    val brimW: Float,
    val brimH: Float,
    val openingW: Float,
    val openingH: Float,
    val slotCenters: List<Offset>,
    val slotSize: Float,
    val spawnTopLeft: Offset,
)

private fun computeHatLayout(w: Float, h: Float, slotCount: Int): HatLayout {
    val cx = w * 0.36f
    val bodyTopY = h * 0.20f
    val brimY = h * 0.82f
    val bodyBottomY = brimY
    val slotSize = minOf(w, h) * 0.12f

    val gridTop = bodyTopY + h * 0.12f
    val gridBottom = bodyBottomY - h * 0.10f
    val gridLeft = cx - w * 0.10f
    val gridRight = cx + w * 0.10f
    val rows = 3
    val cols = 2
    val centers = ArrayList<Offset>(slotCount)
    for (i in 0 until slotCount) {
        val r = i / cols
        val c = i % cols
        val fx = if (cols == 1) 0.5f else c.toFloat() / (cols - 1)
        val fy = if (rows == 1) 0.5f else r.toFloat() / (rows - 1)
        centers += Offset(
            gridLeft + (gridRight - gridLeft) * fx,
            gridTop + (gridBottom - gridTop) * fy,
        )
    }

    return HatLayout(
        cx = cx,
        bodyTopY = bodyTopY,
        bodyBottomY = bodyBottomY,
        bodyTopHalf = w * 0.16f,
        bodyBotHalf = w * 0.20f,
        brimY = brimY,
        brimW = w * 0.52f,
        brimH = h * 0.12f,
        openingW = w * 0.28f,
        openingH = h * 0.08f,
        slotCenters = centers,
        slotSize = slotSize,
        spawnTopLeft = Offset(w * 0.84f - slotSize / 2f, h * 0.50f - slotSize / 2f),
    )
}

@Composable
fun ShapeGameScreen(onBack: () -> Unit) {
    val state = remember { ShapeGameState() }

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
        val layout = remember(w, h) { computeHatLayout(w, h, state.slots.size) }
        val scope = rememberCoroutineScope()

        // Wand-wave state, played after a correct match.
        var wandSlot by remember { mutableStateOf<Int?>(null) }
        val wandProgress = remember { Animatable(0f) }

        // Live position (top-left, stage px) of the draggable piece.
        val piece = state.currentPiece
        val pieceOffset = remember { Animatable(layout.spawnTopLeft, Offset.VectorConverter) }
        LaunchedEffect(piece, layout) {
            pieceOffset.snapTo(layout.spawnTopLeft)
        }

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
            // Wand sweep + sparkles over the just-matched slot.
            wandSlot?.let { id ->
                val center = layout.slotCenters[state.slots.indexOfFirst { it.id == id }]
                drawWand(center, layout.slotSize, wandProgress.value)
            }
        }

        // ---- Draggable piece (on top of the canvas) ----
        if (piece != null) {
            val sizeDp = with(density) { layout.slotSize.toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(pieceOffset.value.x.roundToInt(), pieceOffset.value.y.roundToInt()) }
                    .size(sizeDp)
                    .zIndex(1f)
                    .pointerInput(piece, layout) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                scope.launch { pieceOffset.snapTo(pieceOffset.value + drag) }
                            },
                            onDragEnd = {
                                val pieceCenter = pieceOffset.value +
                                    Offset(layout.slotSize / 2f, layout.slotSize / 2f)
                                val threshold = layout.slotSize * 0.85f
                                // Nearest slot within threshold; tryDrop validates type + empties.
                                val nearest = state.slots
                                    .map { it to layout.slotCenters[state.slots.indexOf(it)] }
                                    .filter { hypot(pieceCenter.x - it.second.x, pieceCenter.y - it.second.y) < threshold }
                                    .minByOrNull { hypot(pieceCenter.x - it.second.x, pieceCenter.y - it.second.y) }
                                    ?.first
                                if (nearest != null && state.tryDrop(nearest.id)) {
                                    scope.launch {
                                        wandSlot = nearest.id
                                        wandProgress.snapTo(0f)
                                        wandProgress.animateTo(1f, tween(700))
                                        wandSlot = null
                                        state.spawnNext()
                                    }
                                } else {
                                    scope.launch { pieceOffset.animateTo(layout.spawnTopLeft) }
                                }
                            },
                        )
                    }
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawFilled(piece, size, piece.fillColor)
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
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "All done! 🎉",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
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

    // stand under the hat
    drawOval(
        color = stand,
        topLeft = Offset(l.cx - l.brimW * 0.30f, l.brimY + l.brimH * 0.30f),
        size = Size(l.brimW * 0.60f, l.brimH * 0.70f),
    )
    // brim
    drawOval(
        color = bodyDark,
        topLeft = Offset(l.cx - l.brimW / 2f, l.brimY - l.brimH / 2f),
        size = Size(l.brimW, l.brimH),
    )
    // body (trapezoid)
    val bodyPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(l.cx - l.bodyTopHalf, l.bodyTopY)
        lineTo(l.cx + l.bodyTopHalf, l.bodyTopY)
        lineTo(l.cx + l.bodyBotHalf, l.bodyBottomY)
        lineTo(l.cx - l.bodyBotHalf, l.bodyBottomY)
        close()
    }
    drawPath(bodyPath, color = body)
    // band near the bottom of the body
    drawRect(
        color = band,
        topLeft = Offset(l.cx - l.bodyBotHalf, l.bodyBottomY - l.brimH * 0.5f),
        size = Size(l.bodyBotHalf * 2f, l.brimH * 0.45f),
    )
    // opening at the top
    drawOval(
        color = Color(0xFF2A1240),
        topLeft = Offset(l.cx - l.openingW / 2f, l.bodyTopY - l.openingH / 2f),
        size = Size(l.openingW, l.openingH),
    )
}

private fun DrawScope.drawWand(center: Offset, slotSize: Float, progress: Float) {
    // Wand sweeps from upper-left to upper-right of the slot.
    val span = slotSize * 1.4f
    val tipX = center.x - span / 2f + span * progress
    val tipY = center.y - slotSize * 0.9f
    val handleX = tipX - slotSize * 0.5f
    val handleY = tipY + slotSize * 0.6f
    drawLine(
        color = Color(0xFF4A2A12),
        start = Offset(handleX, handleY),
        end = Offset(tipX, tipY),
        strokeWidth = slotSize * 0.08f,
    )
    // star tip
    drawCircle(Color(0xFFFFE066), radius = slotSize * 0.10f, center = Offset(tipX, tipY))

    // sparkles fade in/out across the sweep
    val sparkAlpha = (1f - kotlin.math.abs(progress - 0.5f) * 2f).coerceIn(0f, 1f)
    val sparkColor = Color(0xFFFFF3B0).copy(alpha = sparkAlpha)
    val rad = slotSize * 0.06f
    drawCircle(sparkColor, rad, Offset(center.x - slotSize * 0.3f, center.y - slotSize * 0.2f))
    drawCircle(sparkColor, rad * 0.8f, Offset(center.x + slotSize * 0.3f, center.y + slotSize * 0.1f))
    drawCircle(sparkColor, rad * 0.6f, Offset(center.x, center.y - slotSize * 0.45f))
}
