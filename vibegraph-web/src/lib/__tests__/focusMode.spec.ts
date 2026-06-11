import { describe, expect, it } from 'vitest'
import Graph from 'graphology'
import { createFocusReducers, getNeighborsWithinHops, normalizeFocusDepth } from '../focusMode'

function buildGraph(): Graph {
  const graph = new Graph({ type: 'directed', multi: true })
  graph.addNode('selected', { label: 'Selected', color: '#ffffff' })
  graph.addNode('hop-1', { label: 'Hop 1', color: '#ffffff' })
  graph.addNode('hop-2', { label: 'Hop 2', color: '#ffffff' })
  graph.addNode('outside', { label: 'Outside', color: '#ffffff' })
  graph.addEdgeWithKey('selected->hop-1', 'selected', 'hop-1', { color: '#93c5fd' })
  graph.addEdgeWithKey('hop-1->hop-2', 'hop-1', 'hop-2', { color: '#93c5fd' })
  graph.addEdgeWithKey('outside->hop-2', 'outside', 'hop-2', { color: '#93c5fd' })
  return graph
}

describe('normalizeFocusDepth', () => {
  it('allows only supported focus depths', () => {
    expect(normalizeFocusDepth(-1)).toBe(-1)
    expect(normalizeFocusDepth(0)).toBe(0)
    expect(normalizeFocusDepth(5)).toBe(5)
    expect(normalizeFocusDepth(999)).toBe(-1)
    expect(normalizeFocusDepth(Number.POSITIVE_INFINITY)).toBe(-1)
  })
})

describe('getNeighborsWithinHops', () => {
  it('returns the selected node and neighbors within the requested depth', () => {
    const graph = buildGraph()

    expect(getNeighborsWithinHops(graph, 'selected', 0)).toEqual(new Set(['selected']))
    expect(getNeighborsWithinHops(graph, 'selected', 1)).toEqual(new Set(['selected', 'hop-1']))
    expect(getNeighborsWithinHops(graph, 'selected', 2)).toEqual(new Set(['selected', 'hop-1', 'hop-2']))
  })

  it('returns an empty set for missing selected nodes', () => {
    expect(getNeighborsWithinHops(buildGraph(), 'missing', 2)).toEqual(new Set())
  })

  it('returns an empty set for unsupported depths', () => {
    expect(getNeighborsWithinHops(buildGraph(), 'selected', 999)).toEqual(new Set())
  })

  it('caps traversal work on very large neighborhoods', () => {
    const graph = new Graph({ type: 'directed', multi: true })
    graph.addNode('root')
    for (let index = 0; index < 2000; index += 1) {
      const nodeId = `node-${index}`
      graph.addNode(nodeId)
      graph.addEdgeWithKey(`root->${nodeId}`, 'root', nodeId)
    }

    expect(getNeighborsWithinHops(graph, 'root', 1).size).toBeLessThanOrEqual(1500)
  })
})

describe('createFocusReducers', () => {
  it('does not change nodes or edges when focus mode is disabled', () => {
    const graph = buildGraph()
    const reducers = createFocusReducers('selected', -1, graph)

    expect(reducers.nodeReducer?.('outside', { color: '#ffffff' })).toEqual({ color: '#ffffff' })
    expect(reducers.edgeReducer?.('outside->hop-2', { color: '#93c5fd' })).toEqual({ color: '#93c5fd' })
  })

  it('highlights visible focus nodes and hides edges outside the focused neighborhood', () => {
    const graph = buildGraph()
    const reducers = createFocusReducers('selected', 1, graph)

    expect(reducers.nodeReducer?.('selected', { color: '#ffffff', size: 8 })).toEqual({
      color: '#ffffff',
      size: 10,
      highlighted: true,
    })
    expect(reducers.nodeReducer?.('outside', { color: '#ffffff', size: 8 })).toEqual({
      color: '#334155',
      size: 8,
      label: '',
    })
    expect(reducers.edgeReducer?.('selected->hop-1', { color: '#93c5fd' })).toEqual({ color: '#93c5fd' })
    expect(reducers.edgeReducer?.('hop-1->hop-2', { color: '#93c5fd' })).toEqual({
      color: '#93c5fd',
      hidden: true,
    })
  })
})
