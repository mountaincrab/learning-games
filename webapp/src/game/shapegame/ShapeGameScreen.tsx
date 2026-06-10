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

    // ---- Stage (backdrop, floor, curtains), hat, outlines, matched fills, wand ----
    const grad = ctx.createLinearGradient(0, 0, 0, h)
    grad.addColorStop(0, '#3A1C5A')
    grad.addColorStop(1, '#1B0E2E')
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, w, h)

    drawBackdrop(ctx, w, h)
    drawFloor(ctx, w, h, layout.cx)
    drawCurtains(ctx, w, h)
    // Strings the waiting tray pieces dangle from.
    state.tray.forEach((piece, i) => {
      if (!piece) return
      const home = layout.trayHomes[i]
      const sx = home.x + layout.pieceSize / 2
      ctx.strokeStyle = 'rgba(255,255,255,0.30)'
      ctx.lineWidth = Math.min(w, h) * 0.004
      ctx.beginPath()
      ctx.moveTo(sx, h * 0.15)
      ctx.lineTo(sx, home.y + layout.pieceSize * 0.12)
      ctx.stroke()
    })
    drawHat(ctx, layout, Math.min(w, h))

    state.slots.forEach((slot, i) => {
      const center = layout.slotCenters[i]
      const s = layout.slotSize
      ctx.save()
      ctx.translate(center.x - s / 2, center.y - s / 2)
      if (state.isFilled(slot.id)) {
        drawFilled(ctx, slot.typeName, s, shapeFillColorCss(slot.typeName))
      } else {
        drawOutline(ctx, slot.typeName, s, 'rgba(227,200,255,0.9)', s * 0.045)
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

/** Fixed star positions (x/y as stage fractions, plus a 0..1 brightness). */
const STAGE_STARS: [number, number, number][] = [
  [0.12, 0.18, 0.9], [0.21, 0.46, 0.5], [0.27, 0.13, 0.7],
  [0.33, 0.58, 0.4], [0.40, 0.20, 0.8], [0.47, 0.42, 0.5],
  [0.52, 0.15, 0.9], [0.58, 0.55, 0.6], [0.63, 0.25, 0.4],
  [0.68, 0.47, 0.8], [0.73, 0.16, 0.6], [0.78, 0.36, 0.5],
  [0.84, 0.55, 0.7], [0.88, 0.22, 0.9], [0.16, 0.64, 0.5],
  [0.60, 0.68, 0.45], [0.30, 0.34, 0.55], [0.75, 0.65, 0.5],
]

function drawBackdrop(ctx: CanvasRenderingContext2D, w: number, h: number) {
  // Shimmering vertical light streaks falling down the backdrop.
  const streaks = [0.16, 0.27, 0.41, 0.55, 0.68, 0.8, 0.88]
  streaks.forEach((fx, i) => {
    const sw = w * (0.008 + 0.005 * (i % 3))
    const grad = ctx.createLinearGradient(0, h * 0.1, 0, h * 0.78)
    grad.addColorStop(0, 'rgba(255,255,255,0)')
    grad.addColorStop(0.5, 'rgba(255,255,255,0.07)')
    grad.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = grad
    ctx.fillRect(w * fx, h * 0.1, sw, h * 0.68)
  })
  // Twinkling stars.
  for (const [fx, fy, s] of STAGE_STARS) {
    ctx.fillStyle = `rgba(239,227,255,${0.12 + 0.28 * s})`
    ctx.beginPath()
    ctx.arc(w * fx, h * fy, w * 0.0035 * (0.6 + s), 0, Math.PI * 2)
    ctx.fill()
  }
}

function drawFloor(ctx: CanvasRenderingContext2D, w: number, h: number, hatCx: number) {
  const top = h * 0.8
  // Wooden stage boards.
  const grad = ctx.createLinearGradient(0, top, 0, h)
  grad.addColorStop(0, '#454D63')
  grad.addColorStop(1, '#2A3040')
  ctx.fillStyle = grad
  ctx.fillRect(0, top, w, h - top)
  // Bright stage edge where the boards meet the backdrop.
  ctx.fillStyle = '#5A6480'
  ctx.fillRect(0, top, w, h * 0.008)
  // Horizontal plank seams.
  ctx.fillStyle = 'rgba(0,0,0,0.18)'
  for (const fy of [0.865, 0.93]) ctx.fillRect(0, h * fy, w, h * 0.004)
  // Staggered vertical board joints (x fraction to plank row).
  const rowTops = [0.8, 0.865, 0.93]
  const rowBots = [0.865, 0.93, 1]
  const joints: [number, number][] = [
    [0.14, 0], [0.46, 0], [0.78, 0], [0.3, 1], [0.62, 1], [0.9, 1], [0.1, 2], [0.38, 2], [0.7, 2],
  ]
  ctx.fillStyle = 'rgba(0,0,0,0.15)'
  for (const [fx, row] of joints) {
    ctx.fillRect(w * fx, h * rowTops[row], w * 0.0025, h * (rowBots[row] - rowTops[row]))
  }
  // Soft spotlight pool under the hat.
  ctx.fillStyle = 'rgba(255,255,255,0.05)'
  ctx.beginPath()
  ctx.ellipse(hatCx, h * 0.92, w * 0.3, h * 0.07, 0, 0, Math.PI * 2)
  ctx.fill()
}

const CURTAIN = '#A62644'
const CURTAIN_DARK = '#7E1B33'
const CURTAIN_GOLD = '#E9C46A'

function drawCurtains(ctx: CanvasRenderingContext2D, w: number, h: number) {
  drawSideDrape(ctx, w, h, false)
  drawSideDrape(ctx, w, h, true)
  drawValance(ctx, w, h)
}

function drawSideDrape(ctx: CanvasRenderingContext2D, w: number, h: number, rightSide: boolean) {
  const x = (f: number) => (rightSide ? w - f * w : f * w)

  // Drape gathered at the waist by a gold tie-back, flaring back out below it.
  ctx.fillStyle = CURTAIN
  ctx.beginPath()
  ctx.moveTo(x(0), 0)
  ctx.lineTo(x(0.115), 0)
  ctx.quadraticCurveTo(x(0.085), h * 0.18, x(0.062), h * 0.34)
  ctx.quadraticCurveTo(x(0.045), h * 0.46, x(0.048), h * 0.54)
  ctx.quadraticCurveTo(x(0.062), h * 0.72, x(0.115), h * 0.86)
  ctx.quadraticCurveTo(x(0.132), h * 0.92, x(0.125), h * 0.97)
  ctx.quadraticCurveTo(x(0.08), h * 1.0, x(0.04), h * 0.975)
  ctx.quadraticCurveTo(x(0.018), h * 0.96, x(0), h * 0.975)
  ctx.closePath()
  ctx.fill()
  // Shadowed fabric folds following the gather.
  ;[0.03, 0.058, 0.088].forEach((f, k) => {
    ctx.strokeStyle = 'rgba(126,27,51,0.5)'
    ctx.lineWidth = w * 0.006
    ctx.beginPath()
    ctx.moveTo(x(f), h * 0.01)
    ctx.quadraticCurveTo(x(f * 0.78), h * 0.3, x(0.047 + k * 0.006), h * 0.53)
    ctx.quadraticCurveTo(x(0.058 + k * 0.014), h * 0.74, x(0.045 + k * 0.03), h * 0.96)
    ctx.stroke()
  })
  // Light catching the outer edge.
  ctx.strokeStyle = 'rgba(194,59,94,0.55)'
  ctx.lineWidth = w * 0.008
  ctx.beginPath()
  ctx.moveTo(x(0.012), h * 0.04)
  ctx.quadraticCurveTo(x(0.016), h * 0.45, x(0.01), h * 0.9)
  ctx.stroke()
  // Gold tie-back band at the waist.
  const tieL = Math.min(x(0.014), x(0.082))
  ctx.fillStyle = CURTAIN_GOLD
  ctx.beginPath()
  ctx.roundRect(tieL, h * 0.5, w * 0.068, h * 0.055, w * 0.012)
  ctx.fill()
  ctx.strokeStyle = '#B98A2F'
  ctx.lineWidth = w * 0.0025
  ctx.stroke()
}

function drawValance(ctx: CanvasRenderingContext2D, w: number, h: number) {
  const vh = h * 0.105
  const scallops = 9
  const sw = w / scallops
  const dip = h * 0.055
  // Scalloped pelmet across the top.
  ctx.fillStyle = CURTAIN
  ctx.beginPath()
  ctx.moveTo(0, 0)
  ctx.lineTo(w, 0)
  ctx.lineTo(w, vh)
  for (let i = scallops - 1; i >= 0; i--) {
    ctx.quadraticCurveTo(sw * (i + 0.5), vh + dip, sw * i, vh)
  }
  ctx.closePath()
  ctx.fill()
  // Darker gathers where the scallops meet.
  ctx.fillStyle = 'rgba(126,27,51,0.5)'
  for (let i = 1; i < scallops; i++) {
    const cx = sw * i
    ctx.beginPath()
    ctx.moveTo(cx - sw * 0.16, 0)
    ctx.lineTo(cx + sw * 0.16, 0)
    ctx.lineTo(cx, vh)
    ctx.closePath()
    ctx.fill()
  }
  // Gold trim tracing the scallops.
  ctx.strokeStyle = CURTAIN_GOLD
  ctx.lineWidth = h * 0.009
  ctx.beginPath()
  ctx.moveTo(0, vh)
  for (let i = 0; i < scallops; i++) {
    ctx.quadraticCurveTo(sw * (i + 0.5), vh + dip, sw * (i + 1), vh)
  }
  ctx.stroke()
  // Dark rail along the very top.
  ctx.fillStyle = CURTAIN_DARK
  ctx.fillRect(0, 0, w, h * 0.018)
}

function drawHat(ctx: CanvasRenderingContext2D, l: HatLayout, m: number) {
  const bodyLight = '#9355BE'
  const body = '#7B3FA0'
  const bodyDark = '#5E2C7E'
  const outline = '#3F1A57'
  const hole = '#14081F'
  const strokeW = m * 0.006

  const ellipse = (color: string, x: number, y: number, ew: number, eh: number) => {
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.ellipse(x + ew / 2, y + eh / 2, ew / 2, eh / 2, 0, 0, Math.PI * 2)
    ctx.fill()
  }

  // Teal pedestal the up-turned crown balances on (a squat cylinder).
  const pedRx = l.bodyBotHalf * 1.5
  const pedRy = l.brimH * 0.42
  const pedTopCy = l.bodyBottomY + l.brimH * 0.05
  const pedH = l.brimH * 0.55
  ellipse('#1F8478', l.cx - pedRx, pedTopCy + pedH - pedRy, pedRx * 2, pedRy * 2)
  ctx.fillStyle = '#2BA797'
  ctx.fillRect(l.cx - pedRx, pedTopCy, pedRx * 2, pedH)
  ellipse('#3BC9B8', l.cx - pedRx, pedTopCy - pedRy, pedRx * 2, pedRy * 2)
  ctx.strokeStyle = '#1B7C70'
  ctx.lineWidth = strokeW * 0.8
  ctx.beginPath()
  ctx.ellipse(l.cx, pedTopCy, pedRx, pedRy, 0, 0, Math.PI * 2)
  ctx.stroke()

  // Body: gently bowed sides, side-lit from the left.
  const midY = (l.bodyTopY + l.bodyBottomY) / 2
  const ctrlHalf = (l.bodyTopHalf + l.bodyBotHalf) * 0.485
  const bodyPath = new Path2D()
  bodyPath.moveTo(l.cx - l.bodyTopHalf, l.bodyTopY)
  bodyPath.quadraticCurveTo(l.cx - ctrlHalf, midY, l.cx - l.bodyBotHalf, l.bodyBottomY)
  bodyPath.lineTo(l.cx + l.bodyBotHalf, l.bodyBottomY)
  bodyPath.quadraticCurveTo(l.cx + ctrlHalf, midY, l.cx + l.bodyTopHalf, l.bodyTopY)
  bodyPath.closePath()
  const bodyGrad = ctx.createLinearGradient(l.cx - l.bodyTopHalf, 0, l.cx + l.bodyTopHalf, 0)
  bodyGrad.addColorStop(0, bodyLight)
  bodyGrad.addColorStop(0.5, body)
  bodyGrad.addColorStop(1, bodyDark)
  ctx.fillStyle = bodyGrad
  ctx.fill(bodyPath)

  // Rounded closed crown at the bottom.
  ellipse(bodyDark, l.cx - l.bodyBotHalf, l.bodyBottomY - l.brimH * 0.45, l.bodyBotHalf * 2, l.brimH * 0.9)
  ctx.strokeStyle = outline
  ctx.lineWidth = strokeW
  ctx.stroke(bodyPath)

  // Soft sheen down the lit side of the felt.
  ctx.strokeStyle = 'rgba(255,255,255,0.10)'
  ctx.lineWidth = l.bodyTopHalf * 0.1
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(l.cx - l.bodyTopHalf * 0.72, l.bodyTopY + l.brimH * 0.9)
  ctx.quadraticCurveTo(l.cx - ctrlHalf * 0.78, midY, l.cx - l.bodyBotHalf * 0.72, l.bodyBottomY - l.brimH * 0.6)
  ctx.stroke()
  ctx.lineCap = 'butt'

  // Brim disc at the top.
  const brimGrad = ctx.createLinearGradient(0, l.bodyTopY - l.brimH / 2, 0, l.bodyTopY + l.brimH / 2)
  brimGrad.addColorStop(0, '#8F4FBC')
  brimGrad.addColorStop(1, bodyDark)
  ctx.fillStyle = brimGrad
  ctx.beginPath()
  ctx.ellipse(l.cx, l.bodyTopY, l.brimW / 2, l.brimH / 2, 0, 0, Math.PI * 2)
  ctx.fill()
  ctx.strokeStyle = outline
  ctx.lineWidth = strokeW
  ctx.stroke()

  // The mouth (the hole the shapes come from), on top of everything.
  ellipse(hole, l.cx - l.openingW / 2, l.openingCenter.y - l.openingH / 2, l.openingW, l.openingH)
  // Soft inner-rim highlight for depth.
  ellipse(
    'rgba(255,255,255,0.08)',
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
  const tip = { x: opening.x + sweep * span, y: opening.y - scale * 0.1 }
  // Handle trails up to the top-right, toward the unseen magician.
  const handleEnd = { x: tip.x + scale * 0.62, y: tip.y - scale * 0.95 }

  const line = (color: string, x1: number, y1: number, x2: number, y2: number) => {
    ctx.strokeStyle = color
    ctx.lineWidth = scale * 0.05
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(x1, y1)
    ctx.lineTo(x2, y2)
    ctx.stroke()
    ctx.lineCap = 'butt'
  }
  // Classic magician's wand: black stick, white tip cap.
  line('#17151D', handleEnd.x, handleEnd.y, tip.x, tip.y)
  line(
    '#F2EFE6',
    tip.x,
    tip.y,
    tip.x + (handleEnd.x - tip.x) * 0.22,
    tip.y + (handleEnd.y - tip.y) * 0.22,
  )

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
