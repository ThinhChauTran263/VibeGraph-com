import { describe, expect, it } from 'vitest'
import { defaultHiddenEdgeTypes, defaultHiddenNodeTypes, filterGraphData } from '../graphFilters'
import { CPG_LITE_EDGE_TYPES, STRUCTURAL_EDGE_TYPES } from '../constants'
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

/**
 * Phase 1 CPG-lite exposure policy: structural edges visible by default, CPG-lite
 * edges hidden by default but revealable via "Show all". Counts are always
 * computed from the actual filtered data, never hardcoded.
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
    graphEdge('c1', 'm', 'B', 'RETURNS'), // CPG-lite
    graphEdge('c2', 'A', 'f', 'HAS_FIELD'), // CPG-lite
    graphEdge('c3', 'A', 'B', 'INJECTS'), // CPG-lite
  ]
  return {
    nodes,
    edges,
    nodeStats: { Class: 2, Method: 1, Field: 1 } as GraphData['nodeStats'],
    edgeStats: { HAS_METHOD: 1, EXTENDS: 1, RETURNS: 1, HAS_FIELD: 1, INJECTS: 1 } as GraphData['edgeStats'],
  }
}

describe('CPG-lite default vs show-all edge visibility', () => {
  it('default-hidden set is exactly the CPG-lite edge types (and excludes structural)', () => {
    const hidden = defaultHiddenEdgeTypes()
    expect(new Set(hidden)).toEqual(new Set(CPG_LITE_EDGE_TYPES))
    for (const structural of STRUCTURAL_EDGE_TYPES) {
      expect(hidden.has(structural)).toBe(false)
    }
  })

  it('returns a fresh set each call so callers can own their copy', () => {
    const first = defaultHiddenEdgeTypes()
    const removed = [...first][0]
    if (removed) first.delete(removed)
    const second = defaultHiddenEdgeTypes()
    expect(second.size).toBe(CPG_LITE_EDGE_TYPES.size)
  })

  it('default view shows only structural edges and counts match filtered data', () => {
    const data = mixedGraph()
    const filtered = filterGraphData(data, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: defaultHiddenEdgeTypes(),
    })

    const types = filtered.edges.map((edge) => edge.type).sort()
    expect(types).toEqual(['EXTENDS', 'HAS_METHOD'])
    // edgeStats are recomputed from the actual filtered edges, not hardcoded.
    expect(filtered.edgeStats).toEqual({ HAS_METHOD: 1, EXTENDS: 1 })
    // A hidden CPG-lite type must not silently disappear from the source data.
    expect(data.edges.some((edge) => edge.type === 'RETURNS')).toBe(true)
  })

  it('"Show all" (empty hidden set) reveals every CPG-lite edge with correct counts', () => {
    const data = mixedGraph()
    const filtered = filterGraphData(data, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
    })

    expect(filtered.edges).toHaveLength(5)
    expect(filtered.edgeStats).toEqual({
      HAS_METHOD: 1,
      EXTENDS: 1,
      RETURNS: 1,
      HAS_FIELD: 1,
      INJECTS: 1,
    })
  })

  it('no backend-emitted type with count > 0 is impossible to reveal', () => {
    const data = mixedGraph()
    // Every CPG-lite type present in the data can be revealed by clearing the
    // hidden set; none are dropped at ingestion.
    const revealed = filterGraphData(data, {
      hiddenNodeTypes: new Set(),
      hiddenEdgeTypes: new Set(),
    })
    const sourceTypes = new Set(data.edges.map((edge) => edge.type))
    const revealedTypes = new Set(revealed.edges.map((edge) => edge.type))
    const presentCpgLite = [...CPG_LITE_EDGE_TYPES].filter((type) => sourceTypes.has(type))
    for (const type of presentCpgLite) {
      expect(revealedTypes.has(type)).toBe(true)
    }
  })
})

/**
 * Phase 3 deep CPG: LocalVariable nodes + READS/WRITES/CATCHES edges are
 * default-hidden (node type + edge types) so the architecture graph stays
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
    // and they are classified CPG-lite, never structural
    for (const type of ['READS', 'WRITES', 'CATCHES'] as const) {
      expect(CPG_LITE_EDGE_TYPES.has(type)).toBe(true)
      expect(STRUCTURAL_EDGE_TYPES.has(type)).toBe(false)
    }
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
 * Phase 4 STEP_IN_FLOW: inferred execution-flow steps are default-hidden (CPG-lite)
 * and revealed via "Show all"; they never show on the default canvas.
 */
describe('STEP_IN_FLOW default visibility', () => {
  function flowGraph(): GraphData {
    const nodes: GraphNode[] = [
      graphNode('handle', 'Method'),
      graphNode('save', 'Method'),
    ]
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

  it('classifies STEP_IN_FLOW as default-hidden CPG-lite, never structural', () => {
    expect(CPG_LITE_EDGE_TYPES.has('STEP_IN_FLOW')).toBe(true)
    expect(STRUCTURAL_EDGE_TYPES.has('STEP_IN_FLOW')).toBe(false)
    expect(defaultHiddenEdgeTypes().has('STEP_IN_FLOW')).toBe(true)
  })

  it('default view hides STEP_IN_FLOW but keeps CALLS', () => {
    const filtered = filterGraphData(flowGraph(), {
      hiddenNodeTypes: defaultHiddenNodeTypes(),
      hiddenEdgeTypes: defaultHiddenEdgeTypes(),
    })
    expect(filtered.edges.map((e) => e.type)).toEqual(['CALLS'])
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
