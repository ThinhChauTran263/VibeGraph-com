<script setup lang="ts">
import mermaid from 'mermaid'
import { computed, nextTick, onActivated, onMounted, ref, watch } from 'vue'
import { useDiagrams, type DiagramKind } from '@/composables/useDiagrams'
import type { UmlUseCaseResponse } from '@/lib/api'
import { renderUmlUseCaseSvg } from '@/lib/umlUseCaseSvg'

const props = defineProps<{
  projectId: string
}>()

const BASE_RENDER_SCALE = 2.2
const MIN_ZOOM = 0.2
const MAX_ZOOM = 8
const WHEEL_ZOOM_FACTOR = 1.12

const activeKind = ref<DiagramKind>('uml')
const packageFilter = ref('')
const renderedSvg = ref('')
const renderError = ref<string | null>(null)
const diagramZoom = ref(1)
const isFullscreen = ref(false)
const isPanning = ref(false)
const mainCanvas = ref<HTMLElement | null>(null)
const fullscreenCanvas = ref<HTMLElement | null>(null)
const fullscreenDialog = ref<HTMLElement | null>(null)
const { status, diagram, errorMessage, isLoading, isStale, loadUmlUseCaseDiagram, loadClassDiagram, reset } =
  useDiagrams()
let renderSeq = 0

const tabs: Array<{ kind: DiagramKind; label: string }> = [
  { kind: 'uml', label: 'UML Use Case' },
  { kind: 'class', label: 'Class' },
]

const mermaidSource = computed(() => diagram.value?.mermaidSyntax?.trim() ?? '')
const hasDiagramContent = computed(() => status.value === 'success' && mermaidSource.value.length > 0)

// Packages that actually contain classifiers in this project. Drives the click-to-filter
// chips so the user never has to guess an exact package name.
const availablePackages = computed<string[]>(() => {
  const current = diagram.value
  if (current?.kind !== 'class') return []
  return Array.isArray(current.availablePackages) ? current.availablePackages : []
})
// The class diagram came back with no classes for the current filter (vs. a genuinely
// empty project). Used to show a helpful hint instead of a blank canvas.
const classDiagramEmpty = computed(
  () =>
    activeKind.value === 'class' &&
    status.value === 'success' &&
    mermaidSource.value.includes('No classes detected'),
)
const packagePlaceholder = computed(() => availablePackages.value[0] ?? 'com.example.service')
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

async function refresh(force = false): Promise<void> {
  clearRenderedDiagram()
  if (activeKind.value === 'uml') {
    await loadUmlUseCaseDiagram(props.projectId, 'detailed', { force })
  } else {
    await loadClassDiagram(props.projectId, packageFilter.value, { force })
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

/** Set the package filter from a chip (or clear it) and reload the class diagram. */
function applyPackage(pkg: string): void {
  packageFilter.value = pkg
  void refresh(true)
}

function selectTab(kind: DiagramKind): void {
  if (activeKind.value === kind) return
  activeKind.value = kind
  resetZoom()
  void refresh()
}

function clampZoom(value: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value))
}

function zoomIn(): void {
  diagramZoom.value = clampZoom(Number((diagramZoom.value + 0.1).toFixed(2)))
}

function zoomOut(): void {
  diagramZoom.value = clampZoom(Number((diagramZoom.value - 0.1).toFixed(2)))
}

function resetZoom(): void {
  diagramZoom.value = 1
}

/**
 * Zoom by a multiplicative factor while keeping the point under the cursor fixed.
 * Works with the top-left transform-origin: convert the cursor position to a
 * content coordinate before scaling, then restore the same coordinate after.
 */
function zoomAtPointer(canvas: HTMLElement | null, factor: number, clientX?: number, clientY?: number): void {
  const oldZoom = diagramZoom.value
  const newZoom = clampZoom(Number((oldZoom * factor).toFixed(3)))
  if (newZoom === oldZoom) return
  if (!canvas) {
    diagramZoom.value = newZoom
    return
  }

  const rect = canvas.getBoundingClientRect()
  const px = (clientX ?? rect.left + rect.width / 2) - rect.left
  const py = (clientY ?? rect.top + rect.height / 2) - rect.top
  const oldScale = oldZoom * BASE_RENDER_SCALE
  const newScale = newZoom * BASE_RENDER_SCALE
  const contentX = (canvas.scrollLeft + px) / oldScale
  const contentY = (canvas.scrollTop + py) / oldScale

  diagramZoom.value = newZoom
  void nextTick(() => {
    canvas.scrollLeft = contentX * newScale - px
    canvas.scrollTop = contentY * newScale - py
  })
}

function handleWheel(event: WheelEvent): void {
  // Only hijack the wheel for zooming; let the page scroll otherwise.
  event.preventDefault()
  const canvas = event.currentTarget as HTMLElement
  const factor = event.deltaY < 0 ? WHEEL_ZOOM_FACTOR : 1 / WHEEL_ZOOM_FACTOR
  zoomAtPointer(canvas, factor, event.clientX, event.clientY)
}

let panPointerId: number | null = null
let panStartX = 0
let panStartY = 0
let panScrollLeft = 0
let panScrollTop = 0

function startPan(event: PointerEvent): void {
  if (event.button !== 0) return
  // Stop the drag from turning into a text selection of the SVG labels.
  event.preventDefault()
  const canvas = event.currentTarget as HTMLElement
  panPointerId = event.pointerId
  panStartX = event.clientX
  panStartY = event.clientY
  panScrollLeft = canvas.scrollLeft
  panScrollTop = canvas.scrollTop
  isPanning.value = true
  canvas.setPointerCapture?.(event.pointerId)
}

function onPan(event: PointerEvent): void {
  if (panPointerId !== event.pointerId) return
  const canvas = event.currentTarget as HTMLElement
  canvas.scrollLeft = panScrollLeft - (event.clientX - panStartX)
  canvas.scrollTop = panScrollTop - (event.clientY - panStartY)
}

function endPan(event: PointerEvent): void {
  if (panPointerId !== event.pointerId) return
  const canvas = event.currentTarget as HTMLElement
  canvas.releasePointerCapture?.(event.pointerId)
  panPointerId = null
  isPanning.value = false
}

/** Scale the diagram so its full width fits the canvas, then scroll to the top-left. */
function fitToWidth(canvas: HTMLElement | null): void {
  if (!canvas) return
  const svg = canvas.querySelector('svg')
  if (!svg) return

  const currentScale = diagramZoom.value * BASE_RENDER_SCALE
  const intrinsicWidth = svg.getBoundingClientRect().width / currentScale
  if (!Number.isFinite(intrinsicWidth) || intrinsicWidth <= 0) return

  const available = canvas.clientWidth - 32 // account for the 1rem canvas padding on both sides
  const targetScale = available / intrinsicWidth
  diagramZoom.value = clampZoom(Number((targetScale / BASE_RENDER_SCALE).toFixed(3)))
  void nextTick(() => {
    canvas.scrollLeft = 0
    canvas.scrollTop = 0
  })
}

/**
 * Download the rendered diagram as a standalone SVG file. SVG is vector, so it
 * opens in any browser/editor and zooms losslessly — the reliable way to read a
 * very large class diagram that is cramped on screen.
 */
function downloadSvg(): void {
  const source = renderedSvg.value
  if (!source) return

  const withNamespace = source.includes('xmlns=')
    ? source
    : source.replace('<svg', '<svg xmlns="http://www.w3.org/2000/svg"')
  const blob = new Blob([withNamespace], { type: 'image/svg+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${activeKind.value}-diagram.svg`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

function openFullscreen(): void {
  isFullscreen.value = true
  // Move focus into the dialog so it receives the Esc keydown (a bare <div> is not focusable by
  // default) and keyboard users are not stranded behind the modal.
  void nextTick(() => fullscreenDialog.value?.focus())
}

function closeFullscreen(): void {
  isFullscreen.value = false
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

// Under <KeepAlive> the panel is cached, so returning to it does NOT re-fetch. If the
// underlying graph changed while we were away, the shown diagram is stale — revalidate
// it in place. When still fresh, do nothing so the kept-alive SVG stays instant.
onActivated(() => {
  if (isStale.value) void refresh()
})
</script>

<template>
  <section
    class="diagram-panel"
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
        @click="refresh(true)"
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

    <form v-if="activeKind === 'class'" class="diagram-panel__filters" @submit.prevent="refresh(true)">
      <label class="diagram-panel__filter-label" for="diagram-package-filter">
        Package filter
      </label>
      <input
        id="diagram-package-filter"
        v-model="packageFilter"
        class="diagram-panel__package-input"
        type="text"
        list="diagram-package-options"
        :placeholder="packagePlaceholder"
        :disabled="isLoading"
      />
      <datalist id="diagram-package-options">
        <option v-for="pkg in availablePackages" :key="pkg" :value="pkg" />
      </datalist>
      <button class="diagram-panel__filter-submit" type="submit" :disabled="isLoading">Apply</button>
    </form>

    <div
      v-if="activeKind === 'class' && availablePackages.length > 0"
      class="diagram-panel__package-chips"
      data-test="diagram-package-chips"
      aria-label="Available packages"
    >
      <button
        type="button"
        class="diagram-panel__package-chip"
        :class="{ 'diagram-panel__package-chip--active': packageFilter.trim() === '' }"
        :disabled="isLoading"
        @click="applyPackage('')"
      >
        All
      </button>
      <button
        v-for="pkg in availablePackages"
        :key="pkg"
        type="button"
        class="diagram-panel__package-chip"
        :class="{ 'diagram-panel__package-chip--active': packageFilter.trim() === pkg }"
        :disabled="isLoading"
        @click="applyPackage(pkg)"
      >
        {{ pkg }}
      </button>
    </div>

    <p
      v-if="classDiagramEmpty"
      class="diagram-panel__filter-empty"
      data-test="diagram-filter-empty"
      role="status"
    >
      No classes match "{{ packageFilter.trim() }}". Pick a package above or clear the filter.
    </p>

    <ul
      v-if="activeKind === 'uml' && warnings.length > 0"
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
          data-test="diagram-zoom-fit"
          type="button"
          @click="fitToWidth(mainCanvas)"
        >
          Fit
        </button>
        <button
          class="diagram-panel__tool diagram-panel__tool--wide"
          data-test="diagram-download-svg"
          type="button"
          @click="downloadSvg"
        >
          Download SVG
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
      <div
        ref="mainCanvas"
        class="diagram-panel__canvas"
        :class="{ 'diagram-panel__canvas--panning': isPanning }"
        data-test="diagram-canvas"
        @wheel="handleWheel"
        @pointerdown="startPan"
        @pointermove="onPan"
        @pointerup="endPan"
        @pointerleave="endPan"
      >
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
        ref="fullscreenDialog"
        class="diagram-panel__fullscreen"
        data-test="diagram-fullscreen"
        role="dialog"
        aria-modal="true"
        aria-label="Fullscreen diagram viewer"
        tabindex="-1"
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
              type="button"
              @click="fitToWidth(fullscreenCanvas)"
            >
              Fit
            </button>
            <button
              class="diagram-panel__tool diagram-panel__tool--wide"
              type="button"
              @click="downloadSvg"
            >
              Download SVG
            </button>
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
        <div
          ref="fullscreenCanvas"
          class="diagram-panel__fullscreen-canvas"
          :class="{ 'diagram-panel__canvas--panning': isPanning }"
          @wheel="handleWheel"
          @pointerdown="startPan"
          @pointermove="onPan"
          @pointerup="endPan"
          @pointerleave="endPan"
        >
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

.diagram-panel__package-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.diagram-panel__package-chip {
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 999px;
  background: rgba(30, 41, 59, 0.7);
  color: #cbd5e1;
  padding: 0.2rem 0.7rem;
  font-size: 0.8125rem;
  cursor: pointer;
  transition: border-color 150ms ease, background-color 150ms ease, color 150ms ease;
}

.diagram-panel__package-chip:hover:not(:disabled) {
  border-color: rgba(147, 197, 253, 0.7);
  color: #f3f4f6;
}

.diagram-panel__package-chip--active {
  background: #2563eb;
  border-color: #2563eb;
  color: #ffffff;
}

.diagram-panel__package-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.diagram-panel__filter-empty {
  margin: 0;
  font-size: 0.875rem;
  color: #fbbf24;
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

.diagram-panel__canvas {
  min-height: 22rem;
  flex: 1;
  overflow: auto;
  border: 1px solid rgba(51, 65, 85, 0.85);
  border-radius: 0.875rem;
  padding: 1rem;
  background: #f8fafc;
  color: #0f172a;
  cursor: grab;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
}

.diagram-panel__canvas--panning {
  cursor: grabbing;
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
  cursor: grab;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
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
