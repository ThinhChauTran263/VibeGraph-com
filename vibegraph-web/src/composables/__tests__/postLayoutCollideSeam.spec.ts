/**
 * Seam test for the post-layout pipeline (runPostLayoutPass): init() then
 * stopLayout(runPostLayout = true) must drive the Layer 2 d3-forceCollide
 * micro-pass over the live graph and finish with zero residual collisions,
 * a Sigma refresh, and the onLayoutSettled callback. Replaces the old
 * settleScreenOverlaps seam spec after BLOB-3.
 */
import Graph from 'graphology'
import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSigma } from '../useSigma'
import { LAYOUT_SCREEN_OVERLAP_GAP_PX } from '@/lib/runtimeConfig'

const VIEWPORT_WIDTH = 1000
const VIEWPORT_HEIGHT = 600

vi.mock('@/lib/runtimeConfig', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/runtimeConfig')>()
  return {
    ...actual,
    LAYOUT_NORMALIZE_SPAN: 0,
    LAYOUT_BRANCH_ENABLED: false,
    // jsdom has no Worker: pin the fa2 kill-switch engine for this harness.
    LAYOUT_ENGINE: 'fa2' as const,
  }
})

const sigmaState = vi.hoisted(() => ({ instances: [] as MockSigmaInstance[] }))
const layoutState = vi.hoisted(() => ({ instances: [] as MockLayoutInstance[] }))

interface MockSigmaInstance {
  refresh: ReturnType<typeof vi.fn>
}
interface MockLayoutInstance {
  start: ReturnType<typeof vi.fn>
  kill: ReturnType<typeof vi.fn>
}

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
    setSettings = vi.fn()
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
      sigmaState.instances.push(this as unknown as MockSigmaInstance)
    }
  }
  return { default: MockSigma }
})

vi.mock('graphology-layout-forceatlas2', () => ({ default: { assign: vi.fn(), inferSettings: vi.fn() } }))

vi.mock('graphology-layout-forceatlas2/worker', () => {
  class MockLayout {
    start = vi.fn()
    kill = vi.fn()
    constructor(
      public graph: Graph,
      public params: Record<string, unknown>,
    ) {
      layoutState.instances.push(this as unknown as MockLayoutInstance)
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

function makeContainer(width: number, height: number): HTMLDivElement {
  const el = document.createElement('div')
  Object.defineProperty(el, 'clientWidth', { value: width, configurable: true })
  Object.defineProperty(el, 'clientHeight', { value: height, configurable: true })
  return el
}

function mountHarness(container: HTMLDivElement, onLayoutSettled?: () => void) {
  const Harness = defineComponent({
    setup() {
      const containerRef = ref<HTMLDivElement | null>(container)
      const api = useSigma({ container: containerRef, onLayoutSettled })
      return { ...api }
    },
    template: '<div />',
  })
  return mount(Harness)
}

interface HarnessVm {
  init: (graph: Graph) => void
  stopLayout: (runPostLayout?: boolean) => void
}

function buildGraph(): Graph {
  const graph = new Graph()
  // 10-node dense cluster + viewport anchor (unitsPerPixel = 1).
  const cluster = [
    [0, 0], [2, 1], [4, 0], [1, 3], [3, 3], [5, 2], [0, 5], [2, 6], [4, 5], [6, 1],
  ]
  cluster.forEach(([x, y], i) => {
    graph.addNode(`n${i}`, {
      label: `n${i}`, x, y, size: 5, color: '#fff', type: 'circle',
      nodeType: 'Class', fullName: `n${i}`, filePath: '', lineNumber: 1,
    })
  })
  graph.addNode('anchor', {
    label: 'anchor', x: VIEWPORT_WIDTH, y: VIEWPORT_HEIGHT, size: 5, color: '#fff',
    type: 'circle', nodeType: 'Class', fullName: 'anchor', filePath: '', lineNumber: 1,
  })
  return graph
}

function countCollisions(graph: Graph): number {
  const nodes: Array<{ x: number; y: number; size: number }> = []
  graph.forEachNode((_id, a) => {
    if (a.filterHidden === true || a.hidden === true) return
    nodes.push({ x: Number(a.x), y: Number(a.y), size: Number(a.size) })
  })
  let count = 0
  for (let i = 0; i < nodes.length; i += 1) {
    for (let j = i + 1; j < nodes.length; j += 1) {
      const a = nodes[i]!
      const b = nodes[j]!
      if (Math.hypot(b.x - a.x, b.y - a.y) < a.size + b.size + LAYOUT_SCREEN_OVERLAP_GAP_PX) {
        count += 1
      }
    }
  }
  return count
}

describe('post-layout pipeline seam (runPostLayoutPass → collide micro-pass)', () => {
  beforeEach(() => {
    sigmaState.instances.length = 0
    layoutState.instances.length = 0
    delete (globalThis as Record<string, unknown>).__VIBEGRAPH_PRE_SETTLE_HOOK__
  })
  afterEach(() => {
    vi.clearAllTimers()
    vi.clearAllMocks()
    delete (globalThis as Record<string, unknown>).__VIBEGRAPH_PRE_SETTLE_HOOK__
  })

  it('resolves the dense cluster to zero collisions and settles the view', () => {
    const onLayoutSettled = vi.fn()
    const wrapper = mountHarness(makeContainer(VIEWPORT_WIDTH, VIEWPORT_HEIGHT), onLayoutSettled)
    const graph = buildGraph()
    expect(countCollisions(graph)).toBeGreaterThan(0)

    const vm = wrapper.vm as unknown as HarnessVm
    vm.init(graph)
    vm.stopLayout(true)

    expect(countCollisions(graph)).toBe(0)
    const sigma = sigmaState.instances[sigmaState.instances.length - 1]!
    expect(sigma.refresh).toHaveBeenCalled()
    expect(onLayoutSettled).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('fires the pre-settle test hook before the collide pass', () => {
    const hook = vi.fn()
    ;(globalThis as Record<string, unknown>).__VIBEGRAPH_PRE_SETTLE_HOOK__ = hook
    const wrapper = mountHarness(makeContainer(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
    const graph = buildGraph()

    const vm = wrapper.vm as unknown as HarnessVm
    vm.init(graph)
    vm.stopLayout(true)

    expect(hook).toHaveBeenCalledTimes(1)
    expect(hook).toHaveBeenCalledWith(graph)

    wrapper.unmount()
  })

  it('stopLayout without runPostLayout skips the collide pass', () => {
    const wrapper = mountHarness(makeContainer(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
    const graph = buildGraph()
    const before = countCollisions(graph)

    const vm = wrapper.vm as unknown as HarnessVm
    vm.init(graph)
    vm.stopLayout(false)

    expect(countCollisions(graph)).toBe(before)
    wrapper.unmount()
  })
})
