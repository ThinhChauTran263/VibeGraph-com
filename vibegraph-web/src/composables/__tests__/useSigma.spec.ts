import Graph from 'graphology'
import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSigma } from '../useSigma'
import { SIGMA_LABEL_RENDERED_SIZE_THRESHOLD } from '@/lib/runtimeConfig'

interface MockSigmaInstance {
  settings: Record<string, unknown>
}

interface MockLayoutInstance {
  start: ReturnType<typeof vi.fn>
  kill: ReturnType<typeof vi.fn>
}

interface HarnessVm {
  init: (graph: Graph) => void
}

const sigmaState = vi.hoisted(() => ({ instances: [] as MockSigmaInstance[] }))
const layoutState = vi.hoisted(() => ({ instances: [] as MockLayoutInstance[] }))

// jsdom has no Worker for the d3 path; the d3 worker protocol itself is
// covered by layoutClient.spec — here we stub the engine factory.
vi.mock('@/lib/layout/layoutClient', () => ({
  createLayoutEngine: vi.fn(() => {
    const handle: MockLayoutInstance = { start: vi.fn(), kill: vi.fn() }
    layoutState.instances.push(handle)
    return handle
  }),
}))

vi.mock('sigma', () => {
  class MockSigma {
    graph: Graph
    container: HTMLDivElement
    settings: Record<string, unknown>
    camera: {
      state: { x: number; y: number; ratio: number }
      getState: () => { x: number; y: number; ratio: number }
      setState: (next: Partial<{ x: number; y: number; ratio: number }>) => void
      on: ReturnType<typeof vi.fn>
      animatedReset: ReturnType<typeof vi.fn>
      animate: ReturnType<typeof vi.fn>
    }
    mouseCaptor: { on: ReturnType<typeof vi.fn> }
    on = vi.fn()
    getCamera = vi.fn()
    getMouseCaptor = vi.fn()
    viewportToGraph = vi.fn((event: { x?: number; y?: number }) => ({
      x: event.x ?? 0,
      y: event.y ?? 0,
    }))
    viewportToFramedGraph = vi.fn((point: { x: number; y: number }) => point)
    getNodeDisplayData = vi.fn()
    setSetting = vi.fn()
    refresh = vi.fn()
    kill = vi.fn()

    constructor(graph: Graph, container: HTMLDivElement, settings: Record<string, unknown>) {
      this.graph = graph
      this.container = container
      this.settings = settings
      this.camera = {
        state: { x: 0, y: 0, ratio: 1 },
        getState: () => this.camera.state,
        setState: (next) => {
          this.camera.state = { ...this.camera.state, ...next }
        },
        on: vi.fn(),
        animatedReset: vi.fn(),
        animate: vi.fn(),
      }
      this.mouseCaptor = { on: vi.fn() }
      this.getCamera.mockReturnValue(this.camera)
      this.getMouseCaptor.mockReturnValue(this.mouseCaptor)
      sigmaState.instances.push(this)
    }
  }

  return { default: MockSigma }
})

vi.mock('@/lib/ghostLayer', () => ({
  attachGhostLayer: vi.fn(() => ({
    setPartition: vi.fn(),
    destroy: vi.fn(),
  })),
}))

vi.stubGlobal(
  'ResizeObserver',
  class {
    observe(): void {}
    disconnect(): void {}
  },
)

const Harness = defineComponent({
  setup() {
    const container = ref(document.createElement('div'))
    const api = useSigma({ container })
    return { ...api, container }
  },
  template: '<div />',
})

function createGraph(): Graph {
  const graph = new Graph({ type: 'directed', multi: true })
  graph.addNode('service', {
    label: 'Service',
    x: 100,
    y: 100,
    size: 1,
    color: '#fff',
    type: 'circle',
    nodeType: 'Class',
    layer: 'SERVICE',
    fullName: 'com.example.Service',
    filePath: 'Service.java',
    lineNumber: 1,
  })
  graph.addNode('domain', {
    label: 'Domain',
    x: 100,
    y: 100,
    size: 1,
    color: '#fff',
    type: 'circle',
    nodeType: 'Class',
    fullName: 'com.example.Domain',
    filePath: 'Domain.java',
    lineNumber: 1,
  })
  return graph
}

describe('useSigma', () => {
  beforeEach(() => {
    sigmaState.instances.length = 0
    layoutState.instances.length = 0
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('starts the d3 layout engine once and clears old graph state on dispose', () => {
    const wrapper = mount(Harness)
    const graph = createGraph()
    const clearSpy = vi.spyOn(graph, 'clear')

    ;(wrapper.vm as unknown as HarnessVm).init(graph)

    expect(layoutState.instances).toHaveLength(1)
    expect(layoutState.instances[0]!.start).toHaveBeenCalledTimes(1)

    const sigma = sigmaState.instances[0]!
    expect(sigma.settings.hideEdgesOnMove).toBe(false)
    expect(sigma.settings.hideLabelsOnMove).toBe(false)
    expect(sigma.settings.labelRenderedSizeThreshold).toBe(SIGMA_LABEL_RENDERED_SIZE_THRESHOLD)
    expect(sigma.settings.defaultEdgeColor).toBe('#475569')
    expect(sigma.settings.maxCameraRatio).toBe(1)
    // d3 engine contract: node sizes are graph-units rendered through Sigma's
    // graph-space reference (update/graph/qwen/02-ARCHITECTURE.md §5).
    expect(sigma.settings.itemSizesReference).toBe('positions')
    expect(typeof sigma.settings.zoomToSizeRatioFunction).toBe('function')
    const zoomToSizeRatio = sigma.settings.zoomToSizeRatioFunction as (ratio: number) => number
    // f(r) = r — linear growth keeps node/spacing ratio zoom-invariant.
    expect(zoomToSizeRatio(1)).toBeCloseTo(1)
    expect(zoomToSizeRatio(0.5)).toBeCloseTo(0.5)
    expect(zoomToSizeRatio(4)).toBeCloseTo(4)
    // The worker writes positions on 'done'; init must not move seeded nodes.
    expect(graph.getNodeAttribute('service', 'x')).toBe(100)
    expect(graph.getNodeAttribute('service', 'y')).toBe(100)
    expect(graph.getNodeAttribute('domain', 'x')).toBe(100)
    expect(graph.getNodeAttribute('domain', 'y')).toBe(100)

    wrapper.unmount()

    expect(clearSpy).toHaveBeenCalled()
    expect(layoutState.instances[0]!.kill).toHaveBeenCalledTimes(1)
  })
})
