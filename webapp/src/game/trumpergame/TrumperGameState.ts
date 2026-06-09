export const TRUMPER_COUNT = 4

/**
 * In-memory state for the "Stinky Trumpers" game — a direct port of the Android
 * `TrumperGameState`. `TRUMPER_COUNT` little characters stand in a row; at random
 * intervals one of them becomes bloated (tummy swells). Tapping a bloated tummy
 * releases the gas and clears it. There is no win condition — it is an endless
 * tap-to-play game for young children. No persistence.
 */
export class TrumperGameState {
  /** Ids of the characters whose tummies are currently swollen. */
  bloated = new Set<number>()

  isBloated(id: number): boolean {
    return this.bloated.has(id)
  }

  /** Make one random not-yet-bloated character start swelling. No-op if all are. */
  bloatRandom() {
    const candidates: number[] = []
    for (let i = 0; i < TRUMPER_COUNT; i++) {
      if (!this.bloated.has(i)) candidates.push(i)
    }
    if (candidates.length === 0) return
    this.bloated.add(candidates[Math.floor(Math.random() * candidates.length)])
  }

  /** Release the gas for [id]. Returns true only if it was actually bloated. */
  release(id: number): boolean {
    if (!this.bloated.has(id)) return false
    this.bloated.delete(id)
    return true
  }
}
