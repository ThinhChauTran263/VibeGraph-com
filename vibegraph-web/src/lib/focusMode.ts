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

// Nodes keep a little more of their hue than edges so node-type colors survive
// as a faint atmospheric wash, while the denser edge web recedes further. Both
// are pushed closer to the background than before so the dimmed layer reads as
// soft "atmosphere" rather than competing dots/lines over the focused cluster.
const DIMMED_NODE_MIX = 0.86 // 86% background + 14% original hue
const DIMMED_EDGE_MIX = 0.9 // 90% background + 10% original hue

// Dimmed edges shrink to a very thin ghost line. Kept just > 0 so the
// surrounding network still reads as faint context rather than vanishing.
const DIMMED_EDGE_SIZE_MULTIPLIER = 0.25
const DIMMED_EDGE_MIN_SIZE = 0.2

// Dimmed nodes shrink to a sub-pixel speck. This is the fix for the obstruction
// bug: Sigma.js draws the entire node program ON TOP of the entire edge program,
// so zIndex can only order node-vs-node and edge-vs-edge — it can NEVER push a
// node behind an edge. A dimmed node therefore always paints over any foreground
// related edge it overlaps. The only reliable way to stop a dimmed dot from
// covering a bright relation line is to make it too small to obstruct.
//
// DIMMED_NODE_MAX_SIZE is a HARD CAP applied after the multiplier, so even a
// large hub node (size 12+) collapses to <= 0.8px when dimmed. The floor keeps
// it just visible as a faint colored-ghost speck (requirement: preserve context).
const DIMMED_NODE_SIZE_MULTIPLIER = 0.45
const DIMMED_NODE_MIN_SIZE = 0.5
const DIMMED_NODE_MAX_SIZE = 0.8

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

/**
 * Shrink a node to a sub-pixel speck for the dimmed background layer. Clamped to
 * [MIN, MAX] so the dimmed dot is always too small to cover a foreground edge
 * (Sigma always paints nodes above edges) yet still faintly visible as context.
 */
function dimmedNodeSize(size: unknown): unknown {
  if (typeof size !== 'number') return size
  const shrunk = size * DIMMED_NODE_SIZE_MULTIPLIER
  return Math.min(Math.max(shrunk, DIMMED_NODE_MIN_SIZE), DIMMED_NODE_MAX_SIZE)
}

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
 * Click-driven neighborhood focus. Unlike createFocusReducers (depth control),
 * this DIMS unrelated nodes/edges instead of hiding them, keeps the selected
 * node and its directly-connected neighbors readable, and emphasizes the edges
 * that touch the selected node. Drives requirement: clicking a node focuses its
 * cluster regardless of the focus-depth filter.
 *
 * When `hovered` is provided, the focus narrows to a single relation (selected
 * node + one counterpart + the connecting edge), dimming everything else so the
 * hovered/clicked detail-panel item is visually isolated in the graph.
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

  return {
    nodeReducer: (node, attributes) => {
      if (activeHover) {
        if (node === selectedId || node === activeHover.counterpartNodeId) {
          return {
            ...attributes,
            size: typeof attributes.size === 'number' ? attributes.size + 3 : attributes.size,
            highlighted: true,
            forceLabel: true,
            zIndex: Z_SELECTED,
          }
        }

        // Everything else recedes while a single relation is highlighted.
        return {
          ...attributes,
          size: dimmedNodeSize(attributes.size),
          color: dimColor(attributes.color, DIMMED_NODE_MIX),
          label: '',
          forceLabel: false,
          zIndex: Z_DIMMED,
        }
      }

      if (node === selectedId) {
        return {
          ...attributes,
          size: typeof attributes.size === 'number' ? attributes.size + 4 : attributes.size,
          highlighted: true,
          forceLabel: true,
          zIndex: Z_SELECTED,
        }
      }

      if (neighborIds.has(node)) {
        // Neighbors stay readable and layered above the dim background, but we
        // do NOT force their labels — only the selected node (and, on hover, the
        // single counterpart) forces a label, so the view never floods with
        // labels at once.
        return {
          ...attributes,
          size: typeof attributes.size === 'number' ? attributes.size + 1 : attributes.size,
          zIndex: Z_NEIGHBOR,
        }
      }

      // Unrelated node: shrink, mix its own color toward the background so it
      // stays a faint hue-preserving ghost (never black, never white), drop the
      // label, and sink to the bottom layer.
      return {
        ...attributes,
        size: dimmedNodeSize(attributes.size),
        color: dimColor(attributes.color, DIMMED_NODE_MIX),
        label: '',
        forceLabel: false,
        zIndex: Z_DIMMED,
      }
    },
    edgeReducer: (edge, attributes) => {
      const source = graph.source(edge)
      const target = graph.target(edge)

      if (activeHover) {
        // Only the hovered relation's edge stays bright (keeps its edge-type
        // color, bumped width, label shown). Every other edge dims.
        if (edge === activeHover.edgeId) {
          return {
            ...attributes,
            size:
              typeof attributes.size === 'number'
                ? attributes.size * RELATED_EDGE_SIZE_MULTIPLIER
                : attributes.size,
            forceLabel: true,
            zIndex: Z_RELATED_EDGE,
          }
        }

        return {
          ...attributes,
          color: dimColor(attributes.color, DIMMED_EDGE_MIX),
          size: dimmedEdgeSize(attributes.size),
          label: '',
          forceLabel: false,
          zIndex: Z_DIMMED,
        }
      }

      // Edge touching the selected node: keep its edge-type color (set by
      // graphAdapter), bump width modestly, show its label. NEVER recolor to
      // white — that produced the thick white band that hid the label text.
      if (source === selectedId || target === selectedId) {
        return {
          ...attributes,
          size:
            typeof attributes.size === 'number'
              ? attributes.size * RELATED_EDGE_SIZE_MULTIPLIER
              : attributes.size,
          forceLabel: true,
          zIndex: Z_RELATED_EDGE,
        }
      }

      // Edge between two direct neighbors: keep its color, layer it above dim,
      // but blank its label so only the selected node's own relation edges can
      // show labels — prevents a cluster of neighbor-to-neighbor labels.
      if (neighborIds.has(source) && neighborIds.has(target)) {
        return { ...attributes, label: '', forceLabel: false, zIndex: Z_NEIGHBOR_EDGE }
      }

      // Unrelated edge: mix its own relation-type color toward the background
      // so it stays a faint hue-preserving ghost line (never white, never
      // black), drop the label, sink to the bottom layer.
      return {
        ...attributes,
        color: dimColor(attributes.color, DIMMED_EDGE_MIX),
        size: dimmedEdgeSize(attributes.size),
        label: '',
        forceLabel: false,
        zIndex: Z_DIMMED,
      }
    },
  }
}
