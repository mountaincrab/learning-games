import { audioContext } from '../../audio/synth'
import pennyUrl from './audio/penny.ogg'
import tazUrl from './audio/taz.ogg'
import zachUrl from './audio/zach.ogg'
import roryUrl from './audio/rory.ogg'

// The opening sequence shouts each character's name. Unlike the synthesised fart,
// speech can't be generated at runtime, so these are small bundled OGG clips (the
// same files the Android app plays from res/raw), keyed by column order.
const NAME_URLS = [pennyUrl, tazUrl, zachUrl, roryUrl]

/**
 * Sound layer for the Stinky Trumpers game. The Android app plays bundled OGG
 * files; on the web the raspberry is synthesised with Web Audio — a low sawtooth
 * with a pitch wobble through a lowpass filter — and randomised a little on each
 * release so no two sound quite the same. The opening name shouts are bundled clips.
 */
export interface TrumperAudio {
  playFart(): void
  /** Shout character `id`'s name (0-based, column order). */
  playName(id: number): void
  /** Play the round-win celebration cheer. */
  playCheer(): void
}

export function createTrumperAudio(): TrumperAudio {
  // Lazily decoded AudioBuffers for the name clips, fetched on first use.
  const nameBuffers: (AudioBuffer | undefined)[] = []

  const loadName = (id: number) => {
    if (nameBuffers[id] !== undefined || !NAME_URLS[id]) return
    const ac = audioContext()
    fetch(NAME_URLS[id])
      .then((r) => r.arrayBuffer())
      .then((b) => ac.decodeAudioData(b))
      .then((buf) => {
        nameBuffers[id] = buf
      })
      .catch(() => {})
  }

  // Warm the cache up front so the first pop isn't silent.
  NAME_URLS.forEach((_, i) => loadName(i))

  return {
    playName(id: number) {
      const buf = nameBuffers[id]
      if (!buf) {
        loadName(id)
        return
      }
      const ac = audioContext()
      const src = ac.createBufferSource()
      src.buffer = buf
      src.connect(ac.destination)
      src.start()
    },
    playFart() {
      const ac = audioContext()
      const t = ac.currentTime
      const duration = 0.45 + Math.random() * 0.4
      const baseFreq = 75 + Math.random() * 45

      const osc = ac.createOscillator()
      osc.type = 'sawtooth'
      osc.frequency.setValueAtTime(baseFreq, t)
      osc.frequency.exponentialRampToValueAtTime(baseFreq * 0.55, t + duration)

      // Wobble the pitch for the characteristic flapping sound.
      const lfo = ac.createOscillator()
      lfo.type = 'sine'
      lfo.frequency.setValueAtTime(22 + Math.random() * 14, t)
      const lfoGain = ac.createGain()
      lfoGain.gain.value = baseFreq * 0.45
      lfo.connect(lfoGain).connect(osc.frequency)

      const filter = ac.createBiquadFilter()
      filter.type = 'lowpass'
      filter.frequency.setValueAtTime(900, t)
      filter.frequency.exponentialRampToValueAtTime(280, t + duration)
      filter.Q.value = 4

      const gain = ac.createGain()
      gain.gain.setValueAtTime(0, t)
      gain.gain.linearRampToValueAtTime(0.35, t + 0.02)
      gain.gain.setValueAtTime(0.35, t + duration * 0.6)
      gain.gain.exponentialRampToValueAtTime(0.0001, t + duration)

      osc.connect(filter).connect(gain).connect(ac.destination)
      osc.start(t)
      lfo.start(t)
      osc.stop(t + duration + 0.05)
      lfo.stop(t + duration + 0.05)
    },
    playCheer() {
      // A bright little ascending arpeggio (C–E–G–C major triad) — the synthesised
      // stand-in for Android's bundled cheer clip.
      const ac = audioContext()
      const t0 = ac.currentTime
      const notes = [523.25, 659.25, 783.99, 1046.5]
      notes.forEach((freq, i) => {
        const t = t0 + i * 0.12
        const osc = ac.createOscillator()
        osc.type = 'triangle'
        osc.frequency.setValueAtTime(freq, t)
        const gain = ac.createGain()
        gain.gain.setValueAtTime(0, t)
        gain.gain.linearRampToValueAtTime(0.3, t + 0.02)
        gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.35)
        osc.connect(gain).connect(ac.destination)
        osc.start(t)
        osc.stop(t + 0.4)
      })
    },
  }
}
