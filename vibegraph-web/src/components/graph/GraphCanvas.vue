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
import { onMounted, ref, watch } from 'vue'
import { useGraphData } from '@/composables/useGraphData'
import { useSigma } from '@/composables/useSigma'
import SearchBar from '@/components/graph/SearchBar.vue'
import FilterPanel from '@/components/panels/FilterPanel.vue'
import FocusDepthControl from '@/components/panels/FocusDepthControl.vue'
import NodeDetailPanel, { type RelationHoverPayload } from '@/components/panels/NodeDetailPanel.vue'
import ImpactAnalysisPanel from '@/components/panels/ImpactAnalysisPanel.vue'
import {
  createFocusReducers,
  createSelectionFocusReducers,
  partitionFocusGraph,
  resolveFocusLabelDensity,
  type FocusLabelDensity,
  type HoveredRelation,
} from '@/lib/focusMode'
import { useFilters } from '@/composables/useFilters'
import { useGraphRealtime } from '@/composables/useGraphRealtime'
import type { GraphNode } from '@/types/graph'

const props = defineProps<{
  projectId: string
}>()

const emit = defineEmits<{
  (e: 'nodeSelected', nodeId: string | null): void
}>()

const canvasRef = ref<HTMLDivElement | null>(null)
const { focusDepth } = useFilters()

// Graph focus is resolved from three independent inputs with a deterministic
// priority (see applyFocusReducers):
//   1. hoveredRelation   — a relation item in Node Detail is being hovered (preview)
//   2. pinnedRelation    — a relation item in Node Detail was clicked (pinned/sticky)
//   3. hoveredGraphNode  — a node on the graph is being hovered (temporary)
//   4. selectedNode      — a node was clicked/searched (sticky)
//   5. none              — default focus-depth view
//
// hoveredRelation/pinnedRelation are relative to the SELECTED node (the relation's
// edge connects the selected node to a counterpart). A pinned relation survives the
// mouse leaving the item; only another relation click, a graph-node click, or a
// selection reset clears it.
const hoveredRelation = ref<HoveredRelation | null>(null)
const pinnedRelation = ref<HoveredRelation | null>(null)
const hoveredGraphNode = ref<string | null>(null)
const labelDensity = ref<FocusLabelDensity>('nodes')

function resetRelationFocus(): void {
  hoveredRelation.value = null
  pinnedRelation.value = null
}

// T60: subscribe to realtime graph updates for the active project and patch the
// store in place. Resubscribes on project change and cleans up on unmount.
useGraphRealtime(() => props.projectId)

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
  nodes,
} = useGraphData()

const {
  init: initSigma,
  graphInstance,
  setReducers,
  setEdgeLabelsVisible,
  setGhostPartition,
} = useSigma({
  container: canvasRef,
  onNodeClick: (nodeId: string) => {
    const node = nodes.value.find((n) => n.id === nodeId) ?? null
    // Clicking a graph node clears any pinned/previewed relation focus and takes
    // over selection (priority 4). The hovered-node state is cleared too so the
    // newly selected node is what stays focused after the click settles.
    resetRelationFocus()
    hoveredGraphNode.value = null
    selectNode(node)
    emit('nodeSelected', nodeId)
  },
  onStageClick: () => {
    if (!selectedNode.value) return
    resetRelationFocus()
    hoveredGraphNode.value = null
    clearSelection()
    emit('nodeSelected', null)
  },
  onNodeHover: (nodeId: string) => {
    if (selectedNode.value || pinnedRelation.value || hoveredRelation.value) return
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
 * which wins over the clicked/searched selection; with no focus we fall back to
 * the focus-depth filter control.
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

  // 4: clicked/searched selection focus.
  if (selectedNode.value) {
    focusOn(selectedNode.value.id, null)
    return
  }

  // 5: default focus-depth view.
  setReducers(createFocusReducers(null, focusDepth.value, graph))
  setGhostPartition?.(null)
  setEdgeLabelsVisible?.(false)
}

/** Focus the graph on a node (and optional single relation), revealing edge labels. */
function focusOn(nodeId: string, relation: HoveredRelation | null): void {
  if (!graphInstance.value) return
  setReducers(
    createSelectionFocusReducers(nodeId, graphInstance.value, relation, labelDensity.value),
  )
  setGhostPartition?.(partitionFocusGraph(nodeId, graphInstance.value, relation))
  setEdgeLabelsVisible?.(labelDensity.value === 'edges')
}

async function load(projectId: string) {
  const graph = await loadGraph(projectId)
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

watch(
  () => props.projectId,
  (newId) => {
    if (newId) load(newId)
  },
)

watch([selectedNode, focusDepth], () => {
  applyFocusReducers()
})

watch(
  filteredGraphData,
  (graphData) => {
    if (selectedNode.value && !graphData.nodes.some((node) => node.id === selectedNode.value?.id)) {
      clearSelection()
    }

    if (!canvasRef.value || loading.value || error.value) return
    initSigma(buildGraph(graphData))
    applyFocusReducers()
  },
  { deep: true },
)
</script>

<template>
  <div
    class="graph-canvas-wrapper"
    :class="{ 'graph-canvas-wrapper--detail-open': !loading && !error && selectedNode }"
  >
    <aside v-if="!loading && !error" class="graph-canvas__sidebar">
      <FocusDepthControl />
      <FilterPanel :graph-data="graphData" />
    </aside>

    <div class="graph-canvas__stage">
      <div ref="canvasRef" class="graph-canvas" />

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

    <aside v-if="!loading && !error && selectedNode" class="graph-canvas__detail">
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
  grid-template-columns: 18rem 1fr;
  width: 100%;
  height: 100%;
  background: #0f172a;
}

.graph-canvas-wrapper--detail-open {
  grid-template-columns: 18rem 1fr 23rem;
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
  overflow-y: auto;
  border-left: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.85);
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
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
