import { useEffect, useState } from 'react'
import { GameId } from './ui/menu/GameCatalog'
import IslandMenuScreen from './ui/menu/IslandMenuScreen'
import SettingsScreen from './ui/settings/SettingsScreen'
import ShapeGameScreen from './game/shapegame/ShapeGameScreen'
import TrumperGameScreen from './game/trumpergame/TrumperGameScreen'

/** Top-level destinations. Kept as a tiny in-app state machine to mirror the
 * Android app's navigation (Menu / Settings / Game). */
type Screen = { kind: 'menu' } | { kind: 'settings' } | { kind: 'game'; id: GameId }

export default function App() {
  const [screen, setScreen] = useState<Screen>({ kind: 'menu' })

  // Browser back returns to the menu from any sub-screen (the web analogue of
  // PlatformBackHandler). A history entry is pushed when leaving the menu.
  useEffect(() => {
    const onPop = () => setScreen({ kind: 'menu' })
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  const navigate = (next: Screen) => {
    if (next.kind !== 'menu') window.history.pushState({}, '')
    setScreen(next)
  }
  const backToMenu = () => {
    if (window.history.state !== null) window.history.back()
    else setScreen({ kind: 'menu' })
  }

  const content = (() => {
    switch (screen.kind) {
      case 'menu':
        return (
          <IslandMenuScreen
            onSelectGame={(id) => navigate({ kind: 'game', id })}
            onOpenSettings={() => navigate({ kind: 'settings' })}
          />
        )
      case 'settings':
        return <SettingsScreen onBack={backToMenu} />
      case 'game':
        switch (screen.id) {
          case GameId.MAGIC_HAT:
            return <ShapeGameScreen onBack={backToMenu} />
          case GameId.STINKY_TRUMPERS:
            return <TrumperGameScreen onBack={backToMenu} />
        }
    }
  })()

  return (
    <div className="relative h-full w-full">
      {content}
      {/* The Android app is landscape-locked; on the web we just nudge. */}
      <div className="portrait-hint pointer-events-none absolute bottom-4 left-1/2 -translate-x-1/2 items-center gap-2 rounded-full bg-black/60 px-4 py-2 text-sm text-white">
        <span aria-hidden>📱↻</span> Best played sideways!
      </div>
    </div>
  )
}
