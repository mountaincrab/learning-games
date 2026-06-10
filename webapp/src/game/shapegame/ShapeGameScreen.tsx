import { PointerEvent, useEffect, useMemo, useRef, useState } from 'react'
import { DampingRatio, Spring, Stiffness } from '../../anim'
import { useGameCanvas } from '../../useGameCanvas'
import BackButton from '../../ui/components/BackButton'
import { createGameAudio } from './GameAudio'
import { drawFilled, drawOutline } from './ShapeRenderer'
import { computeHatLayout, HatLayout, ShapeGameState, shapeFillColorCss, TRAY_SIZE } from '../../shared'

// HatLayout/computeHatLayout/ShapeGameState come from the shared Kotlin module —
// the exact code the Android app runs.

const WAND_DURATION_MS = 1300

/** Per-tray-slot animation state for the draggable piece living there. */
interface PieceAnim {
  pieceId: number
  x: Spring
  y: Spring
  pop: Spring
  dragging: boolean
}

export default function ShapeGameScreen({ onBack }: { onBack: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const stateRef = useRef<ShapeGameState>()
  if (!stateRef.current) stateRef.current = new ShapeGameState()
  const [won, setWon] = useState(false)

  const audio = useMemo(createGameAudio, [])
  // Gentle background music while the game screen is on-stage.
  useEffect(() => {
    audio.startMusic()
    return () => audio.stopMusic()
  }, [audio])

  const layoutRef = useRef<{ w: number; h: number; layout: HatLayout } | null>(null)
  const piecesRef = useRef<(PieceAnim | null)[]>(Array(TRAY_SIZE).fill(null))
  // Wand sweep over the hole, played after each correct match (0..1 progress).
  const wandRef = useRef<{ active: boolean; elapsed: number }>({ active: false, elapsed: 0 })
  const dragRef = useRef<{ pointerId: number; pieceId: number; lastX: number; lastY: number } | null>(null)

  const layoutFor = (w: number, h: number): HatLayout => {
    if (!layoutRef.current || layoutRef.current.w !== w || layoutRef.current.h !== h) {
      layoutRef.current = { w, h, layout: computeHatLayout(w, h) }
      // Stage resized: send resting pieces back to their (new) homes.
      piecesRef.current.forEach((p, i) => {
        if (p && !p.dragging) {
          const home = layoutRef.current!.layout.trayHomes[i]
          p.x.snapTo(home.x)
          p.y.snapTo(home.y)
        }
      })
    }
    return layoutRef.current.layout
  }

  useGameCanvas(canvasRef, (ctx, w, h, dt) => {
    const state = stateRef.current!
    const layout = layoutFor(w, h)

    // Sync piece animations with the tray: a fresh piece pops in at its home.
    state.tray.forEach((piece, i) => {
      const anim = piecesRef.current[i]
      if (!piece) {
        piecesRef.current[i] = null
      } else if (!anim || anim.pieceId !== piece.pieceId) {
        const home = layout.trayHomes[i]
        const pop = new Spring(0.4, Stiffness.medium, DampingRatio.mediumBouncy)
        pop.animateTo(1)
        piecesRef.current[i] = {
          pieceId: piece.pieceId,
          x: new Spring(home.x),
          y: new Spring(home.y),
          pop,
          dragging: false,
        }
      }
    })

    for (const p of piecesRef.current) {
      if (!p) continue
      p.x.step(dt)
      p.y.step(dt)
      p.pop.step(dt)
    }
    if (wandRef.current.active) {
      wandRef.current.elapsed += dt
      if (wandRef.current.elapsed >= WAND_DURATION_MS) wandRef.current.active = false
    }

    // ---- Background, curtains, hat, outlines, matched fills, wand ----
    const grad = ctx.createLinearGradient(0, 0, 0, h)
    grad.addColorStop(0, '#3A1C5A')
    grad.addColorStop(1, '#1B0E2E')
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, w, h)

    drawCurtains(ctx, w, h)
    drawHat(ctx, layout)

    state.slots.forEach((slot, i) => {
      const center = layout.slotCenters[i]
      const s = layout.slotSize
      ctx.save()
      ctx.translate(center.x - s / 2, center.y - s / 2)
      if (state.isFilled(slot.id)) {
        drawFilled(ctx, slot.typeName, s, shapeFillColorCss(slot.typeName))
      } else {
        drawOutline(ctx, slot.typeName, s, 'rgba(255,255,255,0.85)', s * 0.045)
      }
      ctx.restore()
    })

    if (wandRef.current.active) {
      drawWand(ctx, layout.openingCenter, layout.wandScale, wandRef.current.elapsed / WAND_DURATION_MS)
    }

    // ---- Draggable tray pieces (on top of everything) ----
    state.tray.forEach((piece, i) => {
      const anim = piecesRef.current[i]
      if (!piece || !anim) return
      const s = layout.pieceSize
      ctx.save()
      ctx.translate(anim.x.value + s / 2, anim.y.value + s / 2)
      ctx.scale(anim.pop.value, anim.pop.value)
      ctx.translate(-s / 2, -s / 2)
      drawFilled(ctx, piece.typeName, s, shapeFillColorCss(piece.typeName))
      ctx.restore()
    })
  })

  const stagePos = (e: PointerEvent<HTMLCanvasElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    return { x: e.clientX - rect.left, y: e.clientY - rect.top }
  }

  const onPointerDown = (e: PointerEvent<HTMLCanvasElement>) => {
    if (dragRef.current || !layoutRef.current) return
    const { x, y } = stagePos(e)
    const size = layoutRef.current.layout.pieceSize
    // Topmost piece under the pointer wins.
    for (let i = piecesRef.current.length - 1; i >= 0; i--) {
      const p = piecesRef.current[i]
      if (!p) continue
      if (x >= p.x.value && x <= p.x.value + size && y >= p.y.value && y <= p.y.value + size) {
        p.dragging = true
        dragRef.current = { pointerId: e.pointerId, pieceId: p.pieceId, lastX: x, lastY: y }
        e.currentTarget.setPointerCapture(e.pointerId)
        break
      }
    }
  }

  const onPointerMove = (e: PointerEvent<HTMLCanvasElement>) => {
    const drag = dragRef.current
    if (!drag || drag.pointerId !== e.pointerId) return
    const piece = piecesRef.current.find((p) => p?.pieceId === drag.pieceId)
    if (!piece) return
    const { x, y } = stagePos(e)
    piece.x.snapTo(piece.x.value + (x - drag.lastX))
    piece.y.snapTo(piece.y.value + (y - drag.lastY))
    drag.lastX = x
    drag.lastY = y
  }

  const onPointerUp = (e: PointerEvent<HTMLCanvasElement>) => {
    const drag = dragRef.current
    if (!drag || drag.pointerId !== e.pointerId) return
    dragRef.current = null
    const state = stateRef.current!
    const layout = layoutRef.current!.layout
    const trayIndex = piecesRef.current.findIndex((p) => p?.pieceId === drag.pieceId)
    const piece = trayIndex >= 0 ? piecesRef.current[trayIndex] : null
    if (!piece) return
    piece.dragging = false

    const pieceCenter = { x: piece.x.value + layout.pieceSize / 2, y: piece.y.value + layout.pieceSize / 2 }
    const threshold = layout.slotSize * 0.9
    // Nearest empty slot within threshold; tryDrop validates type.
    let nearest: { id: number; dist: number } | null = null
    for (let i = 0; i < state.slots.length; i++) {
      const slot = state.slots[i]
      if (state.isFilled(slot.id)) continue
      const c = layout.slotCenters[i]
      const dist = Math.hypot(pieceCenter.x - c.x, pieceCenter.y - c.y)
      if (dist < threshold && (!nearest || dist < nearest.dist)) nearest = { id: slot.id, dist }
    }

    if (nearest && state.tryDrop(nearest.id, drag.pieceId)) {
      audio.playMagicChime()
      state.refillTray()
      wandRef.current = { active: true, elapsed: 0 }
      if (state.isWon) setWon(true)
    } else {
      const home = layout.trayHomes[trayIndex]
      piece.x.animateTo(home.x, Stiffness.medium, DampingRatio.noBouncy)
      piece.y.animateTo(home.y, Stiffness.medium, DampingRatio.noBouncy)
    }
  }

  const playAgain = () => {
    stateRef.current!.reset()
    piecesRef.current = Array(TRAY_SIZE).fill(null)
    setWon(false)
  }

  return (
    <div className="relative h-full w-full">
      <canvas
        ref={canvasRef}
        className="h-full w-full"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      />

      <div className="absolute left-4 top-4">
        <BackButton onClick={onBack} />
      </div>

      {won && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/45">
          <div className="flex flex-col items-center rounded-2xl bg-white p-7 shadow-xl">
            <p className="text-[26px] font-bold text-[#2B2B2B]">All done! 🎉</p>
            <button
              onClick={playAgain}
              className="mt-4 rounded-full bg-[#7B3FA0] px-6 py-2.5 font-bold text-white transition-transform active:scale-95"
            >
              Play again
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// --- Canvas drawing helpers ---------------------------------------------------

function drawCurtains(ctx: CanvasRenderingContext2D, w: number, h: number) {
  ctx.fillStyle = '#8E2434'
  // top valance
  ctx.fillRect(0, 0, w, h * 0.1)
  // side drapes
  ctx.fillRect(0, 0, w * 0.08, h)
  ctx.fillRect(w * 0.92, 0, w * 0.08, h)
}

function drawHat(ctx: CanvasRenderingContext2D, l: HatLayout) {
  const body = '#7B3FA0'
  const bodyDark = '#5E2C7E'
  const band = '#FFD23F'
  const stand = '#2BB6A8'
  const hole = '#1E0F33'

  const ellipse = (color: string, x: number, y: number, ew: number, eh: number) => {
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.ellipse(x + ew / 2, y + eh / 2, ew / 2, eh / 2, 0, 0, Math.PI * 2)
    ctx.fill()
  }

  // Little stand the up-turned crown balances on.
  ellipse(stand, l.cx - l.bodyBotHalf * 1.5, l.bodyBottomY - l.brimH * 0.15, l.bodyBotHalf * 3, l.brimH * 0.8)

  // Brim disc at the top (drawn behind the body so only the wings show).
  ellipse(bodyDark, l.cx - l.brimW / 2, l.bodyTopY - l.brimH / 2, l.brimW, l.brimH)

  // Body (trapezoid): wide at the top, narrowing to the crown.
  ctx.fillStyle = body
  ctx.beginPath()
  ctx.moveTo(l.cx - l.bodyTopHalf, l.bodyTopY)
  ctx.lineTo(l.cx + l.bodyTopHalf, l.bodyTopY)
  ctx.lineTo(l.cx + l.bodyBotHalf, l.bodyBottomY)
  ctx.lineTo(l.cx - l.bodyBotHalf, l.bodyBottomY)
  ctx.closePath()
  ctx.fill()

  // Rounded closed crown at the bottom.
  ellipse(bodyDark, l.cx - l.bodyBotHalf, l.bodyBottomY - l.brimH * 0.45, l.bodyBotHalf * 2, l.brimH * 0.9)

  // Hat band near the mouth (width follows the body taper).
  const bandF = (l.bandY - l.bodyTopY) / (l.bodyBottomY - l.bodyTopY)
  const bandHalf = l.bodyTopHalf + (l.bodyBotHalf - l.bodyTopHalf) * bandF
  ctx.fillStyle = band
  ctx.fillRect(l.cx - bandHalf, l.bandY, bandHalf * 2, l.bandH)

  // The mouth (the hole the shapes come from), on top of everything.
  ellipse(hole, l.cx - l.openingW / 2, l.openingCenter.y - l.openingH / 2, l.openingW, l.openingH)
  // Soft inner-rim highlight for depth.
  ellipse(
    'rgba(255,255,255,0.06)',
    l.cx - l.openingW / 2,
    l.openingCenter.y - l.openingH * 0.62,
    l.openingW,
    l.openingH * 0.6,
  )
}

function drawWand(
  ctx: CanvasRenderingContext2D,
  opening: { x: number; y: number },
  scale: number,
  progress: number,
) {
  // Gentle back-and-forth sweep just above the hole (~1.5 waves).
  const sweep = Math.sin(progress * Math.PI * 3)
  const span = scale * 0.42
  const tip = { x: opening.x + sweep * span, y: opening.y - scale * 0.22 }
  // Handle trails down to the lower-right, toward the unseen magician.
  const handleEnd = { x: tip.x + scale * 0.55, y: tip.y + scale * 0.95 }

  const line = (color: string, width: number) => {
    ctx.strokeStyle = color
    ctx.lineWidth = width
    ctx.beginPath()
    ctx.moveTo(handleEnd.x, handleEnd.y)
    ctx.lineTo(tip.x, tip.y)
    ctx.stroke()
  }
  // Stick (dark core + lighter highlight).
  line('#3A220E', scale * 0.05)
  line('#6B4A24', scale * 0.022)

  // Glowing star tip.
  const tipR = scale * 0.12
  const glow = (alpha: number, r: number) => {
    ctx.fillStyle = `rgba(255,243,176,${alpha})`
    ctx.beginPath()
    ctx.arc(tip.x, tip.y, r, 0, Math.PI * 2)
    ctx.fill()
  }
  glow(0.3, tipR * 2.2)
  glow(0.45, tipR * 1.4)
  drawStarTip(ctx, tip, tipR)

  // Sparkles drifting down out of the mouth.
  const seeds = [-0.55, -0.2, 0.1, 0.4, 0.0]
  seeds.forEach((fx, k) => {
    const ph = (progress * 1.5 + k * 0.19) % 1
    const alpha = (1 - ph) * (1 - Math.abs(sweep) * 0.4)
    const sx = opening.x + fx * scale * 0.4
    const sy = opening.y + ph * scale * 0.5
    ctx.fillStyle = `rgba(255,243,176,${Math.min(Math.max(alpha, 0), 1) * 0.8})`
    ctx.beginPath()
    ctx.arc(sx, sy, scale * 0.02 * (1.2 - ph), 0, Math.PI * 2)
    ctx.fill()
  })
}

function drawStarTip(ctx: CanvasRenderingContext2D, c: { x: number; y: number }, r: number) {
  const inner = r * 0.45
  ctx.beginPath()
  for (let i = 0; i < 10; i++) {
    const rad = i % 2 === 0 ? r : inner
    const a = -Math.PI / 2 + (Math.PI * i) / 5
    const x = c.x + rad * Math.cos(a)
    const y = c.y + rad * Math.sin(a)
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  }
  ctx.closePath()
  ctx.fillStyle = '#FFE066'
  ctx.fill()
  ctx.strokeStyle = '#FFFFFF'
  ctx.lineWidth = r * 0.14
  ctx.stroke()
}
