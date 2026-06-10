import { GAME_CATALOG, GameEntry, GameId, darken } from './GameCatalog'

const COLS = 4

interface Props {
  onSelectGame: (id: GameId) => void
  onOpenSettings: () => void
}

/** The island home screen: a grassy island in the sea with one round pad per game,
 * a title banner and a settings gear — a direct port of IslandMenuScreen.kt.
 *
 * The scenery SVG stretches to the stage (`preserveAspectRatio="none"`); on the
 * usual ~2:1 landscape stage a unit of x is about twice a unit of y, so circular
 * decorations are drawn as ellipses with rx ≈ ry/2 to come out round on screen. */
export default function IslandMenuScreen({ onSelectGame, onOpenSettings }: Props) {
  const games = GAME_CATALOG
  const rows = Math.ceil(games.length / COLS)

  return (
    <div
      className="relative h-full w-full overflow-hidden"
      style={{ background: 'linear-gradient(to bottom, #35A3D9, #1E78B5, #145DA0)' }}
    >
      {/* The island and its decorations, drawn behind the pads. */}
      <svg className="absolute inset-0 h-full w-full" preserveAspectRatio="none" viewBox="0 0 100 100">
        {/* ripple rings in the water around the island */}
        <rect x="2" y="6.5" width="96" height="91" rx="40" fill="none" stroke="rgba(255,255,255,0.14)" strokeWidth="0.7" />
        <rect x="-1" y="1.5" width="102" height="101" rx="43" fill="none" stroke="rgba(255,255,255,0.07)" strokeWidth="0.6" />
        {/* sand base with a wet-sand edge */}
        <rect x="3.8" y="9.8" width="92.4" height="84.4" rx="38" fill="#E9D8A6" stroke="#D9C189" strokeWidth="0.6" />
        {/* grass on top, with a darker outline for depth */}
        <rect x="5" y="12" width="90" height="80" rx="36" fill="#6FBF3B" stroke="#4F9E28" strokeWidth="0.7" />

        {/* winding dirt path between the pads */}
        <path
          d="M 6 50 C 28 24, 42 78, 64 48 Q 80 28, 94 56"
          fill="none"
          stroke="rgba(229,213,165,0.9)"
          strokeWidth="4.5"
          strokeLinecap="round"
        />

        {/* darker grass patches for texture */}
        {([
          [18, 16, 5, 3.5], [55, 14, 6, 3.2], [86, 24, 4.5, 3], [10, 52, 5, 3.4],
          [46, 50, 5.5, 3], [90, 60, 4, 3], [24, 86, 6, 3.4], [66, 88, 5, 3.2],
        ] as const).map(([cx, cy, rx, ry], i) => (
          <ellipse key={i} cx={cx} cy={cy} rx={rx} ry={ry} fill="rgba(79,158,40,0.45)" />
        ))}

        {/* trees, bushes, rocks and flowers around the rim (pads draw over overlaps) */}
        <Tree cx={8.5} cy={23.5} s={1} />
        <Tree cx={92.5} cy={32} s={0.9} />
        <Tree cx={9.5} cy={70} s={0.85} />
        <Tree cx={88.5} cy={80} s={1} />
        <Bush cx={30.5} cy={12.5} s={0.7} />
        <Bush cx={50} cy={11.5} s={0.6} />
        <Bush cx={70} cy={89.5} s={0.7} />
        <Rock cx={16.5} cy={89.5} s={0.55} />
        <Rock cx={83.5} cy={13.5} s={0.5} />
        {([
          [22.5, 33], [62, 27], [78, 52], [35, 84.5], [56.5, 78], [13.5, 42],
        ] as const).map(([cx, cy], i) => (
          <Flower key={i} cx={cx} cy={cy} />
        ))}
      </svg>

      {/* Game pads laid out in a grid over the island. */}
      {games.map((game, i) => {
        const col = i % COLS
        const row = Math.floor(i / COLS)
        // Island occupies 6%..94% horizontally and 14%..88% vertically (as on Android).
        const centerX = 6 + (88 / COLS) * (col + 0.5)
        const centerY = 14 + (74 / rows) * (row + 0.5)
        return (
          <GamePad
            key={i}
            game={game}
            centerX={centerX}
            centerY={centerY}
            onClick={() => game.id && onSelectGame(game.id)}
          />
        )
      })}

      <h1 className="absolute left-0 right-0 top-3 text-center text-2xl font-bold text-white drop-shadow">
        Learning Games
      </h1>

      {/* Settings (gear) button, top-right. */}
      <button
        onClick={onOpenSettings}
        aria-label="Settings"
        className="absolute right-4 top-4 flex h-[52px] w-[52px] items-center justify-center rounded-full bg-white/90 text-2xl text-[#2B2B2B] shadow-md transition-transform active:scale-95"
      >
        ⚙
      </button>
    </div>
  )
}

// Decoration glyphs: drawn in the stretched 100×100 viewBox, so "round" parts use
// rx ≈ ry/2 (see the component doc comment). `s` scales the whole glyph.

function Tree({ cx, cy, s }: { cx: number; cy: number; s: number }) {
  return (
    <g>
      <rect x={cx - 0.6 * s} y={cy - 0.4 * s} width={1.2 * s} height={4 * s} fill="#7A4A21" />
      <ellipse cx={cx - 1.4 * s} cy={cy - 1.8 * s} rx={2.6 * s} ry={2.7 * s * 0.95} fill="#3E8A2E" />
      <ellipse cx={cx + 0.8 * s} cy={cy - 2.7 * s} rx={2.9 * s} ry={3 * s * 0.95} fill="#49A337" />
      <ellipse cx={cx + 2 * s} cy={cy - 0.5 * s} rx={2.1 * s} ry={2.2 * s * 0.95} fill="#3E8A2E" />
      <ellipse cx={cx + 0.3 * s} cy={cy - 3.7 * s} rx={1 * s} ry={1 * s} fill="rgba(255,255,255,0.15)" />
    </g>
  )
}

function Bush({ cx, cy, s }: { cx: number; cy: number; s: number }) {
  return (
    <g>
      <ellipse cx={cx - 1.7 * s} cy={cy} rx={3.2 * s * 0.55} ry={3.2 * s} fill="#3E8A2E" />
      <ellipse cx={cx + 1.1 * s} cy={cy - 0.6 * s} rx={3.8 * s * 0.55} ry={3.8 * s} fill="#49A337" />
      <ellipse cx={cx + 1.2 * s} cy={cy - 2.3 * s} rx={1.2 * s * 0.55} ry={1.2 * s} fill="rgba(255,255,255,0.12)" />
    </g>
  )
}

function Rock({ cx, cy, s }: { cx: number; cy: number; s: number }) {
  return (
    <g>
      <ellipse cx={cx} cy={cy} rx={3.8 * s * 0.55} ry={2.4 * s} fill="#8D99A6" />
      <ellipse cx={cx - 0.6 * s} cy={cy - 1 * s} rx={1.9 * s * 0.55} ry={1.1 * s} fill="#B0BAC5" />
    </g>
  )
}

function Flower({ cx, cy }: { cx: number; cy: number }) {
  const petals = [0, 1, 2, 3, 4].map((i) => {
    const a = (i / 5) * 2 * Math.PI
    return [cx + 0.55 * Math.cos(a), cy + 1.1 * Math.sin(a)] as const
  })
  return (
    <g>
      {petals.map(([px, py], i) => (
        <ellipse key={i} cx={px} cy={py} rx={0.42} ry={0.84} fill="#FFB3C6" />
      ))}
      <ellipse cx={cx} cy={cy} rx={0.38} ry={0.76} fill="#FFD23F" />
    </g>
  )
}

function GamePad({
  game,
  centerX,
  centerY,
  onClick,
}: {
  game: GameEntry
  centerX: number
  centerY: number
  onClick: () => void
}) {
  // A chunky colored plinth seen slightly from above: lit top face over a darker side.
  return (
    <button
      onClick={onClick}
      disabled={game.locked}
      aria-label={game.title}
      className={`absolute flex items-center justify-center rounded-full ${game.locked ? '' : 'transition-transform active:scale-95'}`}
      style={{
        left: `${centerX}%`,
        top: `${centerY}%`,
        transform: 'translate(-50%, -50%)',
        width: 'min(13vw, 17vh)',
        height: 'min(13vw, 17vh)',
        background: `radial-gradient(circle at 32% 28%, rgba(255,255,255,0.28), rgba(255,255,255,0) 60%), ${game.padColor}`,
        border: `min(0.35vw, 0.45vh) solid ${darken(game.padColor, 0.75)}`,
        boxShadow: `0 min(0.6vw, 0.8vh) 0 ${darken(game.padColor, 0.62)}, 0 min(1vw, 1.4vh) min(1.2vw, 1.6vh) rgba(0,0,0,0.25)`,
      }}
    >
      <div className="h-[64%] w-[64%]">{game.icon}</div>
      {game.locked && (
        // Little lock plaque on the front of the pad.
        <div
          className="absolute left-1/2 flex items-center justify-center rounded-[20%] bg-[#3E4A3D] shadow"
          style={{
            bottom: '-4%',
            transform: 'translateX(-50%)',
            width: '38%',
            height: '24%',
            fontSize: 'min(2.2vw, 3vh)',
          }}
        >
          🔒
        </div>
      )}
    </button>
  )
}
