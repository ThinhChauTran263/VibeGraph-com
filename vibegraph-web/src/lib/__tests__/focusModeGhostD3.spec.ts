/**
 * d3 engine ghost sizing: dimmed nodes keep FULL proportional size (dim by color
 * only) and scale with zoom like the foreground; no max clamp that would shrink
 * graph-unit sizes to dots (user-reported bug 2026-08-19).
 */
import { describe, expect, it } from 'vitest'
import { ghostNodeSize, GHOST_EDGE_PX } from '../focusMode'

describe('ghost sizing in d3 mode (graph-unit sizes)', () => {
  it('keeps full proportional size — no 20-unit clamp, no 0.8 shrink', () => {
    expect(ghostNodeSize(48)).toBe(48)
    expect(ghostNodeSize(168)).toBe(168)
    expect(ghostNodeSize(12)).toBe(12)
  })

  it('stays proportional: bigger original stays bigger', () => {
    expect(ghostNodeSize(168)).toBeGreaterThan(ghostNodeSize(48))
  })

  it('falls back to the min size for non-numeric sizes', () => {
    expect(ghostNodeSize('nope')).toBe(2)
    expect(ghostNodeSize(undefined)).toBe(2)
  })

  it('ghost edges use a constant screen-px hairline', () => {
    expect(GHOST_EDGE_PX).toBe(0.8)
  })
})
