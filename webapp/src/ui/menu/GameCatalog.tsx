import { ReactNode } from 'react'

/** Games that can be launched from the island menu. */
export enum GameId {
  MAGIC_HAT = 'MAGIC_HAT',
  STINKY_TRUMPERS = 'STINKY_TRUMPERS',
}

/**
 * One pad on the island. `id` is non-null only for playable games; locked entries
 * (`locked` = true) are placeholders for games not yet built.
 */
export interface GameEntry {
  id: GameId | null
  title: string
  locked: boolean
  padColor: string
  icon: ReactNode
}

/** Darken a #rrggbb color by multiplying its channels by `f` (0..1). */
export function darken(hex: string, f: number): string {
  const n = parseInt(hex.slice(1), 16)
  const ch = (shift: number) => Math.round(((n >> shift) & 0xff) * f)
  return `rgb(${ch(16)}, ${ch(8)}, ${ch(0)})`
}

/** Mini hat glyph, ported from the Android Canvas lambda (100×100 viewbox). */
function MiniHat() {
  return (
    <svg viewBox="0 0 100 100" className="h-full w-full">
      <rect x="30" y="28" width="40" height="42" fill="#8F4FBC" stroke="#3F1A57" strokeWidth="3.5" />
      <ellipse cx="50" cy="70" rx="34" ry="8" fill="#5E2C7E" stroke="#3F1A57" strokeWidth="3.5" />
      <rect x="30" y="58" width="40" height="8" fill="#FFD23F" />
    </svg>
  )
}

function MiniTrumper() {
  return (
    <svg viewBox="0 0 100 100" className="h-full w-full">
      <circle cx="50" cy="60" r="26" fill="#FF8FAB" />
      <circle cx="50" cy="26" r="14.3" fill="#FF8FAB" />
      <circle cx="44" cy="26" r="2.3" fill="#2B2B2B" />
      <circle cx="56" cy="26" r="2.3" fill="#2B2B2B" />
      <circle cx="78.6" cy="47" r="9.1" fill="#9CCC3C" opacity="0.7" />
    </svg>
  )
}

function MiniDiamond({ color }: { color: string }) {
  // White gem with a darker facet of the pad's own color, so it reads on the pad.
  return (
    <svg viewBox="0 0 100 100" className="h-full w-full">
      <polygon
        points="50,22 78,50 50,78 22,50"
        fill="rgba(255,255,255,0.92)"
        stroke={darken(color, 0.55)}
        strokeWidth="3.5"
      />
    </svg>
  )
}

const PLACEHOLDER_COLORS = [
  '#4ECDC4', '#FF6B6B', '#FFB703', '#6FBF3B',
  '#577590', '#F4A261', '#9B5DE5', '#00BBF9',
  '#EF476F', '#118AB2',
]

/** The full catalog shown on the island: playable games + locked placeholders. */
export const GAME_CATALOG: GameEntry[] = [
  {
    id: GameId.MAGIC_HAT,
    title: 'Magic Hat',
    locked: false,
    padColor: '#7B3FA0',
    icon: <MiniHat />,
  },
  {
    id: GameId.STINKY_TRUMPERS,
    title: 'Stinky Trumpers',
    locked: false,
    padColor: '#8AC926',
    icon: <MiniTrumper />,
  },
  // Locked placeholders — replace with real games over time.
  ...PLACEHOLDER_COLORS.map(
    (c): GameEntry => ({
      id: null,
      title: 'Coming soon',
      locked: true,
      padColor: c,
      icon: <MiniDiamond color={c} />,
    }),
  ),
]
