import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { GraphData, GraphMeta, GraphNode, NodeType, EdgeType } from '@/types/graph'

export type GraphPayloadMode = 'baseline' | 'baseline+deep'

const emptyStats = <T extends string>(): Record<T, number> => ({}) as Record<T, number>

/**
 * Graph data store - manages nodes, edges, stats, and selection state.
 */
export const useGraphStore = defineStore('graph', () => {
  const graphData = ref<GraphData>({
    nodes: [],
    edges: [],
    nodeStats: emptyStats<NodeType>(),
    edgeStats: emptyStats<EdgeType>(),
  })
  const selectedNode = ref<GraphNode | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const projectId = ref<string | null>(null)
  const baselineGraphData = ref<GraphData | null>(null)
  const deepGraphData = ref<GraphData | null>(null)
  const payloadMode = ref<GraphPayloadMode>('baseline')

  /**
   * Backend payload guardrail metadata from the last fetch (null until first load, or when the
   * backend did not attach it). Held separately from {@code graphData} because client-side
   * filtering rebuilds graphData without the meta.
   */
  const payloadMeta = ref<GraphMeta | null>(null)

  /**
   * Render budget info for the last built graph. Drives the "Safe Mode" banner when
   * the graph was too large to render in full. Null until the first graph is built.
   */
  const renderInfo = ref<{
    truncated: boolean
    renderedNodes: number
    totalNodes: number
    renderedEdges: number
    totalEdges: number
  } | null>(null)

  function reset() {
    graphData.value = {
      nodes: [],
      edges: [],
      nodeStats: emptyStats<NodeType>(),
      edgeStats: emptyStats<EdgeType>(),
    }
    selectedNode.value = null
    error.value = null
    renderInfo.value = null
    payloadMeta.value = null
    projectId.value = null
    baselineGraphData.value = null
    deepGraphData.value = null
    payloadMode.value = 'baseline'
  }

  return {
    graphData,
    selectedNode,
    isLoading,
    error,
    renderInfo,
    payloadMeta,
    projectId,
    baselineGraphData,
    deepGraphData,
    payloadMode,
    reset,
  }
})
