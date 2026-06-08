package com.mountaincrab.learninggames.game.trumpergame

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mountaincrab.learninggames.ui.components.BackButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Per-character geometry, computed once from the stage size (px). [bases] are the
 * resting body centres (at scale 1); the feet stay planted while the tummy swells
 * upward. */
private data class TrumperLayout(
    val bases: List<Offset>,
    val bodyR: Float,
    val groundY: Float,
)

private const val BLOAT_SCALE = 1.7f

/** Distinct, friendly body colours for the four characters. */
private val BODY_COLORS = listOf(
    Color(0xFFFF8FAB), // pink
    Color(0xFF6FD3C2), // teal
    Color(0xFFFFC857), // yellow
    Color(0xFF9D8DF1), // purple
)

private fun computeTrumperLayout(w: Float, h: Float): TrumperLayout {
    val columnW = w / TrumperGameState.COUNT
    val bodyR = minOf(columnW * 0.30f, h * 0.18f)
    val groundY = h * 0.80f
    val bases = (0 until TrumperGameState.COUNT).map { i ->
        Offset(columnW * (i + 0.5f), groundY - bodyR)
    }
    return TrumperLayout(bases = bases, bodyR = bodyR, groundY = groundY)
}

/** Current drawn body centre for a character, given its swell [scale]; the feet stay
 * planted on the ground while the belly grows upward. */
private fun bodyCenter(base: Offset, bodyR: Float, scale: Float): Offset =
    Offset(base.x, (base.y + bodyR) - bodyR * scale)

@Composable
fun TrumperGameScreen(onBack: () -> Unit) {
    val state = remember { TrumperGameState() }
    val audio = rememberTrumperAudio()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFBDE0FE), Color(0xFFA0D8C4)))
            )
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val layout = remember(w, h) { computeTrumperLayout(w, h) }
        val scope = rememberCoroutineScope()

        // Per-character animation: belly swell scale, and a 0→1 gas-puff burst.
        val bellyScale = remember { List(TrumperGameState.COUNT) { Animatable(1f) } }
        val gas = remember { List(TrumperGameState.COUNT) { Animatable(0f) } }

        // Drive each belly toward its target: a slow bouncy swell, a quick shrink.
        for (i in 0 until TrumperGameState.COUNT) {
            val bloated = state.isBloated(i)
            key(i) {
                LaunchedEffect(bloated) {
                    if (bloated) {
                        bellyScale[i].animateTo(
                            BLOAT_SCALE,
                            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
                        )
                    } else {
                        bellyScale[i].animateTo(1f, tween(220))
                    }
                }
            }
        }

        // Bloat a random character at gentle random intervals.
        LaunchedEffect(Unit) {
            while (true) {
                delay(900L + Random.nextLong(2200L))
                state.bloatRandom()
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(layout) {
                    detectTapGestures { tap ->
                        for (i in layout.bases.indices) {
                            if (!state.isBloated(i)) continue
                            val center = bodyCenter(layout.bases[i], layout.bodyR, bellyScale[i].value)
                            val r = layout.bodyR * bellyScale[i].value
                            if ((tap - center).getDistance() <= r * 1.1f) {
                                if (state.release(i)) {
                                    scope.launch {
                                        audio.playFart()
                                        gas[i].snapTo(0f)
                                        gas[i].animateTo(1f, tween(800))
                                    }
                                }
                                break
                            }
                        }
                    }
                }
        ) {
            drawGround(layout.groundY)
            for (i in layout.bases.indices) {
                val scale = bellyScale[i].value
                val center = bodyCenter(layout.bases[i], layout.bodyR, scale)
                // Gas puffs sit behind the character.
                if (gas[i].value > 0f && gas[i].value < 1f) {
                    drawGas(center, layout.bodyR, gas[i].value)
                }
                drawTrumper(center, layout.bodyR, scale, BODY_COLORS[i])
            }
        }

        BackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
    }
}

// --- Canvas drawing helpers ---------------------------------------------------

private fun DrawScope.drawGround(groundY: Float) {
    drawRect(
        color = Color(0xFF8AC926),
        topLeft = Offset(0f, groundY),
        size = Size(size.width, size.height - groundY),
    )
    // soft grass line
    drawRect(
        color = Color(0xFF6FA31C),
        topLeft = Offset(0f, groundY),
        size = Size(size.width, size.height * 0.012f),
    )
}

/** One little round character: planted legs, a swelling belly-body, arms, head and a
 * face that strains (reddens, mouth opens) as the tummy [scale] grows. */
private fun DrawScope.drawTrumper(center: Offset, bodyR: Float, scale: Float, color: Color) {
    val r = bodyR * scale
    val feetY = center.y + r
    // strain 0..1 — how bloated this character looks right now
    val strain = ((scale - 1f) / (BLOAT_SCALE - 1f)).coerceIn(0f, 1f)
    val bodyColor = lerp(color, Color(0xFFE85D5D), strain * 0.55f)

    // Legs (planted on the ground, just under the body).
    val legW = bodyR * 0.20f
    val legH = bodyR * 0.45f
    val legColor = lerp(color, Color.Black, 0.25f)
    for (sx in listOf(-1f, 1f)) {
        drawRoundRectCompat(
            color = legColor,
            topLeft = Offset(center.x + sx * bodyR * 0.45f - legW / 2f, feetY - legH * 0.4f),
            size = Size(legW, legH),
            corner = legW / 2f,
        )
    }

    // Arms (lift out a little as the belly swells).
    val armColor = lerp(color, Color.Black, 0.15f)
    val armLift = strain * bodyR * 0.35f
    for (sx in listOf(-1f, 1f)) {
        drawLine(
            color = armColor,
            start = Offset(center.x + sx * r * 0.7f, center.y),
            end = Offset(center.x + sx * r * 1.15f, center.y - armLift),
            strokeWidth = bodyR * 0.18f,
        )
    }

    // Belly-body.
    drawCircle(color = bodyColor, radius = r, center = center)
    // soft belly highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.18f),
        radius = r * 0.55f,
        center = Offset(center.x - r * 0.28f, center.y - r * 0.28f),
    )

    // Head sitting on top of the belly.
    val headR = bodyR * 0.55f
    val headCenter = Offset(center.x, center.y - r - headR * 0.55f)
    drawCircle(color = lerp(color, Color(0xFFE85D5D), strain * 0.4f), radius = headR, center = headCenter)

    // Eyes.
    val eyeDx = headR * 0.42f
    val eyeY = headCenter.y - headR * 0.05f
    val eyeR = headR * 0.16f
    for (sx in listOf(-1f, 1f)) {
        drawCircle(Color.White, eyeR, Offset(headCenter.x + sx * eyeDx, eyeY))
        drawCircle(Color(0xFF2B2B2B), eyeR * 0.55f, Offset(headCenter.x + sx * eyeDx, eyeY))
    }

    // Mouth: a calm little smile that opens into a strained "O" as it bloats.
    val mouthC = Offset(headCenter.x, headCenter.y + headR * 0.42f)
    val mouthH = headR * (0.10f + strain * 0.40f)
    val mouthW = headR * (0.42f - strain * 0.10f)
    drawOval(
        color = Color(0xFF7A2B2B),
        topLeft = Offset(mouthC.x - mouthW, mouthC.y - mouthH),
        size = Size(mouthW * 2f, mouthH * 2f),
    )

    // Straining extras: blush cheeks + a sweat drop.
    if (strain > 0.35f) {
        val blush = Color(0xFFFF5C7A).copy(alpha = (strain - 0.35f).coerceIn(0f, 1f) * 0.7f)
        for (sx in listOf(-1f, 1f)) {
            drawCircle(blush, headR * 0.18f, Offset(headCenter.x + sx * headR * 0.6f, headCenter.y + headR * 0.2f))
        }
        // sweat drop near the temple
        drawCircle(
            color = Color(0xFF7FC8F8).copy(alpha = strain),
            radius = headR * 0.12f,
            center = Offset(headCenter.x + headR * 0.85f, headCenter.y - headR * 0.5f),
        )
    }
}

/** A burst of greenish stink clouds drifting up and away from behind the character. */
private fun DrawScope.drawGas(center: Offset, bodyR: Float, p: Float) {
    val origin = Offset(center.x + bodyR * 0.7f, center.y + bodyR * 0.5f)
    val green = Color(0xFF9CCC3C)
    val fade = (1f - p).coerceIn(0f, 1f)
    for (k in 0 until 5) {
        val drift = p * bodyR * (1.3f + 0.35f * k)
        val angle = (-0.6f + 0.25f * k) // fan upward/outward
        val cx = origin.x + drift * cos(angle)
        val cy = origin.y - drift * 0.7f
        val rad = bodyR * (0.16f + 0.10f * k) * (0.5f + p)
        drawCircle(
            color = green.copy(alpha = fade * 0.45f),
            radius = rad,
            center = Offset(cx, cy),
        )
    }
    // a couple of little speed lines near the source
    for (k in 0 until 3) {
        val a = -0.5f + 0.3f * k
        val s = Offset(origin.x + bodyR * 0.1f * cos(a), origin.y + bodyR * 0.1f * sin(a))
        val e = Offset(s.x + bodyR * (0.6f + p * 0.6f) * cos(a), s.y - bodyR * 0.4f * (0.6f + p))
        drawLine(green.copy(alpha = fade * 0.6f), s, e, strokeWidth = bodyR * 0.04f)
    }
}

/** Small rounded-rect helper drawn via [Path] (avoids importing CornerRadius types). */
private fun DrawScope.drawRoundRectCompat(color: Color, topLeft: Offset, size: Size, corner: Float) {
    val path = Path().apply {
        val c = corner.coerceAtMost(minOf(size.width, size.height) / 2f)
        val l = topLeft.x; val t = topLeft.y; val rr = topLeft.x + size.width; val b = topLeft.y + size.height
        moveTo(l + c, t)
        lineTo(rr - c, t)
        quadraticTo(rr, t, rr, t + c)
        lineTo(rr, b - c)
        quadraticTo(rr, b, rr - c, b)
        lineTo(l + c, b)
        quadraticTo(l, b, l, b - c)
        lineTo(l, t + c)
        quadraticTo(l, t, l + c, t)
        close()
    }
    drawPath(path, color)
}
