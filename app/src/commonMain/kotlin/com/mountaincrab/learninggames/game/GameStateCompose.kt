package com.mountaincrab.learninggames.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Bridges a shared (Compose-free) [ObservableGameState] into Compose: returns a tick
 * that bumps on every state mutation. Reading the returned value happens inside the
 * caller's recompose scope, so the calling screen recomposes whenever the game state
 * changes — call it from the screen's content lambda and ignore the result.
 */
@Composable
fun observeGameState(state: ObservableGameState): Int {
    var tick by remember(state) { mutableStateOf(0) }
    DisposableEffect(state) {
        state.onChange = { tick++ }
        onDispose { state.onChange = null }
    }
    return tick
}
