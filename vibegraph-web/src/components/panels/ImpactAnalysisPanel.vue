<script setup lang="ts">
/**
 * ImpactAnalysisPanel - shows the blast radius of the selected node.
 *
 * Reads nothing from the graph store directly: the caller passes the active
 * `projectId` and `node`, which keeps this panel unit-testable in isolation
 * from GraphCanvas/Sigma. All request state lives in `useImpactAnalysis`.
 */
import { computed, watch } from 'vue'
import { useImpactAnalysis } from '@/composables/useImpactAnalysis'
import type { GraphNode } from '@/types/graph'
import type { ImpactNode } from '@/lib/api'

const props = defineProps<{
  projectId: string
  node: GraphNode | null
}>()

const { status, result, errorMessage, selectedDepth, isLoading, allowedDepths, loadImpact, reset } =
  useImpactAnalysis()

// A new node selection invalidates any previous result.
watch(
  () => props.node?.id,
  () => {
    reset()
  },
)

const riskClass = computed(() => {
  const level = result.value?.riskLevel?.toUpperCase() ?? ''
  return `impact-panel__risk--${level.toLowerCase() || 'unknown'}`
})

const depthGroups = computed(() => {
  const data = result.value
  if (!data) return []
  return [
    { key: 'willBreak', label: 'Will break (d=1)', nodes: data.willBreak ?? [] },
    { key: 'likelyAffected', label: 'Likely affected (d=2)', nodes: data.likelyAffected ?? [] },
    { key: 'mayNeedTesting', label: 'May need testing (d≥3)', nodes: data.mayNeedTesting ?? [] },
  ].filter((group) => group.nodes.length > 0)
})

const hasAffectedNodes = computed(() => depthGroups.value.length > 0)

function nodeKey(node: ImpactNode): string {
  return node.id || node.fullName || node.name
}

function analyze(): void {
  if (!props.node) return
  void loadImpact(props.projectId, props.node.id, selectedDepth.value)
}
</script>

<template>
  <aside class="impact-panel" aria-labelledby="impact-panel-heading">
    <header class="impact-panel__header">
      <p class="impact-panel__eyebrow">Impact analysis</p>
      <h2 id="impact-panel-heading">Blast radius</h2>
    </header>

    <div v-if="!node" class="impact-panel__empty">
      <p>Select a node to analyze its blast radius.</p>
    </div>

    <template v-else>
      <p class="impact-panel__target" :title="node.fullName">
        <span class="impact-panel__target-name">{{ node.name }}</span>
        <span class="impact-panel__target-type">{{ node.type }}</span>
      </p>

      <form class="impact-panel__controls" @submit.prevent="analyze">
        <label class="impact-panel__depth-label" for="impact-depth">Depth</label>
        <select
          id="impact-depth"
          v-model.number="selectedDepth"
          class="impact-panel__depth-select"
          :disabled="isLoading"
        >
          <option v-for="depth in allowedDepths" :key="depth" :value="depth">{{ depth }}</option>
        </select>
        <button class="impact-panel__analyze" type="submit" :disabled="isLoading">
          {{ isLoading ? 'Analyzing…' : 'Analyze' }}
        </button>
      </form>

      <p v-if="isLoading" class="impact-panel__status" role="status">Loading impact…</p>

      <p v-else-if="status === 'error'" class="impact-panel__error" role="alert">
        {{ errorMessage }}
      </p>

      <template v-else-if="status === 'success' && result">
        <div class="impact-panel__summary">
          <span class="impact-panel__risk" :class="riskClass">{{ result.riskLevel }}</span>
          <dl class="impact-panel__counts">
            <div>
              <dt>Direct</dt>
              <dd>{{ result.directDependents }}</dd>
            </div>
            <div>
              <dt>Total</dt>
              <dd>{{ result.totalDependents }}</dd>
            </div>
          </dl>
        </div>

        <section
          v-for="group in depthGroups"
          :key="group.key"
          class="impact-panel__group"
          :aria-labelledby="`impact-group-${group.key}`"
        >
          <h3 :id="`impact-group-${group.key}`">{{ group.label }} ({{ group.nodes.length }})</h3>
          <ul class="impact-panel__nodes">
            <li v-for="affected in group.nodes" :key="nodeKey(affected)">
              <span class="impact-panel__node-name">{{ affected.name }}</span>
              <span class="impact-panel__node-path">{{ affected.filePath }}</span>
            </li>
          </ul>
        </section>

        <p v-if="!hasAffectedNodes" class="impact-panel__empty-list">
          No dependents found at this depth.
        </p>
      </template>

      <p v-else class="impact-panel__hint">
        Choose a depth and run analysis to see what this node affects.
      </p>
    </template>
  </aside>
</template>

<style scoped>
.impact-panel {
  width: min(24rem, calc(100% - 2rem));
  max-height: calc(100% - 2rem);
  overflow-y: auto;
  border: 1px solid rgba(248, 113, 113, 0.28);
  border-radius: 1rem;
  padding: 1rem;
  background: rgba(17, 24, 39, 0.94);
  color: #e5e7eb;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

.impact-panel__header h2,
.impact-panel__eyebrow {
  margin: 0;
}

.impact-panel__eyebrow {
  color: #fca5a5;
  font-size: 0.8125rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.impact-panel__header h2 {
  margin-top: 0.25rem;
  font-size: 1.125rem;
}

.impact-panel__target {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin: 0.75rem 0 0;
  overflow-wrap: anywhere;
}

.impact-panel__target-name {
  font-weight: 600;
}

.impact-panel__target-type {
  color: #9ca3af;
  font-size: 0.8125rem;
}

.impact-panel__controls {
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
}

.impact-panel__depth-label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  color: #9ca3af;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.impact-panel__depth-select {
  height: 2.25rem;
  border: 1px solid #374151;
  border-radius: 0.5rem;
  background: rgba(31, 41, 55, 0.9);
  color: #e5e7eb;
  padding: 0 0.5rem;
  cursor: pointer;
}

.impact-panel__analyze {
  height: 2.25rem;
  flex: 1;
  border: none;
  border-radius: 0.5rem;
  background: #dc2626;
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 200ms ease;
}

.impact-panel__analyze:hover:not(:disabled) {
  background: #b91c1c;
}

.impact-panel__analyze:disabled {
  opacity: 0.6;
  cursor: progress;
}

.impact-panel__status,
.impact-panel__hint,
.impact-panel__empty,
.impact-panel__empty-list {
  color: #9ca3af;
  font-size: 0.8125rem;
  margin-top: 1rem;
}

.impact-panel__error {
  margin-top: 1rem;
  padding: 0.625rem;
  border: 1px solid rgba(239, 68, 68, 0.5);
  border-radius: 0.5rem;
  background: rgba(127, 29, 29, 0.35);
  color: #fecaca;
  font-size: 0.8125rem;
}

.impact-panel__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 1rem;
}

.impact-panel__risk {
  border-radius: 999px;
  padding: 0.25rem 0.625rem;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.impact-panel__risk--low {
  background: rgba(34, 197, 94, 0.22);
  color: #86efac;
}

.impact-panel__risk--medium {
  background: rgba(234, 179, 8, 0.22);
  color: #fde68a;
}

.impact-panel__risk--high {
  background: rgba(249, 115, 22, 0.24);
  color: #fdba74;
}

.impact-panel__risk--critical {
  background: rgba(239, 68, 68, 0.26);
  color: #fca5a5;
}

.impact-panel__risk--unknown {
  background: rgba(148, 163, 184, 0.22);
  color: #cbd5e1;
}

.impact-panel__counts {
  display: flex;
  gap: 1rem;
  margin: 0;
}

.impact-panel__counts div {
  text-align: right;
}

.impact-panel__counts dt {
  color: #9ca3af;
  font-size: 0.6875rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.impact-panel__counts dd {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
}

.impact-panel__group {
  margin-top: 1.25rem;
}

.impact-panel__group h3 {
  margin: 0 0 0.5rem;
  color: #fca5a5;
  font-size: 0.8125rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.impact-panel__nodes {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0;
  margin: 0;
  list-style: none;
}

.impact-panel__nodes li {
  display: flex;
  flex-direction: column;
  gap: 0.1875rem;
  border: 1px solid rgba(55, 65, 81, 0.85);
  border-radius: 0.625rem;
  padding: 0.5rem 0.625rem;
  background: rgba(31, 41, 55, 0.72);
}

.impact-panel__node-name {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.impact-panel__node-path {
  color: #9ca3af;
  font-size: 0.75rem;
  overflow-wrap: anywhere;
}
</style>
