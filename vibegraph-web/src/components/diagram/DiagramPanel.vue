<script setup lang="ts">
import mermaid from 'mermaid'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useDiagrams, type DiagramKind } from '@/composables/useDiagrams'

const props = defineProps<{
  projectId: string
}>()

const activeKind = ref<DiagramKind>('usecase')
const packageFilter = ref('')
const renderedSvg = ref('')
const renderError = ref<string | null>(null)
const { status, diagram, errorMessage, isLoading, loadUseCaseDiagram, loadClassDiagram, reset } = useDiagrams()
let renderSeq = 0

const tabs: Array<{ kind: DiagramKind; label: string }> = [
  { kind: 'usecase', label: 'Use Case' },
  { kind: 'class', label: 'Class' },
]

const mermaidSource = computed(() => diagram.value?.mermaid?.trim() ?? '')
const hasDiagramContent = computed(() => status.value === 'success' && mermaidSource.value.length > 0)

mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' })

async function refresh(): Promise<void> {
  clearRenderedDiagram()
  if (activeKind.value === 'usecase') {
    await loadUseCaseDiagram(props.projectId)
    return
  }
  await loadClassDiagram(props.projectId, packageFilter.value)
}

async function renderMermaid(source: string): Promise<void> {
  const seq = ++renderSeq
  if (!source) {
    renderedSvg.value = ''
    return
  }

  try {
    const id = `diagram-${activeKind.value}-${Date.now().toString(36)}`
    const { svg } = await mermaid.render(id, source)
    if (seq !== renderSeq) return
    renderedSvg.value = svg
    renderError.value = null
  } catch (err) {
    if (seq !== renderSeq) return
    renderedSvg.value = ''
    renderError.value = err instanceof Error && err.message ? err.message : 'Failed to render Mermaid diagram.'
  }
}

function clearRenderedDiagram(): void {
  renderSeq++
  renderedSvg.value = ''
  renderError.value = null
}

function selectTab(kind: DiagramKind): void {
  if (activeKind.value === kind) return
  activeKind.value = kind
  void refresh()
}

watch(
  () => props.projectId,
  () => {
    reset()
    clearRenderedDiagram()
    void refresh()
  },
)

watch(mermaidSource, async (source) => {
  await nextTick()
  await renderMermaid(source)
})

onMounted(() => {
  void refresh()
})
</script>

<template>
  <section class="diagram-panel" aria-labelledby="diagram-panel-heading">
    <header class="diagram-panel__header">
      <div>
        <p class="diagram-panel__eyebrow">Mermaid diagrams</p>
        <h2 id="diagram-panel-heading">Architecture diagrams</h2>
      </div>
      <button
        class="diagram-panel__refresh"
        data-test="diagram-refresh"
        type="button"
        :disabled="isLoading"
        @click="refresh"
      >
        {{ isLoading ? 'Loading…' : 'Refresh' }}
      </button>
    </header>

    <div class="diagram-panel__tabs" role="tablist" aria-label="Diagram type">
      <button
        v-for="tab in tabs"
        :key="tab.kind"
        class="diagram-panel__tab"
        :class="{ 'diagram-panel__tab--active': activeKind === tab.kind }"
        :data-test="`diagram-tab-${tab.kind}`"
        type="button"
        role="tab"
        :aria-selected="activeKind === tab.kind"
        @click="selectTab(tab.kind)"
      >
        {{ tab.label }}
      </button>
    </div>

    <form v-if="activeKind === 'class'" class="diagram-panel__filters" @submit.prevent="refresh">
      <label class="diagram-panel__filter-label" for="diagram-package-filter">
        Package filter
      </label>
      <input
        id="diagram-package-filter"
        v-model="packageFilter"
        class="diagram-panel__package-input"
        type="text"
        placeholder="com.example.service"
        :disabled="isLoading"
      />
      <button class="diagram-panel__filter-submit" type="submit" :disabled="isLoading">Apply</button>
    </form>

    <p v-if="isLoading" class="diagram-panel__status" role="status">Loading diagram…</p>

    <p v-else-if="status === 'error'" class="diagram-panel__error" role="alert">
      {{ errorMessage }}
    </p>

    <p v-else-if="renderError" class="diagram-panel__error" role="alert">
      {{ renderError }}
    </p>

    <div v-else-if="hasDiagramContent" class="diagram-panel__canvas" v-html="renderedSvg"></div>

    <p v-else-if="status === 'success'" class="diagram-panel__empty">No diagram content returned.</p>

    <p v-else class="diagram-panel__hint">Choose a diagram type to load generated Mermaid output.</p>
  </section>
</template>

<style scoped>
.diagram-panel {
  display: flex;
  min-height: 20rem;
  height: 100%;
  flex-direction: column;
  gap: 1rem;
  border: 1px solid rgba(59, 130, 246, 0.24);
  border-radius: 1rem;
  padding: 1rem;
  background: rgba(15, 23, 42, 0.94);
  color: #e5e7eb;
  box-shadow: 0 18px 52px rgba(15, 23, 42, 0.32);
}

.diagram-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.diagram-panel__header h2,
.diagram-panel__eyebrow {
  margin: 0;
}

.diagram-panel__eyebrow {
  color: #93c5fd;
  font-size: 0.8125rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.diagram-panel__header h2 {
  margin-top: 0.25rem;
  font-size: 1.125rem;
}

.diagram-panel__refresh,
.diagram-panel__filter-submit,
.diagram-panel__tab {
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 0.625rem;
  background: rgba(30, 41, 59, 0.86);
  color: #e5e7eb;
  cursor: pointer;
  font-weight: 600;
}

.diagram-panel__refresh,
.diagram-panel__filter-submit {
  min-height: 2.25rem;
  padding: 0 0.75rem;
}

.diagram-panel__refresh:hover:not(:disabled),
.diagram-panel__filter-submit:hover:not(:disabled),
.diagram-panel__tab:hover {
  border-color: rgba(147, 197, 253, 0.7);
}

.diagram-panel__refresh:disabled,
.diagram-panel__filter-submit:disabled {
  opacity: 0.6;
  cursor: progress;
}

.diagram-panel__tabs {
  display: flex;
  gap: 0.5rem;
}

.diagram-panel__tab {
  padding: 0.5rem 0.75rem;
}

.diagram-panel__tab--active {
  border-color: rgba(96, 165, 250, 0.9);
  background: rgba(37, 99, 235, 0.34);
  color: #bfdbfe;
}

.diagram-panel__filters {
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
}

.diagram-panel__filter-label {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.25rem;
  color: #9ca3af;
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.diagram-panel__package-input {
  min-height: 2.25rem;
  border: 1px solid #374151;
  border-radius: 0.625rem;
  background: rgba(15, 23, 42, 0.78);
  color: #e5e7eb;
  padding: 0 0.75rem;
}

.diagram-panel__status,
.diagram-panel__hint,
.diagram-panel__empty {
  color: #9ca3af;
  font-size: 0.875rem;
}

.diagram-panel__error {
  padding: 0.75rem;
  border: 1px solid rgba(239, 68, 68, 0.5);
  border-radius: 0.75rem;
  background: rgba(127, 29, 29, 0.3);
  color: #fecaca;
  font-size: 0.875rem;
}

.diagram-panel__canvas {
  min-height: 16rem;
  overflow: auto;
  border: 1px solid rgba(51, 65, 85, 0.85);
  border-radius: 0.875rem;
  padding: 1rem;
  background: #f8fafc;
  color: #0f172a;
}

.diagram-panel__canvas :deep(svg) {
  max-width: 100%;
  height: auto;
}
</style>
