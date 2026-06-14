package com.mountaincrab.learninggames.game.trumpergame

import androidx.compose.runtime.Composable

/**
 * Tiny sound layer for the Stinky Trumpers game. Audio is loaded from bundled OGG
 * files in `res/raw` (`fart_1.ogg`, `fart_2.ogg`, …); a random one is picked on each
 * release. The opening sequence shouts each character's name from `name_<n>.ogg`.
 * Kept multiplatform via `expect`/`actual`, so the app still builds (and stays silent)
 * if no files have been added yet.
 */
interface TrumperAudio {
    /** Play a random fart sound effect, if any are bundled. */
    fun playFart()

    /** Shout character [id]'s name (0-based, column order), if its clip is bundled. */
    fun playName(id: Int)

    /** Play the round-win celebration cheer, if it's bundled. */
    fun playCheer()
}

/** Remembers a platform [TrumperAudio], releasing it when it leaves the composition. */
@Composable
expect fun rememberTrumperAudio(): TrumperAudio
