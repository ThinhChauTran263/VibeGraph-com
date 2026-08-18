/**
 * Unit tests for the screen-space overlap-resolution pass (`settleScreenOverlaps`)
 * in useSigma — the assertion "zero remaining collisions after settle" required by
 * update/graph/05-IMPLEMENTATION-PLAN.md §Testing.
 *
 * SEAM: `settleScreenOverlaps` is a private closure inside `useSigma` (no export).
 * Its only production path is the post-layout pipeline (T8(b) removed the
 * graphology-noverlap worker entirely — settle is now THE de-overlap pass):
 *
 *   stopLayout(runPostLayout = true)
 *     → runPostLayoutPass()
 *     → normalizeLayout / spreadLayoutClusters / centerLayout
 *     → settleScreenOverlaps(graph)
 *
 * These tests drive exactly that seam with the same mocked-Sigma harness as
 * useSigma.spec.ts.
 *
 * runtimeConfig is overridden the same way the `VITE_*` env vars would:
 * - LAYOUT_NORMALIZE_SPAN = 0 disables the pre-pass rescale (documented env
 *   disable, "Set 0 to disable"), so synthetic positions reach the settle pass
 *   exactly as constructed and unitsPerPixel is exactly known.
 * - LAYOUT_SCREEN_OVERLAP_ITERATIONS is raised from the default 10 to 200: the
 *   pass converges geometrically (each pass removes STRENGTH = 70% of every
 *   remaining overlap), so under the default cap a deficit shrinks only to
 *   deficit₀·0.3^10 ≈ 3e-6·deficit₀ — not literally zero. The cap is a
 *   frame-budget knob, not part of the math under test; 200 passes let the
 *   relaxation converge to the float64 plateau (~1e-14) so "zero remaining
 *   collisions" can be asserted strictly (within the sub-pixel ZERO_EPSILON).
 */

import Graph from 'graphology'
import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSigma } from '../useSigma'
import {
  LAYOUT_SCREEN_OVERLAP_GAP_PX,
  LAYOUT_SCREEN_OVERLAP_ITERATIONS,
} from '@/lib/runtimeConfig'

const VIEWPORT_WIDTH = 1000
const VIEWPORT_HEIGHT = 600

// The settle pass converges geometrically; Float32Array shift storage plus
// float64 position addition freeze progress once a per-pass shift drops below
// the positional ULP — long before the deficit is literally 0. "Zero remaining
// collisions" therefore means: no pair closer than its target by more than this
// sub-pixel epsilon (1e-6 graph units ≈ 1e-6 px at unitsPerPixel = 1).
const ZERO_EPSILON = 1e-6

vi.mock('@/lib/runtimeConfig', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/runtimeConfig')>()
  return {
    ...actual,
    LAYOUT_NORMALIZE_SPAN: 0,
    LAYOUT_BRANCH_ENABLED: false,
    LAYOUT_SCREEN_OVERLAP_ITERATIONS: 200,
    // jsdom has no Worker: pin the fa2 kill-switch engine for this harness.
    LAYOUT_ENGINE: 'fa2' as const,
  }
})

interface MockSigmaInstance {
  settings: Record<string, unknown>
}

interface MockLayoutInstance {
  params: Record<string, unknown>
  start: ReturnType<typeof vi.fn>
  kill: ReturnType<typeof vi.fn>
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

interface HarnessVm {
  init: (graph: Graph) => void
  stopLayout: (runPostLayout?: boolean) => void
  dispose: () => void
}

function makeContainer(width: number, height: number): HTMLDivElement {
  const el = document.createElement('div')
  // jsdom never lays elements out; settleScreenOverlaps reads the real box, so
  // give the container a known viewport size.
  Object.defineProperty(el, 'clientWidth', { value: width, configurable: true })
  Object.defineProperty(el, 'clientHeight', { value: height, configurable: true })
  return el
}

function mountHarness(container: HTMLDivElement) {
  const Harness = defineComponent({
    setup() {
      const containerRef = ref<HTMLDivElement | null>(container)
      const api = useSigma({ container: containerRef })
      return { ...api }
    },
    template: '<div />',
  })
  return mount(Harness)
}

interface NodeSpec {
  id: string
  x: number
  y: number
  size: number
  filterHidden?: boolean
  hidden?: boolean
}

function buildGraph(specs: NodeSpec[]): Graph {
  const graph = new Graph({ type: 'directed', multi: true })
  for (const spec of specs) {
    graph.addNode(spec.id, {
      label: spec.id,
      x: spec.x,
      y: spec.y,
      size: spec.size,
      color: '#fff',
      type: 'circle',
      nodeType: 'Class',
      fullName: `com.example.${spec.id}`,
      filePath: `${spec.id}.java`,
      lineNumber: 1,
      ...(spec.filterHidden !== undefined ? { filterHidden: spec.filterHidden } : {}),
      ...(spec.hidden !== undefined ? { hidden: spec.hidden } : {}),
    })
  }
  return graph
}

interface VisibleNode {
  id: string
  x: number
  y: number
  size: number
}

/** Mirror of settleScreenOverlaps' visibility filter (hidden nodes never enter the pass). */
function visibleNodes(graph: Graph): VisibleNode[] {
  const nodes: VisibleNode[] = []
  graph.forEachNode((id, attributes) => {
    if (attributes.filterHidden === true || attributes.hidden === true) return
    const x = Number(attributes.x)
    const y = Number(attributes.y)
    const size = Number(attributes.size ?? 0)
    if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(size) || size <= 0) {
      return
    }
    nodes.push({ id, x, y, size })
  })
  return nodes
}

/** Mirror of the pass' px→graph-unit factor: max(bboxW / vpW, bboxH / vpH). */
function unitsPerPixel(
  nodes: VisibleNode[],
  viewportWidth: number,
  viewportHeight: number,
): number {
  let minX = Number.POSITIVE_INFINITY
  let maxX = Number.NEGATIVE_INFINITY
  let minY = Number.POSITIVE_INFINITY
  let maxY = Number.NEGATIVE_INFINITY
  for (const node of nodes) {
    minX = Math.min(minX, node.x)
    maxX = Math.max(maxX, node.x)
    minY = Math.min(minY, node.y)
    maxY = Math.max(maxY, node.y)
  }
  return Math.max((maxX - minX) / viewportWidth, (maxY - minY) / viewportHeight)
}

/**
 * Count pairs closer than the pass' target separation
 * (sizeA + sizeB + GAP_PX) × unitsPerPixel. epsilon > 0 tolerates the
 * sub-pixel convergence residue documented at ZERO_EPSILON.
 */
function countCollisions(nodes: VisibleNode[], upp: number, epsilon = 0): number {
  let count = 0
  for (let i = 0; i < nodes.length; i += 1) {
    for (let j = i + 1; j < nodes.length; j += 1) {
      const a = nodes[i]!
      const b = nodes[j]!
      const target = (a.size + b.size + LAYOUT_SCREEN_OVERLAP_GAP_PX) * upp
      if (Math.hypot(b.x - a.x, b.y - a.y) < target - epsilon) count += 1
    }
  }
  return count
}

function position(graph: Graph, id: string): { x: number; y: number } {
  return {
    x: Number(graph.getNodeAttribute(id, 'x')),
    y: Number(graph.getNodeAttribute(id, 'y')),
  }
}

function distanceBetween(graph: Graph, a: string, b: string): number {
  const pa = position(graph, a)
  const pb = position(graph, b)
  return Math.hypot(pb.x - pa.x, pb.y - pa.y)
}

/** init() then drive the post-layout pipeline all the way into the settle pass. */
function runSettle(wrapper: ReturnType<typeof mountHarness>, graph: Graph): void {
  const vm = wrapper.vm as unknown as HarnessVm
  vm.init(graph)
  vm.stopLayout(true)
}

describe('useSigma settleScreenOverlaps (screen-space overlap resolution)', () => {
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

  it('separates two overlapping nodes until zero collisions remain', () => {
    // Guard: the runtimeConfig override above is what lets the geometric
    // relaxation converge fully (default cap of 10 leaves a ~3e-6 residue).
    expect(LAYOUT_SCREEN_OVERLAP_ITERATIONS).toBeGreaterThanOrEqual(100)

    const wrapper = mountHarness(makeContainer(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
    const graph = buildGraph([
      { id: 'a', x: 0, y: 0, size: 5 },
      { id: 'b', x: 10, y: 0, size: 5 },
      // Anchor node fixes the bounding box (and keeps it 2-dimensional, which
      // the pass requires) without interacting: hypot(1000, 600) ≫ every target.
      { id: 'anchor', x: VIEWPORT_WIDTH, y: VIEWPORT_HEIGHT, size: 5 },
    ])

    const before = visibleNodes(graph)
    const upp = unitsPerPixel(before, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
    expect(upp).toBe(1)
    expect(countCollisions(before, upp)).toBe(1)
    const target = (5 + 5 + LAYOUT_SCREEN_OVERLAP_GAP_PX) * upp

    runSettle(wrapper, graph)

    // Centre-to-centre distance reaches the full target (sum of radii + gap).
    expect(distanceBetween(graph, 'a', 'b')).toBeCloseTo(target, 8)
    // Zero remaining collisions anywhere in the graph.
    expect(countCollisions(visibleNodes(graph), upp, ZERO_EPSILON)).toBe(0)

    wrapper.unmount()
  })

  it('resolves a dense 10-node cluster of deliberately overlapping positions to zero collisions', () => {
    const wrapper = mountHarness(makeContainer(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
    // 10 nodes crammed into a 6×6 box: every pair sits well inside the
    // (5 + 5 + 3) × 1 = 13-unit target separation → 45/45 pairs collide.
    const cluster: NodeSpec[] = [
      { id: 'n0', x: 0, y: 0, size: 5 },
      { id: 'n1', x: 2, y: 1, size: 5 },
      { id: 'n2', x: 4, y: 0, size: 5 },
      { id: 'n3', x: 1, y: 3, size: 5 },
      { id: 'n4', x: 3, y: 3, size: 5 },
      { id: 'n5', x: 5, y: 2, size: 5 },
      { id: 'n6', x: 0, y: 5, size: 5 },
      { id: 'n7', x: 2, y: 6, size: 5 },
      { id: 'n8', x: 4, y: 5, size: 5 },
      { id: 'n9', x: 6, y: 1, size: 5 },
    ]
    const graph = buildGraph([
      ...cluster,
      { id: 'anchor', x: VIEWPORT_WIDTH, y: VIEWPORT_HEIGHT, size: 5 },
    ])

    const before = visibleNodes(graph)
    const upp = unitsPerPixel(before, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
    expect(upp).toBe(1)
    const beforeCollisions = countCollisions(before, upp)
    expect(beforeCollisions).toBe(45)

    runSettle(wrapper, graph)

    const after = visibleNodes(graph)
    const remaining = countCollisions(after, upp, ZERO_EPSILON)
    // T12: assert ZERO residual collisions and never weaken silently — if the
    // iteration cap ever leaves residue, this reports the exact count.
    expect(remaining, `expected 0 residual collisions but found ${remaining}`).toBe(0)
    expect(countCollisions(after, upp, ZERO_EPSILON)).toBeLessThan(beforeCollisions)

    wrapper.unmount()
  })

  it('does not move filterHidden/hidden nodes and excludes them from the collision math', () => {
    const wrapper = mountHarness(makeContainer(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
    const graph = buildGraph([
      { id: 'a', x: 0, y: 0, size: 5 },
      { id: 'b', x: 10, y: 0, size: 5 },
      { id: 'anchor', x: VIEWPORT_WIDTH, y: VIEWPORT_HEIGHT, size: 5 },
      // Sits exactly on top of `a`; if it were counted, the deterministic
      // coincident-node jitter would kick `a` off the x-axis.
      { id: 'ghost', x: 0, y: 0, size: 5, filterHidden: true },
      // Far outside the visible bbox; if it were counted, unitsPerPixel would
      // balloon to 5000/600 ≈ 8.33 and the a–b target to ~108 instead of 13.
      { id: 'farHidden', x: 5000, y: 5000, size: 5, hidden: true },
    ])

    // Snapshot positions right before the settle pass runs (after the
    // centerLayout translation pre-pass) to isolate what settle itself does.
    let ghostBefore = { x: 0, y: 0 }
    let farBefore = { x: 0, y: 0 }
    ;(globalThis as Record<string, unknown>).__VIBEGRAPH_PRE_SETTLE_HOOK__ = (g: Graph) => {
      ghostBefore = position(g, 'ghost')
      farBefore = position(g, 'farHidden')
    }

    runSettle(wrapper, graph)

    // filterHidden / hidden nodes are not moved by the settle pass.
    expect(position(graph, 'ghost')).toEqual(ghostBefore)
    expect(position(graph, 'farHidden')).toEqual(farBefore)

    // And they are not counted: the visible pair settles to the unitsPerPixel=1
    // target. If `ghost` (coincident with `a`) had been counted, the deterministic
    // coincident-node jitter would kick `a` off the a–b line, so assert `a` and
    // `b` remain exactly collinear (pure along-axis separation, no off-axis kick).
    // (centerLayout translates everything — hidden nodes included — so we check
    // relative alignment, not an absolute y of 0.)
    const target = (5 + 5 + LAYOUT_SCREEN_OVERLAP_GAP_PX) * 1
    expect(distanceBetween(graph, 'a', 'b')).toBeCloseTo(target, 8)
    expect(position(graph, 'a').y).toBeCloseTo(position(graph, 'b').y, 10)

    wrapper.unmount()
  })

  it('converts screen-px radii to graph units (radius = size × unitsPerPixel)', () => {
    const wrapper = mountHarness(makeContainer(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
    // bbox spans (0,0)–(2000,600) over a 1000×600 viewport → unitsPerPixel = 2.
    const graph = buildGraph([
      { id: 'small', x: 0, y: 0, size: 4 },
      { id: 'large', x: 5, y: 0, size: 6 },
      { id: 'anchor', x: 2000, y: 600, size: 5 },
    ])

    const before = visibleNodes(graph)
    const upp = unitsPerPixel(before, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
    expect(upp).toBe(2)
    expect(countCollisions(before, upp)).toBe(1)

    runSettle(wrapper, graph)

    // The pass pushes the pair apart until the centre distance equals the sum of
    // the GRAPH-space radii — screen size × unitsPerPixel — plus the gap, also
    // converted: 4·2 + 6·2 + GAP·2 (with the default GAP = 3 that is 26; the
    // expectation is derived from the config constant, not hardcoded, because the
    // gap is a tunable knob). This asserts the conversion math as used.
    const expectedSeparation = 4 * upp + 6 * upp + LAYOUT_SCREEN_OVERLAP_GAP_PX * upp
    expect(expectedSeparation).toBe((4 + 6 + LAYOUT_SCREEN_OVERLAP_GAP_PX) * upp)
    expect(distanceBetween(graph, 'small', 'large')).toBeCloseTo(expectedSeparation, 8)

    wrapper.unmount()
  })

  it('leaves an already collision-free layout untouched (zero-collision early exit)', () => {
    const wrapper = mountHarness(makeContainer(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
    const graph = buildGraph([
      { id: 'a', x: 0, y: 0, size: 5 },
      { id: 'b', x: 100, y: 0, size: 5 },
      { id: 'anchor', x: VIEWPORT_WIDTH, y: VIEWPORT_HEIGHT, size: 5 },
    ])

    runSettle(wrapper, graph)

    // Only centerLayout translated the bbox (centre (500,300)) before the settle
    // pass; the pass itself broke out on 0 collisions and wrote nothing back.
    expect(position(graph, 'a')).toEqual({ x: -500, y: -300 })
    expect(position(graph, 'b')).toEqual({ x: -400, y: -300 })
    expect(position(graph, 'anchor')).toEqual({ x: 500, y: 300 })

    wrapper.unmount()
  })
})
