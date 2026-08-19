/**
 * layoutClient spec — d3 engine path (worker protocol). The fa2 path is covered
 * by useSigma.spec (default engine). Worker is mocked; protocol asserted.
 */
import Graph from 'graphology'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createLayoutEngine, type LayoutPosition } from '../layout/layoutClient'

vi.mock('@/lib/runtimeConfig', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/runtimeConfig')>()
  return { ...actual, LAYOUT_ENGINE: 'd3' as const, LAYOUT_MACRO: 'ngraph' as const }
})

interface MockWorkerInstance {
  sent: unknown[]
  terminated: boolean
  onmessage: ((e: { data: unknown }) => void) | null
  postMessage(msg: unknown): void
  terminate(): void
}

const workerState = vi.hoisted(() => ({ instances: [] as MockWorkerInstance[] }))

vi.stubGlobal(
  'Worker',
  class MockWorker {
    sent: unknown[] = []
    terminated = false
    onmessage: ((e: { data: unknown }) => void) | null = null
    constructor(
      public url: string | URL,
      public options?: { type?: string },
    ) {
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
  graph.addNode('a', { x: 0, y: 0, size: 8 })
  graph.addNode('b', { x: 10, y: 0, size: 5 })
  graph.addEdge('a', 'b')
  return graph
}

describe('layoutClient (d3 engine)', () => {
  beforeEach(() => {
    workerState.instances.length = 0
  })
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('spawns the d3 worker and posts the init protocol (nodes/links/macro)', () => {
    const graph = makeGraph()
    const engine = createLayoutEngine(graph)
    engine.start()

    expect(workerState.instances).toHaveLength(1)
    const worker = workerState.instances[0]!
    expect(worker.sent).toHaveLength(1)
    const init = worker.sent[0] as {
      type: string
      nodes: Array<{ id: string; val: number }>
      links: Array<{ source: string; target: string }>
      macro: string
    }
    expect(init.type).toBe('init')
    expect(init.macro).toBe('ngraph')
    expect(init.nodes.map((n) => n.id).sort()).toEqual(['a', 'b'])
    expect(init.nodes.find((n) => n.id === 'a')?.val).toBe(8)
    expect(init.links).toEqual([{ source: 'a', target: 'b' }])

    engine.kill()
  })

  it('forwards done positions + vals to onDone', () => {
    const graph = makeGraph()
    const onDone = vi.fn()
    const engine = createLayoutEngine(graph, { onDone })
    engine.start()
    const worker = workerState.instances[0]!

    worker.onmessage?.({
      data: { type: 'done', ids: ['a', 'b'], xs: [100, -100], ys: [50, -50] },
    })

    expect(onDone).toHaveBeenCalledTimes(1)
    const [positions, vals] = onDone.mock.calls[0] as unknown as [LayoutPosition[], number[]]
    expect(positions).toEqual([
      { id: 'a', x: 100, y: 50 },
      { id: 'b', x: -100, y: -50 },
    ])
    expect(vals.sort()).toEqual([5, 8])

    engine.kill()
  })

  it('ignores non-done messages and kill() posts stop + terminates', () => {
    const graph = makeGraph()
    const onDone = vi.fn()
    const engine = createLayoutEngine(graph, { onDone })
    engine.start()
    const worker = workerState.instances[0]!

    worker.onmessage?.({ data: { type: 'progress' } })
    expect(onDone).not.toHaveBeenCalled()

    engine.kill()
    expect(worker.sent[1]).toEqual({ type: 'stop' })
    expect(worker.terminated).toBe(true)

    // second kill is a safe no-op
    engine.kill()
    expect(worker.sent).toHaveLength(2)
  })
})
