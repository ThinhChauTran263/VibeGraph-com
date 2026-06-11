import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { normalizeFocusDepth } from '@/lib/focusMode'
import type { NodeType, EdgeType } from '@/types/graph'

const cloneSet = <T>(values: Set<T>): Set<T> => new Set(values)

export const useFilterStore = defineStore('filter', () => {
  const hiddenNodeTypes = ref<Set<NodeType>>(new Set())
  const hiddenEdgeTypes = ref<Set<EdgeType>>(new Set())
  const focusDepth = ref<number>(-1)
  const searchQuery = ref('')

  const hasActiveFilters = computed(
    () => hiddenNodeTypes.value.size > 0 || hiddenEdgeTypes.value.size > 0,
  )

  function toggleNodeType(type: NodeType): void {
    const next = cloneSet(hiddenNodeTypes.value)
    if (next.has(type)) {
      next.delete(type)
    } else {
      next.add(type)
    }
    hiddenNodeTypes.value = next
  }

  function toggleEdgeType(type: EdgeType): void {
    const next = cloneSet(hiddenEdgeTypes.value)
    if (next.has(type)) {
      next.delete(type)
    } else {
      next.add(type)
    }
    hiddenEdgeTypes.value = next
  }

  function showAllNodeTypes(): void {
    hiddenNodeTypes.value = new Set()
  }

  function showAllEdgeTypes(): void {
    hiddenEdgeTypes.value = new Set()
  }

  function setFocusDepth(depth: number): void {
    focusDepth.value = normalizeFocusDepth(depth)
  }

  function reset(): void {
    hiddenNodeTypes.value = new Set()
    hiddenEdgeTypes.value = new Set()
    focusDepth.value = -1
    searchQuery.value = ''
  }

  return {
    hiddenNodeTypes,
    hiddenEdgeTypes,
    hasActiveFilters,
    focusDepth,
    searchQuery,
    toggleNodeType,
    toggleEdgeType,
    showAllNodeTypes,
    showAllEdgeTypes,
    setFocusDepth,
    reset,
  }
})
