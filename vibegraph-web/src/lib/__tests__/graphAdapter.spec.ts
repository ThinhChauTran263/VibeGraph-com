import { describe, it, expect } from 'vitest'
import Graph from 'graphology'
import { apiToGraphology, applyDensitySizeScale } from '../graphAdapter'
import {
  NODE_COLORS,
  EDGE_COLORS,
  STRUCTURAL_EDGE_TYPES,
  CPG_LITE_EDGE_TYPES,
  NODE_SIZE_BY_TYPE,
} from '../constants'
import type { EdgeType, GraphData } from '@/types/graph'

/**
 * Edge types the backend parser actually emits (EdgeData.of in the visitors +
 * ParserServiceImpl). The frontend MUST support (color + render) every one of
 * these. OWNS / ANNOTATED_BY exist in the schema enum but are not currently
 * emitted as parser facts, so they are intentionally excluded here.
 */
const BACKEND_EMITTED_EDGE_TYPES: EdgeType[] = [
  'CONTAINS',
  'DEFINES',
  'HAS_METHOD',
  'HAS_FIELD',
  'HAS_INNER',
  'HAS_RELATION',
  'EXTENDS',
  'IMPLEMENTS',
  'OVERRIDES',
  'IMPORTS',
  'TYPE_OF',
  'RETURNS',
  'PARAMETER_TYPE',
  'THROWS',
  'CALLS',
  'INSTANTIATES',
  'INJECTS',
  'HANDLES_ROUTE',
  'READS',
  'WRITES',
  'CATCHES',
  'STEP_IN_FLOW',
  'PUBLISHES_EVENT',
  'LISTENS_EVENT',
  'TRIGGERS',
  'RESOLVES_TO',
  'CALLS_DYNAMIC',
  'DISPATCH_CANDIDATES',
]

function baseData(): GraphData {
  return {
    nodes: [
      {
        id: 'com.example.UserController',
        type: 'Class',
        name: 'UserController',
        fullName: 'com.example.UserController',
        filePath: 'UserController.java',
        lineNumber: 1,
        properties: {},
      },
      {
        id: 'com.example.UserService',
        type: 'Class',
        name: 'UserService',
        fullName: 'com.example.UserService',
        filePath: 'UserService.java',
        lineNumber: 1,
        properties: {},
      },
    ],
    edges: [
      {
        id: 'com.example.UserController|INJECTS|com.example.UserService',
        source: 'com.example.UserController',
        target: 'com.example.UserService',
        type: 'INJECTS',
      },
    ],
    nodeStats: { Class: 2 } as GraphData['nodeStats'],
    edgeStats: { INJECTS: 1 } as GraphData['edgeStats'],
  }
}

describe('apiToGraphology', () => {
  it('keys nodes by stable fullName-based id', () => {
    const graph = apiToGraphology(baseData())
    expect(graph.hasNode('com.example.UserController')).toBe(true)
    expect(graph.hasNode('com.example.UserService')).toBe(true)
    expect(graph.order).toBe(2)
  })

  it('keys edges by deterministic source|type|target id', () => {
    const graph = apiToGraphology(baseData())
    expect(graph.hasEdge('com.example.UserController|INJECTS|com.example.UserService')).toBe(true)
    expect(graph.size).toBe(1)
  })

  it('sets each edge label color to its edge-type color so labels match the legend', () => {
    const graph = apiToGraphology(baseData())
    const key = 'com.example.UserController|INJECTS|com.example.UserService'
    expect(graph.getEdgeAttribute(key, 'color')).toBe(EDGE_COLORS.INJECTS)
    expect(graph.getEdgeAttribute(key, 'labelColor')).toBe(EDGE_COLORS.INJECTS)
  })

  it('skips edges that reference missing nodes', () => {
    const data = baseData()
    data.edges.push({
      id: 'com.example.UserController|CALLS|com.example.Ghost',
      source: 'com.example.UserController',
      target: 'com.example.Ghost',
      type: 'CALLS',
    })
    const graph = apiToGraphology(data)
    expect(graph.size).toBe(1)
  })

  it('collapses every relationship between a pair of nodes to a single edge', () => {
    const data = baseData()
    // The base data already has UserController -> UserService (INJECTS). Adding
    // PARAMETER_TYPE edges on the same pair must NOT create extra lines: two nodes
    // are connected by exactly one edge on the canvas.
    const dup = {
      id: 'com.example.UserController|PARAMETER_TYPE|com.example.UserService',
      source: 'com.example.UserController',
      target: 'com.example.UserService',
      type: 'PARAMETER_TYPE' as const,
    }
    data.edges.push({ ...dup }, { ...dup })
    const graph = apiToGraphology(data)
    // Only one edge for the pair.
    expect(graph.size).toBe(1)
  })

  it('keeps the highest-priority relationship type when a pair has several', () => {
    const data = baseData()
    // Same pair as the base INJECTS edge, plus IMPORTS and EXTENDS. EXTENDS has the
    // highest structural priority, so it defines the single rendered line.
    data.edges.push(
      {
        id: 'com.example.UserController|IMPORTS|com.example.UserService',
        source: 'com.example.UserController',
        target: 'com.example.UserService',
        type: 'IMPORTS' as const,
      },
      {
        id: 'com.example.UserController|EXTENDS|com.example.UserService',
        source: 'com.example.UserController',
        target: 'com.example.UserService',
        type: 'EXTENDS' as const,
      },
    )
    const graph = apiToGraphology(data)
    expect(graph.size).toBe(1)
    const edgeId = graph.edges()[0]
    expect(graph.getEdgeAttribute(edgeId, 'edgeType')).toBe('EXTENDS')
  })

  it('maps extended node types to explicit colors and sizes', () => {
    const data = baseData()
    data.nodes = [
      {
        id: 'file:User.java',
        type: 'File',
        name: 'User.java',
        fullName: 'User.java',
        filePath: 'User.java',
        lineNumber: 1,
        properties: {},
      },
      {
        id: 'com.example.UserRecord',
        type: 'Record',
        name: 'UserRecord',
        fullName: 'com.example.UserRecord',
        filePath: 'UserRecord.java',
        lineNumber: 1,
        properties: {},
      },
      {
        id: 'com.example.UserEntity',
        type: 'DBModel',
        name: 'UserEntity',
        fullName: 'com.example.UserEntity',
        filePath: 'UserEntity.java',
        lineNumber: 1,
        properties: {},
      },
      {
        id: 'com.example.UserService.<init>()',
        type: 'Constructor',
        name: '<init>',
        fullName: 'com.example.UserService.<init>()',
        filePath: 'UserService.java',
        lineNumber: 10,
        properties: {},
      },
      {
        id: 'GET /api/users',
        type: 'APIEndpoint',
        name: 'GET /api/users',
        fullName: 'GET /api/users',
        filePath: '',
        lineNumber: 12,
        properties: {},
      },
    ]
    data.edges = []

    const graph = apiToGraphology(data)

    expect(graph.getNodeAttribute('file:User.java', 'color')).toBe(NODE_COLORS.File)
    expect(graph.getNodeAttribute('file:User.java', 'size')).toBe(NODE_SIZE_BY_TYPE.File)
    expect(graph.getNodeAttribute('com.example.UserRecord', 'color')).toBe(NODE_COLORS.Record)
    expect(graph.getNodeAttribute('com.example.UserRecord', 'size')).toBe(NODE_SIZE_BY_TYPE.Record)
    expect(graph.getNodeAttribute('com.example.UserEntity', 'color')).toBe(NODE_COLORS.DBModel)
    expect(graph.getNodeAttribute('com.example.UserEntity', 'size')).toBe(NODE_SIZE_BY_TYPE.DBModel)
    expect(graph.getNodeAttribute('com.example.UserService.<init>()', 'color')).toBe(
      NODE_COLORS.Constructor,
    )
    expect(graph.getNodeAttribute('com.example.UserService.<init>()', 'size')).toBe(
      NODE_SIZE_BY_TYPE.Constructor,
    )
    expect(graph.getNodeAttribute('GET /api/users', 'color')).toBe(NODE_COLORS.APIEndpoint)
    expect(graph.getNodeAttribute('GET /api/users', 'size')).toBe(NODE_SIZE_BY_TYPE.APIEndpoint)
  })

  it('labels the Project node with its readable name while keying it by the stable id', () => {
    // Regression guard: the root Project node's stable id is an opaque value
    // (e.g. a numeric id), but the canvas label/title must show the readable
    // project/repository name. The Sigma label is driven by node.name; the node
    // key stays node.id so graph identity, edges, and stats are unaffected.
    const data = baseData()
    data.nodes = [
      {
        id: '44786872',
        type: 'Project',
        name: 'ThinhChauTran263/Lab7_Java6',
        fullName: '44786872',
        filePath: '',
        lineNumber: 0,
        properties: { id: '44786872' },
      },
    ]
    data.edges = []

    const graph = apiToGraphology(data)

    expect(graph.hasNode('44786872')).toBe(true)
    expect(graph.order).toBe(1)
    expect(graph.getNodeAttribute('44786872', 'label')).toBe('ThinhChauTran263/Lab7_Java6')
    expect(graph.getNodeAttribute('44786872', 'label')).not.toBe('44786872')
  })
})

describe('graphAdapter supports every backend-emitted edge type', () => {
  it('renders each emitted edge type with its own EDGE_COLORS color', () => {
    // One distinct node pair per edge type so the pair-collapse logic does not
    // merge them — each emitted type must produce a rendered, colored edge.
    const nodes: GraphData['nodes'] = []
    const edges: GraphData['edges'] = []
    BACKEND_EMITTED_EDGE_TYPES.forEach((type, index) => {
      const source = `n${index}a`
      const target = `n${index}b`
      nodes.push(
        {
          id: source,
          type: 'Class',
          name: source,
          fullName: source,
          filePath: '',
          lineNumber: 1,
          properties: {},
        },
        {
          id: target,
          type: 'Class',
          name: target,
          fullName: target,
          filePath: '',
          lineNumber: 1,
          properties: {},
        },
      )
      edges.push({ id: `${source}|${type}|${target}`, source, target, type })
    })

    const graph = apiToGraphology({
      nodes,
      edges,
      nodeStats: {} as GraphData['nodeStats'],
      edgeStats: {} as GraphData['edgeStats'],
    })

    expect(graph.size).toBe(BACKEND_EMITTED_EDGE_TYPES.length)
    for (const type of BACKEND_EMITTED_EDGE_TYPES) {
      const edgeId = graph.edges().find((id) => graph.getEdgeAttribute(id, 'edgeType') === type)
      expect(edgeId).toBeDefined()
      expect(graph.getEdgeAttribute(edgeId as string, 'color')).toBe(EDGE_COLORS[type])
      expect(graph.getEdgeAttribute(edgeId as string, 'labelColor')).toBe(EDGE_COLORS[type])
      // No emitted type may fall through to the generic gray fallback color.
      expect(graph.getEdgeAttribute(edgeId as string, 'color')).not.toBe('#666666')
    }
  })

  it('has a color for every supported (structural + CPG-lite) edge type', () => {
    for (const type of [...STRUCTURAL_EDGE_TYPES, ...CPG_LITE_EDGE_TYPES]) {
      expect(EDGE_COLORS[type]).toBeDefined()
    }
  })

  it('covers every backend-emitted type across the structural and CPG-lite sets', () => {
    const partitioned = new Set<EdgeType>([...STRUCTURAL_EDGE_TYPES, ...CPG_LITE_EDGE_TYPES])
    for (const type of BACKEND_EMITTED_EDGE_TYPES) {
      expect(partitioned.has(type)).toBe(true)
    }
  })
})

describe('applyDensitySizeScale (density-adaptive fit-view sizing)', () => {
  // Code defaults under vitest: coverage 0.3, scale floor 0.25, gap 3 px.
  const VIEW_W = 1000
  const VIEW_H = 600

  function sizedGraph(count: number, size: number): Graph {
    const graph = new Graph()
    for (let i = 0; i < count; i += 1) {
      graph.addNode(`n${i}`, {
        label: `n${i}`,
        x: i % 50,
        y: Math.floor(i / 50),
        size,
        color: '#fff',
        type: 'circle',
        nodeType: 'Class',
        fullName: `n${i}`,
        filePath: '',
        lineNumber: 0,
      })
    }
    return graph
  }

  function demand(graph: Graph, gap: number): number {
    let total = 0
    graph.forEachNode((_id, attrs) => {
      if (attrs.filterHidden === true || attrs.hidden === true) return
      total += Math.PI * ((attrs.size as number) + gap) ** 2
    })
    return total
  }

  it('returns 1 and leaves sizes untouched when demand fits the viewport', () => {
    const graph = sizedGraph(10, 8)
    const scale = applyDensitySizeScale(graph, VIEW_W, VIEW_H)
    expect(scale).toBe(1)
    expect(graph.getNodeAttribute('n0', 'size')).toBe(8)
  })

  it('shrinks an over-budget graph so post-scale demand stays within coverage', () => {
    const graph = sizedGraph(1500, 10)
    const scale = applyDensitySizeScale(graph, VIEW_W, VIEW_H)
    expect(scale).toBeLessThan(1)
    expect(scale).toBeGreaterThanOrEqual(0.25)
    // Applied sizes + gap must fit the 0.3 coverage budget (float tolerance).
    expect(demand(graph, 3)).toBeLessThanOrEqual(0.3 * VIEW_W * VIEW_H * 1.0000001)
  })

  it('excludes hidden nodes from the budget and never resizes them', () => {
    const graph = sizedGraph(1500, 10)
    graph.setNodeAttribute('n0', 'filterHidden', true)
    graph.setNodeAttribute('n1', 'hidden', true)
    const before0 = graph.getNodeAttribute('n0', 'size')
    const before1 = graph.getNodeAttribute('n1', 'size')
    const scale = applyDensitySizeScale(graph, VIEW_W, VIEW_H)
    expect(scale).toBeLessThan(1)
    expect(graph.getNodeAttribute('n0', 'size')).toBe(before0)
    expect(graph.getNodeAttribute('n1', 'size')).toBe(before1)
    expect(graph.getNodeAttribute('n2', 'size')).toBeLessThan(10)
  })

  it('never shrinks a rendered size below the 1.5 floor', () => {
    const graph = sizedGraph(3000, 4)
    const scale = applyDensitySizeScale(graph, VIEW_W, VIEW_H)
    expect(scale).toBeLessThan(1)
    expect(scale).toBeGreaterThanOrEqual(0.25)
    // Raw 4 × 0.34 ≈ 1.37 would drop under the floor → every size clamps to 1.5.
    graph.forEachNode((_id, attrs) => {
      expect(attrs.size as number).toBeGreaterThanOrEqual(1.5)
    })
  })

  it('is a no-op for degenerate viewports and tiny graphs', () => {
    const graph = sizedGraph(1, 10)
    expect(applyDensitySizeScale(graph, VIEW_W, VIEW_H)).toBe(1)
    expect(applyDensitySizeScale(sizedGraph(5, 10), 0, VIEW_H)).toBe(1)
  })
})
