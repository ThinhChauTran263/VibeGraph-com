import { describe, expect, it } from 'vitest'
import Graph from 'graphology'
import {
  createSelectionFocusReducers,
  getDirectNeighbors,
  ghostEdgeColor,
  ghostNodeColor,
  ghostNodeSize,
  NEIGHBOR_NODE_SIZE_MULTIPLIER,
  partitionFocusGraph,
  relatedEdgeLabelColor,
  resolveFocusLabelDensity,
  SELECTED_NODE_SIZE_MULTIPLIER,
} from '../focusMode'
import { EDGE_COLORS } from '../constants'

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

describe('resolveFocusLabelDensity', () => {
  it('progressively reveals focus labels as the camera zooms in', () => {
    expect(resolveFocusLabelDensity(1.3)).toBe('minimal')
    expect(resolveFocusLabelDensity(0.9)).toBe('nodes')
    expect(resolveFocusLabelDensity(0.3)).toBe('edges')
  })

  it('falls back to node labels for invalid camera ratios', () => {
    expect(resolveFocusLabelDensity(Number.NaN)).toBe('nodes')
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
    expect(reducers.edgeReducer?.('outside->hop-2', { color: '#93c5fd' })).toEqual({
      color: '#93c5fd',
    })
  })

  it('emphasizes the selected node and forces its label', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph)

    expect(reducers.nodeReducer?.('selected', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      hidden: false,
      size: 6.48,
      highlighted: true,
      forceLabel: true,
      zIndex: 3,
    })
  })

  it('keeps direct neighbors readable and HIDES unrelated nodes from the foreground', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph)

    expect(reducers.nodeReducer?.('hop-1', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      hidden: false,
      forceLabel: false,
      size: 6,
      zIndex: 2,
    })
    // Unrelated node is HIDDEN in the interactive Sigma â€” it is redrawn on the
    // ghost canvas physically below the WebGL edges, so it can never paint over a
    // foreground edge. This is the true-layering fix (not size/zIndex tuning).
    expect(reducers.nodeReducer?.('outside', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      size: 6,
      hidden: true,
    })
  })

  it('keeps edges touching the selected node thin and ALWAYS labelled (graph click/hover reveals type), and HIDES unrelated edges', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph)

    // Related edge keeps its edge-type color (no white), stays THIN, and shows its
    // type label directly from graph interaction (forceLabel: true) â€” not only via
    // Node Detail hover.
    expect(
      reducers.edgeReducer?.('selected->hop-1', { color: '#93c5fd', label: 'CALLS', size: 1 }),
    ).toEqual({
      color: '#93c5fd',
      hidden: false,
      size: 0.9,
      label: 'CALLS',
      forceLabel: true,
      labelColor: '#93c5fd',
      zIndex: 2,
    })
    // Unrelated edge is HIDDEN in the foreground; the ghost canvas redraws it faintly.
    expect(
      reducers.edgeReducer?.('outside->hop-2', { color: '#93c5fd', label: 'CALLS', size: 1 }),
    ).toEqual({
      color: '#93c5fd',
      label: 'CALLS',
      size: 1,
      hidden: true,
    })
  })

  it('HIDES neighbor-to-neighbor edges from the foreground (sparse spotlight shows only the selected node relations)', () => {
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
      label: 'CALLS',
      size: 1,
      hidden: true,
    })
  })
})

describe('createSelectionFocusReducers with a hovered relation', () => {
  it('keeps only the selected node and the hovered counterpart bright, hiding other neighbors', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph, {
      edgeId: 'selected->hop-1',
      counterpartNodeId: 'hop-1',
    })

    expect(reducers.nodeReducer?.('selected', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      hidden: false,
      size: 6.48,
      highlighted: true,
      forceLabel: true,
      zIndex: 3,
    })
    expect(reducers.nodeReducer?.('hop-1', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      hidden: false,
      size: 6.48,
      highlighted: true,
      forceLabel: true,
      zIndex: 3,
    })
  })

  it('HIDES nodes that are not part of the hovered relation from the foreground', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph, {
      edgeId: 'selected->hop-1',
      counterpartNodeId: 'hop-1',
    })

    expect(reducers.nodeReducer?.('hop-2', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      size: 6,
      hidden: true,
    })
  })

  it('keeps only the hovered edge bright and HIDES every other edge from the foreground', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph, {
      edgeId: 'selected->hop-1',
      counterpartNodeId: 'hop-1',
    })

    expect(
      reducers.edgeReducer?.('selected->hop-1', { color: '#93c5fd', label: 'CALLS', size: 1 }),
    ).toEqual({
      color: '#93c5fd',
      hidden: false,
      size: 0.9,
      label: 'CALLS',
      forceLabel: true,
      labelColor: '#93c5fd',
      zIndex: 2,
    })
    expect(
      reducers.edgeReducer?.('hop-1->hop-2', { color: '#93c5fd', label: 'CALLS', size: 1 }),
    ).toEqual({
      color: '#93c5fd',
      label: 'CALLS',
      size: 1,
      hidden: true,
    })
  })

  it('falls back to plain selection focus when the hovered edge cannot be resolved', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph, {
      edgeId: 'missing-edge',
      counterpartNodeId: 'hop-1',
    })

    // hop-2 is a non-neighbor; without an active hover it hides via the normal
    // selection path rather than the single-relation path. We assert the
    // selected node keeps its standard selection size multiplier (6 * 1.08).
    expect(reducers.nodeReducer?.('selected', { color: '#fff', size: 6 })).toEqual({
      color: '#fff',
      hidden: false,
      size: 6.48,
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
      hidden: false,
      size: 6.48,
      highlighted: true,
      forceLabel: true,
      zIndex: 3,
    })
  })
})

/**
 * Partition tests. `partitionFocusGraph` is the source of truth for the
 * true-layering split: FOREGROUND (kept in the interactive Sigma) vs BACKGROUND
 * (hidden in Sigma, redrawn on the ghost canvas below the WebGL edges). The
 * partition is what guarantees a background node cannot paint over a foreground
 * edge â€” they live on physically separate canvases.
 */
describe('partitionFocusGraph', () => {
  it('returns empty sets when nothing is selected', () => {
    const graph = buildGraph()
    const partition = partitionFocusGraph(null, graph)

    expect(partition.foregroundNodes.size).toBe(0)
    expect(partition.backgroundNodes.size).toBe(0)
    expect(partition.foregroundEdges.size).toBe(0)
    expect(partition.backgroundEdges.size).toBe(0)
  })

  it('returns empty sets when the selected node is missing', () => {
    const graph = buildGraph()
    const partition = partitionFocusGraph('missing', graph)

    expect(partition.foregroundNodes.size).toBe(0)
    expect(partition.backgroundNodes.size).toBe(0)
  })

  it('puts the selected node and its direct neighbors in the foreground, the rest in the background', () => {
    const graph = buildGraph()
    const partition = partitionFocusGraph('selected', graph)

    // foreground = selected + direct neighbors
    expect(partition.foregroundNodes).toEqual(new Set(['selected', 'hop-1']))
    // background excludes every foreground node
    expect(partition.backgroundNodes).toEqual(new Set(['hop-2', 'outside']))
    for (const id of partition.foregroundNodes) {
      expect(partition.backgroundNodes.has(id)).toBe(false)
    }
  })

  it('keeps only edges touching the selected node in the foreground (neighbor links drop to background)', () => {
    const graph = new Graph({ type: 'directed', multi: true })
    graph.addNode('center', { color: '#fff' })
    graph.addNode('a', { color: '#fff' })
    graph.addNode('b', { color: '#fff' })
    graph.addNode('far', { color: '#fff' })
    graph.addEdgeWithKey('center->a', 'center', 'a', { color: '#93c5fd' })
    graph.addEdgeWithKey('center->b', 'center', 'b', { color: '#93c5fd' })
    graph.addEdgeWithKey('a->b', 'a', 'b', { color: '#93c5fd' }) // neighbor link -> background
    graph.addEdgeWithKey('a->far', 'a', 'far', { color: '#93c5fd' }) // leaves the cluster

    const partition = partitionFocusGraph('center', graph)

    // only edges touching center are foreground
    expect(partition.foregroundEdges).toEqual(new Set(['center->a', 'center->b']))
    // neighbor-to-neighbor link and the edge leaving the cluster are both background
    expect(partition.backgroundEdges).toEqual(new Set(['a->b', 'a->far']))
  })

  it('narrows the foreground to selected + counterpart + the connecting edge on incoming/outgoing hover', () => {
    const graph = buildGraph()

    // incoming/outgoing relation: hover the selected->hop-1 edge, counterpart hop-1
    const partition = partitionFocusGraph('selected', graph, {
      edgeId: 'selected->hop-1',
      counterpartNodeId: 'hop-1',
    })

    expect(partition.foregroundNodes).toEqual(new Set(['selected', 'hop-1']))
    expect(partition.backgroundNodes).toEqual(new Set(['hop-2', 'outside']))
    // only the hovered edge is foreground; all others (including other selected-touching edges) drop to background
    expect(partition.foregroundEdges).toEqual(new Set(['selected->hop-1']))
    expect(partition.backgroundEdges.has('hop-1->hop-2')).toBe(true)
    expect(partition.backgroundEdges.has('outside->hop-2')).toBe(true)
  })

  it('ignores an unresolvable hover and falls back to neighborhood focus', () => {
    const graph = buildGraph()
    const partition = partitionFocusGraph('selected', graph, {
      edgeId: 'missing-edge',
      counterpartNodeId: 'hop-1',
    })

    // same result as plain selection focus
    expect(partition.foregroundNodes).toEqual(new Set(['selected', 'hop-1']))
    expect(partition.foregroundEdges.has('selected->hop-1')).toBe(true)
  })
})

/**
 * Ghost-canvas styling. The user explicitly rejected shrinking unrelated nodes to
 * tiny dots: background nodes must keep PROPORTIONAL size and their type hue
 * (darkened toward the background), never near-black, never bright. These helpers
 * compute what the ghost layer draws.
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
  const rgb = parseChannels(color)
  if (!rgb) return false
  return rgb.r > 150 && rgb.g > 150 && rgb.b > 150
}

function isNearBlack(color: unknown): boolean {
  const rgb = parseChannels(color)
  if (!rgb) return false
  return rgb.r <= 18 && rgb.g <= 18 && rgb.b <= 18
}

function preservesHue(color: unknown): boolean {
  const rgb = parseChannels(color)
  if (!rgb) return false
  const max = Math.max(rgb.r, rgb.g, rgb.b)
  const min = Math.min(rgb.r, rgb.g, rgb.b)
  return max - min > 6
}

describe('ghost-canvas node styling (proportional, hue-preserving â€” never tiny, never black)', () => {
  it('keeps full proportional size — dimming is done by color, not size', () => {
    expect(ghostNodeSize(10)).toBe(10)
    // a large hub keeps its full size (no clamp that would shrink it)
    expect(ghostNodeSize(100)).toBe(100)
    // proportional rule: bigger original => bigger ghost
    expect(ghostNodeSize(6)).toBeGreaterThan(ghostNodeSize(3))
  })

  it('keeps node size well above a tiny dot for typical graph sizes', () => {
    // The user rejected tiny-dot ghosts. A normal node (size 6) must stay > 0.7x.
    expect(ghostNodeSize(6)).toBeGreaterThan(6 * 0.7)
  })

  it('falls back to the minimum size for non-numeric input', () => {
    expect(ghostNodeSize(undefined)).toBeGreaterThanOrEqual(2)
    expect(ghostNodeSize('nope')).toBeGreaterThanOrEqual(2)
  })

  it('darkens node color toward the background while preserving hue (not bright, not black)', () => {
    const amber = ghostNodeColor('#F59E0B')
    expect(isBrightColor(amber)).toBe(false)
    expect(isNearBlack(amber)).toBe(false)
    expect(preservesHue(amber)).toBe(true)

    const green = ghostNodeColor('#10B981')
    expect(isBrightColor(green)).toBe(false)
    expect(isNearBlack(green)).toBe(false)
    expect(preservesHue(green)).toBe(true)
  })

  it('falls back to a faint colored ghost for unparseable node colors (never pure black)', () => {
    const ghost = ghostNodeColor(undefined)
    expect(isNearBlack(ghost)).toBe(false)
    expect(preservesHue(ghost)).toBe(true)
  })
})

describe('ghost-canvas edge styling (hue-preserving)', () => {
  it('darkens edge color toward the background while preserving hue', () => {
    const ghost = ghostEdgeColor('#93c5fd')
    expect(isBrightColor(ghost)).toBe(false)
    expect(isNearBlack(ghost)).toBe(false)
    expect(preservesHue(ghost)).toBe(true)
  })
})

/**
 * Obstruction guarantee, restated for the true-layering architecture. The user
 * reported faint dimmed nodes rendering as dots ON TOP of bright related edges.
 *
 * Root cause: Sigma.js paints the entire node program above the entire edge
 * program, so zIndex orders node-vs-node and edge-vs-edge only â€” it can never push
 * a node behind a foreground edge within ONE Sigma.
 *
 * The fix: unrelated nodes/edges are HIDDEN in the interactive Sigma (so they are
 * not in the foreground node program at all) and redrawn on a ghost canvas that is
 * physically below the WebGL edges layer. These tests assert the foreground
 * reducers hide all unrelated elements and the partition routes them to the
 * background, which is what the ghost canvas draws.
 */
describe('unrelated elements are hidden from the foreground and routed to the ghost background', () => {
  function buildRouteGraph(): Graph {
    const graph = new Graph({ type: 'directed', multi: true })
    graph.addNode('route', {
      label: 'POST /api/users/',
      color: '#10B981',
      size: 8,
      nodeType: 'Route',
    })
    graph.addNode('create', { label: 'create', color: '#3B82F6', size: 6 })
    graph.addEdgeWithKey('route->create', 'route', 'create', {
      color: EDGE_COLORS.HANDLES_ROUTE,
      size: 1,
      label: 'HANDLES_ROUTE',
    })

    for (let i = 0; i < 12; i += 1) {
      graph.addNode(`u${i}`, { label: `Unrelated ${i}`, color: '#F59E0B', size: 6 })
    }
    for (let i = 0; i < 12; i += 1) {
      const target = (i + 1) % 12
      graph.addEdgeWithKey(`u${i}->u${target}`, `u${i}`, `u${target}`, {
        color: '#93c5fd',
        size: 1,
        label: 'IMPORTS',
      })
    }
    return graph
  }

  it('hides every unrelated node from the interactive foreground when a ROUTE node is selected', () => {
    const graph = buildRouteGraph()
    const reducers = createSelectionFocusReducers('route', graph)

    for (let i = 0; i < 12; i += 1) {
      const out = reducers.nodeReducer?.(`u${i}`, graph.getNodeAttributes(`u${i}`)) ?? {}
      expect(out.hidden).toBe(true)
    }
  })

  it('hides every unrelated edge from the interactive foreground when a ROUTE node is selected', () => {
    const graph = buildRouteGraph()
    const reducers = createSelectionFocusReducers('route', graph)

    graph.forEachEdge((edge) => {
      const source = graph.source(edge)
      const target = graph.target(edge)
      if (source === 'route' || target === 'route') return
      const out = reducers.edgeReducer?.(edge, graph.getEdgeAttributes(edge)) ?? {}
      expect(out.hidden).toBe(true)
    })
  })

  it('routes every unrelated node and edge to the background partition (the ghost canvas draws these)', () => {
    const graph = buildRouteGraph()
    const partition = partitionFocusGraph('route', graph)

    // foreground holds only the route + its one neighbor
    expect(partition.foregroundNodes).toEqual(new Set(['route', 'create']))
    expect(partition.foregroundEdges).toEqual(new Set(['route->create']))
    // every unrelated node/edge is in the background, ready for the ghost layer
    for (let i = 0; i < 12; i += 1) {
      expect(partition.backgroundNodes.has(`u${i}`)).toBe(true)
    }
    expect(partition.backgroundEdges.size).toBe(12)
  })

  it('keeps the related edge visible and the selected node dominant on the foreground', () => {
    const graph = buildRouteGraph()
    const reducers = createSelectionFocusReducers('route', graph)

    const related =
      reducers.edgeReducer?.('route->create', graph.getEdgeAttributes('route->create')) ?? {}
    expect(related.hidden).toBe(false)
    expect(related.color).toBe(EDGE_COLORS.HANDLES_ROUTE) // keeps edge-type color, never white
    expect(related.labelColor).toBe(EDGE_COLORS.HANDLES_ROUTE) // label text matches the edge-type hue
    // related edges show their type label when zoom density allows edge labels
    expect(related.label).toBe('HANDLES_ROUTE')
    expect(related.forceLabel).toBe(true)
    expect(related.zIndex).toBe(2)

    const node = reducers.nodeReducer?.('route', graph.getNodeAttributes('route')) ?? {}
    expect(node.hidden).toBe(false)
    expect(node.highlighted).toBe(true)
    expect(node.forceLabel).toBe(true)
    expect(node.zIndex).toBe(3)
    expect(node.size as number).toBeGreaterThan(8)
  })
})

/**
 * Edge label COLOR. Related-edge labels must take the edge-type color (matching
 * the Edge Types legend), never a generic white/slate. The reducer derives this
 * per edge from `EDGE_COLORS[edgeType]` so different relation types stay visually
 * distinct and the focus selection does not collapse every label to one color.
 */
describe('relatedEdgeLabelColor', () => {
  it('resolves the label color from the edge type using EDGE_COLORS', () => {
    expect(relatedEdgeLabelColor({ edgeType: 'DEFINES' })).toBe(EDGE_COLORS.DEFINES)
    expect(relatedEdgeLabelColor({ edgeType: 'CALLS' })).toBe(EDGE_COLORS.CALLS)
    expect(relatedEdgeLabelColor({ edgeType: 'IMPORTS' })).toBe(EDGE_COLORS.IMPORTS)
    expect(relatedEdgeLabelColor({ edgeType: 'HAS_METHOD' })).toBe(EDGE_COLORS.HAS_METHOD)
  })

  it('falls back to labelColor then color, then a readable slate, never white', () => {
    expect(relatedEdgeLabelColor({ labelColor: '#abcdef' })).toBe('#abcdef')
    expect(relatedEdgeLabelColor({ color: '#123456' })).toBe('#123456')
    expect(relatedEdgeLabelColor({})).toBe('#cbd5e1')
  })
})

describe('createSelectionFocusReducers edge label color', () => {
  it('colors each related-edge label by its own edge type, not one generic color', () => {
    const graph = new Graph({ type: 'directed', multi: true })
    graph.addNode('center', { label: 'Center', color: '#fff' })
    graph.addNode('a', { label: 'A', color: '#fff' })
    graph.addNode('b', { label: 'B', color: '#fff' })
    graph.addEdgeWithKey('center->a', 'center', 'a', {
      color: EDGE_COLORS.CALLS,
      label: 'CALLS',
      edgeType: 'CALLS',
      size: 1,
    })
    graph.addEdgeWithKey('center->b', 'center', 'b', {
      color: EDGE_COLORS.IMPORTS,
      label: 'IMPORTS',
      edgeType: 'IMPORTS',
      size: 1,
    })

    const reducers = createSelectionFocusReducers('center', graph)
    const callsEdge =
      reducers.edgeReducer?.('center->a', graph.getEdgeAttributes('center->a')) ?? {}
    const importsEdge =
      reducers.edgeReducer?.('center->b', graph.getEdgeAttributes('center->b')) ?? {}

    expect(callsEdge.labelColor).toBe(EDGE_COLORS.CALLS)
    expect(importsEdge.labelColor).toBe(EDGE_COLORS.IMPORTS)
    expect(callsEdge.labelColor).not.toBe(importsEdge.labelColor)
  })
})

/**
 * Req A â€” focus node scale bounds. The selected node was ballooning under the old
 * additive bump (+4). The fix uses MULTIPLICATIVE scaling kept inside the required
 * band: selected ~1.05x-1.1x, neighbors stay at original size. These guards fail if
 * anyone reintroduces an oversized additive bump.
 */
describe('focus node size multipliers stay within the required band (Req A)', () => {
  it('keeps the selected multiplier subtle (1.05x-1.1x)', () => {
    expect(SELECTED_NODE_SIZE_MULTIPLIER).toBeGreaterThanOrEqual(1.05)
    expect(SELECTED_NODE_SIZE_MULTIPLIER).toBeLessThanOrEqual(1.1)
  })

  it('keeps direct neighbors at original size', () => {
    expect(NEIGHBOR_NODE_SIZE_MULTIPLIER).toBeGreaterThanOrEqual(1)
    expect(NEIGHBOR_NODE_SIZE_MULTIPLIER).toBe(1)
  })

  it('scales the rendered selected/neighbor sizes by the multipliers, not a large additive bump', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph)

    const selected = reducers.nodeReducer?.('selected', { color: '#fff', size: 10 }) ?? {}
    const neighbor = reducers.nodeReducer?.('hop-1', { color: '#fff', size: 10 }) ?? {}

    expect(selected.size).toBeCloseTo(10 * SELECTED_NODE_SIZE_MULTIPLIER, 5)
    expect(neighbor.size).toBeCloseTo(10 * NEIGHBOR_NODE_SIZE_MULTIPLIER, 5)
    // selected stays comfortably under the old +4 additive result (14)
    expect(selected.size as number).toBeLessThan(14)
  })
})

/**
 * Req B â€” graph-interaction edge labels. Selecting (clicking) or hovering a graph
 * node must reveal the edge-type label on every related 1-hop edge, coloured by
 * edge type, WITHOUT any Node Detail interaction. Unrelated edges stay hidden (no
 * label). This is driven purely by createSelectionFocusReducers, which both the
 * click (selected) and hover (hovered graph node) focus paths use.
 */
describe('related edge labels appear from graph interaction alone (Req B)', () => {
  it('marks every edge touching the focused node label-visible with an edge-type colour at edge density', () => {
    const graph = new Graph({ type: 'directed', multi: true })
    graph.addNode('center', { color: '#fff', size: 6 })
    graph.addNode('a', { color: '#fff', size: 6 })
    graph.addNode('b', { color: '#fff', size: 6 })
    graph.addEdgeWithKey('center->a', 'center', 'a', {
      color: EDGE_COLORS.CALLS,
      label: 'CALLS',
      edgeType: 'CALLS',
      size: 1,
    })
    graph.addEdgeWithKey('center->b', 'center', 'b', {
      color: EDGE_COLORS.IMPORTS,
      label: 'IMPORTS',
      edgeType: 'IMPORTS',
      size: 1,
    })

    // Both the click path and the hover path build the same reducers, so this one
    // assertion covers "selected node" and "hovered graph node" label reveal.
    const reducers = createSelectionFocusReducers('center', graph)
    const calls = reducers.edgeReducer?.('center->a', graph.getEdgeAttributes('center->a')) ?? {}
    const imports = reducers.edgeReducer?.('center->b', graph.getEdgeAttributes('center->b')) ?? {}

    expect(calls.forceLabel).toBe(true)
    expect(calls.hidden).toBe(false)
    expect(calls.labelColor).toBe(EDGE_COLORS.CALLS)

    expect(imports.forceLabel).toBe(true)
    expect(imports.hidden).toBe(false)
    expect(imports.labelColor).toBe(EDGE_COLORS.IMPORTS)
  })

  it('leaves unrelated edges hidden and unlabelled', () => {
    const graph = buildGraph()
    const reducers = createSelectionFocusReducers('selected', graph)

    const unrelated =
      reducers.edgeReducer?.('outside->hop-2', { color: '#93c5fd', label: 'CALLS', size: 1 }) ?? {}
    expect(unrelated.hidden).toBe(true)
    expect(unrelated.forceLabel).toBeUndefined()
  })
})

describe('createSelectionFocusReducers label density', () => {
  it('always forces the selected node label and never forces neighbor labels (neighbors reveal by zoom)', () => {
    const graph = buildGraph()

    for (const density of ['minimal', 'nodes', 'edges'] as const) {
      const reducers = createSelectionFocusReducers('selected', graph, null, density)

      // Selected node label is always forced, regardless of zoom density.
      expect(
        reducers.nodeReducer?.('selected', { color: '#fff', label: 'Selected', size: 6 }),
      ).toMatchObject({ label: 'Selected', forceLabel: true })

      // Neighbor keeps its label text but is NEVER force-shown: Sigma's size
      // threshold reveals it only when zoomed in enough (progressive reveal).
      expect(
        reducers.nodeReducer?.('hop-1', { color: '#fff', label: 'Hop 1', size: 6 }),
      ).toMatchObject({ label: 'Hop 1', forceLabel: false })
    }
  })

  it('only shows related edge labels at the most zoomed-in (edges) density', () => {
    const graph = buildGraph()

    for (const density of ['minimal', 'nodes'] as const) {
      const reducers = createSelectionFocusReducers('selected', graph, null, density)
      expect(
        reducers.edgeReducer?.('selected->hop-1', { color: '#93c5fd', label: 'CALLS', size: 1 }),
      ).toMatchObject({ label: '', forceLabel: false })
    }

    const edgeReducers = createSelectionFocusReducers('selected', graph, null, 'edges')
    expect(
      edgeReducers.edgeReducer?.('selected->hop-1', { color: '#93c5fd', label: 'CALLS', size: 1 }),
    ).toMatchObject({ label: 'CALLS', forceLabel: true })
  })
})
