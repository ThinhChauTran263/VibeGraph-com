import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { GraphData, GraphNode, NodeType, EdgeType } from '@/types/graph'

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

  function reset() {
    graphData.value = {
      nodes: [],
      edges: [],
      nodeStats: emptyStats<NodeType>(),
      edgeStats: emptyStats<EdgeType>(),
    }
    selectedNode.value = null
    error.value = null
  }

  return {
    graphData,
    selectedNode,
    isLoading,
    error,
    reset,
  }
})
