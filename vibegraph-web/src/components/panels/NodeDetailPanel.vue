<script setup lang="ts">
/**
 * NodeDetailPanel - Right side panel showing selected node detail.
 *
 * Layout:
 *   NODE DETAIL                    [X close]
 *
 *   ProductRepository.java
 *   [File badge]
 *   path/to/file
 *
 *   ← INCOMING (3)
 *     ● OrderServiceImpl.java
 *       ← IMPORTS
 *     ...
 *
 *   → OUTGOING (5)
 *     ● Product.java
 *       → IMPORTS
 *     ...
 */
</script>

<template>
  <aside class="node-detail-panel">
    <!-- TODO: Header, file path, INCOMING list, OUTGOING list -->
  </aside>
</template>

<style scoped>
.node-detail-panel {
  border-left: 1px solid #2a2a2a;
  padding: 1rem;
  overflow-y: auto;
}
</style>
