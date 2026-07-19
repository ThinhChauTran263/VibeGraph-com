import { describe, expect, it } from 'vitest'
import { applyGraphUpdate, parseGraphUpdateEvent } from '../graphPatch'
import type { GraphData, GraphEdge, GraphNode } from '@/types/graph'

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

function edge(
  id: string,
  source: string,
  target: string,
  overrides: Partial<GraphEdge> = {},
): GraphEdge {
  return { id, source, target, type: 'CALLS', ...overrides }
}

function baseGraph(): GraphData {
  return {
    nodes: [node('A'), node('B')],
    edges: [edge('e1', 'A', 'B')],
    nodeStats: { Class: 2 } as GraphData['nodeStats'],
    edgeStats: { CALLS: 1 } as GraphData['edgeStats'],
  }
}

describe('parseGraphUpdateEvent', () => {
  it('rejects non-objects and missing projectId', () => {
    expect(parseGraphUpdateEvent(null)).toBeNull()
    expect(parseGraphUpdateEvent('nope')).toBeNull()
    expect(parseGraphUpdateEvent({ type: 'FULL_UPDATE' })).toBeNull()
  })

  it('rejects an unknown event type', () => {
    expect(parseGraphUpdateEvent({ type: 'PATCH', projectId: 'p1' })).toBeNull()
  })

  it('accepts a well-formed FULL_UPDATE', () => {
    const event = parseGraphUpdateEvent({
      type: 'FULL_UPDATE',
      projectId: 'p1',
      graph: { nodes: [node('A')], edges: [], nodeStats: {}, edgeStats: {} },
    })
    expect(event).not.toBeNull()
    expect(event?.type).toBe('FULL_UPDATE')
  })

  it('rejects a FULL_UPDATE with a malformed graph', () => {
    expect(
      parseGraphUpdateEvent({
        type: 'FULL_UPDATE',
        projectId: 'p1',
        graph: { nodes: 'x', edges: [] },
      }),
    ).toBeNull()
    // node missing id
    expect(
      parseGraphUpdateEvent({
        type: 'FULL_UPDATE',
        projectId: 'p1',
        graph: { nodes: [{ name: 'no-id' }], edges: [] },
      }),
    ).toBeNull()
  })

  it('accepts a well-formed INCREMENTAL and rejects malformed buckets', () => {
    expect(
      parseGraphUpdateEvent({
        type: 'INCREMENTAL',
        projectId: 'p1',
        added: { nodes: [node('C')] },
        removed: { nodeIds: ['A'] },
      }),
    ).not.toBeNull()

    expect(
      parseGraphUpdateEvent({
        type: 'INCREMENTAL',
        projectId: 'p1',
        added: { nodes: [{ name: 'x' }] },
      }),
    ).toBeNull()
    expect(
      parseGraphUpdateEvent({ type: 'INCREMENTAL', projectId: 'p1', removed: { nodeIds: [42] } }),
    ).toBeNull()
  })
})

describe('applyGraphUpdate - FULL_UPDATE', () => {
  it('replaces the whole graph and recomputes stats', () => {
    const current = baseGraph()
    const next = applyGraphUpdate(current, {
      type: 'FULL_UPDATE',
      projectId: 'p1',
      graph: {
        nodes: [node('X', { type: 'Interface' }), node('Y', { type: 'Interface' })],
        edges: [edge('e9', 'X', 'Y', { type: 'IMPLEMENTS' })],
        nodeStats: {} as GraphData['nodeStats'],
        edgeStats: {} as GraphData['edgeStats'],
      },
    })

    expect(next.nodes.map((n) => n.id)).toEqual(['X', 'Y'])
    expect(next.edges.map((e) => e.id)).toEqual(['e9'])
    expect(next.nodeStats).toEqual({ Interface: 2 })
    expect(next.edgeStats).toEqual({ IMPLEMENTS: 1 })
  })

  it('does not mutate the input graph', () => {
    const current = baseGraph()
    const snapshotIds = current.nodes.map((n) => n.id)
    applyGraphUpdate(current, {
      type: 'FULL_UPDATE',
      projectId: 'p1',
      graph: {
        nodes: [node('Z')],
        edges: [],
        nodeStats: {} as GraphData['nodeStats'],
        edgeStats: {} as GraphData['edgeStats'],
      },
    })
    expect(current.nodes.map((n) => n.id)).toEqual(snapshotIds)
  })
})

describe('applyGraphUpdate - INCREMENTAL', () => {
  it('adds new nodes and edges', () => {
    const next = applyGraphUpdate(baseGraph(), {
      type: 'INCREMENTAL',
      projectId: 'p1',
      added: { nodes: [node('C')], edges: [edge('e2', 'B', 'C')] },
    })
    expect(next.nodes.map((n) => n.id)).toEqual(['A', 'B', 'C'])
    expect(next.edges.map((e) => e.id)).toEqual(['e1', 'e2'])
    expect(next.nodeStats).toEqual({ Class: 3 })
    expect(next.edgeStats).toEqual({ CALLS: 2 })
  })

  it('modifies (replaces) existing nodes by id', () => {
    const next = applyGraphUpdate(baseGraph(), {
      type: 'INCREMENTAL',
      projectId: 'p1',
      modified: { nodes: [node('A', { name: 'A-renamed', type: 'Interface' })] },
    })
    const a = next.nodes.find((n) => n.id === 'A')
    expect(a?.name).toBe('A-renamed')
    expect(a?.type).toBe('Interface')
    expect(next.nodes).toHaveLength(2)
    expect(next.nodeStats).toEqual({ Class: 1, Interface: 1 })
  })

  it('removes nodes and drops edges that dangle afterwards', () => {
    const next = applyGraphUpdate(baseGraph(), {
      type: 'INCREMENTAL',
      projectId: 'p1',
      removed: { nodeIds: ['B'] },
    })
    expect(next.nodes.map((n) => n.id)).toEqual(['A'])
    // e1 (A->B) dangles after B is removed and must be dropped.
    expect(next.edges).toHaveLength(0)
    expect(next.edgeStats).toEqual({})
  })

  it('removes edges by id without touching nodes', () => {
    const next = applyGraphUpdate(baseGraph(), {
      type: 'INCREMENTAL',
      projectId: 'p1',
      removed: { edgeIds: ['e1'] },
    })
    expect(next.nodes.map((n) => n.id)).toEqual(['A', 'B'])
    expect(next.edges).toHaveLength(0)
  })

  it('applies remove → add → modify together', () => {
    const next = applyGraphUpdate(baseGraph(), {
      type: 'INCREMENTAL',
      projectId: 'p1',
      removed: { nodeIds: ['A'] },
      added: { nodes: [node('C')], edges: [edge('e2', 'B', 'C')] },
      modified: { nodes: [node('B', { name: 'B2' })] },
    })
    expect(next.nodes.map((n) => n.id)).toEqual(['B', 'C'])
    expect(next.nodes.find((n) => n.id === 'B')?.name).toBe('B2')
    expect(next.edges.map((e) => e.id)).toEqual(['e2'])
  })

  it('does not mutate the input arrays', () => {
    const current = baseGraph()
    const beforeNodes = current.nodes
    const beforeEdges = current.edges
    applyGraphUpdate(current, {
      type: 'INCREMENTAL',
      projectId: 'p1',
      added: { nodes: [node('C')] },
    })
    expect(current.nodes).toBe(beforeNodes)
    expect(current.nodes).toHaveLength(2)
    expect(current.edges).toBe(beforeEdges)
  })
})
