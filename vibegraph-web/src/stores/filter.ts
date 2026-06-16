import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { normalizeFocusDepth } from '@/lib/focusMode'
import type { NodeType, EdgeType } from '@/types/graph'

const cloneSet = <T>(values: Set<T>): Set<T> => new Set(values)

/**
 * Compute the next HIDDEN set for an "isolate with restore" toggle.
 *
 * Behaviour (per type group):
 *  - All visible (nothing hidden): clicking a type ISOLATES it — hide every other
 *    available type, leaving only the clicked one.
 *  - Already isolating, clicking a hidden type: ADD it to the visible set (keep the
 *    others that are open).
 *  - Already isolating, clicking a visible type while others are also visible:
 *    REMOVE it from the visible set (hide it).
 *  - Already isolating, clicking the ONLY visible type: RESTORE — show everything
 *    again.
 */
function nextIsolateHiddenSet<T>(hidden: Set<T>, type: T, available: readonly T[]): Set<T> {
  const all = new Set<T>(available)
  all.add(type)

  const visibleCount = [...all].reduce((count, t) => count + (hidden.has(t) ? 0 : 1), 0)
  const allVisible = visibleCount === all.size

  // All visible -> isolate the clicked type (hide every other available type).
  if (allVisible) {
    const next = new Set<T>(all)
    next.delete(type)
    return next
  }

  // Clicked type is currently hidden -> reveal it (keep what's already open).
  if (hidden.has(type)) {
    const next = cloneSet(hidden)
    next.delete(type)
    return next
  }

  // Clicked type is the only visible one -> restore all.
  if (visibleCount === 1) {
    return new Set<T>()
  }

  // Clicked type is visible alongside others -> hide just this one.
  const next = cloneSet(hidden)
  next.add(type)
  return next
}

export const useFilterStore = defineStore('filter', () => {
  const hiddenNodeTypes = ref<Set<NodeType>>(new Set())
  const hiddenEdgeTypes = ref<Set<EdgeType>>(new Set())
  const focusDepth = ref<number>(-1)
  const searchQuery = ref('')

  const hasActiveFilters = computed(
    () => hiddenNodeTypes.value.size > 0 || hiddenEdgeTypes.value.size > 0,
  )

  function toggleNodeType(type: NodeType, available: readonly NodeType[] = []): void {
    hiddenNodeTypes.value = nextIsolateHiddenSet(hiddenNodeTypes.value, type, available)
  }

  function toggleEdgeType(type: EdgeType, available: readonly EdgeType[] = []): void {
    hiddenEdgeTypes.value = nextIsolateHiddenSet(hiddenEdgeTypes.value, type, available)
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
