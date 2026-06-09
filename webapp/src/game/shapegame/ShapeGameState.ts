import { ALL_SHAPE_TYPES, ShapeType } from './ShapeType'

/** A fixed shape outline on the hat. Each game uses one slot per `ShapeType` in a
 * randomised arrangement, so a dropped piece matches exactly one slot. */
export interface Slot {
  id: number
  type: ShapeType
}

/** A draggable piece sitting in the tray. `pieceId` is unique per spawn so the UI
 * can tell a freshly-popped piece from the one it replaced. */
export interface TrayPiece {
  pieceId: number
  type: ShapeType
}

export const TRAY_SIZE = 3

/** One slot per shape, in a randomised order. */
function generateSlots(): Slot[] {
  const shuffled = [...ALL_SHAPE_TYPES]
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
  }
  return shuffled.map((type, i) => ({ id: i, type }))
}

/**
 * In-memory state for the Magic Hat shape-matching game — a direct port of the
 * Android `ShapeGameState`. Up to `TRAY_SIZE` pieces are offered at once; dropping
 * one on its matching, still-empty outline fills the slot and a new piece pops out
 * to take its place. The game is won when every slot is filled. No persistence.
 */
export class ShapeGameState {
  slots: Slot[] = generateSlots()
  filledIds = new Set<number>()
  tray: (TrayPiece | null)[] = Array(TRAY_SIZE).fill(null)
  private nextPieceId = 0

  constructor() {
    this.refillTray()
  }

  get isWon(): boolean {
    return this.filledIds.size === this.slots.length
  }

  isFilled(slotId: number): boolean {
    return this.filledIds.has(slotId)
  }

  /**
   * Attempt to drop the tray piece `pieceId` onto `slotId`. On a correct match the
   * slot is filled and the piece removed from the tray (call `refillTray` to pop a
   * replacement). Returns true on success.
   */
  tryDrop(slotId: number, pieceId: number): boolean {
    if (this.filledIds.has(slotId)) return false
    const idx = this.tray.findIndex((p) => p?.pieceId === pieceId)
    if (idx < 0) return false
    const piece = this.tray[idx]!
    const slot = this.slots.find((s) => s.id === slotId)
    if (!slot || slot.type !== piece.type) return false
    this.filledIds.add(slot.id)
    this.tray = this.tray.map((p, i) => (i === idx ? null : p))
    return true
  }

  /**
   * Fill any empty tray positions with new pieces. Only types that still have an
   * unfilled slot (and that aren't already waiting in the tray) are offered, so
   * every piece on screen is always placeable.
   */
  refillTray() {
    const available = new Map<ShapeType, number>()
    for (const slot of this.slots) {
      if (!this.filledIds.has(slot.id)) {
        available.set(slot.type, (available.get(slot.type) ?? 0) + 1)
      }
    }
    // Don't offer a type more times than there are empty slots for it.
    for (const p of this.tray) {
      if (p) available.set(p.type, (available.get(p.type) ?? 0) - 1)
    }
    this.tray = this.tray.map((p) => {
      if (p) return p
      const choices = [...available.entries()].filter(([, n]) => n > 0).map(([t]) => t)
      if (choices.length === 0) return null
      const type = choices[Math.floor(Math.random() * choices.length)]
      available.set(type, (available.get(type) ?? 1) - 1)
      return { pieceId: this.nextPieceId++, type }
    })
  }

  reset() {
    this.slots = generateSlots()
    this.filledIds = new Set()
    this.tray = Array(TRAY_SIZE).fill(null)
    this.refillTray()
  }
}
