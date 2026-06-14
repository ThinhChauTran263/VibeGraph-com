/**
 * Sigma.js graph lifecycle, rendering, and interaction composable.
 * Handles: init, destroy, node/edge reducers, ForceAtlas2 layout, zoom.
 */

import { shallowRef, onUnmounted, type Ref } from 'vue'
import Sigma from 'sigma'
import type Graph from 'graphology'
import type { Settings } from 'sigma/settings'
import FA2Layout from 'graphology-layout-forceatlas2/worker'

export interface UseSigmaOptions {
  container: Ref<HTMLDivElement | null>
  onNodeClick?: (nodeId: string) => void
  onStageClick?: () => void
}

export function useSigma(options: UseSigmaOptions) {
  const { container, onNodeClick, onStageClick } = options

  const sigmaInstance = shallowRef<Sigma | null>(null)
  const graphInstance = shallowRef<Graph | null>(null)
  const layout = shallowRef<FA2Layout | null>(null)
  const layoutStopTimer = shallowRef<ReturnType<typeof setTimeout> | null>(null)

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
      labelRenderedSizeThreshold: 8,
      labelColor: { color: '#e5e7eb' },
      labelFont: 'Inter, system-ui, sans-serif',
      labelSize: 13,
      labelWeight: '600',
      edgeLabelColor: { color: '#cbd5e1' },
      edgeLabelSize: 11,
    })

    sigmaInstance.value = sigma

    // Register node click handler
    if (onNodeClick) {
      sigma.on('clickNode', ({ node }) => {
        onNodeClick(node)
      })
    }

    // Background (stage) click deselects
    if (onStageClick) {
      sigma.on('clickStage', () => {
        onStageClick()
      })
    }

    // Start ForceAtlas2 layout in a web worker
    startLayout(graph)
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
    if (sigmaInstance.value) {
      sigmaInstance.value.kill()
      sigmaInstance.value = null
    }
    graphInstance.value = null
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
    init,
    dispose,
    zoomToFit,
    startLayout: () => {
      if (graphInstance.value) startLayout(graphInstance.value)
    },
    stopLayout,
    setReducers,
    setEdgeLabelsVisible,
  }
}
