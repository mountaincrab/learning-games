import { GAME_CATALOG, GameEntry, GameId } from './GameCatalog'

const COLS = 4

interface Props {
  onSelectGame: (id: GameId) => void
  onOpenSettings: () => void
}

/** The island home screen: a grassy island in the sea with one round pad per game,
 * a title banner and a settings gear — a direct port of IslandMenuScreen.kt. */
export default function IslandMenuScreen({ onSelectGame, onOpenSettings }: Props) {
  const games = GAME_CATALOG
  const rows = Math.ceil(games.length / COLS)

  return (
    <div
      className="relative h-full w-full overflow-hidden"
      style={{ background: 'linear-gradient(to bottom, #2E8BC0, #145DA0)' }}
    >
      {/* The island itself, drawn behind the pads. */}
      <svg className="absolute inset-0 h-full w-full" preserveAspectRatio="none" viewBox="0 0 100 100">
        {/* sand base */}
        <rect x="4" y="10" width="92" height="84" rx="38" fill="#E9D8A6" />
        {/* grass on top, with a darker outline for depth */}
        <rect x="5" y="12" width="90" height="80" rx="36" fill="#6FBF3B" stroke="#4F9E28" strokeWidth="0.6" />
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
  return (
    <button
      onClick={onClick}
      disabled={game.locked}
      aria-label={game.title}
      className={`absolute flex items-center justify-center rounded-full shadow-lg transition-transform ${
        game.locked ? 'bg-[#8A8A8A]' : 'bg-[#EFE6D2] active:scale-95'
      }`}
      style={{
        left: `${centerX}%`,
        top: `${centerY}%`,
        transform: 'translate(-50%, -50%)',
        width: 'min(13vw, 17vh)',
        height: 'min(13vw, 17vh)',
      }}
    >
      <div className="h-[72%] w-[72%]">{game.icon}</div>
      {game.locked && (
        // Dim + padlock overlay for not-yet-available games.
        <div className="absolute inset-0 flex items-center justify-center rounded-full bg-black/35 text-[min(4.5vw,6vh)]">
          🔒
        </div>
      )}
    </button>
  )
}
