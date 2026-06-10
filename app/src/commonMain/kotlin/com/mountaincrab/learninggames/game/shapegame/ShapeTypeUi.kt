package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.ui.graphics.Color

/** Compose colour for a shape; the shared module stores colours as ARGB ints. */
val ShapeType.fillColor: Color get() = Color(fillColorArgb)
