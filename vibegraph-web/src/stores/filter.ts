import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { NodeType, EdgeType } from '@/types/graph'

/**
 * Filter store - manages visibility toggles for node/edge types.
 */
export const useFilterStore = defineStore('filter', () => {
  const visibleNodeTypes = ref<Set<NodeType>>(new Set())
  const visibleEdgeTypes = ref<Set<EdgeType>>(new Set())
  const focusDepth = ref<number>(-1) // -1 = All, 1-5 = hops
  const searchQuery = ref('')

  // TODO: Implement toggle actions

  return {
    visibleNodeTypes,
    visibleEdgeTypes,
    focusDepth,
    searchQuery,
  }
})
