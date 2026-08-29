import type {
  GraphData,
  GraphEdge,
  GraphIncrementalEvent,
  GraphNode,
  GraphUpdateEvent,
  NodeType,
} from '@/types/graph'

const REMOVED_NODE_TYPE: NodeType = 'Package'
const REMOVED_DETAIL_NODE_TYPES: ReadonlySet<NodeType> = new Set<NodeType>([
  'Field',
  'LocalVariable',
])

function isRemovedNodeType(type: NodeType): boolean {
  return type === REMOVED_NODE_TYPE || REMOVED_DETAIL_NODE_TYPES.has(type)
}

function countByType<T extends string>(values: T[]): Record<T, number> {
  return values.reduce<Record<T, number>>(
    (stats, value) => {
      stats[value] = (stats[value] ?? 0) + 1
      return stats
    },
    {} as Record<T, number>,
  )
}

/** Remove frontend-excluded nodes and every edge incident to them. */
export function withoutPackageNodes(data: GraphData): GraphData {
  const nodes = data.nodes.filter((node) => !isRemovedNodeType(node.type))
  const nodeIds = new Set(nodes.map((node) => node.id))
  const edges = data.edges.filter((edge) => nodeIds.has(edge.source) && nodeIds.has(edge.target))

  return {
    ...data,
    nodes,
    edges,
    nodeStats: countByType(nodes.map((node) => node.type)),
    edgeStats: countByType(edges.map((edge) => edge.type)),
  }
}

/** Keep realtime and expand patches from reintroducing frontend-excluded nodes. */
export function withoutPackageFromEvent(event: GraphUpdateEvent): GraphUpdateEvent {
  if (event.type === 'FULL_UPDATE') {
    return { ...event, graph: withoutPackageNodes(event.graph) }
  }

  const excludedNodeIds = new Set(
    [...(event.added?.nodes ?? []), ...(event.modified?.nodes ?? [])]
      .filter((node) => isRemovedNodeType(node.type))
      .map((node) => node.id),
  )
  const filterNodes = (nodes: GraphNode[] | undefined): GraphNode[] | undefined =>
    nodes?.filter((node) => !isRemovedNodeType(node.type))
  const filterEdges = (edges: GraphEdge[] | undefined): GraphEdge[] | undefined =>
    edges?.filter((edge) => !excludedNodeIds.has(edge.source) && !excludedNodeIds.has(edge.target))
  const removedNodeIds = new Set(event.removed?.nodeIds ?? [])
  excludedNodeIds.forEach((id) => removedNodeIds.add(id))

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
