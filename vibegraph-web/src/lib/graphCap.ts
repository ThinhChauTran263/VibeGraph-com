/**
 * Optional graph capping ("Safe Mode") - bounds how many nodes are handed to the renderer only
 * when `GRAPH_SAFE_NODE_LIMIT` is positive.
 *
 * The freeze/crash on heavy projects can come from rendering the ENTIRE graph (tens of
 * thousands of nodes) on the browser main thread + WebGL. When enabled, this module keeps only
 * the most architecturally meaningful nodes once the graph exceeds a threshold, so the canvas
 * stays responsive. With the default `0` limit, the renderer receives the full frontend graph.
 *
 * Selection strategy when capping:
 *   1. node-type priority (Project/Package/File/Class first, Field/LocalVariable last)
 *   2. then node degree (better-connected, more central nodes win)
 *   3. then id (deterministic, stable output)
 * Edges are pruned to those whose BOTH endpoints survived.
 */

import type { GraphData, GraphNode, NodeType } from '@/types/graph'
import { GRAPH_SAFE_NODE_LIMIT } from '@/lib/runtimeConfig'

/**
 * Default render budget. `0` disables Safe Mode; above a positive value the graph enters Safe
 * Mode and only the top-ranked nodes are rendered. Sourced from `VITE_GRAPH_SAFE_NODE_LIMIT`.
 */
export { GRAPH_SAFE_NODE_LIMIT }

/** Higher = kept first when capping. Structural/architectural nodes outrank leaves. */
const NODE_TYPE_PRIORITY: Record<NodeType, number> = {
  Project: 100,
  Package: 90,
  File: 80,
  Class: 70,
  Interface: 70,
  Enum: 65,
  Record: 65,
  DBModel: 65,
  Route: 60,
  APIEndpoint: 60,
  Method: 40,
  Constructor: 40,
  Field: 20,
  Annotation: 15,
  External: 10,
  LocalVariable: 5,
}

export interface GraphCapResult {
  /** Bounded graph data safe to render. Identical reference semantics to the input shape. */
  data: GraphData
  /** True when nodes were dropped to fit the limit (Safe Mode active). */
  truncated: boolean
  /** Number of nodes actually rendered. */
  renderedNodes: number
  /** Total nodes before capping. */
  totalNodes: number
  /** Number of edges actually rendered. */
  renderedEdges: number
  /** Total edges before capping. */
  totalEdges: number
}

function nodeTypePriority(type: NodeType): number {
  return NODE_TYPE_PRIORITY[type] ?? 0
}

/**
 * Cap the graph to at most {@link limit} nodes, keeping the most meaningful ones.
 * Returns the original data untouched when it already fits.
 */
export function capGraphData(
  data: GraphData,
  limit: number = GRAPH_SAFE_NODE_LIMIT,
): GraphCapResult {
  const totalNodes = data.nodes.length
  const totalEdges = data.edges.length

  if (limit <= 0 || totalNodes <= limit) {
    return {
      data,
      truncated: false,
      renderedNodes: totalNodes,
      totalNodes,
      renderedEdges: totalEdges,
      totalEdges,
    }
  }

  // Degree from the (already filtered) edge set drives centrality within a type tier.
  const degree = new Map<string, number>()
  for (const edge of data.edges) {
    degree.set(edge.source, (degree.get(edge.source) ?? 0) + 1)
    degree.set(edge.target, (degree.get(edge.target) ?? 0) + 1)
  }

  const ranked = [...data.nodes].sort((a, b) => {
    const pa = nodeTypePriority(a.type)
    const pb = nodeTypePriority(b.type)
    if (pb !== pa) return pb - pa
    const da = degree.get(a.id) ?? 0
    const db = degree.get(b.id) ?? 0
    if (db !== da) return db - da
    return a.id < b.id ? -1 : a.id > b.id ? 1 : 0
  })

  const keptNodes: GraphNode[] = ranked.slice(0, limit)
  const keptIds = new Set(keptNodes.map((n) => n.id))
  const keptEdges = data.edges.filter((e) => keptIds.has(e.source) && keptIds.has(e.target))

  return {
    data: {
      nodes: keptNodes,
      edges: keptEdges,
      nodeStats: data.nodeStats,
      edgeStats: data.edgeStats,
    },
    truncated: true,
    renderedNodes: keptNodes.length,
    totalNodes,
    renderedEdges: keptEdges.length,
    totalEdges,
  }
}
