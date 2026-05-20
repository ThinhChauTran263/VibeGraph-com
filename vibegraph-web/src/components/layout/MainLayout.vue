<script setup lang="ts">
/**
 * MainLayout - 3-column layout container.
 * Left: SidePanel (resizable)
 * Center: GraphCanvas + DiagramPanel (tabs)
 * Right: NodeDetailPanel (collapsible)
 */
</script>

<template>
  <div class="main-layout">
    <!-- TODO: SidePanel | GraphCanvas | NodeDetailPanel -->
  </div>
</template>

<style scoped>
.main-layout {
  flex: 1;
  display: grid;
  grid-template-columns: 280px 1fr 320px;
  overflow: hidden;
}
</style>
