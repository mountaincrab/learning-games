# Magic Hat audio files

The Magic Hat game plays two sounds, loaded from `app/src/androidMain/res/raw/` at
runtime. **The files are not committed yet** — drop them into that folder and rebuild,
and the audio turns on automatically. No code changes are needed.

## Files to add

| File | Purpose | Played by |
|------|---------|-----------|
| `magic_chime.ogg` | Short "magic" sound effect, played on each correct shape match | `SoundPool` |
| `background_music.ogg` | Gentle, looping background music while the game is on screen | `MediaPlayer` |

Put both files directly in:

```
app/src/androidMain/res/raw/
```

## Filename rules (Android is strict here)

Resource filenames in `res/raw` may contain **only lowercase letters, digits and
underscores** — no spaces, hyphens or capitals. The names must be exactly:

- `magic_chime.ogg`
- `background_music.ogg`

## Format

- Use **OGG (Vorbis)** — the Android standard for game audio. It compresses well and
  loops cleanly.
- WAV also works if you prefer (e.g. `magic_chime.wav`); the code resolves files by
  name regardless of extension. Avoid MP3 for the loop — it can introduce a gap at
  the loop seam.

## Tips for good results

- **Background music must loop seamlessly.** Choose a clip already trimmed to a clean
  loop — no silence at the very start or end — or you'll hear a gap on every repeat.
- **Keep the chime short** (~1–2 seconds) and small; `SoundPool` is designed for brief
  clips.
- Keep both files reasonably small, since they're bundled into the APK.

## Licensing

If you use third-party audio, prefer **CC0 / public-domain** so the files can be
committed and redistributed without attribution. If a clip requires attribution
(e.g. CC-BY), record the source and licence somewhere in the repo.

## Behaviour without the files

The app still builds and runs without these files — it simply stays **silent**. The
resource ids are looked up by name (`getIdentifier`) in
`GameAudio.android.kt`, so as soon as the files are present the sound works with no
further changes.

## Tuning volume

Playback volumes are set in `GameAudio.android.kt`:

- chime: `soundPool.play(chimeId, 0.7f, 0.7f, ...)`
- music: `setVolume(0.35f, 0.35f)`

Adjust those values (`0.0`–`1.0`) if either is too loud or too quiet.
