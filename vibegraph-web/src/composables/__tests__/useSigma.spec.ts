import Graph from 'graphology'
import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSigma } from '../useSigma'

interface MockSigmaInstance {
  settings: Record<string, unknown>
}

interface MockLayoutInstance {
  params: Record<string, unknown>
  start: ReturnType<typeof vi.fn>
  kill: ReturnType<typeof vi.fn>
}

interface HarnessVm {
  init: (graph: Graph) => void
}

const sigmaState = vi.hoisted(() => ({ instances: [] as MockSigmaInstance[] }))
const layoutState = vi.hoisted(() => ({ instances: [] as MockLayoutInstance[] }))
const syncLayoutState = vi.hoisted(() => ({ assign: vi.fn(), inferSettings: vi.fn() }))

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

vi.mock('graphology-layout-forceatlas2', () => ({ default: syncLayoutState }))

vi.mock('graphology-layout-forceatlas2/worker', () => {
  class MockLayout {
    graph: Graph
    params: Record<string, unknown>
    start = vi.fn()
    stop = vi.fn()
    kill = vi.fn()
    isRunning = vi.fn(() => true)

    constructor(graph: Graph, params: Record<string, unknown>) {
      this.graph = graph
      this.params = params
      layoutState.instances.push(this as MockLayoutInstance)
    }
  }

  return { default: MockLayout }
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
    syncLayoutState.assign.mockClear()
    syncLayoutState.inferSettings.mockClear()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('runs layout in the worker without soft-band y post-processing and clears old graph state on dispose', () => {
    const wrapper = mount(Harness)
    const graph = createGraph()
    const clearSpy = vi.spyOn(graph, 'clear')

    ;(wrapper.vm as unknown as HarnessVm).init(graph)

    expect(syncLayoutState.assign).not.toHaveBeenCalled()
    expect(layoutState.instances).toHaveLength(1)
    expect(layoutState.instances[0]!.start).toHaveBeenCalledTimes(1)
    expect(layoutState.instances[0]!.params).toMatchObject({
      settings: expect.objectContaining({
        gravity: 1,
        scalingRatio: 5,
        linLogMode: false,
      }),
    })

    const sigma = sigmaState.instances[0]!
    expect(sigma.settings.hideEdgesOnMove).toBe(true)
    expect(sigma.settings.hideLabelsOnMove).toBe(true)
    expect(sigma.settings.labelRenderedSizeThreshold).toBe(8)
    expect(sigma.settings.defaultEdgeColor).toBe('#475569')
    expect(sigma.settings.itemSizesReference).toBe('positions')
    expect(typeof sigma.settings.zoomToSizeRatioFunction).toBe('function')
    expect(layoutState.instances[0]!.params).not.toHaveProperty('outputReducer')
    expect(graph.getNodeAttribute('service', 'x')).toBe(100)
    expect(graph.getNodeAttribute('service', 'y')).toBe(100)
    expect(graph.getNodeAttribute('domain', 'x')).toBe(100)
    expect(graph.getNodeAttribute('domain', 'y')).toBe(100)

    wrapper.unmount()

    expect(clearSpy).toHaveBeenCalled()
    expect(layoutState.instances[0]!.kill).toHaveBeenCalledTimes(1)
  })
})
