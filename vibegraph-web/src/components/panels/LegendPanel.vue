<script setup lang="ts">
/**
 * LegendPanel - Floating legend showing node type colors.
 * Position: bottom-left of graph canvas.
 */
import { NODE_COLORS } from '@/lib/constants'
</script>

<template>
  <div class="legend-panel">
    <!-- TODO: Render NODE_COLORS as legend items -->
  </div>
</template>

<style scoped>
.legend-panel {
  position: absolute;
  bottom: 1rem;
  left: 1rem;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 8px;
  padding: 0.75rem;
}
</style>
