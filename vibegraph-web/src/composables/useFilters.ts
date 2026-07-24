import { computed } from 'vue'
import { useFilterStore } from '@/stores/filter'
import { filterGraphData } from '@/lib/graphFilters'
import type { GraphData } from '@/types/graph'

export function useFilters() {
  const store = useFilterStore()

  function applyFilters(data: GraphData): GraphData {
    return filterGraphData(data, {
      hiddenNodeTypes: store.hiddenNodeTypes,
      hiddenEdgeTypes: store.hiddenEdgeTypes,
      hideIsolatedNodes: store.hideIsolatedNodes,
    })
  }

  return {
    hiddenNodeTypes: computed(() => store.hiddenNodeTypes),
    hiddenEdgeTypes: computed(() => store.hiddenEdgeTypes),
    hideIsolatedNodes: computed(() => store.hideIsolatedNodes),
    searchQuery: computed(() => store.searchQuery),
    hasActiveFilters: computed(() => store.hasActiveFilters),
    toggleNodeType: store.toggleNodeType,
    toggleEdgeType: store.toggleEdgeType,
    toggleIsolatedNodes: store.toggleIsolatedNodes,
    showAllNodeTypes: store.showAllNodeTypes,
    showAllEdgeTypes: store.showAllEdgeTypes,
    reset: store.reset,
    applyFilters,
  }
}
