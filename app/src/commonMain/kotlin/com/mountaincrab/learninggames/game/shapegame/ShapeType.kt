package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.ui.graphics.Color

/** The shapes a child matches in the Magic Hat game. */
enum class ShapeType(val displayName: String, val fillColor: Color) {
    CIRCLE("circle", Color(0xFFFFD23F)),
    SQUARE("square", Color(0xFF4ECDC4)),
    TRIANGLE("triangle", Color(0xFF6FBF3B)),
    PENTAGON("pentagon", Color(0xFF9B5DE5)),
    DIAMOND("diamond", Color(0xFFFF6B6B)),
    RECTANGLE("rectangle", Color(0xFF577590)),
    DOME("arch", Color(0xFFF4A261)),
    STAR("star", Color(0xFFFFB703)),
}
