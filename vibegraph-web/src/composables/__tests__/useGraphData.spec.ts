import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, it, expect, vi } from 'vitest'

import { fetchFullGraph } from '@/lib/api'
import { useGraphData } from '@/composables/useGraphData'
import { useGraphStore } from '@/stores/graph'
import { GRAPH_SAFE_NODE_LIMIT } from '@/lib/graphCap'
import type { GraphData, GraphEdge, GraphNode } from '@/types/graph'

vi.mock('@/lib/api', () => ({
  fetchFullGraph: vi.fn(),
}))

const fetchFullGraphMock = vi.mocked(fetchFullGraph)

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

function edge(id: string, source: string, target: string, type: GraphEdge['type']): GraphEdge {
  return { id, source, target, type }
}

describe('useGraphData - Safe Mode render info', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchFullGraphMock.mockReset()
  })

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

  it('truncates on the frontend by default when the graph exceeds the Safe Mode limit (B-M10)', () => {
    const store = useGraphStore()
    store.payloadMeta = null
    const { buildGraph } = useGraphData()

    buildGraph(graph(GRAPH_SAFE_NODE_LIMIT + 500))

    expect(store.renderInfo?.truncated).toBe(true)
    expect(store.renderInfo?.renderedNodes).toBe(GRAPH_SAFE_NODE_LIMIT)
    expect(store.renderInfo?.totalNodes).toBe(GRAPH_SAFE_NODE_LIMIT + 500)
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

describe('useGraphData - complete initial graph', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchFullGraphMock.mockReset()
  })

  it('loads baseline and deep together while excluding detail nodes and incident edges', async () => {
    const fileA = { ...node('A.java'), type: 'File' as const, filePath: 'A.java' }
    const fileB = { ...node('B.java'), type: 'File' as const, filePath: 'B.java' }
    const classA = { ...node('A'), type: 'Class' as const, filePath: 'A.java' }
    const fieldA = { ...node('A.id'), type: 'Field' as const, filePath: 'A.java' }
    const localA = { ...node('A.local'), type: 'LocalVariable' as const, filePath: 'A.java' }
    const baseline: GraphData = {
      nodes: [fileA, fileB, classA],
      edges: [edge('A.java|IMPORTS|B.java', 'A.java', 'B.java', 'IMPORTS')],
      nodeStats: { File: 2, Class: 1 } as GraphData['nodeStats'],
      edgeStats: { IMPORTS: 1 } as GraphData['edgeStats'],
    }
    const deep: GraphData = {
      nodes: [fileA, fileB, classA, fieldA, localA],
      edges: [
        edge('A|HAS_FIELD|A.id', 'A', 'A.id', 'HAS_FIELD'),
        edge('A|WRITES|A.local', 'A', 'A.local', 'WRITES'),
      ],
      nodeStats: { File: 2, Class: 1, Field: 1, LocalVariable: 1 } as GraphData['nodeStats'],
      edgeStats: { HAS_FIELD: 1, WRITES: 1 } as GraphData['edgeStats'],
    }
    fetchFullGraphMock.mockResolvedValueOnce(baseline).mockResolvedValueOnce(deep)

    const store = useGraphStore()
    const { ensureDeepGraph, loadGraph } = useGraphData()

    await loadGraph('project-1')
    expect(fetchFullGraphMock).toHaveBeenNthCalledWith(1, 'project-1', { mode: 'baseline' })
    expect(fetchFullGraphMock).toHaveBeenNthCalledWith(2, 'project-1', { mode: 'deep' })
    expect(store.payloadMode).toBe('baseline+deep')
    expect(store.graphData.nodeStats.Field).toBeUndefined()
    expect(store.graphData.nodeStats.LocalVariable).toBeUndefined()

    await ensureDeepGraph('project-1')
    expect(fetchFullGraphMock).toHaveBeenCalledTimes(2)
    expect(store.payloadMode).toBe('baseline+deep')
    expect(store.graphData.edgeStats.IMPORTS).toBe(1)
    expect(store.graphData.edgeStats.HAS_FIELD).toBeUndefined()
    expect(store.graphData.edgeStats.WRITES).toBeUndefined()
    expect(store.graphData.edges.map((graphEdge) => graphEdge.id)).toEqual([
      'A.java|IMPORTS|B.java',
    ])
  })
})
