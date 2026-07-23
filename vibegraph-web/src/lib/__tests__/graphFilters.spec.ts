import { describe, expect, it } from 'vitest'
import { defaultHiddenEdgeTypes, defaultHiddenNodeTypes, filterGraphData } from '../graphFilters'
import {
  BASELINE_EDGE_TYPES,
  BASELINE_NODE_TYPES,
  DETAIL_EDGE_TYPES,
  DETAIL_NODE_TYPES,
} from '../constants'
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
  nodes: [graphNode('OrderController', 'Class'), graphNode('placeOrder', 'Method')],
  edges: [graphEdge('e1', 'OrderController', 'placeOrder', 'HAS_METHOD')],
  nodeStats: { Class: 1, Method: 1 } as GraphData['nodeStats'],
  edgeStats: { HAS_METHOD: 1 } as GraphData['edgeStats'],
}

describe('filterGraphData', () => {
  it('removes hidden nodes and drops dangling or isolated render nodes', () => {
    const filtered = filterGraphData(data, {
      hiddenNodeTypes: new Set(['Method']),
      hiddenEdgeTypes: new Set(),
      hideIsolatedNodes: true,
    })

    expect(filtered.nodes).toEqual([])
    expect(filtered.edges).toEqual([])
    expect(filtered.nodeStats).toEqual({})
    expect(filtered.edgeStats).toEqual({})
  })

  it('removes hidden edge types and drops nodes left isolated by that filter', () => {
    const filtered = filterGraphData(data, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(['HAS_METHOD']),
      hideIsolatedNodes: true,
    })

    expect(filtered.nodes).toEqual([])
    expect(filtered.edges).toEqual([])
    expect(filtered.nodeStats).toEqual({})
  })
})

function mixedGraph(): GraphData {
  const nodes: GraphNode[] = [
    graphNode('A', 'Class'),
    graphNode('B', 'Class'),
    graphNode('m', 'Method'),
    graphNode('ep', 'APIEndpoint'),
    graphNode('field', 'Field'),
    graphNode('local', 'LocalVariable'),
  ]
  const edges: GraphEdge[] = [
    graphEdge('s1', 'A', 'm', 'CALLS'),
    graphEdge('s2', 'A', 'B', 'INJECTS'),
    graphEdge('s3', 'A', 'B', 'EXTENDS'),
    graphEdge('s4', 'm', 'B', 'STEP_IN_FLOW'),
    graphEdge('s5', 'ep', 'm', 'HANDLES_ROUTE'),
    graphEdge('d1', 'm', 'B', 'PARAMETER_TYPE'),
    graphEdge('d2', 'm', 'B', 'RETURNS'),
    graphEdge('d3', 'm', 'field', 'READS'),
    graphEdge('d4', 'm', 'field', 'WRITES'),
    graphEdge('d5', 'field', 'A', 'TYPE_OF'),
    graphEdge('d6', 'A', 'm', 'HAS_METHOD'),
    graphEdge('d7', 'A', 'm', 'DEFINES'),
    graphEdge('d8', 'A', 'B', 'IMPORTS'),
  ]
  return {
    nodes,
    edges,
    nodeStats: {
      Class: 2,
      Method: 1,
      APIEndpoint: 1,
      Field: 1,
      LocalVariable: 1,
    } as GraphData['nodeStats'],
    edgeStats: {
      CALLS: 1,
      INJECTS: 1,
      EXTENDS: 1,
      STEP_IN_FLOW: 1,
      HANDLES_ROUTE: 1,
      PARAMETER_TYPE: 1,
      RETURNS: 1,
      READS: 1,
      WRITES: 1,
      TYPE_OF: 1,
      HAS_METHOD: 1,
      DEFINES: 1,
      IMPORTS: 1,
    } as GraphData['edgeStats'],
  }
}

describe('baseline vs detail visibility', () => {
  it('default-hidden edge set is exactly the detail edge types', () => {
    const hidden = defaultHiddenEdgeTypes()
    expect(new Set(hidden)).toEqual(new Set(DETAIL_EDGE_TYPES))
    for (const baseline of BASELINE_EDGE_TYPES) {
      expect(hidden.has(baseline)).toBe(false)
    }
  })

  it('default-hidden node set is exactly the detail node types', () => {
    const hidden = defaultHiddenNodeTypes()
    expect(new Set(hidden)).toEqual(new Set(DETAIL_NODE_TYPES))
    for (const baseline of BASELINE_NODE_TYPES) {
      expect(hidden.has(baseline)).toBe(false)
    }
  })

  it('default view shows only baseline edges and counts match filtered data', () => {
    const filtered = filterGraphData(mixedGraph(), {
      hiddenNodeTypes: defaultHiddenNodeTypes(),
      hiddenEdgeTypes: defaultHiddenEdgeTypes(),
      hideIsolatedNodes: true,
    })

    expect(filtered.nodes.map((n) => n.type).sort()).toEqual([
      'APIEndpoint',
      'Class',
      'Class',
      'Method',
    ])
    expect(filtered.edges.map((e) => e.type).sort()).toEqual([
      'CALLS',
      'EXTENDS',
      'HANDLES_ROUTE',
      'IMPORTS',
      'INJECTS',
      'STEP_IN_FLOW',
    ])
    expect(filtered.edgeStats).toEqual({
      CALLS: 1,
      EXTENDS: 1,
      HANDLES_ROUTE: 1,
      INJECTS: 1,
      IMPORTS: 1,
      STEP_IN_FLOW: 1,
    })
  })

  it('"Show all" reveals every edge type with correct counts', () => {
    const filtered = filterGraphData(mixedGraph(), {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
      hideIsolatedNodes: true,
    })

    expect(filtered.edges).toHaveLength(13)
    expect(filtered.edgeStats).toEqual({
      CALLS: 1,
      INJECTS: 1,
      EXTENDS: 1,
      STEP_IN_FLOW: 1,
      HANDLES_ROUTE: 1,
      PARAMETER_TYPE: 1,
      RETURNS: 1,
      READS: 1,
      WRITES: 1,
      TYPE_OF: 1,
      HAS_METHOD: 1,
      DEFINES: 1,
      IMPORTS: 1,
    })
  })

  it('baseline types are visible by default while detail types start hidden', () => {
    const hiddenEdges = defaultHiddenEdgeTypes()
    const hiddenNodes = defaultHiddenNodeTypes()

    expect(hiddenEdges.has('STEP_IN_FLOW')).toBe(false)
    expect(hiddenEdges.has('IMPORTS')).toBe(false)
    expect(hiddenEdges.has('PARAMETER_TYPE')).toBe(true)
    expect(hiddenNodes.has('File')).toBe(false)
    expect(hiddenNodes.has('Package')).toBe(false)
    expect(hiddenNodes.has('Field')).toBe(true)
    expect(hiddenNodes.has('LocalVariable')).toBe(true)
  })

  it('hides isolated nodes by default but keeps them revealable', () => {
    const isolated: GraphData = {
      nodes: [
        graphNode('Project', 'Project'),
        graphNode('Package', 'Package'),
        graphNode('File', 'File'),
        graphNode('Class', 'Class'),
      ],
      edges: [graphEdge('e1', 'Project', 'Package', 'CONTAINS')],
      nodeStats: { Project: 1, Package: 1, File: 1, Class: 1 } as GraphData['nodeStats'],
      edgeStats: { CONTAINS: 1 } as GraphData['edgeStats'],
    }

    const hidden = filterGraphData(isolated, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
      hideIsolatedNodes: true,
    })

    expect(hidden.nodes.map((node) => node.id)).toEqual(['Project', 'Package'])
    expect(hidden.edges.map((edge) => edge.id)).toEqual(['e1'])

    const revealed = filterGraphData(isolated, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
      hideIsolatedNodes: false,
    })

    expect(revealed.nodes.map((node) => node.id)).toEqual(['Project', 'Package', 'File', 'Class'])
    expect(revealed.edges.map((edge) => edge.id)).toEqual(['e1'])
  })
})
