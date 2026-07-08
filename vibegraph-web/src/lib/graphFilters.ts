import type { EdgeType, GraphData, GraphEdge, GraphNode, NodeType } from '@/types/graph'
import { DEFAULT_HIDDEN_EDGE_TYPES, DEFAULT_HIDDEN_NODE_TYPES } from './constants'

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
 * The edge types hidden in the DEFAULT view. CPG-lite relationships
 * (TYPE_OF, PARAMETER_TYPE, RETURNS, THROWS, HAS_FIELD, INJECTS) are kept in the
 * data but hidden by default so the architecture graph stays readable; they are
 * revealed via the Edge Types "Show all" button. Returns a fresh mutable set so
 * the filter store can own its copy.
 */
export function defaultHiddenEdgeTypes(): Set<EdgeType> {
  return new Set<EdgeType>(DEFAULT_HIDDEN_EDGE_TYPES)
}

/**
 * The node types hidden in the DEFAULT view. Currently `LocalVariable` (deep CPG),
 * which is only present when the backend deep-cpg flag is enabled. Returns a fresh
 * mutable set so the filter store can own its copy.
 */
export function defaultHiddenNodeTypes(): Set<NodeType> {
  return new Set<NodeType>(DEFAULT_HIDDEN_NODE_TYPES)
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
