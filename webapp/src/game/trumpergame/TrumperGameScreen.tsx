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

    drawSky(ctx, w, h, layout.groundY)
    drawGround(ctx, w, h, layout.groundY)
    for (let i = 0; i < layout.bases.length; i++) {
      const scale = bellyRef.current[i].value
      const center = trumperBodyCenter(layout.bases[i], layout.bodyR, scale)
      // Gas puffs sit behind the character.
      const gas = gasRef.current[i]
      if (gas.active) {
        drawGas(ctx, center, layout.bodyR, gas.elapsed / GAS_DURATION_MS)
      }
      drawTrumper(ctx, center, layout.bodyR, scale, LOOKS[i])
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

/** One little person: shadow, planted legs with shoes, shorts, a swelling shirt-belly,
 * arms with hands, a head with hair and a face that strains (reddens, mouth opens) as
 * the tummy scale grows. */
function drawTrumper(
  ctx: CanvasRenderingContext2D,
  center: { x: number; y: number },
  bodyR: number,
  scale: number,
  look: TrumperLook,
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

  // Arms with little hands (lift out a little as the belly swells).
  const armColor = lerpColor(look.shirt, '#000000', 0.15)
  const armLift = strain * bodyR * 0.35
  ctx.strokeStyle = armColor
  ctx.lineWidth = bodyR * 0.18
  ctx.lineCap = 'round'
  for (const sx of [-1, 1]) {
    const handCenter = { x: center.x + sx * r * 1.15, y: center.y - armLift }
    ctx.beginPath()
    ctx.moveTo(center.x + sx * r * 0.7, center.y)
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
