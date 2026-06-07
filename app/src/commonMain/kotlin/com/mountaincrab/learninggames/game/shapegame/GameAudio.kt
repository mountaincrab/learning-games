package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.runtime.Composable

/**
 * Tiny sound layer for the Magic Hat game. Audio is synthesised on the platform side
 * (no asset files) so it stays fully multiplatform via `expect`/`actual`.
 */
interface GameAudio {
    /** A short, sparkly "magic" chime, played when a shape is matched. */
    fun playMagicChime()

    /** Start the gentle looping background music. Safe to call repeatedly. */
    fun startMusic()

    /** Stop the background music. */
    fun stopMusic()
}

/** Remembers a platform [GameAudio], releasing it when it leaves the composition. */
@Composable
expect fun rememberGameAudio(): GameAudio
