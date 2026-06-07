package com.mountaincrab.learninggames.ui.menu

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Games that can be launched from the island menu. */
enum class GameId {
    MAGIC_HAT,
}

/**
 * One pad on the island. [id] is non-null only for playable games; locked entries
 * ([locked] = true) are placeholders for games not yet built.
 */
data class GameEntry(
    val id: GameId?,
    val title: String,
    val locked: Boolean,
    val padColor: Color,
    val icon: DrawScope.() -> Unit,
)

/** The full catalog shown on the island: one playable game + locked placeholders. */
val GAME_CATALOG: List<GameEntry> = buildList {
    add(
        GameEntry(
            id = GameId.MAGIC_HAT,
            title = "Magic Hat",
            locked = false,
            padColor = Color(0xFF7B3FA0),
            icon = { drawMiniHat() },
        )
    )
    // Locked placeholders — replace with real games over time.
    val placeholderColors = listOf(
        Color(0xFF4ECDC4), Color(0xFFFF6B6B), Color(0xFFFFB703), Color(0xFF6FBF3B),
        Color(0xFF577590), Color(0xFFF4A261), Color(0xFF9B5DE5), Color(0xFF00BBF9),
        Color(0xFFEF476F), Color(0xFF118AB2), Color(0xFF06D6A0),
    )
    placeholderColors.forEachIndexed { i, c ->
        add(
            GameEntry(
                id = null,
                title = "Coming soon",
                locked = true,
                padColor = c,
                icon = { drawMiniDiamond(c) },
            )
        )
    }
}

private fun DrawScope.drawMiniHat() {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    // body
    drawRect(
        Color(0xFF7B3FA0),
        topLeft = Offset(cx - w * 0.20f, h * 0.28f),
        size = Size(w * 0.40f, h * 0.42f),
    )
    // brim
    drawOval(
        Color(0xFF5E2C7E),
        topLeft = Offset(cx - w * 0.34f, h * 0.62f),
        size = Size(w * 0.68f, h * 0.16f),
    )
    // band
    drawRect(
        Color(0xFFFFD23F),
        topLeft = Offset(cx - w * 0.20f, h * 0.58f),
        size = Size(w * 0.40f, h * 0.08f),
    )
}

private fun DrawScope.drawMiniDiamond(c: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) * 0.28f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r, cy)
        lineTo(cx, cy + r)
        lineTo(cx - r, cy)
        close()
    }
    drawPath(path, c)
}
