import type { EdgeType, GraphData, GraphEdge, GraphNode, NodeType } from '@/types/graph'

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
