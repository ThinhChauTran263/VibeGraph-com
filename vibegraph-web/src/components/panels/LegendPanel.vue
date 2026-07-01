<script setup lang="ts">
/**
 * LegendPanel - node-type color legend.
 *
 * Renders one swatch per node type that is actually present in the graph (with
 * its count), so users can identify node colors from the Explorer without
 * opening the Filters tab. Colors come straight from NODE_COLORS, so the legend
 * always matches what the graph renders.
 */
import { computed } from 'vue'
import { NODE_COLORS } from '@/lib/constants'
import type { NodeType } from '@/types/graph'

const props = defineProps<{
  /** Per-type node counts (e.g. graph nodeStats, or counts derived from nodes). */
  nodeStats: Partial<Record<NodeType, number>>
  /** Optional heading; defaults to "Legend". */
  title?: string
}>()

const items = computed(() =>
  (Object.entries(props.nodeStats) as [NodeType, number][])
    .filter(([, count]) => count > 0)
    .map(([type, count]) => ({ type, count, color: NODE_COLORS[type] ?? '#94a3b8' }))
    .sort((left, right) => right.count - left.count || left.type.localeCompare(right.type)),
)
</script>

<template>
  <section class="legend" aria-label="Node type legend">
    <h3 v-if="(title ?? 'Legend') !== ''" class="legend__title">{{ title ?? 'Legend' }}</h3>
    <ul v-if="items.length > 0" class="legend__grid">
      <li v-for="item in items" :key="item.type" class="legend__item">
        <span class="legend__swatch" :style="{ backgroundColor: item.color }" aria-hidden="true" />
        <span class="legend__name">{{ item.type }}</span>
        <span class="legend__count">{{ item.count }}</span>
      </li>
    </ul>
    <p v-else class="legend__empty">No nodes to describe.</p>
  </section>
</template>

<style scoped>
.legend {
  display: flex;
  flex-direction: column;
}

.legend__title {
  margin: 0 0 0.5rem;
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #93c5fd;
}

.legend__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.25rem 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.legend__item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.legend__swatch {
  flex: 0 0 auto;
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 999px;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.4);
}

.legend__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.75rem;
  color: #e5e7eb;
}

.legend__count {
  color: #94a3b8;
  font-size: 0.6875rem;
  font-variant-numeric: tabular-nums;
}

.legend__empty {
  margin: 0;
  color: #9ca3af;
  font-size: 0.75rem;
}
</style>
