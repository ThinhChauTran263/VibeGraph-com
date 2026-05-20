<script setup lang="ts">
/**
 * GraphCanvas - Sigma.js container.
 * Renders force-directed graph with WebGL.
 *
 * Features:
 * - ForceAtlas2 layout (in Web Worker)
 * - Click node → focus mode
 * - Zoom/pan/drag
 * - Edge labels on zoom
 * - Curved or straight edges (toggle in settings)
 */
import { onMounted, onUnmounted, ref } from 'vue'

const canvasRef = ref<HTMLDivElement | null>(null)

onMounted(() => {
  // TODO: Initialize Sigma.js
})

onUnmounted(() => {
  // TODO: Cleanup Sigma instance
})
</script>

<template>
  <div ref="canvasRef" class="graph-canvas">
    <!-- Sigma.js renders here -->
  </div>
</template>

<style scoped>
.graph-canvas {
  width: 100%;
  height: 100%;
  position: relative;
}
</style>
