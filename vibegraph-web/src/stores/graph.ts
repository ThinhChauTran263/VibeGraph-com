import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { GraphData, GraphNode } from '@/types/graph'

/**
 * Graph data store - manages nodes, edges, and selection state.
 */
export const useGraphStore = defineStore('graph', () => {
  const graphData = ref<GraphData>({ nodes: [], edges: [] })
  const selectedNode = ref<GraphNode | null>(null)
  const isLoading = ref(false)

  // TODO: Implement actions (fetchGraph, selectNode, clearSelection)

  return {
    graphData,
    selectedNode,
    isLoading,
  }
})
