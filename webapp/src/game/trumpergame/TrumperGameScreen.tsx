import { PointerEvent, useMemo, useRef } from 'react'
import { DampingRatio, Spring, Stiffness } from '../../anim'
import { useGameCanvas } from '../../useGameCanvas'
import BackButton from '../../ui/components/BackButton'
import { createTrumperAudio } from './TrumperAudio'
import {
  BLOAT_SCALE,
  computeTrumperLayout,
  TRUMPER_COUNT,
  TRUMPER_GOAL,
  TrumperGameState,
  trumperBodyCenter,
  TrumperLayout,
} from '../../shared'

// TrumperLayout/computeTrumperLayout/trumperBodyCenter/TrumperGameState come from
// the shared Kotlin module — the exact code the Android app runs.

const GAS_DURATION_MS = 800

/** A friendly look for each character: shirt colour, skin tone, hair colour/style and
 * shoe colour. `hairStyle`: 0 = short spiky, 1 = pigtails, 2 = afro, 3 = top bun. */
interface TrumperLook {
  shirt: string
  skin: string
  hair: string
  shoe: string
  hairStyle: number
}

const LOOKS: TrumperLook[] = [
  { shirt: '#FF8FAB', skin: '#C68642', hair: '#2B2B2B', shoe: '#3A2412', hairStyle: 0 },
  { shirt: '#6FD3C2', skin: '#FFDBAC', hair: '#FF7F50', shoe: '#5C4033', hairStyle: 1 },
  { shirt: '#FFC857', skin: '#8D5524', hair: '#1A1A1A', shoe: '#2B2B2B', hairStyle: 2 },
  { shirt: '#9D8DF1', skin: '#F1C27D', hair: '#F4D35E', shoe: '#6B4F2A', hairStyle: 3 },
]

const PANTS_COLOR = '#4A6FA5'

// --- Opening sequence (each character pops out of its burrow shouting its name) ---
// Audio clips are keyed by column order: 0 = Penny, 1 = Taz, 2 = Zach, 3 = Rory
// (matching LOOKS and the bundled name OGGs). Mirrors the Android TrumperGameScreen.
const INTRO_LEAD_MS = 600 // a beat before the first pop (shows burrows, lets clips load)
const INTRO_JUMP_MS = 1300 // length of one character's pop-out jump
const INTRO_STAGGER_MS = 2000 // delay between one character popping out and the next
const POP_DEPTH = 2.3 // how far below ground (×bodyR) a not-yet-emerged character hides
const INTRO_TOTAL_MS = INTRO_LEAD_MS + INTRO_STAGGER_MS * (TRUMPER_COUNT - 1) + INTRO_JUMP_MS

// --- Round flow ---------------------------------------------------------------
// A round runs intro → play → celebrate → outro → replay, then loops back to intro
// when the child taps the play-again button. Mirrors the Android TrumperGameScreen.
type Phase = 'intro' | 'play' | 'celebrate' | 'outro' | 'replay'
const CELEBRATE_MS = 2200 // confetti + cheer + wave hold before the goodbye
const OUTRO_JUMP_MS = 700 // one character's hop back down into its burrow
const OUTRO_STAGGER_MS = 320 // delay between one character diving in and the next
const OUTRO_TOTAL_MS = OUTRO_STAGGER_MS * (TRUMPER_COUNT - 1) + OUTRO_JUMP_MS
const CONFETTI_MS = 4200 // falling-confetti run (covers celebrate + goodbye)
const CONFETTI_COUNT = 70
const CONFETTI_COLORS = ['#FF6B6B', '#FFD93D', '#6BCB77', '#4D96FF', '#B983FF', '#FF9F45']

/** One falling confetti piece; its motion is computed analytically from elapsed time. */
interface Confetti {
  x0: number
  color: string
  size: number
  speed: number
  driftAmp: number
  driftFreq: number
  phase: number
  rotSpeed: number
}

/** Overshoot ("back") ease-out: rises past the resting spot then settles — a little
 * hop as the character lands out of the hole. */
function easeOutBack(t: number): number {
  const s = 2.0
  const p = t - 1
  return 1 + (s + 1) * p * p * p + s * p * p
}

/** Anticipation ("back") ease-in: dips back before diving down — used as the
 * characters hop back into their burrows at the end of a round. */
function easeInBack(t: number): number {
  const s = 1.7
  return t * t * ((s + 1) * t - s)
}

const clamp01 = (x: number) => Math.min(Math.max(x, 0), 1)

/** Where character `i` sits between burrow (0) and standing (1) for the current flow. */
function trumperEmerge(f: { phase: Phase; t: number }, i: number): number {
  switch (f.phase) {
    case 'intro':
      return easeOutBack(clamp01((f.t - INTRO_LEAD_MS - i * INTRO_STAGGER_MS) / INTRO_JUMP_MS))
    case 'outro':
      return 1 - easeInBack(clamp01((f.t - i * OUTRO_STAGGER_MS) / OUTRO_JUMP_MS))
    case 'replay':
      return 0
    default:
      return 1
  }
}

function makeConfetti(w: number, h: number): Confetti[] {
  return Array.from({ length: CONFETTI_COUNT }, () => ({
    x0: Math.random() * w,
    color: CONFETTI_COLORS[Math.floor(Math.random() * CONFETTI_COLORS.length)],
    size: w * 0.008 + Math.random() * w * 0.012,
    speed: h * 0.25 + Math.random() * h * 0.4,
    driftAmp: w * 0.015 + Math.random() * w * 0.03,
    driftFreq: 1.5 + Math.random() * 2.5,
    phase: Math.random() * 2 * Math.PI,
    rotSpeed: (Math.random() - 0.5) * 12,
  }))
}

export default function TrumperGameScreen({ onBack }: { onBack: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const stateRef = useRef<TrumperGameState>()
  if (!stateRef.current) stateRef.current = new TrumperGameState()
  const audio = useMemo(createTrumperAudio, [])

  // Per-character animation: belly swell scale, and a 0→1 gas-puff burst.
  const bellyRef = useRef(Array.from({ length: TRUMPER_COUNT }, () => new Spring(1)))
  const gasRef = useRef(Array.from({ length: TRUMPER_COUNT }, () => ({ active: false, elapsed: 0 })))
  const layoutRef = useRef<{ w: number; h: number; layout: TrumperLayout } | null>(null)
  const confettiRef = useRef<{ w: number; h: number; pieces: Confetti[] } | null>(null)

  // Round-flow state, advanced each frame: which phase we're in and how long we've been
  // in it, the per-character name-shout flags, fading burrow opacity, confetti/wave
  // progress, the cheer-once latch, the next-bloat countdown and the replay pulse clock.
  const flow = useRef({
    phase: 'intro' as Phase,
    t: 0,
    played: Array.from({ length: TRUMPER_COUNT }, () => false),
    holeAlpha: 1,
    confetti: 0,
    wave: 0,
    cheered: false,
    bloatTimer: 0,
    replayT: 0,
  })

  const setPhase = (p: Phase) => {
    const f = flow.current
    f.phase = p
    f.t = 0
    if (p === 'play') f.bloatTimer = 900 + Math.random() * 2200
    if (p === 'celebrate') f.cheered = false
  }

  useGameCanvas(canvasRef, (ctx, w, h, dt) => {
    const state = stateRef.current!
    if (!layoutRef.current || layoutRef.current.w !== w || layoutRef.current.h !== h) {
      layoutRef.current = { w, h, layout: computeTrumperLayout(w, h) }
    }
    const layout = layoutRef.current.layout
    if (!confettiRef.current || confettiRef.current.w !== w || confettiRef.current.h !== h) {
      confettiRef.current = { w, h, pieces: makeConfetti(w, h) }
    }

    const f = flow.current
    f.t += dt
    switch (f.phase) {
      case 'intro': {
        // Stagger the pops, shouting each name once as it starts, then fade the burrows.
        for (let i = 0; i < TRUMPER_COUNT; i++) {
          if (!f.played[i] && f.t >= INTRO_LEAD_MS + i * INTRO_STAGGER_MS) {
            f.played[i] = true
            audio.playName(i)
          }
        }
        if (f.t >= INTRO_TOTAL_MS) {
          f.holeAlpha = Math.max(0, f.holeAlpha - dt / 500)
          if (f.holeAlpha <= 0) setPhase('play')
        }
        break
      }
      case 'play': {
        f.bloatTimer -= dt
        if (f.bloatTimer <= 0) {
          state.bloatRandom()
          f.bloatTimer = 900 + Math.random() * 2200
        }
        if (state.isWon) setPhase('celebrate')
        break
      }
      case 'celebrate': {
        if (!f.cheered) {
          audio.playCheer()
          f.cheered = true
        }
        f.wave = Math.min(1, f.wave + dt / 400)
        f.confetti = Math.min(1, f.confetti + dt / CONFETTI_MS)
        if (f.t >= CELEBRATE_MS) setPhase('outro')
        break
      }
      case 'outro': {
        f.holeAlpha = Math.min(1, f.holeAlpha + dt / 400)
        f.confetti = Math.min(1, f.confetti + dt / CONFETTI_MS)
        if (f.t >= OUTRO_TOTAL_MS) {
          f.wave = 0
          setPhase('replay')
        }
        break
      }
      case 'replay': {
        f.replayT += dt
        f.confetti = Math.min(1, f.confetti + dt / CONFETTI_MS)
        break
      }
    }

    // Drive each belly toward its target: a bouncy swell while bloated in play, a quick
    // shrink otherwise (so everyone deflates for the celebration).
    for (let i = 0; i < TRUMPER_COUNT; i++) {
      const belly = bellyRef.current[i]
      const target = state.isBloated(i) && f.phase === 'play' ? BLOAT_SCALE : 1
      if (belly.target !== target) {
        if (target > 1) belly.animateTo(target, Stiffness.veryLow, DampingRatio.lowBouncy)
        else belly.animateTo(target, 900, DampingRatio.noBouncy) // ≈ the 220ms shrink tween
      }
      belly.step(dt)
      const gas = gasRef.current[i]
      if (gas.active) {
        gas.elapsed += dt
        if (gas.elapsed >= GAS_DURATION_MS) gas.active = false
      }
    }

    // ---- Sky, ground, characters ----
    const grad = ctx.createLinearGradient(0, 0, 0, h)
    grad.addColorStop(0, '#BDE0FE')
    grad.addColorStop(1, '#A0D8C4')
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, w, h)

    drawSky(ctx, w, h, layout.groundY)
    drawGround(ctx, w, h, layout.groundY)

    // Burrows the characters pop out of / hop back into (faded except at the ends).
    if (f.holeAlpha > 0.01) {
      for (let i = 0; i < layout.bases.length; i++) {
        drawBurrow(ctx, layout.bases[i].x, layout.groundY, layout.bodyR, f.holeAlpha)
      }
    }

    const wavePhase = f.confetti * (CONFETTI_MS / 1000) * 3 * (2 * Math.PI)
    for (let i = 0; i < layout.bases.length; i++) {
      const scale = bellyRef.current[i].value
      const emerge = trumperEmerge(f, i)
      const popPx = (1 - emerge) * POP_DEPTH * layout.bodyR
      const base = trumperBodyCenter(layout.bases[i], layout.bodyR, scale)
      const center = { x: base.x, y: base.y + popPx }
      if (emerge < 0.999) {
        // Mid-hop in or out of the hole: clip away the part below ground.
        ctx.save()
        ctx.beginPath()
        ctx.rect(0, 0, w, layout.groundY)
        ctx.clip()
        drawTrumper(ctx, center, layout.bodyR, scale, LOOKS[i], f.wave, wavePhase)
        ctx.restore()
      } else {
        // Gas puffs sit behind the character.
        const gas = gasRef.current[i]
        if (gas.active) {
          drawGas(ctx, center, layout.bodyR, gas.elapsed / GAS_DURATION_MS)
        }
        drawTrumper(ctx, center, layout.bodyR, scale, LOOKS[i], f.wave, wavePhase)
      }
    }

    // Confetti rains over everything during the celebration.
    if (f.confetti > 0) drawConfetti(ctx, confettiRef.current.pieces, f.confetti, w, h)

    // Star tally of progress toward the goal, across the top.
    drawStars(ctx, w, h, state.score, TRUMPER_GOAL)

    // Play-again button once everyone has waved goodbye.
    if (f.phase === 'replay') {
      const pulse = (1 - Math.cos((f.replayT / 700) * Math.PI)) / 2
      drawReplayButton(ctx, w, h, pulse)
    }
  })

  const onPointerDown = (e: PointerEvent<HTMLCanvasElement>) => {
    if (!layoutRef.current) return
    const f = flow.current
    const state = stateRef.current!

    if (f.phase === 'replay') {
      // Tap anywhere to start a fresh round from the top.
      state.reset()
      for (let i = 0; i < TRUMPER_COUNT; i++) {
        bellyRef.current[i] = new Spring(1)
        gasRef.current[i] = { active: false, elapsed: 0 }
      }
      f.played = Array.from({ length: TRUMPER_COUNT }, () => false)
      f.holeAlpha = 1
      f.confetti = 0
      f.wave = 0
      f.cheered = false
      f.replayT = 0
      setPhase('intro')
      return
    }
    if (f.phase !== 'play') return

    const rect = e.currentTarget.getBoundingClientRect()
    const tap = { x: e.clientX - rect.left, y: e.clientY - rect.top }
    const layout = layoutRef.current.layout
    for (let i = 0; i < layout.bases.length; i++) {
      if (!state.isBloated(i)) continue
      const center = trumperBodyCenter(layout.bases[i], layout.bodyR, bellyRef.current[i].value)
      const r = layout.bodyR * bellyRef.current[i].value
      if (Math.hypot(tap.x - center.x, tap.y - center.y) <= r * 1.1) {
        if (state.release(i)) {
          audio.playFart()
          gasRef.current[i] = { active: true, elapsed: 0 }
        }
        break
      }
    }
  }

  return (
    <div className="relative h-full w-full">
      <canvas ref={canvasRef} className="h-full w-full" onPointerDown={onPointerDown} />
      <div className="absolute left-4 top-4">
        <BackButton onClick={onBack} />
      </div>
    </div>
  )
}

// --- Canvas drawing helpers ---------------------------------------------------

/** Linear blend between two hex colours (the web stand-in for Compose `lerp`). */
function lerpColor(a: string, b: string, t: number): string {
  const pa = parseInt(a.slice(1), 16)
  const pb = parseInt(b.slice(1), 16)
  const ch = (shift: number) => {
    const ca = (pa >> shift) & 0xff
    const cb = (pb >> shift) & 0xff
    return Math.round(ca + (cb - ca) * t)
  }
  return `rgb(${ch(16)},${ch(8)},${ch(0)})`
}

function circle(ctx: CanvasRenderingContext2D, color: string, r: number, c: { x: number; y: number }) {
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.arc(c.x, c.y, r, 0, Math.PI * 2)
  ctx.fill()
}

/** Sun, drifting clouds and rolling hills behind the ground. */
function drawSky(ctx: CanvasRenderingContext2D, w: number, h: number, groundY: number) {
  // Sun with a soft glow, top-right corner.
  const sun = { x: w * 0.86, y: h * 0.16 }
  circle(ctx, 'rgba(255,243,176,0.35)', w * 0.1, sun)
  circle(ctx, 'rgba(255,243,176,0.55)', w * 0.065, sun)
  circle(ctx, '#FFE066', w * 0.045, sun)

  // Drifting clouds.
  drawCloud(ctx, { x: w * 0.18, y: h * 0.16 }, w * 0.075)
  drawCloud(ctx, { x: w * 0.46, y: h * 0.1 }, w * 0.06)
  drawCloud(ctx, { x: w * 0.68, y: h * 0.3 }, w * 0.05)

  // Rolling hills, partially covered by the ground drawn afterwards.
  ctx.fillStyle = '#A0D8C4'
  ctx.beginPath()
  ctx.ellipse(-w * 0.10 + w * 0.325, groundY - h * 0.12 + h * 0.11, w * 0.325, h * 0.11, 0, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = '#8FCDB6'
  ctx.beginPath()
  ctx.ellipse(w * 0.45 + w * 0.375, groundY - h * 0.16 + h * 0.13, w * 0.375, h * 0.13, 0, 0, Math.PI * 2)
  ctx.fill()
}

function drawCloud(ctx: CanvasRenderingContext2D, center: { x: number; y: number }, r: number) {
  ctx.fillStyle = 'rgba(255,255,255,0.85)'
  circle(ctx, 'rgba(255,255,255,0.85)', r, { x: center.x - r * 0.9, y: center.y })
  circle(ctx, 'rgba(255,255,255,0.85)', r * 1.2, center)
  circle(ctx, 'rgba(255,255,255,0.85)', r * 0.85, { x: center.x + r * 1.0, y: center.y + r * 0.1 })
  ctx.beginPath()
  ctx.ellipse(center.x, center.y + r * 0.45, r * 1.6, r * 0.45, 0, 0, Math.PI * 2)
  ctx.fill()
}

function drawGround(ctx: CanvasRenderingContext2D, w: number, h: number, groundY: number) {
  ctx.fillStyle = '#8AC926'
  ctx.fillRect(0, groundY, w, h - groundY)
  // soft grass line
  ctx.fillStyle = '#6FA31C'
  ctx.fillRect(0, groundY, w, h * 0.012)

  // Little flowers dotted along the grass.
  ;[0.06, 0.30, 0.50, 0.71, 0.93].forEach((fx, k) => {
    const petal = k % 2 === 0 ? '#FFB3C6' : '#FFFFFF'
    drawFlower(ctx, { x: w * fx, y: groundY + h * 0.05 }, w * 0.012, petal)
  })

  // Tufts of grass.
  ;[0.14, 0.22, 0.40, 0.60, 0.80, 0.88].forEach((fx) => {
    drawGrassTuft(ctx, { x: w * fx, y: groundY }, w * 0.018)
  })
}

function drawFlower(ctx: CanvasRenderingContext2D, center: { x: number; y: number }, r: number, petalColor: string) {
  for (const sx of [-1, 0, 1]) {
    for (const sy of [-1, 0, 1]) {
      if (sx === 0 && sy === 0) continue
      if (sx !== 0 && sy !== 0) continue
      circle(ctx, petalColor, r * 0.6, { x: center.x + sx * r, y: center.y + sy * r })
    }
  }
  circle(ctx, '#FFD23F', r * 0.55, center)
}

function drawGrassTuft(ctx: CanvasRenderingContext2D, base: { x: number; y: number }, h: number) {
  ctx.strokeStyle = '#6FA31C'
  ctx.lineWidth = h * 0.12
  ctx.lineCap = 'round'
  for (const sx of [-1, 0, 1]) {
    ctx.beginPath()
    ctx.moveTo(base.x + sx * h * 0.3, base.y + h * 0.15)
    ctx.lineTo(base.x + sx * h * 0.55, base.y - h * (0.7 - Math.abs(sx) * 0.2))
    ctx.stroke()
  }
}

/** A little dirt burrow opening straddling the ground line, that a character pops out
 * of during the opening sequence. `alpha` fades the whole thing in/out. */
function drawBurrow(ctx: CanvasRenderingContext2D, cx: number, groundY: number, bodyR: number, alpha: number) {
  const rimW = bodyR * 1.7
  const rimH = bodyR * 0.5
  // Mounded dirt rim sitting on the grass.
  ctx.fillStyle = `rgba(122,82,48,${alpha})`
  ctx.beginPath()
  ctx.ellipse(cx, groundY - rimH * 0.05, rimW / 2, rimH / 2, 0, 0, Math.PI * 2)
  ctx.fill()
  // Dark opening the character rises out of.
  const openW = rimW * 0.74
  const openH = rimH * 0.7
  ctx.fillStyle = `rgba(42,27,16,${alpha})`
  ctx.beginPath()
  ctx.ellipse(cx, groundY, openW / 2, openH / 2, 0, 0, Math.PI * 2)
  ctx.fill()
}

/** One little person: shadow, planted legs with shoes, shorts, a swelling shirt-belly,
 * arms with hands, a head with hair and a face that strains (reddens, mouth opens) as
 * the tummy scale grows. `waveAmount` (0..1) blends the arms up into an overhead
 * goodbye wave that wiggles with `wavePhase`. */
function drawTrumper(
  ctx: CanvasRenderingContext2D,
  center: { x: number; y: number },
  bodyR: number,
  scale: number,
  look: TrumperLook,
  waveAmount = 0,
  wavePhase = 0,
) {
  const r = bodyR * scale
  const feetY = center.y + r
  // strain 0..1 — how bloated this character looks right now
  const strain = Math.min(Math.max((scale - 1) / (BLOAT_SCALE - 1), 0), 1)
  const bodyColor = lerpColor(look.shirt, '#E85D5D', strain * 0.55)

  // Ground shadow.
  ctx.fillStyle = 'rgba(0,0,0,0.14)'
  ctx.beginPath()
  ctx.ellipse(center.x, feetY + bodyR * 0.04 + bodyR * 0.14, bodyR * 0.65, bodyR * 0.14, 0, 0, Math.PI * 2)
  ctx.fill()

  // Legs with shoes (planted on the ground, just under the body).
  const legW = bodyR * 0.22
  const legH = bodyR * 0.45
  for (const sx of [-1, 1]) {
    const legX = center.x + sx * bodyR * 0.45
    ctx.fillStyle = PANTS_COLOR
    roundRect(ctx, legX - legW / 2, feetY - legH * 0.4, legW, legH * 0.55, legW * 0.3)
    ctx.fillStyle = look.skin
    roundRect(ctx, legX - legW * 0.36, feetY - legH * 0.05, legW * 0.72, legH * 0.2, legW * 0.2)
    ctx.fillStyle = look.shoe
    ctx.beginPath()
    ctx.ellipse(legX, feetY - legH * 0.04 + legH * 0.16, legW * 0.65, legH * 0.16, 0, 0, Math.PI * 2)
    ctx.fill()
  }

  // Shorts peeking out below the shirt.
  ctx.fillStyle = PANTS_COLOR
  ctx.beginPath()
  ctx.ellipse(center.x, feetY - r * 0.55 + r * 0.275, r * 0.62, r * 0.275, 0, 0, Math.PI * 2)
  ctx.fill()

  // Arms with little hands (lift out a little as the belly swells, and raise into an
  // overhead wiggle when waving goodbye).
  const armColor = lerpColor(look.shirt, '#000000', 0.15)
  const armLift = strain * bodyR * 0.35
  ctx.strokeStyle = armColor
  ctx.lineWidth = bodyR * 0.18
  ctx.lineCap = 'round'
  for (const sx of [-1, 1]) {
    const shoulder = { x: center.x + sx * r * 0.7, y: center.y }
    const rest = { x: center.x + sx * r * 1.15, y: center.y - armLift }
    // Raised, wiggling hand for the goodbye wave (arms wave in opposite phase).
    const wiggle = Math.sin(wavePhase + (sx < 0 ? Math.PI : 0)) * bodyR * 0.3
    const raised = { x: center.x + sx * r * 0.85 + wiggle, y: center.y - r * 1.05 }
    const handCenter = {
      x: rest.x + (raised.x - rest.x) * waveAmount,
      y: rest.y + (raised.y - rest.y) * waveAmount,
    }
    ctx.beginPath()
    ctx.moveTo(shoulder.x, shoulder.y)
    ctx.lineTo(handCenter.x, handCenter.y)
    ctx.stroke()
    circle(ctx, look.skin, bodyR * 0.13, handCenter)
  }

  // Shirt-belly.
  circle(ctx, bodyColor, r, center)
  // soft belly highlight
  circle(ctx, 'rgba(255,255,255,0.18)', r * 0.55, { x: center.x - r * 0.28, y: center.y - r * 0.28 })
  // collar
  ctx.fillStyle = 'rgba(255,255,255,0.35)'
  ctx.beginPath()
  ctx.moveTo(center.x - r * 0.22, center.y - r * 0.92)
  ctx.lineTo(center.x, center.y - r * 0.62)
  ctx.lineTo(center.x + r * 0.22, center.y - r * 0.92)
  ctx.closePath()
  ctx.fill()

  // Head sitting on top of the belly.
  const headR = bodyR * 0.55
  const headCenter = { x: center.x, y: center.y - r - headR * 0.55 }

  // Hair drawn behind the head for styles that frame the face (afro, pigtails).
  if (look.hairStyle === 2 || look.hairStyle === 1) {
    drawHairBack(ctx, headCenter, headR, look.hair, look.hairStyle)
  }

  circle(ctx, look.skin, headR, headCenter)

  // Ears.
  for (const sx of [-1, 1]) {
    circle(ctx, look.skin, headR * 0.13, { x: headCenter.x + sx * headR * 0.92, y: headCenter.y + headR * 0.05 })
  }

  // Eyes.
  const eyeDx = headR * 0.42
  const eyeY = headCenter.y - headR * 0.05
  const eyeR = headR * 0.16
  for (const sx of [-1, 1]) {
    circle(ctx, '#FFFFFF', eyeR, { x: headCenter.x + sx * eyeDx, y: eyeY })
    circle(ctx, '#2B2B2B', eyeR * 0.55, { x: headCenter.x + sx * eyeDx, y: eyeY })
  }

  // Eyebrows: neutral, but knit together a little as strain builds.
  const browLift = strain * headR * 0.10
  ctx.strokeStyle = look.hair
  ctx.lineWidth = headR * 0.07
  ctx.lineCap = 'round'
  for (const sx of [-1, 1]) {
    const bx = headCenter.x + sx * eyeDx
    const by = eyeY - headR * 0.32 + browLift
    ctx.beginPath()
    ctx.moveTo(bx - eyeR * 0.9, by + sx * browLift * 0.6)
    ctx.lineTo(bx + eyeR * 0.9, by - sx * browLift * 0.6)
    ctx.stroke()
  }

  // Mouth: a calm little smile that opens into a strained "O" as it bloats.
  const mouthC = { x: headCenter.x, y: headCenter.y + headR * 0.42 }
  const mouthH = headR * (0.1 + strain * 0.4)
  const mouthW = headR * (0.42 - strain * 0.1)
  ctx.fillStyle = '#7A2B2B'
  ctx.beginPath()
  ctx.ellipse(mouthC.x, mouthC.y, mouthW, mouthH, 0, 0, Math.PI * 2)
  ctx.fill()

  // Hair drawn on top of/around the head for styles that sit above it.
  if (look.hairStyle === 0 || look.hairStyle === 3) {
    drawHairFront(ctx, headCenter, headR, look.hair, look.hairStyle)
  }

  // Straining extras: blush cheeks + a sweat drop.
  if (strain > 0.35) {
    const blushAlpha = Math.min(Math.max(strain - 0.35, 0), 1) * 0.7
    for (const sx of [-1, 1]) {
      circle(ctx, `rgba(255,92,122,${blushAlpha})`, headR * 0.18, {
        x: headCenter.x + sx * headR * 0.6,
        y: headCenter.y + headR * 0.2,
      })
    }
    // sweat drop near the temple
    circle(ctx, `rgba(127,200,248,${strain})`, headR * 0.12, {
      x: headCenter.x + headR * 0.85,
      y: headCenter.y - headR * 0.5,
    })
  }
}

/** Hair that frames the face from behind: a big afro puff (style 2) or pigtail
 * bunches either side of the head (style 1). */
function drawHairBack(
  ctx: CanvasRenderingContext2D,
  headCenter: { x: number; y: number },
  headR: number,
  color: string,
  style: number,
) {
  if (style === 2) {
    circle(ctx, color, headR * 1.12, { x: headCenter.x, y: headCenter.y - headR * 0.05 })
  } else if (style === 1) {
    for (const sx of [-1, 1]) {
      circle(ctx, color, headR * 0.40, { x: headCenter.x + sx * headR * 1.18, y: headCenter.y + headR * 0.20 })
    }
  }
}

/** Hair that sits on top of the head: short spiky tufts (style 0) or a top-knot bun
 * over a hairline cap (style 3). */
function drawHairFront(
  ctx: CanvasRenderingContext2D,
  headCenter: { x: number; y: number },
  headR: number,
  color: string,
  style: number,
) {
  if (style === 0) {
    ctx.fillStyle = color
    for (const sx of [-2, -1, 0, 1, 2]) {
      const baseX = headCenter.x + sx * headR * 0.32
      const baseY = headCenter.y - headR * 0.78
      ctx.beginPath()
      ctx.moveTo(baseX - headR * 0.16, baseY)
      ctx.lineTo(baseX + headR * 0.16, baseY)
      ctx.lineTo(baseX + sx * headR * 0.06, baseY - headR * 0.42)
      ctx.closePath()
      ctx.fill()
    }
  } else if (style === 3) {
    // hairline cap
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.arc(headCenter.x, headCenter.y, headR, Math.PI, Math.PI * 2)
    ctx.closePath()
    ctx.fill()
    // top-knot bun
    circle(ctx, color, headR * 0.28, { x: headCenter.x, y: headCenter.y - headR * 1.05 })
  }
}

/** A burst of greenish stink clouds drifting up and away from behind the character. */
function drawGas(ctx: CanvasRenderingContext2D, center: { x: number; y: number }, bodyR: number, p: number) {
  const origin = { x: center.x + bodyR * 0.7, y: center.y + bodyR * 0.5 }
  const fade = Math.min(Math.max(1 - p, 0), 1)
  for (let k = 0; k < 5; k++) {
    const drift = p * bodyR * (1.3 + 0.35 * k)
    const angle = -0.6 + 0.25 * k // fan upward/outward
    const cx = origin.x + drift * Math.cos(angle)
    const cy = origin.y - drift * 0.7
    const rad = bodyR * (0.16 + 0.1 * k) * (0.5 + p)
    circle(ctx, `rgba(156,204,60,${fade * 0.45})`, rad, { x: cx, y: cy })
  }
  // a couple of little speed lines near the source
  ctx.strokeStyle = `rgba(156,204,60,${fade * 0.6})`
  ctx.lineWidth = bodyR * 0.04
  for (let k = 0; k < 3; k++) {
    const a = -0.5 + 0.3 * k
    const s = { x: origin.x + bodyR * 0.1 * Math.cos(a), y: origin.y + bodyR * 0.1 * Math.sin(a) }
    const e = { x: s.x + bodyR * (0.6 + p * 0.6) * Math.cos(a), y: s.y - bodyR * 0.4 * (0.6 + p) }
    ctx.beginPath()
    ctx.moveTo(s.x, s.y)
    ctx.lineTo(e.x, e.y)
    ctx.stroke()
  }
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.roundRect(x, y, w, h, r)
  ctx.fill()
}

/** Trace a five-pointed star path centred on (cx, cy). */
function starPath(ctx: CanvasRenderingContext2D, cx: number, cy: number, rOuter: number) {
  const rInner = rOuter * 0.45
  ctx.beginPath()
  for (let k = 0; k < 10; k++) {
    const rr = k % 2 === 0 ? rOuter : rInner
    const a = -Math.PI / 2 + (k * Math.PI) / 5
    const x = cx + rr * Math.cos(a)
    const y = cy + rr * Math.sin(a)
    if (k === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  }
  ctx.closePath()
}

/** Row of `total` stars across the top; the first `filled` are gold, the rest faint. */
function drawStars(ctx: CanvasRenderingContext2D, w: number, h: number, filled: number, total: number) {
  const r = h * 0.028
  const gap = r * 2.4
  const startX = w / 2 - (gap * (total - 1)) / 2
  const y = h * 0.085
  for (let i = 0; i < total; i++) {
    const cx = startX + i * gap
    starPath(ctx, cx, y, r)
    if (i < filled) {
      ctx.fillStyle = '#FFD23F'
      ctx.fill()
      ctx.lineWidth = r * 0.12
      ctx.strokeStyle = '#E0A800'
      ctx.stroke()
    } else {
      ctx.fillStyle = 'rgba(255,255,255,0.25)'
      ctx.fill()
      ctx.lineWidth = r * 0.1
      ctx.strokeStyle = 'rgba(255,255,255,0.5)'
      ctx.stroke()
    }
  }
}

/** Falling confetti: each piece's position/rotation is derived from `progress` (0..1)
 * so the rain is stable across frames, fading out as it completes. */
function drawConfetti(ctx: CanvasRenderingContext2D, pieces: Confetti[], progress: number, w: number, h: number) {
  const t = progress * (CONFETTI_MS / 1000)
  const span = h + w * 0.06
  const fade = progress > 0.85 ? clamp01(1 - (progress - 0.85) / 0.15) : 1
  ctx.globalAlpha = fade
  for (const c of pieces) {
    const wrap = span + c.size * 2
    let y = ((c.size + t * c.speed) % wrap) - c.size
    if (y < -c.size) y += wrap
    const x = c.x0 + Math.sin(t * c.driftFreq + c.phase) * c.driftAmp
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate((t * c.rotSpeed * 60 * Math.PI) / 180)
    ctx.fillStyle = c.color
    ctx.fillRect(-c.size / 2, -c.size * 0.35, c.size, c.size * 0.7)
    ctx.restore()
  }
  ctx.globalAlpha = 1
}

/** A big green play-again button with a white play triangle; `pulse` (0..1) gently
 * breathes its size to invite a tap. */
function drawReplayButton(ctx: CanvasRenderingContext2D, w: number, h: number, pulse: number) {
  const cx = w / 2
  const cy = h * 0.52
  const r = h * 0.13 * (1 + pulse * 0.06)
  ctx.fillStyle = 'rgba(255,255,255,0.35)'
  ctx.beginPath()
  ctx.arc(cx, cy, r * 1.18, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = '#6BCB77'
  ctx.beginPath()
  ctx.arc(cx, cy, r, 0, Math.PI * 2)
  ctx.fill()
  ctx.strokeStyle = '#FFFFFF'
  ctx.lineWidth = r * 0.08
  ctx.beginPath()
  ctx.arc(cx, cy, r, 0, Math.PI * 2)
  ctx.stroke()
  const s = r * 0.5
  ctx.fillStyle = '#FFFFFF'
  ctx.beginPath()
  ctx.moveTo(cx - s * 0.5, cy - s)
  ctx.lineTo(cx - s * 0.5, cy + s)
  ctx.lineTo(cx + s * 0.85, cy)
  ctx.closePath()
  ctx.fill()
}
