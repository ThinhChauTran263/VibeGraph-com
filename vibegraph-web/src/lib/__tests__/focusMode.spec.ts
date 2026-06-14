import { describe, expect, it } from 'vitest'
import Graph from 'graphology'
import {
  createFocusReducers,
  createSelectionFocusReducers,
  getDirectNeighbors,
  getNeighborsWithinHops,
  normalizeFocusDepth,
} from '../focusMode'

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

describe('getDirectNeighbors', () => {
  it('returns the node plus its direct (1-hop) neighbors, undirected', () => {
    const graph = buildGraph()

    expect(getDirectNeighbors(graph, 'selected')).toEqual(new Set(['selected', 'hop-1']))
    expect(getDirectNeighbors(graph, 'hop-1')).toEqual(new Set(['hop-1', 'selected', 'hop-2']))
  })

  it('returns an empty set for a missing node', () => {
    expect(getDirectNeighbors(buildGraph(), 'missing')).toEqual(new Set())
  })
})

describe('createSelectionFocusReducers', () => {
  it('is identity when nothing is selected', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers(null, graph)

    expect(reducers.nodeReducer?.('outside', { color: '#fff' })).toEqual({ color: '#fff' })
    expect(reducers.edgeReducer?.('outside->hop-2', { color: '#93c5fd' })).toEqual({ color: '#93c5fd' })
  })

  it('emphasizes the selected node and forces its label', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph)

    expect(reducers.nodeReducer?.('selected', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      size: 10,
      highlighted: true,
      forceLabel: true,
      zIndex: 3,
    })
  })

  it('keeps direct neighbors readable and deep-dims unrelated nodes to the bottom layer', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph)

    expect(reducers.nodeReducer?.('hop-1', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      size: 7,
      forceLabel: true,
      zIndex: 2,
    })
    const dimmed = reducers.nodeReducer?.('outside', { color: '#fff', size: 6 })
    expect(dimmed).toMatchObject({
      color: 'rgba(100, 116, 139, 0.10)',
      label: '',
      zIndex: 0,
    })
    expect(dimmed?.size as number).toBeCloseTo(3.6)
  })

  it('thickens edges touching the selected node without recoloring them white, and deep-dims unrelated edges', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph)

    // Related edge keeps its edge-type color (no white), just thickens + labels.
    expect(reducers.edgeReducer?.('selected->hop-1', { color: '#93c5fd', size: 1 })).toEqual({
      color: '#93c5fd',
      size: 1.6,
      forceLabel: true,
      zIndex: 2,
    })
    expect(reducers.edgeReducer?.('outside->hop-2', { color: '#93c5fd', label: 'CALLS' })).toEqual({
      color: 'rgba(100, 116, 139, 0.05)',
      label: '',
      zIndex: 0,
    })
  })

  it('layers neighbor-to-neighbor edges above the dimmed background without thickening them', () => {
    // center is connected to a and b; a->b is an edge between two direct
    // neighbors of center where neither endpoint is the selected node.
    const graph = new Graph({ type: 'directed', multi: true })
    graph.addNode('center', { label: 'Center', color: '#fff' })
    graph.addNode('a', { label: 'A', color: '#fff' })
    graph.addNode('b', { label: 'B', color: '#fff' })
    graph.addEdgeWithKey('center->a', 'center', 'a', { color: '#93c5fd' })
    graph.addEdgeWithKey('center->b', 'center', 'b', { color: '#93c5fd' })
    graph.addEdgeWithKey('a->b', 'a', 'b', { color: '#93c5fd' })

    const reducers = createSelectionFocusReducers('center', graph)

    expect(reducers.edgeReducer?.('a->b', { color: '#93c5fd', size: 1 })).toEqual({
      color: '#93c5fd',
      size: 1,
      zIndex: 1,
    })
  })
})
