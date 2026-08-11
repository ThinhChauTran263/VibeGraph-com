import { describe, expect, it } from 'vitest'
import { defaultHiddenEdgeTypes, defaultHiddenNodeTypes, filterGraphData } from '../graphFilters'
import { ALL_NODE_TYPES, CPG_LITE_EDGE_TYPES, STRUCTURAL_EDGE_TYPES } from '../constants'
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

  it('keeps a connected Constructor when its endpoint type and edges are hidden', () => {
    const constructorGraph: GraphData = {
      nodes: [graphNode('OrderService', 'Class'), graphNode('OrderService#init', 'Constructor')],
      edges: [graphEdge('constructor-edge', 'OrderService', 'OrderService#init', 'HAS_METHOD')],
      nodeStats: { Class: 1, Constructor: 1 } as GraphData['nodeStats'],
      edgeStats: { HAS_METHOD: 1 } as GraphData['edgeStats'],
    }

    const filtered = filterGraphData(constructorGraph, {
      hiddenNodeTypes: new Set(['Class']),
      hiddenEdgeTypes: new Set(['HAS_METHOD']),
      hideIsolatedNodes: true,
    })

    expect(filtered.nodes.map((node) => node.id)).toEqual(['OrderService#init'])
    expect(filtered.edges).toEqual([])
  })

  it.each(ALL_NODE_TYPES)('can isolate connected %s nodes without removing them', (type) => {
    const companionType = type === 'Project' ? 'Class' : 'Project'
    const isolatedTypeGraph: GraphData = {
      nodes: [graphNode('selected', type), graphNode('companion', companionType)],
      edges: [graphEdge('relation', 'selected', 'companion', 'HAS_RELATION')],
      nodeStats: { [type]: 1, [companionType]: 1 } as GraphData['nodeStats'],
      edgeStats: { HAS_RELATION: 1 } as GraphData['edgeStats'],
    }

    const filtered = filterGraphData(isolatedTypeGraph, {
      hiddenNodeTypes: new Set(ALL_NODE_TYPES.filter((candidate) => candidate !== type)),
      hiddenEdgeTypes: new Set(['HAS_RELATION']),
      hideIsolatedNodes: true,
    })

    expect(filtered.nodes.map((node) => node.id)).toEqual(['selected'])
    expect(filtered.edges).toEqual([])
  })

  it('still removes nodes that have no edge in the source graph', () => {
    const sourceWithOrphan: GraphData = {
      nodes: [...data.nodes, graphNode('orphan', 'Constructor')],
      edges: data.edges,
      nodeStats: { Class: 1, Method: 1, Constructor: 1 } as GraphData['nodeStats'],
      edgeStats: data.edgeStats,
    }

    const filtered = filterGraphData(sourceWithOrphan, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
      hideIsolatedNodes: true,
    })

    expect(filtered.nodes.map((node) => node.id)).toEqual(['OrderService', 'placeOrder'])
  })

  it('never returns an edge without both endpoints in the filtered node set', () => {
    const graphWithDanglingCandidates: GraphData = {
      nodes: [
        graphNode('visible-a', 'Class'),
        graphNode('visible-b', 'Class'),
        graphNode('hidden', 'Method'),
        graphNode('dangling-source', 'Class'),
      ],
      edges: [
        graphEdge('visible', 'visible-a', 'visible-b', 'EXTENDS'),
        graphEdge('hidden-target', 'visible-a', 'hidden', 'CALLS'),
        graphEdge('missing-target', 'visible-a', 'missing', 'CALLS'),
        graphEdge('dangling-source-edge', 'dangling-source', 'missing', 'CALLS'),
      ],
      nodeStats: { Class: 3, Method: 1 } as GraphData['nodeStats'],
      edgeStats: { EXTENDS: 1, CALLS: 3 } as GraphData['edgeStats'],
    }

    const filtered = filterGraphData(graphWithDanglingCandidates, {
      hiddenNodeTypes: new Set(['Method']),
      hiddenEdgeTypes: new Set(),
      hideIsolatedNodes: true,
    })
    const visibleNodeIds = new Set(filtered.nodes.map((node) => node.id))

    expect(filtered.edges.map((edge) => edge.id)).toEqual(['visible'])
    for (const edge of filtered.edges) {
      expect(visibleNodeIds.has(edge.source)).toBe(true)
      expect(visibleNodeIds.has(edge.target)).toBe(true)
    }
  })
})

/**
 * Default exposure policy: the graph loads all available node/edge types, but
 * starts with noisy detail edges disabled. Counts are always computed from the
 * actual filtered data, never hardcoded.
 */
function mixedGraph(): GraphData {
  const nodes: GraphNode[] = [
    graphNode('A', 'Class'),
    graphNode('B', 'Class'),
    graphNode('m', 'Method'),
    graphNode('f', 'Field'),
  ]
  const edges: GraphEdge[] = [
    graphEdge('s1', 'A', 'm', 'HAS_METHOD'), // structural
    graphEdge('s2', 'A', 'B', 'EXTENDS'), // structural
    graphEdge('s3', 'A', 'B', 'IMPORTS'), // structural, visible by default
    graphEdge('c1', 'm', 'B', 'RETURNS'), // CPG-lite
    graphEdge('c2', 'A', 'f', 'HAS_FIELD'), // CPG-lite
    graphEdge('c3', 'A', 'B', 'INJECTS'), // visible semantic edge
  ]
  return {
    nodes,
    edges,
    nodeStats: { Class: 2, Method: 1, Field: 1 } as GraphData['nodeStats'],
    edgeStats: {
      HAS_METHOD: 1,
      EXTENDS: 1,
      IMPORTS: 1,
      RETURNS: 1,
      HAS_FIELD: 1,
      INJECTS: 1,
    } as GraphData['edgeStats'],
  }
}

describe('default vs show-all edge visibility', () => {
  it('default-hidden set matches the curated hidden edge policy', () => {
    const hidden = defaultHiddenEdgeTypes()
    expect(hidden).toEqual(
      new Set([
        'HAS_FIELD',
        'RETURNS',
        'TYPE_OF',
        'PARAMETER_TYPE',
        'THROWS',
        'INSTANTIATES',
        'ANNOTATED_BY',
        'READS',
        'WRITES',
        'CATCHES',
        'PUBLISHES_EVENT',
        'LISTENS_EVENT',
        'TRIGGERS',
        'CALLS_DYNAMIC',
        'DISPATCH_CANDIDATES',
      ]),
    )
  })

  it('returns a fresh set each call so callers can own their copy', () => {
    const first = defaultHiddenEdgeTypes()
    const removed = [...first][0]
    if (removed) first.delete(removed)
    const second = defaultHiddenEdgeTypes()
    expect(second.has('HAS_FIELD')).toBe(true)
  })

  it('default view shows curated semantic edges and counts match filtered data', () => {
    const data = mixedGraph()
    const filtered = filterGraphData(data, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: defaultHiddenEdgeTypes(),
    })

    const types = filtered.edges.map((edge) => edge.type).sort()
    expect(types).toEqual(['EXTENDS', 'HAS_METHOD', 'IMPORTS', 'INJECTS'])
    // edgeStats are recomputed from the actual filtered edges, not hardcoded.
    expect(filtered.edgeStats).toEqual({ HAS_METHOD: 1, EXTENDS: 1, IMPORTS: 1, INJECTS: 1 })
    // A hidden CPG-lite type must not silently disappear from the source data.
    expect(data.edges.some((edge) => edge.type === 'RETURNS')).toBe(true)
  })

  it('"Show all" (empty hidden set) reveals every edge with correct counts', () => {
    const data = mixedGraph()
    const filtered = filterGraphData(data, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
    })

    expect(filtered.edges).toHaveLength(6)
    expect(filtered.edgeStats).toEqual({
      HAS_METHOD: 1,
      EXTENDS: 1,
      IMPORTS: 1,
      RETURNS: 1,
      HAS_FIELD: 1,
      INJECTS: 1,
    })
  })

  it('no backend-emitted type with count > 0 is impossible to reveal', () => {
    const data = mixedGraph()
    // Every hidden-by-default type present in the data can be revealed by clearing the
    // hidden set; none are dropped at ingestion.
    const revealed = filterGraphData(data, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
    })
    const sourceTypes = new Set(data.edges.map((edge) => edge.type))
    const revealedTypes = new Set(revealed.edges.map((edge) => edge.type))
    const presentHidden = [...defaultHiddenEdgeTypes()].filter((type) => sourceTypes.has(type))
    for (const type of presentHidden) {
      expect(revealedTypes.has(type)).toBe(true)
    }
  })
})

/**
 * Phase 3 deep CPG: LocalVariable nodes + READS/WRITES/CATCHES edges stay
 * default-hidden (node type + edge types) so the graph stays
 * readable, and revealed only via "Show all".
 */
describe('deep CPG (READS/WRITES/CATCHES + LocalVariable) default visibility', () => {
  function deepGraph(): GraphData {
    const nodes: GraphNode[] = [
      graphNode('Svc', 'Class'),
      graphNode('run', 'Method'),
      graphNode('total', 'Field'),
      graphNode('local', 'LocalVariable'),
    ]
    const edges: GraphEdge[] = [
      graphEdge('h', 'Svc', 'run', 'HAS_METHOD'), // structural, visible
      graphEdge('w', 'run', 'total', 'WRITES'), // deep CPG, hidden
      graphEdge('r', 'run', 'local', 'READS'), // deep CPG, target hidden node
      graphEdge('c', 'run', 'total', 'CATCHES'), // deep CPG, hidden
    ]
    return {
      nodes,
      edges,
      nodeStats: { Class: 1, Method: 1, Field: 1, LocalVariable: 1 } as GraphData['nodeStats'],
      edgeStats: { HAS_METHOD: 1, WRITES: 1, READS: 1, CATCHES: 1 } as GraphData['edgeStats'],
    }
  }

  it('default-hidden edge set includes READS, WRITES, CATCHES', () => {
    const hidden = defaultHiddenEdgeTypes()
    expect(hidden.has('READS')).toBe(true)
    expect(hidden.has('WRITES')).toBe(true)
    expect(hidden.has('CATCHES')).toBe(true)
  })

  it('default-hidden node set includes LocalVariable', () => {
    expect(defaultHiddenNodeTypes().has('LocalVariable')).toBe(true)
  })

  it('default view hides LocalVariable nodes and all deep-CPG edges', () => {
    const filtered = filterGraphData(deepGraph(), {
      hiddenNodeTypes: defaultHiddenNodeTypes(),
      hiddenEdgeTypes: defaultHiddenEdgeTypes(),
    })
    expect(filtered.nodes.map((n) => n.type)).not.toContain('LocalVariable')
    expect(filtered.edges.map((e) => e.type).sort()).toEqual(['HAS_METHOD'])
    expect(filtered.edgeStats).toEqual({ HAS_METHOD: 1 })
  })

  it('"Show all" (cleared node + edge hidden sets) reveals deep CPG with correct counts', () => {
    const filtered = filterGraphData(deepGraph(), {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
    })
    expect(filtered.nodes.map((n) => n.type)).toContain('LocalVariable')
    expect(filtered.edges).toHaveLength(4)
    expect(filtered.edgeStats).toEqual({ HAS_METHOD: 1, WRITES: 1, READS: 1, CATCHES: 1 })
  })
})

/**
 * Phase 4 STEP_IN_FLOW: inferred execution-flow steps are visible by default
 * because they summarize the execution path better than raw detail edges.
 */
describe('STEP_IN_FLOW default visibility', () => {
  function flowGraph(): GraphData {
    const nodes: GraphNode[] = [graphNode('handle', 'Method'), graphNode('save', 'Method')]
    const edges: GraphEdge[] = [
      graphEdge('call', 'handle', 'save', 'CALLS'), // visible by default
      graphEdge('flow', 'handle', 'save', 'STEP_IN_FLOW'), // default-hidden
    ]
    return {
      nodes,
      edges,
      nodeStats: { Method: 2 } as GraphData['nodeStats'],
      edgeStats: { CALLS: 1, STEP_IN_FLOW: 1 } as GraphData['edgeStats'],
    }
  }

  it('classifies STEP_IN_FLOW as visible-by-default semantic flow', () => {
    expect(CPG_LITE_EDGE_TYPES.has('STEP_IN_FLOW')).toBe(true)
    expect(STRUCTURAL_EDGE_TYPES.has('STEP_IN_FLOW')).toBe(false)
    expect(defaultHiddenEdgeTypes().has('STEP_IN_FLOW')).toBe(false)
  })

  it('default view keeps STEP_IN_FLOW alongside CALLS', () => {
    const filtered = filterGraphData(flowGraph(), {
      hiddenNodeTypes: defaultHiddenNodeTypes(),
      hiddenEdgeTypes: defaultHiddenEdgeTypes(),
    })
    expect(filtered.edges.map((e) => e.type).sort()).toEqual(['CALLS', 'STEP_IN_FLOW'])
  })

  it('"Show all" reveals STEP_IN_FLOW alongside CALLS', () => {
    const filtered = filterGraphData(flowGraph(), {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
    })
    expect(filtered.edges.map((e) => e.type).sort()).toEqual(['CALLS', 'STEP_IN_FLOW'])
    expect(filtered.edgeStats).toEqual({ CALLS: 1, STEP_IN_FLOW: 1 })
  })
})
