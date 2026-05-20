/**
 * Graph adapter - converts API response to Sigma.js / Graphology format.
 *
 * TODO:
 * - apiToGraphology(GraphDataResponse): Graph
 * - applyNodeStyle(node): node attributes (color, size based on type)
 * - applyEdgeStyle(edge): edge attributes (color, label)
 */

import type { GraphData } from '@/types/graph'

export function apiToGraphology(data: GraphData) {
  // TODO: Convert API data to Graphology graph instance
}

export function getNodeStyle(nodeType: string) {
  // TODO: Return Sigma.js node style based on type
}

export function getEdgeStyle(edgeType: string) {
  // TODO: Return Sigma.js edge style based on type
}
