import { describe, it, expect } from 'vitest'
import { apiToGraphology } from '../graphAdapter'
import { NODE_COLORS, EDGE_COLORS } from '../constants'
import type { GraphData } from '@/types/graph'

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
    expect(graph.getNodeAttribute('file:User.java', 'size')).toBe(6.5)
    expect(graph.getNodeAttribute('com.example.UserRecord', 'color')).toBe(NODE_COLORS.Record)
    expect(graph.getNodeAttribute('com.example.UserRecord', 'size')).toBe(5)
    expect(graph.getNodeAttribute('com.example.UserEntity', 'color')).toBe(NODE_COLORS.DBModel)
    expect(graph.getNodeAttribute('com.example.UserEntity', 'size')).toBe(5)
    expect(graph.getNodeAttribute('com.example.UserService.<init>()', 'color')).toBe(NODE_COLORS.Constructor)
    expect(graph.getNodeAttribute('com.example.UserService.<init>()', 'size')).toBe(4)
    expect(graph.getNodeAttribute('GET /api/users', 'color')).toBe(NODE_COLORS.APIEndpoint)
    expect(graph.getNodeAttribute('GET /api/users', 'size')).toBe(4)
  })
})
