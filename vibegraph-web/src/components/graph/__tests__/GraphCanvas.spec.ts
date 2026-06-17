import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { computed, ref } from 'vue'
import Graph from 'graphology'
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

const selectNode = vi.fn<(node: GraphNode | null) => void>((node) => {
  selectedNode.value = node
})

vi.mock('@/composables/useGraphData', () => ({
  useGraphData: () => ({
    graphData: computed(() => emptyGraphData),
    filteredGraphData: computed(() => emptyGraphData),
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    loadGraph: vi.fn<() => Promise<null>>(() => Promise.resolve(null)),
    buildGraph: vi.fn<() => null>(() => null),
    selectNode,
    clearSelection,
    selectedNode: computed(() => selectedNode.value),
    nodes: computed(() => nodes.value),
  }),
}))

// Stable spy + captured camera handler for the edge-label toggle test. A
// non-null graphInstance is required: applyFocusReducers/focusOn early-return
// when there is no graph, so setEdgeLabelsVisible would otherwise never be hit.
const setEdgeLabelsVisible = vi.fn<(visible: boolean) => void>()
const graphInstanceRef = ref<Graph | null>(new Graph({ type: 'directed', multi: true }))
let capturedCameraRatioChange: ((ratio: number) => void) | undefined

vi.mock('@/composables/useSigma', () => ({
  useSigma: (config: { onCameraRatioChange?: (ratio: number) => void }) => {
    capturedCameraRatioChange = config?.onCameraRatioChange
    return {
      init: vi.fn<() => void>(),
      graphInstance: graphInstanceRef,
      setReducers: vi.fn<() => void>(),
      setEdgeLabelsVisible,
      setGhostPartition: vi.fn<() => void>(),
    }
  },
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

  it('pins relation focus without navigating when a detail relation is chosen (Req C)', async () => {
    const selected: GraphNode = {
      id: 'selected',
      type: 'Class',
      name: 'OrderService',
      fullName: 'com.example.OrderService',
      filePath: 'src/main/java/com/example/OrderService.java',
      lineNumber: 10,
      properties: {},
    }
    nodes.value = [selected]
    selectedNode.value = selected
    selectNode.mockClear()

    const wrapper = mount(GraphCanvas, { props: { projectId: 'project-1' } })

    await wrapper.findComponent({ name: 'NodeDetailPanel' }).vm.$emit('relationSelect', {
      edgeId: 'counterpart|CALLS|selected',
      counterpartNodeId: 'counterpart',
    })

    // Pinning keeps the current selection anchored; it must NOT navigate to the
    // counterpart node or emit a new nodeSelected event.
    expect(selectNode).not.toHaveBeenCalledWith(
      expect.objectContaining({ id: 'counterpart' }),
    )
    expect(wrapper.emitted('nodeSelected')).toBeUndefined()

    // The pinned edge id is forwarded to NodeDetailPanel so the chosen relation
    // item keeps its selected styling after the pointer leaves.
    expect(
      wrapper.findComponent({ name: 'NodeDetailPanel' }).props('pinnedEdgeId'),
    ).toBe('counterpart|CALLS|selected')
  })

  it('clears pinned relation focus when the detail panel closes (Req C)', async () => {
    const selected: GraphNode = {
      id: 'selected',
      type: 'Class',
      name: 'OrderService',
      fullName: 'com.example.OrderService',
      filePath: 'src/main/java/com/example/OrderService.java',
      lineNumber: 10,
      properties: {},
    }
    nodes.value = [selected]
    selectedNode.value = selected
    clearSelection.mockClear()

    const wrapper = mount(GraphCanvas, { props: { projectId: 'project-1' } })
    const panel = wrapper.findComponent({ name: 'NodeDetailPanel' })

    await panel.vm.$emit('relationSelect', {
      edgeId: 'counterpart|CALLS|selected',
      counterpartNodeId: 'counterpart',
    })
    expect(panel.props('pinnedEdgeId')).toBe('counterpart|CALLS|selected')

    await panel.vm.$emit('close')

    expect(clearSelection).toHaveBeenCalledTimes(1)
    const nodeSelectedEvents = wrapper.emitted('nodeSelected')
    expect(nodeSelectedEvents?.[nodeSelectedEvents.length - 1]).toEqual([null])
  })
})

/**
 * Edge-label toggle. The "Edge labels" button flips `edgeLabelsEnabled`, and
 * both focus paths (selection focus and the default no-selection view) gate edge
 * label rendering on `edgeLabelsEnabled && labelDensity === 'edges'`. With no node
 * selected the default path forwards that exact boolean to Sigma via
 * `setEdgeLabelsVisible`, so we assert against it directly. Driving the captured
 * `onCameraRatioChange` handler sets the zoom density deterministically (no real
 * Sigma camera / timers), keeping the test non-flaky.
 */
describe('GraphCanvas edge label toggle', () => {
  it('forces edge labels off when toggled, and only shows them at edges density', async () => {
    // No selection -> applyFocusReducers takes the default path and pushes
    // (edgeLabelsEnabled && labelDensity === 'edges') straight to Sigma.
    selectedNode.value = null
    nodes.value = []
    loading.value = false
    error.value = null

    const wrapper = mount(GraphCanvas, { props: { projectId: 'project-1' } })
    await flushPromises()
    setEdgeLabelsVisible.mockClear()

    expect(capturedCameraRatioChange).toBeTypeOf('function')

    // Zoom in past the edge-label ratio (0.7) -> density 'edges'. Toggle defaults
    // ON, so edge labels become visible.
    capturedCameraRatioChange?.(0.5)
    expect(setEdgeLabelsVisible).toHaveBeenLastCalledWith(true)

    // Toggle OFF -> edge labels forced off even though we are still zoomed in.
    const toggle = wrapper.get('.graph-edge-label-toggle')
    await toggle.trigger('click')
    expect(setEdgeLabelsVisible).toHaveBeenLastCalledWith(false)
    expect(toggle.attributes('aria-pressed')).toBe('false')
    expect(toggle.text()).toBe('Edge labels: Off')

    // Toggle back ON -> visible again at edges density.
    await toggle.trigger('click')
    expect(setEdgeLabelsVisible).toHaveBeenLastCalledWith(true)

    // AND-gate: zoom back out to 'nodes' density. Even with the toggle ON, edge
    // labels stay off because the density half of the gate is false.
    capturedCameraRatioChange?.(0.9)
    expect(setEdgeLabelsVisible).toHaveBeenLastCalledWith(false)
  })
})
