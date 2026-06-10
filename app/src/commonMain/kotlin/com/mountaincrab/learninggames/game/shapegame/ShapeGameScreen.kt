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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import com.mountaincrab.learninggames.game.observeGameState
import com.mountaincrab.learninggames.game.shapegame.ShapeRenderer.drawFilled
import com.mountaincrab.learninggames.game.shapegame.ShapeRenderer.drawOutline
import com.mountaincrab.learninggames.geometry.toOffset
import com.mountaincrab.learninggames.ui.components.BackButton
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// HatLayout/computeHatLayout live in the `shared` module (same package), so the
// geometry is identical to the webapp's.

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

        // Recompose whenever the shared (Compose-free) game state mutates.
        observeGameState(state)

        // Wand sweep over the hole, played after each correct match.
        var wandWaving by remember { mutableStateOf(false) }
        val wandProgress = remember { Animatable(0f) }

        // ---- Stage, hat, outlines, matched fills, wand ----
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawBackdrop()
            drawFloor(layout.cx)
            drawCurtains()
            // Strings the waiting tray pieces dangle from.
            state.tray.forEachIndexed { i, piece ->
                if (piece != null) {
                    val home = layout.trayHomes[i]
                    val sx = home.x + layout.pieceSize / 2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.30f),
                        start = Offset(sx, size.height * 0.15f),
                        end = Offset(sx, home.y + layout.pieceSize * 0.12f),
                        strokeWidth = size.minDimension * 0.004f,
                    )
                }
            }
            drawHat(layout)
            // Shape outlines / matched fills.
            state.slots.forEachIndexed { i, slot ->
                val center = layout.slotCenters[i]
                val s = layout.slotSize
                translate(center.x - s / 2f, center.y - s / 2f) {
                    if (state.isFilled(slot.id)) {
                        drawFilled(slot.type, Size(s, s), slot.type.fillColor)
                    } else {
                        drawOutline(slot.type, Size(s, s), Color(0xFFE3C8FF).copy(alpha = 0.9f), s * 0.045f)
                    }
                }
            }
            // Wand sweep + sparkles over the hat's mouth.
            if (wandWaving) {
                drawWand(layout.openingCenter.toOffset(), layout.wandScale, wandProgress.value)
            }
        }

        // ---- Draggable tray pieces (on top of the canvas) ----
        val sizeDp = with(density) { layout.pieceSize.toDp() }
        state.tray.forEachIndexed { index, piece ->
            if (piece != null) {
                key(index) {
                    val home = layout.trayHomes[index].toOffset()
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

/** Fixed star positions (x/y as stage fractions, plus a 0..1 brightness). */
private val STAGE_STARS = listOf(
    Triple(0.12f, 0.18f, 0.9f), Triple(0.21f, 0.46f, 0.5f), Triple(0.27f, 0.13f, 0.7f),
    Triple(0.33f, 0.58f, 0.4f), Triple(0.40f, 0.20f, 0.8f), Triple(0.47f, 0.42f, 0.5f),
    Triple(0.52f, 0.15f, 0.9f), Triple(0.58f, 0.55f, 0.6f), Triple(0.63f, 0.25f, 0.4f),
    Triple(0.68f, 0.47f, 0.8f), Triple(0.73f, 0.16f, 0.6f), Triple(0.78f, 0.36f, 0.5f),
    Triple(0.84f, 0.55f, 0.7f), Triple(0.88f, 0.22f, 0.9f), Triple(0.16f, 0.64f, 0.5f),
    Triple(0.60f, 0.68f, 0.45f), Triple(0.30f, 0.34f, 0.55f), Triple(0.75f, 0.65f, 0.5f),
)

private fun DrawScope.drawBackdrop() {
    val w = size.width
    val h = size.height
    // Shimmering vertical light streaks falling down the backdrop.
    val streaks = listOf(0.16f, 0.27f, 0.41f, 0.55f, 0.68f, 0.80f, 0.88f)
    streaks.forEachIndexed { i, fx ->
        val sw = w * (0.008f + 0.005f * (i % 3))
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0f)),
                startY = h * 0.10f,
                endY = h * 0.78f,
            ),
            topLeft = Offset(w * fx, h * 0.10f),
            size = Size(sw, h * 0.68f),
        )
    }
    // Twinkling stars.
    for ((fx, fy, s) in STAGE_STARS) {
        drawCircle(
            color = Color(0xFFEFE3FF).copy(alpha = 0.12f + 0.28f * s),
            radius = w * 0.0035f * (0.6f + s),
            center = Offset(w * fx, h * fy),
        )
    }
}

private fun DrawScope.drawFloor(hatCx: Float) {
    val w = size.width
    val h = size.height
    val top = h * 0.80f
    // Wooden stage boards.
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF454D63), Color(0xFF2A3040)), startY = top, endY = h),
        topLeft = Offset(0f, top),
        size = Size(w, h - top),
    )
    // Bright stage edge where the boards meet the backdrop.
    drawRect(Color(0xFF5A6480), topLeft = Offset(0f, top), size = Size(w, h * 0.008f))
    // Horizontal plank seams.
    for (fy in listOf(0.865f, 0.93f)) {
        drawRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(0f, h * fy), size = Size(w, h * 0.004f))
    }
    // Staggered vertical board joints (x fraction to plank row).
    val rowTops = floatArrayOf(0.80f, 0.865f, 0.93f)
    val rowBots = floatArrayOf(0.865f, 0.93f, 1f)
    val joints = listOf(0.14f to 0, 0.46f to 0, 0.78f to 0, 0.30f to 1, 0.62f to 1, 0.90f to 1, 0.10f to 2, 0.38f to 2, 0.70f to 2)
    for ((fx, row) in joints) {
        drawRect(
            Color.Black.copy(alpha = 0.15f),
            topLeft = Offset(w * fx, h * rowTops[row]),
            size = Size(w * 0.0025f, h * (rowBots[row] - rowTops[row])),
        )
    }
    // Soft spotlight pool under the hat.
    drawOval(
        Color.White.copy(alpha = 0.05f),
        topLeft = Offset(hatCx - w * 0.30f, h * 0.85f),
        size = Size(w * 0.60f, h * 0.14f),
    )
}

private val CURTAIN = Color(0xFFA62644)
private val CURTAIN_DARK = Color(0xFF7E1B33)
private val CURTAIN_LIGHT = Color(0xFFC23B5E)
private val CURTAIN_GOLD = Color(0xFFE9C46A)

private fun DrawScope.drawCurtains() {
    drawSideDrape(rightSide = false)
    drawSideDrape(rightSide = true)
    drawValance()
}

private fun DrawScope.drawSideDrape(rightSide: Boolean) {
    val w = size.width
    val h = size.height
    fun x(f: Float) = if (rightSide) w - f * w else f * w

    // Drape gathered at the waist by a gold tie-back, flaring back out below it.
    val drape = Path().apply {
        moveTo(x(0f), 0f)
        lineTo(x(0.115f), 0f)
        quadraticTo(x(0.085f), h * 0.18f, x(0.062f), h * 0.34f)
        quadraticTo(x(0.045f), h * 0.46f, x(0.048f), h * 0.54f)
        quadraticTo(x(0.062f), h * 0.72f, x(0.115f), h * 0.86f)
        quadraticTo(x(0.132f), h * 0.92f, x(0.125f), h * 0.97f)
        quadraticTo(x(0.080f), h * 1.00f, x(0.040f), h * 0.975f)
        quadraticTo(x(0.018f), h * 0.96f, x(0f), h * 0.975f)
        close()
    }
    drawPath(drape, CURTAIN)
    // Shadowed fabric folds following the gather.
    for ((k, f) in listOf(0.030f, 0.058f, 0.088f).withIndex()) {
        val fold = Path().apply {
            moveTo(x(f), h * 0.01f)
            quadraticTo(x(f * 0.78f), h * 0.30f, x(0.047f + k * 0.006f), h * 0.53f)
            quadraticTo(x(0.058f + k * 0.014f), h * 0.74f, x(0.045f + k * 0.030f), h * 0.96f)
        }
        drawPath(fold, CURTAIN_DARK.copy(alpha = 0.5f), style = Stroke(width = w * 0.006f))
    }
    // Light catching the outer edge.
    val sheen = Path().apply {
        moveTo(x(0.012f), h * 0.04f)
        quadraticTo(x(0.016f), h * 0.45f, x(0.010f), h * 0.90f)
    }
    drawPath(sheen, CURTAIN_LIGHT.copy(alpha = 0.55f), style = Stroke(width = w * 0.008f))
    // Gold tie-back band at the waist.
    val tieL = minOf(x(0.014f), x(0.082f))
    drawRoundRect(
        color = CURTAIN_GOLD,
        topLeft = Offset(tieL, h * 0.50f),
        size = Size(w * 0.068f, h * 0.055f),
        cornerRadius = CornerRadius(w * 0.012f, w * 0.012f),
    )
    drawRoundRect(
        color = Color(0xFFB98A2F),
        topLeft = Offset(tieL, h * 0.50f),
        size = Size(w * 0.068f, h * 0.055f),
        cornerRadius = CornerRadius(w * 0.012f, w * 0.012f),
        style = Stroke(width = w * 0.0025f),
    )
}

private fun DrawScope.drawValance() {
    val w = size.width
    val h = size.height
    val vh = h * 0.105f
    val scallops = 9
    val sw = w / scallops
    val dip = h * 0.055f
    // Scalloped pelmet across the top.
    val valance = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(w, vh)
        for (i in scallops - 1 downTo 0) {
            quadraticTo(sw * (i + 0.5f), vh + dip, sw * i, vh)
        }
        close()
    }
    drawPath(valance, CURTAIN)
    // Darker gathers where the scallops meet.
    for (i in 1 until scallops) {
        val cx = sw * i
        val gather = Path().apply {
            moveTo(cx - sw * 0.16f, 0f)
            lineTo(cx + sw * 0.16f, 0f)
            lineTo(cx, vh)
            close()
        }
        drawPath(gather, CURTAIN_DARK.copy(alpha = 0.5f))
    }
    // Gold trim tracing the scallops.
    val trim = Path().apply {
        moveTo(0f, vh)
        for (i in 0 until scallops) {
            quadraticTo(sw * (i + 0.5f), vh + dip, sw * (i + 1f), vh)
        }
    }
    drawPath(trim, CURTAIN_GOLD, style = Stroke(width = h * 0.009f))
    // Dark rail along the very top.
    drawRect(CURTAIN_DARK, topLeft = Offset(0f, 0f), size = Size(w, h * 0.018f))
}

private fun DrawScope.drawHat(l: HatLayout) {
    val bodyLight = Color(0xFF9355BE)
    val body = Color(0xFF7B3FA0)
    val bodyDark = Color(0xFF5E2C7E)
    val outline = Color(0xFF3F1A57)
    val hole = Color(0xFF14081F)
    val strokeW = size.minDimension * 0.006f

    // Teal pedestal the up-turned crown balances on (a squat cylinder).
    val pedRx = l.bodyBotHalf * 1.5f
    val pedRy = l.brimH * 0.42f
    val pedTopCy = l.bodyBottomY + l.brimH * 0.05f
    val pedH = l.brimH * 0.55f
    drawOval(
        color = Color(0xFF1F8478),
        topLeft = Offset(l.cx - pedRx, pedTopCy + pedH - pedRy),
        size = Size(pedRx * 2f, pedRy * 2f),
    )
    drawRect(
        color = Color(0xFF2BA797),
        topLeft = Offset(l.cx - pedRx, pedTopCy),
        size = Size(pedRx * 2f, pedH),
    )
    drawOval(
        color = Color(0xFF3BC9B8),
        topLeft = Offset(l.cx - pedRx, pedTopCy - pedRy),
        size = Size(pedRx * 2f, pedRy * 2f),
    )
    drawOval(
        color = Color(0xFF1B7C70),
        topLeft = Offset(l.cx - pedRx, pedTopCy - pedRy),
        size = Size(pedRx * 2f, pedRy * 2f),
        style = Stroke(width = strokeW * 0.8f),
    )

    // Body: gently bowed sides, side-lit from the left.
    val midY = (l.bodyTopY + l.bodyBottomY) / 2f
    val ctrlHalf = (l.bodyTopHalf + l.bodyBotHalf) * 0.485f
    val bodyPath = Path().apply {
        moveTo(l.cx - l.bodyTopHalf, l.bodyTopY)
        quadraticTo(l.cx - ctrlHalf, midY, l.cx - l.bodyBotHalf, l.bodyBottomY)
        lineTo(l.cx + l.bodyBotHalf, l.bodyBottomY)
        quadraticTo(l.cx + ctrlHalf, midY, l.cx + l.bodyTopHalf, l.bodyTopY)
        close()
    }
    drawPath(
        bodyPath,
        brush = Brush.horizontalGradient(
            listOf(bodyLight, body, bodyDark),
            startX = l.cx - l.bodyTopHalf,
            endX = l.cx + l.bodyTopHalf,
        ),
    )

    // Rounded closed crown at the bottom.
    drawOval(
        color = bodyDark,
        topLeft = Offset(l.cx - l.bodyBotHalf, l.bodyBottomY - l.brimH * 0.45f),
        size = Size(l.bodyBotHalf * 2f, l.brimH * 0.9f),
    )
    drawPath(bodyPath, outline, style = Stroke(width = strokeW))

    // Soft sheen down the lit side of the felt.
    val sheen = Path().apply {
        moveTo(l.cx - l.bodyTopHalf * 0.72f, l.bodyTopY + l.brimH * 0.9f)
        quadraticTo(l.cx - ctrlHalf * 0.78f, midY, l.cx - l.bodyBotHalf * 0.72f, l.bodyBottomY - l.brimH * 0.6f)
    }
    drawPath(sheen, Color.White.copy(alpha = 0.10f), style = Stroke(width = l.bodyTopHalf * 0.10f, cap = StrokeCap.Round))

    // Brim disc at the top.
    drawOval(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF8F4FBC), bodyDark),
            startY = l.bodyTopY - l.brimH / 2f,
            endY = l.bodyTopY + l.brimH / 2f,
        ),
        topLeft = Offset(l.cx - l.brimW / 2f, l.bodyTopY - l.brimH / 2f),
        size = Size(l.brimW, l.brimH),
    )
    drawOval(
        color = outline,
        topLeft = Offset(l.cx - l.brimW / 2f, l.bodyTopY - l.brimH / 2f),
        size = Size(l.brimW, l.brimH),
        style = Stroke(width = strokeW),
    )

    // The mouth (the hole the shapes come from), on top of everything.
    drawOval(
        color = hole,
        topLeft = Offset(l.cx - l.openingW / 2f, l.openingCenter.y - l.openingH / 2f),
        size = Size(l.openingW, l.openingH),
    )
    // Soft inner-rim highlight for depth.
    drawOval(
        color = Color.White.copy(alpha = 0.08f),
        topLeft = Offset(l.cx - l.openingW / 2f, l.openingCenter.y - l.openingH * 0.62f),
        size = Size(l.openingW, l.openingH * 0.6f),
    )
}

private fun DrawScope.drawWand(opening: Offset, scale: Float, progress: Float) {
    // Gentle back-and-forth sweep just above the hole (~1.5 waves).
    val sweep = sin(progress * PI.toFloat() * 3f)
    val span = scale * 0.42f
    val tip = Offset(opening.x + sweep * span, opening.y - scale * 0.10f)
    // Handle trails up to the top-right, toward the unseen magician.
    val handleEnd = Offset(tip.x + scale * 0.62f, tip.y - scale * 0.95f)

    // Classic magician's wand: black stick, white tip cap.
    drawLine(Color(0xFF17151D), handleEnd, tip, strokeWidth = scale * 0.05f, cap = StrokeCap.Round)
    val capEnd = Offset(
        tip.x + (handleEnd.x - tip.x) * 0.22f,
        tip.y + (handleEnd.y - tip.y) * 0.22f,
    )
    drawLine(Color(0xFFF2EFE6), tip, capEnd, strokeWidth = scale * 0.05f, cap = StrokeCap.Round)

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
