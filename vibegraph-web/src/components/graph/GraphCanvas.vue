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
import NodeDetailPanel from '@/components/panels/NodeDetailPanel.vue'
import ImpactAnalysisPanel from '@/components/panels/ImpactAnalysisPanel.vue'
import { createFocusReducers, createSelectionFocusReducers } from '@/lib/focusMode'
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

const { init: initSigma, graphInstance, setReducers, setEdgeLabelsVisible } = useSigma({
  container: canvasRef,
  onNodeClick: (nodeId: string) => {
    const node = nodes.value.find((n) => n.id === nodeId) ?? null
    selectNode(node)
    emit('nodeSelected', nodeId)
  },
  onStageClick: () => {
    if (!selectedNode.value) return
    clearSelection()
    emit('nodeSelected', null)
  },
})

/**
 * Apply visual reducers. A clicked/searched selection always focuses its
 * directly-connected neighborhood (dimming the rest). When no node is selected
 * we fall back to the focus-depth filter control.
 */
function applyFocusReducers(): void {
  if (!graphInstance.value) return

  if (selectedNode.value) {
    setReducers(createSelectionFocusReducers(selectedNode.value.id, graphInstance.value))
    setEdgeLabelsVisible?.(true)
    return
  }

  setReducers(createFocusReducers(null, focusDepth.value, graphInstance.value))
  setEdgeLabelsVisible?.(false)
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
  clearSelection()
  emit('nodeSelected', null)
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

watch(
  [selectedNode, focusDepth],
  () => {
    applyFocusReducers()
  },
)

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
  <div class="graph-canvas-wrapper" :class="{ 'graph-canvas-wrapper--detail-open': !loading && !error && selectedNode }">
    <aside v-if="!loading && !error" class="graph-canvas__sidebar">
      <FocusDepthControl />
      <FilterPanel :graph-data="graphData" />
    </aside>

    <div class="graph-canvas__stage">
      <div ref="canvasRef" class="graph-canvas" />

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
      <NodeDetailPanel @close="onDetailClose" />
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

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
