import type Graph from 'graphology'
import { dimColor } from './color'
import { EDGE_COLORS } from './constants'
import type { EdgeType } from '@/types/graph'

const ALLOWED_FOCUS_DEPTHS = new Set([-1, 0, 1, 2, 3, 5])
const MAX_FOCUSED_NODES = 1500

export function normalizeFocusDepth(depth: number): number {
  if (!Number.isInteger(depth) || !ALLOWED_FOCUS_DEPTHS.has(depth)) return -1
  return depth
}

export { getUndirectedNeighborsWithinHops as getNeighborsWithinHops }

export interface FocusReducers {
  nodeReducer: (node: string, attributes: Record<string, unknown>) => Record<string, unknown>
  edgeReducer: (edge: string, attributes: Record<string, unknown>) => Record<string, unknown>
}

export type FocusLabelDensity = 'minimal' | 'nodes' | 'edges'

const MINIMAL_LABEL_RATIO = 1.05
const EDGE_LABEL_RATIO = 0.2

export function resolveFocusLabelDensity(cameraRatio: number): FocusLabelDensity {
  if (!Number.isFinite(cameraRatio)) return 'nodes'
  if (cameraRatio > MINIMAL_LABEL_RATIO) return 'minimal'
  if (cameraRatio > EDGE_LABEL_RATIO) return 'nodes'
  return 'edges'
}

/**
 * Partition of the graph into a FOREGROUND set (selected + related, kept in the
 * interactive Sigma) and a BACKGROUND set (unrelated, HIDDEN in Sigma and drawn
 * instead on the ghost canvas layer physically below the WebGL edges).
 */
export interface FocusPartition {
  foregroundNodes: Set<string>
  backgroundNodes: Set<string>
  foregroundEdges: Set<string>
  backgroundEdges: Set<string>
}

export function getUndirectedNeighborsWithinHops(
  graph: Graph,
  nodeId: string,
  hops: number,
): Set<string> {
  const normalizedHops = normalizeFocusDepth(hops)
  if (normalizedHops < 0 || !graph.hasNode(nodeId)) return new Set<string>()

  const visited = new Set<string>([nodeId])
  let frontier = new Set<string>([nodeId])

  for (let depth = 0; depth < normalizedHops && frontier.size > 0; depth += 1) {
    const next = new Set<string>()
    frontier.forEach((currentNode) => {
      if (visited.size >= MAX_FOCUSED_NODES) return

      graph.forEachNeighbor(currentNode, (neighbor) => {
        if (visited.size >= MAX_FOCUSED_NODES) return
        if (!visited.has(neighbor)) {
          visited.add(neighbor)
          next.add(neighbor)
        }
      })
    })
    frontier = next
  }

  return visited
}

export function createFocusReducers(
  selectedId: string | null,
  depth: number,
  graph: Graph,
): FocusReducers {
  const normalizedDepth = normalizeFocusDepth(depth)
  if (!selectedId || normalizedDepth < 0 || !graph.hasNode(selectedId)) {
    return {
      nodeReducer: (_node, attributes) => attributes,
      edgeReducer: (_edge, attributes) => attributes,
    }
  }

  const focusedNodeIds = getUndirectedNeighborsWithinHops(graph, selectedId, normalizedDepth)

  return {
    nodeReducer: (node, attributes) => {
      if (focusedNodeIds.has(node)) {
        return {
          ...attributes,
          size: typeof attributes.size === 'number' ? attributes.size + 2 : attributes.size,
          highlighted: node === selectedId,
        }
      }

      return {
        ...attributes,
        color: dimColor(attributes.color, DIMMED_NODE_MIX),
        label: '',
      }
    },
    edgeReducer: (edge, attributes) => {
      const source = graph.source(edge)
      const target = graph.target(edge)

      if (focusedNodeIds.has(source) && focusedNodeIds.has(target)) {
        return attributes
      }

      return { ...attributes, hidden: true }
    },
  }
}

// LAYERING (the real fix). Sigma.js draws the entire node WebGL program ON TOP of
// the entire edge program, so zIndex can only order node-vs-node and edge-vs-edge
// — it can NEVER push a node behind an edge. A visible unrelated node therefore
// always paints over any foreground related edge it overlaps.
//
// The robust solution is TRUE layer separation: unrelated (background) nodes and
// edges are HIDDEN in the interactive Sigma and redrawn on a separate ghost canvas
// that sits PHYSICALLY BELOW the WebGL edges layer (created via
// `sigma.createCanvas('ghost-graph', { beforeLayer: 'edges' })`). Because that
// canvas is below the edges layer in the DOM/render order, a background node can
// never cover a foreground edge — regardless of pan/zoom. See ghostLayer.ts.
//
// Background styling keeps nodes at PROPORTIONAL size (not tiny dots) with their
// type hue preserved but blended toward the dark background so they read as soft
// "behind" context, matching the reference image.

// Unrelated context preserves each element's own hue by MIXING toward the canvas
// background (#0f172a) with `dimColor`, NOT a flat near-black and NOT rgba alpha.
// Flat near-black erased all type/relation hue; rgba alpha is unreliable in
// Sigma's WebGL line program (accumulates into a bright white web). Color-mixing
// yields an opaque, darkened, still hue-preserving color: faint amber stays amber.
const DIMMED_NODE_MIX = 0.86 // 86% background + 14% original hue (depth-filter path)

// Ghost-canvas (background layer) styling. Nodes stay at PROPORTIONAL size — the
// user explicitly rejected shrinking them to tiny dots. The layer separation
// (not the size) is what stops obstruction.
const GHOST_NODE_SIZE_MULTIPLIER = 0.8
const GHOST_NODE_MIN_SIZE = 2
const GHOST_NODE_MAX_SIZE = 20
const GHOST_NODE_MIX = 0.72 // 72% background + 28% original hue — softer, clearly "behind"
const GHOST_EDGE_SIZE_MULTIPLIER = 0.3
const GHOST_EDGE_MIN_SIZE = 0.4
const GHOST_EDGE_MIX = 0.85 // 85% background + 15% original hue — faint but visible

// Related edges keep their edge-type color (set by graphAdapter). For a sparse
// spotlight we keep them THIN (slightly under their original width) so the few
// related connections read as elegant threads, not heavy highlighted bands. They
// are NEVER recolored to white.
const RELATED_EDGE_SIZE_MULTIPLIER = 0.9

// Focus node scaling. The selected node is HIGHLIGHTED but only modestly enlarged
// (≈1.2x) — large additive bumps made it balloon out of proportion. Direct
// neighbors stay essentially their real size (≈1.05x). A single hovered/pinned
// counterpart uses the slightly smaller SELECTED multiplier too (no extra growth).
const SELECTED_NODE_SIZE_MULTIPLIER = 1.08
const NEIGHBOR_NODE_SIZE_MULTIPLIER = 1

/** Scale a node `size` attribute by a focus multiplier, leaving non-numeric sizes untouched. */
function scaleNodeSize(size: unknown, multiplier: number): unknown {
  // Round to 2 decimals so floating-point products (e.g. 6 * 1.2 = 7.199999…)
  // stay clean for rendering and deterministic in assertions.
  return typeof size === 'number' ? Math.round(size * multiplier * 100) / 100 : size
}

export { SELECTED_NODE_SIZE_MULTIPLIER, NEIGHBOR_NODE_SIZE_MULTIPLIER }

/**
 * Resolve the label text color for a related edge from its edge type, so the
 * label matches the Edge Types legend (colored stroke + same-colored text). Falls
 * back to the edge's own `labelColor`/`color` attribute, then a readable slate,
 * never white. Used by the selection focus reducer for foreground edges.
 */
export function relatedEdgeLabelColor(attributes: Record<string, unknown>): string {
  const edgeType = attributes.edgeType
  if (typeof edgeType === 'string' && edgeType in EDGE_COLORS) {
    return EDGE_COLORS[edgeType as EdgeType]
  }
  if (typeof attributes.labelColor === 'string') return attributes.labelColor
  if (typeof attributes.color === 'string') return attributes.color
  return '#cbd5e1'
}

// zIndex layers within the FOREGROUND Sigma only (requires `zIndex: true`).
// Higher renders on top. Background elements live on the ghost canvas, not here.
const Z_SELECTED = 3
const Z_NEIGHBOR = 2
const Z_RELATED_EDGE = 2

/**
 * Background-layer node radius for the ghost canvas. Proportional to the original
 * size (≈85%) and clamped to a readable band so large hubs don't dominate and
 * small nodes stay visible — never shrunk to a dot.
 */
export function ghostNodeSize(size: unknown): number {
  const base = typeof size === 'number' ? size : GHOST_NODE_MIN_SIZE
  const scaled = base * GHOST_NODE_SIZE_MULTIPLIER
  return Math.min(Math.max(scaled, GHOST_NODE_MIN_SIZE), GHOST_NODE_MAX_SIZE)
}

/** Background-layer node color: original hue blended toward the dark background. */
export function ghostNodeColor(color: unknown): string {
  return dimColor(color, GHOST_NODE_MIX)
}

/** Background-layer edge width for the ghost canvas: thin but visible. */
export function ghostEdgeSize(size: unknown): number {
  const base = typeof size === 'number' ? size : GHOST_EDGE_MIN_SIZE
  return Math.max(base * GHOST_EDGE_SIZE_MULTIPLIER, GHOST_EDGE_MIN_SIZE)
}

/** Background-layer edge color: original hue blended toward the dark background. */
export function ghostEdgeColor(color: unknown): string {
  return dimColor(color, GHOST_EDGE_MIX)
}

/**
 * Direct (1-hop) neighbors of a node plus the node itself, undirected.
 */
export function getDirectNeighbors(graph: Graph, nodeId: string): Set<string> {
  if (!graph.hasNode(nodeId)) return new Set<string>()

  const neighbors = new Set<string>([nodeId])
  graph.forEachNeighbor(nodeId, (neighbor) => {
    neighbors.add(neighbor)
  })
  return neighbors
}

/**
 * A single incoming/outgoing relation the user is hovering or has clicked in the
 * Node Detail panel. When present, the selection focus narrows further: only the
 * selected node, this one counterpart node, and the connecting edge stay bright;
 * every other node/edge (including the selected node's other neighbors) dims.
 */
export interface HoveredRelation {
  edgeId: string
  counterpartNodeId: string
}

/**
 * Resolved focus state used by the deterministic visibility helpers. Computed
 * once per reducer build so node/edge decisions are pure lookups.
 */
export interface SelectionFocusState {
  selectedId: string
  neighborIds: Set<string>
  activeHover: HoveredRelation | null
}

/**
 * Deterministic rule for whether a node is a FOREGROUND node while selection
 * focus is active (kept bright, full-size, layered on top). When a single
 * relation is hovered, only the selected node and its counterpart qualify.
 * Otherwise the selected node and its direct neighbors qualify. Every other node
 * is rendered as an ultra-subtle ghost dot — not hidden — so the surrounding
 * network still provides background depth.
 */
export function isNodeVisibleInFocus(nodeId: string, focusState: SelectionFocusState): boolean {
  if (focusState.activeHover) {
    return nodeId === focusState.selectedId || nodeId === focusState.activeHover.counterpartNodeId
  }
  return nodeId === focusState.selectedId || focusState.neighborIds.has(nodeId)
}

/**
 * Deterministic rule for whether an edge is a FOREGROUND related edge (kept
 * bright, labelled, layered above the ghost background). When a single relation
 * is hovered, only that edge qualifies. Otherwise any edge touching the selected
 * node qualifies. All other edges become faint ghost context.
 */
export function isEdgeRelatedToFocus(
  source: string,
  target: string,
  edgeId: string,
  focusState: SelectionFocusState,
): boolean {
  if (focusState.activeHover) {
    return edgeId === focusState.activeHover.edgeId
  }
  return source === focusState.selectedId || target === focusState.selectedId
}

/**
 * Resolve a hovered relation against the live graph, returning null when the edge
 * or counterpart node cannot be found (no crash, no graph mutation).
 */
function resolveActiveHover(graph: Graph, hovered: HoveredRelation | null): HoveredRelation | null {
  return hovered && graph.hasNode(hovered.counterpartNodeId) && graph.hasEdge(hovered.edgeId)
    ? hovered
    : null
}

/**
 * Pure partition of the graph for selection focus. FOREGROUND = selected node +
 * related nodes (direct neighbors, or the single hover counterpart) and their
 * related edges. BACKGROUND = everything else. The background set is what the
 * ghost canvas layer draws (and what the foreground Sigma hides), guaranteeing
 * unrelated nodes can never paint over foreground edges — they live on a separate
 * canvas physically below the WebGL edges.
 *
 * Returns empty foreground/background sets when there is no valid selection, so
 * callers can treat "no partition" as "normal graph view".
 */
export function partitionFocusGraph(
  selectedId: string | null,
  graph: Graph,
  hovered: HoveredRelation | null = null,
): FocusPartition {
  const empty: FocusPartition = {
    foregroundNodes: new Set<string>(),
    backgroundNodes: new Set<string>(),
    foregroundEdges: new Set<string>(),
    backgroundEdges: new Set<string>(),
  }

  if (!selectedId || !graph.hasNode(selectedId)) return empty

  const neighborIds = getDirectNeighbors(graph, selectedId)
  const activeHover = resolveActiveHover(graph, hovered)
  const focusState: SelectionFocusState = { selectedId, neighborIds, activeHover }

  const foregroundNodes = new Set<string>()
  const backgroundNodes = new Set<string>()
  graph.forEachNode((node) => {
    if (isNodeVisibleInFocus(node, focusState)) foregroundNodes.add(node)
    else backgroundNodes.add(node)
  })

  const foregroundEdges = new Set<string>()
  const backgroundEdges = new Set<string>()
  graph.forEachEdge((edge, _attributes, source, target) => {
    if (isEdgeRelatedToFocus(source, target, edge, focusState)) foregroundEdges.add(edge)
    else backgroundEdges.add(edge)
  })

  return { foregroundNodes, backgroundNodes, foregroundEdges, backgroundEdges }
}

/**
 * Click-driven neighborhood focus. Unlike createFocusReducers (depth control),
 * this keeps the selected node and its directly-connected neighbors readable and
 * layered on top in the interactive (foreground) Sigma, and HIDES every unrelated
 * node and edge there. The hidden unrelated graph is redrawn on the ghost canvas
 * layer (ghostLayer.ts), which sits physically below the WebGL edges — so a
 * background node can never paint over a foreground edge during pan/zoom/drag.
 * Drives requirement: clicking a node focuses its cluster regardless of the
 * focus-depth filter.
 *
 * When `hovered` is provided, the focus narrows to a single relation (selected
 * node + one counterpart + the connecting edge stay bright, and only then is the
 * edge labelled); every other node and edge is hidden here and rendered on the
 * ghost background instead, so the hovered/clicked detail-panel item is visually
 * isolated in the graph.
 */
export function createSelectionFocusReducers(
  selectedId: string | null,
  graph: Graph,
  hovered: HoveredRelation | null = null,
  labelDensity: FocusLabelDensity = 'edges',
): FocusReducers {
  if (!selectedId || !graph.hasNode(selectedId)) {
    return {
      nodeReducer: (_node, attributes) => attributes,
      edgeReducer: (_edge, attributes) => attributes,
    }
  }

  const neighborIds = getDirectNeighbors(graph, selectedId)
  const activeHover = resolveActiveHover(graph, hovered)
  const focusState: SelectionFocusState = { selectedId, neighborIds, activeHover }

  return {
    nodeReducer: (node, attributes) => {
      // Unrelated node: HIDE it in the interactive Sigma. It is redrawn on the
      // ghost canvas below the WebGL edges layer, so it physically cannot cover a
      // foreground edge. (This is the true-layering fix — not size/zIndex tuning.)
      if (!isNodeVisibleInFocus(node, focusState)) {
        return { ...attributes, hidden: true }
      }

      // Selected node, or the single hover counterpart: brightest, labelled.
      if (node === selectedId || (activeHover && node === activeHover.counterpartNodeId)) {
        return {
          ...attributes,
          hidden: false,
          size: scaleNodeSize(attributes.size, SELECTED_NODE_SIZE_MULTIPLIER),
          highlighted: true,
          forceLabel: true,
          zIndex: Z_SELECTED,
        }
      }

      // Direct neighbor (only reachable when no hover is active). Keep its label
      // text but DO NOT force it: Sigma reveals it via labelRenderedSizeThreshold
      // only once the node is big enough on screen (i.e. zoomed in enough). This
      // drives the progressive reveal — when zoomed out there isn't room, so only
      // the (forced) selected node label shows; neighbor names appear as you zoom
      // in, before edge labels.
      return {
        ...attributes,
        hidden: false,
        forceLabel: false,
        size: scaleNodeSize(attributes.size, NEIGHBOR_NODE_SIZE_MULTIPLIER),
        zIndex: Z_NEIGHBOR,
      }
    },
    edgeReducer: (edge, attributes) => {
      const source = graph.source(edge)
      const target = graph.target(edge)

      // Foreground related edge (touches the selected node, or the single hovered
      // relation): keep its edge-type color (set by graphAdapter), keep it THIN for
      // a sparse spotlight, and ALWAYS show its type label so graph hover/click
      // alone reveals every related edge's relationship — not just Node Detail
      // interaction. The label text takes the edge-type color so it matches the
      // legend and stays readable on the dark canvas — never the generic white.
      if (isEdgeRelatedToFocus(source, target, edge, focusState)) {
        const showEdgeLabel = labelDensity === 'edges'
        return {
          ...attributes,
          hidden: false,
          size:
            typeof attributes.size === 'number'
              ? attributes.size * RELATED_EDGE_SIZE_MULTIPLIER
              : attributes.size,
          forceLabel: showEdgeLabel,
          label: showEdgeLabel ? attributes.label : '',
          labelColor: relatedEdgeLabelColor(attributes),
          zIndex: Z_RELATED_EDGE,
        }
      }

      // Every other edge — including links between two direct neighbors — is HIDDEN
      // in the interactive Sigma and redrawn faintly on the ghost canvas below. A
      // spotlight shows only the selected node's own relations in the foreground.
      return { ...attributes, hidden: true }
    },
  }
}
