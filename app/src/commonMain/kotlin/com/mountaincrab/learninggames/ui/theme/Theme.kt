package com.mountaincrab.learninggames.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Bright, friendly palette for a children's game.
private val Sky = Color(0xFF1E6FB0)
private val Grass = Color(0xFF6FBF3B)
private val Sunshine = Color(0xFFFFD23F)
private val Coral = Color(0xFFFF6B6B)
private val Plum = Color(0xFF6A2C8C)

private val LearningGamesColors = lightColorScheme(
    primary = Plum,
    onPrimary = Color.White,
    secondary = Sunshine,
    onSecondary = Color(0xFF2B2B2B),
    tertiary = Coral,
    onTertiary = Color.White,
    background = Sky,
    onBackground = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF2B2B2B),
)

private val LearningGamesShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/** Convenience palette references used directly by the hand-drawn Canvas screens. */
object GamePalette {
    val water = Sky
    val grass = Grass
    val sunshine = Sunshine
    val coral = Coral
    val plum = Plum
}

@Composable
fun LearningGamesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LearningGamesColors,
        shapes = LearningGamesShapes,
        content = content,
    )
}
