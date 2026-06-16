/**
 * Ghost background layer for graph focus mode.
 *
 * This is the TRUE-LAYERING fix for the long-standing bug where dimmed/unrelated
 * background nodes painted over highlighted foreground edges. zIndex, smaller
 * dots, and darker colors all failed because Sigma draws the entire node WebGL
 * program ON TOP of the entire edge program inside one renderer — zIndex can only
 * order node-vs-node and edge-vs-edge, never push a node behind an edge.
 *
 * The robust solution: a SEPARATE 2D canvas created via Sigma's native layer API
 * (`createCanvasContext(id, { beforeLayer: 'edges' })`) which inserts the canvas
 * PHYSICALLY BELOW the WebGL edges layer in the DOM/render order. Unrelated nodes
 * and edges are HIDDEN in the interactive Sigma and redrawn here instead. Because
 * this canvas sits under the edges layer, a background node can NEVER cover a
 * foreground edge — regardless of pan/zoom/drag.
 *
 * Camera sync is automatic: the canvas is Sigma-managed, so it shares Sigma's
 * camera and is resized + DPR-scaled by Sigma's own `resize()` (it lives in
 * `canvasContexts`, whose 2D contexts Sigma pre-scales by `pixelRatio`). We draw
 * in CSS-pixel viewport coordinates (`graphToViewport`) and scale radii/widths
 * with `scaleSize`, then redraw on every `afterRender` frame.
 */

import type Sigma from 'sigma'
import type Graph from 'graphology'
import type { FocusPartition } from './focusMode'
import { ghostNodeSize, ghostNodeColor, ghostEdgeSize, ghostEdgeColor } from './focusMode'

const GHOST_LAYER_ID = 'ghost-graph'

// `createCanvasContext` forwards its options to `createCanvas → createLayer`,
// which honors `beforeLayer`, but Sigma's published type for the method omits it.
// This typed view restores it without resorting to `any`.
type CreateCanvasContext = (
  id: string,
  options: { style?: Partial<CSSStyleDeclaration>; beforeLayer?: string },
) => unknown

export interface GhostLayerHandle {
  /** Update which nodes/edges are background, then redraw. */
  setPartition: (partition: FocusPartition | null) => void
  /** Remove the canvas and detach the render listener. */
  destroy: () => void
}

/**
 * Attach a ghost background canvas to a Sigma instance. The canvas is inserted
 * below the WebGL edges layer and redraws the supplied background partition on
 * every Sigma frame. Returns a handle to update the partition or tear down.
 */
export function attachGhostLayer(sigma: Sigma, graph: Graph): GhostLayerHandle {
  ;(sigma.createCanvasContext as unknown as CreateCanvasContext)(GHOST_LAYER_ID, {
    beforeLayer: 'edges',
    style: { pointerEvents: 'none' },
  })
  // Force a resize so Sigma sizes the new canvas's backing store and applies the
  // devicePixelRatio transform to its 2D context (done only inside resize()).
  sigma.resize(true)

  const canvas = sigma.getCanvases()[GHOST_LAYER_ID]
  const context = canvas ? canvas.getContext('2d') : null

  let partition: FocusPartition | null = null

  function clear(): void {
    if (!context) return
    // Context is pre-scaled by pixelRatio, so clear in CSS-pixel space.
    context.clearRect(0, 0, sigma.getDimensions().width, sigma.getDimensions().height)
  }

  function draw(): void {
    if (!context) return
    clear()
    if (!partition || partition.backgroundNodes.size === 0) return

    const cameraRatio = sigma.getCamera().ratio

    // Edges first so background nodes sit above background edges (within this
    // layer only — the whole layer is still below the foreground edges).
    context.lineCap = 'round'
    partition.backgroundEdges.forEach((edge) => {
      if (!graph.hasEdge(edge)) return
      const source = graph.source(edge)
      const target = graph.target(edge)
      if (!graph.hasNode(source) || !graph.hasNode(target)) return

      const start = sigma.graphToViewport({
        x: graph.getNodeAttribute(source, 'x') as number,
        y: graph.getNodeAttribute(source, 'y') as number,
      })
      const end = sigma.graphToViewport({
        x: graph.getNodeAttribute(target, 'x') as number,
        y: graph.getNodeAttribute(target, 'y') as number,
      })

      context.strokeStyle = ghostEdgeColor(graph.getEdgeAttribute(edge, 'color'))
      context.lineWidth = sigma.scaleSize(ghostEdgeSize(graph.getEdgeAttribute(edge, 'size')), cameraRatio)
      context.beginPath()
      context.moveTo(start.x, start.y)
      context.lineTo(end.x, end.y)
      context.stroke()
    })

    partition.backgroundNodes.forEach((node) => {
      if (!graph.hasNode(node)) return
      const position = sigma.graphToViewport({
        x: graph.getNodeAttribute(node, 'x') as number,
        y: graph.getNodeAttribute(node, 'y') as number,
      })
      const radius = sigma.scaleSize(ghostNodeSize(graph.getNodeAttribute(node, 'size')), cameraRatio)
      context.fillStyle = ghostNodeColor(graph.getNodeAttribute(node, 'color'))
      context.beginPath()
      context.arc(position.x, position.y, radius, 0, Math.PI * 2)
      context.fill()
    })
  }

  // Redraw in lockstep with each Sigma frame so pan/zoom/drag stay aligned.
  sigma.on('afterRender', draw)

  return {
    setPartition(next: FocusPartition | null) {
      partition = next
      draw()
    },
    destroy() {
      sigma.removeListener('afterRender', draw)
      sigma.killLayer(GHOST_LAYER_ID)
    },
  }
}
