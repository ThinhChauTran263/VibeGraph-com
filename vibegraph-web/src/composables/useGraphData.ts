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
import type { EdgeType, GraphData, GraphEdge, GraphNode, NodeType } from '@/types/graph'

const emptyStats = <T extends string>(): Record<T, number> => ({}) as Record<T, number>

function countNodes(nodes: GraphNode[]): Record<NodeType, number> {
  return nodes.reduce<Record<NodeType, number>>((stats, node) => {
    stats[node.type] = (stats[node.type] ?? 0) + 1
    return stats
  }, emptyStats<NodeType>())
}

function countEdges(edges: GraphEdge[]): Record<EdgeType, number> {
  return edges.reduce<Record<EdgeType, number>>((stats, edge) => {
    stats[edge.type] = (stats[edge.type] ?? 0) + 1
    return stats
  }, emptyStats<EdgeType>())
}

function mergeGraphData(baseline: GraphData, deep: GraphData): GraphData {
  const nodesById = new Map<string, GraphNode>()
  for (const node of baseline.nodes) nodesById.set(node.id, node)
  for (const node of deep.nodes) nodesById.set(node.id, node)

  const edgesById = new Map<string, GraphEdge>()
  for (const edge of baseline.edges) edgesById.set(edge.id, edge)
  for (const edge of deep.edges) edgesById.set(edge.id, edge)

  const nodes = [...nodesById.values()]
  const edges = [...edgesById.values()]
  return {
    nodes,
    edges,
    nodeStats: countNodes(nodes),
    edgeStats: countEdges(edges),
    meta: deep.meta ?? baseline.meta,
  }
}

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
  const payloadMode = computed(() => store.payloadMode)

  /**
   * Fetch the full graph for a project and store the result.
   * Returns the Graphology instance for Sigma rendering.
   *
   * The backend graph stays in the store (legend counts, "show all"). If the frontend render cap
   * is configured to a positive value, only the subset handed to Sigma is bounded by
   * {@link capGraphData}. {@code store.renderInfo} records whether any capping happened.
   */
  async function loadGraph(projectId: string): Promise<Graph | null> {
    store.isLoading = true
    store.error = null

    try {
      const data = await fetchFullGraph(projectId, { mode: 'baseline' })
      store.projectId = projectId
      store.baselineGraphData = data
      store.deepGraphData = null
      store.payloadMode = 'baseline'
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
   * Lazily fetch raw/deep CPG details and merge them with the baseline architecture
   * payload. The merge preserves baseline-only projections such as File -> File
   * dependencies while making hidden detail types revealable on demand.
   */
  async function ensureDeepGraph(projectId: string): Promise<void> {
    if (store.projectId !== projectId || !store.baselineGraphData) {
      await loadGraph(projectId)
    }
    if (store.payloadMode === 'baseline+deep' && store.deepGraphData) {
      return
    }

    store.isLoading = true
    store.error = null
    try {
      const deep = await fetchFullGraph(projectId, { mode: 'deep' })
      const baseline = store.baselineGraphData ?? deep
      store.projectId = projectId
      store.deepGraphData = deep
      store.graphData = mergeGraphData(baseline, deep)
      store.payloadMode = 'baseline+deep'
      store.payloadMeta = deep.meta ?? baseline.meta ?? null
      bumpGraphVersion()
    } catch (err) {
      store.error = err instanceof Error ? err.message : 'Failed to load deep graph'
    } finally {
      store.isLoading = false
    }
  }

  /**
   * Build a renderable Graphology instance, applying the optional Safe Mode cap and recording the
   * outcome in {@code store.renderInfo}. Truncation is the union of the backend payload cap and
   * the client render cap; the displayed totals reflect the full backend graph so the banner is
   * truthful even when a cap is explicitly enabled.
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
    payloadMode,
    loadGraph,
    ensureDeepGraph,
    buildGraph,
    selectNode,
    clearSelection,
  }
}
