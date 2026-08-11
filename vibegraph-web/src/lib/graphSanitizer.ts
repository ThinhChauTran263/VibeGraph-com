import type {
  GraphData,
  GraphEdge,
  GraphIncrementalEvent,
  GraphNode,
  GraphUpdateEvent,
  NodeType,
} from '@/types/graph'

const REMOVED_NODE_TYPE: NodeType = 'Package'

function countByType<T extends string>(values: T[]): Record<T, number> {
  return values.reduce<Record<T, number>>((stats, value) => {
    stats[value] = (stats[value] ?? 0) + 1
    return stats
  }, {} as Record<T, number>)
}

/** Remove Package nodes before any frontend view or renderer can consume them. */
export function withoutPackageNodes(data: GraphData): GraphData {
  const nodes = data.nodes.filter((node) => node.type !== REMOVED_NODE_TYPE)
  const nodeIds = new Set(nodes.map((node) => node.id))
  const edges = data.edges.filter(
    (edge) => nodeIds.has(edge.source) && nodeIds.has(edge.target),
  )

  return {
    ...data,
    nodes,
    edges,
    nodeStats: countByType(nodes.map((node) => node.type)),
    edgeStats: countByType(edges.map((edge) => edge.type)),
  }
}

/** Keep realtime and expand patches from reintroducing Package nodes. */
export function withoutPackageFromEvent(event: GraphUpdateEvent): GraphUpdateEvent {
  if (event.type === 'FULL_UPDATE') {
    return { ...event, graph: withoutPackageNodes(event.graph) }
  }

  const packageIds = new Set(
    [...(event.added?.nodes ?? []), ...(event.modified?.nodes ?? [])]
      .filter((node) => node.type === REMOVED_NODE_TYPE)
      .map((node) => node.id),
  )
  const filterNodes = (nodes: GraphNode[] | undefined): GraphNode[] | undefined =>
    nodes?.filter((node) => node.type !== REMOVED_NODE_TYPE)
  const filterEdges = (edges: GraphEdge[] | undefined): GraphEdge[] | undefined =>
    edges?.filter((edge) => !packageIds.has(edge.source) && !packageIds.has(edge.target))
  const removedNodeIds = new Set(event.removed?.nodeIds ?? [])
  packageIds.forEach((id) => removedNodeIds.add(id))

  const incremental: GraphIncrementalEvent = {
    ...event,
    added: event.added
      ? { nodes: filterNodes(event.added.nodes), edges: filterEdges(event.added.edges) }
      : undefined,
    modified: event.modified
      ? { nodes: filterNodes(event.modified.nodes), edges: filterEdges(event.modified.edges) }
      : undefined,
    removed: { ...event.removed, nodeIds: [...removedNodeIds] },
  }
  return incremental
}
