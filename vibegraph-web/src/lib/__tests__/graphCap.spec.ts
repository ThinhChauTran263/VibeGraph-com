import { describe, it, expect } from 'vitest'

import { capGraphData, GRAPH_SAFE_NODE_LIMIT } from '../graphCap'
import type { EdgeType, GraphData, GraphEdge, GraphNode, NodeType } from '@/types/graph'

function node(id: string, type: NodeType): GraphNode {
  return {
    id,
    type,
    name: id,
    fullName: id,
    filePath: `${id}.java`,
    lineNumber: 1,
    properties: {},
  }
}

function edge(source: string, target: string, type: EdgeType = 'CALLS'): GraphEdge {
  return { id: `${source}|${type}|${target}`, source, target, type }
}

function graph(nodes: GraphNode[], edges: GraphEdge[] = []): GraphData {
  return {
    nodes,
    edges,
    nodeStats: {} as GraphData['nodeStats'],
    edgeStats: {} as GraphData['edgeStats'],
  }
}

describe('capGraphData', () => {
  it('returns the original data untouched when under the limit', () => {
    const data = graph([node('a', 'Class'), node('b', 'Method')])
    const result = capGraphData(data, 10)

    expect(result.truncated).toBe(false)
    expect(result.data).toBe(data)
    expect(result.renderedNodes).toBe(2)
    expect(result.totalNodes).toBe(2)
  })

  it('caps to the limit and flags truncation', () => {
    const nodes = Array.from({ length: 20 }, (_, i) => node(`n${i}`, 'Method'))
    const result = capGraphData(graph(nodes), 5)

    expect(result.truncated).toBe(true)
    expect(result.renderedNodes).toBe(5)
    expect(result.totalNodes).toBe(20)
    expect(result.data.nodes).toHaveLength(5)
  })

  it('keeps higher-priority node types over lower ones when capping', () => {
    const nodes = [
      node('field1', 'Field'),
      node('class1', 'Class'),
      node('field2', 'Field'),
      node('pkg1', 'Package'),
    ]
    const result = capGraphData(graph(nodes), 2)

    const kept = result.data.nodes.map((n) => n.id)
    // Package (90) and Class (70) outrank Field (20).
    expect(kept).toContain('pkg1')
    expect(kept).toContain('class1')
    expect(kept).not.toContain('field1')
  })

  it('breaks type-priority ties by node degree (more central nodes win)', () => {
    const nodes = [node('hub', 'Method'), node('leaf', 'Method'), node('other', 'Method')]
    // hub has two incident edges, leaf has one, other has none.
    const edges = [edge('hub', 'leaf'), edge('hub', 'other')]
    const result = capGraphData(graph(nodes, edges), 1)

    expect(result.data.nodes.map((n) => n.id)).toEqual(['hub'])
  })

  it('prunes edges whose endpoints did not survive the cap', () => {
    const nodes = [node('class1', 'Class'), node('field1', 'Field')]
    const edges = [edge('class1', 'field1', 'HAS_FIELD')]
    const result = capGraphData(graph(nodes, edges), 1)

    // Only the Class survives; the edge to the dropped Field is pruned.
    expect(result.data.nodes.map((n) => n.id)).toEqual(['class1'])
    expect(result.data.edges).toHaveLength(0)
    expect(result.renderedEdges).toBe(0)
    expect(result.totalEdges).toBe(1)
  })

  it('is deterministic across repeated calls', () => {
    const nodes = Array.from({ length: 30 }, (_, i) => node(`n${i}`, 'Method'))
    const a = capGraphData(graph(nodes), 7)
    const b = capGraphData(graph(nodes), 7)

    expect(a.data.nodes.map((n) => n.id)).toEqual(b.data.nodes.map((n) => n.id))
  })

  it('treats a non-positive limit as no cap', () => {
    const nodes = Array.from({ length: 5 }, (_, i) => node(`n${i}`, 'Method'))
    const result = capGraphData(graph(nodes), 0)

    expect(result.truncated).toBe(false)
    expect(result.renderedNodes).toBe(5)
  })

  it('disables the frontend cap by default', () => {
    expect(GRAPH_SAFE_NODE_LIMIT).toBe(0)
  })
})
