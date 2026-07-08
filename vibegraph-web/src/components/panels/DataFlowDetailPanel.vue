<script setup lang="ts">
/**
 * DataFlowDetailPanel - right-hand detail for a selected Data Flow.
 *
 * Mirrors the reference layout: title (entry → terminal), method/route chip,
 * a plain-language description, the ordered steps with file paths, and a
 * Steps / Files summary. Selecting a step focuses that node on the graph.
 */
import { computed } from 'vue'
import { describeFlow, type FlowListItem } from '@/lib/dataFlow'
import { NODE_COLORS } from '@/lib/constants'

const props = defineProps<{
  item: FlowListItem
  selectedNodeId?: string | null
}>()

const emit = defineEmits<{
  focusStep: [nodeId: string]
  close: []
}>()

const description = computed(() => describeFlow(props.item))
const steps = computed(() => props.item.flow.steps)

function methodClass(method: string): string {
  return `dfd__method dfd__method--${(method || 'other').toLowerCase()}`
}

function shortPath(filePath?: string): string {
  if (!filePath) return ''
  const norm = filePath.replace(/\\/g, '/')
  const parts = norm.split('/')
  return parts.slice(-2).join('/')
}
</script>

<template>
  <section class="dfd" aria-labelledby="dfd-heading">
    <header class="dfd__header">
      <h2 id="dfd-heading" class="dfd__eyebrow">Data Flow</h2>
      <button class="dfd__close" type="button" aria-label="Close data flow detail" @click="emit('close')">
        ✕
      </button>
    </header>

    <p class="dfd__title">{{ item.title }}</p>

    <div class="dfd__tags">
      <span v-if="item.method" :class="methodClass(item.method)">{{ item.method }}</span>
      <span class="dfd__domain">{{ item.domain }}</span>
    </div>

    <p v-if="item.path" class="dfd__route">
      <span class="dfd__route-icon" aria-hidden="true">🔗</span>{{ item.path }}
    </p>

    <p class="dfd__desc">{{ description }}</p>

    <p
      v-if="!item.flow.complete"
      class="dfd__notice"
      role="status"
    >
      ⚠ Incomplete — trace stopped before reaching a database model.
    </p>

    <h3 class="dfd__section">Flow steps ({{ steps.length }})</h3>
    <ol class="dfd__steps">
      <li v-for="step in steps" :key="step.nodeId">
        <button
          class="dfd__step"
          :class="{ 'dfd__step--selected': step.nodeId === selectedNodeId }"
          type="button"
          :aria-label="`Step ${step.index}: ${step.name}${step.springLayer ? ', ' + step.springLayer : ''}`"
          @click="emit('focusStep', step.nodeId)"
        >
          <span class="dfd__step-index">{{ step.index }}</span>
          <span class="dfd__step-body">
            <span class="dfd__step-top">
              <span class="dfd__step-dot" :style="{ backgroundColor: NODE_COLORS[step.nodeType] ?? '#94a3b8' }" />
              <span class="dfd__step-name">{{ step.name }}</span>
              <span v-if="step.springLayer" class="dfd__layer">{{ step.springLayer }}</span>
            </span>
            <span v-if="shortPath(step.filePath)" class="dfd__step-file">{{ shortPath(step.filePath) }}</span>
          </span>
        </button>
      </li>
    </ol>

    <footer class="dfd__summary">
      <div class="dfd__stat">
        <span class="dfd__stat-value">{{ item.stepCount }}</span>
        <span class="dfd__stat-label">Steps</span>
      </div>
      <div class="dfd__stat">
        <span class="dfd__stat-value">{{ item.fileCount }}</span>
        <span class="dfd__stat-label">Files</span>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.dfd {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 1rem;
  border: 1px solid rgba(96, 165, 250, 0.25);
  border-radius: 1rem;
  background: rgba(17, 24, 39, 0.94);
  color: #e5e7eb;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
  overflow-y: auto;
}

.dfd__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dfd__eyebrow {
  margin: 0;
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #93c5fd;
}

.dfd__close {
  border: 0;
  border-radius: 999px;
  width: 1.5rem;
  height: 1.5rem;
  background: rgba(31, 41, 55, 0.85);
  color: #d1d5db;
  cursor: pointer;
}

.dfd__close:hover,
.dfd__close:focus-visible {
  background: rgba(37, 99, 235, 0.3);
}

.dfd__title {
  margin: 0.5rem 0 0;
  font-size: 0.9375rem;
  font-weight: 700;
  line-height: 1.3;
  word-break: break-word;
}

.dfd__tags {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.dfd__method {
  padding: 0.125rem 0.375rem;
  border-radius: 0.375rem;
  font-size: 0.6875rem;
  font-weight: 700;
  background: rgba(148, 163, 184, 0.22);
  color: #cbd5e1;
}

.dfd__method--get { background: rgba(16, 185, 129, 0.22); color: #6ee7b7; }
.dfd__method--post { background: rgba(59, 130, 246, 0.22); color: #93c5fd; }
.dfd__method--put { background: rgba(245, 158, 11, 0.22); color: #fcd34d; }
.dfd__method--patch { background: rgba(168, 85, 247, 0.22); color: #d8b4fe; }
.dfd__method--delete { background: rgba(239, 68, 68, 0.22); color: #fca5a5; }

.dfd__domain {
  padding: 0.125rem 0.5rem;
  border-radius: 999px;
  background: rgba(96, 165, 250, 0.18);
  color: #bfdbfe;
  font-size: 0.6875rem;
  font-weight: 600;
}

.dfd__route {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  margin: 0.625rem 0 0;
  padding: 0.375rem 0.5rem;
  border-radius: 0.5rem;
  background: rgba(15, 23, 42, 0.7);
  color: #cbd5e1;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
  word-break: break-all;
}

.dfd__desc {
  margin: 0.75rem 0 0;
  color: #9ca3af;
  font-size: 0.8125rem;
  line-height: 1.45;
}

.dfd__notice {
  margin: 0.625rem 0 0;
  padding: 0.4rem 0.6rem;
  border: 1px solid rgba(251, 191, 36, 0.4);
  border-radius: 0.5rem;
  background: rgba(120, 53, 15, 0.5);
  color: #fde68a;
  font-size: 0.75rem;
}

.dfd__section {
  margin: 1rem 0 0.5rem;
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #93c5fd;
}

.dfd__steps {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.dfd__step {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  width: 100%;
  padding: 0.5rem;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  background: rgba(31, 41, 55, 0.6);
  color: inherit;
  cursor: pointer;
  text-align: left;
  font: inherit;
}

.dfd__step:hover,
.dfd__step:focus-visible {
  border-color: rgba(96, 165, 250, 0.45);
  background: rgba(37, 99, 235, 0.2);
  outline: none;
}

.dfd__step--selected {
  border-color: rgba(96, 165, 250, 0.82);
  background: rgba(37, 99, 235, 0.32);
}

.dfd__step-index {
  flex: 0 0 auto;
  width: 1.25rem;
  height: 1.25rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(96, 165, 250, 0.25);
  color: #bfdbfe;
  font-size: 0.6875rem;
  font-weight: 700;
}

.dfd__step-body {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  min-width: 0;
}

.dfd__step-top {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  min-width: 0;
}

.dfd__step-dot {
  flex: 0 0 auto;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 999px;
}

.dfd__step-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  font-size: 0.8125rem;
}

.dfd__layer {
  flex: 0 0 auto;
  padding: 0 0.3rem;
  border-radius: 0.25rem;
  background: rgba(96, 165, 250, 0.2);
  color: #bfdbfe;
  font-size: 0.625rem;
}

.dfd__step-file {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.625rem;
}

.dfd__summary {
  display: flex;
  gap: 1.5rem;
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.dfd__stat {
  display: flex;
  flex-direction: column;
}

.dfd__stat-value {
  font-size: 1.25rem;
  font-weight: 700;
  color: #e5e7eb;
}

.dfd__stat-label {
  font-size: 0.6875rem;
  color: #94a3b8;
}
</style>
