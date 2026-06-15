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
      color: '#313748',
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
      zIndex: 2,
    })
    const dimmed = reducers.nodeReducer?.('outside', { color: '#fff', size: 6 })
    expect(dimmed).toMatchObject({
      color: '#313748',
      label: '',
      forceLabel: false,
      zIndex: 0,
    })
    expect(dimmed?.size as number).toBeCloseTo(2.7)
  })

  it('thickens edges touching the selected node without recoloring them white, and deep-dims unrelated edges', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph)

    // Related edge keeps its edge-type color (no white), just thickens + labels.
    expect(reducers.edgeReducer?.('selected->hop-1', { color: '#93c5fd', size: 1 })).toEqual({
      color: '#93c5fd',
      size: 1.2,
      forceLabel: true,
      zIndex: 2,
    })
    expect(reducers.edgeReducer?.('outside->hop-2', { color: '#93c5fd', label: 'CALLS', size: 1 })).toEqual({
      color: '#1c283f',
      size: 0.25,
      label: '',
      forceLabel: false,
      zIndex: 0,
    })
  })

  it('layers neighbor-to-neighbor edges above the dimmed background, blanking their label, without thickening them', () => {
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

    expect(reducers.edgeReducer?.('a->b', { color: '#93c5fd', label: 'CALLS', size: 1 })).toEqual({
      color: '#93c5fd',
      size: 1,
      label: '',
      forceLabel: false,
      zIndex: 1,
    })
  })
})

describe('createSelectionFocusReducers with a hovered relation', () => {
  it('keeps only the selected node and the hovered counterpart bright, dimming other neighbors', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph, {
      edgeId: 'selected->hop-1',
      counterpartNodeId: 'hop-1',
    })

    expect(reducers.nodeReducer?.('selected', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      size: 9,
      highlighted: true,
      forceLabel: true,
      zIndex: 3,
    })
    expect(reducers.nodeReducer?.('hop-1', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      size: 9,
      highlighted: true,
      forceLabel: true,
      zIndex: 3,
    })
  })

  it('deep-dims nodes that are not part of the hovered relation', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph, {
      edgeId: 'selected->hop-1',
      counterpartNodeId: 'hop-1',
    })

    const dimmed = reducers.nodeReducer?.('hop-2', { color: '#fff', size: 6 })
    expect(dimmed).toMatchObject({
      color: '#313748',
      label: '',
      forceLabel: false,
      zIndex: 0,
    })
    expect(dimmed?.size as number).toBeCloseTo(2.7)
  })

  it('keeps only the hovered edge bright and dims every other edge', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph, {
      edgeId: 'selected->hop-1',
      counterpartNodeId: 'hop-1',
    })

    expect(reducers.edgeReducer?.('selected->hop-1', { color: '#93c5fd', size: 1 })).toEqual({
      color: '#93c5fd',
      size: 1.2,
      forceLabel: true,
      zIndex: 2,
    })
    expect(reducers.edgeReducer?.('hop-1->hop-2', { color: '#93c5fd', label: 'CALLS', size: 1 })).toEqual({
      color: '#1c283f',
      size: 0.25,
      label: '',
      forceLabel: false,
      zIndex: 0,
    })
  })

  it('falls back to plain selection focus when the hovered edge cannot be resolved', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph, {
      edgeId: 'missing-edge',
      counterpartNodeId: 'hop-1',
    })

    // hop-2 is a non-neighbor; without an active hover it dims via the normal
    // selection path rather than the single-relation path (same dim color, so
    // we assert the selected node keeps its standard +4 selection size).
    expect(reducers.nodeReducer?.('selected', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      size: 10,
      highlighted: true,
      forceLabel: true,
      zIndex: 3,
    })
  })

  it('falls back to plain selection focus when the counterpart node is missing', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph, {
      edgeId: 'selected->hop-1',
      counterpartNodeId: 'missing-node',
    })

    expect(reducers.nodeReducer?.('selected', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      size: 10,
      highlighted: true,
      forceLabel: true,
      zIndex: 3,
    })
  })
})

/**
 * Strict "colored ghost background" guarantees. These encode two visual bugs the
 * user reported in sequence:
 *   1. unrelated edges rendered as a bright white/light-blue spaghetti web, and
 *   2. the over-correction that painted everything flat near-black, killing all
 *      surrounding context.
 *
 * A dimmed edge/node must therefore:
 *   - never keep a white or light (bright) color,
 *   - never collapse to pure/near black (context must survive),
 *   - preserve some of its own hue (chroma > 0), so node-type / relation-type
 *     colors stay faintly legible,
 *   - shrink (smaller than its original size) but stay visible,
 *   - drop its label,
 *   - sink to the bottom zIndex layer.
 */
function parseChannels(color: unknown): { r: number; g: number; b: number } | null {
  if (typeof color !== 'string') return null
  const match = /^#([0-9a-f]{6})$/.exec(color.trim().toLowerCase())
  if (!match) return null
  const channels = match[1]
  if (!channels) return null
  return {
    r: parseInt(channels.slice(0, 2), 16),
    g: parseInt(channels.slice(2, 4), 16),
    b: parseInt(channels.slice(4, 6), 16),
  }
}

function isBrightColor(color: unknown): boolean {
  if (typeof color !== 'string') return false
  const hex = color.trim().toLowerCase()
  const rgb = parseChannels(hex)
  if (!rgb) return /rgba?\(\s*2[0-9]{2}/.test(hex)
  return rgb.r > 150 && rgb.g > 150 && rgb.b > 150
}

// Pure or near-pure black means context was destroyed — every channel collapsed
// toward 0. The previous flat dim color (#0b1220) is intentionally flagged here.
function isNearBlack(color: unknown): boolean {
  const rgb = parseChannels(color)
  if (!rgb) return false
  return rgb.r <= 18 && rgb.g <= 18 && rgb.b <= 18
}

// A faint colored ghost must retain chroma: the spread between its brightest and
// darkest channel stays above a small threshold. Pure greys (and pure black)
// have ~0 spread and fail this check.
function preservesHue(color: unknown): boolean {
  const rgb = parseChannels(color)
  if (!rgb) return false
  const max = Math.max(rgb.r, rgb.g, rgb.b)
  const min = Math.min(rgb.r, rgb.g, rgb.b)
  return max - min > 6
}

function buildRouteGraph(): Graph {
  // Mirrors the screenshot: a ROUTE node with a couple of related nodes, plus
  // many unrelated nodes wired into a dense web (the "spaghetti").
  const graph = new Graph({ type: 'directed', multi: true })
  graph.addNode('route', { label: 'POST /api/users/', color: '#10B981', size: 8, nodeType: 'Route' })
  graph.addNode('create', { label: 'create', color: '#3B82F6', size: 6 })
  graph.addEdgeWithKey('route->create', 'route', 'create', { color: '#059669', size: 1, label: 'HANDLES_ROUTE' })

  for (let i = 0; i < 12; i += 1) {
    graph.addNode(`u${i}`, { label: `Unrelated ${i}`, color: '#F59E0B', size: 6 })
  }
  for (let i = 0; i < 12; i += 1) {
    const target = (i + 1) % 12
    graph.addEdgeWithKey(`u${i}->u${target}`, `u${i}`, `u${target}`, {
      color: '#93c5fd', // light-blue: the exact color that bled through before
      size: 1,
      label: 'IMPORTS',
    })
  }
  return graph
}

describe('focus mode produces a colored ghost background (no bright spaghetti, no dead black)', () => {
  it('dims every unrelated edge to a faint colored ghost line with no label when a ROUTE node is selected', () => {
    const graph = buildRouteGraph()
    const reducers = createSelectionFocusReducers('route', graph)

    graph.forEachEdge((edge) => {
      const source = graph.source(edge)
      const target = graph.target(edge)
      const isRelated = source === 'route' || target === 'route'
      const out = reducers.edgeReducer?.(edge, graph.getEdgeAttributes(edge)) ?? {}

      if (isRelated) return

      expect(isBrightColor(out.color), `edge ${edge} color must not be bright`).toBe(false)
      expect(isNearBlack(out.color), `edge ${edge} color must not be near-black`).toBe(false)
      expect(preservesHue(out.color), `edge ${edge} must keep some relation hue`).toBe(true)
      // Shrunk from its original size (1) but still a visible ghost line.
      expect((out.size as number) < 1 && (out.size as number) > 0, `edge ${edge} must shrink but stay visible`).toBe(
        true,
      )
      expect(out.label).toBe('')
      expect(out.forceLabel).toBe(false)
      expect(out.zIndex).toBe(0)
    })
  })

  it('dims every unrelated node to a faint colored ghost (not black) with no label when a ROUTE node is selected', () => {
    const graph = buildRouteGraph()
    const reducers = createSelectionFocusReducers('route', graph)

    for (let i = 0; i < 12; i += 1) {
      const out = reducers.nodeReducer?.(`u${i}`, graph.getNodeAttributes(`u${i}`)) ?? {}
      expect(isBrightColor(out.color), `node u${i} color must not be bright`).toBe(false)
      expect(isNearBlack(out.color), `node u${i} color must not be near-black`).toBe(false)
      expect(preservesHue(out.color), `node u${i} must keep some node-type hue`).toBe(true)
      expect(out.color).toBe('#2f2a26') // amber mixed toward the dark background
      expect(out.label).toBe('')
      expect(out.forceLabel).toBe(false)
      expect(out.zIndex).toBe(0)
    }
  })

  it('keeps the related edge as the only non-dimmed, high-visibility edge', () => {
    const graph = buildRouteGraph()
    const reducers = createSelectionFocusReducers('route', graph)

    const related = reducers.edgeReducer?.('route->create', graph.getEdgeAttributes('route->create')) ?? {}
    expect(related.color).toBe('#059669') // keeps edge-type color, never white
    expect(related.size as number).toBeGreaterThan(0.25)
    expect(related.forceLabel).toBe(true)
    expect(related.zIndex).toBe(2)
  })

  it('keeps the selected ROUTE node dominant on the top layer', () => {
    const graph = buildRouteGraph()
    const reducers = createSelectionFocusReducers('route', graph)

    const out = reducers.nodeReducer?.('route', graph.getNodeAttributes('route')) ?? {}
    expect(out.highlighted).toBe(true)
    expect(out.forceLabel).toBe(true)
    expect(out.zIndex).toBe(3)
    expect(out.size as number).toBeGreaterThan(8)
  })

  it('never emits a bright or near-black dimmed color for any node type (Class/File/Method/Route)', () => {
    const graph = new Graph({ type: 'directed', multi: true })
    graph.addNode('sel', { color: '#F59E0B', size: 6 }) // Class-style
    graph.addNode('other-class', { color: '#F59E0B', size: 6 })
    graph.addNode('other-file', { color: '#EF4444', size: 6 })
    graph.addNode('other-method', { color: '#3B82F6', size: 6 })
    graph.addNode('other-route', { color: '#10B981', size: 6 })
    graph.addEdgeWithKey('sel->isolated', 'sel', 'other-class', { color: '#93c5fd', size: 1 })

    const reducers = createSelectionFocusReducers('sel', graph)

    for (const id of ['other-file', 'other-method', 'other-route']) {
      const out = reducers.nodeReducer?.(id, graph.getNodeAttributes(id)) ?? {}
      expect(isBrightColor(out.color), `${id} must not be bright`).toBe(false)
      expect(isNearBlack(out.color), `${id} must not be near-black`).toBe(false)
      expect(preservesHue(out.color), `${id} must keep some node-type hue`).toBe(true)
    }
  })
})
