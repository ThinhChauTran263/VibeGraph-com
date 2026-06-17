import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { defaultHiddenEdgeTypes, defaultHiddenNodeTypes } from '@/lib/graphFilters'
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
  // LocalVariable (deep CPG) starts HIDDEN so the default graph stays readable.
  const hiddenNodeTypes = ref<Set<NodeType>>(defaultHiddenNodeTypes())
  // CPG-lite edge types start HIDDEN so the default architecture graph stays
  // readable. They remain in the data and are revealed via "Show all".
  const hiddenEdgeTypes = ref<Set<EdgeType>>(defaultHiddenEdgeTypes())
  const searchQuery = ref('')

  /** True when a hidden set deviates from its default-hidden baseline. */
  function deviatesFromDefault<T>(hidden: ReadonlySet<T>, defaults: ReadonlySet<T>): boolean {
    if (hidden.size !== defaults.size) return true
    for (const value of hidden) {
      if (!defaults.has(value)) return true
    }
    return false
  }

  // "Active filters" means the user deviated from the defaults (node or edge
  // visibility). The default-hidden deep-CPG types alone do NOT count as active
  // (otherwise Reset would always appear enabled).
  const hasActiveFilters = computed(
    () =>
      deviatesFromDefault(hiddenNodeTypes.value, defaultHiddenNodeTypes()) ||
      deviatesFromDefault(hiddenEdgeTypes.value, defaultHiddenEdgeTypes()),
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

  function reset(): void {
    hiddenNodeTypes.value = defaultHiddenNodeTypes()
    // Reset returns to the readable DEFAULT (deep-CPG hidden), not "show all".
    hiddenEdgeTypes.value = defaultHiddenEdgeTypes()
    searchQuery.value = ''
  }

  return {
    hiddenNodeTypes,
    hiddenEdgeTypes,
    hasActiveFilters,
    searchQuery,
    toggleNodeType,
    toggleEdgeType,
    showAllNodeTypes,
    showAllEdgeTypes,
    reset,
  }
})
