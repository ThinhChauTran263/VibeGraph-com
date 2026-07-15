<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import DiagramPanel from '@/components/diagram/DiagramPanel.vue'
import GraphCanvas from '@/components/graph/GraphCanvas.vue'
import BrandMark from '@/components/ui/BrandMark.vue'

const route = useRoute()
const projectId = computed(() => (route.params.projectId as string) || 'default')
const activeView = ref<'graph' | 'diagrams'>('graph')
</script>

<template>
  <main class="graph-view">
    <nav class="graph-view__tabs" aria-label="Project visualization">
      <RouterLink
        class="graph-view__home"
        :to="{ name: 'dashboard' }"
        aria-label="Back to dashboard"
      >
        <BrandMark :size="24" :show-wordmark="false" />
        <span class="graph-view__home-label">Dashboard</span>
      </RouterLink>
      <span class="graph-view__divider" aria-hidden="true"></span>
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
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.graph-view__home {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.4rem 0.7rem;
  border-radius: 999px;
  color: #cbd5e1;
  transition:
    background-color 150ms ease,
    color 150ms ease;
}

.graph-view__home:hover {
  background: rgba(148, 163, 184, 0.1);
  color: #fff;
}

.graph-view__home-label {
  font-size: 0.8125rem;
  font-weight: 600;
}

.graph-view__divider {
  width: 1px;
  height: 20px;
  background: rgba(148, 163, 184, 0.24);
  margin: 0 0.25rem;
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
