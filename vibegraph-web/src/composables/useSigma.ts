/**
 * Sigma.js graph lifecycle, rendering, and interaction composable.
 * Handles: init, destroy, node/edge reducers, ForceAtlas2 layout, zoom.
 */

import { shallowRef, onUnmounted, type Ref } from 'vue'
import Sigma from 'sigma'
import type Graph from 'graphology'
import type { Settings } from 'sigma/settings'
import FA2Layout from 'graphology-layout-forceatlas2/worker'
import { DEFAULT_LABEL_COLOR } from '@/lib/constants'
import { drawDefaultNodeLabel, drawHighlightNodeHover } from '@/lib/sigmaRenderers'
import { attachGhostLayer, type GhostLayerHandle } from '@/lib/ghostLayer'
import type { FocusPartition } from '@/lib/focusMode'

export interface UseSigmaOptions {
  container: Ref<HTMLDivElement | null>
  onNodeClick?: (nodeId: string) => void
  onStageClick?: () => void
  onNodeHover?: (nodeId: string) => void
  onNodeLeave?: () => void
  onCameraRatioChange?: (ratio: number) => void
}

// Base label sizes at the default (ratio = 1) zoom. Labels are scaled inversely
// with the camera ratio so they GROW as the user zooms in and shrink as they zoom
// out, matching the node sizes (which Sigma already scales with zoom). Without
// this, Sigma keeps labels at a fixed pixel size, so a zoomed-in node looks huge
// while its label stays tiny.
const BASE_NODE_LABEL_SIZE = 12
const BASE_EDGE_LABEL_SIZE = 7
// Allow labels to shrink further when zoomed OUT (small nodes) so they don't look
// oversized, while still capping growth when zoomed IN.
const MIN_LABEL_ZOOM_SCALE = 0.5
const MAX_LABEL_ZOOM_SCALE = 2.2

function clampLabelScale(ratio: number): number {
  if (!Number.isFinite(ratio) || ratio <= 0) return 1
  return Math.min(Math.max(1 / ratio, MIN_LABEL_ZOOM_SCALE), MAX_LABEL_ZOOM_SCALE)
}

function applyZoomResponsiveLabelSize(sigma: Sigma, ratio: number): void {
  const scale = clampLabelScale(ratio)
  sigma.setSetting('labelSize', Math.round(BASE_NODE_LABEL_SIZE * scale * 100) / 100)
  sigma.setSetting('edgeLabelSize', Math.round(BASE_EDGE_LABEL_SIZE * scale * 100) / 100)
}

export function useSigma(options: UseSigmaOptions) {
  const { container, onNodeClick, onStageClick, onNodeHover, onNodeLeave, onCameraRatioChange } =
    options

  const sigmaInstance = shallowRef<Sigma | null>(null)
  const graphInstance = shallowRef<Graph | null>(null)
  const layout = shallowRef<FA2Layout | null>(null)
  const layoutStopTimer = shallowRef<ReturnType<typeof setTimeout> | null>(null)
  const ghostLayer = shallowRef<GhostLayerHandle | null>(null)

  // Node currently being dragged (null when idle). While set, the camera pan is
  // disabled and the layout worker is stopped so the node stays where dropped.
  const draggedNode = shallowRef<string | null>(null)

  // True once the pointer actually MOVED while holding a node. Sigma still fires
  // `clickNode` on the mouse-up that ends a drag, which would wrongly select the
  // node the user was just repositioning. We use this flag to swallow exactly
  // that one click. A plain click (no movement) never sets it, so selecting by
  // clicking still works.
  const dragMoved = shallowRef(false)

  /**
   * Initialize Sigma with a Graphology graph.
   * Starts ForceAtlas2 layout in a web worker.
   */
  function init(graph: Graph) {
    dispose()

    if (!container.value) return

    graphInstance.value = graph

    const sigma = new Sigma(graph, container.value, {
      allowInvalidContainer: true,
      renderEdgeLabels: false,
      defaultEdgeType: 'line',
      zIndex: true,
      labelRenderedSizeThreshold: 8,
      labelColor: { color: DEFAULT_LABEL_COLOR },
      labelFont: 'Inter, system-ui, sans-serif',
      labelSize: BASE_NODE_LABEL_SIZE,
      labelWeight: '600',
      // Edge labels render in their own edge-type color (per-edge `labelColor`
      // attribute set by graphAdapter / focus reducer), matching the Edge Types
      // legend. Sigma's edge label renderer draws text only (no white box). The
      // `color` fallback applies when an edge has no labelColor attribute.
      edgeLabelColor: { attribute: 'labelColor', color: '#cbd5e1' },
      edgeLabelSize: BASE_EDGE_LABEL_SIZE,
      // Override Sigma's default hover renderer (which paints a solid white
      // label box) and label renderer with text-only variants. See
      // lib/sigmaRenderers.ts.
      defaultDrawNodeLabel: drawDefaultNodeLabel,
      defaultDrawNodeHover: drawHighlightNodeHover,
    })

    sigmaInstance.value = sigma

    // Ghost background canvas: a Sigma-managed 2D canvas inserted physically below
    // the WebGL edges layer. Unrelated nodes/edges are hidden in this Sigma during
    // focus and redrawn here, so a background node can never cover a foreground
    // edge. Shares Sigma's camera, so pan/zoom/drag stay aligned automatically.
    ghostLayer.value = attachGhostLayer(sigma, graph)

    // Register node click handler
    if (onNodeClick) {
      sigma.on('clickNode', ({ node }) => {
        // Swallow the click that ends a drag so repositioning a node does not
        // also select / highlight it. See `dragMoved`.
        if (dragMoved.value) {
          dragMoved.value = false
          return
        }
        onNodeClick(node)
      })
    }

    // Background (stage) click deselects
    if (onStageClick) {
      sigma.on('clickStage', () => {
        onStageClick()
      })
    }

    registerDragHandlers(sigma, graph)

    const camera = sigma.getCamera()
    let lastRatio = camera.getState().ratio
    applyZoomResponsiveLabelSize(sigma, lastRatio)
    onCameraRatioChange?.(lastRatio)
    camera.on('updated', () => {
      const ratio = camera.getState().ratio
      if (ratio === lastRatio) return
      lastRatio = ratio
      applyZoomResponsiveLabelSize(sigma, ratio)
      onCameraRatioChange?.(ratio)
    })

    // Start ForceAtlas2 layout in a web worker
    startLayout(graph)
  }

  /**
   * Wire up manual node dragging. Sigma natively distinguishes a click from a
   * drag (small pointer movement = click, larger = drag), so a real drag does
   * NOT fire `clickNode` and the selected state is preserved. While dragging we
   * stop the layout worker and disable camera panning so the node lands and
   * stays exactly where it is dropped instead of snapping back.
   */
  function registerDragHandlers(sigma: Sigma, graph: Graph) {
    sigma.on('downNode', ({ node }) => {
      draggedNode.value = node
      dragMoved.value = false
      stopLayout()
      sigma.setSetting('enableCameraPanning', false)
      if (container.value) container.value.style.cursor = 'grabbing'
    })

    // Hover affordance: show a grab cursor over a draggable node when idle, and
    // notify the host so it can drive a temporary hover focus on the graph. We do
    // NOT emit hover focus mid-drag (the node is being moved, not inspected).
    sigma.on('enterNode', ({ node }) => {
      if (!draggedNode.value && container.value) container.value.style.cursor = 'grab'
      if (!draggedNode.value) onNodeHover?.(node)
    })

    sigma.on('leaveNode', () => {
      if (!draggedNode.value && container.value) container.value.style.cursor = ''
      if (!draggedNode.value) onNodeLeave?.()
    })

    const mouseCaptor = sigma.getMouseCaptor()

    mouseCaptor.on('mousemovebody', (event) => {
      const node = draggedNode.value
      if (!node) return

      // The pointer moved while holding the node: this is a real drag, so the
      // click that ends it must not select the node.
      dragMoved.value = true

      const pos = sigma.viewportToGraph(event)
      graph.setNodeAttribute(node, 'x', pos.x)
      graph.setNodeAttribute(node, 'y', pos.y)

      // Prevent Sigma's default camera move while a node is being dragged.
      event.preventSigmaDefault()
      event.original.preventDefault()
      event.original.stopPropagation()
    })

    const releaseDrag = () => {
      if (!draggedNode.value) return
      draggedNode.value = null
      sigma.setSetting('enableCameraPanning', true)
      if (container.value) container.value.style.cursor = ''
    }

    mouseCaptor.on('mouseup', releaseDrag)
  }

  /**
   * Start ForceAtlas2 layout. Stops automatically after a timeout.
   */
  function startLayout(graph: Graph) {
    stopLayout()

    const fa2 = new FA2Layout(graph, {
      settings: {
        gravity: 1,
        scalingRatio: 10,
        barnesHutOptimize: graph.order > 500,
        slowDown: 5,
      },
    })

    fa2.start()
    layout.value = fa2

    layoutStopTimer.value = setTimeout(() => {
      if (layout.value === fa2) {
        stopLayout()
      }
    }, 5000)
  }

  /**
   * Stop the running layout.
   */
  function stopLayout() {
    if (layoutStopTimer.value) {
      clearTimeout(layoutStopTimer.value)
      layoutStopTimer.value = null
    }

    if (layout.value) {
      layout.value.kill()
      layout.value = null
    }
  }

  /**
   * Dispose Sigma instance and layout.
   */
  function dispose() {
    stopLayout()
    if (ghostLayer.value) {
      ghostLayer.value.destroy()
      ghostLayer.value = null
    }
    if (sigmaInstance.value) {
      sigmaInstance.value.kill()
      sigmaInstance.value = null
    }
    graphInstance.value = null
  }

  /**
   * Feed the ghost background layer the focus partition (unrelated nodes/edges to
   * draw below the foreground). Pass null to clear the ghost layer in normal mode.
   */
  function setGhostPartition(partition: FocusPartition | null): void {
    ghostLayer.value?.setPartition(partition)
  }

  function setReducers(reducers: Pick<Settings, 'nodeReducer' | 'edgeReducer'>): void {
    const sigma = sigmaInstance.value
    if (!sigma) return
    sigma.setSetting('nodeReducer', reducers.nodeReducer)
    sigma.setSetting('edgeReducer', reducers.edgeReducer)
    sigma.refresh()
  }

  /**
   * Toggle edge label rendering. Edge labels add clutter at the default view,
   * so we only show them while a node is focused.
   */
  function setEdgeLabelsVisible(visible: boolean): void {
    const sigma = sigmaInstance.value
    if (!sigma) return
    sigma.setSetting('renderEdgeLabels', visible)
    sigma.refresh()
  }

  /**
   * Zoom to fit the entire graph in view.
   */
  function zoomToFit() {
    const sigma = sigmaInstance.value
    if (!sigma) return
    const camera = sigma.getCamera()
    camera.animatedReset({ duration: 300 })
  }

  // Cleanup on component unmount
  onUnmounted(() => {
    dispose()
  })

  return {
    sigmaInstance,
    graphInstance,
    draggedNode,
    init,
    dispose,
    zoomToFit,
    startLayout: () => {
      if (graphInstance.value) startLayout(graphInstance.value)
    },
    stopLayout,
    setReducers,
    setEdgeLabelsVisible,
    setGhostPartition,
  }
}
