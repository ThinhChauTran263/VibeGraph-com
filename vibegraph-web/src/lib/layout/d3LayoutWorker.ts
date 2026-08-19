/**
 * Layer 1 — headless d3-force layout worker, replicating grapuco's measured
 * production recipe (read from their bundle 0qy4-68qybnav.js):
 *
 *   forceSimulation(nodes)
 *     .force('charge',    forceManyBody().strength(-3000).distanceMax(5000))
 *     .force('collision', forceCollide(n => Math.max(3*(n.val||8),10) + 100))
 *     .force('link',      forceLink(links).id(d=>d.id).distance(150))
 *     .force('center',    forceCenter(0,0).strength(0.02))
 *     .alphaDecay(0.02).velocityDecay(0.3).stop()
 *   for (i<300) sim.tick()   // synchronous, then pin (fx/fy) → static graph
 *
 * Collision is a HARD constraint INSIDE the simulation with a FIXED +100
 * padding in the same unit system as node draw radii — this is what guarantees
 * zero visual overlap at every project size and every zoom (no normalize step
 * exists here, so the guarantee survives to the camera fit).
 *
 * Macro slot: 'd3' (default, exactly grapuco) or 'ngraph' (hybrid fallback:
 * ngraph.forcelayout shapes the skeleton first, then a collide-only d3 sim
 * untangles overlaps — sequential, never concurrent).
 *
 * Protocol:
 *   main → worker: { type:'init', nodes:[{id,x,y,val}], links:[{source,target}],
 *                    macro:'d3'|'ngraph' }
 *                | { type:'stop' }
 *   worker → main: { type:'progress', xs, ys } (mid-run, optional)
 *                | { type:'done', xs, ys }
 */
import {
  forceCenter,
  forceCollide,
  forceLink,
  forceManyBody,
  forceSimulation,
  type Simulation,
  type SimulationNodeDatum,
} from 'd3-force'
import createNgraphGraph from 'ngraph.graph'
import createNgraphLayout from 'ngraph.forcelayout'

export interface D3WorkerInitMessage {
  type: 'init'
  nodes: Array<{ id: string; x: number; y: number; val: number }>
  links: Array<{ source: string; target: string }>
  macro: 'd3' | 'ngraph'
}

export interface D3WorkerStopMessage {
  type: 'stop'
}

interface SimNode extends SimulationNodeDatum {
  id: string
  val: number
}

const TICKS = 300
const CHARGE_STRENGTH = -3000
const CHARGE_DISTANCE_MAX = 5000
const LINK_DISTANCE = 150
const CENTER_STRENGTH = 0.02
const ALPHA_DECAY = 0.02
const VELOCITY_DECAY = 0.3
const COLLIDE_PAD = 100

const scope = self as unknown as {
  onmessage: ((e: MessageEvent<D3WorkerInitMessage | D3WorkerStopMessage>) => void) | null
  postMessage: (msg: unknown) => void
}

const drawRadius = (n: SimNode): number => Math.max(3 * (n.val || 8), 10)
const collideRadius = (n: SimNode): number => drawRadius(n) + COLLIDE_PAD

/** Collide-only d3 refinement used by the ngraph macro slot. */
function runCollidePhase(nodes: SimNode[], links: Array<{ source: string; target: string }>): void {
  const sim = forceSimulation<SimNode>(nodes)
    .force('collision', forceCollide<SimNode>(collideRadius))
    .alphaDecay(ALPHA_DECAY)
    .velocityDecay(VELOCITY_DECAY)
    .stop()
  void links
  for (let i = 0; i < TICKS; i += 1) sim.tick()
}

/** ngraph.forcelayout macro skeleton (hybrid fallback slot). */
function runNgraphMacro(
  nodes: SimNode[],
  links: Array<{ source: string; target: string }>,
): void {
  const ng = createNgraphGraph()
  for (const n of nodes) ng.addNode(n.id)
  for (const l of links) {
    if (ng.hasNode(l.source) && ng.hasNode(l.target)) ng.addLink(l.source, l.target)
  }
  const layout = createNgraphLayout(ng, {
    timeStep: 0.08,
    springLength: LINK_DISTANCE,
    springCoefficient: 0.0008,
    dragCoefficient: 0.02,
    gravity: -1.2,
    theta: 0.8,
  })
  const byId = new Map(nodes.map((n) => [n.id, n]))
  for (const n of nodes) layout.setNodePosition(n.id, n.x ?? 0, n.y ?? 0)
  for (let i = 0; i < TICKS; i += 1) layout.step()
  for (const n of nodes) {
    const pos = layout.getNodePosition(n.id)
    n.x = pos.x
    n.y = pos.y
  }
  byId.clear()
}

scope.onmessage = (e) => {
  const msg = e.data
  if (msg.type === 'stop') return

  const nodes: SimNode[] = msg.nodes.map((n) => ({
    id: n.id,
    val: n.val,
    x: n.x,
    y: n.y,
  }))
  const links = msg.links

  if (msg.macro === 'ngraph') {
    runNgraphMacro(nodes, links)
    runCollidePhase(nodes, links)
  } else {
    const sim: Simulation<SimNode, undefined> = forceSimulation<SimNode>(nodes)
      .force('charge', forceManyBody<SimNode>().strength(CHARGE_STRENGTH).distanceMax(CHARGE_DISTANCE_MAX))
      .force('collision', forceCollide<SimNode>(collideRadius))
      .force(
        'link',
        forceLink<SimNode, { source: string; target: string } & { index?: number }>(
          links as never,
        )
          .id((d) => d.id)
          .distance(LINK_DISTANCE),
      )
      .force('center', forceCenter<SimNode>(0, 0).strength(CENTER_STRENGTH))
      .alphaDecay(ALPHA_DECAY)
      .velocityDecay(VELOCITY_DECAY)
      .stop()
    for (let i = 0; i < TICKS; i += 1) sim.tick()
  }

  // Pin everything → static graph, zero CPU after this message.
  const ids = new Array<string>(nodes.length)
  const xs = new Array<number>(nodes.length)
  const ys = new Array<number>(nodes.length)
  for (let i = 0; i < nodes.length; i += 1) {
    const n = nodes[i]!
    n.fx = n.x
    n.fy = n.y
    ids[i] = n.id
    xs[i] = n.x ?? 0
    ys[i] = n.y ?? 0
  }
  scope.postMessage({ type: 'done', ids, xs, ys })
}
