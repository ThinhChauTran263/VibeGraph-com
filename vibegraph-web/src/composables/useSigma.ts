/**
 * Sigma.js graph lifecycle, rendering, and interaction composable.
 * Handles: init, destroy, node/edge reducers, ForceAtlas2 layout, zoom.
 */

import { shallowRef, onUnmounted, type Ref } from 'vue'
import Sigma from 'sigma'
import type Graph from 'graphology'
import type { Settings } from 'sigma/settings'
import FA2Layout from 'graphology-layout-forceatlas2/worker'
import forceAtlas2 from 'graphology-layout-forceatlas2'
import { DEFAULT_LABEL_COLOR } from '@/lib/constants'
import {
  SIGMA_BASE_NODE_LABEL_SIZE,
  SIGMA_BASE_EDGE_LABEL_SIZE,
  SIGMA_MIN_LABEL_ZOOM_SCALE,
  SIGMA_MAX_LABEL_ZOOM_SCALE,
  SIGMA_MIN_EDGE_LABEL_ZOOM_SCALE,
  SIGMA_MAX_EDGE_LABEL_ZOOM_SCALE,
  SIGMA_LABEL_RENDERED_SIZE_THRESHOLD,
  FA2_GRAVITY,
  FA2_SCALING_RATIO,
  FA2_BARNES_HUT_MIN_NODES,
  FA2_SLOW_DOWN,
  FA2_ITERATIONS,
  LAYOUT_AUTO_STOP_MS,
  ZOOM_FIT_DURATION_MS,
} from '@/lib/runtimeConfig'
import { drawDefaultNodeLabel, drawHighlightNodeHover, drawEdgeTypeLabel } from '@/lib/sigmaRenderers'
import { attachGhostLayer, type GhostLayerHandle } from '@/lib/ghostLayer'
import type { FocusPartition } from '@/lib/focusMode'

export interface UseSigmaOptions {
  container: Ref<HTMLDivElement | null>
  onNodeClick?: (nodeId: string) => void
  onNodeDoubleClick?: (nodeId: string) => void
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
const BASE_NODE_LABEL_SIZE = SIGMA_BASE_NODE_LABEL_SIZE
const BASE_EDGE_LABEL_SIZE = SIGMA_BASE_EDGE_LABEL_SIZE
// Allow labels to shrink further when zoomed OUT (small nodes) so they don't look
// oversized, while still capping growth when zoomed IN.
const MIN_LABEL_ZOOM_SCALE = SIGMA_MIN_LABEL_ZOOM_SCALE
const MAX_LABEL_ZOOM_SCALE = SIGMA_MAX_LABEL_ZOOM_SCALE
// Edge labels can grow with zoom-in like node labels. Raise MAX to let
// relationship text (DEFINES, IMPORTS…) get bigger when zoomed in; lower it to
// cap sooner. MIN is the floor when zoomed out (smaller = shrinks more).
const MIN_EDGE_LABEL_ZOOM_SCALE = SIGMA_MIN_EDGE_LABEL_ZOOM_SCALE
const MAX_EDGE_LABEL_ZOOM_SCALE = SIGMA_MAX_EDGE_LABEL_ZOOM_SCALE

function clampLabelScale(ratio: number): number {
  if (!Number.isFinite(ratio) || ratio <= 0) return 1
  return Math.min(Math.max(1 / ratio, MIN_LABEL_ZOOM_SCALE), MAX_LABEL_ZOOM_SCALE)
}

function clampEdgeLabelScale(ratio: number): number {
  if (!Number.isFinite(ratio) || ratio <= 0) return 1
  return Math.min(Math.max(1 / ratio, MIN_EDGE_LABEL_ZOOM_SCALE), MAX_EDGE_LABEL_ZOOM_SCALE)
}

function applyZoomResponsiveLabelSize(sigma: Sigma, ratio: number): void {
  sigma.setSetting('labelSize', Math.round(BASE_NODE_LABEL_SIZE * clampLabelScale(ratio) * 100) / 100)
  sigma.setSetting(
    'edgeLabelSize',
    Math.round(BASE_EDGE_LABEL_SIZE * clampEdgeLabelScale(ratio) * 100) / 100,
  )
}

export function useSigma(options: UseSigmaOptions) {
  const { container, onNodeClick, onNodeDoubleClick, onStageClick, onNodeHover, onNodeLeave, onCameraRatioChange } =
    options

  const sigmaInstance = shallowRef<Sigma | null>(null)
  const graphInstance = shallowRef<Graph | null>(null)
  const layout = shallowRef<FA2Layout | null>(null)
  const layoutStopTimer = shallowRef<ReturnType<typeof setTimeout> | null>(null)
  const ghostLayer = shallowRef<GhostLayerHandle | null>(null)
  // Cleanup for the manual right/middle-button canvas panning listeners.
  const panCleanup = shallowRef<null | (() => void)>(null)
  // Observes the canvas container so Sigma re-measures whenever its box changes
  // (sidebar collapse, detail panel open/close, responsive grid reflow, or a
  // first-paint mis-measure). Without this the WebGL surface keeps a stale size
  // and the graph renders offset to one edge / squished. See attachResizeObserver.
  const resizeObserver = shallowRef<ResizeObserver | null>(null)
  // rAF handle that coalesces a burst of resize callbacks into a single refresh.
  const resizeRaf = shallowRef<number | null>(null)

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

    // Run ForceAtlas2 SYNCHRONOUSLY once before the first paint, so nodes appear
    // already settled (no live "drift" animation on load). Stronger repulsion +
    // weaker gravity spread the graph out.
    settleLayout(graph)

    const sigma = new Sigma(graph, container.value, {
      allowInvalidContainer: true,
      renderEdgeLabels: false,
      defaultEdgeType: 'line',
      zIndex: true,
      // Performance on large graphs: skip drawing edges and labels during camera
      // pan/zoom so interaction stays at 60fps; they snap back when movement stops.
      hideEdgesOnMove: true,
      hideLabelsOnMove: true,
      labelRenderedSizeThreshold: SIGMA_LABEL_RENDERED_SIZE_THRESHOLD,
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
      // Edge labels: hide entirely when the full text doesn't fit the edge
      // (no "DEFI…" truncation). See lib/sigmaRenderers.ts.
      defaultDrawEdgeLabel: drawEdgeTypeLabel,
    })

    sigmaInstance.value = sigma

    // Ghost background canvas: a Sigma-managed 2D canvas inserted physically below
    // the WebGL edges layer. Unrelated nodes/edges are hidden in this Sigma during
    // focus and redrawn here, so a background node can never cover a foreground
    // edge. Shares Sigma's camera, so pan/zoom/drag stay aligned automatically.
    ghostLayer.value = attachGhostLayer(sigma, graph)

    // Register node click handler
    if (onNodeClick) {      sigma.on('clickNode', ({ node }) => {
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

    // Double-click a node to lazily expand its neighborhood. We swallow Sigma's default
    // double-click zoom so the gesture means "expand", not "zoom in".
    if (onNodeDoubleClick) {
      sigma.on('doubleClickNode', ({ node, event }) => {
        event.preventSigmaDefault()
        onNodeDoubleClick(node)
      })
    }

    registerDragHandlers(sigma, graph)

    // Right/middle-button drag panning (Sigma natively pans only with the left
    // button). Suppress the context menu so a right-drag pans instead of opening
    // the browser menu.
    if (container.value) {
      panCleanup.value = registerCanvasPanning(sigma, container.value)
    }

    // Re-measure on any container resize so the graph stays correctly framed.
    attachResizeObserver(sigma)

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

    // Layout is precomputed synchronously (settleLayout) before paint, so there is
    // no live worker animation. Node positions are static until the user drags.
  }

  /**
   * Compute the ForceAtlas2 layout synchronously (no worker, no animation) and
   * write final x/y onto the graph before it is first rendered.
   */
  function settleLayout(graph: Graph): void {
    if (graph.order === 0) return
    try {
      forceAtlas2.assign(graph, {
        iterations: FA2_ITERATIONS,
        settings: {
          gravity: FA2_GRAVITY,
          scalingRatio: FA2_SCALING_RATIO,
          barnesHutOptimize: graph.order > FA2_BARNES_HUT_MIN_NODES,
          slowDown: FA2_SLOW_DOWN,
        },
      })
    } catch {
      // Leave the random seed positions if the layout fails.
    }
  }

  /**
   * Re-measure and repaint Sigma whenever its container's box changes.
   *
   * Sigma only listens for *window* resizes, not container-only changes (sidebar
   * collapse, right-hand detail panel opening, responsive grid reflow, or the
   * canvas being laid out a frame after init). When the box changes underneath a
   * stale WebGL surface the graph renders offset to one edge or squished. A
   * ResizeObserver catches every such change; callbacks are coalesced through a
   * single rAF so a burst of reflows triggers one refresh. refresh() is a no-op
   * on container size, so observing the container can't loop.
   */
  function attachResizeObserver(sigma: Sigma): void {
    const el = container.value
    if (!el || typeof ResizeObserver === 'undefined') return

    let lastW = el.clientWidth
    let lastH = el.clientHeight

    const observer = new ResizeObserver(() => {
      const w = el.clientWidth
      const h = el.clientHeight
      // Ignore zero-size (detached) and no-op callbacks.
      if (w === 0 || h === 0 || (w === lastW && h === lastH)) return
      lastW = w
      lastH = h
      if (resizeRaf.value !== null) cancelAnimationFrame(resizeRaf.value)
      resizeRaf.value = requestAnimationFrame(() => {
        resizeRaf.value = null
        if (sigmaInstance.value === sigma) sigma.refresh()
      })
    })

    observer.observe(el)
    resizeObserver.value = observer
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
      // Disable Sigma's native LEFT-drag stage panning entirely — panning is
      // right/middle-button only (see registerCanvasPanning), matching the
      // on-canvas Controls hint. Sigma checks `sigmaDefaultPrevented` before its
      // pan block, so this blocks stage panning while leaving click-selection and
      // hover intact. Node dragging is still handled below.
      event.preventSigmaDefault()

      const node = draggedNode.value
      if (!node) return

      // The pointer moved while holding the node: this is a real drag, so the
      // click that ends it must not select the node.
      dragMoved.value = true

      const pos = sigma.viewportToGraph(event)
      graph.setNodeAttribute(node, 'x', pos.x)
      graph.setNodeAttribute(node, 'y', pos.y)

      // Prevent the browser's default while a node is being dragged.
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
   * Manual canvas panning with the RIGHT or MIDDLE mouse button.
   *
   * Sigma's built-in pan only responds to the left button; the on-canvas Controls
   * hint promises "Right / Mid → Pan", so we implement it here. We reuse Sigma's
   * own conversion ({@code viewportToFramedGraph}) to shift the camera by the same
   * framed-graph delta its left-drag panning uses, and suppress the context menu
   * so a right-drag pans instead of opening the browser menu.
   *
   * Returns a disposer that removes every listener.
   */
  function registerCanvasPanning(sigma: Sigma, el: HTMLDivElement): () => void {
    let panning = false
    let lastX = 0
    let lastY = 0

    const localPos = (event: MouseEvent) => {
      const rect = el.getBoundingClientRect()
      return { x: event.clientX - rect.left, y: event.clientY - rect.top }
    }

    const onContextMenu = (event: MouseEvent) => event.preventDefault()

    const onDown = (event: MouseEvent) => {
      if (event.button !== 1 && event.button !== 2) return
      panning = true
      const point = localPos(event)
      lastX = point.x
      lastY = point.y
      el.style.cursor = 'grabbing'
      event.preventDefault()
    }

    const onMove = (event: MouseEvent) => {
      if (!panning) return
      const point = localPos(event)
      const last = sigma.viewportToFramedGraph({ x: lastX, y: lastY })
      const curr = sigma.viewportToFramedGraph({ x: point.x, y: point.y })
      const camera = sigma.getCamera()
      const state = camera.getState()
      camera.setState({ x: state.x + (last.x - curr.x), y: state.y + (last.y - curr.y) })
      lastX = point.x
      lastY = point.y
    }

    const onUp = () => {
      if (!panning) return
      panning = false
      el.style.cursor = ''
    }

    el.addEventListener('contextmenu', onContextMenu)
    el.addEventListener('mousedown', onDown)
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)

    return () => {
      el.removeEventListener('contextmenu', onContextMenu)
      el.removeEventListener('mousedown', onDown)
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
  }

  /**
   * Start ForceAtlas2 layout. Stops automatically after a timeout.
   */
  function startLayout(graph: Graph) {
    stopLayout()

    const fa2 = new FA2Layout(graph, {
      settings: {
        gravity: FA2_GRAVITY,
        scalingRatio: FA2_SCALING_RATIO,
        barnesHutOptimize: graph.order > FA2_BARNES_HUT_MIN_NODES,
        slowDown: FA2_SLOW_DOWN,
      },
    })

    fa2.start()
    layout.value = fa2

    layoutStopTimer.value = setTimeout(() => {
      if (layout.value === fa2) {
        stopLayout()
      }
    }, LAYOUT_AUTO_STOP_MS)
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
    if (resizeObserver.value) {
      resizeObserver.value.disconnect()
      resizeObserver.value = null
    }
    if (resizeRaf.value !== null) {
      cancelAnimationFrame(resizeRaf.value)
      resizeRaf.value = null
    }
    if (panCleanup.value) {
      panCleanup.value()
      panCleanup.value = null
    }
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
    camera.animatedReset({ duration: ZOOM_FIT_DURATION_MS })
  }

  /**
   * Animate the camera to center a node in the viewport (used when a Data Flow
   * step is selected). No-op when the node is absent from the live graph.
   */
  function focusNode(nodeId: string): void {
    const sigma = sigmaInstance.value
    if (!sigma || !graphInstance.value?.hasNode(nodeId)) return
    const display = sigma.getNodeDisplayData(nodeId)
    if (!display) return
    sigma.getCamera().animate(
      { x: display.x, y: display.y, ratio: Math.min(sigma.getCamera().getState().ratio, 0.6) },
      { duration: ZOOM_FIT_DURATION_MS },
    )
  }

  /**
   * Re-measure the container and repaint. Needed after the canvas DOM is
   * detached/re-attached (e.g. a {@code <KeepAlive>} tab switch), otherwise the
   * WebGL surface can stay blank or keep a stale size.
   */
  function refresh(): void {
    const sigma = sigmaInstance.value
    if (!sigma) return
    sigma.refresh()
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
    refresh,
    zoomToFit,
    focusNode,
    startLayout: () => {
      if (graphInstance.value) startLayout(graphInstance.value)
    },
    stopLayout,
    setReducers,
    setEdgeLabelsVisible,
    setGhostPartition,
  }
}
