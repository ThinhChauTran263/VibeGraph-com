<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import DiagramPanel from '@/components/diagram/DiagramPanel.vue'
import GraphCanvas from '@/components/graph/GraphCanvas.vue'

const route = useRoute()
const projectId = computed(() => (route.params.projectId as string) || 'default')
const activeView = ref<'graph' | 'diagrams'>('graph')
</script>

<template>
  <main class="graph-view">
    <nav class="graph-view__tabs" aria-label="Project visualization">
      <button
        class="graph-view__tab"
        :class="{ 'graph-view__tab--active': activeView === 'graph' }"
        type="button"
        @click="activeView = 'graph'"
      >
        Graph
      </button>
      <button
        class="graph-view__tab"
        :class="{ 'graph-view__tab--active': activeView === 'diagrams' }"
        type="button"
        @click="activeView = 'diagrams'"
      >
        Diagrams
      </button>
    </nav>

    <KeepAlive>
      <GraphCanvas v-if="activeView === 'graph'" :project-id="projectId" />
      <DiagramPanel v-else :project-id="projectId" />
    </KeepAlive>
  </main>
</template>

<style scoped>
.graph-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #020617;
}

.graph-view__tabs {
  display: flex;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.graph-view__tab {
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.92);
  color: #cbd5e1;
  cursor: pointer;
  font-weight: 600;
  padding: 0.5rem 0.875rem;
}

.graph-view__tab--active {
  border-color: rgba(96, 165, 250, 0.82);
  background: rgba(37, 99, 235, 0.32);
  color: #bfdbfe;
}
</style>
