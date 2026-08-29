/**
 * Layout engine factory (update/graph/qwen/02-ARCHITECTURE.md).
 *
 * 'd3' (grapuco recipe): headless worker runs ngraph macro (or pure d3) then
 * d3 forceCollide(+100) in-sim, 300 ticks, pinned; main thread writes the
 * final positions + graph-unit sizes once and lets Sigma fit.
 */
import type Graph from 'graphology'
import {
  LAYOUT_MACRO,
  LAYOUT_DRAW_SCALE,
  LAYOUT_DRAW_MIN,
  LAYOUT_COLLIDE_PAD,
} from '@/lib/runtimeConfig'
import type { D3WorkerInitMessage } from './d3LayoutWorker'

export interface LayoutPosition {
  id: string
  x: number
  y: number
}

export interface LayoutEngineHandle {
  start(): void
  kill(): void
}

export interface LayoutEngineOptions {
  /** d3 engine: final pinned positions (graph units) + per-node base val. */
  onDone?: (positions: LayoutPosition[], vals: number[]) => void
}

export function createLayoutEngine(
  graph: Graph,
  options: LayoutEngineOptions = {},
): LayoutEngineHandle {
  return createD3Engine(graph, options)
}

/** Grapuco-recipe worker: macro (ngraph|d3) + d3 forceCollide, 300 ticks, pinned. */
function createD3Engine(graph: Graph, options: LayoutEngineOptions): LayoutEngineHandle {
  let worker: Worker | null = null
  return {
    start() {
      worker = new Worker(new URL('./d3LayoutWorker.ts', import.meta.url), { type: 'module' })
      const ids = graph.nodes()
      const nodes = ids.map((id) => ({
        id,
        x: Number(graph.getNodeAttribute(id, 'x')) || 0,
        y: Number(graph.getNodeAttribute(id, 'y')) || 0,
        val: Number(graph.getNodeAttribute(id, 'layoutVal')) || Number(graph.getNodeAttribute(id, 'size')) || 8,
      }))
      const vals = nodes.map((n) => n.val)
      const links = graph.edges().map((edge) => {
        const [source, target] = graph.extremities(edge)
        return { source, target }
      })
      worker.onmessage = (event: MessageEvent) => {
        const msg = event.data as { type: string; ids?: string[]; xs?: number[]; ys?: number[] }
        if (msg.type !== 'done' || !msg.ids || !msg.xs || !msg.ys) return
        const positions: LayoutPosition[] = msg.ids.map((id, i) => ({
          id,
          x: msg.xs![i] ?? 0,
          y: msg.ys![i] ?? 0,
        }))
        options.onDone?.(positions, vals)
      }
      const message: D3WorkerInitMessage = {
        type: 'init',
        nodes,
        links,
        macro: LAYOUT_MACRO,
        drawScale: LAYOUT_DRAW_SCALE,
        drawMin: LAYOUT_DRAW_MIN,
        collidePad: LAYOUT_COLLIDE_PAD,
      }
      worker.postMessage(message)
    },
    kill() {
      if (!worker) return
      worker.postMessage({ type: 'stop' })
      worker.terminate()
      worker = null
    },
  }
}
