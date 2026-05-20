/**
 * Focus mode logic - Sigma.js node and edge reducers.
 *
 * When user selects a node:
 * - Highlight selected node + N-hop neighbors
 * - Dim all other nodes (opacity 0.1-0.2)
 * - Hide all edges not connected to selected node
 *
 * TODO:
 * - createNodeReducer(selectedId, depth, graph)
 * - createEdgeReducer(selectedId, graph)
 * - getNeighborsWithinHops(graph, nodeId, hops)
 */

export function getNeighborsWithinHops(graph: unknown, nodeId: string, hops: number): Set<string> {
  // TODO: BFS to find N-hop neighbors
  return new Set<string>()
}

export function createFocusReducers(selectedId: string | null, depth: number, graph: unknown) {
  // TODO: Return { nodeReducer, edgeReducer }
}
