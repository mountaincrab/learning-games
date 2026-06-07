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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF2E8BC0), Color(0xFF145DA0))
                )
            )
    ) {
        // The island itself, drawn behind the pads.
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
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = if (game.locked) modifier else modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (game.locked) Color(0xFF8A8A8A) else Color(0xFFEFE6D2),
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.14f)) {
                game.icon(this)
            }
            if (game.locked) {
                // Dim + padlock overlay for not-yet-available games.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🔒", fontSize = (size.value * 0.34f).sp)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIsland() {
    val w = size.width
    val h = size.height
    // sandy shoreline (slightly larger, behind the grass)
    val sand = Color(0xFFE9D8A6)
    val grass = Color(0xFF6FBF3B)
    val grassDark = Color(0xFF4F9E28)

    val left = w * 0.05f
    val top = h * 0.12f
    val islandW = w * 0.90f
    val islandH = h * 0.80f

    // sand base
    drawIslandBlob(Offset(left - w * 0.01f, top - h * 0.02f), Size(islandW + w * 0.02f, islandH + h * 0.04f), sand)
    // grass on top
    drawIslandBlob(Offset(left, top), Size(islandW, islandH), grass)
    // a darker grass outline for depth
    drawIslandBlobOutline(Offset(left, top), Size(islandW, islandH), grassDark, w * 0.006f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.islandPath(topLeft: Offset, size: Size): Path {
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIslandBlob(topLeft: Offset, size: Size, color: Color) {
    drawPath(islandPath(topLeft, size), color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIslandBlobOutline(
    topLeft: Offset, size: Size, color: Color, stroke: Float,
) {
    drawPath(islandPath(topLeft, size), color, style = Stroke(width = stroke))
}

private val Size.minDimension: Float get() = minOf(width, height)
