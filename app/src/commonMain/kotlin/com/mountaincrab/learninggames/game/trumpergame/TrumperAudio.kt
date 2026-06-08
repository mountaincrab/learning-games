package com.mountaincrab.learninggames.game.trumpergame

import androidx.compose.runtime.Composable

/**
 * Tiny sound layer for the Stinky Trumpers game. Audio is loaded from bundled OGG
 * files in `res/raw` (`fart_1.ogg`, `fart_2.ogg`, …); a random one is picked on each
 * release. Kept multiplatform via `expect`/`actual`, so the app still builds (and
 * stays silent) if no files have been added yet.
 */
interface TrumperAudio {
    /** Play a random fart sound effect, if any are bundled. */
    fun playFart()
}

/** Remembers a platform [TrumperAudio], releasing it when it leaves the composition. */
@Composable
expect fun rememberTrumperAudio(): TrumperAudio
