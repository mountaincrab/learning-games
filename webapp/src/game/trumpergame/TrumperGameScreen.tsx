import { PointerEvent, useEffect, useMemo, useRef } from 'react'
import { DampingRatio, Spring, Stiffness } from '../../anim'
import { useGameCanvas } from '../../useGameCanvas'
import BackButton from '../../ui/components/BackButton'
import { createTrumperAudio } from './TrumperAudio'
import {
  BLOAT_SCALE,
  computeTrumperLayout,
  TRUMPER_COUNT,
  TrumperGameState,
  trumperBodyCenter,
  TrumperLayout,
} from '../../shared'

// TrumperLayout/computeTrumperLayout/trumperBodyCenter/TrumperGameState come from
// the shared Kotlin module — the exact code the Android app runs.

const GAS_DURATION_MS = 800

/** Distinct, friendly body colours for the four characters. */
const BODY_COLORS = ['#FF8FAB', '#6FD3C2', '#FFC857', '#9D8DF1']

export default function TrumperGameScreen({ onBack }: { onBack: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const stateRef = useRef<TrumperGameState>()
  if (!stateRef.current) stateRef.current = new TrumperGameState()
  const audio = useMemo(createTrumperAudio, [])

  // Per-character animation: belly swell scale, and a 0→1 gas-puff burst.
  const bellyRef = useRef(Array.from({ length: TRUMPER_COUNT }, () => new Spring(1)))
  const gasRef = useRef(Array.from({ length: TRUMPER_COUNT }, () => ({ active: false, elapsed: 0 })))
  const layoutRef = useRef<{ w: number; h: number; layout: TrumperLayout } | null>(null)

  // Bloat a random character at gentle random intervals.
  useEffect(() => {
    let timer = 0
    const tick = () => {
      stateRef.current!.bloatRandom()
      timer = window.setTimeout(tick, 900 + Math.random() * 2200)
    }
    timer = window.setTimeout(tick, 900 + Math.random() * 2200)
    return () => clearTimeout(timer)
  }, [])

  useGameCanvas(canvasRef, (ctx, w, h, dt) => {
    const state = stateRef.current!
    if (!layoutRef.current || layoutRef.current.w !== w || layoutRef.current.h !== h) {
      layoutRef.current = { w, h, layout: computeTrumperLayout(w, h) }
    }
    const layout = layoutRef.current.layout

    // Drive each belly toward its target: a slow bouncy swell, a quick shrink.
    for (let i = 0; i < TRUMPER_COUNT; i++) {
      const belly = bellyRef.current[i]
      const target = state.isBloated(i) ? BLOAT_SCALE : 1
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

    drawGround(ctx, w, h, layout.groundY)
    for (let i = 0; i < layout.bases.length; i++) {
      const scale = bellyRef.current[i].value
      const center = trumperBodyCenter(layout.bases[i], layout.bodyR, scale)
      // Gas puffs sit behind the character.
      const gas = gasRef.current[i]
      if (gas.active) {
        drawGas(ctx, center, layout.bodyR, gas.elapsed / GAS_DURATION_MS)
      }
      drawTrumper(ctx, center, layout.bodyR, scale, BODY_COLORS[i])
    }
  })

  const onPointerDown = (e: PointerEvent<HTMLCanvasElement>) => {
    if (!layoutRef.current) return
    const rect = e.currentTarget.getBoundingClientRect()
    const tap = { x: e.clientX - rect.left, y: e.clientY - rect.top }
    const state = stateRef.current!
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

function drawGround(ctx: CanvasRenderingContext2D, w: number, h: number, groundY: number) {
  ctx.fillStyle = '#8AC926'
  ctx.fillRect(0, groundY, w, h - groundY)
  // soft grass line
  ctx.fillStyle = '#6FA31C'
  ctx.fillRect(0, groundY, w, h * 0.012)
}

/** One little round character: planted legs, a swelling belly-body, arms, head and a
 * face that strains (reddens, mouth opens) as the tummy scale grows. */
function drawTrumper(
  ctx: CanvasRenderingContext2D,
  center: { x: number; y: number },
  bodyR: number,
  scale: number,
  color: string,
) {
  const r = bodyR * scale
  const feetY = center.y + r
  // strain 0..1 — how bloated this character looks right now
  const strain = Math.min(Math.max((scale - 1) / (BLOAT_SCALE - 1), 0), 1)
  const bodyColor = lerpColor(color, '#E85D5D', strain * 0.55)

  // Legs (planted on the ground, just under the body).
  const legW = bodyR * 0.2
  const legH = bodyR * 0.45
  const legColor = lerpColor(color, '#000000', 0.25)
  ctx.fillStyle = legColor
  for (const sx of [-1, 1]) {
    roundRect(ctx, center.x + sx * bodyR * 0.45 - legW / 2, feetY - legH * 0.4, legW, legH, legW / 2)
  }

  // Arms (lift out a little as the belly swells).
  const armColor = lerpColor(color, '#000000', 0.15)
  const armLift = strain * bodyR * 0.35
  ctx.strokeStyle = armColor
  ctx.lineWidth = bodyR * 0.18
  ctx.lineCap = 'round'
  for (const sx of [-1, 1]) {
    ctx.beginPath()
    ctx.moveTo(center.x + sx * r * 0.7, center.y)
    ctx.lineTo(center.x + sx * r * 1.15, center.y - armLift)
    ctx.stroke()
  }

  // Belly-body.
  circle(ctx, bodyColor, r, center)
  // soft belly highlight
  circle(ctx, 'rgba(255,255,255,0.18)', r * 0.55, { x: center.x - r * 0.28, y: center.y - r * 0.28 })

  // Head sitting on top of the belly.
  const headR = bodyR * 0.55
  const headCenter = { x: center.x, y: center.y - r - headR * 0.55 }
  circle(ctx, lerpColor(color, '#E85D5D', strain * 0.4), headR, headCenter)

  // Eyes.
  const eyeDx = headR * 0.42
  const eyeY = headCenter.y - headR * 0.05
  const eyeR = headR * 0.16
  for (const sx of [-1, 1]) {
    circle(ctx, '#FFFFFF', eyeR, { x: headCenter.x + sx * eyeDx, y: eyeY })
    circle(ctx, '#2B2B2B', eyeR * 0.55, { x: headCenter.x + sx * eyeDx, y: eyeY })
  }

  // Mouth: a calm little smile that opens into a strained "O" as it bloats.
  const mouthC = { x: headCenter.x, y: headCenter.y + headR * 0.42 }
  const mouthH = headR * (0.1 + strain * 0.4)
  const mouthW = headR * (0.42 - strain * 0.1)
  ctx.fillStyle = '#7A2B2B'
  ctx.beginPath()
  ctx.ellipse(mouthC.x, mouthC.y, mouthW, mouthH, 0, 0, Math.PI * 2)
  ctx.fill()

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
