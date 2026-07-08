/**
 * Pure helpers for the realtime graph-update consumer (T60).
 *
 * - `parseGraphUpdateEvent` validates an untrusted WebSocket payload at the
 *   boundary and narrows it to a typed {@link GraphUpdateEvent}, or returns
 *   `null` for anything malformed.
 * - `applyGraphUpdate` produces a NEW {@link GraphData} by applying the event
 *   immutably (it never mutates the input or its nested arrays/objects) and
 *   recomputes `nodeStats`/`edgeStats` so the store invariant holds.
 *
 * The backend producer (T36 broadcast + FileChangeBroadcaster, wired to the file watcher)
 * is implemented; this mirrors that contract and still validates defensively at the boundary.
 */

import type {
  EdgeType,
  GraphData,
  GraphEdge,
  GraphIncrementalEvent,
  GraphNode,
  GraphUpdateEvent,
  NodeType,
} from '@/types/graph'

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0
}

/** A node is structurally valid for patching if it carries a string `id`. */
function isGraphNode(value: unknown): value is GraphNode {
  return isObject(value) && isNonEmptyString(value.id)
}

/** An edge is structurally valid for patching if it carries `id`/`source`/`target`. */
function isGraphEdge(value: unknown): value is GraphEdge {
  return (
    isObject(value) &&
    isNonEmptyString(value.id) &&
    isNonEmptyString(value.source) &&
    isNonEmptyString(value.target)
  )
}

function isArrayOf<T>(value: unknown, guard: (item: unknown) => item is T): value is T[] {
  return Array.isArray(value) && value.every(guard)
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
}

/**
 * Validate an optional `{ nodes?, edges? }` bucket. Returns `true` when the
 * bucket is absent or when every present field is a well-formed array.
 */
function isValidNodeEdgeBucket(value: unknown): boolean {
  if (value === undefined) return true
  if (!isObject(value)) return false
  if (value.nodes !== undefined && !isArrayOf(value.nodes, isGraphNode)) return false
  if (value.edges !== undefined && !isArrayOf(value.edges, isGraphEdge)) return false
  return true
}

function isValidRemovedBucket(value: unknown): boolean {
  if (value === undefined) return true
  if (!isObject(value)) return false
  if (value.nodeIds !== undefined && !isStringArray(value.nodeIds)) return false
  if (value.edgeIds !== undefined && !isStringArray(value.edgeIds)) return false
  return true
}

/**
 * Validate and narrow an untrusted payload to a {@link GraphUpdateEvent}.
 * Returns `null` for any structurally invalid payload (the caller should treat
 * `null` as "ignore + report").
 */
export function parseGraphUpdateEvent(payload: unknown): GraphUpdateEvent | null {
  if (!isObject(payload)) return null
  if (!isNonEmptyString(payload.projectId)) return null

  if (payload.type === 'FULL_UPDATE') {
    const graph = payload.graph
    if (!isObject(graph)) return null
    if (!isArrayOf(graph.nodes, isGraphNode)) return null
    if (!isArrayOf(graph.edges, isGraphEdge)) return null
    return {
      type: 'FULL_UPDATE',
      projectId: payload.projectId,
      graph: graph as unknown as GraphData,
    }
  }

  if (payload.type === 'INCREMENTAL') {
    if (!isValidNodeEdgeBucket(payload.added)) return null
    if (!isValidNodeEdgeBucket(payload.modified)) return null
    if (!isValidRemovedBucket(payload.removed)) return null
    return payload as unknown as GraphIncrementalEvent
  }

  return null
}

function computeNodeStats(nodes: GraphNode[]): Record<NodeType, number> {
  const stats = {} as Record<NodeType, number>
  for (const node of nodes) {
    stats[node.type] = (stats[node.type] ?? 0) + 1
  }
  return stats
}

function computeEdgeStats(edges: GraphEdge[]): Record<EdgeType, number> {
  const stats = {} as Record<EdgeType, number>
  for (const edge of edges) {
    stats[edge.type] = (stats[edge.type] ?? 0) + 1
  }
  return stats
}

function withStats(nodes: GraphNode[], edges: GraphEdge[]): GraphData {
  return {
    nodes,
    edges,
    nodeStats: computeNodeStats(nodes),
    edgeStats: computeEdgeStats(edges),
  }
}

/**
 * Apply an incremental patch immutably. Order: remove → add (upsert) →
 * modify (upsert). Edges whose endpoints were removed are also dropped so the
 * resulting graph never references missing nodes.
 */
function applyIncremental(current: GraphData, event: GraphIncrementalEvent): GraphData {
  const removedNodeIds = new Set(event.removed?.nodeIds ?? [])
  const removedEdgeIds = new Set(event.removed?.edgeIds ?? [])

  // Build ordered node map (preserves insertion order, dedupes by id).
  const nodeMap = new Map<string, GraphNode>()
  for (const node of current.nodes) {
    if (!removedNodeIds.has(node.id)) nodeMap.set(node.id, node)
  }
  for (const node of event.added?.nodes ?? []) {
    if (!removedNodeIds.has(node.id)) nodeMap.set(node.id, node)
  }
  for (const node of event.modified?.nodes ?? []) {
    if (!removedNodeIds.has(node.id)) nodeMap.set(node.id, node)
  }

  const edgeMap = new Map<string, GraphEdge>()
  for (const edge of current.edges) {
    if (!removedEdgeIds.has(edge.id)) edgeMap.set(edge.id, edge)
  }
  for (const edge of event.added?.edges ?? []) {
    if (!removedEdgeIds.has(edge.id)) edgeMap.set(edge.id, edge)
  }
  for (const edge of event.modified?.edges ?? []) {
    if (!removedEdgeIds.has(edge.id)) edgeMap.set(edge.id, edge)
  }

  const nodes = [...nodeMap.values()]
  // Drop edges that dangle after node removals to keep the graph consistent.
  const presentNodeIds = new Set(nodes.map((n) => n.id))
  const edges = [...edgeMap.values()].filter(
    (edge) => presentNodeIds.has(edge.source) && presentNodeIds.has(edge.target),
  )

  return withStats(nodes, edges)
}

/**
 * Apply a validated {@link GraphUpdateEvent} to the current graph, returning a
 * brand-new {@link GraphData}. The input is never mutated.
 */
export function applyGraphUpdate(current: GraphData, event: GraphUpdateEvent): GraphData {
  if (event.type === 'FULL_UPDATE') {
    // Recompute stats from the authoritative node/edge arrays so the store
    // invariant holds even if the producer sent stale/missing stats.
    return withStats([...event.graph.nodes], [...event.graph.edges])
  }
  return applyIncremental(current, event)
}
