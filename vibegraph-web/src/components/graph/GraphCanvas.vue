<script setup lang="ts">
/**
 * GraphCanvas - Sigma.js container.
 * Renders force-directed graph with WebGL.
 *
 * Features:
 * - ForceAtlas2 layout (in Web Worker)
 * - Click node, emit selected
 * - Loading + error states
 */
import { onActivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useGraphData } from '@/composables/useGraphData'
import { useGraphExpand } from '@/composables/useGraphExpand'
import { useFilters } from '@/composables/useFilters'
import { useSigma } from '@/composables/useSigma'
import { debounce } from '@/lib/debounce'
import { getEdgeAttributes, getNodeColor, getNodeSize } from '@/lib/graphAdapter'
import { DEEP_LOAD_EDGE_TYPES, DEEP_LOAD_NODE_TYPES } from '@/lib/constants'
import SearchBar from '@/components/graph/SearchBar.vue'
import FilterPanel from '@/components/panels/FilterPanel.vue'
import ExplorerPanel from '@/components/panels/ExplorerPanel.vue'
import FlowsPanel from '@/components/panels/FlowsPanel.vue'
import DataFlowDetailPanel from '@/components/panels/DataFlowDetailPanel.vue'
import NodeDetailPanel, { type RelationHoverPayload } from '@/components/panels/NodeDetailPanel.vue'
import ImpactAnalysisPanel from '@/components/panels/ImpactAnalysisPanel.vue'
import {
  createSelectionFocusReducers,
  partitionFocusGraph,
  resolveFocusLabelDensity,
  type FocusLabelDensity,
  type HoveredRelation,
} from '@/lib/focusMode'
import { createFlowFocusReducers, partitionFlowGraph } from '@/lib/flowFocus'
import type { FlowListItem } from '@/lib/dataFlow'
import { useGraphRealtime } from '@/composables/useGraphRealtime'
import type { GraphIncrementalEvent, GraphNode, GraphUpdateEvent, NodeType } from '@/types/graph'

const props = defineProps<{
  projectId: string
}>()

const emit = defineEmits<{
  (e: 'nodeSelected', nodeId: string | null): void
}>()

const canvasRef = ref<HTMLDivElement | null>(null)

// Left sidebar tab: file Explorer (browse source), Graph filters, or Data Flows.
const activeSidebarTab = ref<'explorer' | 'filters' | 'flows'>('explorer')

// Active Data Flow highlight (set of node ids + connecting edge ids). When set,
// the canvas spotlights the whole traced chain instead of a single node.
const activeFlow = ref<{
  nodeIds: Set<string>
  edgeIds: Set<string>
  primaryNodeId: string
} | null>(null)
// The selected flow shown in the right-hand DataFlowDetailPanel.
const activeFlowDetail = ref<FlowListItem | null>(null)

// User-resizable left sidebar width (px). Bound to a CSS custom property so the
// responsive media query can still collapse the layout on narrow screens.
const wrapperRef = ref<HTMLElement | null>(null)
const sidebarWidth = ref(288)
let resizing = false

// Collapsed state for the left sidebar. When collapsed the panel column shrinks
// to zero and a floating chevron lets the user reopen it, freeing the full width
// for the graph canvas.
const sidebarCollapsed = ref(false)

function toggleSidebar(): void {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function onResizeStart(event: PointerEvent): void {
  resizing = true
  ;(event.target as HTMLElement).setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', onResizeEnd)
  event.preventDefault()
}

function onResizeMove(event: PointerEvent): void {
  if (!resizing || !wrapperRef.value) return
  const left = wrapperRef.value.getBoundingClientRect().left
  sidebarWidth.value = Math.min(Math.max(event.clientX - left, 220), 640)
}

function onResizeEnd(): void {
  resizing = false
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', onResizeEnd)
}

// Monotonic counter to discard stale async graph loads (see load()).
let loadSeq = 0

// Graph focus is resolved from three independent inputs with a deterministic
// priority (see applyFocusReducers):
//   1. hoveredRelation   — a relation item in Node Detail is being hovered (preview)
//   2. pinnedRelation    — a relation item in Node Detail was clicked (pinned/sticky)
//   3. hoveredGraphNode  — a node on the graph is being hovered (temporary)
//   4. selectedNode      — a node was clicked/searched (sticky)
//   5. none              — default full graph view
//
// hoveredRelation/pinnedRelation are relative to the SELECTED node (the relation's
// edge connects the selected node to a counterpart). A pinned relation survives the
// mouse leaving the item; only another relation click, a graph-node click, or a
// selection reset clears it.
const hoveredRelation = ref<HoveredRelation | null>(null)
const pinnedRelation = ref<HoveredRelation | null>(null)
const hoveredGraphNode = ref<string | null>(null)
const labelDensity = ref<FocusLabelDensity>('nodes')

// Node types present in the currently highlighted cluster (selected/hovered node +
// its bright neighbours, or the active flow's nodes). Drives the yellow ring around
// matching swatches in the Legend, so the legend reflects what the focus contains.
// Empty when nothing is focused.
const highlightedNodeTypes = ref<Set<NodeType>>(new Set())

/** Map a set of node ids to the set of their node types (via the live graph). */
function updateHighlightedTypes(nodeIds: Set<string> | null): void {
  const graph = graphInstance.value
  if (!nodeIds || !graph) {
    if (highlightedNodeTypes.value.size > 0) highlightedNodeTypes.value = new Set()
    return
  }
  const types = new Set<NodeType>()
  nodeIds.forEach((id) => {
    if (!graph.hasNode(id)) return
    const type = graph.getNodeAttribute(id, 'nodeType') as NodeType | undefined
    if (type) types.add(type)
  })
  highlightedNodeTypes.value = types
}

// User toggle for showing edge type labels at all. When off, edge labels never
// render regardless of zoom/selection. Driven by the "Edge labels" button.
const edgeLabelsEnabled = ref(false)

function toggleEdgeLabels(): void {
  edgeLabelsEnabled.value = !edgeLabelsEnabled.value
  applyFocusReducers()
}

// User toggle for appending the target node's kind to edge labels (e.g. "IMPORTS
// Class", kind tinted to the node color). Independent of the edge-label toggle.
const edgeKindEnabled = ref(false)

function toggleEdgeKind(): void {
  edgeKindEnabled.value = !edgeKindEnabled.value
  setEdgeKindVisible?.(edgeKindEnabled.value)
}

function resetRelationFocus(): void {
  hoveredRelation.value = null
  pinnedRelation.value = null
}

// T60: subscribe to realtime graph updates for the active project and patch the
// store in place. Resubscribes on project change and cleans up on unmount.
useGraphRealtime(() => props.projectId, { onPatched: onRealtimePatched })

const filters = useFilters()

const {
  graphData,
  filteredGraphData,
  loading,
  error,
  loadGraph,
  ensureDeepGraph,
  buildGraph,
  selectNode,
  clearSelection,
  selectedNode,
  payloadMode,
  nodes,
} = useGraphData()

const { expandNode, reset: resetExpand } = useGraphExpand()

const {
  init: initSigma,
  graphInstance,
  setReducers,
  setEdgeLabelsVisible,
  setEdgeKindVisible,
  setGhostPartition,
  refresh: refreshSigma,
  resetLayout: resetSigmaLayout,
  zoomToFit,
  focusNode,
} = useSigma({
  container: canvasRef,
  onNodeClick: (nodeId: string) => {
    const node = nodes.value.find((n) => n.id === nodeId) ?? null
    // Clicking a graph node clears any pinned/previewed relation focus and takes
    // over selection (priority 4). The hovered-node state is cleared too so the
    // newly selected node is what stays focused after the click settles.
    resetRelationFocus()
    hoveredGraphNode.value = null
    activeFlow.value = null
    activeFlowDetail.value = null
    selectNode(node)
    emit('nodeSelected', nodeId)
  },
  onNodeDoubleClick: (nodeId: string) => {
    // Lazy expand: pull this node's 1-hop neighborhood from the backend and merge it in.
    // The graphData change drives the debounced rebuild, so the new nodes render automatically.
    void expandNode(props.projectId, nodeId, 1)
  },
  onStageClick: () => {
    if (!selectedNode.value) return
    resetRelationFocus()
    hoveredGraphNode.value = null
    clearSelection()
    emit('nodeSelected', null)
  },
  onNodeHover: (nodeId: string) => {
    if (selectedNode.value || pinnedRelation.value || hoveredRelation.value || activeFlow.value)
      return
    hoveredGraphNode.value = nodeId
    applyFocusReducers()
  },
  onNodeLeave: () => {
    hoveredGraphNode.value = null
    applyFocusReducers()
  },
  onCameraRatioChange: (ratio: number) => {
    const nextDensity = resolveFocusLabelDensity(ratio)
    if (nextDensity === labelDensity.value) return
    labelDensity.value = nextDensity
    // Apply the label-density reducer swap immediately so edge labels appear the
    // moment you cross the zoom threshold and then stay drawn every frame (the
    // per-frame budget + viewport culling keep that cheap) — no vanish/reload.
    applyFocusReducers()
  },
})

/**
 * Apply visual reducers using the deterministic focus priority documented above.
 * A relation focus (hover preview, then pinned) wins over a hovered graph node,
 * which wins over the clicked/searched selection; with no focus we keep the
 * default full graph view.
 *
 * Whenever a focus is active, unrelated nodes/edges are HIDDEN in the interactive
 * Sigma and the background partition is handed to the ghost canvas layer, which
 * redraws them below the WebGL edges. This guarantees a background node can never
 * cover a foreground edge.
 */
function applyFocusReducers(): void {
  if (!graphInstance.value) return
  const graph = graphInstance.value

  // 1 & 2: relation focus (hover preview, then pinned) — relative to the selected
  // node. The relation's edge connects the selected node to its counterpart.
  const relation = hoveredRelation.value ?? pinnedRelation.value
  if (selectedNode.value && relation) {
    focusOn(selectedNode.value.id, relation)
    return
  }

  // 3: hovered graph node — temporary focus on the hovered node's neighborhood.
  if (hoveredGraphNode.value && graph.hasNode(hoveredGraphNode.value)) {
    focusOn(hoveredGraphNode.value, null)
    return
  }

  // 3.5: an active Data Flow highlights the whole traced chain.
  if (activeFlow.value) {
    applyFlowFocus()
    return
  }

  // 4: clicked/searched selection focus.
  if (selectedNode.value) {
    focusOn(selectedNode.value.id, null)
    return
  }

  // 5: default full graph view (no node focused). Edge type labels still reveal
  // by zoom: when zoomed in enough (edges density) we FORCE them for the whole
  // graph (Sigma otherwise only labels edges between already-labelled nodes, so
  // they'd flicker/vanish without a selection). The renderer hides any that don't
  // fully fit their edge.
  const showEdgeLabels = edgeLabelsEnabled.value && labelDensity.value === 'edges'
  setReducers({
    nodeReducer: (_node, attributes) => attributes,
    edgeReducer: (_edge, attributes) => {
      return showEdgeLabels ? { ...attributes, forceLabel: true } : attributes
    },
  })
  setGhostPartition?.(null)
  setEdgeLabelsVisible?.(showEdgeLabels)
  updateHighlightedTypes(null)
}

/** Focus the graph on a node (and optional single relation), revealing edge labels. */
function focusOn(nodeId: string, relation: HoveredRelation | null): void {
  if (!graphInstance.value) return
  setReducers(
    createSelectionFocusReducers(nodeId, graphInstance.value, relation, labelDensity.value),
  )
  const partition = partitionFocusGraph(nodeId, graphInstance.value, relation)
  setGhostPartition?.(partition)
  updateHighlightedTypes(partition.foregroundNodes)
  setEdgeLabelsVisible?.(edgeLabelsEnabled.value && labelDensity.value === 'edges')
}

/** Spotlight the whole active Data Flow chain on the graph. */
function applyFlowFocus(): void {
  if (!graphInstance.value || !activeFlow.value) return
  const { nodeIds, edgeIds, primaryNodeId } = activeFlow.value
  setReducers(createFlowFocusReducers(nodeIds, edgeIds, graphInstance.value, primaryNodeId))
  setGhostPartition?.(partitionFlowGraph(nodeIds, edgeIds, graphInstance.value))
  updateHighlightedTypes(nodeIds)
  setEdgeLabelsVisible?.(true)
}

// A Data Flow was selected in the Flows panel: highlight the chain, open the
// detail panel, and exit any single-node selection.
function onFlowSelect(item: FlowListItem): void {
  resetRelationFocus()
  hoveredGraphNode.value = null
  clearSelection()
  emit('nodeSelected', null)
  activeFlowDetail.value = item
  activeFlow.value = {
    nodeIds: new Set(item.flow.steps.map((step) => step.nodeId)),
    edgeIds: new Set(item.flow.edgeIds),
    primaryNodeId: item.flow.steps[0]?.nodeId ?? '',
  }
  applyFlowFocus()
}

// A flow step row was activated: keep the chain highlighted but center the node.
function onFlowStep(nodeId: string): void {
  if (activeFlow.value) {
    activeFlow.value = { ...activeFlow.value, primaryNodeId: nodeId }
    applyFlowFocus()
  }
  focusNode(nodeId)
}

// The flow was cleared: restore the default graph view.
function onFlowClear(): void {
  activeFlow.value = null
  activeFlowDetail.value = null
  applyFocusReducers()
}

async function load(projectId: string) {
  // Stale-load guard: if the project changes (or another load starts) while this
  // fetch is in flight, a late-resolving response must NOT init an outdated graph.
  const seq = ++loadSeq
  const graph = await loadGraph(projectId)
  if (seq !== loadSeq) return
  if (graph && canvasRef.value) {
    // A project (re)load starts from a clean layout: drop cached positions so the
    // fresh graph is laid out from scratch. Filter toggles / expansions keep the
    // cache (see useSigma.applyCachedLayout) so they never recompute the layout.
    resetSigmaLayout()
    initSigma(graph)
    applyFocusReducers()
    // Frame the whole graph once the canvas has its final box (next frame), so a
    // first-paint mis-measure can't leave the graph offset to one edge.
    requestAnimationFrame(() => {
      if (seq === loadSeq) zoomToFit()
    })
    loadDeepGraphIfNeeded()
  }
}

function needsDeepGraphForVisibleFilters(): boolean {
  if (payloadMode.value === 'baseline+deep') return false
  const nodeStats = graphData.value.nodeStats
  const edgeStats = graphData.value.edgeStats
  const hiddenNodes = filters.hiddenNodeTypes.value
  const hiddenEdges = filters.hiddenEdgeTypes.value

  for (const type of DEEP_LOAD_NODE_TYPES) {
    if (!hiddenNodes.has(type) && (nodeStats[type] ?? 0) === 0) {
      return true
    }
  }
  for (const type of DEEP_LOAD_EDGE_TYPES) {
    if (!hiddenEdges.has(type) && (edgeStats[type] ?? 0) === 0) {
      return true
    }
  }
  return false
}

let pendingDeepLoad: Promise<void> | null = null

function loadDeepGraphIfNeeded(): void {
  if (!props.projectId || !needsDeepGraphForVisibleFilters() || pendingDeepLoad) return
  pendingDeepLoad = ensureDeepGraph(props.projectId).finally(() => {
    pendingDeepLoad = null
  })
}

function onSearchSelect(node: GraphNode): void {
  selectNode(node)
  emit('nodeSelected', node.id)
  applyFocusReducers()
}

// Clicking a file in the Explorer focuses its representative node. The Explorer
// is built from the FULL graph (so the whole source tree shows even when filters
// hide types), so resolve against graphData rather than the filtered `nodes`.
function onExplorerSelect(nodeId: string): void {
  const node = graphData.value.nodes.find((candidate) => candidate.id === nodeId) ?? null
  if (!node) return
  resetRelationFocus()
  hoveredGraphNode.value = null
  selectNode(node)
  emit('nodeSelected', node.id)
  applyFocusReducers()
}

function onSearchClear(): void {
  clearSelection()
  emit('nodeSelected', null)
}

// Clicking an affected node in Impact Analysis navigates the graph to it: select it (so the
// detail/impact panels retarget) and center it on the canvas. Resolve against the FULL graph
// since a dependent may be filtered out of the visible `nodes`.
function onImpactSelect(nodeId: string): void {
  const node = graphData.value.nodes.find((candidate) => candidate.id === nodeId) ?? null
  if (!node) return
  resetRelationFocus()
  hoveredGraphNode.value = null
  activeFlow.value = null
  activeFlowDetail.value = null
  selectNode(node)
  emit('nodeSelected', node.id)
  applyFocusReducers()
  focusNode(node.id)
}

function onDetailClose(): void {
  resetRelationFocus()
  clearSelection()
  emit('nodeSelected', null)
}

// Hover over a relation item is a temporary PREVIEW (priority 1). It does not
// disturb the pinned relation; on mouse-leave (payload === null) the preview
// clears and focus falls back to the pinned relation, then the selection.
function onRelationHover(payload: RelationHoverPayload | null): void {
  hoveredRelation.value = payload
  applyFocusReducers()
}

// Clicking a relation item PINS it (priority 2). The pin survives the mouse
// leaving the item — only another relation click, a graph-node click, or a
// selection reset clears it. We intentionally do NOT navigate to the counterpart;
// the selected node stays selected so the relation stays anchored to it.
function onRelationSelect(payload: RelationHoverPayload): void {
  hoveredRelation.value = null
  pinnedRelation.value = payload
  applyFocusReducers()
}

onMounted(() => {
  if (props.projectId) {
    load(props.projectId)
  }
})

// Under <KeepAlive> the component is cached (not unmounted) on tab switch, so the
// graph is NOT reloaded. When it becomes visible again the canvas DOM was just
// re-attached, so ask Sigma to re-measure and repaint to avoid a blank/stale canvas.
onActivated(() => {
  refreshSigma()
})

watch(
  () => props.projectId,
  (newId) => {
    resetExpand()
    if (newId) load(newId)
  },
)

watch([filters.hiddenNodeTypes, filters.hiddenEdgeTypes], () => {
  loadDeepGraphIfNeeded()
})

watch(selectedNode, () => {
  applyFocusReducers()
})

// When a realtime patch is applied directly to the live Sigma graph, the store also
// changes → the filteredGraphData watcher below would otherwise trigger a full rebuild
// (which resets camera/zoom). This flag tells that watcher to skip exactly one rebuild.
let skipNextRebuild = false

/**
 * Mirror a realtime event on the live Sigma graph without rebuilding it, so adding a class C
 * just inserts C (near its neighbours) and removing a file drops only its nodes — camera, zoom
 * and existing node positions are preserved. FULL_UPDATE falls back to the normal rebuild.
 */
function onRealtimePatched(event: GraphUpdateEvent): void {
  if (event.type !== 'INCREMENTAL') return
  if (!graphInstance.value) return // no live graph yet → let the rebuild path handle it
  try {
    patchSigmaIncremental(event)
    skipNextRebuild = true
  } catch {
    // Patch failed for any reason: let the debounced rebuild converge instead.
    skipNextRebuild = false
  }
}

function patchSigmaIncremental(event: GraphIncrementalEvent): void {
  const graph = graphInstance.value
  if (!graph) return
  const hiddenNodes = filters.hiddenNodeTypes.value
  const hiddenEdges = filters.hiddenEdgeTypes.value

  // Removals first (dropNode also drops its incident edges).
  for (const id of event.removed?.nodeIds ?? []) {
    if (graph.hasNode(id)) graph.dropNode(id)
  }
  for (const id of event.removed?.edgeIds ?? []) {
    if (graph.hasEdge(id)) graph.dropEdge(id)
  }

  const addedEdges = event.added?.edges ?? []
  // Added/modified nodes.
  for (const node of [...(event.added?.nodes ?? []), ...(event.modified?.nodes ?? [])]) {
    if (hiddenNodes.has(node.type)) continue
    if (graph.hasNode(node.id)) {
      graph.mergeNodeAttributes(node.id, {
        label: node.name,
        size: getNodeSize(node.type),
        color: getNodeColor(node.type),
        nodeType: node.type,
        layer: getNodeLayer(node),
        fullName: node.fullName,
        filePath: node.filePath,
        lineNumber: node.lineNumber,
      })
      continue
    }
    const { x, y } = spawnPosition(node.id, addedEdges)
    graph.addNode(node.id, {
      label: node.name,
      x,
      y,
      size: getNodeSize(node.type),
      color: getNodeColor(node.type),
      type: 'circle',
      nodeType: node.type,
      layer: getNodeLayer(node),
      fullName: node.fullName,
      filePath: node.filePath,
      lineNumber: node.lineNumber,
    })
  }

  // Added edges: respect hidden types and present endpoints. Preserve every
  // backend edge key so directed edges and multiple edge types between the same
  // node pair are not silently dropped.
  for (const edge of addedEdges) {
    if (hiddenEdges.has(edge.type)) continue
    if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target)) continue
    if (graph.hasEdge(edge.id)) continue
    graph.addEdgeWithKey(edge.id, edge.source, edge.target, getEdgeAttributes(edge))
  }

  refreshSigma()
  applyFocusReducers()
}

/** Place a newly-added node next to an already-present neighbour (small jitter), else near origin. */
function spawnPosition(
  nodeId: string,
  edges: { source: string; target: string }[],
): { x: number; y: number } {
  const graph = graphInstance.value
  if (graph) {
    for (const edge of edges) {
      const other =
        edge.source === nodeId ? edge.target : edge.target === nodeId ? edge.source : null
      if (other && graph.hasNode(other)) {
        const ox = graph.getNodeAttribute(other, 'x') as number
        const oy = graph.getNodeAttribute(other, 'y') as number
        return { x: ox + (Math.random() * 40 - 20), y: oy + (Math.random() * 40 - 20) }
      }
    }
  }
  return { x: Math.random() * 200 - 100, y: Math.random() * 200 - 100 }
}

function getNodeLayer(node: GraphNode): string {
  const value = node.properties?.layer ?? node.properties?.springLayer
  return typeof value === 'string' && value ? value : ''
}

// Rebuilding + re-rendering the whole Sigma graph is expensive. Filter toggles can
// fire several reactive changes in a burst, so we debounce the rebuild: the graph is
// rebuilt once after the user stops toggling, never on every intermediate change.
const rebuildGraph = debounce((data: typeof filteredGraphData.value) => {
  // A realtime patch already applied this change in place — don't rebuild (would reset camera).
  if (skipNextRebuild) {
    skipNextRebuild = false
    return
  }
  if (!canvasRef.value || loading.value || error.value) return
  initSigma(buildGraph(data))
  applyFocusReducers()
}, 200)

watch(filteredGraphData, (graphData) => {
  // Selection consistency is cheap and must stay synchronous so a stale selected
  // node is cleared immediately even before the debounced rebuild runs.
  if (selectedNode.value && !graphData.nodes.some((node) => node.id === selectedNode.value?.id)) {
    clearSelection()
  }

  if (!canvasRef.value || loading.value || error.value) return
  rebuildGraph(graphData)
})

onUnmounted(() => {
  rebuildGraph.cancel()
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', onResizeEnd)
})
</script>

<template>
  <div
    ref="wrapperRef"
    class="graph-canvas-wrapper"
    :class="{
      'graph-canvas-wrapper--detail-open': !loading && !error && (selectedNode || activeFlowDetail),
      'graph-canvas-wrapper--collapsed': !loading && !error && sidebarCollapsed,
      'graph-canvas-wrapper--loading': loading || error,
    }"
    :style="{ '--sidebar-width': (sidebarCollapsed ? 0 : sidebarWidth) + 'px' }"
  >
    <aside v-show="!loading && !error && !sidebarCollapsed" class="graph-canvas__sidebar">
      <div class="graph-canvas__sidebar-topbar">
        <div class="graph-canvas__sidebar-tabs" role="tablist" aria-label="Sidebar panels">
          <button
            class="graph-canvas__sidebar-tab"
            :class="{ 'graph-canvas__sidebar-tab--active': activeSidebarTab === 'explorer' }"
            type="button"
            role="tab"
            :aria-selected="activeSidebarTab === 'explorer'"
            @click="activeSidebarTab = 'explorer'"
          >
            Explorer
          </button>
          <button
            class="graph-canvas__sidebar-tab"
            :class="{ 'graph-canvas__sidebar-tab--active': activeSidebarTab === 'filters' }"
            type="button"
            role="tab"
            :aria-selected="activeSidebarTab === 'filters'"
            @click="activeSidebarTab = 'filters'"
          >
            Filters
          </button>
          <button
            class="graph-canvas__sidebar-tab"
            :class="{ 'graph-canvas__sidebar-tab--active': activeSidebarTab === 'flows' }"
            type="button"
            role="tab"
            :aria-selected="activeSidebarTab === 'flows'"
            @click="activeSidebarTab = 'flows'"
          >
            Flows
          </button>
        </div>
        <button
          class="graph-canvas__sidebar-collapse"
          type="button"
          title="Collapse panel"
          aria-label="Collapse sidebar panel"
          @click="toggleSidebar"
        >
          <span aria-hidden="true">‹</span>
        </button>
      </div>

      <ExplorerPanel
        v-show="activeSidebarTab === 'explorer'"
        :nodes="graphData.nodes"
        :selected-node-id="selectedNode?.id ?? null"
        :highlighted-types="highlightedNodeTypes"
        @select="onExplorerSelect"
      />
      <FilterPanel v-show="activeSidebarTab === 'filters'" :graph-data="graphData" />
      <FlowsPanel
        v-show="activeSidebarTab === 'flows'"
        :graph-data="graphData"
        :selected-flow-id="activeFlowDetail?.id ?? null"
        @select="onFlowSelect"
      />
    </aside>

    <div
      v-if="!loading && !error && !sidebarCollapsed"
      class="graph-canvas__resizer"
      role="separator"
      aria-orientation="vertical"
      aria-label="Resize sidebar"
      title="Drag to resize"
      :style="{ left: sidebarWidth + 'px' }"
      @pointerdown="onResizeStart"
    />

    <div class="graph-canvas__stage">
      <div ref="canvasRef" class="graph-canvas" />

      <div v-if="!loading && !error" class="graph-top-controls">
        <button
          v-if="sidebarCollapsed"
          type="button"
          class="graph-canvas__sidebar-expand"
          title="Expand panel"
          aria-label="Expand sidebar panel"
          @click="toggleSidebar"
        >
          <span aria-hidden="true">›</span>
        </button>

        <button
          type="button"
          class="graph-edge-label-toggle"
          :class="{ 'graph-edge-label-toggle--off': !edgeLabelsEnabled }"
          :aria-pressed="edgeLabelsEnabled"
          @click="toggleEdgeLabels"
        >
          {{ edgeLabelsEnabled ? 'Edge labels: On' : 'Edge labels: Off' }}
        </button>

        <button
          type="button"
          class="graph-edge-label-toggle graph-edge-kind-toggle"
          :class="{ 'graph-edge-label-toggle--off': !edgeKindEnabled }"
          :aria-pressed="edgeKindEnabled"
          @click="toggleEdgeKind"
        >
          {{ edgeKindEnabled ? 'Node kind: On' : 'Node kind: Off' }}
        </button>

        <SearchBar
          :nodes="nodes"
          :selected-node-id="selectedNode?.id ?? null"
          @select="onSearchSelect"
          @clear="onSearchClear"
        />
      </div>

      <div v-if="!loading && !error" class="graph-controls-help" aria-label="Graph mouse controls">
        <div class="graph-controls-help__title">Controls</div>
        <div class="graph-controls-help__row">
          <span class="graph-controls-help__icon graph-controls-help__icon--primary">L</span>
          <span><strong>Left Click</strong><small>Select / Drag</small></span>
        </div>
        <div class="graph-controls-help__row">
          <span class="graph-controls-help__icon">R</span>
          <span><strong>Right / Mid</strong><small>Pan Canvas</small></span>
        </div>
        <div class="graph-controls-help__row">
          <span class="graph-controls-help__icon">S</span>
          <span><strong>Scroll Wheel</strong><small>Zoom In / Out</small></span>
        </div>
      </div>

      <div v-if="loading" class="graph-overlay graph-overlay--loading">
        <div class="spinner" aria-label="Loading graph" />
        <p>Loading graph...</p>
      </div>

      <div v-else-if="error" class="graph-overlay graph-overlay--error" role="alert">
        <p class="error-title">Failed to load graph</p>
        <p class="error-message">{{ error }}</p>
        <button class="retry-button" type="button" @click="load(props.projectId)">Retry</button>
      </div>

      <aside v-if="!loading && !error && activeFlowDetail" class="graph-canvas__detail">
        <DataFlowDetailPanel
          :item="activeFlowDetail"
          :selected-node-id="activeFlow?.primaryNodeId ?? null"
          @focus-step="onFlowStep"
          @close="onFlowClear"
        />
      </aside>

      <aside v-else-if="!loading && !error && selectedNode" class="graph-canvas__detail">
        <NodeDetailPanel
          :pinned-edge-id="pinnedRelation?.edgeId ?? null"
          :project-id="props.projectId"
          @close="onDetailClose"
          @relation-hover="onRelationHover"
          @relation-select="onRelationSelect"
        />
        <ImpactAnalysisPanel
          :project-id="props.projectId"
          :node="selectedNode"
          @select="onImpactSelect"
        />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.graph-canvas-wrapper {
  display: grid;
  grid-template-columns: var(--sidebar-width, 18rem) 1fr;
  position: relative;
  width: 100%;
  /* Fill the remaining height of the 100vh flex column in GraphView instead of
     forcing 100% (which resolves to the full viewport and overflows past it by
     the height of the tab bar, pushing the bottom-left controls box off-screen). */
  flex: 1 1 0;
  min-height: 0;
  background: #0f172a;
}

.graph-canvas-wrapper--detail-open {
  grid-template-columns: var(--sidebar-width, 18rem) 1fr;
}

/* When the sidebar is collapsed it is removed from the grid flow (display:none),
   so the stage would auto-place into the now-0px first track and shrink to
   nothing. Drop the empty sidebar track entirely so the stage fills the row. */
.graph-canvas-wrapper--collapsed {
  grid-template-columns: 1fr;
}

.graph-canvas-wrapper--collapsed.graph-canvas-wrapper--detail-open {
  grid-template-columns: 1fr;
}

/* While loading or in an error state the sidebar/detail are hidden, but the grid
   would otherwise keep their reserved columns and push the centered overlay off to
   one side. Collapse to a single full-width column so the spinner is truly centered. */
.graph-canvas-wrapper--loading,
.graph-canvas-wrapper--loading.graph-canvas-wrapper--detail-open {
  grid-template-columns: 1fr;
}

/* Draggable divider on the right edge of the sidebar. */
.graph-canvas__resizer {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 0.5rem;
  transform: translateX(-50%);
  z-index: 8;
  cursor: col-resize;
  touch-action: none;
}

.graph-canvas__resizer::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  background: rgba(148, 163, 184, 0.2);
  transition:
    background 150ms ease,
    width 150ms ease;
}

.graph-canvas__resizer:hover::after,
.graph-canvas__resizer:active::after {
  width: 3px;
  background: rgba(96, 165, 250, 0.8);
}

.graph-canvas__sidebar {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  overflow-y: auto;
  border-right: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.85);
}

.graph-canvas__sidebar-topbar {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 0.375rem;
  flex: 0 0 auto;
}

.graph-canvas__sidebar-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  flex: 1 1 auto;
  min-width: 0;
  /* Reserve room for the pinned collapse button so wrapped tabs never slide under it. */
  padding-right: 2.25rem;
}

.graph-canvas__sidebar-collapse {
  position: absolute;
  top: 0;
  right: 0;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.92);
  color: #cbd5e1;
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  transition:
    background 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
}

.graph-canvas__sidebar-collapse:hover,
.graph-canvas__sidebar-collapse:focus-visible {
  border-color: rgba(96, 165, 250, 0.6);
  background: rgba(37, 99, 235, 0.22);
  color: #f8fafc;
  outline: none;
}

/* Inline chevron to reopen the sidebar after it has been collapsed. */
.graph-canvas__sidebar-expand {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid rgba(148, 163, 184, 0.32);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.92);
  color: #cbd5e1;
  font-size: 1.25rem;
  line-height: 1;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
  transition:
    background 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
}

.graph-canvas__sidebar-expand:hover,
.graph-canvas__sidebar-expand:focus-visible {
  border-color: rgba(96, 165, 250, 0.7);
  background: rgba(37, 99, 235, 0.3);
  color: #f8fafc;
  outline: none;
}

.graph-canvas__sidebar-tab {
  flex: 1 1 auto;
  min-width: max-content;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  padding: 0.4rem 0.6rem;
  background: rgba(15, 23, 42, 0.92);
  color: #cbd5e1;
  font-size: 0.8125rem;
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
}

.graph-canvas__sidebar-tab:hover,
.graph-canvas__sidebar-tab:focus-visible {
  border-color: rgba(96, 165, 250, 0.6);
  outline: none;
}

.graph-canvas__sidebar-tab--active {
  border-color: rgba(96, 165, 250, 0.82);
  background: rgba(37, 99, 235, 0.32);
  color: #bfdbfe;
}

/* The Explorer and Flows panels own the remaining height and scroll internally. */
.graph-canvas__sidebar > .explorer-panel,
.graph-canvas__sidebar > .flows-panel {
  flex: 1 1 auto;
}

.graph-canvas__stage {
  position: relative;
  min-width: 0;
  height: 100%;
}

.graph-canvas {
  width: 100%;
  height: 100%;
  position: relative;
  background: #0f172a;
}

.graph-canvas__detail {
  position: absolute;
  top: 1rem;
  right: 1rem;
  bottom: 1rem;
  z-index: 7;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: min(23rem, calc(100% - 2rem));
  padding: 1rem;
  /* The whole column scrolls as one. Panels size to their content so the Impact
     Analysis results are never crushed to a zero-height (previously the panel was
     capped at 38vh and its header/controls ate all of it, hiding the results). */
  min-height: 0;
  overflow-y: auto;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 0.5rem;
  background: rgba(15, 23, 42, 0.9);
  box-shadow: 0 20px 56px rgba(0, 0, 0, 0.38);
  backdrop-filter: blur(12px);
}

/* Node Detail caps its height and scrolls internally so a node with many
   relations cannot push Impact Analysis far down the column. */
.graph-canvas__detail :deep(.node-detail-panel) {
  flex: 0 0 auto;
  max-height: 55vh;
  overflow-y: auto;
}

/* Impact Analysis sizes to its content so its result list is always visible. */
.graph-canvas__detail :deep(.impact-panel) {
  flex: 0 0 auto;
}

/* Data Flow detail fills the right column and scrolls internally. */
.graph-canvas__detail :deep(.dfd) {
  flex: 1 1 auto;
  min-height: 0;
}

@media (max-width: 64rem) {
  .graph-canvas-wrapper,
  .graph-canvas-wrapper--detail-open {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .graph-canvas__sidebar {
    flex-direction: row;
    flex-wrap: wrap;
    border-right: none;
    border-bottom: 1px solid rgba(148, 163, 184, 0.16);
    max-height: 14rem;
  }

  .graph-canvas__resizer {
    display: none;
  }

  .graph-canvas-wrapper--detail-open {
    grid-template-rows: auto 1fr;
  }

  .graph-canvas-wrapper--collapsed {
    grid-template-rows: minmax(0, 1fr);
  }

  .graph-canvas-wrapper--collapsed.graph-canvas-wrapper--detail-open {
    grid-template-rows: minmax(0, 1fr);
  }

  .graph-canvas__detail {
    top: auto;
    left: 0.75rem;
    right: 0.75rem;
    bottom: 0.75rem;
    width: auto;
    max-height: 50vh;
  }
}

.graph-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(15, 23, 42, 0.85);
  color: #e5e5e5;
  z-index: 10;
}

.graph-overlay--error .error-title {
  font-weight: 600;
  color: #ef4444;
}

.graph-overlay--error .error-message {
  font-size: 0.875rem;
  color: #a1a1a1;
  max-width: 400px;
  text-align: center;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #2a2a2a;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.retry-button {
  padding: 8px 16px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.retry-button:hover {
  background: #2563eb;
}

.graph-controls-help {
  position: absolute;
  left: 1rem;
  bottom: 1rem;
  z-index: 6;
  width: 12.5rem;
  padding: 0.75rem;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 6px;
  background: rgba(2, 6, 23, 0.72);
  color: #cbd5e1;
  backdrop-filter: blur(10px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.28);
  pointer-events: none;
}

.graph-controls-help__title {
  margin-bottom: 0.625rem;
  font-size: 0.6875rem;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.graph-controls-help__row {
  display: grid;
  grid-template-columns: 1.5rem 1fr;
  align-items: center;
  gap: 0.625rem;
}

.graph-controls-help__row + .graph-controls-help__row {
  margin-top: 0.625rem;
}

.graph-controls-help__row strong,
.graph-controls-help__row small {
  display: block;
  min-width: 0;
}

.graph-controls-help__row strong {
  font-size: 0.8125rem;
  line-height: 1.1;
  color: #e5e7eb;
}

.graph-controls-help__row small {
  margin-top: 0.125rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.6875rem;
  line-height: 1.1;
  color: #64748b;
}

.graph-controls-help__icon {
  display: inline-flex;
  width: 1.5rem;
  height: 1.5rem;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.78);
  color: #c4b5fd;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
  font-weight: 700;
}

.graph-controls-help__icon--primary {
  color: #34d399;
}

.graph-top-controls {
  position: absolute;
  top: 1rem;
  left: 1rem;
  right: 1rem;
  z-index: 6;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 0.625rem;
}

.graph-top-controls :deep(.search-bar) {
  flex: 1 1 14rem;
  width: auto;
  max-width: 45rem;
  min-width: 0;
}

.graph-edge-label-toggle {
  flex: 0 0 auto;
  min-width: 8rem;
  min-height: 2.25rem;
  text-align: center;
  padding: 0.4rem 0.75rem;
  border: 1px solid rgba(96, 165, 250, 0.45);
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.28);
  color: #bfdbfe;
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  backdrop-filter: blur(8px);
  transition:
    background 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
}

.graph-edge-label-toggle:hover,
.graph-edge-label-toggle:focus-visible {
  background: rgba(37, 99, 235, 0.42);
  border-color: rgba(96, 165, 250, 0.7);
}

.graph-edge-label-toggle--off {
  border-color: rgba(148, 163, 184, 0.3);
  background: rgba(15, 23, 42, 0.78);
  color: #94a3b8;
}

.graph-edge-kind-toggle {
  min-width: 8rem;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
