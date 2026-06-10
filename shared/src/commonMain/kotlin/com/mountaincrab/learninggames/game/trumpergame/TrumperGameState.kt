package com.mountaincrab.learninggames.game.trumpergame

import com.mountaincrab.learninggames.game.ObservableGameState
import kotlin.js.JsExport
import kotlin.random.Random

/** The number of characters ([TrumperGameState.COUNT], re-exported top-level
 * because companion objects don't export cleanly to JS). */
@JsExport
const val TRUMPER_COUNT = 4

/**
 * In-memory state for the "Stinky Trumpers" game, shared by the Android app and the
 * webapp. [TRUMPER_COUNT] little characters stand in a row; at random intervals one
 * of them becomes bloated (tummy swells). Tapping a bloated tummy [release]s the gas
 * and clears it. There is no win condition — it is an endless tap-to-play game for
 * young children. No persistence.
 */
@JsExport
class TrumperGameState : ObservableGameState() {
    /** Ids of the characters whose tummies are currently swollen. */
    @JsExport.Ignore
    var bloated: Set<Int> = emptySet()
        private set

    fun isBloated(id: Int): Boolean = id in bloated

    /** Make one random not-yet-bloated character start swelling. No-op if all are. */
    fun bloatRandom() {
        val candidates = (0 until COUNT).filter { it !in bloated }
        if (candidates.isEmpty()) return
        bloated = bloated + candidates[Random.nextInt(candidates.size)]
        notifyChanged()
    }

    /** Release the gas for [id]. Returns true only if it was actually bloated. */
    fun release(id: Int): Boolean {
        if (id !in bloated) return false
        bloated = bloated - id
        notifyChanged()
        return true
    }

    @JsExport.Ignore
    companion object {
        const val COUNT = 4
    }
}
