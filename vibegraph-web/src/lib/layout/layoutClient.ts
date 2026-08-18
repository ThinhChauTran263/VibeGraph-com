/**
 * Layout engine factory (Layer 1 of the 3-layer hybrid layout).
 *
 * Returns a uniform `{start, kill}` handle over two macro-layout engines:
 *  - 'ngraph' (default): headless ngraph.forcelayout inside a Web Worker;
 *    progressive position batches are written back into the graphology graph
 *    so Sigma animates the spread without ever blocking the main thread.
 *  - 'fa2' (kill-switch): the legacy graphology ForceAtlas2 worker, kept as a
 *    one-line `VITE_LAYOUT_ENGINE=fa2` revert while the ngraph engine proves
 *    itself in production.
 *
 * Collision is NOT handled by either engine — the Layer 2 d3-forceCollide
 * micro-pass (collideSettle.ts) runs after the engine stops.
 */
import FA2Layout from 'graphology-layout-forceatlas2/worker'
import type Graph from 'graphology'
import {
  LAYOUT_ENGINE,
  FA2_GRAVITY,
  FA2_SCALING_RATIO,
  FA2_BARNES_HUT_MIN_NODES,
  FA2_SLOW_DOWN,
  FA2_LINLOG_MODE,
  FA2_OUTBOUND_ATTRACTION,
  FA2_ADJUST_SIZES,
  FA2_STRONG_GRAVITY_MODE,
  FA2_LARGE_GRAPH_THRESHOLD,
  FA2_GRAVITY_LARGE,
  FA2_SCALING_RATIO_LARGE,
  NGRAPH_TIME_STEP,
  NGRAPH_SPRING_LENGTH,
  NGRAPH_SPRING_COEFFICIENT,
  NGRAPH_DRAG_COEFFICIENT,
  NGRAPH_GRAVITY,
  NGRAPH_THETA,
} from '@/lib/runtimeConfig'
import type { NgraphInitMessage } from './ngraphLayoutWorker'

export interface LayoutEngineHandle {
  start(): void
  kill(): void
}

export interface LayoutEngineOptions {
  /** Called after each progressive position batch so Sigma can repaint. */
  onTick?: () => void
}

export function createLayoutEngine(graph: Graph, options: LayoutEngineOptions = {}): LayoutEngineHandle {
  if (LAYOUT_ENGINE === 'fa2') return createFa2Engine(graph)
  return createNgraphEngine(graph, options)
}

/** Legacy ForceAtlas2 worker path (kill-switch). */
function createFa2Engine(graph: Graph): LayoutEngineHandle {
  let fa2: FA2Layout | null = null
  return {
    start() {
      const isLarge = graph.order > FA2_LARGE_GRAPH_THRESHOLD
      fa2 = new FA2Layout(graph, {
        settings: {
          gravity: isLarge ? FA2_GRAVITY_LARGE : FA2_GRAVITY,
          scalingRatio: isLarge ? FA2_SCALING_RATIO_LARGE : FA2_SCALING_RATIO,
          barnesHutOptimize: graph.order > FA2_BARNES_HUT_MIN_NODES,
          slowDown: FA2_SLOW_DOWN,
          linLogMode: FA2_LINLOG_MODE,
          outboundAttractionDistribution: FA2_OUTBOUND_ATTRACTION,
          adjustSizes: FA2_ADJUST_SIZES,
          strongGravityMode: FA2_STRONG_GRAVITY_MODE,
        },
      })
      fa2.start()
    },
    kill() {
      fa2?.kill()
      fa2 = null
    },
  }
}

/** ngraph.forcelayout Web Worker path (default). */
function createNgraphEngine(graph: Graph, options: LayoutEngineOptions): LayoutEngineHandle {
  let worker: Worker | null = null
  let ids: string[] = []

  return {
    start() {
      worker = new Worker(new URL('./ngraphLayoutWorker.ts', import.meta.url), { type: 'module' })
      ids = graph.nodes()
      const xs = new Array<number>(ids.length)
      const ys = new Array<number>(ids.length)
      for (let i = 0; i < ids.length; i += 1) {
        const id = ids[i]!
        xs[i] = Number(graph.getNodeAttribute(id, 'x')) || 0
        ys[i] = Number(graph.getNodeAttribute(id, 'y')) || 0
      }
      const edges = graph.edges().map((edge) => {
        const [from, to] = graph.extremities(edge)
        return { from, to }
      })

      worker.onmessage = (event: MessageEvent) => {
        const msg = event.data as { type: string; xs?: number[]; ys?: number[] }
        if (msg.type !== 'positions' || !msg.xs || !msg.ys) return
        for (let i = 0; i < ids.length; i += 1) {
          const id = ids[i]
          const x = msg.xs[i]
          const y = msg.ys[i]
          if (!id || !Number.isFinite(x) || !Number.isFinite(y)) continue
          graph.setNodeAttribute(id, 'x', x)
          graph.setNodeAttribute(id, 'y', y)
        }
        options.onTick?.()
      }

      const message: NgraphInitMessage = {
        type: 'init',
        ids,
        xs,
        ys,
        edges,
        settings: {
          timeStep: NGRAPH_TIME_STEP,
          springLength: NGRAPH_SPRING_LENGTH,
          springCoefficient: NGRAPH_SPRING_COEFFICIENT,
          dragCoefficient: NGRAPH_DRAG_COEFFICIENT,
          gravity: NGRAPH_GRAVITY,
          theta: NGRAPH_THETA,
        },
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
