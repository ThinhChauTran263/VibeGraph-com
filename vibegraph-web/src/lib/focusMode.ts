import type Graph from 'graphology'
import { dimColor } from './color'

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

export function getUndirectedNeighborsWithinHops(graph: Graph, nodeId: string, hops: number): Set<string> {
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

export function createFocusReducers(selectedId: string | null, depth: number, graph: Graph): FocusReducers {
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

// Unrelated context is pushed into the background by MIXING each element's own
// color toward the canvas background (#0f172a) with `dimColor`, NOT by painting
// a flat near-black hex and NOT by rgba alpha.
//
// Why not flat near-black: it erased all context — the unrelated graph went
// fully dead/black (no node-type or relation-type hue survived), so the user
// lost the surrounding network as a reference.
//
// Why not rgba alpha: Sigma's WebGL `line` edge program does not reliably honor
// low-alpha rgba. With hundreds of overlapping near-transparent edges the alpha
// either fails to apply or the fragments accumulate, leaving a bright
// white/light-blue "spaghetti" web over the whole canvas.
//
// Color-mixing toward the dark background yields an OPAQUE, darkened, but still
// hue-preserving color: a dimmed amber node stays faint amber, a dimmed blue
// edge stays faint blue. The result is a soft "colored ghost" background —
// never white, never pure black.

// Edges keep a faint hue-preserving ghost so the surrounding network still reads
// as soft "atmosphere" rather than vanishing. In click-driven selection focus,
// unrelated NODES are hidden entirely (see below); only the depth-filter path
// (createFocusReducers) still color-mixes nodes toward the background.
const DIMMED_NODE_MIX = 0.86 // 86% background + 14% original hue
const DIMMED_EDGE_MIX = 0.9 // 90% background + 10% original hue

// Dimmed edges shrink to a very thin ghost line. Kept just > 0 so the
// surrounding network still reads as faint context rather than vanishing.
const DIMMED_EDGE_SIZE_MULTIPLIER = 0.25
const DIMMED_EDGE_MIN_SIZE = 0.2

// ROOT CAUSE of the obstruction bug: Sigma.js draws the entire node program ON
// TOP of the entire edge program, so zIndex can only order node-vs-node and
// edge-vs-edge — it can NEVER push a node behind an edge. A visible unrelated
// node therefore always paints over any foreground related edge it overlaps, no
// matter how small or how low its zIndex. Shrinking to a sub-pixel speck (the
// previous attempt) still left dark dots sitting on bright HAS_METHOD/IMPORTS
// edges. The only reliable fix is to set `hidden: true` on unrelated nodes while
// focus is active, so they are not rendered at all. Background context then
// comes solely from the faint ghost edges.

// Related edges keep their edge-type color (set by graphAdapter) — we only bump
// thickness slightly (~20% reduction from the previous 1.6) for an elegant,
// not-heavy look. They are NEVER recolored to white, which previously made
// thick white bands that hid the edge label text.
const RELATED_EDGE_SIZE_MULTIPLIER = 1.2

// zIndex layers (requires Sigma `zIndex: true`). Higher renders on top.
const Z_SELECTED = 3
const Z_NEIGHBOR = 2
const Z_RELATED_EDGE = 2
const Z_NEIGHBOR_EDGE = 1
const Z_DIMMED = 0

/** Shrink an edge to a thin ghost line, floored so the network still reads as faint context. */
function dimmedEdgeSize(size: unknown): number {
  return typeof size === 'number'
    ? Math.max(size * DIMMED_EDGE_SIZE_MULTIPLIER, DIMMED_EDGE_MIN_SIZE)
    : DIMMED_EDGE_MIN_SIZE
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
 * Deterministic rule for whether a node is RENDERED while selection focus is
 * active. When a single relation is hovered, only the selected node and its
 * counterpart are visible. Otherwise the selected node and its direct neighbors
 * are visible. Every other node is hidden — never merely dimmed — because Sigma
 * always paints nodes above edges, so a visible unrelated node would obstruct
 * foreground related edges.
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
 * Click-driven neighborhood focus. Unlike createFocusReducers (depth control),
 * this HIDES unrelated nodes entirely (Sigma paints nodes above edges, so a
 * visible unrelated dot would obstruct foreground related edges), keeps the
 * selected node and its directly-connected neighbors readable, dims unrelated
 * edges to faint ghost lines for background context, and emphasizes the edges
 * that touch the selected node. Drives requirement: clicking a node focuses its
 * cluster regardless of the focus-depth filter.
 *
 * When `hovered` is provided, the focus narrows to a single relation (selected
 * node + one counterpart + the connecting edge); every other node is hidden and
 * every other edge becomes a faint ghost, so the hovered/clicked detail-panel
 * item is visually isolated in the graph.
 */
export function createSelectionFocusReducers(
  selectedId: string | null,
  graph: Graph,
  hovered: HoveredRelation | null = null,
): FocusReducers {
  if (!selectedId || !graph.hasNode(selectedId)) {
    return {
      nodeReducer: (_node, attributes) => attributes,
      edgeReducer: (_edge, attributes) => attributes,
    }
  }

  const neighborIds = getDirectNeighbors(graph, selectedId)

  // Resolve the hovered relation against the live graph. If the edge or
  // counterpart node can't be resolved, fall back to plain selection focus
  // (no crash, no graph mutation).
  const activeHover =
    hovered && graph.hasNode(hovered.counterpartNodeId) && graph.hasEdge(hovered.edgeId)
      ? hovered
      : null

  const focusState: SelectionFocusState = { selectedId, neighborIds, activeHover }

  return {
    nodeReducer: (node, attributes) => {
      // Unrelated node: hide it entirely. Sigma paints the node layer above the
      // edge layer, so a visible dot — at any size or zIndex — would obstruct
      // foreground related edges. Hiding is the only reliable fix.
      if (!isNodeVisibleInFocus(node, focusState)) {
        return { ...attributes, hidden: true, label: '', forceLabel: false, zIndex: Z_DIMMED }
      }

      // Selected node, or the single hover counterpart: brightest, labelled.
      if (node === selectedId || (activeHover && node === activeHover.counterpartNodeId)) {
        const bump = activeHover ? 3 : 4
        return {
          ...attributes,
          hidden: false,
          size: typeof attributes.size === 'number' ? attributes.size + bump : attributes.size,
          highlighted: true,
          forceLabel: true,
          zIndex: Z_SELECTED,
        }
      }

      // Direct neighbor (only reachable when no hover is active). Readable and
      // layered above the ghost background, but we do NOT force its label so the
      // view never floods with labels at once.
      return {
        ...attributes,
        hidden: false,
        size: typeof attributes.size === 'number' ? attributes.size + 1 : attributes.size,
        zIndex: Z_NEIGHBOR,
      }
    },
    edgeReducer: (edge, attributes) => {
      const source = graph.source(edge)
      const target = graph.target(edge)

      // Foreground related edge: keep its edge-type color (set by graphAdapter),
      // bump width modestly, show its label. NEVER recolor to white — that
      // produced the thick white band that hid the label text.
      if (isEdgeRelatedToFocus(source, target, edge, focusState)) {
        return {
          ...attributes,
          hidden: false,
          size:
            typeof attributes.size === 'number'
              ? attributes.size * RELATED_EDGE_SIZE_MULTIPLIER
              : attributes.size,
          forceLabel: true,
          zIndex: Z_RELATED_EDGE,
        }
      }

      // Edge between two direct neighbors (no hover active): keep its color,
      // layer it above the ghost background, but blank its label so only the
      // selected node's own relation edges show labels.
      if (!activeHover && neighborIds.has(source) && neighborIds.has(target)) {
        return { ...attributes, hidden: false, label: '', forceLabel: false, zIndex: Z_NEIGHBOR_EDGE }
      }

      // Unrelated edge: mix its own relation-type color toward the background so
      // it stays a faint hue-preserving ghost line (never white, never black),
      // drop the label, sink to the bottom layer. This faint edge web is the
      // ONLY background context now that unrelated nodes are hidden.
      return {
        ...attributes,
        hidden: false,
        color: dimColor(attributes.color, DIMMED_EDGE_MIX),
        size: dimmedEdgeSize(attributes.size),
        label: '',
        forceLabel: false,
        zIndex: Z_DIMMED,
      }
    },
  }
}
