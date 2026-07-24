import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { CPG_LITE_EDGE_TYPES, STRUCTURAL_EDGE_TYPES } from '@/lib/constants'
import { defaultHiddenEdgeTypes, defaultHiddenNodeTypes } from '@/lib/graphFilters'
import { useFilterStore } from '@/stores/filter'
import type { EdgeType, NodeType } from '@/types/graph'

const ALL_EDGE_TYPES: readonly EdgeType[] = [...STRUCTURAL_EDGE_TYPES, ...CPG_LITE_EDGE_TYPES]

const sortedTypes = (set: ReadonlySet<EdgeType>): EdgeType[] => [...set].sort()

describe('useFilterStore — edge filter state semantics', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('initializes with the curated edge types hidden by default', () => {
    const store = useFilterStore()

    expect(sortedTypes(store.hiddenEdgeTypes)).toEqual(sortedTypes(defaultHiddenEdgeTypes()))
    expect(store.hiddenEdgeTypes.has('HAS_FIELD')).toBe(true)
    expect(store.hiddenEdgeTypes.has('TYPE_OF')).toBe(true)
    expect(store.hiddenEdgeTypes.has('IMPORTS')).toBe(false)
    expect(store.hiddenEdgeTypes.has('CALLS')).toBe(false)
    expect(store.hiddenEdgeTypes.has('INJECTS')).toBe(false)
    expect(store.hiddenEdgeTypes.has('STEP_IN_FLOW')).toBe(false)
    expect(store.hiddenEdgeTypes.has('RESOLVES_TO')).toBe(false)
    expect(store.hiddenEdgeTypes.has('HAS_RELATION')).toBe(false)
  })

  it('reports no active filters at the default baseline', () => {
    const store = useFilterStore()

    // The default-hidden CPG-lite edges alone must NOT count as an active filter,
    // otherwise Reset would always appear enabled.
    expect(store.hasActiveFilters).toBe(false)
  })

  it('flags active filters when a structural edge type is hidden', () => {
    const store = useFilterStore()

    store.toggleEdgeType('DEFINES', ALL_EDGE_TYPES)

    expect(store.hiddenEdgeTypes.has('DEFINES')).toBe(true)
    expect(store.hasActiveFilters).toBe(true)
  })

  it('flags active filters when a hidden CPG-lite edge type is revealed', () => {
    const store = useFilterStore()

    // TYPE_OF starts hidden; revealing it deviates from the default baseline.
    store.toggleEdgeType('TYPE_OF', ALL_EDGE_TYPES)

    expect(store.hiddenEdgeTypes.has('TYPE_OF')).toBe(false)
    expect(store.hasActiveFilters).toBe(true)
  })

  it('showAllEdgeTypes clears hidden edges and makes CPG-lite types visible', () => {
    const store = useFilterStore()

    store.showAllEdgeTypes()

    expect(store.hiddenEdgeTypes.size).toBe(0)
    for (const type of CPG_LITE_EDGE_TYPES) {
      expect(store.hiddenEdgeTypes.has(type)).toBe(false)
    }
    // Deviating from the default-hidden baseline counts as an active filter.
    expect(store.hasActiveFilters).toBe(true)
  })

  it('reset returns to the default-hidden CPG-lite baseline after showAllEdgeTypes', () => {
    const store = useFilterStore()

    store.showAllEdgeTypes()
    store.reset()

    expect(sortedTypes(store.hiddenEdgeTypes)).toEqual(sortedTypes(defaultHiddenEdgeTypes()))
    expect(store.hasActiveFilters).toBe(false)
  })

  it('reset clears user node-type filters and restores default node + edge baselines', () => {
    const store = useFilterStore()

    store.toggleNodeType('Class', ['Class', 'Method', 'Field'])
    store.toggleEdgeType('DEFINES', ALL_EDGE_TYPES)
    expect(store.hasActiveFilters).toBe(true)

    store.reset()

    const sortedNodes = (set: ReadonlySet<NodeType>): NodeType[] => [...set].sort()
    expect(sortedNodes(store.hiddenNodeTypes)).toEqual(sortedNodes(defaultHiddenNodeTypes()))
    expect(sortedTypes(store.hiddenEdgeTypes)).toEqual(sortedTypes(defaultHiddenEdgeTypes()))
    expect(store.searchQuery).toBe('')
    expect(store.hasActiveFilters).toBe(false)
  })
})
