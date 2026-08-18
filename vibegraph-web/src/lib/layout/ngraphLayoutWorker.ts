/**
 * Layer 1 — ngraph macro-layout Web Worker with in-simulation d3-forceCollide.
 *
 * Headless ngraph.forcelayout streams progressive position batches back to the
 * main thread so Sigma animates the spread without ever blocking the UI.
 *
 * CRITICAL DESIGN (learned from grapuco, measured in
 * update/graph/grapuco-evidence/VERIFICATION-2026-08-18.md): collision must be
 * a constraint INSIDE the simulation, not a post-pass. A post-hoc de-overlap
 * cannot create area (the scale-invariance trap: expanding positions and
 * re-fitting the camera cancel exactly), so a dense core stays a packed sheet.
 * Grapuco runs forceCollide with padding 2–4× the draw radius inside its sim,
 * which makes the macro layout itself grow the core until every node has
 * room. We do the same: after each ngraph step batch, one d3-forceCollide tick
 * (quadtree-accelerated, headless) pushes overlapping bodies apart and the
 * adjusted positions are fed back into ngraph so springs/repulsion see the
 * expanded core.
 *
 * Protocol:
 *   main → worker: { type:'init', ids, xs, ys, sizes, edges, gapPx,
 *                    viewportWidth, viewportHeight, settings } | { type:'stop' }
 *   worker → main: { type:'positions', xs, ys, tick } (every 10 ticks)
 *                | { type:'stopped' }
 */
import createGraph, { type Graph as NGraph } from 'ngraph.graph'
import createLayout, { type Layout } from 'ngraph.forcelayout'
import { forceCollide, forceSimulation, type SimulationNodeDatum } from 'd3-force'

export interface NgraphInitMessage {
  type: 'init'
  ids: string[]
  xs: number[]
  ys: number[]
  /** Node draw radius in fit-screen px (the `size` attribute). */
  sizes: number[]
  edges: Array<{ from: string; to: string }>
  /** Screen-px gap added to every collide radius. */
  gapPx: number
  viewportWidth: number
  viewportHeight: number
  settings: {
    timeStep: number
    springLength: number
    springCoefficient: number
    dragCoefficient: number
    gravity: number
    theta: number
  }
}

export interface NgraphStopMessage {
  type: 'stop'
}

interface SimNode extends SimulationNodeDatum {
  /** Collide radius in graph units (recomputed as the span becomes known). */
  r: number
}

const TICKS_PER_LOOP = 2
const LOOP_MS = 16
const POST_EVERY_TICKS = 10
/** Grapuco-calibrated pad: collide radius = 3× draw radius + gap (they used ~2–4×). */
const COLLIDE_PAD_FACTOR = 3

const scope = self as unknown as {
  onmessage: ((e: MessageEvent<NgraphInitMessage | NgraphStopMessage>) => void) | null
  postMessage: (msg: unknown) => void
}

let layout: Layout<NGraph> | null = null
let ids: string[] = []
let timer: ReturnType<typeof setInterval> | null = null
let tick = 0

function stopLoop(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function postPositions(): void {
  if (!layout) return
  const xs = new Array<number>(ids.length)
  const ys = new Array<number>(ids.length)
  for (let i = 0; i < ids.length; i += 1) {
    const pos = layout.getNodePosition(ids[i]!)
    xs[i] = pos.x
    ys[i] = pos.y
  }
  scope.postMessage({ type: 'positions', xs, ys, tick })
}

scope.onmessage = (e) => {
  const msg = e.data
  if (msg.type === 'stop') {
    stopLoop()
    scope.postMessage({ type: 'stopped' })
    return
  }

  stopLoop()
  const init = msg
  const graph = createGraph()
  ids = init.ids
  for (const id of ids) graph.addNode(id)
  for (const edge of init.edges) {
    if (graph.hasNode(edge.from) && graph.hasNode(edge.to)) graph.addLink(edge.from, edge.to)
  }

  layout = createLayout(graph, { ...init.settings })
  for (let i = 0; i < ids.length; i += 1) {
    layout.setNodePosition(ids[i]!, init.xs[i] ?? 0, init.ys[i] ?? 0)
  }

  // ── In-simulation collision (Layer 2, running inside the Layer 1 loop) ──
  const simNodes: SimNode[] = ids.map((id, i) => ({
    x: init.xs[i] ?? 0,
    y: init.ys[i] ?? 0,
    r: 1,
  }))
  const collideSim = forceSimulation<SimNode>(simNodes)
    .force(
      'collide',
      forceCollide<SimNode>((node) => node.r).strength(1).iterations(2),
    )
    .velocityDecay(0.3)
    .alphaDecay(0) // constant-strength constraint while the macro layout runs
    .stop()
  // d3-forceCollide CACHES radii at initialize; re-setting the radius
  // accessor is the only way to make it re-read node.r after we rescale.
  const collideForce = collideSim.force('collide') as ReturnType<
    typeof forceCollide<SimNode>
  >

  // Collide radii live in graph units; the graph-units-per-screen-px factor is
  // derived from the live bounding box vs the viewport (same conversion the
  // post-pass uses) and refreshed as the layout spreads.
  let unitsPerPx = 1
  function refreshCollideRadii(): void {
    let minX = Infinity
    let maxX = -Infinity
    let minY = Infinity
    let maxY = -Infinity
    for (const node of simNodes) {
      const x = node.x ?? 0
      const y = node.y ?? 0
      if (x < minX) minX = x
      if (x > maxX) maxX = x
      if (y < minY) minY = y
      if (y > maxY) maxY = y
    }
    const w = maxX - minX
    const h = maxY - minY
    if (w > 0 && h > 0 && init.viewportWidth > 0 && init.viewportHeight > 0) {
      unitsPerPx = Math.max(w / init.viewportWidth, h / init.viewportHeight)
    }
    for (let i = 0; i < simNodes.length; i += 1) {
      const size = init.sizes[i] ?? 4
      simNodes[i]!.r = (COLLIDE_PAD_FACTOR * size + init.gapPx) * unitsPerPx
    }
    collideForce.radius((node) => node.r) // re-read the rescaled radii
  }
  refreshCollideRadii()

  tick = 0
  postPositions()
  timer = setInterval(() => {
    if (!layout) return
    for (let i = 0; i < TICKS_PER_LOOP; i += 1) layout.step()

    // Sync ngraph → collide bodies, run one constraint tick, feed the
    // de-overlapped positions back into ngraph.
    for (let i = 0; i < ids.length; i += 1) {
      const pos = layout.getNodePosition(ids[i]!)
      simNodes[i]!.x = pos.x
      simNodes[i]!.y = pos.y
    }
    if (tick % POST_EVERY_TICKS === 0) refreshCollideRadii()
    // Two constraint ticks per loop so collide holds its own against the
    // two ngraph steps (springs otherwise re-compress linked cores).
    collideSim.tick()
    collideSim.tick()
    for (let i = 0; i < ids.length; i += 1) {
      layout.setNodePosition(ids[i]!, simNodes[i]!.x ?? 0, simNodes[i]!.y ?? 0)
    }

    tick += TICKS_PER_LOOP
    if (tick % POST_EVERY_TICKS === 0) postPositions()
  }, LOOP_MS)
}
