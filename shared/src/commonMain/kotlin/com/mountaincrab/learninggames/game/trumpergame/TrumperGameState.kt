package com.mountaincrab.learninggames.game.trumpergame

import com.mountaincrab.learninggames.game.ObservableGameState
import kotlin.js.JsExport
import kotlin.random.Random

/** The number of characters ([TrumperGameState.COUNT], re-exported top-level
 * because companion objects don't export cleanly to JS). */
@JsExport
const val TRUMPER_COUNT = 4

/** How many tummies must be tooted to win a round ([TrumperGameState.GOAL],
 * re-exported top-level for the same reason as [TRUMPER_COUNT]). */
@JsExport
const val TRUMPER_GOAL = 10

/**
 * In-memory state for the "Stinky Trumpers" game, shared by the Android app and the
 * webapp. [TRUMPER_COUNT] little characters stand in a row; at random intervals one
 * of them becomes bloated (tummy swells). Tapping a bloated tummy [release]s the gas,
 * clears it and counts toward the round's goal. After [TRUMPER_GOAL] toots the round
 * [isWon] — the platforms play a celebration and the characters wave goodbye into
 * their burrows — then [reset] starts a fresh round. No persistence.
 */
@JsExport
class TrumperGameState : ObservableGameState() {
    /** Ids of the characters whose tummies are currently swollen. */
    @JsExport.Ignore
    var bloated: Set<Int> = emptySet()
        private set

    /** How many tummies have been tooted this round (0..[GOAL]). */
    var score: Int = 0
        private set

    /** True once [score] reaches [GOAL] — the round is over. */
    val isWon: Boolean get() = score >= GOAL

    fun isBloated(id: Int): Boolean = id in bloated

    /** Make one random not-yet-bloated character start swelling. No-op once the round
     * is won or if everyone is already bloated. */
    fun bloatRandom() {
        if (isWon) return
        val candidates = (0 until COUNT).filter { it !in bloated }
        if (candidates.isEmpty()) return
        bloated = bloated + candidates[Random.nextInt(candidates.size)]
        notifyChanged()
    }

    /** Release the gas for [id], counting it toward the goal. Returns true only if it
     * was actually bloated. */
    fun release(id: Int): Boolean {
        if (id !in bloated) return false
        bloated = bloated - id
        if (score < GOAL) score += 1
        notifyChanged()
        return true
    }

    /** Start a fresh round: clear every tummy and the score. */
    fun reset() {
        bloated = emptySet()
        score = 0
        notifyChanged()
    }

    @JsExport.Ignore
    companion object {
        const val COUNT = 4
        const val GOAL = 10
    }
}
