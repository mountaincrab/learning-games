package com.mountaincrab.learninggames.ui.menu

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Games that can be launched from the island menu. */
enum class GameId {
    MAGIC_HAT,
    STINKY_TRUMPERS,
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
    add(
        GameEntry(
            id = GameId.STINKY_TRUMPERS,
            title = "Stinky Trumpers",
            locked = false,
            padColor = Color(0xFF8AC926),
            icon = { drawMiniTrumper() },
        )
    )
    // Locked placeholders — replace with real games over time.
    val placeholderColors = listOf(
        Color(0xFF4ECDC4), Color(0xFFFF6B6B), Color(0xFFFFB703), Color(0xFF6FBF3B),
        Color(0xFF577590), Color(0xFFF4A261), Color(0xFF9B5DE5), Color(0xFF00BBF9),
        Color(0xFFEF476F), Color(0xFF118AB2),
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

private fun DrawScope.drawMiniTrumper() {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val bodyR = minOf(w, h) * 0.26f
    val center = Offset(cx, h * 0.60f)
    // round bloated body
    drawCircle(Color(0xFFFF8FAB), bodyR, center)
    // head
    val headR = bodyR * 0.55f
    val headCenter = Offset(cx, center.y - bodyR - headR * 0.55f)
    drawCircle(Color(0xFFFF8FAB), headR, headCenter)
    // eyes
    for (sx in listOf(-1f, 1f)) {
        drawCircle(Color(0xFF2B2B2B), headR * 0.16f, Offset(headCenter.x + sx * headR * 0.42f, headCenter.y))
    }
    // little stink puff
    drawCircle(Color(0xFF9CCC3C).copy(alpha = 0.7f), bodyR * 0.35f, Offset(center.x + bodyR * 1.1f, center.y - bodyR * 0.5f))
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
