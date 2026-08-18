/**
 * Sigma.js graph lifecycle, rendering, and interaction composable.
 * Handles: init, destroy, node/edge reducers, ForceAtlas2 layout, zoom.
 */

import { shallowRef, onUnmounted, type Ref } from 'vue'
import Sigma from 'sigma'
import type Graph from 'graphology'
import type { Settings } from 'sigma/settings'
import FA2Layout from 'graphology-layout-forceatlas2/worker'
import NoverlapLayout from 'graphology-layout-noverlap/worker'
import { DEFAULT_LABEL_COLOR } from '@/lib/constants'
import { applyDensitySizeScale } from '@/lib/graphAdapter'
import {
  SIGMA_BASE_NODE_LABEL_SIZE,
  SIGMA_BASE_EDGE_LABEL_SIZE,
  SIGMA_LABEL_RENDERED_SIZE_THRESHOLD,
  SIGMA_NODE_GROW_ZOOM,
  SIGMA_NODE_ZOOM_SIZE_POWER,
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
  LAYOUT_NORMALIZE_SPAN,
  LAYOUT_BRANCH_ENABLED,
  LAYOUT_BRANCH_MIN_NODES,
  LAYOUT_BRANCH_STRENGTH,
  LAYOUT_BRANCH_LEVEL_GAP,
  LAYOUT_BRANCH_JITTER,
  LAYOUT_BRANCH_COMPONENT_GAP,
  NOVERLAP_ENABLED,
  NOVERLAP_MARGIN,
  NOVERLAP_RATIO,
  NOVERLAP_AUTO_STOP_MS,
  LAYOUT_SCREEN_OVERLAP_ENABLED,
  LAYOUT_SCREEN_OVERLAP_GAP_PX,
  LAYOUT_SCREEN_OVERLAP_ITERATIONS,
  LAYOUT_SCREEN_OVERLAP_STRENGTH,
  LAYOUT_AUTO_STOP_MS,
  ZOOM_FIT_DURATION_MS,
  SIGMA_MAX_EDGE_LABELS_PER_FRAME,
  SIGMA_MIN_EDGE_THICKNESS,
} from '@/lib/runtimeConfig'
import {
  drawDefaultNodeLabel,
  drawHighlightNodeHover,
  drawEdgeTypeLabel,
  setLabelZoom,
  resetEdgeLabelBudget,
  setShowEdgeKind,
} from '@/lib/sigmaRenderers'
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
  onLayoutSettled?: () => void
}

// Base label sizes (on-screen px) at the fit view. The actual per-zoom scaling is
// applied live inside the label renderers (see setLabelZoom / sigmaRenderers), NOT
// via sigma.setSetting — so zooming never triggers a full refresh (no "reload"
// flash) and labels resize smoothly as the camera renders each frame.
const BASE_NODE_LABEL_SIZE = SIGMA_BASE_NODE_LABEL_SIZE
const BASE_EDGE_LABEL_SIZE = SIGMA_BASE_EDGE_LABEL_SIZE

const LABEL_RENDERED_SIZE_THRESHOLD = SIGMA_LABEL_RENDERED_SIZE_THRESHOLD
const NODE_GROW_ZOOM = SIGMA_NODE_GROW_ZOOM
const NODE_ZOOM_SIZE_POWER = SIGMA_NODE_ZOOM_SIZE_POWER

// Sigma divides item size by this value. Hold nodes steady through the normal
// zoom range, then grow them smoothly after the configured deep-zoom threshold.
const zoomToSizeRatio = (ratio: number): number => {
  const safeRatio = Number.isFinite(ratio) && ratio > 0 ? ratio : 1
  const zoom = 1 / safeRatio

  if (zoom < 1) return Math.max(0.001, Math.pow(safeRatio, NODE_ZOOM_SIZE_POWER))
  if (zoom <= NODE_GROW_ZOOM) return 1

  return Math.max(0.001, Math.pow(NODE_GROW_ZOOM / zoom, NODE_ZOOM_SIZE_POWER))
}

export function useSigma(options: UseSigmaOptions) {
  const {
    container,
    onNodeClick,
    onNodeDoubleClick,
    onStageClick,
    onNodeHover,
    onNodeLeave,
    onCameraRatioChange,
    onLayoutSettled,
  } = options

  const sigmaInstance = shallowRef<Sigma | null>(null)
  const graphInstance = shallowRef<Graph | null>(null)
  const layout = shallowRef<FA2Layout | null>(null)
  const layoutStopTimer = shallowRef<ReturnType<typeof setTimeout> | null>(null)
  const overlapLayout = shallowRef<NoverlapLayout | null>(null)
  const overlapStopTimer = shallowRef<ReturnType<typeof setTimeout> | null>(null)
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

  // True once the pointer actually MOVED while holding a node. Sigma still fires  // `clickNode` on the mouse-up that ends a drag, which would wrongly select the
  // node the user was just repositioning. We use this flag to swallow exactly
  // that one click. A plain click (no movement) never sets it, so selecting by
  // clicking still works.
  const dragMoved = shallowRef(false)

  // Last edge-label visibility batched with reducer updates. Reset on each init
  // because a new Sigma instance starts with renderEdgeLabels disabled.
  let lastEdgeLabelsVisible: boolean | null = null

  /**
   * Initialize Sigma with a Graphology graph.
   * Starts ForceAtlas2 layout in a web worker.
   */
  function init(graph: Graph) {
    // Preserve the camera across a REBUILD (filter toggle / lazy expand) so
    // re-showing a node type doesn't snap the view back to the default framing.
    // On the very first init there is no prior Sigma → null → default framing
    // (load() then calls zoomToFit once the canvas has its final box).
    const prevCameraState = sigmaInstance.value?.getCamera().getState() ?? null

    dispose()

    if (!container.value) return

    graphInstance.value = graph

    // Seed node positions from the cache so rebuilds keep the last stable placement
    // for known nodes before the worker refines them.
    applyCachedLayout(graph)

    // Density-adaptive fit-view sizing: on large graphs the configured radii
    // exceed the viewport's circle-area budget and any de-overlap pass would
    // close-pack the layout into a round disc. Shrink sizes (NOT positions) to
    // a feasible budget before Sigma renders; no-op on small graphs.
    applyDensitySizeScale(graph, container.value.clientWidth, container.value.clientHeight)

    const sigma = new Sigma(graph, container.value, {
      allowInvalidContainer: true,
      renderEdgeLabels: false,
      defaultEdgeType: 'line',
      zIndex: true,
      // Edges render at a constant thin width regardless of zoom: SIGMA_EDGE_SIZE is
      // tiny so this floor dominates, pinning line thickness instead of letting it
      // balloon with the default size/√ratio zoom scaling.
      minEdgeThickness: SIGMA_MIN_EDGE_THICKNESS,
      // Keep the graph visually continuous while zooming. Performance comes from
      // batched settings and renderer culling, not hiding content during motion.
      hideEdgesOnMove: false,
      hideLabelsOnMove: false,
      labelRenderedSizeThreshold: LABEL_RENDERED_SIZE_THRESHOLD,
      itemSizesReference: 'screen',
      zoomToSizeRatioFunction: zoomToSizeRatio,
      // Bound zoom-out so the graph cannot shrink into a useless dot, and cap
      // deep zoom so node growth remains predictable at the configured power.
      maxCameraRatio: 4,
      minCameraRatio: 0.01,
      defaultEdgeColor: '#475569',
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
    // DEV-only QA hook: browser-side verification scripts read the live Sigma
    // instance + graph (touching-pair counts, zoom metrics). import.meta.env.DEV
    // is a build-time constant → tree-shaken out of production builds.
    if (import.meta.env.DEV) {
      ;(window as unknown as Record<string, unknown>).__vibegraph_qa = { sigma, graph }
    }
    // Reset the visibility guard for the first batched reducer update after rebuild.
    lastEdgeLabelsVisible = false

    // Refill the per-frame edge-label draw budget at the start of every frame so a
    // zoom level with many visible edges can't stack hundreds of text draws.
    sigma.on('beforeRender', () => resetEdgeLabelBudget(SIGMA_MAX_EDGE_LABELS_PER_FRAME))

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
    // Restore the pre-rebuild camera (see prevCameraState) so filter toggles and
    // expansions keep the user's current pan/zoom instead of resetting the view.
    if (prevCameraState) camera.setState(prevCameraState)
    let lastRatio = camera.getState().ratio
    // zoom level relative to the fit view (ratio 1 = fit): 1/ratio grows as you zoom in.
    // Feed it to the renderers (no setSetting → no refresh flash); Sigma repaints on
    // camera move and the label renderers read this value to size text live.
    setLabelZoom(1 / lastRatio)
    onCameraRatioChange?.(lastRatio)
    camera.on('updated', () => {
      const ratio = camera.getState().ratio
      if (ratio === lastRatio) return
      lastRatio = ratio
      setLabelZoom(1 / ratio)
      onCameraRatioChange?.(ratio)
    })

    startLayout(graph)
  }

  /**
   * Persistent node position cache. Survives graph rebuilds (filter toggles, lazy
   * expansions) so re-showing nodes reuses their settled coordinates instead of
   * recomputing the whole ForceAtlas2 layout — and re-seeding every node to a new
   * random spot — on every rebuild. Cleared on project switch via resetLayout().
   */
  const positionCache = new Map<string, { x: number; y: number }>()

  /** Drop all cached positions so the next init recomputes a fresh layout. */
  function resetLayout(): void {
    positionCache.clear()
  }

  /** Seed node positions from the cache and keep the latest values for next rebuild. */
  function applyCachedLayout(graph: Graph): void {
    if (graph.order === 0) return

    graph.forEachNode((id) => {
      const cached = positionCache.get(id)
      if (cached) {
        graph.setNodeAttribute(id, 'x', cached.x)
        graph.setNodeAttribute(id, 'y', cached.y)
      }
    })

    graph.forEachNode((id) => {
      positionCache.set(id, {
        x: graph.getNodeAttribute(id, 'x') as number,
        y: graph.getNodeAttribute(id, 'y') as number,
      })
    })
  }

  function cacheLayoutPositions(graph: Graph): void {
    graph.forEachNode((id) => {
      positionCache.set(id, {
        x: graph.getNodeAttribute(id, 'x') as number,
        y: graph.getNodeAttribute(id, 'y') as number,
      })
    })
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
    if (graph.order === 0) return

    const isLarge = graph.order > FA2_LARGE_GRAPH_THRESHOLD
    const fa2 = new FA2Layout(graph, {
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
    layout.value = fa2

    layoutStopTimer.value = setTimeout(() => {
      if (layout.value === fa2) {
        stopLayout(true)
      }
    }, LAYOUT_AUTO_STOP_MS)
  }

  /**
   * Stop the running layout.
   */
  function stopLayout(runPostLayout: boolean = false) {
    if (layoutStopTimer.value) {
      clearTimeout(layoutStopTimer.value)
      layoutStopTimer.value = null
    }

    stopOverlapLayout()

    if (layout.value) {
      layout.value.kill()
      layout.value = null
    }

    if (graphInstance.value) {
      cacheLayoutPositions(graphInstance.value)
    }

    if (runPostLayout && graphInstance.value) {
      runPostLayoutPass(graphInstance.value)
    }
  }

  function runPostLayoutPass(graph: Graph): void {
    normalizeLayout(graph)
    spreadLayoutClusters(graph)
    centerLayout(graph)
    cacheLayoutPositions(graph)
    sigmaInstance.value?.refresh({ skipIndexation: true })

    if (!NOVERLAP_ENABLED || graph.order < 2) {
      onLayoutSettled?.()
      return
    }

    const overlap = new NoverlapLayout(graph, {
      settings: {
        margin: NOVERLAP_MARGIN,
        ratio: NOVERLAP_RATIO,
      },
      onConverged: () => stopOverlapLayout(true),
    })

    overlapLayout.value = overlap
    overlap.start()
    overlapStopTimer.value = setTimeout(() => stopOverlapLayout(true), NOVERLAP_AUTO_STOP_MS)
  }

  function normalizeLayout(graph: Graph): void {
    if (LAYOUT_NORMALIZE_SPAN <= 0 || graph.order < 2) return

    let minX = Number.POSITIVE_INFINITY
    let maxX = Number.NEGATIVE_INFINITY
    let minY = Number.POSITIVE_INFINITY
    let maxY = Number.NEGATIVE_INFINITY

    graph.forEachNode((_id, attributes) => {
      const x = Number(attributes.x)
      const y = Number(attributes.y)
      if (!Number.isFinite(x) || !Number.isFinite(y)) return
      minX = Math.min(minX, x)
      maxX = Math.max(maxX, x)
      minY = Math.min(minY, y)
      maxY = Math.max(maxY, y)
    })

    const width = maxX - minX
    const height = maxY - minY
    const span = Math.max(width, height)
    if (!Number.isFinite(span) || span <= 0) return

    const scale = LAYOUT_NORMALIZE_SPAN / span
    const centerX = (minX + maxX) / 2
    const centerY = (minY + maxY) / 2

    graph.updateEachNodeAttributes((_id, attributes) => ({
      ...attributes,
      x: (Number(attributes.x) - centerX) * scale,
      y: (Number(attributes.y) - centerY) * scale,
    }))
  }

  function centerLayout(graph: Graph): void {
    if (graph.order < 2) return

    let minX = Number.POSITIVE_INFINITY
    let maxX = Number.NEGATIVE_INFINITY
    let minY = Number.POSITIVE_INFINITY
    let maxY = Number.NEGATIVE_INFINITY

    graph.forEachNode((_id, attributes) => {
      const x = Number(attributes.x)
      const y = Number(attributes.y)
      if (!Number.isFinite(x) || !Number.isFinite(y)) return
      minX = Math.min(minX, x)
      maxX = Math.max(maxX, x)
      minY = Math.min(minY, y)
      maxY = Math.max(maxY, y)
    })

    const centerX = (minX + maxX) / 2
    const centerY = (minY + maxY) / 2
    if (!Number.isFinite(centerX) || !Number.isFinite(centerY)) return

    graph.updateEachNodeAttributes((_id, attributes) => ({
      ...attributes,
      x: Number(attributes.x) - centerX,
      y: Number(attributes.y) - centerY,
    }))
  }

  interface LayoutCluster {
    nodeIds: string[]
    centerX: number
    centerY: number
    radius: number
  }

  function spreadLayoutClusters(graph: Graph): boolean {
    if (!LAYOUT_BRANCH_ENABLED || graph.order < LAYOUT_BRANCH_MIN_NODES) return false

    const clusters = collectLayoutClusters(graph)
    if (clusters.length === 0) return false
    const clusterByNode = new Map<string, LayoutCluster>()
    clusters.forEach((cluster) => {
      cluster.nodeIds.forEach((nodeId) => clusterByNode.set(nodeId, cluster))
    })

    const mainCluster = clusters.reduce((largest, cluster) =>
      cluster.nodeIds.length > largest.nodeIds.length ? cluster : largest,
    )
    const centerX = mainCluster.centerX
    const centerY = mainCluster.centerY
    const hasMultipleClusters = clusters.length > 1
    const mainSizeBoost = Math.log(mainCluster.nodeIds.length + 1) * 0.03
    const mainCompactBoost =
      mainCluster.radius > 0
        ? Math.min(0.34, LAYOUT_BRANCH_LEVEL_GAP / mainCluster.radius / 10)
        : 0.34
    const mainClusterScale =
      1 + Math.min(1.08, LAYOUT_BRANCH_STRENGTH * 0.1 + mainSizeBoost + 0.28 + mainCompactBoost)
    const expandedMainRadius = mainCluster.radius * mainClusterScale
    const satelliteGapBase =
      LAYOUT_BRANCH_COMPONENT_GAP * 0.32 + LAYOUT_BRANCH_LEVEL_GAP * 0.1 + expandedMainRadius * 0.14

    graph.updateEachNodeAttributes((nodeId, attributes) => {
      const cluster = clusterByNode.get(nodeId)
      if (!cluster) return attributes

      const sizeBoost = Math.log(cluster.nodeIds.length + 1) * 0.02
      const baseBoost = LAYOUT_BRANCH_STRENGTH * 0.08
      const compactBoost =
        cluster.radius > 0 ? Math.min(0.24, LAYOUT_BRANCH_LEVEL_GAP / cluster.radius / 18) : 0.24
      const intraScale =
        cluster === mainCluster
          ? mainClusterScale
          : 1 + Math.min(0.72, baseBoost + sizeBoost + 0.22 + compactBoost)

      const offsetX = Number(attributes.x) - cluster.centerX
      const offsetY = Number(attributes.y) - cluster.centerY
      let nextX = cluster.centerX + offsetX * intraScale
      let nextY = cluster.centerY + offsetY * intraScale

      if (!hasMultipleClusters || cluster === mainCluster) {
        return { ...attributes, x: nextX, y: nextY }
      }

      const fromCenterX = cluster.centerX - centerX
      const fromCenterY = cluster.centerY - centerY
      const distance = Math.hypot(fromCenterX, fromCenterY)
      let dirX = 0
      let dirY = 0

      if (distance > 0.0001) {
        dirX = fromCenterX / distance
        dirY = fromCenterY / distance
      } else {
        const angle = ((cluster.nodeIds.length * 37) % 360) * (Math.PI / 180)
        dirX = Math.cos(angle)
        dirY = Math.sin(angle)
      }

      const clusterCoreBuffer = Math.max(0, expandedMainRadius - mainCluster.radius)
      const clusterSpreadBias = Math.log(cluster.nodeIds.length + 1) * 24 + cluster.radius * 0.22
      const targetDistance =
        satelliteGapBase + cluster.radius * 0.48 + clusterCoreBuffer * 0.9 + clusterSpreadBias
      const shiftDistance = Math.max(0, targetDistance - distance) + LAYOUT_BRANCH_JITTER * 0.35
      nextX += dirX * shiftDistance
      nextY += dirY * shiftDistance

      return { ...attributes, x: nextX, y: nextY }
    })

    return true
  }

  function collectLayoutClusters(graph: Graph): LayoutCluster[] {
    const visited = new Set<string>()
    const clusters: LayoutCluster[] = []

    graph.forEachNode((nodeId) => {
      if (visited.has(nodeId)) return

      const queue = [nodeId]
      const nodeIds: string[] = []
      visited.add(nodeId)

      for (let i = 0; i < queue.length; i += 1) {
        const current = queue[i]
        if (!current) continue
        nodeIds.push(current)
        graph.neighbors(current).forEach((neighbor) => {
          if (visited.has(neighbor)) return
          visited.add(neighbor)
          queue.push(neighbor)
        })
      }

      let sumX = 0
      let sumY = 0
      let count = 0

      for (const id of nodeIds) {
        const x = Number(graph.getNodeAttribute(id, 'x'))
        const y = Number(graph.getNodeAttribute(id, 'y'))
        if (!Number.isFinite(x) || !Number.isFinite(y)) continue
        sumX += x
        sumY += y
        count += 1
      }

      if (count === 0) return

      const centerX = sumX / count
      const centerY = sumY / count
      let radius = 0

      for (const id of nodeIds) {
        const x = Number(graph.getNodeAttribute(id, 'x'))
        const y = Number(graph.getNodeAttribute(id, 'y'))
        if (!Number.isFinite(x) || !Number.isFinite(y)) continue
        radius = Math.max(radius, Math.hypot(x - centerX, y - centerY))
      }

      clusters.push({ nodeIds, centerX, centerY, radius })
    })

    return clusters
  }

  interface ScreenOverlapNode {
    id: string
    x: number
    y: number
    radius: number
  }

  function settleScreenOverlaps(graph: Graph): boolean {
    if (!LAYOUT_SCREEN_OVERLAP_ENABLED || graph.order < 2 || !container.value) return false

    const viewportWidth = container.value.clientWidth
    const viewportHeight = container.value.clientHeight
    if (viewportWidth <= 0 || viewportHeight <= 0) return false

    let minX = Number.POSITIVE_INFINITY
    let maxX = Number.NEGATIVE_INFINITY
    let minY = Number.POSITIVE_INFINITY
    let maxY = Number.NEGATIVE_INFINITY
    const nodes: ScreenOverlapNode[] = []

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
      nodes.push({ id, x, y, radius: size })
    })

    if (nodes.length < 2) return false

    const width = maxX - minX
    const height = maxY - minY
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
      return false
    }

    const unitsPerPixel = Math.max(width / viewportWidth, height / viewportHeight)
    if (!Number.isFinite(unitsPerPixel) || unitsPerPixel <= 0) return false

    const gap = LAYOUT_SCREEN_OVERLAP_GAP_PX * unitsPerPixel
    let maxRadius = 0
    for (const node of nodes) {
      node.radius *= unitsPerPixel
      maxRadius = Math.max(maxRadius, node.radius)
    }

    const cellSize = Math.max(1, maxRadius * 2 + gap)
    let moved = false

    for (let iteration = 0; iteration < LAYOUT_SCREEN_OVERLAP_ITERATIONS; iteration += 1) {
      const grid = new Map<string, number[]>()

      for (let i = 0; i < nodes.length; i += 1) {
        const node = nodes[i]
        if (!node) continue
        const key = `${Math.floor(node.x / cellSize)}:${Math.floor(node.y / cellSize)}`
        const bucket = grid.get(key)
        if (bucket) bucket.push(i)
        else grid.set(key, [i])
      }

      const shiftX = new Float32Array(nodes.length)
      const shiftY = new Float32Array(nodes.length)
      let collisions = 0

      for (let i = 0; i < nodes.length; i += 1) {
        const a = nodes[i]
        if (!a) continue
        const cellX = Math.floor(a.x / cellSize)
        const cellY = Math.floor(a.y / cellSize)

        for (let dxCell = -1; dxCell <= 1; dxCell += 1) {
          for (let dyCell = -1; dyCell <= 1; dyCell += 1) {
            const bucket = grid.get(`${cellX + dxCell}:${cellY + dyCell}`)
            if (!bucket) continue

            for (const j of bucket) {
              if (j <= i) continue
              const b = nodes[j]
              if (!b) continue

              let dx = b.x - a.x
              let dy = b.y - a.y
              let distance = Math.hypot(dx, dy)
              const target = a.radius + b.radius + gap
              if (distance >= target) continue

              if (distance < 0.0001) {
                const angle = ((i * 97 + j * 53) % 360) * (Math.PI / 180)
                dx = Math.cos(angle)
                dy = Math.sin(angle)
                distance = 1
              }

              collisions += 1
              const push = ((target - distance) / distance) * LAYOUT_SCREEN_OVERLAP_STRENGTH * 0.5
              const moveX = dx * push
              const moveY = dy * push
              shiftX[i] = (shiftX[i] ?? 0) - moveX
              shiftY[i] = (shiftY[i] ?? 0) - moveY
              shiftX[j] = (shiftX[j] ?? 0) + moveX
              shiftY[j] = (shiftY[j] ?? 0) + moveY
            }
          }
        }
      }

      if (collisions === 0) break

      for (let i = 0; i < nodes.length; i += 1) {
        const node = nodes[i]
        if (!node) continue
        node.x += shiftX[i] ?? 0
        node.y += shiftY[i] ?? 0
      }

      moved = true
    }

    if (!moved) return false

    for (const node of nodes) {
      graph.mergeNodeAttributes(node.id, { x: node.x, y: node.y })
    }

    return true
  }

  function stopOverlapLayout(runVisualSettle: boolean = false): void {
    const hadOverlapLayout = overlapLayout.value !== null

    if (overlapStopTimer.value) {
      clearTimeout(overlapStopTimer.value)
      overlapStopTimer.value = null
    }

    if (overlapLayout.value) {
      overlapLayout.value.kill()
      overlapLayout.value = null
    }

    if (runVisualSettle && hadOverlapLayout && graphInstance.value) {
      settleScreenOverlaps(graphInstance.value)
    }

    if (graphInstance.value) {
      cacheLayoutPositions(graphInstance.value)
    }

    sigmaInstance.value?.refresh({ skipIndexation: true })
    if (runVisualSettle) onLayoutSettled?.()
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
    if (graphInstance.value) {
      graphInstance.value.clear()
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

  function setReducers(
    reducers: Pick<Settings, 'nodeReducer' | 'edgeReducer'>,
    edgeLabelsVisible?: boolean,
  ): void {
    const sigma = sigmaInstance.value
    if (!sigma) return
    // Sigma schedules one refresh for setSettings; avoid two indexations plus a
    // third explicit refresh when focus/filter reducers change together.
    const settings: Partial<Settings> = {
      nodeReducer: reducers.nodeReducer,
      edgeReducer: reducers.edgeReducer,
    }
    if (typeof edgeLabelsVisible === 'boolean' && edgeLabelsVisible !== lastEdgeLabelsVisible) {
      lastEdgeLabelsVisible = edgeLabelsVisible
      settings.renderEdgeLabels = edgeLabelsVisible
    }
    sigma.setSettings(settings)
  }

  /**
   * Toggle the target-node kind suffix on edge labels (e.g. "IMPORTS Class"). A
   * cheap repaint (no re-index) since it only changes label text/colors.
   */
  function setEdgeKindVisible(visible: boolean): void {
    setShowEdgeKind(visible)
    sigmaInstance.value?.refresh({ skipIndexation: true })
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
    sigma
      .getCamera()
      .animate(
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
    resetLayout,
    zoomToFit,
    focusNode,
    startLayout: () => {
      if (graphInstance.value) startLayout(graphInstance.value)
    },
    stopLayout,
    setReducers,
    setEdgeKindVisible,
    setGhostPartition,
  }
}
