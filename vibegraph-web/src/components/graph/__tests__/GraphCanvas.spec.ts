import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { computed, ref } from 'vue'
import GraphCanvas from '../GraphCanvas.vue'
import type { GraphData, GraphNode } from '@/types/graph'

const selectedNode = ref<GraphNode | null>(null)
const nodes = ref<GraphNode[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const clearSelection = vi.fn<() => void>(() => {
  selectedNode.value = null
})

const emptyGraphData: GraphData = {
  nodes: [],
  edges: [],
  nodeStats: {} as GraphData['nodeStats'],
  edgeStats: {} as GraphData['edgeStats'],
}

vi.mock('@/composables/useGraphData', () => ({
  useGraphData: () => ({
    graphData: computed(() => emptyGraphData),
    filteredGraphData: computed(() => emptyGraphData),
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    loadGraph: vi.fn<() => Promise<null>>(() => Promise.resolve(null)),
    buildGraph: vi.fn<() => null>(() => null),
    selectNode: vi.fn<(node: GraphNode | null) => void>((node) => {
      selectedNode.value = node
    }),
    clearSelection,
    selectedNode: computed(() => selectedNode.value),
    nodes: computed(() => nodes.value),
  }),
}))

vi.mock('@/composables/useSigma', () => ({
  useSigma: () => ({
    init: vi.fn<() => void>(),
    graphInstance: ref(null),
    setReducers: vi.fn<() => void>(),
  }),
}))

vi.mock('@/composables/useFilters', () => ({
  useFilters: () => ({
    focusDepth: computed(() => -1),
  }),
}))

// T60: GraphCanvas now wires the realtime consumer. Stub it so this test stays
// focused on canvas/search behavior and avoids pulling in Pinia + a socket.
vi.mock('@/composables/useGraphRealtime', () => ({
  useGraphRealtime: () => ({
    status: ref('disconnected'),
    error: ref(null),
    lastError: ref(null),
    stop: () => {},
  }),
}))

vi.mock('@/components/panels/FilterPanel.vue', () => ({
  default: { template: '<div data-test="filter-panel" />', props: ['graphData'] },
}))

vi.mock('@/components/panels/FocusDepthControl.vue', () => ({
  default: { template: '<div data-test="focus-depth-control" />' },
}))

describe('GraphCanvas', () => {
  it('emits null when search selection is cleared', async () => {
    const wrapper = mount(GraphCanvas, { props: { projectId: 'project-1' } })

    await wrapper.findComponent({ name: 'SearchBar' }).vm.$emit('clear')

    expect(clearSelection).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('nodeSelected')?.[0]).toEqual([null])
  })
})
