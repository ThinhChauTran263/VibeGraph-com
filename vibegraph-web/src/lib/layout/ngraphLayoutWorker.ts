/**
 * Layer 1 — ngraph macro-layout Web Worker (headless, non-blocking).
 *
 * Receives the graph's seeded positions, runs ngraph.forcelayout (Barnes-Hut
 * n-body + springs) on a ~16 ms timer loop and streams progressive position
 * batches back to the main thread so Sigma can animate the spread without
 * ever blocking the UI. Collision is NOT handled here — the Layer 2
 * d3-forceCollide micro-pass (collideSettle.ts) resolves overlaps after the
 * worker stops.
 *
 * Protocol:
 *   main → worker: { type:'init', ids, xs, ys, edges, settings } | { type:'stop' }
 *   worker → main: { type:'positions', xs, ys, tick } (every 10 ticks) | { type:'stopped' }
 */
import createGraph, { type Graph as NGraph } from 'ngraph.graph'
import createLayout, { type Layout } from 'ngraph.forcelayout'

export interface NgraphInitMessage {
  type: 'init'
  ids: string[]
  xs: number[]
  ys: number[]
  edges: Array<{ from: string; to: string }>
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

const TICKS_PER_LOOP = 2
const LOOP_MS = 16
const POST_EVERY_TICKS = 10

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
  const graph = createGraph()
  ids = msg.ids
  for (const id of ids) graph.addNode(id)
  for (const edge of msg.edges) {
    if (graph.hasNode(edge.from) && graph.hasNode(edge.to)) graph.addLink(edge.from, edge.to)
  }

  layout = createLayout(graph, { ...msg.settings })
  for (let i = 0; i < ids.length; i += 1) {
    layout.setNodePosition(ids[i]!, msg.xs[i] ?? 0, msg.ys[i] ?? 0)
  }

  tick = 0
  postPositions()
  timer = setInterval(() => {
    if (!layout) return
    for (let i = 0; i < TICKS_PER_LOOP; i += 1) layout.step()
    tick += TICKS_PER_LOOP
    if (tick % POST_EVERY_TICKS === 0) postPositions()
  }, LOOP_MS)
}
