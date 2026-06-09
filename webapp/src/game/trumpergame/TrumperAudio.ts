import { audioContext } from '../../audio/synth'

/**
 * Sound layer for the Stinky Trumpers game. The Android app plays bundled OGG
 * files; on the web the raspberry is synthesised with Web Audio — a low sawtooth
 * with a pitch wobble through a lowpass filter — and randomised a little on each
 * release so no two sound quite the same.
 */
export interface TrumperAudio {
  playFart(): void
}

export function createTrumperAudio(): TrumperAudio {
  return {
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
  }
}
