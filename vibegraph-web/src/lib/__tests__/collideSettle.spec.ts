/**
 * Unit tests for the Layer 2 d3-forceCollide micro-pass (collideSettle.ts).
 * Pure-function level: no Sigma harness needed — the pass only touches the
 * graphology graph and the viewport dimensions.
 */
import Graph from 'graphology'
import { describe, expect, it } from 'vitest'
import { runCollideSettle } from '../layout/collideSettle'
import { LAYOUT_SCREEN_OVERLAP_GAP_PX } from '@/lib/runtimeConfig'

const VIEW_W = 1000
const VIEW_H = 600
// collide converges geometrically; tolerate a sub-percent residual.
const DIST_TOLERANCE = 0.02

interface VisibleNode {
  id: string
  x: number
  y: number
  size: number
}

function visibleNodes(graph: Graph): VisibleNode[] {
  const nodes: VisibleNode[] = []
  graph.forEachNode((id, attributes) => {
    if (attributes.filterHidden === true || attributes.hidden === true) return
    nodes.push({
      id,
      x: Number(attributes.x),
      y: Number(attributes.y),
      size: Number(attributes.size),
    })
  })
  return nodes
}

/** Pairs closer than (sizeA + sizeB + gap) × unitsPerPixel — the OLD (stricter) metric. */
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

function buildGraph(specs: Array<{ id: string; x: number; y: number; size: number; hidden?: boolean }>): Graph {
  const graph = new Graph()
  for (const s of specs) {
    graph.addNode(s.id, {
      label: s.id,
      x: s.x,
      y: s.y,
      size: s.size,
      color: '#fff',
      type: 'circle',
      nodeType: 'Class',
      fullName: s.id,
      filePath: '',
      lineNumber: 1,
      ...(s.hidden ? { filterHidden: true } : {}),
    })
  }
  return graph
}

describe('runCollideSettle (Layer 2 d3-forceCollide micro-pass)', () => {
  it('separates two overlapping nodes to the collide radius sum', () => {
    // bbox spans exactly the viewport → unitsPerPixel = 1.
    const graph = buildGraph([
      { id: 'a', x: 0, y: 0, size: 5 },
      { id: 'b', x: 6, y: 0, size: 5 },
      { id: 'anchor', x: VIEW_W, y: VIEW_H, size: 5 },
    ])

    const moved = runCollideSettle(graph, VIEW_W, VIEW_H)

    expect(moved).toBe(true)
    const pa = graph.getNodeAttribute('a', 'x') as number
    const pb = graph.getNodeAttribute('b', 'x') as number
    // collide radius = size + gap per node → centre distance = (5+3)+(5+3) = 16.
    const expected = (5 + LAYOUT_SCREEN_OVERLAP_GAP_PX) * 2
    expect(Math.abs(pb - pa)).toBeCloseTo(expected, 1)
    expect(countCollisions(visibleNodes(graph), 1)).toBe(0)
  })

  it('resolves a dense 10-node cluster to zero residual collisions', () => {
    const cluster = [
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
    const graph = buildGraph([...cluster, { id: 'anchor', x: VIEW_W, y: VIEW_H, size: 5 }])
    const before = countCollisions(visibleNodes(graph), 1)
    expect(before).toBe(45)

    runCollideSettle(graph, VIEW_W, VIEW_H)

    const after = visibleNodes(graph)
    expect(countCollisions(after, 1, 1e-6)).toBe(0)
  })

  it('converts screen-px radii to graph units via unitsPerPixel', () => {
    // bbox (0,0)-(2000,600) over 1000×600 → unitsPerPixel = 2.
    const graph = buildGraph([
      { id: 'small', x: 0, y: 0, size: 4 },
      { id: 'large', x: 5, y: 0, size: 6 },
      { id: 'anchor', x: 2000, y: 600, size: 5 },
    ])

    runCollideSettle(graph, VIEW_W, VIEW_H)

    const d = Math.hypot(
      (graph.getNodeAttribute('large', 'x') as number) - (graph.getNodeAttribute('small', 'x') as number),
      (graph.getNodeAttribute('large', 'y') as number) - (graph.getNodeAttribute('small', 'y') as number),
    )
    // ((4+3) + (6+3)) × 2 = 32 graph units.
    const expected = ((4 + LAYOUT_SCREEN_OVERLAP_GAP_PX) + (6 + LAYOUT_SCREEN_OVERLAP_GAP_PX)) * 2
    expect(d).toBeCloseTo(expected, 0)
    expect(Math.abs(d - expected) / expected).toBeLessThan(DIST_TOLERANCE)
  })

  it('does not move filterHidden nodes and excludes them from the math', () => {
    const graph = buildGraph([
      { id: 'a', x: 0, y: 0, size: 5 },
      { id: 'b', x: 10, y: 0, size: 5 },
      { id: 'anchor', x: VIEW_W, y: VIEW_H, size: 5 },
      // Coincident with a: if counted, a would be kicked off the x-axis.
      { id: 'ghost', x: 0, y: 0, size: 5, hidden: true },
    ])

    runCollideSettle(graph, VIEW_W, VIEW_H)

    // a/b stay collinear (pure along-axis separation) and hidden stays put.
    expect(graph.getNodeAttribute('ghost', 'x')).toBe(0)
    expect(graph.getNodeAttribute('ghost', 'y')).toBe(0)
    const ya = graph.getNodeAttribute('a', 'y') as number
    const yb = graph.getNodeAttribute('b', 'y') as number
    expect(ya).toBeCloseTo(yb, 6)
  })

  it('leaves an already collision-free layout untouched', () => {
    const graph = buildGraph([
      { id: 'a', x: 0, y: 0, size: 5 },
      { id: 'b', x: 100, y: 0, size: 5 },
      { id: 'anchor', x: VIEW_W, y: VIEW_H, size: 5 },
    ])

    const moved = runCollideSettle(graph, VIEW_W, VIEW_H)

    expect(moved).toBe(false)
    expect(graph.getNodeAttribute('a', 'x')).toBe(0)
    expect(graph.getNodeAttribute('b', 'x')).toBe(100)
  })

  it('is a no-op for degenerate viewports', () => {
    const graph = buildGraph([
      { id: 'a', x: 0, y: 0, size: 5 },
      { id: 'b', x: 1, y: 0, size: 5 },
    ])
    expect(runCollideSettle(graph, 0, VIEW_H)).toBe(false)
    expect(graph.getNodeAttribute('a', 'x')).toBe(0)
  })
})
