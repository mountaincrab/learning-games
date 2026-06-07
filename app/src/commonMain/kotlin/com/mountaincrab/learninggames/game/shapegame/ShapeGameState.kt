package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** A fixed shape outline on the hat. Slot [type]s are unique so a dropped piece
 * matches exactly one slot. */
data class Slot(val id: Int, val type: ShapeType)

/**
 * In-memory state for the Magic Hat shape-matching game. One draggable piece is
 * presented at a time; dropping it on its matching, still-empty outline fills the
 * slot and the next piece is spawned. The game is won when every slot is filled.
 *
 * Created via `remember { ShapeGameState() }` — no persistence.
 */
class ShapeGameState(
    val slots: List<Slot> = defaultSlots(),
) {
    var filledIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** The shape currently waiting on the right to be dragged, or null while the
     * wand is waving / once the game is won. */
    var currentPiece by mutableStateOf<ShapeType?>(null)
        private set

    val isWon: Boolean get() = filledIds.size == slots.size

    init {
        spawnNext()
    }

    fun isFilled(slotId: Int): Boolean = slotId in filledIds

    /**
     * Attempt to drop the current piece onto [slotId]. Returns true on a correct
     * match (slot then becomes filled and [currentPiece] is cleared so the caller
     * can play the wand animation before calling [spawnNext]).
     */
    fun tryDrop(slotId: Int): Boolean {
        val piece = currentPiece ?: return false
        val slot = slots.firstOrNull { it.id == slotId } ?: return false
        if (slot.id in filledIds || slot.type != piece) return false
        filledIds = filledIds + slot.id
        currentPiece = null
        return true
    }

    /** Spawn the next piece for a random still-empty slot (no-op once won). */
    fun spawnNext() {
        val remaining = slots.filter { it.id !in filledIds }
        currentPiece = if (remaining.isEmpty()) null
        else remaining[Random.nextInt(remaining.size)].type
    }

    fun reset() {
        filledIds = emptySet()
        spawnNext()
    }

    companion object {
        fun defaultSlots(): List<Slot> = listOf(
            ShapeType.CIRCLE,
            ShapeType.SQUARE,
            ShapeType.TRIANGLE,
            ShapeType.PENTAGON,
            ShapeType.DIAMOND,
            ShapeType.STAR,
        ).mapIndexed { i, type -> Slot(i, type) }
    }
}
