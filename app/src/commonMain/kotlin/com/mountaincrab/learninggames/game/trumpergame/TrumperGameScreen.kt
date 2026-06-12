package com.mountaincrab.learninggames.game.trumpergame

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mountaincrab.learninggames.game.observeGameState
import com.mountaincrab.learninggames.geometry.toOffset
import com.mountaincrab.learninggames.ui.components.BackButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// TrumperLayout/computeTrumperLayout/BLOAT_SCALE/trumperBodyCenter live in the
// `shared` module (same package), so the geometry is identical to the webapp's.

/** A friendly look for each character: shirt colour, skin tone, hair colour/style and
 * shoe colour. [hairStyle]: 0 = short spiky, 1 = pigtails, 2 = afro, 3 = top bun. */
private data class TrumperLook(
    val shirt: Color,
    val skin: Color,
    val hair: Color,
    val shoe: Color,
    val hairStyle: Int,
)

private val LOOKS = listOf(
    TrumperLook(Color(0xFFFF8FAB), Color(0xFFC68642), Color(0xFF2B2B2B), Color(0xFF3A2412), hairStyle = 0),
    TrumperLook(Color(0xFF6FD3C2), Color(0xFFFFDBAC), Color(0xFFFF7F50), Color(0xFF5C4033), hairStyle = 1),
    TrumperLook(Color(0xFFFFC857), Color(0xFF8D5524), Color(0xFF1A1A1A), Color(0xFF2B2B2B), hairStyle = 2),
    TrumperLook(Color(0xFF9D8DF1), Color(0xFFF1C27D), Color(0xFFF4D35E), Color(0xFF6B4F2A), hairStyle = 3),
)

private val PANTS_COLOR = Color(0xFF4A6FA5)

// --- Opening sequence (each character pops out of its burrow shouting its name) ---
// Audio clips are keyed by column order: 0 = Penny, 1 = Taz, 2 = Zach, 3 = Rory
// (matching [LOOKS] and the `name_*.ogg` files in res/raw).

/** A beat before the first pop — shows the burrows and lets the name clips finish
 * loading so the very first shout isn't missed. */
private const val INTRO_LEAD_MS = 600L

/** Length of one character's pop-out jump. */
private const val INTRO_JUMP_MS = 1300

/** Delay between one character popping out and the next. */
private const val INTRO_STAGGER_MS = 2000L

/** How far below ground (×bodyR) a not-yet-emerged character is hidden. */
private const val POP_DEPTH = 2.3f

/** Overshoot ("back") ease-out: rises past the resting spot then settles — a little
 * hop as the character lands out of the hole. */
private val EaseOutBack = Easing { t ->
    val s = 2.0f
    val p = t - 1f
    1f + (s + 1f) * p * p * p + s * p * p
}

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

        // Recompose whenever the shared (Compose-free) game state mutates.
        observeGameState(state)

        // Per-character animation: belly swell scale, and a 0→1 gas-puff burst.
        val bellyScale = remember { List(TrumperGameState.COUNT) { Animatable(1f) } }
        val gas = remember { List(TrumperGameState.COUNT) { Animatable(0f) } }

        // Opening sequence: every character starts hidden in its burrow (emerge 0) and
        // pops out one by one (emerge → 1 with a hop), shouting its name as it appears.
        val emerge = remember { List(TrumperGameState.COUNT) { Animatable(0f) } }
        val holeAlpha = remember { Animatable(1f) }
        var introDone by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(INTRO_LEAD_MS)
            for (i in 0 until TrumperGameState.COUNT) {
                audio.playName(i)
                launch { emerge[i].animateTo(1f, tween(INTRO_JUMP_MS, easing = EaseOutBack)) }
                if (i < TrumperGameState.COUNT - 1) delay(INTRO_STAGGER_MS) else delay(INTRO_JUMP_MS.toLong())
            }
            introDone = true
        }

        // Once everyone is out, fade the burrows away, leaving the normal play scene.
        LaunchedEffect(introDone) {
            if (introDone) holeAlpha.animateTo(0f, tween(500))
        }

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

        // Bloat a random character at gentle random intervals — only once the opening
        // sequence has finished and everyone is standing in their row.
        LaunchedEffect(introDone) {
            if (!introDone) return@LaunchedEffect
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
                        if (!introDone) return@detectTapGestures
                        for (i in layout.bases.indices) {
                            if (!state.isBloated(i)) continue
                            val center = trumperBodyCenter(layout.bases[i], layout.bodyR, bellyScale[i].value).toOffset()
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
            drawSky(layout.groundY)
            drawGround(layout.groundY)

            // Burrows the characters pop out of (fade away after the opening).
            if (holeAlpha.value > 0.01f) {
                for (i in layout.bases.indices) {
                    drawBurrow(layout.bases[i].x, layout.groundY, layout.bodyR, holeAlpha.value)
                }
            }

            for (i in layout.bases.indices) {
                val scale = bellyScale[i].value
                val popPx = (1f - emerge[i].value) * POP_DEPTH * layout.bodyR
                val center = trumperBodyCenter(layout.bases[i], layout.bodyR, scale).toOffset() + Offset(0f, popPx)
                if (!introDone) {
                    // Still popping out: clip away the part below ground (inside the hole).
                    clipRect(0f, 0f, size.width, layout.groundY) {
                        drawTrumper(center, layout.bodyR, scale, LOOKS[i])
                    }
                } else {
                    // Gas puffs sit behind the character.
                    if (gas[i].value > 0f && gas[i].value < 1f) {
                        drawGas(center, layout.bodyR, gas[i].value)
                    }
                    drawTrumper(center, layout.bodyR, scale, LOOKS[i])
                }
            }
        }

        BackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
    }
}

// --- Canvas drawing helpers ---------------------------------------------------

/** Sun, drifting clouds and rolling hills behind the ground. */
private fun DrawScope.drawSky(groundY: Float) {
    val w = size.width
    val h = size.height

    // Sun with a soft glow, top-right corner.
    val sun = Offset(w * 0.86f, h * 0.16f)
    drawCircle(Color(0xFFFFF3B0).copy(alpha = 0.35f), w * 0.10f, sun)
    drawCircle(Color(0xFFFFF3B0).copy(alpha = 0.55f), w * 0.065f, sun)
    drawCircle(Color(0xFFFFE066), w * 0.045f, sun)

    // Drifting clouds.
    drawCloud(Offset(w * 0.18f, h * 0.16f), w * 0.075f)
    drawCloud(Offset(w * 0.46f, h * 0.10f), w * 0.06f)
    drawCloud(Offset(w * 0.68f, h * 0.30f), w * 0.05f)

    // Rolling hills, partially covered by the ground drawn afterwards.
    drawOval(
        color = Color(0xFFA0D8C4),
        topLeft = Offset(-w * 0.10f, groundY - h * 0.12f),
        size = Size(w * 0.65f, h * 0.22f),
    )
    drawOval(
        color = Color(0xFF8FCDB6),
        topLeft = Offset(w * 0.45f, groundY - h * 0.16f),
        size = Size(w * 0.75f, h * 0.26f),
    )
}

private fun DrawScope.drawCloud(center: Offset, r: Float) {
    val c = Color.White.copy(alpha = 0.85f)
    drawCircle(c, r, Offset(center.x - r * 0.9f, center.y))
    drawCircle(c, r * 1.2f, center)
    drawCircle(c, r * 0.85f, Offset(center.x + r * 1.0f, center.y + r * 0.1f))
    drawOval(c, topLeft = Offset(center.x - r * 1.6f, center.y), size = Size(r * 3.2f, r * 0.9f))
}

private fun DrawScope.drawGround(groundY: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        color = Color(0xFF8AC926),
        topLeft = Offset(0f, groundY),
        size = Size(w, h - groundY),
    )
    // soft grass line
    drawRect(
        color = Color(0xFF6FA31C),
        topLeft = Offset(0f, groundY),
        size = Size(w, h * 0.012f),
    )

    // Little flowers dotted along the grass.
    val flowers = listOf(0.06f, 0.30f, 0.50f, 0.71f, 0.93f)
    for ((k, fx) in flowers.withIndex()) {
        val petal = if (k % 2 == 0) Color(0xFFFFB3C6) else Color(0xFFFFFFFF)
        drawFlower(Offset(w * fx, groundY + h * 0.05f), w * 0.012f, petal)
    }

    // Tufts of grass.
    val tufts = listOf(0.14f, 0.22f, 0.40f, 0.60f, 0.80f, 0.88f)
    for (fx in tufts) {
        drawGrassTuft(Offset(w * fx, groundY), w * 0.018f)
    }
}

private fun DrawScope.drawFlower(center: Offset, r: Float, petalColor: Color) {
    for (sx in listOf(-1f, 0f, 1f)) {
        for (sy in listOf(-1f, 0f, 1f)) {
            if (sx == 0f && sy == 0f) continue
            if (sx != 0f && sy != 0f) continue
            drawCircle(petalColor, r * 0.6f, Offset(center.x + sx * r, center.y + sy * r))
        }
    }
    drawCircle(Color(0xFFFFD23F), r * 0.55f, center)
}

private fun DrawScope.drawGrassTuft(base: Offset, h: Float) {
    val color = Color(0xFF6FA31C)
    for (sx in listOf(-1f, 0f, 1f)) {
        drawLine(
            color = color,
            start = Offset(base.x + sx * h * 0.3f, base.y + h * 0.15f),
            end = Offset(base.x + sx * h * 0.55f, base.y - h * (0.7f - kotlin.math.abs(sx) * 0.2f)),
            strokeWidth = h * 0.12f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/** A little dirt burrow opening straddling the ground line, that a character pops out
 * of during the opening sequence. [alpha] fades the whole thing in/out. */
private fun DrawScope.drawBurrow(cx: Float, groundY: Float, bodyR: Float, alpha: Float) {
    val rimW = bodyR * 1.7f
    val rimH = bodyR * 0.5f
    // Mounded dirt rim sitting on the grass.
    drawOval(
        color = Color(0xFF7A5230).copy(alpha = alpha),
        topLeft = Offset(cx - rimW / 2f, groundY - rimH * 0.55f),
        size = Size(rimW, rimH),
    )
    // Dark opening the character rises out of.
    val openW = rimW * 0.74f
    val openH = rimH * 0.7f
    drawOval(
        color = Color(0xFF2A1B10).copy(alpha = alpha),
        topLeft = Offset(cx - openW / 2f, groundY - openH * 0.5f),
        size = Size(openW, openH),
    )
}

/** One little person: shadow, planted legs with shoes, shorts, a swelling shirt-belly,
 * arms with hands, a head with hair and a face that strains (reddens, mouth opens) as
 * the tummy [scale] grows. */
private fun DrawScope.drawTrumper(center: Offset, bodyR: Float, scale: Float, look: TrumperLook) {
    val r = bodyR * scale
    val feetY = center.y + r
    // strain 0..1 — how bloated this character looks right now
    val strain = ((scale - 1f) / (BLOAT_SCALE - 1f)).coerceIn(0f, 1f)
    val bodyColor = lerp(look.shirt, Color(0xFFE85D5D), strain * 0.55f)

    // Ground shadow.
    drawOval(
        color = Color.Black.copy(alpha = 0.14f),
        topLeft = Offset(center.x - bodyR * 0.65f, feetY + bodyR * 0.04f),
        size = Size(bodyR * 1.3f, bodyR * 0.28f),
    )

    // Legs with shoes (planted on the ground, just under the body).
    val legW = bodyR * 0.22f
    val legH = bodyR * 0.45f
    for (sx in listOf(-1f, 1f)) {
        val legX = center.x + sx * bodyR * 0.45f
        drawRoundRectCompat(
            color = PANTS_COLOR,
            topLeft = Offset(legX - legW / 2f, feetY - legH * 0.4f),
            size = Size(legW, legH * 0.55f),
            corner = legW * 0.3f,
        )
        drawRoundRectCompat(
            color = look.skin,
            topLeft = Offset(legX - legW * 0.36f, feetY - legH * 0.05f),
            size = Size(legW * 0.72f, legH * 0.20f),
            corner = legW * 0.2f,
        )
        drawOval(
            color = look.shoe,
            topLeft = Offset(legX - legW * 0.65f, feetY - legH * 0.04f),
            size = Size(legW * 1.3f, legH * 0.32f),
        )
    }

    // Shorts peeking out below the shirt.
    drawOval(
        color = PANTS_COLOR,
        topLeft = Offset(center.x - r * 0.62f, feetY - r * 0.55f),
        size = Size(r * 1.24f, r * 0.55f),
    )

    // Arms with little hands (lift out a little as the belly swells).
    val armColor = lerp(look.shirt, Color.Black, 0.15f)
    val armLift = strain * bodyR * 0.35f
    for (sx in listOf(-1f, 1f)) {
        val handCenter = Offset(center.x + sx * r * 1.15f, center.y - armLift)
        drawLine(
            color = armColor,
            start = Offset(center.x + sx * r * 0.7f, center.y),
            end = handCenter,
            strokeWidth = bodyR * 0.18f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawCircle(look.skin, bodyR * 0.13f, handCenter)
    }

    // Shirt-belly.
    drawCircle(color = bodyColor, radius = r, center = center)
    // soft belly highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.18f),
        radius = r * 0.55f,
        center = Offset(center.x - r * 0.28f, center.y - r * 0.28f),
    )
    // collar
    val collar = Path().apply {
        moveTo(center.x - r * 0.22f, center.y - r * 0.92f)
        lineTo(center.x, center.y - r * 0.62f)
        lineTo(center.x + r * 0.22f, center.y - r * 0.92f)
        close()
    }
    drawPath(collar, Color.White.copy(alpha = 0.35f))

    // Head sitting on top of the belly.
    val headR = bodyR * 0.55f
    val headCenter = Offset(center.x, center.y - r - headR * 0.55f)

    // Hair drawn behind the head for styles that frame the face (afro, pigtails).
    if (look.hairStyle == 2 || look.hairStyle == 1) {
        drawHairBack(headCenter, headR, look.hair, look.hairStyle)
    }

    drawCircle(color = look.skin, radius = headR, center = headCenter)

    // Ears.
    for (sx in listOf(-1f, 1f)) {
        drawCircle(look.skin, headR * 0.13f, Offset(headCenter.x + sx * headR * 0.92f, headCenter.y + headR * 0.05f))
    }

    // Eyes.
    val eyeDx = headR * 0.42f
    val eyeY = headCenter.y - headR * 0.05f
    val eyeR = headR * 0.16f
    for (sx in listOf(-1f, 1f)) {
        drawCircle(Color.White, eyeR, Offset(headCenter.x + sx * eyeDx, eyeY))
        drawCircle(Color(0xFF2B2B2B), eyeR * 0.55f, Offset(headCenter.x + sx * eyeDx, eyeY))
    }

    // Eyebrows: neutral, but knit together a little as strain builds.
    val browLift = strain * headR * 0.10f
    for (sx in listOf(-1f, 1f)) {
        val bx = headCenter.x + sx * eyeDx
        val by = eyeY - headR * 0.32f + browLift
        drawLine(
            color = look.hair,
            start = Offset(bx - eyeR * 0.9f, by + sx * browLift * 0.6f),
            end = Offset(bx + eyeR * 0.9f, by - sx * browLift * 0.6f),
            strokeWidth = headR * 0.07f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
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

    // Hair drawn on top of/around the head for styles that sit above it.
    if (look.hairStyle == 0 || look.hairStyle == 3) {
        drawHairFront(headCenter, headR, look.hair, look.hairStyle)
    }

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

/** Hair that frames the face from behind: a big afro puff (style 2) or pigtail
 * bunches either side of the head (style 1). */
private fun DrawScope.drawHairBack(headCenter: Offset, headR: Float, color: Color, style: Int) {
    when (style) {
        2 -> drawCircle(color, headR * 1.12f, Offset(headCenter.x, headCenter.y - headR * 0.05f))
        1 -> {
            for (sx in listOf(-1f, 1f)) {
                drawCircle(color, headR * 0.40f, Offset(headCenter.x + sx * headR * 1.18f, headCenter.y + headR * 0.20f))
            }
        }
    }
}

/** Hair that sits on top of the head: short spiky tufts (style 0) or a top-knot bun
 * over a hairline cap (style 3). */
private fun DrawScope.drawHairFront(headCenter: Offset, headR: Float, color: Color, style: Int) {
    when (style) {
        0 -> {
            for (sx in listOf(-2f, -1f, 0f, 1f, 2f)) {
                val spike = Path().apply {
                    val baseX = headCenter.x + sx * headR * 0.32f
                    val baseY = headCenter.y - headR * 0.78f
                    moveTo(baseX - headR * 0.16f, baseY)
                    lineTo(baseX + headR * 0.16f, baseY)
                    lineTo(baseX + sx * headR * 0.06f, baseY - headR * 0.42f)
                    close()
                }
                drawPath(spike, color)
            }
        }
        3 -> {
            // hairline cap
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(headCenter.x - headR, headCenter.y - headR),
                size = Size(headR * 2f, headR * 2f),
            )
            // top-knot bun
            drawCircle(color, headR * 0.28f, Offset(headCenter.x, headCenter.y - headR * 1.05f))
        }
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
