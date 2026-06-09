/**
 * Shared Web Audio plumbing. The Android app plays bundled OGG files; on the web
 * everything is synthesised instead, so the bundle ships no audio assets.
 *
 * Browsers only allow audio after a user gesture, so the context is created
 * lazily and resumed on demand (every game entry point is behind a tap).
 */
let ctx: AudioContext | null = null

export function audioContext(): AudioContext {
  if (!ctx) ctx = new AudioContext()
  if (ctx.state === 'suspended') void ctx.resume()
  return ctx
}

/** A single decaying note. */
export function playNote(
  ac: AudioContext,
  opts: {
    freq: number
    at?: number
    duration?: number
    type?: OscillatorType
    gain?: number
  },
) {
  const { freq, at = ac.currentTime, duration = 0.4, type = 'sine', gain = 0.15 } = opts
  const osc = ac.createOscillator()
  const g = ac.createGain()
  osc.type = type
  osc.frequency.value = freq
  g.gain.setValueAtTime(0, at)
  g.gain.linearRampToValueAtTime(gain, at + 0.015)
  g.gain.exponentialRampToValueAtTime(0.0001, at + duration)
  osc.connect(g).connect(ac.destination)
  osc.start(at)
  osc.stop(at + duration + 0.05)
}
