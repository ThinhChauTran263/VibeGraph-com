/**
 * Graph data fetching, caching, and state management composable.
 * Wraps the Pinia graph store with fetch logic.
 */

import { computed } from 'vue'
import { useGraphStore } from '@/stores/graph'
import { fetchFullGraph } from '@/lib/api'
import { apiToGraphology } from '@/lib/graphAdapter'
import { capGraphData } from '@/lib/graphCap'
import { useFilters } from '@/composables/useFilters'
import { bumpGraphVersion } from '@/lib/graphVersion'
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
  const renderInfo = computed(() => store.renderInfo)

  /**
   * Fetch the full graph for a project and store the result.
   * Returns the Graphology instance for Sigma rendering.
   *
 * The FULL backend graph stays in the store (legend counts, "show all"); the subset
 * handed to Sigma is only bounded by {@link capGraphData} when Safe Mode is enabled
 * via env. {@code store.renderInfo} records whether that capping happened.
   */
  async function loadGraph(projectId: string): Promise<Graph | null> {
    store.isLoading = true
    store.error = null

    try {
      const data = await fetchFullGraph(projectId)
      store.graphData = data
      store.payloadMeta = data.meta ?? null
      // Signal derived views (diagrams) that the graph changed, so their caches revalidate.
      bumpGraphVersion()
      return buildGraph(filters.applyFilters(data))
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load graph'
      store.error = message
      return null
    } finally {
      store.isLoading = false
    }
  }

  /**
   * Build a renderable Graphology instance, applying the optional Safe Mode cap and recording
   * the outcome in {@code store.renderInfo}. Truncation is the union of the backend payload cap
   * and the client render cap; the displayed totals reflect the FULL backend graph so the banner
   * is truthful even when both layers reduce the graph.
   */
  function buildGraph(data: GraphData = filteredGraphData.value): Graph {
    const capped = capGraphData(data)
    const backendMeta = store.payloadMeta
    const backendTruncated = backendMeta?.truncated ?? false
    store.renderInfo = {
      truncated: capped.truncated || backendTruncated,
      renderedNodes: capped.renderedNodes,
      totalNodes: backendMeta?.totalNodes ?? capped.totalNodes,
      renderedEdges: capped.renderedEdges,
      totalEdges: backendMeta?.totalEdges ?? capped.totalEdges,
    }
    return apiToGraphology(capped.data)
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
    renderInfo,
    loadGraph,
    buildGraph,
    selectNode,
    clearSelection,
  }
}
