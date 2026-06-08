package com.mountaincrab.learninggames.game.trumpergame

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * In-memory state for the "Stinky Trumpers" game. [COUNT] little characters stand
 * in a row; at random intervals one of them becomes [bloated] (tummy swells). Tapping
 * a bloated tummy [release]s the gas and clears it. There is no win condition — it is
 * an endless tap-to-play game for young children.
 *
 * Created via `remember { TrumperGameState() }` — no persistence.
 */
class TrumperGameState {
    /** Ids of the characters whose tummies are currently swollen. */
    var bloated by mutableStateOf<Set<Int>>(emptySet())
        private set

    fun isBloated(id: Int): Boolean = id in bloated

    /** Make one random not-yet-bloated character start swelling. No-op if all are. */
    fun bloatRandom() {
        val candidates = (0 until COUNT).filter { it !in bloated }
        if (candidates.isEmpty()) return
        bloated = bloated + candidates[Random.nextInt(candidates.size)]
    }

    /** Release the gas for [id]. Returns true only if it was actually bloated. */
    fun release(id: Int): Boolean {
        if (id !in bloated) return false
        bloated = bloated - id
        return true
    }

    companion object {
        const val COUNT = 4
    }
}
