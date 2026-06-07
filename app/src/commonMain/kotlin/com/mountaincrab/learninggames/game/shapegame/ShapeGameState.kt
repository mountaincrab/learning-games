package com.mountaincrab.learninggames.game.shapegame

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** A fixed shape outline on the hat. Each game uses one slot per [ShapeType] in a
 * randomised arrangement, so a dropped piece matches exactly one slot. */
data class Slot(val id: Int, val type: ShapeType)

/** A draggable piece sitting in the tray. [pieceId] is unique per spawn so the UI
 * can tell a freshly-popped piece from the one it replaced. */
data class TrayPiece(val pieceId: Int, val type: ShapeType)

/**
 * In-memory state for the Magic Hat shape-matching game. Up to [TRAY_SIZE] pieces
 * are offered at once; dropping one on its matching, still-empty outline fills the
 * slot and a new piece pops out to take its place in the tray. The game is won when
 * every slot is filled.
 *
 * Created via `remember { ShapeGameState() }` — no persistence.
 */
class ShapeGameState {
    /** The twelve target outlines, shuffled fresh each game. */
    var slots by mutableStateOf(generateSlots())
        private set

    var filledIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** The tray: [TRAY_SIZE] entries, each a draggable piece or `null` once the
     * board is nearly full and there is nothing left to spawn. */
    var tray by mutableStateOf<List<TrayPiece?>>(List(TRAY_SIZE) { null })
        private set

    private var nextPieceId = 0

    val isWon: Boolean get() = filledIds.size == slots.size

    init {
        refillTray()
    }

    fun isFilled(slotId: Int): Boolean = slotId in filledIds

    /**
     * Attempt to drop the tray piece [pieceId] onto [slotId]. On a correct match the
     * slot is filled and the piece removed from the tray (call [refillTray] to pop a
     * replacement). Returns true on success.
     */
    fun tryDrop(slotId: Int, pieceId: Int): Boolean {
        if (slotId in filledIds) return false
        val idx = tray.indexOfFirst { it?.pieceId == pieceId }
        if (idx < 0) return false
        val piece = tray[idx] ?: return false
        val slot = slots.firstOrNull { it.id == slotId } ?: return false
        if (slot.type != piece.type) return false
        filledIds = filledIds + slot.id
        tray = tray.toMutableList().also { it[idx] = null }
        return true
    }

    /**
     * Fill any empty tray positions with new pieces. Only types that still have an
     * unfilled slot (and that aren't already waiting in the tray) are offered, so
     * every piece on screen is always placeable.
     */
    fun refillTray() {
        val remaining = slots.filter { it.id !in filledIds }
        val available = remaining.groupingBy { it.type }.eachCount().toMutableMap()
        // Don't offer a type more times than there are empty slots for it.
        tray.filterNotNull().forEach { p ->
            available[p.type] = (available[p.type] ?: 0) - 1
        }
        val next = tray.toMutableList()
        for (i in next.indices) {
            if (next[i] != null) continue
            val choices = available.filter { it.value > 0 }.keys.toList()
            if (choices.isEmpty()) continue
            val type = choices[Random.nextInt(choices.size)]
            next[i] = TrayPiece(nextPieceId++, type)
            available[type] = (available[type] ?: 1) - 1
        }
        tray = next
    }

    fun reset() {
        slots = generateSlots()
        filledIds = emptySet()
        tray = List(TRAY_SIZE) { null }
        refillTray()
    }

    companion object {
        const val TRAY_SIZE = 3

        /** One slot per shape, in a randomised order. */
        fun generateSlots(): List<Slot> =
            ShapeType.values().toList().shuffled().mapIndexed { i, type -> Slot(i, type) }
    }
}
