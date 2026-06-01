<script setup lang="ts">
import { useRoute } from 'vue-router'
import GraphCanvas from '@/components/graph/GraphCanvas.vue'

const route = useRoute()
const projectId = (route.params.projectId as string) || 'default'
</script>

<template>
  <main class="graph-view">
    <GraphCanvas :project-id="projectId" @node-selected="(node) => console.log('selected', node)" />
  </main>
</template>

<style scoped>
.graph-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
</style>
