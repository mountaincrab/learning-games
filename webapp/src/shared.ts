/**
 * Single import point for the `learning-games-shared` Kotlin Multiplatform library
 * (game state, layout math and shape geometry compiled to JS — the same code the
 * Android app runs). Build it with `npm run build:shared` before `npm install`.
 *
 * This shim re-exports everything the app uses and unwraps the Kotlin top-level
 * constants, which the compiler exposes as `{ get() }` accessor objects.
 */
import {
  BLOAT_SCALE as bloatScale,
  TRAY_SIZE as traySize,
  TRUMPER_COUNT as trumperCount,
} from 'learning-games-shared'

export {
  ShapeGameState,
  TrumperGameState,
  computeHatLayout,
  computeTrumperLayout,
  shapeFillColorCss,
  shapePathCommands,
  shapeTypeNames,
  trumperBodyCenter,
} from 'learning-games-shared'

export type {
  HatLayout,
  PathCommand,
  Slot,
  TrayPiece,
  TrumperLayout,
  Vec2,
} from 'learning-games-shared'

export const TRAY_SIZE = traySize.get()
export const TRUMPER_COUNT = trumperCount.get()
export const BLOAT_SCALE = bloatScale.get()
