<script setup lang="ts">
/**
 * ImpactAnalysisPanel - shows the blast radius of the selected node.
 *
 * Reads nothing from the graph store directly: the caller passes the active
 * `projectId` and `node`, which keeps this panel unit-testable in isolation
 * from GraphCanvas/Sigma. All request state lives in `useImpactAnalysis`.
 */
import { computed, nextTick, ref, watch } from 'vue'
import { useImpactAnalysis } from '@/composables/useImpactAnalysis'
import type { GraphNode, NodeType } from '@/types/graph'
import type { ImpactNode, ImpactProfile } from '@/lib/api'
import FilePath from '@/components/ui/FilePath.vue'
import ThemedSelect from '@/components/ui/ThemedSelect.vue'
import CodeViewerModal from '@/components/panels/CodeViewerModal.vue'

const props = defineProps<{
  projectId: string
  node: GraphNode | null
}>()

const emit = defineEmits<{
  (e: 'select', nodeId: string): void
}>()

const {
  status,
  result,
  errorMessage,
  selectedDepth,
  selectedProfile,
  isLoading,
  allowedDepths,
  allowedProfiles,
  loadImpact,
  reset,
} = useImpactAnalysis()

// Changing the depth after a result is shown would otherwise leave a stale
// result that no longer matches the selector. Invalidate it so the displayed
// numbers never disagree with the chosen depth; the user re-runs Analyze.
watch(selectedDepth, () => {
  if (status.value === 'success' || status.value === 'error') {
    reset()
  }
})

watch(selectedProfile, () => {
  if (status.value === 'success' || status.value === 'error') {
    reset()
  }
})

/**
 * Node types that can actually be the TARGET of the dependency-impact traversal
 * (CALLS | IMPORTS | EXTENDS | IMPLEMENTS | INJECTS). For any other type
 * (Project, Package, File, APIEndpoint, Field, LocalVariable, Route) the result
 * is structurally always empty, so the empty-state copy must say so instead of
 * implying a deeper depth or a different selection would help.
 */
const DEPENDENCY_TARGET_TYPES: ReadonlySet<NodeType> = new Set<NodeType>([
  'Class',
  'Interface',
  'Enum',
  'Record',
  'DBModel',
  'Annotation',
  'Method',
  'Constructor',
  'External',
])

const targetSupported = computed(() => !!props.node && DEPENDENCY_TARGET_TYPES.has(props.node.type))

// A new node selection invalidates any previous result, and we pick the profile
// that yields meaningful results for that node type: dependency-target types keep
// the dependency blast radius; structural-only nodes (File, Package, Project,
// APIEndpoint, …) default to the Structural profile so Analyze isn't an empty
// result on the big nodes users click first.
watch(
  () => props.node?.id,
  () => {
    reset()
    if (props.node) {
      selectedProfile.value = DEPENDENCY_TARGET_TYPES.has(props.node.type)
        ? 'dependency'
        : 'structural'
    }
  },
  { immediate: true },
)

const riskClass = computed(() => {
  const level = result.value?.riskLevel?.toUpperCase() ?? ''
  return `impact-panel__risk--${level.toLowerCase() || 'unknown'}`
})

const profileLabels: Record<ImpactProfile, string> = {
  dependency: 'Dependency',
  structural: 'Structural',
  'type-data-flow': 'Type/Data-flow',
}

const profileHelp: Record<ImpactProfile, string> = {
  dependency: 'Calls, imports, inheritance and injection dependents.',
  structural: 'Containment, definitions, fields, methods and route handlers.',
  'type-data-flow': 'Type links plus deep CPG reads, writes, catches and flow steps.',
}

const activeProfileHelp = computed(() => profileHelp[selectedProfile.value])

const profileOptions = computed(() =>
  allowedProfiles.map((profile) => ({ value: profile, label: profileLabels[profile] })),
)

const depthOptions = computed(() =>
  allowedDepths.map((depth) => ({ value: depth, label: String(depth) })),
)

const depthGroupLabels = computed(() => {
  if (selectedProfile.value === 'dependency') {
    return {
      direct: 'Will break (d=1)',
      likely: 'Likely affected (d=2)',
      maybe: 'May need testing (d>=3)',
    }
  }
  return {
    direct: 'Direct related (d=1)',
    likely: 'Indirect related (d=2)',
    maybe: 'Further related (d>=3)',
  }
})

const depthGroups = computed(() => {
  const data = result.value
  if (!data) return []
  const labels = depthGroupLabels.value
  return [
    { key: 'willBreak', label: labels.direct, nodes: data.willBreak ?? [] },
    { key: 'likelyAffected', label: labels.likely, nodes: data.likelyAffected ?? [] },
    { key: 'mayNeedTesting', label: labels.maybe, nodes: data.mayNeedTesting ?? [] },
  ].filter((group) => group.nodes.length > 0)
})

const hasAffectedNodes = computed(() => depthGroups.value.length > 0)

function nodeKey(node: ImpactNode): string {
  return node.id || node.fullName || node.name
}

// Affected nodes that resolve to a real source file can open the read-only code viewer.
// Packages/projects are directories and External imports live outside the source tree.
const NON_SOURCE_TYPES = new Set(['Package', 'Project', 'External'])
function canViewImpact(node: ImpactNode): boolean {
  return !!node.filePath && !NON_SOURCE_TYPES.has(node.type)
}

const showCode = ref(false)
const codeNode = ref<ImpactNode | null>(null)

function openImpactCode(node: ImpactNode): void {
  if (!node.filePath) return
  codeNode.value = node
  showCode.value = true
}

// Clicking an affected node navigates the graph to it (selects + focuses), so the user can
// inspect that node's own detail/impact without hunting for it on the canvas.
function selectAffected(node: ImpactNode): void {
  if (node.id) emit('select', node.id)
}

function closeCode(): void {
  showCode.value = false
  codeNode.value = null
}

function analyze(): void {
  if (!props.node) return
  void loadImpact(props.projectId, props.node.id, selectedDepth.value, selectedProfile.value)
}

// After a run completes, bring the result into view. The right column scrolls as a
// whole, so a freshly-loaded blast radius can otherwise land just below the fold —
// making Analyze feel like it did nothing. Scrolling the body in confirms the result.
const bodyRef = ref<HTMLElement | null>(null)
watch(status, (next) => {
  if (next !== 'success' && next !== 'error') return
  void nextTick(() => {
    bodyRef.value?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' })
  })
})
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
        <label class="impact-panel__field" for="impact-profile">
          <span>Profile</span>
          <ThemedSelect
            input-id="impact-profile"
            v-model="selectedProfile"
            :options="profileOptions"
            :disabled="isLoading"
          />
        </label>
        <label class="impact-panel__field" for="impact-depth">
          <span>Depth</span>
          <ThemedSelect
            input-id="impact-depth"
            v-model="selectedDepth"
            :options="depthOptions"
            :disabled="isLoading"
          />
        </label>
        <button class="impact-panel__analyze" type="submit" :disabled="isLoading">
          <span v-if="isLoading" class="impact-panel__spinner" aria-hidden="true"></span>
          {{ isLoading ? 'Analyzing…' : 'Analyze' }}
        </button>
      </form>
      <p class="impact-panel__profile-help">{{ activeProfileHelp }}</p>

      <div class="impact-panel__body" ref="bodyRef">
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
                <div class="impact-panel__node-head">
                  <button
                    type="button"
                    class="impact-panel__node-name"
                    :title="`Show ${affected.name} in graph`"
                    @click="selectAffected(affected)"
                  >
                    {{ affected.name }}
                  </button>
                  <button
                    v-if="canViewImpact(affected)"
                    type="button"
                    class="impact-panel__node-code"
                    :aria-label="`View source of ${affected.name}`"
                    title="View source"
                    @click="openImpactCode(affected)"
                  >
                    <span aria-hidden="true">{&nbsp;}</span>
                  </button>
                </div>
                <FilePath
                  v-if="affected.filePath"
                  :path="affected.filePath"
                  class="impact-panel__node-path"
                />
              </li>
            </ul>
          </section>

          <p
            v-if="selectedProfile === 'dependency' && !hasAffectedNodes && !targetSupported"
            class="impact-panel__empty-list"
          >
            Impact analysis measures the <strong>dependency blast radius</strong> — what calls,
            imports, extends, implements or injects this node.
            <strong>{{ node.type }}</strong> nodes are not targets of those relationships, so an
            empty result here is expected (not a missing dependency). Select a
            <strong>Class</strong>, <strong>Interface</strong>, or <strong>Method</strong>
            to see dependents.
          </p>
          <p
            v-else-if="selectedProfile === 'dependency' && !hasAffectedNodes"
            class="impact-panel__empty-list"
          >
            Nothing depends on <strong>{{ node.name }}</strong> through call, import, inheritance or
            injection edges within depth {{ selectedDepth }}. This is common for entrypoints such as
            a controller or route handler that nothing else depends on.
          </p>
          <p v-else-if="!hasAffectedNodes" class="impact-panel__empty-list">
            No {{ profileLabels[selectedProfile].toLowerCase() }} relationships found for
            <strong>{{ node.name }}</strong> within depth {{ selectedDepth }}.
          </p>
        </template>

        <p v-else class="impact-panel__hint">
          Choose a depth and run analysis to see what this node affects.
        </p>
      </div>

      <CodeViewerModal
        v-if="showCode && codeNode"
        :project-id="props.projectId"
        :node="codeNode"
        @close="closeCode"
      />
    </template>
  </aside>
</template>

<style scoped>
.impact-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  width: 100%;
  /* The whole card scrolls. A short inner results window forced users to
     scroll a sliver; scrolling the full card gives results the entire
     frame once the controls move out of view. */
  overflow-y: auto;
  border: 1px solid rgba(248, 113, 113, 0.28);
  border-radius: var(--vg-radius-lg);
  padding: 0.875rem;
  background: var(--vg-grad-surface);
  color: var(--vg-text);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

/* Body sizes to its content; the panel root owns the scrollbar (see above). */
.impact-panel__body {
  margin-top: 0.875rem;
  border-top: 1px solid var(--vg-border);
  padding-top: 0.25rem;
}

.impact-panel__body > :first-child {
  margin-top: 0.5rem;
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

/* Selected node rendered as a compact card: mono name + type pill, so the
   target reads as "the thing being analyzed" instead of two loose lines. */
.impact-panel__target {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.375rem 0.5rem;
  margin: 0.75rem 0 0;
  padding: 0.5rem 0.625rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface-2);
  overflow-wrap: anywhere;
}

.impact-panel__target-name {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-sm);
  font-weight: 600;
}

.impact-panel__target-type {
  flex: 0 0 auto;
  padding: 0.125rem 0.5rem;
  border-radius: var(--vg-radius-pill);
  background: rgba(248, 113, 113, 0.14);
  color: #fca5a5;
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

/* Grid, not flex-wrap. The panel is only ~21rem wide, so Profile + Depth + Analyze
   never fit on one line: Analyze used to wrap to its own full-width row while the
   two selects kept their content widths above it, leaving a ragged right edge.
   Two explicit columns make the selector row end exactly where the button ends. */
.impact-panel__controls {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  align-items: end;
  gap: 0.5rem;
  margin-top: 1rem;
}

.impact-panel__field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

/* The grid column now sets the width; a fixed min-width would push the row wider
   than the Analyze button and reintroduce the ragged edge. */

.impact-panel__profile-help {
  margin: 0.5rem 0 0;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
  line-height: 1.5;
}

.impact-panel__analyze {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  height: 2.5rem;
  grid-column: 1 / -1;
  border: none;
  border-radius: var(--vg-radius-sm);
  background: linear-gradient(135deg, #ef4444, #dc2626 55%, #b91c1c);
  box-shadow: 0 10px 24px -12px rgba(239, 68, 68, 0.65);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  transition:
    filter var(--vg-dur-fast) ease,
    transform var(--vg-dur-fast) ease,
    box-shadow var(--vg-dur-fast) ease;
}

.impact-panel__analyze:hover:not(:disabled) {
  filter: brightness(1.1);
  transform: translateY(-1px);
  box-shadow: 0 14px 28px -12px rgba(239, 68, 68, 0.75);
}

.impact-panel__analyze:active:not(:disabled) {
  transform: translateY(0);
  filter: brightness(0.96);
}

.impact-panel__analyze:disabled {
  opacity: 0.75;
  cursor: progress;
}

.impact-panel__spinner {
  width: 0.875rem;
  height: 0.875rem;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: impact-spin 700ms linear infinite;
}

@keyframes impact-spin {
  to {
    transform: rotate(360deg);
  }
}

.impact-panel__status,
.impact-panel__hint,
.impact-panel__empty,
.impact-panel__empty-list {
  color: var(--vg-text-muted);
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
  color: var(--vg-text-muted);
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
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  padding: 0.5rem 0.625rem;
  background: var(--vg-surface-2);
  transition:
    border-color var(--vg-dur-fast) ease,
    background-color var(--vg-dur-fast) ease;
}

.impact-panel__nodes li:hover {
  border-color: var(--vg-border-strong);
  background: var(--vg-surface-3);
}

.impact-panel__node-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.impact-panel__node-code {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 1.7rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: 0.4rem;
  background: var(--vg-bg-elev);
  color: var(--vg-blue-bright);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    border-color 120ms ease,
    background-color 120ms ease,
    color 120ms ease;
}

.impact-panel__node-code:hover,
.impact-panel__node-code:focus-visible {
  border-color: rgba(96, 165, 250, 0.85);
  background: rgba(37, 99, 235, 0.22);
  color: #f8fafc;
  outline: none;
}

.impact-panel__node-code:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

.impact-panel__node-name {
  flex: 1 1 auto;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  color: var(--vg-text);
  font: inherit;
  font-weight: 600;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
}

.impact-panel__node-name:hover,
.impact-panel__node-name:focus-visible {
  color: var(--vg-blue-bright);
  text-decoration: underline;
  text-underline-offset: 2px;
  outline: none;
}

.impact-panel__node-path {
  color: var(--vg-text-muted);
  font-size: 0.75rem;
  overflow-wrap: anywhere;
}
</style>
