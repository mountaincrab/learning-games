# Game audio files

Games load their sounds from `app/src/androidMain/res/raw/` at runtime. **The files
are not committed yet** — drop them into that folder and rebuild, and the audio turns
on automatically. No code changes are needed.

All audio for every game goes in the same folder:

```
app/src/androidMain/res/raw/
```

## Magic Hat

| File | Purpose | Played by |
|------|---------|-----------|
| `magic_chime.ogg` | Short "magic" sound effect, played on each correct shape match | `SoundPool` |
| `background_music.ogg` | Gentle, looping background music while the game is on screen | `MediaPlayer` |

## Stinky Trumpers

Drop in as many fart clips as you like, named `fart_1.ogg`, `fart_2.ogg`, … (numbered
consecutively from 1, up to 12). The game loads every one it finds and plays a
**random** clip each time a tummy is tapped, so more clips = more variety.

| File | Purpose | Played by |
|------|---------|-----------|
| `fart_1.ogg`, `fart_2.ogg`, … | Short fart sound effects; one is picked at random on each release | `SoundPool` |

Just `fart_1.ogg` on its own works fine (it'll play every time); add more to randomise.

## Filename rules (Android is strict here)

Resource filenames in `res/raw` may contain **only lowercase letters, digits and
underscores** — no spaces, hyphens or capitals. Use exactly the names listed for each
game above (`magic_chime`, `background_music`, `fart_1`, `fart_2`, …).

## Format

- Use **OGG (Vorbis)** — the Android standard for game audio. It compresses well and
  loops cleanly.
- WAV also works if you prefer (e.g. `magic_chime.wav`); the code resolves files by
  name regardless of extension. Avoid MP3 for the loop — it can introduce a gap at
  the loop seam.

## Tips for good results

- **Background music must loop seamlessly.** Choose a clip already trimmed to a clean
  loop — no silence at the very start or end — or you'll hear a gap on every repeat.
- **Keep sound effects short** (~1–2 seconds) and small; `SoundPool` is designed for
  brief clips (the chime, the farts).
- Keep all files reasonably small, since they're bundled into the APK.

## Licensing

If you use third-party audio, prefer **CC0 / public-domain** so the files can be
committed and redistributed without attribution. If a clip requires attribution
(e.g. CC-BY), record the source and licence somewhere in the repo.

## Behaviour without the files

The app still builds and runs without these files — it simply stays **silent**. The
resource ids are looked up by name (`getIdentifier`) in `GameAudio.android.kt`
(Magic Hat) and `TrumperAudio.android.kt` (Stinky Trumpers), so as soon as the files
are present the sound works with no further changes.

## Tuning volume

Playback volumes are set in the platform audio files:

- Magic Hat chime (`GameAudio.android.kt`): `soundPool.play(chimeId, 0.7f, 0.7f, ...)`
- Magic Hat music (`GameAudio.android.kt`): `setVolume(0.35f, 0.35f)`
- Stinky Trumpers farts (`TrumperAudio.android.kt`): `soundPool.play(id, 0.9f, 0.9f, ...)`

Adjust those values (`0.0`–`1.0`) if any is too loud or too quiet.
