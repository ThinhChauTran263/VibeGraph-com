/**
 * Graph data fetching, caching, and state management composable.
 * Wraps the Pinia graph store with fetch logic.
 */

import { computed } from 'vue'
import { useGraphStore } from '@/stores/graph'
import { fetchFullGraph } from '@/lib/api'
import { apiToGraphology } from '@/lib/graphAdapter'
import { useFilters } from '@/composables/useFilters'
import type Graph from 'graphology'
import type { GraphData, GraphNode } from '@/types/graph'

export function useGraphData() {
  const store = useGraphStore()
  const filters = useFilters()

  const filteredGraphData = computed(() => filters.applyFilters(store.graphData))
  const nodes = computed(() => filteredGraphData.value.nodes)
  const edges = computed(() => filteredGraphData.value.edges)
  const graphData = computed(() => store.graphData)
  const loading = computed(() => store.isLoading)
  const error = computed(() => store.error)
  const nodeStats = computed(() => filteredGraphData.value.nodeStats)
  const edgeStats = computed(() => filteredGraphData.value.edgeStats)
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
      return apiToGraphology(filters.applyFilters(data))
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load graph'
      store.error = message
      return null
    } finally {
      store.isLoading = false
    }
  }

  function buildGraph(data: GraphData = filteredGraphData.value): Graph {
    return apiToGraphology(data)
  }

  function selectNode(node: GraphNode | null) {
    store.selectedNode = node
  }

  function clearSelection() {
    store.selectedNode = null
  }

  return {
    graphData,
    filteredGraphData,
    nodes,
    edges,
    loading,
    error,
    nodeStats,
    edgeStats,
    selectedNode,
    loadGraph,
    buildGraph,
    selectNode,
    clearSelection,
  }
}
