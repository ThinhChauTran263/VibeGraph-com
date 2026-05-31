import { describe, it, expect } from 'vitest'
import { apiToGraphology } from '../graphAdapter'
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

  it('keeps parallel edges of the same type by suffixing duplicate keys', () => {
    const data = baseData()
    // Two PARAMETER_TYPE edges between the same pair collapse to one deterministic key.
    const dup = {
      id: 'com.example.UserController|PARAMETER_TYPE|com.example.UserService',
      source: 'com.example.UserController',
      target: 'com.example.UserService',
      type: 'PARAMETER_TYPE' as const,
    }
    data.edges.push({ ...dup }, { ...dup })
    const graph = apiToGraphology(data)
    // 1 INJECTS + 2 PARAMETER_TYPE (one suffixed) = 3 edges retained, none lost.
    expect(graph.size).toBe(3)
  })
})
