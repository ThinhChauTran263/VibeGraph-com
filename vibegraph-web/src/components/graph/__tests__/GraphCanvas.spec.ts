import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { computed, ref } from 'vue'
import Graph from 'graphology'
import GraphCanvas from '../GraphCanvas.vue'
import i18n from '@/language'
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

let capturedRealtimePatched: ((event: unknown) => void) | undefined

vi.mock('@/composables/useGraphData', () => ({
  useGraphData: () => ({
    graphData: computed(() => emptyGraphData),
    filteredGraphData: computed(() => emptyGraphData),
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    loadGraph: vi.fn<() => Promise<null>>(() => Promise.resolve(null)),
    ensureDeepGraph: vi.fn<() => Promise<void>>(() => Promise.resolve()),
    buildGraph: vi.fn<() => null>(() => null),
    selectNode,
    clearSelection,
    selectedNode: computed(() => selectedNode.value),
    renderInfo: computed(() => null),
    payloadMode: computed(() => 'baseline'),
    nodes: computed(() => nodes.value),
  }),
}))

// Lazy-expand composable: stub so the component test does not pull in Pinia or the API.
vi.mock('@/composables/useGraphExpand', () => ({
  useGraphExpand: () => ({
    expanding: ref(false),
    lastError: ref(null),
    expandNode: vi.fn<() => Promise<number>>(() => Promise.resolve(0)),
    reset: vi.fn<() => void>(),
  }),
}))

// Stable spy + captured camera handler for the edge-label toggle test. A
// non-null graphInstance is required because focus reducers early-return when
// there is no graph.
const setReducers = vi.fn<(reducers: unknown, edgeLabelsVisible?: boolean) => void>()
const graphInstanceRef = ref<Graph | null>(new Graph({ type: 'directed', multi: true }))
let capturedCameraRatioChange: ((ratio: number) => void) | undefined

vi.mock('@/composables/useSigma', () => ({
  useSigma: (config: { onCameraRatioChange?: (ratio: number) => void }) => {
    capturedCameraRatioChange = config?.onCameraRatioChange
    return {
      init: vi.fn<() => void>(),
      graphInstance: graphInstanceRef,
      setReducers,
      setGhostPartition: vi.fn<() => void>(),
      refresh: vi.fn<() => void>(),
      resetLayout: vi.fn<() => void>(),
      zoomToFit: vi.fn<() => void>(),
      focusNode: vi.fn<() => void>(),
    }
  },
}))

// T60: GraphCanvas now wires the realtime consumer. Stub it so this test stays
// focused on canvas/search behavior and avoids pulling in Pinia + a socket.
vi.mock('@/composables/useGraphRealtime', () => ({
  useGraphRealtime: (_projectId: () => string, options?: { onPatched?: (event: unknown) => void }) => {
    capturedRealtimePatched = options?.onPatched
    return {
      status: ref('disconnected'),
      error: ref(null),
      lastError: ref(null),
      stop: () => {},
    }
  },
}))

vi.mock('@/components/panels/FilterPanel.vue', () => ({
  default: { template: '<div data-test="filter-panel" />', props: ['graphData'] },
}))

// Filters are used for realtime in-place patching (hidden type sets). Stub so the test
// avoids Pinia; default = nothing hidden.
vi.mock('@/composables/useFilters', () => ({
  useFilters: () => ({
    hiddenNodeTypes: computed(() => new Set<string>()),
    hiddenEdgeTypes: computed(() => new Set<string>()),
    hideIsolatedNodes: computed(() => false),
  }),
}))

// The analyzing-status preflight in load() must not hit a real backend in unit
// tests; keep every other lib/api export intact for child components.
vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    projectApi: { get: vi.fn<() => Promise<never>>(() => Promise.reject(new Error('test: no backend'))) },
  }
})

describe('GraphCanvas', () => {
  it('emits null when search selection is cleared', async () => {
    const wrapper = mount(GraphCanvas, {
          props: { projectId: 'project-1' },
          global: { plugins: [createTestingPinia({ createSpy: vi.fn }), i18n] },
        })

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

    const wrapper = mount(GraphCanvas, {
          props: { projectId: 'project-1' },
          global: { plugins: [createTestingPinia({ createSpy: vi.fn }), i18n] },
        })

    await wrapper.findComponent({ name: 'NodeDetailPanel' }).vm.$emit('relationSelect', {
      edgeId: 'counterpart|CALLS|selected',
      counterpartNodeId: 'counterpart',
    })

    // Pinning keeps the current selection anchored; it must NOT navigate to the
    // counterpart node or emit a new nodeSelected event.
    expect(selectNode).not.toHaveBeenCalledWith(expect.objectContaining({ id: 'counterpart' }))
    expect(wrapper.emitted('nodeSelected')).toBeUndefined()

    // The pinned edge id is forwarded to NodeDetailPanel so the chosen relation
    // item keeps its selected styling after the pointer leaves.
    expect(wrapper.findComponent({ name: 'NodeDetailPanel' }).props('pinnedEdgeId')).toBe(
      'counterpart|CALLS|selected',
    )
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

    const wrapper = mount(GraphCanvas, {
          props: { projectId: 'project-1' },
          global: { plugins: [createTestingPinia({ createSpy: vi.fn }), i18n] },
        })
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

  it('keeps realtime edges with the same node pair but different direction or type', async () => {
    const graph = new Graph({ type: 'directed', multi: true })
    graph.addNode('a', {
      label: 'A',
      x: 0,
      y: 0,
      size: 1,
      color: '#fff',
      type: 'circle',
      nodeType: 'Class',
      fullName: 'a',
      filePath: '',
      lineNumber: 1,
      properties: {},
    })
    graph.addNode('b', {
      label: 'B',
      x: 10,
      y: 10,
      size: 1,
      color: '#fff',
      type: 'circle',
      nodeType: 'Class',
      fullName: 'b',
      filePath: '',
      lineNumber: 1,
      properties: {},
    })
    graph.addEdgeWithKey('a|CALLS|b', 'a', 'b', { color: '#93c5fd' })
    graphInstanceRef.value = graph

    const wrapper = mount(GraphCanvas, {
          props: { projectId: 'project-1' },
          global: { plugins: [createTestingPinia({ createSpy: vi.fn }), i18n] },
        })
    await flushPromises()

    capturedRealtimePatched?.({
      type: 'INCREMENTAL',
      added: {
        nodes: [],
        edges: [
          { id: 'a|INJECTS|b', source: 'a', target: 'b', type: 'INJECTS' },
          { id: 'b|CALLS|a', source: 'b', target: 'a', type: 'CALLS' },
        ],
      },
      removed: { nodeIds: [], edgeIds: [] },
      modified: { nodes: [], edges: [] },
    })

    expect(graph.hasEdge('a|CALLS|b')).toBe(true)
    expect(graph.hasEdge('a|INJECTS|b')).toBe(true)
    expect(graph.hasEdge('b|CALLS|a')).toBe(true)

    wrapper.unmount()
  })
})

/**
 * Edge-label toggle. The "Edge labels" button flips `edgeLabelsEnabled`, and
 * both focus paths (selection focus and the default no-selection view) gate edge
 * label rendering on `edgeLabelsEnabled && labelDensity === 'edges'`. With no node
 * selected the default path batches that exact boolean with the reducer settings,
 * so we assert against the second argument. Driving the captured
 * `onCameraRatioChange` handler sets the zoom density deterministically (no real
 * Sigma camera / timers), keeping the test non-flaky.
 */
describe('GraphCanvas edge label toggle', () => {
  it('hides default edges at fit and reveals them only after zoom-in', async () => {
    selectedNode.value = null
    nodes.value = []
    loading.value = false
    error.value = null
    graphInstanceRef.value?.clear()
    graphInstanceRef.value?.addNode('a', { filterHidden: false })
    graphInstanceRef.value?.addNode('b', { filterHidden: false })
    graphInstanceRef.value?.addEdgeWithKey('edge', 'a', 'b', { filterHidden: false })

    const wrapper = mount(GraphCanvas, {
      props: { projectId: 'project-1' },
      global: { plugins: [createTestingPinia({ createSpy: vi.fn }), i18n] },
    })
    await flushPromises()
    setReducers.mockClear()

    capturedCameraRatioChange?.(1.1)
    const fitReducers = setReducers.mock.calls[setReducers.mock.calls.length - 1]?.[0] as {
      nodeReducer: (node: string, attributes: Record<string, unknown>) => Record<string, unknown>
      edgeReducer: (edge: string, attributes: Record<string, unknown>) => Record<string, unknown>
    }
    expect(fitReducers.edgeReducer('edge', { color: '#fff' }).hidden).toBe(true)
    expect(fitReducers.nodeReducer('a', { size: 10 }).size).toBe(15)

    capturedCameraRatioChange?.(0.9)
    const earlyZoomReducers = setReducers.mock.calls[setReducers.mock.calls.length - 1]?.[0] as {
      nodeReducer: (node: string, attributes: Record<string, unknown>) => Record<string, unknown>
      edgeReducer: (edge: string, attributes: Record<string, unknown>) => Record<string, unknown>
    }
    expect(earlyZoomReducers.edgeReducer('edge', { color: '#fff' }).hidden).toBe(true)
    expect(earlyZoomReducers.nodeReducer('a', { size: 10 }).size).toBe(14)

    capturedCameraRatioChange?.(0.45)
    const edgeRevealReducers = setReducers.mock.calls[setReducers.mock.calls.length - 1]?.[0] as {
      nodeReducer: (node: string, attributes: Record<string, unknown>) => Record<string, unknown>
      edgeReducer: (edge: string, attributes: Record<string, unknown>) => Record<string, unknown>
    }
    expect(edgeRevealReducers.edgeReducer('edge', { color: '#fff' }).hidden).not.toBe(true)
    expect(edgeRevealReducers.nodeReducer('a', { size: 10 }).size).toBe(10)

    capturedCameraRatioChange?.(1)
    const resetReducers = setReducers.mock.calls[setReducers.mock.calls.length - 1]?.[0] as {
      edgeReducer: (edge: string, attributes: Record<string, unknown>) => Record<string, unknown>
    }
    expect(resetReducers.edgeReducer('edge', { color: '#fff' }).hidden).toBe(true)
    wrapper.unmount()
  })

  it('forces edge labels off when toggled, and only shows them at edges density', async () => {
    // No selection -> applyFocusReducers takes the default path and pushes
    // (edgeLabelsEnabled && labelDensity === 'edges') straight to Sigma.
    selectedNode.value = null
    nodes.value = []
    loading.value = false
    error.value = null

    // The zoom-driven density change applies its reducer swap synchronously, so the
    // captured onCameraRatioChange handler drives the batched setting immediately.
    const wrapper = mount(GraphCanvas, {
          props: { projectId: 'project-1' },
          global: { plugins: [createTestingPinia({ createSpy: vi.fn }), i18n] },
        })
    await flushPromises()
    setReducers.mockClear()

    expect(capturedCameraRatioChange).toBeTypeOf('function')

    // Zoom in past the edge-label ratio (0.45) -> density 'edges'. Labels are on
    // by default, so they are visible at this density.
    capturedCameraRatioChange?.(0.3)
    expect(setReducers).toHaveBeenLastCalledWith(expect.any(Object), true)

    // Toggle OFF -> edge labels disappear while the camera remains zoomed in.
    const toggle = wrapper.get('.graph-edge-label-toggle')
    await toggle.trigger('click')
    expect(setReducers).toHaveBeenLastCalledWith(expect.any(Object), false)
    expect(toggle.attributes('aria-pressed')).toBe('false')
    expect(toggle.text()).toBe('Edge labels: Off')

    // Toggle back ON -> visible again at edges density.
    await toggle.trigger('click')
    expect(setReducers).toHaveBeenLastCalledWith(expect.any(Object), true)

    // AND-gate: zoom back out to 'nodes' density. Even with the toggle OFF, edge
    // labels stay off because the density half of the gate is false.
    capturedCameraRatioChange?.(0.9)
    expect(setReducers).toHaveBeenLastCalledWith(expect.any(Object), false)
  })
})
