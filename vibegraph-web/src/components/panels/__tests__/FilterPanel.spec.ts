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
    hasActiveFilters: computed(() => hiddenNodeTypes.value.size > 0 || hiddenEdgeTypes.value.size > 0),
    focusDepth: -1,
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

    expect(wrapper.text()).toContain('Method')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('CALLS')
    expect(wrapper.text()).toContain('3')
  })

  it('toggles node and edge types in the filter store', async () => {
    const wrapper = mount(FilterPanel, { props: { graphData } })

    await wrapper.findAll('button').find((button) => button.text().includes('Method'))!.trigger('click')
    await wrapper.findAll('button').find((button) => button.text().includes('CALLS'))!.trigger('click')

    expect(hiddenNodeTypes.value.has('Method')).toBe(true)
    expect(hiddenEdgeTypes.value.has('CALLS')).toBe(true)
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
