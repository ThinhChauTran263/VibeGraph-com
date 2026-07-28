import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref, computed } from 'vue'
import FilterPanel from '../FilterPanel.vue'
import type { EdgeType, GraphData, NodeType } from '@/types/graph'

const hiddenNodeTypes = ref<Set<NodeType>>(new Set())
const hiddenEdgeTypes = ref<Set<EdgeType>>(new Set())

vi.mock('@/stores/filter', () => ({
  useFilterStore: () => ({
    hiddenNodeTypes: hiddenNodeTypes.value,
    hiddenEdgeTypes: hiddenEdgeTypes.value,
    hasActiveFilters: computed(
      () => hiddenNodeTypes.value.size > 0 || hiddenEdgeTypes.value.size > 0,
    ),
    searchQuery: '',
    toggleNodeType: (type: NodeType) => {
      const next = new Set(hiddenNodeTypes.value)
      if (next.has(type)) next.delete(type)
      else next.add(type)
      hiddenNodeTypes.value = next
    },
    toggleEdgeType: (type: EdgeType) => {
      const next = new Set(hiddenEdgeTypes.value)
      if (next.has(type)) next.delete(type)
      else next.add(type)
      hiddenEdgeTypes.value = next
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

    expect(wrapper.text()).toContain('Graph filters')
    expect(wrapper.text()).toContain('Reset all')
    expect(wrapper.text()).not.toContain('Isolated')
    expect(wrapper.text()).not.toContain('Package')
    expect(wrapper.text()).toContain('Method')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('CALLS')
    expect(wrapper.text()).toContain('3')
  })

  it('resets filters to the default state', async () => {
    hiddenNodeTypes.value = new Set(['Class'])
    hiddenEdgeTypes.value = new Set(['CALLS'])
    const wrapper = mount(FilterPanel, { props: { graphData } })

    await wrapper.get('.filter-panel__reset').trigger('click')

    expect(hiddenNodeTypes.value.size).toBe(0)
    expect(hiddenEdgeTypes.value.size).toBe(0)
  })

  it('toggles a clicked type independently', async () => {
    const wrapper = mount(FilterPanel, { props: { graphData } })

    const clickType = async (label: string) =>
      wrapper
        .findAll('button')
        .find((button) => button.text().includes(label))!
        .trigger('click')

    // First click hides Method without changing Class.
    await clickType('Method')
    expect(hiddenNodeTypes.value.has('Method')).toBe(true)
    expect(hiddenNodeTypes.value.has('Class')).toBe(false)

    // Clicking Method again reveals only Method.
    await clickType('Method')
    expect(hiddenNodeTypes.value.size).toBe(0)

    // Edge types behave independently too.
    await clickType('CALLS')
    expect(hiddenEdgeTypes.value.has('CALLS')).toBe(true)
    expect(hiddenEdgeTypes.value.has('HAS_METHOD')).toBe(false)
  })

  it('shows all node and edge types from the panel controls', async () => {
    hiddenNodeTypes.value = new Set(['Class'])
    hiddenEdgeTypes.value = new Set(['HAS_METHOD'])
    const wrapper = mount(FilterPanel, { props: { graphData } })

    const showAllButtons = wrapper
      .findAll('.filter-panel__section-header button')
      .map((button) => button)
    await showAllButtons[0]!.trigger('click')
    await showAllButtons[1]!.trigger('click')

    expect(hiddenNodeTypes.value.size).toBe(0)
    expect(hiddenEdgeTypes.value.size).toBe(0)
  })
})
