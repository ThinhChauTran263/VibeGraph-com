import { describe, expect, it } from 'vitest'
import { filterGraphData } from '../graphFilters'
import type { GraphData, GraphEdge, GraphNode } from '@/types/graph'

function graphNode(id: string, type: GraphNode['type']): GraphNode {
  return {
    id,
    type,
    name: id,
    fullName: `com.example.${id}`,
    filePath: `${id}.java`,
    lineNumber: 1,
    properties: {},
  }
}

function graphEdge(id: string, source: string, target: string, type: GraphEdge['type']): GraphEdge {
  return { id, source, target, type }
}

const data: GraphData = {
  nodes: [graphNode('OrderService', 'Class'), graphNode('placeOrder', 'Method')],
  edges: [graphEdge('e1', 'OrderService', 'placeOrder', 'HAS_METHOD')],
  nodeStats: { Class: 1, Method: 1 } as GraphData['nodeStats'],
  edgeStats: { HAS_METHOD: 1 } as GraphData['edgeStats'],
}

describe('filterGraphData', () => {
  it('removes nodes with hidden types and drops dangling edges', () => {
    const filtered = filterGraphData(data, {
      hiddenNodeTypes: new Set(['Method']),
      hiddenEdgeTypes: new Set(),
    })

    expect(filtered.nodes.map((node) => node.id)).toEqual(['OrderService'])
    expect(filtered.edges).toEqual([])
    expect(filtered.nodeStats).toEqual({ Class: 1 })
    expect(filtered.edgeStats).toEqual({})
  })

  it('removes hidden edge types while keeping visible nodes', () => {
    const filtered = filterGraphData(data, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(['HAS_METHOD']),
    })

    expect(filtered.nodes).toHaveLength(2)
    expect(filtered.edges).toEqual([])
    expect(filtered.nodeStats).toEqual({ Class: 1, Method: 1 })
  })
})
