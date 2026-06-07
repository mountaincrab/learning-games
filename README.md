# Learning Games

A landscape Android game for young children: a learn-through-play collection of
mini-games, presented as an island of game "pads" you tap to play.

The first game — **Magic Hat** — is a shape-matching game: drag each coloured shape
on the right onto its matching outline on the magician's hat. A correct drop fills the
outline, a wand waves over it, and the next shape pops out. Match them all to win.

## Stack

- **Compose Multiplatform** (Kotlin Multiplatform) — UI + game code in `commonMain`,
  so the game can later target iOS/desktop. Android is the only active target today.
- **Jetpack Compose** drawing/gestures/animation — no game engine, no Firebase, no
  database. Game state is in-memory only.

## Build

```bash
./gradlew :app:assembleDebug          # build the debug APK
./gradlew :app:compileDebugKotlinAndroid   # faster compile-only check
./gradlew -q :app:printVersionName    # the git-derived version string
```

Install on a device/emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Versioning & releases

Version numbers are derived from git (see `app/build.gradle.kts`) — no manual bumps.
Merges to `main` run the **Release** workflow, which uses
[Conventional Commits](https://www.conventionalcommits.org/) to bump the semver tag,
generate release notes, and attach a versioned APK. **All commits / PR titles must use
a Conventional Commits prefix** (`feat:`, `fix:`, `ci:`, …). The current version is shown
on the in-app **Settings** screen.
