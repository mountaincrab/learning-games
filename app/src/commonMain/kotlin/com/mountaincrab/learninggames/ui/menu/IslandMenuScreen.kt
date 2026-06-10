package com.mountaincrab.learninggames.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

private const val COLS = 4

@Composable
fun IslandMenuScreen(
    onSelectGame: (GameId) -> Unit,
    onOpenSettings: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF35A3D9), Color(0xFF1E78B5), Color(0xFF145DA0))
                )
            )
    ) {
        // The island and its decorations, drawn behind the pads.
        Canvas(modifier = Modifier.fillMaxSize()) { drawIsland() }

        val games = GAME_CATALOG
        val rows = ceil(games.size / COLS.toFloat()).toInt()

        val islandLeft = maxWidth * 0.06f
        val islandTop = maxHeight * 0.14f
        val islandW = maxWidth * 0.88f
        val islandH = maxHeight * 0.74f
        val cellW = islandW / COLS
        val cellH = islandH / rows
        val padSize = minOf(cellW, cellH) * 0.74f

        games.forEachIndexed { i, game ->
            val col = i % COLS
            val row = i / COLS
            val centerX = islandLeft + cellW * (col + 0.5f)
            val centerY = islandTop + cellH * (row + 0.5f)
            GamePad(
                game = game,
                size = padSize,
                modifier = Modifier
                    .offset(x = centerX - padSize / 2f, y = centerY - padSize / 2f)
                    .size(padSize),
                onClick = { game.id?.let(onSelectGame) },
            )
        }

        Text(
            text = "Learning Games",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        )

        // Settings (gear) button, top-right.
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(52.dp)
                .clickable(onClick = onOpenSettings),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("⚙", fontSize = 24.sp, color = Color(0xFF2B2B2B))
            }
        }
    }
}

@Composable
private fun GamePad(
    game: GameEntry,
    size: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(modifier = if (game.locked) modifier else modifier.clickable(onClick = onClick)) {
        // The pad itself: a chunky colored plinth seen slightly from above.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val base = game.padColor
            val topC = Offset(c.x, c.y - r * 0.04f)
            // Ground shadow.
            drawOval(
                Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(c.x - r * 0.95f, c.y + r * 0.50f),
                size = Size(r * 1.9f, r * 0.5f),
            )
            // Side of the plinth peeking out below the top face.
            drawCircle(base.darken(0.62f), r * 0.96f, Offset(c.x, c.y + r * 0.08f))
            // Top face with a soft light from the upper-left.
            drawCircle(base, r * 0.92f, topC)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)),
                    center = Offset(c.x - r * 0.28f, c.y - r * 0.40f),
                    radius = r * 1.1f,
                ),
                radius = r * 0.92f,
                center = topC,
            )
            drawCircle(base.darken(0.75f), r * 0.92f, topC, style = Stroke(width = r * 0.05f))
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.18f)) {
                game.icon(this)
            }
        }
        if (game.locked) {
            // Little lock plaque on the front of the pad.
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = size * 0.03f),
                shape = RoundedCornerShape(size * 0.07f),
                color = Color(0xFF3E4A3D),
                shadowElevation = 2.dp,
            ) {
                Text(
                    "🔒",
                    fontSize = (size.value * 0.17f).sp,
                    modifier = Modifier.padding(horizontal = size * 0.07f, vertical = size * 0.015f),
                )
            }
        }
    }
}

internal fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)

// --- Island drawing ------------------------------------------------------------

private fun DrawScope.drawIsland() {
    val w = size.width
    val h = size.height
    val m = minOf(w, h)
    val sand = Color(0xFFE9D8A6)
    val sandDark = Color(0xFFD9C189)
    val grass = Color(0xFF6FBF3B)
    val grassDark = Color(0xFF4F9E28)

    val left = w * 0.05f
    val top = h * 0.12f
    val islandW = w * 0.90f
    val islandH = h * 0.80f

    // Ripple rings in the water around the island.
    drawIslandBlobOutline(
        Offset(left - w * 0.030f, top - h * 0.055f),
        Size(islandW + w * 0.060f, islandH + h * 0.11f),
        Color.White.copy(alpha = 0.14f), m * 0.008f,
    )
    drawIslandBlobOutline(
        Offset(left - w * 0.060f, top - h * 0.105f),
        Size(islandW + w * 0.120f, islandH + h * 0.21f),
        Color.White.copy(alpha = 0.07f), m * 0.007f,
    )

    // Sand base, with a wet-sand edge.
    drawIslandBlob(Offset(left - w * 0.012f, top - h * 0.022f), Size(islandW + w * 0.024f, islandH + h * 0.044f), sand)
    drawIslandBlobOutline(Offset(left - w * 0.012f, top - h * 0.022f), Size(islandW + w * 0.024f, islandH + h * 0.044f), sandDark, m * 0.006f)
    // Grass on top, with a darker outline for depth.
    drawIslandBlob(Offset(left, top), Size(islandW, islandH), grass)
    drawIslandBlobOutline(Offset(left, top), Size(islandW, islandH), grassDark, m * 0.008f)

    // Winding dirt path between the pads.
    val path = Path().apply {
        moveTo(w * 0.06f, h * 0.50f)
        cubicTo(w * 0.28f, h * 0.24f, w * 0.42f, h * 0.78f, w * 0.64f, h * 0.48f)
        quadraticTo(w * 0.80f, h * 0.28f, w * 0.94f, h * 0.56f)
    }
    drawPath(path, Color(0xFFE5D5A5).copy(alpha = 0.9f), style = Stroke(width = m * 0.05f, cap = StrokeCap.Round))

    // Darker grass patches for texture.
    val patches = listOf(
        floatArrayOf(0.18f, 0.16f, 0.05f, 0.025f), floatArrayOf(0.55f, 0.14f, 0.06f, 0.022f),
        floatArrayOf(0.86f, 0.24f, 0.045f, 0.02f), floatArrayOf(0.10f, 0.52f, 0.05f, 0.024f),
        floatArrayOf(0.46f, 0.50f, 0.055f, 0.02f), floatArrayOf(0.90f, 0.60f, 0.04f, 0.02f),
        floatArrayOf(0.24f, 0.86f, 0.06f, 0.024f), floatArrayOf(0.66f, 0.88f, 0.05f, 0.022f),
    )
    for (p in patches) {
        drawOval(
            grassDark.copy(alpha = 0.45f),
            topLeft = Offset(w * (p[0] - p[2]), h * (p[1] - p[3]) - 0f),
            size = Size(w * p[2] * 2f, h * p[3] * 2f + m * 0.01f),
        )
    }

    // Decorations around the rim (pads draw over anything they overlap).
    val deco = m * 0.075f
    drawTree(Offset(w * 0.085f, h * 0.235f), deco)
    drawTree(Offset(w * 0.925f, h * 0.32f), deco * 0.9f)
    drawTree(Offset(w * 0.095f, h * 0.70f), deco * 0.85f)
    drawTree(Offset(w * 0.885f, h * 0.80f), deco)
    drawBush(Offset(w * 0.305f, h * 0.125f), deco * 0.7f)
    drawBush(Offset(w * 0.50f, h * 0.115f), deco * 0.6f)
    drawBush(Offset(w * 0.70f, h * 0.895f), deco * 0.7f)
    drawRock(Offset(w * 0.165f, h * 0.895f), deco * 0.55f)
    drawRock(Offset(w * 0.835f, h * 0.135f), deco * 0.5f)
    val flowers = listOf(
        0.225f to 0.33f, 0.62f to 0.27f, 0.78f to 0.52f,
        0.35f to 0.845f, 0.565f to 0.78f, 0.135f to 0.42f,
    )
    for ((fx, fy) in flowers) drawFlower(Offset(w * fx, h * fy), deco * 0.22f)
}

private fun DrawScope.drawTree(c: Offset, s: Float) {
    drawRect(Color(0xFF7A4A21), topLeft = Offset(c.x - s * 0.08f, c.y - s * 0.05f), size = Size(s * 0.16f, s * 0.5f))
    drawCircle(Color(0xFF3E8A2E), s * 0.34f, Offset(c.x - s * 0.18f, c.y - s * 0.22f))
    drawCircle(Color(0xFF49A337), s * 0.38f, Offset(c.x + s * 0.10f, c.y - s * 0.34f))
    drawCircle(Color(0xFF3E8A2E), s * 0.28f, Offset(c.x + s * 0.26f, c.y - s * 0.06f))
    drawCircle(Color.White.copy(alpha = 0.15f), s * 0.13f, Offset(c.x + s * 0.04f, c.y - s * 0.46f))
}

private fun DrawScope.drawBush(c: Offset, s: Float) {
    drawCircle(Color(0xFF3E8A2E), s * 0.42f, Offset(c.x - s * 0.22f, c.y))
    drawCircle(Color(0xFF49A337), s * 0.50f, Offset(c.x + s * 0.14f, c.y - s * 0.08f))
    drawCircle(Color.White.copy(alpha = 0.12f), s * 0.16f, Offset(c.x + s * 0.16f, c.y - s * 0.30f))
}

private fun DrawScope.drawRock(c: Offset, s: Float) {
    drawOval(Color(0xFF8D99A6), topLeft = Offset(c.x - s * 0.5f, c.y - s * 0.32f), size = Size(s, s * 0.64f))
    drawOval(Color(0xFFB0BAC5), topLeft = Offset(c.x - s * 0.34f, c.y - s * 0.30f), size = Size(s * 0.5f, s * 0.3f))
}

private fun DrawScope.drawFlower(c: Offset, s: Float) {
    for (i in 0 until 5) {
        val a = (i / 5f) * 2f * kotlin.math.PI.toFloat()
        drawCircle(
            Color(0xFFFFB3C6),
            s * 0.45f,
            Offset(c.x + s * 0.62f * kotlin.math.cos(a), c.y + s * 0.62f * kotlin.math.sin(a)),
        )
    }
    drawCircle(Color(0xFFFFD23F), s * 0.4f, c)
}

private fun islandPath(topLeft: Offset, size: Size): Path {
    // A rounded, slightly irregular blob approximated with a rounded rectangle oval.
    return Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = topLeft.x,
                top = topLeft.y,
                right = topLeft.x + size.width,
                bottom = topLeft.y + size.height,
                radiusX = size.minDimension * 0.45f,
                radiusY = size.minDimension * 0.45f,
            )
        )
    }
}

private fun DrawScope.drawIslandBlob(topLeft: Offset, size: Size, color: Color) {
    drawPath(islandPath(topLeft, size), color)
}

private fun DrawScope.drawIslandBlobOutline(
    topLeft: Offset, size: Size, color: Color, stroke: Float,
) {
    drawPath(islandPath(topLeft, size), color, style = Stroke(width = stroke))
}

private val Size.minDimension: Float get() = minOf(width, height)
