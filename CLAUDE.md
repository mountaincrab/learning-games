# Learning Games — Claude guidance

A landscape Android game for young children (learn-through-play). One Gradle module
(`app/`) built with **Compose Multiplatform**. No database, no persistence — game
state is in-memory. A web version lives in `webapp/` (see below); the only Firebase
product used is Hosting for that web app.

## Commit messages (required: Conventional Commits)

Every commit / PR title **must** use [Conventional Commits](https://www.conventionalcommits.org/).
The `Release` workflow (`.github/workflows/release.yml`) feeds the commit history through
`mathieudutour/github-tag-action`, which **bumps the semver tag** and **generates the
GitHub Release notes from the commit subjects**. An unprefixed commit is silently dropped
from the changelog.

| Prefix | Bump |
|--------|------|
| `feat:` | minor |
| `fix:` | patch |
| `feat!:` / `BREAKING CHANGE:` footer | major |
| `ci:` `chore:` `docs:` `refactor:` `perf:` `test:` `style:` | patch |

**Squash-merge caveat:** the **PR title** becomes the `main` commit subject, so the PR
title is what needs the prefix.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:compileDebugKotlinAndroid   # faster compile-only check
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Versioning (git-derived)

`app/build.gradle.kts` computes `versionName`/`versionCode` from git tags at build time —
no version-bump commits. `versionCode` = commit count; `versionName` = latest `vX.Y.Z`
tag (clean on `main`/tagged builds, `X.Y.Z-<branch>.<sha>` on branches). CI passes
`VERSION_BRANCH`/`VERSION_SHA` so PR builds version correctly. The version is shown on the
Settings screen via `AppInfo`.

## Project structure

```
app/src/
  commonMain/kotlin/com/mountaincrab/learninggames/
    App.kt                 — root composable; tiny in-app nav state machine (Menu/Settings/Game)
    AppInfo.kt             — expect: app version metadata
    PlatformBackHandler.kt — expect: system back hook
    ui/theme/Theme.kt      — MaterialTheme + GamePalette
    ui/components/         — shared widgets (BackButton)
    ui/menu/               — IslandMenuScreen + GameCatalog (registry of games/pads)
    ui/settings/           — SettingsScreen (shows version)
    game/<name>/           — one folder per game (e.g. shapegame/)
  androidMain/
    AndroidManifest.xml    — single landscape, immersive MainActivity
    kotlin/.../MainActivity.kt        — setContent { App() }
    kotlin/.../AppInfo.android.kt     — actual: reads BuildConfig.VERSION_*
    kotlin/.../PlatformBackHandler.android.kt — actual: androidx BackHandler
    res/                   — strings, theme, launcher icon
```

### Multiplatform patterns (non-obvious)

- UI/game code lives in **`commonMain`** using JetBrains Compose MP (`compose.runtime`,
  `compose.foundation`, `compose.material3`, `compose.ui`, `compose.animation`) — NOT the
  AndroidX `androidx.compose.*` artifacts. Keep new screens in `commonMain`.
- Anything platform-specific goes through **`expect`/`actual`**: `AppInfo` (version) and
  `PlatformBackHandler` (system back). Add an `actual` in `androidMain` for any new
  `expect`.
- No `material-icons` dependency — draw glyphs with `Canvas`/`Path` or use text/emoji
  (see `BackButton`, the settings gear, padlocks).

## Web app (`webapp/`)

A web replica of the Android app: React + TypeScript + Tailwind + Vite (same stack as
the Crab Do webapp), deployed to Firebase Hosting (`firebase.json` at the repo root
serves `webapp/dist` as an SPA). Setup/deploy steps: `docs/WEBAPP_DEPLOYMENT.md`.

```bash
cd webapp && npm run dev          # dev server
cd webapp && npx tsc --noEmit     # type check
cd webapp && npm run build        # tsc + vite build → webapp/dist
```

Structure mirrors the Android `commonMain` packages (`src/ui/menu/`, `src/ui/settings/`,
`src/game/<name>/`). Non-obvious points:

- **Games render on `<canvas>`** via a requestAnimationFrame loop (`useGameCanvas`),
  porting the Compose `DrawScope` code near 1:1. Game logic/state classes
  (`ShapeGameState`, `TrumperGameState`) are direct ports of the Kotlin classes —
  keep them in sync when game rules change.
- `anim.ts` has a `Spring` standing in for Compose's `Animatable`; layout functions
  (`computeHatLayout`, `computeTrumperLayout`) are copied from the Kotlin screens, so
  geometry tweaks should be applied to both.
- **Audio is synthesised with Web Audio** (`src/audio/synth.ts` + per-game audio
  modules) — no audio assets are shipped, unlike Android's `res/raw` OGGs.
- Version string comes from `git describe` baked in at build time (`vite.config.ts`,
  `__APP_VERSION__`), mirroring the Android git-derived versioning.
- When adding a game: create `src/game/<name>/`, register it in
  `src/ui/menu/GameCatalog.tsx` (SVG icon instead of a Canvas lambda), and wire it in
  `src/App.tsx` — same three steps as Android.

## Adding a new game

1. Add a folder `game/<name>/` in `commonMain` with the game's screen + in-memory state
   holder (model the `shapegame/` package: `ShapeGameState` is a plain class held via
   `remember { }`, no ViewModel/DI).
2. Add a `GameId` entry and register a `GameEntry` in `ui/menu/GameCatalog.kt`
   (set `locked = false`, give it a `padColor` and a `Canvas` icon lambda).
3. Wire it in `App.kt`'s `when (s.id)` dispatch.

### Magic Hat game (`game/shapegame/`)

- `ShapeType` — the shapes; `ShapeRenderer` — `Path` builders + `DrawScope` outline/fill
  helpers; `ShapeGameState` — slots, current draggable piece, match/spawn/win logic;
  `ShapeGameScreen` — draws the hat + outlines, the draggable piece, and the wand sweep.
- **Coordinates:** all geometry is computed in stage pixels inside `BoxWithConstraints`
  (`computeHatLayout`). The draggable piece tracks a top-left `Offset` (an `Animatable`);
  drop hit-testing compares the piece centre against slot centres in the same stage space —
  no `boundsInRoot`/global-position math needed. Keep new draggables in this shared space.
