/**
 * Layer 2 — d3-forceCollide micro-pass (headless, bounded).
 *
 * Takes the macro-layout's raw coordinates and resolves node overlaps with
 * d3-force's quadtree-accelerated forceCollide — the same collision algorithm
 * grapuco runs inside its simulation (measured in
 * update/graph/grapuco-evidence/VERIFICATION-2026-08-18.md) — applied here as a
 * short post-pass so it works for BOTH macro engines (ngraph default, fa2
 * kill-switch).
 *
 * Units: the pass works in fit-screen space (1 unit = 1 px at the fit view),
 * where a node's radius is simply `size + gap` screen px. With
 * ZOOM_SIZE_POWER = 1.0 that ratio is zoom-invariant, so solving overlap once
 * here is correct at every zoom level. Positions are converted back to graph
 * units afterwards.
 *
 * NOTE: collide cannot create area — if the configured radii exceed the
 * viewport budget the density-adaptive sizing (BLOB-1, applyDensitySizeScale)
 * shrinks them first so this pass only untangles local overlaps and size
 * heterogeneity while preserving the organic macro silhouette.
 */
import { forceCollide, forceSimulation, type SimulationNodeDatum } from 'd3-force'
import type Graph from 'graphology'
import {
  LAYOUT_SCREEN_OVERLAP_ENABLED,
  LAYOUT_SCREEN_OVERLAP_GAP_PX,
  COLLIDE_ITERATIONS,
  COLLIDE_STRENGTH,
} from '@/lib/runtimeConfig'

interface CollideNode extends SimulationNodeDatum {
  id: string
  /** Collision radius in fit-screen px (draw radius + gap). */
  r: number
  /** Original graph-space coordinates (to detect movement / write back). */
  ox: number
  oy: number
}

const MOVE_EPSILON = 1e-6

/**
 * Run the bounded collide relaxation over the graph's visible nodes.
 * Returns true if any node moved.
 */
export function runCollideSettle(
  graph: Graph,
  viewportWidth: number,
  viewportHeight: number,
): boolean {
  if (!LAYOUT_SCREEN_OVERLAP_ENABLED || graph.order < 2) return false
  if (viewportWidth <= 0 || viewportHeight <= 0) return false

  const gap = LAYOUT_SCREEN_OVERLAP_GAP_PX
  const nodes: CollideNode[] = []
  let minX = Number.POSITIVE_INFINITY
  let maxX = Number.NEGATIVE_INFINITY
  let minY = Number.POSITIVE_INFINITY
  let maxY = Number.NEGATIVE_INFINITY

  graph.forEachNode((id, attributes) => {
    if (attributes.filterHidden === true || attributes.hidden === true) return
    const x = Number(attributes.x)
    const y = Number(attributes.y)
    const size = Number(attributes.size ?? 0)
    if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(size) || size <= 0) {
      return
    }
    minX = Math.min(minX, x)
    maxX = Math.max(maxX, x)
    minY = Math.min(minY, y)
    maxY = Math.max(maxY, y)
    nodes.push({ id, x, y, r: size + gap, ox: x, oy: y })
  })
  if (nodes.length < 2) return false

  const width = maxX - minX
  const height = maxY - minY
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return false
  }

  // graph units → fit-screen px (the same unitsPerPixel conversion the old
  // settle pass used, now exact and zoom-invariant with p = 1).
  const unitsPerPixel = Math.max(width / viewportWidth, height / viewportHeight)
  for (const node of nodes) {
    node.x = node.ox / unitsPerPixel
    node.y = node.oy / unitsPerPixel
  }

  const simulation = forceSimulation<CollideNode>(nodes)
    .force('collide', forceCollide<CollideNode>((node) => node.r).strength(COLLIDE_STRENGTH).iterations(2))
    // Keep alpha at 1 for the whole bounded budget so every tick pushes with
    // full strength; COLLIDE_ITERATIONS is the frame-budget knob.
    .alphaDecay(0)
    // Pure position relaxation: heavy damping so pairs converge onto the
    // collide radius without inertial overshoot (velocityDecay(1) would freeze
    // all motion in d3 — the integration factor is 1 − decay).
    .velocityDecay(0.9)
    .stop()
  for (let i = 0; i < COLLIDE_ITERATIONS; i += 1) simulation.tick()

  let moved = false
  for (const node of nodes) {
    const nx = (node.x ?? 0) * unitsPerPixel
    const ny = (node.y ?? 0) * unitsPerPixel
    if (Math.abs(nx - node.ox) > MOVE_EPSILON || Math.abs(ny - node.oy) > MOVE_EPSILON) {
      moved = true
    }
    graph.mergeNodeAttributes(node.id, { x: nx, y: ny })
  }
  return moved
}
