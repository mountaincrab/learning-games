package com.mountaincrab.learninggames

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mountaincrab.learninggames.game.shapegame.ShapeGameScreen
import com.mountaincrab.learninggames.game.trumpergame.TrumperGameScreen
import com.mountaincrab.learninggames.ui.menu.GameId
import com.mountaincrab.learninggames.ui.menu.IslandMenuScreen
import com.mountaincrab.learninggames.ui.settings.SettingsScreen
import com.mountaincrab.learninggames.ui.theme.LearningGamesTheme

/** Top-level destinations. Kept as a tiny in-app state machine so navigation stays
 * fully multiplatform (no navigation-compose dependency). */
private sealed interface Screen {
    data object Menu : Screen
    data object Settings : Screen
    data class Game(val id: GameId) : Screen
}

@Composable
fun App() {
    LearningGamesTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Menu) }

        // System back returns to the menu from any sub-screen.
        PlatformBackHandler(enabled = screen != Screen.Menu) { screen = Screen.Menu }

        when (val s = screen) {
            is Screen.Menu -> IslandMenuScreen(
                onSelectGame = { screen = Screen.Game(it) },
                onOpenSettings = { screen = Screen.Settings },
            )
            is Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Menu })
            is Screen.Game -> when (s.id) {
                GameId.MAGIC_HAT -> ShapeGameScreen(onBack = { screen = Screen.Menu })
                GameId.STINKY_TRUMPERS -> TrumperGameScreen(onBack = { screen = Screen.Menu })
            }
        }
    }
}
