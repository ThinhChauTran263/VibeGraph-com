/**
 * Graph data fetching, caching, and state management composable.
 * Wraps the Pinia graph store with fetch logic.
 */

import { computed } from 'vue'
import { useGraphStore } from '@/stores/graph'
import { fetchFullGraph } from '@/lib/api'
import { apiToGraphology } from '@/lib/graphAdapter'
import type Graph from 'graphology'
import type { GraphNode } from '@/types/graph'

export function useGraphData() {
  const store = useGraphStore()

  const nodes = computed(() => store.graphData.nodes)
  const edges = computed(() => store.graphData.edges)
  const loading = computed(() => store.isLoading)
  const error = computed(() => store.error)
  const nodeStats = computed(() => store.graphData.nodeStats)
  const edgeStats = computed(() => store.graphData.edgeStats)
  const selectedNode = computed(() => store.selectedNode)

  /**
   * Fetch the full graph for a project and store the result.
   * Returns the Graphology instance for Sigma rendering.
   */
  async function loadGraph(projectId: string): Promise<Graph | null> {
    store.isLoading = true
    store.error = null

    try {
      const data = await fetchFullGraph(projectId)
      store.graphData = data
      const graph = apiToGraphology(data)
      return graph
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load graph'
      store.error = message
      return null
    } finally {
      store.isLoading = false
    }
  }

  function selectNode(node: GraphNode | null) {
    store.selectedNode = node
  }

  function clearSelection() {
    store.selectedNode = null
  }

  return {
    nodes,
    edges,
    loading,
    error,
    nodeStats,
    edgeStats,
    selectedNode,
    loadGraph,
    selectNode,
    clearSelection,
  }
}
