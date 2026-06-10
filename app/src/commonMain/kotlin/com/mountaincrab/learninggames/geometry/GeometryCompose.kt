package com.mountaincrab.learninggames.geometry

import androidx.compose.ui.geometry.Offset

/** Converts a shared [Vec2] (stage pixels) to a Compose [Offset]. */
fun Vec2.toOffset(): Offset = Offset(x, y)
