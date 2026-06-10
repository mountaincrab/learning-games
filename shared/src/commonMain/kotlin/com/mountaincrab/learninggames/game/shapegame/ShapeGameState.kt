package com.mountaincrab.learninggames.game.shapegame

import com.mountaincrab.learninggames.game.ObservableGameState
import kotlin.js.JsExport
import kotlin.random.Random

/** A fixed shape outline on the hat. Each game uses one slot per [ShapeType] in a
 * randomised arrangement, so a dropped piece matches exactly one slot. */
@JsExport
class Slot internal constructor(val id: Int, @JsExport.Ignore val type: ShapeType) {
    /** The shape name, for the web side (enums aren't exportable). */
    val typeName: String get() = type.name
}

/** A draggable piece sitting in the tray. [pieceId] is unique per spawn so the UI
 * can tell a freshly-popped piece from the one it replaced. */
@JsExport
class TrayPiece internal constructor(val pieceId: Int, @JsExport.Ignore val type: ShapeType) {
    /** The shape name, for the web side (enums aren't exportable). */
    val typeName: String get() = type.name
}

/** The number of pieces offered at once ([ShapeGameState.TRAY_SIZE], re-exported
 * top-level because companion objects don't export cleanly to JS). */
@JsExport
const val TRAY_SIZE = 3

/**
 * In-memory state for the Magic Hat shape-matching game, shared by the Android app
 * and the webapp. Up to [TRAY_SIZE] pieces are offered at once; dropping one on its
 * matching, still-empty outline fills the slot and a new piece pops out to take its
 * place in the tray. The game is won when every slot is filled. No persistence.
 */
@JsExport
class ShapeGameState : ObservableGameState() {
    /** The twelve target outlines, shuffled fresh each game. */
    var slots: Array<Slot> = generateSlots()
        private set

    @JsExport.Ignore
    var filledIds: Set<Int> = emptySet()
        private set

    /** The tray: [TRAY_SIZE] entries, each a draggable piece or `null` once the
     * board is nearly full and there is nothing left to spawn. */
    var tray: Array<TrayPiece?> = arrayOfNulls(TRAY_SIZE)
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
        tray = tray.copyOf().also { it[idx] = null }
        notifyChanged()
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
        val next = tray.copyOf()
        for (i in next.indices) {
            if (next[i] != null) continue
            val choices = available.filter { it.value > 0 }.keys.toList()
            if (choices.isEmpty()) continue
            val type = choices[Random.nextInt(choices.size)]
            next[i] = TrayPiece(nextPieceId++, type)
            available[type] = (available[type] ?: 1) - 1
        }
        tray = next
        notifyChanged()
    }

    fun reset() {
        slots = generateSlots()
        filledIds = emptySet()
        tray = arrayOfNulls(TRAY_SIZE)
        refillTray()
    }

    @JsExport.Ignore
    companion object {
        const val TRAY_SIZE = 3

        /** One slot per shape, in a randomised order. */
        fun generateSlots(): Array<Slot> =
            ShapeType.entries.shuffled().mapIndexed { i, type -> Slot(i, type) }.toTypedArray()
    }
}
