<script setup lang="ts">
/**
 * FlowsPanel - Data Flow Analysis list.
 *
 * Lists every traceable flow for the active project (HTTP endpoints plus
 * call-graph entry methods), each precomputed with its domain, step count and
 * file count. Selecting a flow emits it so the canvas highlights the chain and
 * the right-hand DataFlowDetailPanel opens.
 */
import { computed, ref, watch } from 'vue'
import { listFlows, type FlowListItem } from '@/lib/dataFlow'
import type { GraphData } from '@/types/graph'

const props = defineProps<{
  graphData: GraphData
  selectedFlowId?: string | null
}>()

const emit = defineEmits<{
  select: [item: FlowListItem]
}>()

const query = ref('')

// listFlows traces every flow; recompute only when the graph changes.
const flows = ref<FlowListItem[]>([])
watch(
  () => props.graphData,
  (graph) => {
    flows.value = graph.nodes.length > 0 ? listFlows(graph) : []
  },
  { immediate: true },
)

const hasFlows = computed(() => flows.value.length > 0)

const filtered = computed(() => {
  const term = query.value.trim().toLowerCase()
  if (!term) return flows.value
  return flows.value.filter(
    (item) =>
      item.title.toLowerCase().includes(term) ||
      item.domain.toLowerCase().includes(term) ||
      item.path.toLowerCase().includes(term),
  )
})

function methodClass(method: string): string {
  return `flows-panel__method flows-panel__method--${(method || 'other').toLowerCase()}`
}
</script>

<template>
  <section class="flows-panel" aria-labelledby="flows-panel-heading">
    <header class="flows-panel__header">
      <h2 id="flows-panel-heading">
        <span class="flows-panel__icon" aria-hidden="true">⑃</span>
        {{ flows.length }} Flows
      </h2>
    </header>

    <div v-if="hasFlows" class="flows-panel__search" role="search">
      <label class="flows-panel__sr-only" for="flows-search">Search flows</label>
      <input
        id="flows-search"
        v-model="query"
        class="flows-panel__search-input"
        type="search"
        placeholder="Search flows..."
        autocomplete="off"
        spellcheck="false"
      />
    </div>

    <ul v-if="filtered.length > 0" class="flows-panel__list" aria-label="Data flows">
      <li v-for="item in filtered" :key="item.id">
        <button
          class="flows-panel__item"
          :class="{ 'flows-panel__item--selected': item.id === selectedFlowId }"
          type="button"
          :aria-label="`Trace ${item.title}`"
          @click="emit('select', item)"
        >
          <span class="flows-panel__item-title">
            <span v-if="item.method" :class="methodClass(item.method)">{{ item.method }}</span>
            <span class="flows-panel__title-text">{{ item.title }}</span>
          </span>
          <span v-if="item.path" class="flows-panel__item-path">{{ item.path }}</span>
          <span class="flows-panel__item-meta">
            <span class="flows-panel__domain">{{ item.domain }}</span>
            <span class="flows-panel__steps">{{ item.stepCount }} steps</span>
          </span>
        </button>
      </li>
    </ul>

    <p v-else-if="!hasFlows" class="flows-panel__empty">No traceable data flows in this project.</p>
    <p v-else class="flows-panel__empty">No flows match "{{ query }}".</p>
  </section>
</template>

<style scoped>
.flows-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 0.75rem;
  border: 1px solid rgba(96, 165, 250, 0.25);
  border-radius: 1rem;
  background: rgba(17, 24, 39, 0.94);
  color: #e5e7eb;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

.flows-panel__header h2 {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  margin: 0;
  font-size: 0.8125rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #93c5fd;
}

.flows-panel__icon {
  color: #60a5fa;
}

.flows-panel__sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.flows-panel__search {
  margin-top: 0.625rem;
}

.flows-panel__search-input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1px solid rgba(96, 165, 250, 0.3);
  border-radius: 0.625rem;
  background: rgba(15, 23, 42, 0.9);
  color: inherit;
  font: inherit;
}

.flows-panel__search-input:focus-visible {
  outline: none;
  border-color: rgba(96, 165, 250, 0.7);
}

.flows-panel__list {
  margin: 0.625rem 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.flows-panel__item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  width: 100%;
  padding: 0.5rem;
  border: 1px solid transparent;
  border-left: 3px solid rgba(96, 165, 250, 0.5);
  border-radius: 0.5rem;
  background: rgba(31, 41, 55, 0.6);
  color: inherit;
  cursor: pointer;
  text-align: left;
  font: inherit;
}

.flows-panel__item:hover,
.flows-panel__item:focus-visible {
  border-color: rgba(96, 165, 250, 0.45);
  border-left-color: rgba(96, 165, 250, 0.9);
  background: rgba(37, 99, 235, 0.2);
  outline: none;
}

.flows-panel__item--selected {
  border-color: rgba(96, 165, 250, 0.82);
  background: rgba(37, 99, 235, 0.32);
}

.flows-panel__item-title {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  min-width: 0;
}

.flows-panel__method {
  flex: 0 0 auto;
  min-width: 2.75rem;
  padding: 0.0625rem 0.3rem;
  border-radius: 0.3rem;
  font-size: 0.625rem;
  font-weight: 700;
  text-align: center;
  background: rgba(148, 163, 184, 0.22);
  color: #cbd5e1;
}

.flows-panel__method--get {
  background: rgba(16, 185, 129, 0.22);
  color: #6ee7b7;
}
.flows-panel__method--post {
  background: rgba(59, 130, 246, 0.22);
  color: #93c5fd;
}
.flows-panel__method--put {
  background: rgba(245, 158, 11, 0.22);
  color: #fcd34d;
}
.flows-panel__method--patch {
  background: rgba(168, 85, 247, 0.22);
  color: #d8b4fe;
}
.flows-panel__method--delete {
  background: rgba(239, 68, 68, 0.22);
  color: #fca5a5;
}

.flows-panel__title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  font-size: 0.8125rem;
}

.flows-panel__item-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #94a3b8;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.6875rem;
}

.flows-panel__item-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.flows-panel__domain {
  padding: 0.0625rem 0.4rem;
  border-radius: 0.25rem;
  background: rgba(96, 165, 250, 0.16);
  color: #bfdbfe;
  font-size: 0.625rem;
  font-weight: 600;
}

.flows-panel__steps {
  color: #64748b;
  font-size: 0.625rem;
}

.flows-panel__empty {
  margin: 0.75rem 0 0;
  color: #9ca3af;
  font-size: 0.8125rem;
}
</style>
