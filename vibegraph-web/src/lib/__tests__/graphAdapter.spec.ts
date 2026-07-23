import Graph from 'graphology'
import { describe, it, expect, vi } from 'vitest'
import { apiToGraphology } from '../graphAdapter'
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
 * these.
 */
const BACKEND_EMITTED_EDGE_TYPES: EdgeType[] = [
  'CONTAINS',
  'DEFINES',
  'HAS_METHOD',
  'HAS_FIELD',
  'HAS_INNER',
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
  'ANNOTATED_BY',
  'READS',
  'WRITES',
  'CATCHES',
  'STEP_IN_FLOW',
]

function baseData(): GraphData {
  return {
    nodes: [
      {
        id: 'com.example.UserController',
        type: 'Class',
        name: 'UserController',
        fullName: 'com.example.UserController',
        filePath: 'src/main/java/com/example/UserController.java',
        lineNumber: 1,
        properties: { springLayer: 'Controller' },
      },
      {
        id: 'com.example.UserService',
        type: 'Class',
        name: 'UserService',
        fullName: 'com.example.UserService',
        filePath: 'src/main/java/com/example/UserService.java',
        lineNumber: 1,
        properties: { springLayer: 'Service' },
      },
      {
        id: 'src/main/java/com/example/UserService.java',
        type: 'File',
        name: 'UserService.java',
        fullName: 'UserService.java',
        filePath: 'src/main/java/com/example/UserService.java',
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
        weight: 3,
        occurrences: 7,
      },
      {
        id: 'com.example.UserService|CALLS|com.example.UserController',
        source: 'com.example.UserService',
        target: 'com.example.UserController',
        type: 'CALLS',
      },
      {
        id: 'com.example.UserController|PARAMETER_TYPE|com.example.UserService',
        source: 'com.example.UserController',
        target: 'com.example.UserService',
        type: 'PARAMETER_TYPE',
      },
      {
        id: 'com.example.UserController|CALLS|com.example.Ghost',
        source: 'com.example.UserController',
        target: 'com.example.Ghost',
        type: 'CALLS',
      },
    ],
    nodeStats: { Class: 2, File: 1 } as GraphData['nodeStats'],
    edgeStats: { INJECTS: 1, CALLS: 2, PARAMETER_TYPE: 1 } as GraphData['edgeStats'],
  }
}

describe('apiToGraphology', () => {
  it('imports nodes and edges through Graphology serialized format', () => {
    const spy = vi.spyOn(Graph.prototype, 'import')
    try {
      apiToGraphology(baseData())
      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          nodes: expect.arrayContaining([
            expect.objectContaining({
              key: 'com.example.UserController',
              attributes: expect.objectContaining({
                label: 'UserController',
                packageName: 'com.example',
                community: 'com.example',
                layer: 'Controller',
              }),
            }),
          ]),
          edges: expect.arrayContaining([
            expect.objectContaining({
              key: 'com.example.UserController|INJECTS|com.example.UserService',
              source: 'com.example.UserController',
              target: 'com.example.UserService',
              attributes: expect.objectContaining({
                label: 'INJECTS',
                weight: 3,
                occurrences: 7,
              }),
            }),
          ]),
        }),
      )
    } finally {
      spy.mockRestore()
    }
  })

  it('preserves directed backend edges 1:1, even for the same source-target pair', () => {
    const graph = apiToGraphology(baseData())

    expect(graph.hasEdge('com.example.UserController|INJECTS|com.example.UserService')).toBe(true)
    expect(graph.hasEdge('com.example.UserService|CALLS|com.example.UserController')).toBe(true)
    expect(graph.hasEdge('com.example.UserController|PARAMETER_TYPE|com.example.UserService')).toBe(
      true,
    )
    expect(graph.size).toBe(3)
    expect(graph.source('com.example.UserController|INJECTS|com.example.UserService')).toBe(
      'com.example.UserController',
    )
    expect(graph.target('com.example.UserController|INJECTS|com.example.UserService')).toBe(
      'com.example.UserService',
    )
  })

  it('imports every edge whose endpoints exist in the graph', () => {
    const data = baseData()
    const importableEdges = data.edges.filter((edge) => {
      const nodeIds = new Set(data.nodes.map((node) => node.id))
      return nodeIds.has(edge.source) && nodeIds.has(edge.target)
    })

    const graph = apiToGraphology(data)

    expect(graph.size).toBe(importableEdges.length)
    for (const edge of importableEdges) {
      expect(graph.hasEdge(edge.id)).toBe(true)
    }
  })

  it('skips edges that reference missing nodes', () => {
    const graph = apiToGraphology(baseData())
    expect(graph.size).toBe(3)
    expect(graph.hasEdge('com.example.UserController|CALLS|com.example.Ghost')).toBe(false)
  })

  it('maps metadata attributes and edge payload fields', () => {
    const graph = apiToGraphology(baseData())

    expect(graph.getNodeAttribute('com.example.UserController', 'packageName')).toBe('com.example')
    expect(graph.getNodeAttribute('com.example.UserController', 'community')).toBe('com.example')
    expect(graph.getNodeAttribute('com.example.UserController', 'layer')).toBe('Controller')
    expect(
      graph.getNodeAttribute('src/main/java/com/example/UserService.java', 'packageName'),
    ).toBe('com.example')
    expect(
      graph.getEdgeAttribute(
        'com.example.UserController|INJECTS|com.example.UserService',
        'weight',
      ),
    ).toBe(3)
    expect(
      graph.getEdgeAttribute(
        'com.example.UserController|INJECTS|com.example.UserService',
        'occurrences',
      ),
    ).toBe(7)
  })

  it('seeds initial node positions in a wider 2D cloud before ForceAtlas2 runs', () => {
    const graph = apiToGraphology(baseData())

    graph.forEachNode((node) => {
      const x = graph.getNodeAttribute(node, 'x') as number
      const y = graph.getNodeAttribute(node, 'y') as number
      expect(Number.isFinite(x)).toBe(true)
      expect(Number.isFinite(y)).toBe(true)
      expect(x).toBeGreaterThanOrEqual(-400)
      expect(x).toBeLessThanOrEqual(400)
      expect(y).toBeGreaterThanOrEqual(-400)
      expect(y).toBeLessThanOrEqual(400)
      expect(Math.hypot(x, y)).toBeGreaterThan(20)
    })
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
      expect(graph.getEdgeAttribute(edgeId as string, 'color')).not.toBe('#666666')
    }
  })

  it('has a color for every supported edge type', () => {
    for (const type of [...STRUCTURAL_EDGE_TYPES, ...CPG_LITE_EDGE_TYPES]) {
      expect(EDGE_COLORS[type]).toBeDefined()
    }
  })

  it('covers every backend-emitted type across the baseline and detail sets', () => {
    const partitioned = new Set<EdgeType>([...STRUCTURAL_EDGE_TYPES, ...CPG_LITE_EDGE_TYPES])
    for (const type of BACKEND_EMITTED_EDGE_TYPES) {
      expect(partitioned.has(type)).toBe(true)
    }
  })
})
