import { shapePathCommands } from '../../shared'

const DEG = Math.PI / 180

/**
 * Interprets the shared, renderer-neutral shape geometry (`ShapeGeometry` in the
 * Kotlin `shared` module) into `Path2D`s. The same path is used for the empty
 * outline (stroked) and the filled piece. Shapes are identified by their
 * `ShapeType` name (e.g. `'CIRCLE'`), as exposed by `Slot.typeName`.
 */
export function pathFor(typeName: string, w: number, h: number): Path2D {
  const path = new Path2D()
  for (const cmd of shapePathCommands(typeName, w, h)) {
    switch (cmd.op) {
      case 'move':
        path.moveTo(cmd.a, cmd.b)
        break
      case 'line':
        path.lineTo(cmd.a, cmd.b)
        break
      case 'cubic':
        path.bezierCurveTo(cmd.a, cmd.b, cmd.c, cmd.d, cmd.e, cmd.f)
        break
      case 'ellipse':
        path.ellipse(cmd.a, cmd.b, cmd.c, cmd.d, 0, 0, Math.PI * 2)
        break
      case 'arc':
        path.arc(cmd.a, cmd.b, cmd.c, cmd.d * DEG, (cmd.d + cmd.e) * DEG)
        break
      case 'rect':
        path.rect(cmd.a, cmd.b, cmd.c, cmd.d)
        break
      case 'close':
        path.closePath()
        break
    }
  }
  return path
}

/** Draws an empty target outline at the current origin. */
export function drawOutline(
  ctx: CanvasRenderingContext2D,
  typeName: string,
  size: number,
  color: string,
  strokeWidth: number,
) {
  ctx.strokeStyle = color
  ctx.lineWidth = strokeWidth
  ctx.stroke(pathFor(typeName, size, size))
}

/** Draws a solid filled shape (a draggable piece or a matched slot). */
export function drawFilled(ctx: CanvasRenderingContext2D, typeName: string, size: number, color: string) {
  const path = pathFor(typeName, size, size)
  ctx.fillStyle = color
  ctx.fill(path)
  ctx.strokeStyle = 'rgba(0,0,0,0.18)'
  ctx.lineWidth = size * 0.04
  ctx.stroke(path)
}
