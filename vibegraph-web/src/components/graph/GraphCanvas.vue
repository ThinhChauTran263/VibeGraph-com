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
import type { GraphIncrementalEvent, GraphNode, GraphUpdateEvent } from '@/types/graph'

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
const activeFlow = ref<{ nodeIds: Set<string>; edgeIds: Set<string>; primaryNodeId: string } | null>(null)
// The selected flow shown in the right-hand DataFlowDetailPanel.
const activeFlowDetail = ref<FlowListItem | null>(null)

// User-resizable left sidebar width (px). Bound to a CSS custom property so the
// responsive media query can still collapse the layout on narrow screens.
const wrapperRef = ref<HTMLElement | null>(null)
const sidebarWidth = ref(288)
let resizing = false

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

// User toggle for showing edge type labels at all. When off, edge labels never
// render regardless of zoom/selection. Driven by the "Edge labels" button.
const edgeLabelsEnabled = ref(true)

function toggleEdgeLabels(): void {
  edgeLabelsEnabled.value = !edgeLabelsEnabled.value
  applyFocusReducers()
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
  buildGraph,
  selectNode,
  clearSelection,
  selectedNode,
  renderInfo,
  nodes,
} = useGraphData()

const { expandNode, reset: resetExpand } = useGraphExpand()

const {
  init: initSigma,
  graphInstance,
  setReducers,
  setEdgeLabelsVisible,
  setGhostPartition,
  refresh: refreshSigma,
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
    if (selectedNode.value || pinnedRelation.value || hoveredRelation.value || activeFlow.value) return
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
}

/** Focus the graph on a node (and optional single relation), revealing edge labels. */
function focusOn(nodeId: string, relation: HoveredRelation | null): void {
  if (!graphInstance.value) return
  setReducers(
    createSelectionFocusReducers(nodeId, graphInstance.value, relation, labelDensity.value),
  )
  setGhostPartition?.(partitionFocusGraph(nodeId, graphInstance.value, relation))
  setEdgeLabelsVisible?.(edgeLabelsEnabled.value && labelDensity.value === 'edges')
}

/** Spotlight the whole active Data Flow chain on the graph. */
function applyFlowFocus(): void {
  if (!graphInstance.value || !activeFlow.value) return
  const { nodeIds, edgeIds, primaryNodeId } = activeFlow.value
  setReducers(createFlowFocusReducers(nodeIds, edgeIds, graphInstance.value, primaryNodeId))
  setGhostPartition?.(partitionFlowGraph(nodeIds, edgeIds, graphInstance.value))
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
    initSigma(graph)
    applyFocusReducers()
  }
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
      fullName: node.fullName,
      filePath: node.filePath,
      lineNumber: node.lineNumber,
    })
  }

  // Added edges: respect hidden types, present endpoints, and the one-line-per-pair rule.
  for (const edge of addedEdges) {
    if (hiddenEdges.has(edge.type)) continue
    if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target)) continue
    if (graph.hasEdge(edge.id)) continue
    if (graph.hasEdge(edge.source, edge.target) || graph.hasEdge(edge.target, edge.source)) continue
    graph.addEdgeWithKey(edge.id, edge.source, edge.target, getEdgeAttributes(edge))
  }

  refreshSigma()
  applyFocusReducers()
}

/** Place a newly-added node next to an already-present neighbour (small jitter), else near origin. */
function spawnPosition(nodeId: string, edges: { source: string; target: string }[]): { x: number; y: number } {
  const graph = graphInstance.value
  if (graph) {
    for (const edge of edges) {
      const other = edge.source === nodeId ? edge.target : edge.target === nodeId ? edge.source : null
      if (other && graph.hasNode(other)) {
        const ox = graph.getNodeAttribute(other, 'x') as number
        const oy = graph.getNodeAttribute(other, 'y') as number
        return { x: ox + (Math.random() * 40 - 20), y: oy + (Math.random() * 40 - 20) }
      }
    }
  }
  return { x: Math.random() * 200 - 100, y: Math.random() * 200 - 100 }
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

watch(
  filteredGraphData,
  (graphData) => {
    // Selection consistency is cheap and must stay synchronous so a stale selected
    // node is cleared immediately even before the debounced rebuild runs.
    if (selectedNode.value && !graphData.nodes.some((node) => node.id === selectedNode.value?.id)) {
      clearSelection()
    }

    if (!canvasRef.value || loading.value || error.value) return
    rebuildGraph(graphData)
  },
  { deep: true },
)

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
    :class="{ 'graph-canvas-wrapper--detail-open': !loading && !error && (selectedNode || activeFlowDetail) }"
    :style="{ '--sidebar-width': sidebarWidth + 'px' }"
  >
    <aside v-if="!loading && !error" class="graph-canvas__sidebar">
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

      <ExplorerPanel
        v-show="activeSidebarTab === 'explorer'"
        :nodes="graphData.nodes"
        :selected-node-id="selectedNode?.id ?? null"
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
      v-if="!loading && !error"
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

      <div
        v-if="!loading && !error && renderInfo?.truncated"
        class="graph-safe-mode-notice"
        role="status"
      >
        <span class="graph-safe-mode-notice__dot" aria-hidden="true" />
        <span>
          Safe Mode — showing
          <strong>{{ renderInfo.renderedNodes.toLocaleString() }}</strong> /
          <strong>{{ renderInfo.totalNodes.toLocaleString() }}</strong> nodes and
          <strong>{{ renderInfo.renderedEdges.toLocaleString() }}</strong> /
          <strong>{{ renderInfo.totalEdges.toLocaleString() }}</strong> relationships. Use search
          or filters to narrow the view.
        </span>
      </div>

      <button
        v-if="!loading && !error"
        type="button"
        class="graph-edge-label-toggle"
        :class="{ 'graph-edge-label-toggle--off': !edgeLabelsEnabled }"
        :aria-pressed="edgeLabelsEnabled"
        @click="toggleEdgeLabels"
      >
        {{ edgeLabelsEnabled ? 'Edge labels: On' : 'Edge labels: Off' }}
      </button>

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

      <SearchBar
        v-if="!loading && !error"
        :nodes="nodes"
        :selected-node-id="selectedNode?.id ?? null"
        @select="onSearchSelect"
        @clear="onSearchClear"
      />

      <div v-if="loading" class="graph-overlay graph-overlay--loading">
        <div class="spinner" aria-label="Loading graph" />
        <p>Loading graph...</p>
      </div>

      <div v-else-if="error" class="graph-overlay graph-overlay--error" role="alert">
        <p class="error-title">Failed to load graph</p>
        <p class="error-message">{{ error }}</p>
        <button class="retry-button" type="button" @click="load(props.projectId)">Retry</button>
      </div>
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
        @close="onDetailClose"
        @relation-hover="onRelationHover"
        @relation-select="onRelationSelect"
      />
      <ImpactAnalysisPanel :project-id="props.projectId" :node="selectedNode" />
    </aside>
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
  grid-template-columns: var(--sidebar-width, 18rem) 1fr 23rem;
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
  transition: background 150ms ease, width 150ms ease;
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

.graph-canvas__sidebar-tabs {
  display: flex;
  gap: 0.375rem;
  flex: 0 0 auto;
}

.graph-canvas__sidebar-tab {
  flex: 1;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  padding: 0.4rem 0.75rem;
  background: rgba(15, 23, 42, 0.92);
  color: #cbd5e1;
  font-size: 0.8125rem;
  font-weight: 600;
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
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  /* The column is height-constrained to the graph viewport; each panel manages
     its OWN scroll so a long Node Detail can never crush Impact Analysis. */
  min-height: 0;
  overflow: hidden;
  border-left: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.85);
}

/* Node Detail takes the flexible space and scrolls internally when it has many
   relations. */
.graph-canvas__detail :deep(.node-detail-panel) {
  flex: 1 1 auto;
  min-height: 8rem;
  overflow-y: auto;
}

/* Impact Analysis keeps a stable, readable height and never collapses. Its
   header/controls stay pinned; only its results body scrolls (see panel CSS). */
.graph-canvas__detail :deep(.impact-panel) {
  flex: 0 0 auto;
  min-height: 15rem;
  max-height: 38vh;
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
    grid-template-rows: auto 1fr auto;
  }

  .graph-canvas__detail {
    border-left: none;
    border-top: 1px solid rgba(148, 163, 184, 0.16);
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

.graph-edge-label-toggle {
  position: absolute;
  top: 1rem;
  left: 1rem;
  z-index: 6;
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

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

</style>

<style scoped>
.graph-safe-mode-notice {
  position: absolute;
  top: 1rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 7;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  max-width: min(90%, 40rem);
  padding: 0.5rem 0.9rem;
  border: 1px solid rgba(251, 191, 36, 0.45);
  border-radius: 999px;
  background: rgba(120, 53, 15, 0.82);
  color: #fde68a;
  font-size: 0.8125rem;
  line-height: 1.3;
  backdrop-filter: blur(8px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.32);
}

.graph-safe-mode-notice strong {
  color: #fef3c7;
  font-weight: 700;
}

.graph-safe-mode-notice__dot {
  flex: 0 0 auto;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: #fbbf24;
  box-shadow: 0 0 0 3px rgba(251, 191, 36, 0.25);
}
</style>
