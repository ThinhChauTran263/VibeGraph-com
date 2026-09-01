import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { defaultHiddenEdgeTypes, defaultHiddenNodeTypes } from '@/lib/graphFilters'
import { EDGE_COLORS, NODE_COLORS } from '@/lib/constants'
import type { NodeType, EdgeType } from '@/types/graph'

const cloneSet = <T>(values: Set<T>): Set<T> => new Set(values)
const FILTER_STORAGE_KEY = 'vibegraph.graphFiltersByProject'

interface PersistedFilterState {
  hiddenNodeTypes: NodeType[]
  hiddenEdgeTypes: EdgeType[]
  hideIsolatedNodes: boolean
}

type PersistedFilterStates = Record<string, PersistedFilterState>

function isNodeType(value: unknown): value is NodeType {
  return typeof value === 'string' && value in NODE_COLORS
}

function isEdgeType(value: unknown): value is EdgeType {
  return typeof value === 'string' && value in EDGE_COLORS
}

function readPersistedStates(): PersistedFilterStates {
  if (typeof localStorage === 'undefined') return {}
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(FILTER_STORAGE_KEY) ?? '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return parsed as PersistedFilterStates
  } catch {
    return {}
  }
}

function persistState(projectId: string | null, state: PersistedFilterState): void {
  if (!projectId || typeof localStorage === 'undefined') return
  try {
    const states = readPersistedStates()
    states[projectId] = state
    localStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify(states))
  } catch {
    // Filter changes must still work when browser storage is unavailable or full.
  }
}

function defaultState(): PersistedFilterState {
  return {
    hiddenNodeTypes: [...defaultHiddenNodeTypes()],
    hiddenEdgeTypes: [...defaultHiddenEdgeTypes()],
    hideIsolatedNodes: false,
  }
}

function normalizeState(value: unknown): PersistedFilterState {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return defaultState()
  const candidate = value as Partial<PersistedFilterState>
  return {
    hiddenNodeTypes: Array.isArray(candidate.hiddenNodeTypes)
      ? candidate.hiddenNodeTypes.filter(isNodeType)
      : defaultState().hiddenNodeTypes,
    hiddenEdgeTypes: Array.isArray(candidate.hiddenEdgeTypes)
      ? candidate.hiddenEdgeTypes.filter(isEdgeType)
      : defaultState().hiddenEdgeTypes,
    hideIsolatedNodes: candidate.hideIsolatedNodes === true,
  }
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
  const currentProjectId = ref<string | null>(null)

  function persistCurrentState(): void {
    persistState(currentProjectId.value, {
      hiddenNodeTypes: [...hiddenNodeTypes.value],
      hiddenEdgeTypes: [...hiddenEdgeTypes.value],
      hideIsolatedNodes: hideIsolatedNodes.value,
    })
  }

  function setProject(projectId: string | null): void {
    currentProjectId.value = projectId || null
    const state = normalizeState(
      currentProjectId.value ? readPersistedStates()[currentProjectId.value] : undefined,
    )
    hiddenNodeTypes.value = new Set(state.hiddenNodeTypes)
    hiddenEdgeTypes.value = new Set(state.hiddenEdgeTypes)
    hideIsolatedNodes.value = state.hideIsolatedNodes
    searchQuery.value = ''
  }

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
    persistCurrentState()
  }

  function toggleEdgeType(type: EdgeType): void {
    const next = cloneSet(hiddenEdgeTypes.value)
    if (next.has(type)) next.delete(type)
    else next.add(type)
    hiddenEdgeTypes.value = next
    persistCurrentState()
  }

  function showAllNodeTypes(): void {
    hiddenNodeTypes.value = new Set()
    persistCurrentState()
  }

  function showAllEdgeTypes(): void {
    hiddenEdgeTypes.value = new Set()
    persistCurrentState()
  }

  function toggleIsolatedNodes(): void {
    hideIsolatedNodes.value = !hideIsolatedNodes.value
    persistCurrentState()
  }

  function reset(): void {
    hiddenNodeTypes.value = defaultHiddenNodeTypes()
    // Reset returns to the readable DEFAULT (deep-CPG hidden), not "show all".
    hiddenEdgeTypes.value = defaultHiddenEdgeTypes()
    hideIsolatedNodes.value = false
    searchQuery.value = ''
    persistCurrentState()
  }

  return {
    hiddenNodeTypes,
    hiddenEdgeTypes,
    hideIsolatedNodes,
    setProject,
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
