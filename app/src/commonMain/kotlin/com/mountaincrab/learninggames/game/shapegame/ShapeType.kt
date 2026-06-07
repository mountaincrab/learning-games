package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.ui.graphics.Color

/** The shapes a child matches in the Magic Hat game. There are twelve so a full
 * game shows every shape once, in a randomised arrangement. */
enum class ShapeType(val displayName: String, val fillColor: Color) {
    CIRCLE("circle", Color(0xFFFFD23F)),
    SQUARE("square", Color(0xFF4ECDC4)),
    TRIANGLE("triangle", Color(0xFF6FBF3B)),
    PENTAGON("pentagon", Color(0xFF9B5DE5)),
    DIAMOND("diamond", Color(0xFFFF6B6B)),
    RECTANGLE("rectangle", Color(0xFF577590)),
    DOME("arch", Color(0xFFF4A261)),
    STAR("star", Color(0xFFFFB703)),
    HEXAGON("hexagon", Color(0xFF00BBF9)),
    HEART("heart", Color(0xFFF15BB5)),
    OVAL("oval", Color(0xFF80ED99)),
    CROSS("cross", Color(0xFFE07A5F)),
}
