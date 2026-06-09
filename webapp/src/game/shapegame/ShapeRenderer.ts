import { ShapeType } from './ShapeType'

/**
 * Builds `Path2D`s for each `ShapeType`, centered within a box of the given size.
 * The same path is used for the empty outline (stroked) and the filled piece.
 * A direct port of the Android `ShapeRenderer`.
 */
export function pathFor(type: ShapeType, w: number, h: number): Path2D {
  const cx = w / 2
  const cy = h / 2
  const r = Math.min(w, h) / 2
  const path = new Path2D()
  switch (type) {
    case ShapeType.CIRCLE:
      path.arc(cx, cy, r, 0, Math.PI * 2)
      break
    case ShapeType.SQUARE: {
      const s = r * 1.6
      path.rect(cx - s / 2, cy - s / 2, s, s)
      break
    }
    case ShapeType.RECTANGLE:
      path.rect(cx - w * 0.42, cy - h * 0.28, w * 0.84, h * 0.56)
      break
    case ShapeType.TRIANGLE:
      polygon(path, cx, cy, r, 3, -90)
      break
    case ShapeType.PENTAGON:
      polygon(path, cx, cy, r, 5, -90)
      break
    case ShapeType.DIAMOND:
      path.moveTo(cx, cy - r)
      path.lineTo(cx + r * 0.8, cy)
      path.lineTo(cx, cy + r)
      path.lineTo(cx - r * 0.8, cy)
      path.closePath()
      break
    case ShapeType.DOME: {
      // Flat-bottomed arch: semicircle top on a short rectangular base.
      const left = cx - r
      const right = cx + r
      const baseTop = cy + r * 0.4
      path.moveTo(left, baseTop)
      path.lineTo(left, cy)
      path.arc(cx, cy, r, Math.PI, Math.PI * 2)
      path.lineTo(right, baseTop)
      path.closePath()
      break
    }
    case ShapeType.STAR:
      star(path, cx, cy, r, r * 0.45, 5, -90)
      break
    case ShapeType.HEXAGON:
      polygon(path, cx, cy, r, 6, 0)
      break
    case ShapeType.OVAL:
      path.ellipse(cx, cy, r, r * 0.68, 0, 0, Math.PI * 2)
      break
    case ShapeType.HEART:
      path.moveTo(cx, cy + r * 0.8)
      path.bezierCurveTo(cx - r * 1.1, cy - r * 0.2, cx - r * 0.5, cy - r * 1.0, cx, cy - r * 0.3)
      path.bezierCurveTo(cx + r * 0.5, cy - r * 1.0, cx + r * 1.1, cy - r * 0.2, cx, cy + r * 0.8)
      path.closePath()
      break
    case ShapeType.CROSS: {
      const a = r * 0.34 // arm half-width
      const b = r * 0.95 // arm half-length
      path.moveTo(cx - a, cy - b)
      path.lineTo(cx + a, cy - b)
      path.lineTo(cx + a, cy - a)
      path.lineTo(cx + b, cy - a)
      path.lineTo(cx + b, cy + a)
      path.lineTo(cx + a, cy + a)
      path.lineTo(cx + a, cy + b)
      path.lineTo(cx - a, cy + b)
      path.lineTo(cx - a, cy + a)
      path.lineTo(cx - b, cy + a)
      path.lineTo(cx - b, cy - a)
      path.lineTo(cx - a, cy - a)
      path.closePath()
      break
    }
  }
  return path
}

function polygon(path: Path2D, cx: number, cy: number, r: number, sides: number, rotationDeg: number) {
  const rot = (rotationDeg * Math.PI) / 180
  for (let i = 0; i < sides; i++) {
    const a = rot + (2 * Math.PI * i) / sides
    const x = cx + r * Math.cos(a)
    const y = cy + r * Math.sin(a)
    if (i === 0) path.moveTo(x, y)
    else path.lineTo(x, y)
  }
  path.closePath()
}

function star(
  path: Path2D,
  cx: number,
  cy: number,
  outer: number,
  inner: number,
  points: number,
  rotationDeg: number,
) {
  const rot = (rotationDeg * Math.PI) / 180
  const steps = points * 2
  for (let i = 0; i < steps; i++) {
    const rad = i % 2 === 0 ? outer : inner
    const a = rot + (Math.PI * i) / points
    const x = cx + rad * Math.cos(a)
    const y = cy + rad * Math.sin(a)
    if (i === 0) path.moveTo(x, y)
    else path.lineTo(x, y)
  }
  path.closePath()
}

/** Draws an empty target outline at the current origin. */
export function drawOutline(
  ctx: CanvasRenderingContext2D,
  type: ShapeType,
  size: number,
  color: string,
  strokeWidth: number,
) {
  ctx.strokeStyle = color
  ctx.lineWidth = strokeWidth
  ctx.stroke(pathFor(type, size, size))
}

/** Draws a solid filled shape (a draggable piece or a matched slot). */
export function drawFilled(ctx: CanvasRenderingContext2D, type: ShapeType, size: number, color: string) {
  const path = pathFor(type, size, size)
  ctx.fillStyle = color
  ctx.fill(path)
  ctx.strokeStyle = 'rgba(0,0,0,0.18)'
  ctx.lineWidth = size * 0.04
  ctx.stroke(path)
}
