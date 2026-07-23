import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, it, expect } from 'vitest'

import { useGraphData } from '@/composables/useGraphData'
import { useGraphStore } from '@/stores/graph'
import type { GraphData, GraphNode } from '@/types/graph'

function node(id: string): GraphNode {
  return {
    id,
    type: 'Method',
    name: id,
    fullName: id,
    filePath: `${id}.java`,
    lineNumber: 1,
    properties: {},
  }
}

function graph(count: number): GraphData {
  return {
    nodes: Array.from({ length: count }, (_, i) => node(`n${i}`)),
    edges: [],
    nodeStats: {} as GraphData['nodeStats'],
    edgeStats: {} as GraphData['edgeStats'],
  }
}

describe('useGraphData - Safe Mode render info', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('merges backend payload meta into renderInfo totals (backend truncation surfaced)', () => {
    const store = useGraphStore()
    store.payloadMeta = {
      truncated: true,
      totalNodes: 5000,
      totalEdges: 12000,
      returnedNodes: 1500,
      returnedEdges: 4000,
      nodeLimit: 1500,
      edgeLimit: 4000,
      reason: 'GRAPH_TOO_LARGE',
    }
    const { buildGraph } = useGraphData()

    // Backend already capped to 100 nodes (under the frontend limit) -> frontend keeps all 100,
    // but the banner must still report the FULL backend totals and truncated = true.
    buildGraph(graph(100))

    expect(store.renderInfo?.truncated).toBe(true)
    expect(store.renderInfo?.renderedNodes).toBe(100)
    expect(store.renderInfo?.totalNodes).toBe(5000)
    expect(store.renderInfo?.totalEdges).toBe(12000)
  })

  it('keeps the full filtered graph when the frontend cap is disabled by default', () => {
    const store = useGraphStore()
    store.payloadMeta = null
    const { buildGraph } = useGraphData()

    buildGraph(graph(2000))

    expect(store.renderInfo?.truncated).toBe(false)
    expect(store.renderInfo?.renderedNodes).toBe(2000)
    expect(store.renderInfo?.totalNodes).toBe(2000)
  })

  it('reports no truncation when both layers are under their limits', () => {
    const store = useGraphStore()
    store.payloadMeta = null
    const { buildGraph } = useGraphData()

    buildGraph(graph(50))

    expect(store.renderInfo?.truncated).toBe(false)
    expect(store.renderInfo?.renderedNodes).toBe(50)
    expect(store.renderInfo?.totalNodes).toBe(50)
  })
})
