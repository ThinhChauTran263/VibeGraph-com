import type { EdgeType, GraphData, GraphEdge, GraphNode, NodeType } from '@/types/graph'
import { ALLOWED_EDGE_TYPES } from './constants'

export interface GraphFilterState {
  hiddenNodeTypes: ReadonlySet<NodeType>
  hiddenEdgeTypes: ReadonlySet<EdgeType>
}

function countByType<T extends string>(values: T[]): Record<T, number> {
  return values.reduce<Record<T, number>>((counts, type) => {
    return {
      ...counts,
      [type]: (counts[type] ?? 0) + 1,
    }
  }, {} as Record<T, number>)
}

/**
 * Drop edges whose type is not in {@link ALLOWED_EDGE_TYPES} and recompute
 * `edgeStats` so the graph render, the Edge Types legend, and the Node Detail
 * relations all stay consistent. Applied once at ingestion (see useGraphData),
 * so disallowed types never reach the store. Returns the input unchanged when
 * nothing is filtered out.
 */
export function sanitizeAllowedEdgeTypes(data: GraphData): GraphData {
  const edges = data.edges.filter((edge) => ALLOWED_EDGE_TYPES.has(edge.type))
  if (edges.length === data.edges.length) return data
  return {
    ...data,
    edges,
    edgeStats: countByType(edges.map((edge: GraphEdge) => edge.type)),
  }
}

export function filterGraphData(data: GraphData, filters: GraphFilterState): GraphData {
  const nodes = data.nodes.filter((node) => !filters.hiddenNodeTypes.has(node.type))
  const visibleNodeIds = new Set(nodes.map((node) => node.id))
  const edges = data.edges.filter((edge) => {
    return (
      !filters.hiddenEdgeTypes.has(edge.type) &&
      visibleNodeIds.has(edge.source) &&
      visibleNodeIds.has(edge.target)
    )
  })

  return {
    nodes,
    edges,
    nodeStats: countByType(nodes.map((node: GraphNode) => node.type)),
    edgeStats: countByType(edges.map((edge: GraphEdge) => edge.type)),
  }
}
