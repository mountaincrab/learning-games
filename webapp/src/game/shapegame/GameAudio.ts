import { audioContext, playNote } from '../../audio/synth'

/**
 * Sound layer for the Magic Hat game, mirroring the Android `GameAudio` interface:
 * a sparkly chime on each match and gentle looping background music. All sounds
 * are synthesised with Web Audio (no asset files).
 */
export interface GameAudio {
  playMagicChime(): void
  startMusic(): void
  stopMusic(): void
}

/** Soft pentatonic loop, scheduled a little ahead of time. */
const MUSIC_PATTERN = [523.25, 659.25, 783.99, 659.25, 587.33, 783.99, 880.0, 659.25] // C5 E5 G5 E5 D5 G5 A5 E5
const NOTE_INTERVAL = 0.55 // seconds

export function createGameAudio(): GameAudio {
  let timer: number | null = null
  let nextNoteAt = 0
  let noteIndex = 0

  return {
    playMagicChime() {
      const ac = audioContext()
      const t = ac.currentTime
      // A quick rising arpeggio with a high sparkle on top.
      playNote(ac, { freq: 880, at: t, duration: 0.35, type: 'triangle', gain: 0.18 })
      playNote(ac, { freq: 1174.66, at: t + 0.08, duration: 0.35, type: 'triangle', gain: 0.16 })
      playNote(ac, { freq: 1567.98, at: t + 0.16, duration: 0.5, type: 'triangle', gain: 0.14 })
      playNote(ac, { freq: 2637.02, at: t + 0.2, duration: 0.6, type: 'sine', gain: 0.08 })
    },

    startMusic() {
      if (timer !== null) return
      const ac = audioContext()
      nextNoteAt = ac.currentTime + 0.1
      noteIndex = 0
      timer = window.setInterval(() => {
        // Lookahead scheduler: keep ~0.4s of notes queued.
        while (nextNoteAt < ac.currentTime + 0.4) {
          playNote(ac, {
            freq: MUSIC_PATTERN[noteIndex % MUSIC_PATTERN.length],
            at: nextNoteAt,
            duration: 0.9,
            type: 'sine',
            gain: 0.05,
          })
          nextNoteAt += NOTE_INTERVAL
          noteIndex++
        }
      }, 150)
    },

    stopMusic() {
      if (timer !== null) {
        clearInterval(timer)
        timer = null
      }
    },
  }
}
