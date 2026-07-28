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
function setEquals<T>(left: ReadonlySet<T>, right: ReadonlySet<T>): boolean {
  if (left.size !== right.size) return false
  for (const value of left) {
    if (!right.has(value)) return false
  }
  return true
}

function intersection<T>(values: ReadonlySet<T>, allowed: ReadonlySet<T>): Set<T> {
  const result = new Set<T>()
  for (const value of values) {
    if (allowed.has(value)) result.add(value)
  }
  return result
}

function nextIsolateHiddenSet<T>(
  hidden: Set<T>,
  type: T,
  available: readonly T[],
  defaultHidden: ReadonlySet<T> = new Set<T>(),
): Set<T> {
  const all = new Set<T>(available)
  all.add(type)

  const visibleCount = [...all].reduce((count, t) => count + (hidden.has(t) ? 0 : 1), 0)
  const allVisible = visibleCount === all.size
  const atDefaultHiddenState = setEquals(intersection(hidden, all), intersection(defaultHidden, all))

  // Default baseline, or all visible after Show all -> isolate the clicked type.
  // Preserve hidden types outside the current inventory so project switches do not leak state.
  if ((atDefaultHiddenState && !hidden.has(type)) || allVisible) {
    const next = cloneSet(hidden)
    for (const value of all) next.add(value)
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
    return cloneSet(new Set(defaultHidden))
  }

  // Clicked type is visible alongside others -> hide just this one.
  const next = cloneSet(hidden)
  next.add(type)
  return next
}

export const useFilterStore = defineStore('filter', () => {
  // Detail node types start HIDDEN so the default graph stays readable.
  const hiddenNodeTypes = ref<Set<NodeType>>(defaultHiddenNodeTypes())
  // Detail edge types start HIDDEN so the default architecture graph stays
  // readable. They remain in the data and are revealed via "Show all".
  const hiddenEdgeTypes = ref<Set<EdgeType>>(defaultHiddenEdgeTypes())
  // Isolated nodes stay hidden by default so the canvas does not fill up with
  // degree-zero leaves; the user can reveal them explicitly in the filter panel.
  const hideIsolatedNodes = ref(false)
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
  // visibility). The default-hidden detail types alone do NOT count as active
  // (otherwise Reset would always appear enabled).
  const hasActiveFilters = computed(
    () =>
      deviatesFromDefault(hiddenNodeTypes.value, defaultHiddenNodeTypes()) ||
      deviatesFromDefault(hiddenEdgeTypes.value, defaultHiddenEdgeTypes()) ||
      hideIsolatedNodes.value,
  )

  function toggleNodeType(type: NodeType): void {
    const next = cloneSet(hiddenNodeTypes.value)
    const shouldHide = !next.has(type)
    if (shouldHide) next.add(type)
    else next.delete(type)
    hiddenNodeTypes.value = next

    if (type === 'Field') {
      const nextEdges = cloneSet(hiddenEdgeTypes.value)
      if (shouldHide) nextEdges.add('HAS_FIELD')
      else nextEdges.delete('HAS_FIELD')
      hiddenEdgeTypes.value = nextEdges
    }
  }

  function toggleEdgeType(type: EdgeType): void {
    const next = cloneSet(hiddenEdgeTypes.value)
    if (next.has(type)) next.delete(type)
    else next.add(type)
    hiddenEdgeTypes.value = next
  }

  function showAllNodeTypes(): void {
    hiddenNodeTypes.value = new Set()
  }

  function showAllEdgeTypes(): void {
    hiddenEdgeTypes.value = new Set()
  }

  function toggleIsolatedNodes(): void {
    hideIsolatedNodes.value = !hideIsolatedNodes.value
  }

  function reset(): void {
    hiddenNodeTypes.value = defaultHiddenNodeTypes()
    // Reset returns to the readable DEFAULT (deep-CPG hidden), not "show all".
    hiddenEdgeTypes.value = defaultHiddenEdgeTypes()
    hideIsolatedNodes.value = false
    searchQuery.value = ''
  }

  return {
    hiddenNodeTypes,
    hiddenEdgeTypes,
    hideIsolatedNodes,
    hasActiveFilters,
    searchQuery,
    toggleNodeType,
    toggleEdgeType,
    toggleIsolatedNodes,
    showAllNodeTypes,
    showAllEdgeTypes,
    reset,
  }
})
