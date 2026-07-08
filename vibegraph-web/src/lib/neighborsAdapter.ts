/**
 * Converts a backend {@link NodeDetailResponse} (a node + its INCOMING/OUTGOING connections)
 * into a graph fragment ({ nodes, edges }) suitable for incremental merge into the displayed
 * graph. This powers lazy expansion: instead of loading the whole project graph, the UI pulls a
 * single node's neighborhood on demand and merges it in.
 *
 * Edge ids are reconstructed deterministically as {@code source|TYPE|target} (the same convention
 * the backend/full-graph adapter uses), so re-expanding a node never produces duplicate edges.
 */

import type { NodeDetailConnection, NodeDetailResponse } from './api'
import type { GraphEdge, GraphNode, NodeType } from '@/types/graph'
import { EXPAND_MAX_NEIGHBORS } from '@/lib/runtimeConfig'

export interface GraphFragment {
  nodes: GraphNode[]
  edges: GraphEdge[]
  /** True when neighbors were dropped to fit {@link EXPAND_MAX_NEIGHBORS}. */
  truncated: boolean
  /** Total neighbor connections before capping. */
  totalNeighbors: number
}

/**
 * Max neighbors merged in a single lazy expansion. Bounds the freeze risk of double-clicking a
 * high-degree "hub" node (a popular Class/File reached by thousands of callers): without this,
 * one expand could inject thousands of nodes into Sigma and hang the tab — the exact failure the
 * HTTP/WebSocket caps prevent elsewhere. Sourced from `VITE_EXPAND_MAX_NEIGHBORS` (runtimeConfig).
 */
export { EXPAND_MAX_NEIGHBORS }

/** Map a backend NodeDto-shaped object to a frontend GraphNode (filling nullable fields). */
function toGraphNode(n: NodeDetailResponse['node']): GraphNode {
  return {
    id: n.id,
    type: n.type as NodeType,
    name: n.name,
    fullName: n.fullName,
    filePath: n.filePath,
    lineNumber: n.lineNumber ?? 0,
    properties: n.properties ?? {},
  }
}

function edgeId(source: string, type: string, target: string): string {
  return `${source}|${type}|${target}`
}

interface OrientedConnection {
  conn: NodeDetailConnection
  incoming: boolean
}

/**
 * Build the graph fragment for an expanded node. Includes the center node and up to
 * {@code limit} connected nodes (one edge per kept connection, oriented by direction).
 * Connections are ordered deterministically (by counterpart id, then type, then direction) before
 * capping so the result is stable and re-expansion never duplicates edges. Connections with a
 * blank counterpart id are skipped defensively.
 */
export function neighborsToFragment(
  detail: NodeDetailResponse,
  limit: number = EXPAND_MAX_NEIGHBORS,
): GraphFragment {
  const center = toGraphNode(detail.node)

  const all: OrientedConnection[] = []
  for (const conn of detail.incoming ?? []) all.push({ conn, incoming: true })
  for (const conn of detail.outgoing ?? []) all.push({ conn, incoming: false })

  const valid = all.filter((x) => x.conn.otherNode && x.conn.otherNode.id)
  valid.sort((a, b) => {
    const ai = a.conn.otherNode.id
    const bi = b.conn.otherNode.id
    if (ai !== bi) return ai < bi ? -1 : 1
    if (a.conn.relationshipType !== b.conn.relationshipType) {
      return a.conn.relationshipType < b.conn.relationshipType ? -1 : 1
    }
    return a.incoming === b.incoming ? 0 : a.incoming ? -1 : 1
  })

  const totalNeighbors = valid.length
  const kept = limit > 0 && totalNeighbors > limit ? valid.slice(0, limit) : valid

  const nodes: GraphNode[] = [center]
  const edges: GraphEdge[] = []
  for (const { conn, incoming } of kept) {
    const other = conn.otherNode
    nodes.push(toGraphNode(other))
    const source = incoming ? other.id : center.id
    const target = incoming ? center.id : other.id
    edges.push({
      id: edgeId(source, conn.relationshipType, target),
      source,
      target,
      type: conn.relationshipType as GraphEdge['type'],
    })
  }

  return { nodes, edges, truncated: kept.length < totalNeighbors, totalNeighbors }
}
