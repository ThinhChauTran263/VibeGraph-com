<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DiagramPanel from '@/components/diagram/DiagramPanel.vue'
import GraphCanvas from '@/components/graph/GraphCanvas.vue'
import BrandMark from '@/components/ui/BrandMark.vue'

const route = useRoute()
const projectId = computed(() => {
  const value = route.params.projectId
  return typeof value === 'string' && value.trim() ? value : null
})
const activeView = ref<'graph' | 'diagrams'>('graph')

// Sidebar state lives in GraphCanvas (its resizer owns it) but the tab bar above
// needs the same column edge, so it is shared through v-model and re-published
// as the --sidebar-width custom property on this root. Persisted so the aligned
// tab bar survives a reload at the width the user actually works at.
const SIDEBAR_WIDTH_KEY = 'vibegraph.sidebarWidth'
function initialSidebarWidth(): number {
  const stored = Number(localStorage.getItem(SIDEBAR_WIDTH_KEY))
  return Number.isFinite(stored) && stored >= 220 && stored <= 640 ? stored : 288
}
const sidebarWidth = ref(initialSidebarWidth())
const sidebarCollapsed = ref(false)
watch(sidebarWidth, (width) => {
  localStorage.setItem(SIDEBAR_WIDTH_KEY, String(width))
})
</script>

<template>
  <main
    class="graph-view"
    :style="{ '--sidebar-width': sidebarCollapsed ? '100%' : sidebarWidth + 'px' }"
  >
    <nav class="graph-view__tabs" aria-label="Project visualization">
      <span class="graph-view__tabs-left">
        <span class="graph-view__home">
          <BrandMark
            :size="24"
            :show-wordmark="false"
            glyph-to="/"
            glyph-aria-label="VibeGraph landing page"
          />
          <RouterLink :to="{ name: 'dashboard' }" aria-label="Back to dashboard">
            <span class="graph-view__home-label">Dashboard</span>
          </RouterLink>
        </span>
        <span class="graph-view__divider" aria-hidden="true"></span>
      </span>
      <span class="graph-view__tabs-right">
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
      </span>
    </nav>

    <KeepAlive v-if="projectId">
      <GraphCanvas
        v-if="activeView === 'graph'"
        v-model:sidebar-width="sidebarWidth"
        v-model:sidebar-collapsed="sidebarCollapsed"
        :project-id="projectId"
      />
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
  justify-content: space-between;
  gap: 0.375rem;
  padding: 0.75rem 1rem;
  /* The bar's content box ends exactly at the sidebar column's right edge, so
     Graph/Diagrams sit flush with the sidebar's collapse arrow on one row
     instead of poking past the divider below. Collapsed sidebar publishes
     100%, which reduces this back to a plain 1rem gutter. */
  padding-right: calc(100% - var(--sidebar-width, 18rem) + 1rem);
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.graph-view__tabs-left,
.graph-view__tabs-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.graph-view__home {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.55rem;
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

.graph-view__home a {
  color: inherit;
  text-decoration: none;
}

.graph-view__home-label {
  font-size: 0.8125rem;
  font-weight: 600;
}

.graph-view__divider {
  width: 1px;
  height: 20px;
  background: rgba(148, 163, 184, 0.24);
  margin: 0 0.125rem;
}

.graph-view__tab {
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.92);
  color: #cbd5e1;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.8125rem;
  padding: 0.45rem 0.75rem;
}

.graph-view__tab--active {
  border-color: rgba(96, 165, 250, 0.82);
  background: rgba(37, 99, 235, 0.32);
  color: #bfdbfe;
}

/* Below the wrapper's own breakpoint the sidebar becomes a full-width row, so
   the tab bar must not keep tracking its (now meaningless) column edge. */
@media (max-width: 64rem) {
  .graph-view__tabs {
    padding-right: 1rem;
  }
}
</style>
