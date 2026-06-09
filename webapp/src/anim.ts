/**
 * Tiny spring animation helper, standing in for Compose's `Animatable`.
 * Defaults match Compose's `spring()` (stiffness 1500, critically damped).
 */
export class Spring {
  velocity = 0
  target: number

  constructor(
    public value: number,
    public stiffness = 1500,
    public dampingRatio = 1,
  ) {
    this.target = value
  }

  snapTo(v: number) {
    this.value = v
    this.target = v
    this.velocity = 0
  }

  animateTo(target: number, stiffness = this.stiffness, dampingRatio = this.dampingRatio) {
    this.target = target
    this.stiffness = stiffness
    this.dampingRatio = dampingRatio
  }

  get isResting(): boolean {
    return Math.abs(this.value - this.target) < 0.001 && Math.abs(this.velocity) < 0.001
  }

  /** Advance by [dtMs] using semi-implicit Euler (sub-stepped for stiff springs). */
  step(dtMs: number) {
    if (this.isResting) {
      this.value = this.target
      this.velocity = 0
      return
    }
    let remaining = Math.min(dtMs, 100) / 1000
    const damping = 2 * this.dampingRatio * Math.sqrt(this.stiffness)
    const maxStep = 1 / 240
    while (remaining > 0) {
      const dt = Math.min(remaining, maxStep)
      const accel = -this.stiffness * (this.value - this.target) - damping * this.velocity
      this.velocity += accel * dt
      this.value += this.velocity * dt
      remaining -= dt
    }
  }
}

/** Compose-like spring constants used by the games. */
export const Stiffness = { medium: 1500, veryLow: 50 }
export const DampingRatio = { noBouncy: 1, lowBouncy: 0.75, mediumBouncy: 0.5 }
