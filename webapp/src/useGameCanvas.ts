import { RefObject, useEffect, useRef } from 'react'

/**
 * Runs a resize-aware requestAnimationFrame loop over a full-stage canvas.
 * [draw] is called every frame with a DPR-corrected context, the stage size in
 * CSS pixels and the elapsed time since the previous frame — the web analogue
 * of the Android games' `BoxWithConstraints` + `Canvas` stage.
 */
export function useGameCanvas(
  canvasRef: RefObject<HTMLCanvasElement>,
  draw: (ctx: CanvasRenderingContext2D, w: number, h: number, dtMs: number) => void,
) {
  const drawRef = useRef(draw)
  drawRef.current = draw

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const resize = () => {
      const dpr = window.devicePixelRatio || 1
      const { clientWidth, clientHeight } = canvas
      canvas.width = Math.max(1, Math.round(clientWidth * dpr))
      canvas.height = Math.max(1, Math.round(clientHeight * dpr))
    }
    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(canvas)

    let raf = 0
    let last = performance.now()
    const frame = (now: number) => {
      const dt = now - last
      last = now
      const dpr = window.devicePixelRatio || 1
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, canvas.clientWidth, canvas.clientHeight)
      drawRef.current(ctx, canvas.clientWidth, canvas.clientHeight, dt)
      raf = requestAnimationFrame(frame)
    }
    raf = requestAnimationFrame(frame)

    return () => {
      cancelAnimationFrame(raf)
      observer.disconnect()
    }
  }, [canvasRef])
}
