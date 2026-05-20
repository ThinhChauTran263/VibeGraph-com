<script setup lang="ts">
/**
 * GraphView - Main graph view (3-column layout).
 * Left: SidePanel (Filters | Explorer | Flows)
 * Center: GraphCanvas (Sigma.js)
 * Right: NodeDetailPanel (when node selected)
 */
</script>

<template>
  <main class="graph-view">
    <!-- TODO: HeaderBar, MainLayout, StatusBar -->
  </main>
</template>

<style scoped>
.graph-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
</style>
