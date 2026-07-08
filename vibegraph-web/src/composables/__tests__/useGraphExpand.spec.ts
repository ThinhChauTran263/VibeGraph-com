import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, it, expect, vi } from 'vitest'

import type { NodeDetailResponse } from '@/lib/api'
import type { GraphData } from '@/types/graph'
import { EXPAND_MAX_NEIGHBORS } from '@/lib/neighborsAdapter'

const getNeighbors = vi.fn<(p: string, n: string, h: number) => Promise<NodeDetailResponse>>()
vi.mock('@/lib/api', () => ({ graphApi: { getNeighbors: (...a: [string, string, number]) => getNeighbors(...a) } }))

const { useGraphExpand } = await import('@/composables/useGraphExpand')
const { useGraphStore } = await import('@/stores/graph')

function seedGraph(): GraphData {
  return {
    nodes: [{ id: 'Center', type: 'Class', name: 'Center', fullName: 'Center', filePath: 'C.java', lineNumber: 1, properties: {} }],
    edges: [],
    nodeStats: { Class: 1 } as GraphData['nodeStats'],
    edgeStats: {} as GraphData['edgeStats'],
  }
}

function detail(): NodeDetailResponse {
  return {
    node: { id: 'Center', type: 'Class', name: 'Center', fullName: 'Center', filePath: 'C.java', lineNumber: 1 },
    incoming: [
      { otherNode: { id: 'Caller', type: 'Class', name: 'Caller', fullName: 'Caller', filePath: 'X.java', lineNumber: 2 }, relationshipType: 'CALLS', direction: 'INCOMING' },
    ],
    outgoing: [
      { otherNode: { id: 'Dep', type: 'Interface', name: 'Dep', fullName: 'Dep', filePath: 'D.java', lineNumber: 4 }, relationshipType: 'INJECTS', direction: 'OUTGOING' },
    ],
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  getNeighbors.mockReset()
})

describe('useGraphExpand', () => {
  it('merges fetched neighbors into the store and returns the new node count', async () => {
    const store = useGraphStore()
    store.graphData = seedGraph()
    getNeighbors.mockResolvedValue(detail())

    const { expandNode } = useGraphExpand()
    const added = await expandNode('p1', 'Center', 1)

    expect(added).toBe(2)
    expect(store.graphData.nodes.map((n) => n.id).sort()).toEqual(['Caller', 'Center', 'Dep'])
    expect(store.graphData.edges.map((e) => e.id).sort()).toEqual(['Caller|CALLS|Center', 'Center|INJECTS|Dep'])
  })

  it('ignores a response that resolves after the project was reset (stale guard)', async () => {
    const store = useGraphStore()
    store.graphData = seedGraph()
    let resolveFn: (d: NodeDetailResponse) => void = () => {}
    getNeighbors.mockReturnValue(new Promise<NodeDetailResponse>((r) => (resolveFn = r)))

    const { expandNode, reset } = useGraphExpand()
    const pending = expandNode('p1', 'Center', 1)
    reset() // project switched away before the response arrives
    resolveFn(detail())
    const added = await pending

    expect(added).toBe(0)
    expect(store.graphData.nodes.map((n) => n.id)).toEqual(['Center'])
  })

  it('returns 0 and records an error when the request fails', async () => {
    const store = useGraphStore()
    store.graphData = seedGraph()
    getNeighbors.mockRejectedValue(new Error('boom'))

    const { expandNode, lastError } = useGraphExpand()
    const added = await expandNode('p1', 'Center', 1)

    expect(added).toBe(0)
    expect(lastError.value).toContain('boom')
    expect(store.graphData.nodes.map((n) => n.id)).toEqual(['Center'])
  })

  it('no-ops on blank projectId or nodeId', async () => {
    useGraphStore().graphData = seedGraph()
    const { expandNode } = useGraphExpand()
    expect(await expandNode('', 'Center', 1)).toBe(0)
    expect(await expandNode('p1', '', 1)).toBe(0)
    expect(getNeighbors).not.toHaveBeenCalled()
  })

  it('flags lastTruncated when a hub node exceeds the neighbor cap', async () => {
    const store = useGraphStore()
    store.graphData = seedGraph()
    getNeighbors.mockResolvedValue({
      node: { id: 'Center', type: 'Class', name: 'Center', fullName: 'Center', filePath: 'C.java', lineNumber: 1 },
      incoming: [],
      outgoing: Array.from({ length: EXPAND_MAX_NEIGHBORS + 10 }, (_, i) => ({
        otherNode: { id: `n${String(i).padStart(4, '0')}`, type: 'Class', name: `n${i}`, fullName: `n${i}`, filePath: 'x.java', lineNumber: 1 },
        relationshipType: 'CALLS',
        direction: 'OUTGOING',
      })),
    })

    const { expandNode, lastTruncated } = useGraphExpand()
    const added = await expandNode('p1', 'Center', 1)

    expect(lastTruncated.value).toBe(true)
    // center already present; exactly EXPAND_MAX_NEIGHBORS new neighbors merged.
    expect(added).toBe(EXPAND_MAX_NEIGHBORS)
  })
})
