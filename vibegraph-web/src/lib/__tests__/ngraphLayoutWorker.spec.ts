/**
 * Protocol tests for the ngraph macro-layout worker module. The worker scope
 * (`self`) is stubbed before the module is imported so its onmessage handler
 * and postMessage output can be driven synchronously under fake timers.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

interface PostedMessage {
  type: string
  xs?: number[]
  ys?: number[]
  tick?: number
}

const posted: PostedMessage[] = []
const selfStub = {
  onmessage: null as null | ((e: { data: unknown }) => void),
  postMessage: (msg: PostedMessage) => {
    posted.push(msg)
  },
}

vi.stubGlobal('self', selfStub)

const SETTINGS = {
  timeStep: 0.08,
  springLength: 150,
  springCoefficient: 0.0008,
  dragCoefficient: 0.02,
  gravity: -1.2,
  theta: 0.8,
}

function initMessage() {
  return {
    type: 'init',
    ids: ['a', 'b', 'c'],
    xs: [0, 50, -50],
    ys: [0, 10, -10],
    sizes: [5, 5, 5],
    edges: [
      { from: 'a', to: 'b' },
      { from: 'a', to: 'c' },
    ],
    gapPx: 3,
    padFactor: 1.5,
    viewportWidth: 1000,
    viewportHeight: 600,
    settings: SETTINGS,
  }
}

describe('ngraphLayoutWorker (headless macro-layout)', () => {
  beforeEach(async () => {
    posted.length = 0
    vi.useFakeTimers()
    await import('../layout/ngraphLayoutWorker')
  })

  afterEach(() => {
    selfStub.onmessage?.({ data: { type: 'stop' } })
    vi.useRealTimers()
  })

  it('posts an initial positions batch immediately on init', () => {
    selfStub.onmessage?.({ data: initMessage() })

    expect(posted).toHaveLength(1)
    const first = posted[0]!
    expect(first.type).toBe('positions')
    expect(first.xs).toHaveLength(3)
    expect(first.ys).toHaveLength(3)
    for (const v of [...(first.xs ?? []), ...(first.ys ?? [])]) {
      expect(Number.isFinite(v)).toBe(true)
    }
    // Seeded positions survive as the starting point (near the seeds).
    expect(first.xs![0]).toBeCloseTo(0, 0)
  })

  it('streams progressive batches every 10 ticks and moves nodes', () => {
    selfStub.onmessage?.({ data: initMessage() })
    posted.length = 0

    // 2 ticks per 16 ms loop → 5 loops = 10 ticks = one batch.
    vi.advanceTimersByTime(16 * 5)
    expect(posted).toHaveLength(1)
    vi.advanceTimersByTime(16 * 5)
    expect(posted).toHaveLength(2)

    const batch = posted[1]!
    expect(batch.tick).toBe(20)
    // Repulsion must have separated the initially-close nodes.
    const [xa, xb] = [batch.xs![0]!, batch.xs![1]!]
    expect(Math.abs(xa - xb)).toBeGreaterThan(0)
  })

  it('stop halts the loop and acknowledges with stopped', () => {
    selfStub.onmessage?.({ data: initMessage() })
    posted.length = 0

    selfStub.onmessage?.({ data: { type: 'stop' } })
    expect(posted).toEqual([{ type: 'stopped' }])

    vi.advanceTimersByTime(16 * 50)
    // No further position batches after stop.
    expect(posted).toEqual([{ type: 'stopped' }])
  })

  it('re-init replaces the previous layout without leaking loops', () => {
    selfStub.onmessage?.({ data: initMessage() })
    selfStub.onmessage?.({ data: initMessage() })
    posted.length = 0

    vi.advanceTimersByTime(16 * 10)
    // Only ONE active loop: exactly two batches (tick 10 and 20), not four.
    expect(posted).toHaveLength(2)
  })
})
