# Learning Games — Claude guidance

A landscape Android game for young children (learn-through-play). Two Gradle modules:
`app/` (the Android app, **Compose Multiplatform**) and `shared/` (Compose-free game
logic + geometry, compiled for both the JVM and **JS** — see below). No database, no
persistence — game state is in-memory. A web version lives in `webapp/` and consumes
the `shared` module's JS build; the only Firebase product used is Hosting for that
web app.

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

# Shared Kotlin→JS library for the webapp (run after changing shared/ code):
./gradlew :shared:jsBrowserProductionLibraryDistribution
```

## Versioning (git-derived)

`app/build.gradle.kts` computes `versionName`/`versionCode` from git tags at build time —
no version-bump commits. `versionCode` = commit count; `versionName` = latest `vX.Y.Z`
tag (clean on `main`/tagged builds, `X.Y.Z-<branch>.<sha>` on branches). CI passes
`VERSION_BRANCH`/`VERSION_SHA` so PR builds version correctly. The version is shown on the
Settings screen via `AppInfo`.

## Project structure

```
shared/src/commonMain/kotlin/com/mountaincrab/learninggames/
    geometry/              — Vec2 + PathCommand (renderer-neutral path steps)
    game/ObservableGameState.kt — base: onChange callback for the platforms
    game/shapegame/        — ShapeType, ShapeGameState, ShapeGeometry, HatLayout
    game/trumpergame/      — TrumperGameState, TrumperLayout
app/src/
  commonMain/kotlin/com/mountaincrab/learninggames/
    App.kt                 — root composable; tiny in-app nav state machine (Menu/Settings/Game)
    AppInfo.kt             — expect: app version metadata
    PlatformBackHandler.kt — expect: system back hook
    geometry/              — Vec2 → Compose Offset conversion
    ui/theme/Theme.kt      — MaterialTheme + GamePalette
    ui/components/         — shared widgets (BackButton)
    ui/menu/               — IslandMenuScreen + GameCatalog (registry of games/pads)
    ui/settings/           — SettingsScreen (shows version)
    game/GameStateCompose.kt — observeGameState(): recompose on shared-state change
    game/<name>/           — one folder per game: the screen + drawing (e.g. shapegame/)
  androidMain/
    AndroidManifest.xml    — single landscape, immersive MainActivity
    kotlin/.../MainActivity.kt        — setContent { App() }
    kotlin/.../AppInfo.android.kt     — actual: reads BuildConfig.VERSION_*
    kotlin/.../PlatformBackHandler.android.kt — actual: androidx BackHandler
    res/                   — strings, theme, launcher icon
```

### The `shared/` module (game logic shared with the webapp)

- Game **rules, layout math and shape geometry** live in `shared/` and are used by both
  the Android app (as a normal project dependency) and the webapp (compiled to JS with
  TypeScript definitions, consumed as the npm package `learning-games-shared` via a
  `file:` dependency on `shared/build/dist/js/productionLibrary`).
- `shared/` keeps the **same Kotlin packages** as the app (`…game.shapegame` etc.), so
  app code uses the classes without import churn.
- `shared/` is **Compose-free**: state classes extend `ObservableGameState` and call
  `notifyChanged()` after mutations; the app's `observeGameState(state)` bridges that
  into recomposition (the webapp redraws every rAF frame and ignores it).
- **JS export rules:** public API is `@JsExport`ed. Enums can't be exported — expose
  name strings (`Slot.typeName`, `shapeTypeNames()`) and mark enum-typed members
  `@JsExport.Ignore`. Use `Array` (not `List`) and `Float`/`Int` (not `Long`) in
  exported signatures. Top-level `const val`s export as `{ get() }` accessor objects —
  the webapp unwraps them once in `src/shared.ts`.
- Shapes are geometry **data**, not drawing code: `ShapeGeometry.pathCommandsFor` returns
  `PathCommand`s that `app`'s `ShapeRenderer` turns into a Compose `Path` and the
  webapp's `ShapeRenderer.ts` turns into a `Path2D`.

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
./gradlew :shared:jsBrowserProductionLibraryDistribution   # first, and after shared/ changes
cd webapp && npm run dev          # dev server
cd webapp && npx tsc --noEmit     # type check
cd webapp && npm run build        # tsc + vite build → webapp/dist
```

Structure mirrors the Android `commonMain` packages (`src/ui/menu/`, `src/ui/settings/`,
`src/game/<name>/`). Non-obvious points:

- **Game logic/state, layout math and shape geometry come from the `shared` Kotlin
  module** (imported via `src/shared.ts`) — the exact code the Android app runs. Do
  NOT re-implement game rules in TS; change the Kotlin and rebuild the JS library.
- **Games render on `<canvas>`** via a requestAnimationFrame loop (`useGameCanvas`),
  porting only the Compose `DrawScope` *drawing* code near 1:1 (`drawHat`,
  `drawTrumper`, …) — drawing remains per-platform.
- `anim.ts` has a `Spring` standing in for Compose's `Animatable`.
- **Audio is synthesised with Web Audio** (`src/audio/synth.ts` + per-game audio
  modules) — no audio assets are shipped, unlike Android's `res/raw` OGGs.
- Version string comes from `git describe` baked in at build time (`vite.config.ts`,
  `__APP_VERSION__`), mirroring the Android git-derived versioning.
- When adding a game: create `src/game/<name>/`, register it in
  `src/ui/menu/GameCatalog.tsx` (SVG icon instead of a Canvas lambda), and wire it in
  `src/App.tsx` — same three steps as Android.

## Adding a new game

1. Add the game's in-memory state holder (+ layout math) in `shared/`'s
   `game/<name>/` package — extend `ObservableGameState`, call `notifyChanged()` after
   mutations, follow the JS-export rules above. Add the screen in `app`'s `commonMain`
   `game/<name>/` (plain class held via `remember { }` + `observeGameState(state)`,
   no ViewModel/DI — model the `shapegame/` package).
2. Add a `GameId` entry and register a `GameEntry` in `ui/menu/GameCatalog.kt`
   (set `locked = false`, give it a `padColor` and a `Canvas` icon lambda).
3. Wire it in `App.kt`'s `when (s.id)` dispatch.

### Magic Hat game (`game/shapegame/`)

- In `shared/`: `ShapeType` — the shapes; `ShapeGeometry` — `PathCommand` builders;
  `ShapeGameState` — slots, current draggable piece, match/spawn/win logic;
  `HatLayout` — stage geometry. In `app/`: `ShapeRenderer` — `PathCommand` → Compose
  `Path` + `DrawScope` outline/fill helpers; `ShapeGameScreen` — draws the hat +
  outlines, the draggable piece, and the wand sweep.
- **Coordinates:** all geometry is computed in stage pixels inside `BoxWithConstraints`
  (`computeHatLayout`). The draggable piece tracks a top-left `Offset` (an `Animatable`);
  drop hit-testing compares the piece centre against slot centres in the same stage space —
  no `boundsInRoot`/global-position math needed. Keep new draggables in this shared space.
