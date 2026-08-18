/**
 * Kill-switch path: with VITE_LAYOUT_ENGINE=fa2 the client must delegate to the
 * legacy graphology ForceAtlas2 worker (mocked here) with the same settings the
 * old useSigma startLayout used.
 */
import Graph from 'graphology'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createLayoutEngine } from '../layout/layoutClient'

vi.mock('@/lib/runtimeConfig', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/runtimeConfig')>()
  return { ...actual, LAYOUT_ENGINE: 'fa2' as const }
})

const layoutState = vi.hoisted(() => ({ instances: [] as MockFa2Instance[] }))

interface MockFa2Instance {
  graph: unknown
  params: { settings: Record<string, unknown> }
  started: number
  killed: number
}

vi.mock('graphology-layout-forceatlas2/worker', () => {
  class MockFa2 {
    started = 0
    killed = 0
    constructor(
      public graph: unknown,
      public params: { settings: Record<string, unknown> },
    ) {
      layoutState.instances.push(this as unknown as MockFa2Instance)
    }
    start(): void {
      this.started += 1
    }
    kill(): void {
      this.killed += 1
    }
  }
  return { default: MockFa2 }
})

describe('createLayoutEngine (fa2 kill-switch path)', () => {
  beforeEach(() => {
    layoutState.instances.length = 0
  })

  it('uses the ForceAtlas2 worker with the adaptive large/small profile', () => {
    const graph = new Graph()
    graph.addNode('a', { x: 0, y: 0 })
    graph.addNode('b', { x: 1, y: 0 })
    graph.addEdge('a', 'b')

    const engine = createLayoutEngine(graph)
    engine.start()

    expect(layoutState.instances).toHaveLength(1)
    const fa2 = layoutState.instances[0]!
    expect(fa2.started).toBe(1)
    expect(fa2.params.settings).toHaveProperty('gravity')
    expect(fa2.params.settings).toHaveProperty('scalingRatio')
    expect(fa2.params.settings).toHaveProperty('barnesHutOptimize')

    engine.kill()
    expect(fa2.killed).toBe(1)
  })

  it('does not spawn a Worker on the fa2 path', () => {
    const graph = new Graph()
    graph.addNode('a', { x: 0, y: 0 })

    const engine = createLayoutEngine(graph)
    engine.start()
    engine.kill()

    // No Worker global is stubbed in this file; if the client had taken the
    // ngraph path it would have thrown on `new Worker(...)`.
    expect(layoutState.instances).toHaveLength(1)
  })
})
