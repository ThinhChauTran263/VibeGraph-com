/**
 * Unit tests for the ngraph macro-layout client (Layer 1, default engine).
 * The real Web Worker is replaced by a MockWorker so the protocol can be
 * driven synchronously: init payload shape, progressive position writes,
 * onTick repaint hook, and stop/terminate semantics.
 */
import Graph from 'graphology'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createLayoutEngine } from '../layout/layoutClient'
import {
  NGRAPH_SPRING_LENGTH,
  NGRAPH_SPRING_COEFFICIENT,
  NGRAPH_GRAVITY,
  NGRAPH_THETA,
  NGRAPH_TIME_STEP,
  NGRAPH_DRAG_COEFFICIENT,
} from '@/lib/runtimeConfig'

interface MockWorkerInstance {
  url: string | URL
  sent: unknown[]
  terminated: boolean
  onmessage: ((event: { data: unknown }) => void) | null
  postMessage(msg: unknown): void
  terminate(): void
}

const workerState = vi.hoisted(() => ({ instances: [] as MockWorkerInstance[] }))

vi.stubGlobal(
  'Worker',
  class MockWorker {
    url: string | URL
    sent: unknown[] = []
    terminated = false
    onmessage: ((event: { data: unknown }) => void) | null = null
    constructor(url: string | URL) {
      this.url = url
      workerState.instances.push(this as unknown as MockWorkerInstance)
    }
    postMessage(msg: unknown): void {
      this.sent.push(msg)
    }
    terminate(): void {
      this.terminated = true
    }
  },
)

function makeGraph(): Graph {
  const graph = new Graph()
  graph.addNode('a', { x: 0, y: 0, size: 5 })
  graph.addNode('b', { x: 10, y: 0, size: 5 })
  graph.addEdge('a', 'b')
  return graph
}

describe('createLayoutEngine (ngraph worker path)', () => {
  beforeEach(() => {
    workerState.instances.length = 0
  })
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('spawns the ngraph worker and posts a complete init message', () => {
    const graph = makeGraph()
    const engine = createLayoutEngine(graph)
    engine.start()

    expect(workerState.instances).toHaveLength(1)
    const worker = workerState.instances[0]!
    expect(worker.sent).toHaveLength(1)
    const init = worker.sent[0] as {
      type: string
      ids: string[]
      xs: number[]
      ys: number[]
      edges: Array<{ from: string; to: string }>
      settings: Record<string, number>
    }
    expect(init.type).toBe('init')
    expect(init.ids).toEqual(['a', 'b'])
    expect(init.xs).toEqual([0, 10])
    expect(init.ys).toEqual([0, 0])
    expect(init.edges).toEqual([{ from: 'a', to: 'b' }])
    expect(init.settings).toMatchObject({
      timeStep: NGRAPH_TIME_STEP,
      springLength: NGRAPH_SPRING_LENGTH,
      springCoefficient: NGRAPH_SPRING_COEFFICIENT,
      dragCoefficient: NGRAPH_DRAG_COEFFICIENT,
      gravity: NGRAPH_GRAVITY,
      theta: NGRAPH_THETA,
    })

    engine.kill()
  })

  it('writes progressive positions back into the graph and fires onTick', () => {
    const graph = makeGraph()
    const onTick = vi.fn()
    const engine = createLayoutEngine(graph, { onTick })
    engine.start()
    const worker = workerState.instances[0]!

    worker.onmessage?.({ data: { type: 'positions', xs: [1.5, 8.5], ys: [0.5, -0.5], tick: 10 } })

    expect(graph.getNodeAttribute('a', 'x')).toBe(1.5)
    expect(graph.getNodeAttribute('a', 'y')).toBe(0.5)
    expect(graph.getNodeAttribute('b', 'x')).toBe(8.5)
    expect(graph.getNodeAttribute('b', 'y')).toBe(-0.5)
    expect(onTick).toHaveBeenCalledTimes(1)

    engine.kill()
  })

  it('ignores non-position and non-finite messages', () => {
    const graph = makeGraph()
    const onTick = vi.fn()
    const engine = createLayoutEngine(graph, { onTick })
    engine.start()
    const worker = workerState.instances[0]!

    worker.onmessage?.({ data: { type: 'stopped' } })
    worker.onmessage?.({ data: { type: 'positions', xs: [Number.NaN, 2], ys: [0, 0], tick: 20 } })

    // NaN x for 'a' must be dropped; 'b' still updates from the same message.
    expect(graph.getNodeAttribute('a', 'x')).toBe(0)
    expect(graph.getNodeAttribute('b', 'x')).toBe(2)
    expect(onTick).toHaveBeenCalledTimes(1)

    engine.kill()
  })

  it('kill() posts stop and terminates the worker', () => {
    const graph = makeGraph()
    const engine = createLayoutEngine(graph)
    engine.start()
    const worker = workerState.instances[0]!

    engine.kill()

    expect(worker.sent[1]).toEqual({ type: 'stop' })
    expect(worker.terminated).toBe(true)

    // Second kill is a safe no-op.
    engine.kill()
    expect(worker.sent).toHaveLength(2)
  })
})
