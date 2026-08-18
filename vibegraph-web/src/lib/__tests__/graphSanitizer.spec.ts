import { describe, expect, it } from 'vitest'
import { withoutPackageFromEvent, withoutPackageNodes } from '../graphSanitizer'
import type { GraphData, GraphEdge, GraphNode, GraphUpdateEvent } from '@/types/graph'

function node(id: string, type: GraphNode['type']): GraphNode {
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

function edge(id: string, source: string, target: string, type: GraphEdge['type']): GraphEdge {
  return { id, source, target, type }
}

describe('graphSanitizer', () => {
  it('removes Package, Field, and LocalVariable nodes with every incident edge', () => {
    const data: GraphData = {
      nodes: [
        node('package', 'Package'),
        node('class', 'Class'),
        node('method', 'Method'),
        node('field', 'Field'),
        node('local', 'LocalVariable'),
      ],
      edges: [
        edge('class-method', 'class', 'method', 'HAS_METHOD'),
        edge('package-class', 'package', 'class', 'CONTAINS'),
        edge('class-field', 'class', 'field', 'HAS_FIELD'),
        edge('method-local', 'method', 'local', 'WRITES'),
        edge('field-method', 'field', 'method', 'READS'),
      ],
      nodeStats: {} as GraphData['nodeStats'],
      edgeStats: {} as GraphData['edgeStats'],
      meta: {
        truncated: false,
        totalNodes: 5,
        totalEdges: 5,
        returnedNodes: 5,
        returnedEdges: 5,
        nodeLimit: 100,
        edgeLimit: 100,
      },
    }

    const sanitized = withoutPackageNodes(data)

    expect(sanitized.nodes.map((item) => item.id)).toEqual(['class', 'method'])
    expect(sanitized.edges.map((item) => item.id)).toEqual(['class-method'])
    expect(sanitized.nodeStats).toEqual({ Class: 1, Method: 1 })
    expect(sanitized.edgeStats).toEqual({ HAS_METHOD: 1 })
    expect(sanitized.meta).toBe(data.meta)
  })

  it('prevents incremental patches from reintroducing excluded nodes or their edges', () => {
    const event: GraphUpdateEvent = {
      type: 'INCREMENTAL',
      projectId: 'project-1',
      added: {
        nodes: [node('method', 'Method'), node('field', 'Field'), node('local', 'LocalVariable')],
        edges: [
          edge('class-method', 'class', 'method', 'HAS_METHOD'),
          edge('class-field', 'class', 'field', 'HAS_FIELD'),
          edge('method-local', 'method', 'local', 'WRITES'),
        ],
      },
      modified: { nodes: [node('package', 'Package')] },
      removed: { nodeIds: ['already-removed'] },
    }

    const sanitized = withoutPackageFromEvent(event)

    expect(sanitized.type).toBe('INCREMENTAL')
    if (sanitized.type !== 'INCREMENTAL') throw new Error('Expected an incremental event')
    expect(sanitized.added?.nodes?.map((item) => item.id)).toEqual(['method'])
    expect(sanitized.added?.edges?.map((item) => item.id)).toEqual(['class-method'])
    expect(sanitized.modified?.nodes).toEqual([])
    expect(sanitized.removed?.nodeIds).toEqual(
      expect.arrayContaining(['already-removed', 'field', 'local', 'package']),
    )
  })
})
