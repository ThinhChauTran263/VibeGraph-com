import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref, computed } from 'vue'
import FilterPanel from '../FilterPanel.vue'
import type { EdgeType, GraphData, NodeType } from '@/types/graph'

const hiddenNodeTypes = ref<Set<NodeType>>(new Set())
const hiddenEdgeTypes = ref<Set<EdgeType>>(new Set())

const nextIsolate = <T>(hidden: Set<T>, type: T, available: readonly T[]): Set<T> => {
  const all = new Set<T>(available)
  all.add(type)
  const visibleCount = [...all].reduce((count, t) => count + (hidden.has(t) ? 0 : 1), 0)
  if (visibleCount === all.size) {
    const next = new Set<T>(all)
    next.delete(type)
    return next
  }
  if (hidden.has(type)) {
    const next = new Set<T>(hidden)
    next.delete(type)
    return next
  }
  if (visibleCount === 1) return new Set<T>()
  const next = new Set<T>(hidden)
  next.add(type)
  return next
}

vi.mock('@/stores/filter', () => ({
  useFilterStore: () => ({
    hiddenNodeTypes: hiddenNodeTypes.value,
    hiddenEdgeTypes: hiddenEdgeTypes.value,
    hasActiveFilters: computed(() => hiddenNodeTypes.value.size > 0 || hiddenEdgeTypes.value.size > 0),
    focusDepth: -1,
    searchQuery: '',
    toggleNodeType: (type: NodeType, available: readonly NodeType[] = []) => {
      hiddenNodeTypes.value = nextIsolate(hiddenNodeTypes.value, type, available)
    },
    toggleEdgeType: (type: EdgeType, available: readonly EdgeType[] = []) => {
      hiddenEdgeTypes.value = nextIsolate(hiddenEdgeTypes.value, type, available)
    },
    showAllNodeTypes: () => {
      hiddenNodeTypes.value = new Set()
    },
    showAllEdgeTypes: () => {
      hiddenEdgeTypes.value = new Set()
    },
    reset: () => {
      hiddenNodeTypes.value = new Set()
      hiddenEdgeTypes.value = new Set()
    },
  }),
}))

const graphData: GraphData = {
  nodes: [],
  edges: [],
  nodeStats: { Class: 2, Method: 5 } as GraphData['nodeStats'],
  edgeStats: { HAS_METHOD: 5, CALLS: 3 } as GraphData['edgeStats'],
}

beforeEach(() => {
  hiddenNodeTypes.value = new Set()
  hiddenEdgeTypes.value = new Set()
})

describe('FilterPanel', () => {
  it('renders node and edge type counts', () => {
    const wrapper = mount(FilterPanel, { props: { graphData } })

    expect(wrapper.text()).toContain('Method')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('CALLS')
    expect(wrapper.text()).toContain('3')
  })

  it('isolates a clicked type and restores when re-clicked alone', async () => {
    const wrapper = mount(FilterPanel, { props: { graphData } })

    const clickType = async (label: string) =>
      wrapper.findAll('button').find((button) => button.text().includes(label))!.trigger('click')

    // First click isolates Method: every OTHER node type is hidden, Method stays.
    await clickType('Method')
    expect(hiddenNodeTypes.value.has('Method')).toBe(false)
    expect(hiddenNodeTypes.value.has('Class')).toBe(true)

    // Clicking the sole visible type again restores all node types.
    await clickType('Method')
    expect(hiddenNodeTypes.value.size).toBe(0)

    // Edge types behave the same way.
    await clickType('CALLS')
    expect(hiddenEdgeTypes.value.has('CALLS')).toBe(false)
    expect(hiddenEdgeTypes.value.has('HAS_METHOD')).toBe(true)
  })

  it('keeps earlier types open when adding more during isolation', async () => {
    const wrapper = mount(FilterPanel, { props: { graphData } })

    const clickType = async (label: string) =>
      wrapper.findAll('button').find((button) => button.text().includes(label))!.trigger('click')

    // Isolate Method, then add Class back: both visible, nothing hidden.
    await clickType('Method')
    await clickType('Class')
    expect(hiddenNodeTypes.value.has('Method')).toBe(false)
    expect(hiddenNodeTypes.value.has('Class')).toBe(false)
  })

  it('resets active filters', async () => {
    hiddenNodeTypes.value = new Set(['Class'])
    hiddenEdgeTypes.value = new Set(['HAS_METHOD'])
    const wrapper = mount(FilterPanel, { props: { graphData } })

    await wrapper.get('.filter-panel__reset').trigger('click')

    expect(hiddenNodeTypes.value.size).toBe(0)
    expect(hiddenEdgeTypes.value.size).toBe(0)
  })
})
