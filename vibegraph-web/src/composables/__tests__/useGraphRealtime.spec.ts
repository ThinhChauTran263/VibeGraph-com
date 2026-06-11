import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { useGraphRealtime } from '../useGraphRealtime'
import type { UseWebSocketReturn, WebSocketStatus } from '../useWebSocket'
import type { GraphData, GraphEdge, GraphNode } from '@/types/graph'

/**
 * Mock the Pinia store with a plain reactive object. This keeps the test free
 * of `createPinia()` (which pulls in @vue/devtools-kit and trips Node's
 * experimental localStorage under jsdom) while still letting the composable
 * read and reassign `store.graphData` reactively.
 */
const storeHolder = vi.hoisted(() => ({ store: null as unknown }))

vi.mock('@/stores/graph', async () => {
  const { reactive: makeReactive } = await import('vue')
  const store = makeReactive({
    graphData: { nodes: [], edges: [], nodeStats: {}, edgeStats: {} },
  })
  storeHolder.store = store
  return { useGraphStore: () => store }
})

const { useGraphStore } = await import('@/stores/graph')

function node(id: string, overrides: Partial<GraphNode> = {}): GraphNode {
  return {
    id,
    type: 'Class',
    name: id,
    fullName: `com.example.${id}`,
    filePath: `src/${id}.java`,
    lineNumber: 1,
    properties: {},
    ...overrides,
  }
}

function edge(id: string, source: string, target: string): GraphEdge {
  return { id, source, target, type: 'CALLS' }
}

function baseGraph(): GraphData {
  return {
    nodes: [node('A'), node('B')],
    edges: [edge('e1', 'A', 'B')],
    nodeStats: { Class: 2 } as GraphData['nodeStats'],
    edgeStats: { CALLS: 1 } as GraphData['edgeStats'],
  }
}

/**
 * Fake WebSocket transport. Captures (topic, callback) per subscription and
 * tracks active state so tests can emit messages and assert unsubscribe.
 */
function makeFakeWs() {
  const status = ref<WebSocketStatus>('disconnected')
  const error = ref<string | null>(null)
  const subscriptions: { topic: string; cb: (p: unknown) => void; active: boolean }[] = []

  const ws = {
    status,
    error,
    connect: vi.fn<() => Promise<void>>(async () => {
      status.value = 'connected'
    }),
    disconnect: vi.fn<() => Promise<void>>(async () => {
      status.value = 'disconnected'
    }),
    subscribe: (topic: string, cb: (p: unknown) => void) => {
      const entry = { topic, cb, active: true }
      subscriptions.push(entry)
      return {
        unsubscribe: () => {
          entry.active = false
        },
      }
    },
  } as unknown as UseWebSocketReturn

  return {
    ws,
    subscriptions,
    emit(topic: string, payload: unknown) {
      subscriptions
        .filter((s) => s.topic === topic && s.active)
        .forEach((s) => s.cb(payload))
    },
  }
}

let scope: ReturnType<typeof effectScope> | null = null

function runInScope<T>(fn: () => T): T {
  scope = effectScope()
  return scope.run(fn) as T
}

beforeEach(() => {
  // Reset the shared mocked store to an empty graph before each test.
  useGraphStore().graphData = {
    nodes: [],
    edges: [],
    nodeStats: {} as GraphData['nodeStats'],
    edgeStats: {} as GraphData['edgeStats'],
  }
})

afterEach(() => {
  scope?.stop()
  scope = null
  vi.clearAllMocks()
})

describe('useGraphRealtime - subscription lifecycle', () => {
  it('subscribes to the project updates topic and connects', () => {
    const { ws, subscriptions } = makeFakeWs()
    runInScope(() => useGraphRealtime('p1', { ws }))

    expect(subscriptions).toHaveLength(1)
    expect(subscriptions[0]!.topic).toBe('/topic/projects/p1/updates')
    expect(ws.connect).toHaveBeenCalledTimes(1)
  })

  it('does not subscribe when projectId is empty', () => {
    const { ws, subscriptions } = makeFakeWs()
    runInScope(() => useGraphRealtime(null, { ws }))
    expect(subscriptions).toHaveLength(0)
    expect(ws.connect).not.toHaveBeenCalled()
  })

  it('unsubscribes and disconnects on scope dispose (unmount)', () => {
    const { ws, subscriptions } = makeFakeWs()
    runInScope(() => useGraphRealtime('p1', { ws }))
    expect(subscriptions[0]!.active).toBe(true)

    scope!.stop()

    expect(subscriptions[0]!.active).toBe(false)
    expect(ws.disconnect).toHaveBeenCalledTimes(1)
  })
})

describe('useGraphRealtime - applying updates', () => {
  it('replaces the graph on a FULL_UPDATE', () => {
    const { ws, emit } = makeFakeWs()
    const store = useGraphStore()
    store.graphData = baseGraph()
    runInScope(() => useGraphRealtime('p1', { ws }))

    emit('/topic/projects/p1/updates', {
      type: 'FULL_UPDATE',
      projectId: 'p1',
      graph: { nodes: [node('X')], edges: [], nodeStats: {}, edgeStats: {} },
    })

    expect(store.graphData.nodes.map((n) => n.id)).toEqual(['X'])
    expect(store.graphData.nodeStats).toEqual({ Class: 1 })
  })

  it('patches the graph on an INCREMENTAL event', () => {
    const { ws, emit } = makeFakeWs()
    const store = useGraphStore()
    store.graphData = baseGraph()
    runInScope(() => useGraphRealtime('p1', { ws }))

    emit('/topic/projects/p1/updates', {
      type: 'INCREMENTAL',
      projectId: 'p1',
      added: { nodes: [node('C')], edges: [edge('e2', 'B', 'C')] },
      removed: { nodeIds: ['A'] },
    })

    expect(store.graphData.nodes.map((n) => n.id)).toEqual(['B', 'C'])
    // e1 (A->B) dangles after A removed; e2 remains.
    expect(store.graphData.edges.map((e) => e.id)).toEqual(['e2'])
  })

  it('ignores and reports a malformed payload without touching the store', () => {
    const { ws, emit } = makeFakeWs()
    const store = useGraphStore()
    store.graphData = baseGraph()
    const api = runInScope(() => useGraphRealtime('p1', { ws }))

    emit('/topic/projects/p1/updates', { type: 'NONSENSE', projectId: 'p1' })

    expect(store.graphData.nodes.map((n) => n.id)).toEqual(['A', 'B'])
    expect(api.lastError.value).toContain('malformed')
  })

  it('ignores a stale event whose projectId differs from the watched project', () => {
    const { ws, emit } = makeFakeWs()
    const store = useGraphStore()
    store.graphData = baseGraph()
    runInScope(() => useGraphRealtime('p1', { ws }))

    // Event arrives on p1's topic but is tagged for a different project.
    emit('/topic/projects/p1/updates', {
      type: 'FULL_UPDATE',
      projectId: 'p2',
      graph: { nodes: [node('Z')], edges: [], nodeStats: {}, edgeStats: {} },
    })

    expect(store.graphData.nodes.map((n) => n.id)).toEqual(['A', 'B'])
  })
})

describe('useGraphRealtime - project switch', () => {
  it('resubscribes to the new topic and ignores late events from the old project', async () => {
    const { ws, subscriptions, emit } = makeFakeWs()
    const store = useGraphStore()
    store.graphData = baseGraph()
    const projectId = ref<string | null>('p1')
    runInScope(() => useGraphRealtime(projectId, { ws }))

    expect(subscriptions[0]!.topic).toBe('/topic/projects/p1/updates')

    projectId.value = 'p2'
    await nextTick()

    // Old subscription torn down; new one for p2 created.
    expect(subscriptions[0]!.active).toBe(false)
    const p2sub = subscriptions.find((s) => s.topic === '/topic/projects/p2/updates')
    expect(p2sub?.active).toBe(true)

    // A late event delivered on the old (inactive) p1 topic must not apply.
    emit('/topic/projects/p1/updates', {
      type: 'FULL_UPDATE',
      projectId: 'p1',
      graph: { nodes: [node('OLD')], edges: [], nodeStats: {}, edgeStats: {} },
    })
    expect(store.graphData.nodes.map((n) => n.id)).toEqual(['A', 'B'])

    // The new project's event applies.
    emit('/topic/projects/p2/updates', {
      type: 'FULL_UPDATE',
      projectId: 'p2',
      graph: { nodes: [node('NEW')], edges: [], nodeStats: {}, edgeStats: {} },
    })
    expect(store.graphData.nodes.map((n) => n.id)).toEqual(['NEW'])
  })
})
