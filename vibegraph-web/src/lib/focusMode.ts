import type Graph from 'graphology'

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
        color: '#334155',
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

// Unrelated context is pushed deep into the background with low-alpha colors
// (rgba) rather than a solid dark fill, so dimmed nodes/edges read as faint
// context instead of opaque blobs that sit on top of the focused cluster.
const DIMMED_NODE_COLOR = 'rgba(100, 116, 139, 0.10)'
const DIMMED_EDGE_COLOR = 'rgba(100, 116, 139, 0.05)'

// Related edges keep their edge-type color (set by graphAdapter) — we only bump
// thickness modestly. They are NEVER recolored to white, which previously made
// thick white bands that hid the edge label text.
const RELATED_EDGE_SIZE_MULTIPLIER = 1.6

// zIndex layers (requires Sigma `zIndex: true`). Higher renders on top.
const Z_SELECTED = 3
const Z_NEIGHBOR = 2
const Z_RELATED_EDGE = 2
const Z_NEIGHBOR_EDGE = 1
const Z_DIMMED = 0

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
 * Click-driven neighborhood focus. Unlike createFocusReducers (depth control),
 * this DIMS unrelated nodes/edges instead of hiding them, keeps the selected
 * node and its directly-connected neighbors readable, and emphasizes the edges
 * that touch the selected node. Drives requirement: clicking a node focuses its
 * cluster regardless of the focus-depth filter.
 */
export function createSelectionFocusReducers(selectedId: string | null, graph: Graph): FocusReducers {
  if (!selectedId || !graph.hasNode(selectedId)) {
    return {
      nodeReducer: (_node, attributes) => attributes,
      edgeReducer: (_edge, attributes) => attributes,
    }
  }

  const neighborIds = getDirectNeighbors(graph, selectedId)

  return {
    nodeReducer: (node, attributes) => {
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
        return {
          ...attributes,
          size: typeof attributes.size === 'number' ? attributes.size + 1 : attributes.size,
          forceLabel: true,
          zIndex: Z_NEIGHBOR,
        }
      }

      // Unrelated node: shrink, deep-fade via low-alpha color, drop the label,
      // and sink to the bottom layer so it reads as faint background context.
      return {
        ...attributes,
        size: typeof attributes.size === 'number' ? Math.max(attributes.size * 0.6, 1) : attributes.size,
        color: DIMMED_NODE_COLOR,
        label: '',
        zIndex: Z_DIMMED,
      }
    },
    edgeReducer: (edge, attributes) => {
      const source = graph.source(edge)
      const target = graph.target(edge)

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

      // Edge between two direct neighbors: keep it as-is but layer it above dim.
      if (neighborIds.has(source) && neighborIds.has(target)) {
        return { ...attributes, zIndex: Z_NEIGHBOR_EDGE }
      }

      return { ...attributes, color: DIMMED_EDGE_COLOR, label: '', zIndex: Z_DIMMED }
    },
  }
}
