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
import { createFocusReducers } from '@/lib/focusMode'
import { useFilters } from '@/composables/useFilters'
import type { GraphNode } from '@/types/graph'

const props = defineProps<{
  projectId: string
}>()

const emit = defineEmits<{
  (e: 'nodeSelected', nodeId: string | null): void
}>()

const canvasRef = ref<HTMLDivElement | null>(null)
const { focusDepth } = useFilters()

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

const { init: initSigma, graphInstance, setReducers } = useSigma({
  container: canvasRef,
  onNodeClick: (nodeId: string) => {
    const node = nodes.value.find((n) => n.id === nodeId) ?? null
    selectNode(node)
    emit('nodeSelected', nodeId)
  },
})

function applyFocusReducers(): void {
  if (!graphInstance.value) return
  setReducers(createFocusReducers(selectedNode.value?.id ?? null, focusDepth.value, graphInstance.value))
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
  <div class="graph-canvas-wrapper">
    <div ref="canvasRef" class="graph-canvas" />

    <SearchBar
      v-if="!loading && !error"
      :nodes="nodes"
      :selected-node-id="selectedNode?.id ?? null"
      @select="onSearchSelect"
      @clear="onSearchClear"
    />

    <div v-if="!loading && !error" class="graph-canvas__controls">
      <FocusDepthControl />
      <FilterPanel :graph-data="graphData" />
    </div>

    <div v-if="!loading && !error && selectedNode" class="graph-canvas__detail">
      <NodeDetailPanel />
      <ImpactAnalysisPanel :project-id="props.projectId" :node="selectedNode" />
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
  </div>
</template>

<style scoped>
.graph-canvas-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}

.graph-canvas {
  width: 100%;
  height: 100%;
  position: relative;
  background: #0f0f0f;
}

.graph-canvas__controls {
  position: absolute;
  top: 1rem;
  right: 1rem;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: min(22rem, calc(100% - 2rem));
  max-height: calc(100% - 2rem);
  overflow: auto;
}

.graph-canvas__detail {
  position: absolute;
  right: 1rem;
  bottom: 1rem;
  z-index: 21;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  max-height: calc(100% - 2rem);
  overflow-y: auto;
}

@media (max-height: 48rem) {
  .graph-canvas__controls {
    max-height: 45%;
  }

  .graph-canvas__detail {
    max-height: 45%;
  }
}

@media (max-width: 48rem) {
  .graph-canvas__controls,
  .graph-canvas__detail {
    left: 1rem;
    width: auto;
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
  background: rgba(15, 15, 15, 0.85);
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
