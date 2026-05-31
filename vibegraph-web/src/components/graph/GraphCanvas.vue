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

const props = defineProps<{
  projectId: string
}>()

const emit = defineEmits<{
  (e: 'nodeSelected', nodeId: string): void
}>()

const canvasRef = ref<HTMLDivElement | null>(null)

const { loading, error, loadGraph, selectNode, nodes } = useGraphData()

const { init: initSigma } = useSigma({
  container: canvasRef,
  onNodeClick: (nodeId: string) => {
    const node = nodes.value.find((n) => n.id === nodeId) ?? null
    selectNode(node)
    emit('nodeSelected', nodeId)
  },
})

async function load(projectId: string) {
  const graph = await loadGraph(projectId)
  if (graph && canvasRef.value) {
    initSigma(graph)
  }
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
</script>

<template>
  <div class="graph-canvas-wrapper">
    <div ref="canvasRef" class="graph-canvas" />

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
