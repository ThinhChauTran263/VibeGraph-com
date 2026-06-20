<script setup lang="ts">
import mermaid from 'mermaid'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useDiagrams, type DiagramKind } from '@/composables/useDiagrams'
import type { UmlUseCaseResponse } from '@/lib/api'
import { renderUmlUseCaseSvg } from '@/lib/umlUseCaseSvg'

const props = defineProps<{
  projectId: string
}>()

const BASE_RENDER_SCALE = 2.2

const activeKind = ref<DiagramKind>('uml')
const packageFilter = ref('')
const renderedSvg = ref('')
const renderError = ref<string | null>(null)
const diagramZoom = ref(1)
const isFullscreen = ref(false)
// Formal export mode: hides developer-facing inference warnings so screenshots/exports look like a
// finished SRS diagram. Warnings stay visible in normal interactive mode.
const isExportMode = ref(false)
const {
  status,
  diagram,
  errorMessage,
  isLoading,
  loadUmlUseCaseDiagram,
  loadClassDiagram,
  reset,
} = useDiagrams()
let renderSeq = 0

const tabs: Array<{ kind: DiagramKind; label: string }> = [
  { kind: 'uml', label: 'UML Use Case' },
  { kind: 'class', label: 'Class' },
]

const mermaidSource = computed(() => diagram.value?.mermaidSyntax?.trim() ?? '')
const hasDiagramContent = computed(() => status.value === 'success' && mermaidSource.value.length > 0)
const zoomPercent = computed(() => `${Math.round(diagramZoom.value * 100)}%`)
const diagramStageStyle = computed(() => ({
  transform: `scale(${Number((diagramZoom.value * BASE_RENDER_SCALE).toFixed(2))})`,
}))

// Inference warnings (e.g. role guessed from HTTP method) — UML only.
const warnings = computed<string[]>(() => {
  const current = diagram.value
  if (current?.kind !== 'uml') return []
  return 'warnings' in current && Array.isArray(current.warnings) ? current.warnings : []
})

mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' })

async function refresh(): Promise<void> {
  clearRenderedDiagram()
  if (activeKind.value === 'uml') {
    await loadUmlUseCaseDiagram(props.projectId)
  } else {
    await loadClassDiagram(props.projectId, packageFilter.value)
  }
  // Re-render explicitly after a refresh. The `mermaidSource` watch only fires when the source
  // STRING changes, so refreshing the same tab (identical diagram) would otherwise leave the
  // viewer blank — clearRenderedDiagram() emptied the SVG and the watch never re-ran. Rendering
  // here guarantees a repaint whether or not the source changed.
  await nextTick()
  // The UML Use Case tab draws a real OMG UML 2.5.1 diagram (stick actors, ellipses, system
  // boundary, dashed include/extend, hollow-triangle generalization) from the JSON model. The
  // Class tab keeps using Mermaid.
  if (activeKind.value === 'uml' && diagram.value?.kind === 'uml') {
    // diagram.value is a deep-readonly ref value; the renderer only reads it.
    renderUmlSvg(diagram.value as UmlUseCaseResponse & { kind: 'uml' })
  } else {
    await renderMermaid(mermaidSource.value)
  }
}

function renderUmlSvg(model: UmlUseCaseResponse & { kind: 'uml' }): void {
  const seq = ++renderSeq
  try {
    const svg = renderUmlUseCaseSvg(model)
    if (seq !== renderSeq) return
    renderedSvg.value = svg
    renderError.value = null
  } catch (err) {
    if (seq !== renderSeq) return
    renderedSvg.value = ''
    renderError.value = err instanceof Error && err.message ? err.message : 'Failed to render UML diagram.'
  }
}

async function renderMermaid(source: string): Promise<void> {
  const seq = ++renderSeq
  if (!source) {
    renderedSvg.value = ''
    return
  }

  try {
    const id = `diagram-${activeKind.value}-${seq}`
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
  resetZoom()
  void refresh()
}

function zoomIn(): void {
  diagramZoom.value = Math.min(2.5, Number((diagramZoom.value + 0.1).toFixed(2)))
}

function zoomOut(): void {
  diagramZoom.value = Math.max(0.4, Number((diagramZoom.value - 0.1).toFixed(2)))
}

function resetZoom(): void {
  diagramZoom.value = 1
}

function openFullscreen(): void {
  isFullscreen.value = true
}

function closeFullscreen(): void {
  isFullscreen.value = false
}

function toggleExportMode(): void {
  isExportMode.value = !isExportMode.value
}

watch(
  () => props.projectId,
  () => {
    reset()
    clearRenderedDiagram()
    resetZoom()
    closeFullscreen()
    void refresh()
  },
)

onMounted(() => {
  void refresh()
})
</script>

<template>
  <section
    class="diagram-panel"
    :class="{ 'diagram-panel--export': isExportMode }"
    :data-export-mode="isExportMode"
    aria-labelledby="diagram-panel-heading"
  >
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

    <ul
      v-if="activeKind === 'uml' && warnings.length > 0 && !isExportMode"
      class="diagram-panel__warnings"
      data-test="diagram-warnings"
      aria-label="Inference warnings"
    >
      <li v-for="(warning, index) in warnings" :key="index" class="diagram-panel__warning">
        {{ warning }}
      </li>
    </ul>

    <p v-if="isLoading" class="diagram-panel__status" role="status">Loading diagram…</p>

    <p v-else-if="status === 'error'" class="diagram-panel__error" role="alert">
      {{ errorMessage }}
    </p>

    <p v-else-if="renderError" class="diagram-panel__error" role="alert">
      {{ renderError }}
    </p>

    <div v-else-if="hasDiagramContent" class="diagram-panel__viewer">
      <button
        v-if="isExportMode"
        class="diagram-panel__exit-export"
        data-test="diagram-export-exit"
        type="button"
        @click="toggleExportMode"
      >
        Exit export mode
      </button>
      <div class="diagram-panel__viewer-toolbar" aria-label="Diagram view controls">
        <button
          class="diagram-panel__tool"
          data-test="diagram-zoom-out"
          type="button"
          aria-label="Zoom out"
          @click="zoomOut"
        >
          -
        </button>
        <button
          class="diagram-panel__tool diagram-panel__tool--wide"
          data-test="diagram-zoom-reset"
          type="button"
          @click="resetZoom"
        >
          {{ zoomPercent }}
        </button>
        <button
          class="diagram-panel__tool"
          data-test="diagram-zoom-in"
          type="button"
          aria-label="Zoom in"
          @click="zoomIn"
        >
          +
        </button>
        <button
          class="diagram-panel__tool diagram-panel__tool--wide"
          :class="{ 'diagram-panel__tool--active': isExportMode }"
          data-test="diagram-export-mode"
          type="button"
          :aria-pressed="isExportMode"
          @click="toggleExportMode"
        >
          {{ isExportMode ? 'Export: On' : 'Export Mode' }}
        </button>
        <button
          class="diagram-panel__tool diagram-panel__tool--wide"
          data-test="diagram-fullscreen-open"
          type="button"
          @click="openFullscreen"
        >
          Fullscreen
        </button>
      </div>
      <div class="diagram-panel__canvas" data-test="diagram-canvas">
        <div
          class="diagram-panel__stage"
          data-test="diagram-stage"
          :style="diagramStageStyle"
          v-html="renderedSvg"
        ></div>
      </div>
    </div>

    <p v-else-if="status === 'success'" class="diagram-panel__empty">No diagram content returned.</p>

    <p v-else class="diagram-panel__hint">Choose a diagram type to load generated diagram output.</p>

    <Teleport to="body">
      <div
        v-if="isFullscreen && hasDiagramContent"
        class="diagram-panel__fullscreen"
        data-test="diagram-fullscreen"
        role="dialog"
        aria-modal="true"
        aria-label="Fullscreen diagram viewer"
        @keydown.esc="closeFullscreen"
      >
        <header class="diagram-panel__fullscreen-header">
          <div>
            <p class="diagram-panel__eyebrow">Diagram viewer</p>
            <h2>{{ tabs.find((tab) => tab.kind === activeKind)?.label }}</h2>
          </div>
          <div class="diagram-panel__fullscreen-actions">
            <button class="diagram-panel__tool" type="button" aria-label="Zoom out" @click="zoomOut">-</button>
            <button class="diagram-panel__tool diagram-panel__tool--wide" type="button" @click="resetZoom">
              {{ zoomPercent }}
            </button>
            <button class="diagram-panel__tool" type="button" aria-label="Zoom in" @click="zoomIn">+</button>
            <button
              class="diagram-panel__tool diagram-panel__tool--wide"
              data-test="diagram-fullscreen-close"
              type="button"
              @click="closeFullscreen"
            >
              Close
            </button>
          </div>
        </header>
        <div class="diagram-panel__fullscreen-canvas">
          <div class="diagram-panel__stage" :style="diagramStageStyle" v-html="renderedSvg"></div>
        </div>
      </div>
    </Teleport>
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

.diagram-panel__viewer {
  position: relative;
  display: flex;
  min-height: 20rem;
  flex: 1;
  flex-direction: column;
  gap: 0.75rem;
}

.diagram-panel__viewer-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
}

.diagram-panel__tool {
  min-width: 2.25rem;
  min-height: 2rem;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 0.5rem;
  background: rgba(30, 41, 59, 0.92);
  color: #e5e7eb;
  cursor: pointer;
  font-weight: 700;
}

.diagram-panel__tool:hover {
  border-color: rgba(147, 197, 253, 0.7);
  background: rgba(37, 99, 235, 0.34);
}

.diagram-panel__tool--wide {
  min-width: 5.5rem;
  padding: 0 0.75rem;
}

.diagram-panel__tool--active {
  border-color: rgba(96, 165, 250, 0.9);
  background: rgba(37, 99, 235, 0.5);
  color: #bfdbfe;
}

/*
 * Formal export mode: strip the panel down to the diagram-only surface so a screenshot reads like a
 * finished SRS figure. Only the system boundary, actors, use cases and relationships remain — every
 * developer-facing control (header, tabs, filters, warnings, toolbar) is removed from the capture
 * area, and the canvas scrollbars/borders are dropped so the diagram floats on clean whitespace.
 */
.diagram-panel--export {
  border: none;
  box-shadow: none;
  background: #ffffff;
  padding: 0;
  gap: 0;
}

.diagram-panel--export .diagram-panel__header,
.diagram-panel--export .diagram-panel__tabs,
.diagram-panel--export .diagram-panel__filters,
.diagram-panel--export .diagram-panel__warnings,
.diagram-panel--export .diagram-panel__viewer-toolbar {
  display: none;
}

.diagram-panel--export .diagram-panel__viewer {
  gap: 0;
}

.diagram-panel--export .diagram-panel__canvas {
  overflow: visible;
  border: none;
  border-radius: 0;
  padding: 2.5rem;
  background: #ffffff;
}

.diagram-panel--export .diagram-panel__stage {
  transform: none !important;
}

/* Sole control kept in export mode: a discreet floating button to return to interactive mode. */
.diagram-panel__exit-export {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  z-index: 5;
  border: 1px solid rgba(148, 163, 184, 0.4);
  border-radius: 0.5rem;
  padding: 0.375rem 0.75rem;
  background: rgba(15, 23, 42, 0.85);
  color: #e5e7eb;
  cursor: pointer;
  font-size: 0.8125rem;
  font-weight: 600;
}

.diagram-panel__exit-export:hover {
  border-color: rgba(147, 197, 253, 0.7);
}

.diagram-panel__canvas {
  min-height: 22rem;
  flex: 1;
  overflow: auto;
  border: 1px solid rgba(51, 65, 85, 0.85);
  border-radius: 0.875rem;
  padding: 1rem;
  background: #f8fafc;
  color: #0f172a;
}

.diagram-panel__stage {
  display: inline-block;
  min-width: max-content;
  min-height: max-content;
  transform-origin: top left;
}

.diagram-panel__stage :deep(svg) {
  max-width: none;
  height: auto;
}

.diagram-panel__warnings {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  margin: 0;
  padding: 0.75rem 0.75rem 0.75rem 1.75rem;
  border: 1px solid rgba(234, 179, 8, 0.4);
  border-radius: 0.75rem;
  background: rgba(120, 53, 15, 0.24);
  color: #fde68a;
  font-size: 0.8125rem;
}

.diagram-panel__fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
  background: rgba(2, 6, 23, 0.98);
  color: #e5e7eb;
}

.diagram-panel__fullscreen-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.diagram-panel__fullscreen-header h2,
.diagram-panel__fullscreen-header p {
  margin: 0;
}

.diagram-panel__fullscreen-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.5rem;
}

.diagram-panel__fullscreen-canvas {
  min-height: 0;
  flex: 1;
  overflow: auto;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 0.875rem;
  padding: 1rem;
  background: #f8fafc;
  color: #0f172a;
}

@media (max-width: 42rem) {
  .diagram-panel__header,
  .diagram-panel__fullscreen-header {
    flex-direction: column;
  }

  .diagram-panel__viewer-toolbar,
  .diagram-panel__fullscreen-actions {
    justify-content: flex-start;
  }
}
</style>
