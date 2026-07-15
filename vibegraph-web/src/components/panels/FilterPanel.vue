<script setup lang="ts">
import { computed } from 'vue'
import { EDGE_COLORS, NODE_COLORS } from '@/lib/constants'
import { useFilters } from '@/composables/useFilters'
import type { EdgeType, GraphData, NodeType } from '@/types/graph'

const props = defineProps<{
  graphData: GraphData
}>()

const {
  hiddenNodeTypes,
  hiddenEdgeTypes,
  hasActiveFilters,
  toggleNodeType,
  toggleEdgeType,
  showAllNodeTypes,
  showAllEdgeTypes,
  reset,
} = useFilters()

const nodeTypeItems = computed(() => {
  return Object.entries(props.graphData.nodeStats)
    .filter(([, count]) => count > 0)
    .map(([type, count]) => ({ type: type as NodeType, count }))
    .sort((left, right) => right.count - left.count || left.type.localeCompare(right.type))
})

const edgeTypeItems = computed(() => {
  return Object.entries(props.graphData.edgeStats)
    .filter(([, count]) => count > 0)
    .map(([type, count]) => ({ type: type as EdgeType, count }))
    .sort((left, right) => right.count - left.count || left.type.localeCompare(right.type))
})

// The full set of currently-present types, passed to the isolate toggle so it
// knows which other types to close/restore.
const nodeTypeList = computed(() => nodeTypeItems.value.map((item) => item.type))
const edgeTypeList = computed(() => edgeTypeItems.value.map((item) => item.type))
</script>

<template>
  <aside class="filter-panel" aria-labelledby="filter-panel-heading">
    <header class="filter-panel__header">
      <div>
        <h2 id="filter-panel-heading">Graph filters</h2>
        <p>Toggle node and edge types in the current graph.</p>
      </div>
      <button
        class="filter-panel__reset"
        type="button"
        :disabled="!hasActiveFilters"
        @click="reset"
      >
        Reset all
      </button>
    </header>

    <section class="filter-panel__section" aria-labelledby="filter-node-types-heading">
      <div class="filter-panel__section-header">
        <h3 id="filter-node-types-heading">Node types</h3>
        <button type="button" @click="showAllNodeTypes">Show all</button>
      </div>

      <ul class="filter-panel__list">
        <li v-for="item in nodeTypeItems" :key="item.type">
          <button
            class="filter-panel__toggle"
            :class="{ 'filter-panel__toggle--muted': hiddenNodeTypes.has(item.type) }"
            type="button"
            :aria-label="`${item.type} nodes ${hiddenNodeTypes.has(item.type) ? 'hidden' : 'visible'}, ${item.count}`"
            :aria-pressed="!hiddenNodeTypes.has(item.type)"
            @click="toggleNodeType(item.type, nodeTypeList)"
          >
            <span
              class="filter-panel__swatch"
              :style="{ backgroundColor: NODE_COLORS[item.type] }"
            />
            <span class="filter-panel__name">{{ item.type }}</span>
            <span class="filter-panel__count">{{ item.count }}</span>
          </button>
        </li>
      </ul>

      <p v-if="nodeTypeItems.length === 0" class="filter-panel__empty">No node types available.</p>
    </section>

    <section class="filter-panel__section" aria-labelledby="filter-edge-types-heading">
      <div class="filter-panel__section-header">
        <h3 id="filter-edge-types-heading">Edge types</h3>
        <button type="button" @click="showAllEdgeTypes">Show all</button>
      </div>

      <ul class="filter-panel__list">
        <li v-for="item in edgeTypeItems" :key="item.type">
          <button
            class="filter-panel__toggle"
            :class="{ 'filter-panel__toggle--muted': hiddenEdgeTypes.has(item.type) }"
            type="button"
            :aria-label="`${item.type} edges ${hiddenEdgeTypes.has(item.type) ? 'hidden' : 'visible'}, ${item.count}`"
            :aria-pressed="!hiddenEdgeTypes.has(item.type)"
            @click="toggleEdgeType(item.type, edgeTypeList)"
          >
            <span
              class="filter-panel__edge-swatch"
              :style="{ backgroundColor: EDGE_COLORS[item.type] }"
            />
            <span class="filter-panel__name">{{ item.type }}</span>
            <span class="filter-panel__count">{{ item.count }}</span>
          </button>
        </li>
      </ul>

      <p v-if="edgeTypeItems.length === 0" class="filter-panel__empty">No edge types available.</p>
    </section>
  </aside>
</template>

<style scoped>
.filter-panel {
  padding: 1rem;
  border: 1px solid rgba(96, 165, 250, 0.25);
  border-radius: 1rem;
  background: rgba(17, 24, 39, 0.94);
  color: #e5e7eb;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

.filter-panel__header,
.filter-panel__section-header,
.filter-panel__toggle {
  display: flex;
  align-items: center;
}

.filter-panel__header {
  justify-content: space-between;
  gap: 1rem;
}

.filter-panel__header h2,
.filter-panel__section h3,
.filter-panel__header p,
.filter-panel__empty {
  margin: 0;
}

.filter-panel__header h2 {
  font-size: 1rem;
}

.filter-panel__header p,
.filter-panel__empty {
  margin-top: 0.25rem;
  color: #9ca3af;
  font-size: 0.8125rem;
}

.filter-panel__reset,
.filter-panel__section-header button {
  border: 1px solid #374151;
  border-radius: 999px;
  padding: 0.25rem 0.625rem;
  background: rgba(31, 41, 55, 0.85);
  color: #d1d5db;
  cursor: pointer;
}

.filter-panel__reset:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.filter-panel__section {
  margin-top: 1rem;
}

.filter-panel__section-header {
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.filter-panel__section h3 {
  font-size: 0.8125rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #93c5fd;
}

.filter-panel__list {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.filter-panel__toggle {
  width: 100%;
  gap: 0.5rem;
  border: 1px solid transparent;
  border-radius: 0.625rem;
  padding: 0.5rem;
  background: rgba(31, 41, 55, 0.72);
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.filter-panel__toggle:hover,
.filter-panel__toggle:focus-visible {
  border-color: rgba(96, 165, 250, 0.4);
  background: rgba(37, 99, 235, 0.18);
}

.filter-panel__toggle--muted {
  opacity: 0.45;
}

.filter-panel__swatch {
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 999px;
  flex: 0 0 auto;
}

.filter-panel__edge-swatch {
  width: 0.875rem;
  height: 0.1875rem;
  border-radius: 999px;
  flex: 0 0 auto;
}

.filter-panel__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-panel__count {
  color: #9ca3af;
  font-variant-numeric: tabular-nums;
}
</style>
