<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, ref, watch } from 'vue'
import { useDiagrams } from '@/composables/useDiagrams'
import type { UmlUseCaseResponse, UmlUseCaseView } from '@/lib/api'
import { renderUmlUseCaseSvg, type UmlUseCaseModel } from '@/lib/umlUseCaseSvg'
import ThemedSelect from '@/components/ui/ThemedSelect.vue'
import LogoSpinner from '@/components/ui/LogoSpinner.vue'

const props = defineProps<{
  projectId: string
}>()

const BASE_RENDER_SCALE = 2.2
const MIN_ZOOM = 0.2
const MAX_ZOOM = 8
const WHEEL_ZOOM_FACTOR = 1.12

// -1 = the full diagram; >=0 selects a per-actor / per-domain projection from `umlViews`.
const selectedViewIndex = ref(-1)
const renderedSvg = ref('')
const renderError = ref<string | null>(null)
const diagramZoom = ref(1)
const isFullscreen = ref(false)
const isPanning = ref(false)
const mainCanvas = ref<HTMLElement | null>(null)
const fullscreenCanvas = ref<HTMLElement | null>(null)
const fullscreenDialog = ref<HTMLElement | null>(null)
const { status, diagram, errorMessage, isLoading, isStale, loadUmlUseCaseDiagram, reset } =
  useDiagrams()
let renderSeq = 0

const mermaidSource = computed(() => diagram.value?.mermaidSyntax?.trim() ?? '')
const hasDiagramContent = computed(
  () => status.value === 'success' && mermaidSource.value.length > 0,
)
const zoomPercent = computed(() => `${Math.round(diagramZoom.value * 100)}%`)
const diagramStageStyle = computed(() => ({
  transform: `scale(${Number((diagramZoom.value * BASE_RENDER_SCALE).toFixed(2))})`,
}))

const warnings = computed<string[]>(() => {
  const current = diagram.value
  if (!current) return []
  return 'warnings' in current && Array.isArray(current.warnings) ? current.warnings : []
})
const notesSummary = computed(() => {
  const count = warnings.value.length
  return count === 1 ? '1 warning' : `${count} warnings`
})

// True when the model was derived from the class layer (service/controller/entity methods) because
// the project exposes no HTTP endpoints. Drives an accurate caption instead of the endpoint wording.
const derivedFromClassLayer = computed<boolean>(() =>
  warnings.value.some((w) => w.toLowerCase().includes('no http endpoints')),
)

// Per-actor / per-domain projections of the same canonical UML model (R4). Drives the view selector;
// empty when the backend returned no views (older payloads / non-UML), so the selector stays hidden.
//
// The loaded value is deep-readonly; the renderer only reads it, so we view it through a plain
// (mutable-typed) shape via a single safe cast and derive everything from there.
const umlResponse = computed<UmlUseCaseResponse | null>(() => {
  const current = diagram.value
  return current as unknown as UmlUseCaseResponse
})

const umlViews = computed<UmlUseCaseView[]>(() => {
  const views = umlResponse.value?.views
  return Array.isArray(views) ? views : []
})
const viewOptions = computed(() => [
  { value: -1, label: 'Full diagram' },
  ...umlViews.value.map((view, index) => ({
    value: index,
    label: `${view.viewType === 'actor' ? 'Actor' : 'Domain'}: ${view.title}`,
  })),
])

// The model actually drawn: the full diagram (index -1) or the selected projection. Switching views
// is pure client-side filtering of data already in the response — never a re-fetch or re-inference.
const currentUmlModel = computed<UmlUseCaseModel | null>(() => {
  const uml = umlResponse.value
  if (!uml) return null
  const views = umlViews.value
  const idx = selectedViewIndex.value
  const scope = idx >= 0 && idx < views.length ? views[idx] : uml
  if (!scope) return null
  return {
    systemName: uml.systemName,
    actors: scope.actors,
    useCases: scope.useCases,
    relations: scope.relations,
  }
})

async function refresh(force = false): Promise<void> {
  clearRenderedDiagram()
  await loadUmlUseCaseDiagram(props.projectId, 'detailed', { force })
  // Re-render explicitly after a refresh. The `mermaidSource` watch only fires when the source
  // STRING changes, so refreshing the same tab (identical diagram) would otherwise leave the
  // viewer blank — clearRenderedDiagram() emptied the SVG and the watch never re-ran. Rendering
  // here guarantees a repaint whether or not the source changed.
  await nextTick()
  // A fresh load always starts on the full diagram; views are an explicit user choice.
  selectedViewIndex.value = -1
  renderCurrentUml()
}

/** Render the currently-selected UML model (full diagram or a per-actor/per-domain projection). */
function renderCurrentUml(): void {
  const model = currentUmlModel.value
  if (!model) return
  renderUmlSvg(model)
}

function renderUmlSvg(model: UmlUseCaseModel): void {
  const seq = ++renderSeq
  try {
    const svg = renderUmlUseCaseSvg(model)
    if (seq !== renderSeq) return
    renderedSvg.value = svg
    renderError.value = null
  } catch (err) {
    if (seq !== renderSeq) return
    renderedSvg.value = ''
    renderError.value =
      err instanceof Error && err.message ? err.message : 'Failed to render UML diagram.'
  }
}

function clearRenderedDiagram(): void {
  renderSeq++
  renderedSvg.value = ''
  renderError.value = null
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
function zoomAtPointer(
  canvas: HTMLElement | null,
  factor: number,
  clientX?: number,
  clientY?: number,
): void {
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

/** Natural pixel size of the diagram from its viewBox (preferred) or width/height attributes. */
function svgPixelSize(svg: string): { width: number; height: number } {
  const el = new DOMParser().parseFromString(svg, 'image/svg+xml').documentElement
  const viewBox = el.getAttribute('viewBox')
  if (viewBox) {
    const parts = viewBox.split(/[\s,]+/).map(Number)
    const w = parts[2]
    const h = parts[3]
    if (w !== undefined && h !== undefined && w > 0 && h > 0) return { width: w, height: h }
  }
  const width = Number.parseFloat(el.getAttribute('width') ?? '')
  const height = Number.parseFloat(el.getAttribute('height') ?? '')
  if (width > 0 && height > 0) return { width, height }
  return { width: 1200, height: 800 }
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('Failed to rasterize the diagram SVG'))
    image.src = src
  })
}

/**
 * Download the diagram as a PNG by rasterizing the rendered SVG onto a canvas. Uses the SVG's
 * natural viewBox size (independent of the on-screen zoom) and a 2x scale for a crisp image, on a
 * white background since Mermaid's default theme draws dark strokes/text with no fill of its own.
 */
async function downloadPng(): Promise<void> {
  const source = renderedSvg.value
  if (!source) return

  const withNamespace = source.includes('xmlns=')
    ? source
    : source.replace('<svg', '<svg xmlns="http://www.w3.org/2000/svg"')

  const { width, height } = svgPixelSize(withNamespace)
  const scale = 2
  const blob = new Blob([withNamespace], { type: 'image/svg+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  try {
    const image = await loadImage(url)
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(width * scale))
    canvas.height = Math.max(1, Math.round(height * scale))
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(image, 0, 0, canvas.width, canvas.height)

    const anchor = document.createElement('a')
    anchor.href = canvas.toDataURL('image/png')
    anchor.download = 'uml-diagram.png'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
  } finally {
    URL.revokeObjectURL(url)
  }
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

// Re-render when the user switches between the full diagram and a per-actor/per-domain view.
// Pure client-side: no fetch, just redraw the projection already present in the loaded response.
watch(selectedViewIndex, () => {
  if (status.value === 'success') {
    resetZoom()
    renderCurrentUml()
  }
})

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
  <section class="diagram-panel" aria-labelledby="diagram-panel-heading">
    <header class="diagram-panel__header">
      <div>
        <p class="diagram-panel__eyebrow">UML use case</p>
        <h2 id="diagram-panel-heading">As-Built Use Case</h2>
      </div>
      <div class="diagram-panel__header-actions">
        <div
          v-if="umlViews.length > 0"
          class="diagram-panel__views"
          data-test="diagram-view-select-wrap"
        >
          <label class="diagram-panel__views-label" for="diagram-view-select">View</label>
          <ThemedSelect
            v-model="selectedViewIndex"
            class="diagram-panel__views-select"
            input-id="diagram-view-select"
            name="diagramView"
            data-test="diagram-view-select"
            :options="viewOptions"
            aria-label="Diagram view"
            size="sm"
            :disabled="isLoading"
          />
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
      </div>
    </header>

    <details class="diagram-panel__notes" data-test="diagram-notes">
      <summary class="diagram-panel__notes-summary">
        <span>Diagram notes</span>
        <span v-if="warnings.length > 0" class="diagram-panel__notes-count">
          {{ notesSummary }}
        </span>
      </summary>
      <p class="diagram-panel__caption" data-test="diagram-asbuilt-caption">
        <strong>As-Built Use Case View</strong> —
        <template v-if="derivedFromClassLayer">
          reverse-engineered from the service/controller class layer and their public methods (no
          HTTP endpoints were found), using OMG UML 2.5 use-case notation. It reflects the system's
          implemented capabilities for design-vs-code verification, not a hand-authored
          business-intent model.
        </template>
        <template v-else>
          reverse-engineered from the source (controllers + Spring Security), using OMG UML 2.5
          use-case notation. It reflects the system's implemented capabilities for design-vs-code
          verification, not a hand-authored business-intent model.
        </template>
      </p>
      <ul
        v-if="warnings.length > 0"
        class="diagram-panel__warnings"
        data-test="diagram-warnings"
        aria-label="Inference warnings"
      >
        <li v-for="(warning, index) in warnings" :key="index" class="diagram-panel__warning">
          {{ warning }}
        </li>
      </ul>
    </details>

    <div v-if="isLoading" class="diagram-panel__loading" role="status">
      <LogoSpinner :size="96" />
      <p>Loading diagram…</p>
    </div>

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
          data-test="diagram-download-png"
          type="button"
          @click="downloadPng"
        >
          Download PNG
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

    <p v-else-if="status === 'success'" class="diagram-panel__empty">
      No diagram content returned.
    </p>

    <p v-else class="diagram-panel__hint">
      Choose a diagram type to load generated diagram output.
    </p>

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
            <h2>As-Built Use Case</h2>
          </div>
          <div class="diagram-panel__fullscreen-actions">
            <button
              class="diagram-panel__tool"
              type="button"
              aria-label="Zoom out"
              @click="zoomOut"
            >
              -
            </button>
            <button
              class="diagram-panel__tool diagram-panel__tool--wide"
              type="button"
              @click="resetZoom"
            >
              {{ zoomPercent }}
            </button>
            <button class="diagram-panel__tool" type="button" aria-label="Zoom in" @click="zoomIn">
              +
            </button>
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
              @click="downloadPng"
            >
              Download PNG
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
  gap: 0.75rem;
  border: 1px solid rgba(59, 130, 246, 0.24);
  border-radius: 0.75rem;
  padding: 0.75rem;
  background: rgba(15, 23, 42, 0.94);
  color: #e5e7eb;
  box-shadow: 0 18px 52px rgba(15, 23, 42, 0.32);
}

.diagram-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.diagram-panel__header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
}

.diagram-panel__header h2,
.diagram-panel__eyebrow {
  margin: 0;
}

.diagram-panel__eyebrow {
  color: #93c5fd;
  font-size: 0.6875rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.diagram-panel__header h2 {
  margin-top: 0.125rem;
  font-size: 1rem;
  line-height: 1.2;
}

.diagram-panel__refresh {
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 0.625rem;
  background: rgba(30, 41, 59, 0.86);
  color: #e5e7eb;
  cursor: pointer;
  font-weight: 600;
}

.diagram-panel__refresh {
  min-height: 2.25rem;
  padding: 0 0.75rem;
}

.diagram-panel__refresh:hover:not(:disabled) {
  border-color: rgba(147, 197, 253, 0.7);
}

.diagram-panel__refresh:disabled {
  opacity: 0.6;
  cursor: progress;
}

.diagram-panel__status,
.diagram-panel__hint,
.diagram-panel__empty {
  color: #9ca3af;
  font-size: 0.875rem;
}

/* Centered loading feedback so the spinner + label sit in the middle of the
   diagram area instead of clinging to the top-left. */
.diagram-panel__loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  min-height: 14rem;
  color: #9ca3af;
  font-size: 0.875rem;
}

.diagram-panel__loading p {
  margin: 0;
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
  min-height: 26rem;
  flex: 1;
  flex-direction: column;
  gap: 0.5rem;
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
  min-height: 28rem;
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

.diagram-panel__views {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
}

.diagram-panel__views-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: #9fb0c7;
}

.diagram-panel__views-select {
  flex: 0 1 auto;
  width: min(18rem, 48vw);
  max-width: 18rem;
}

.diagram-panel__views-select :deep(.vg-select__trigger),
.diagram-panel__views-select :deep(.vg-select__menu) {
  background: #0f172a;
  color: #e8edf6;
}

.diagram-panel__views-select :deep(.vg-select__trigger) {
  border-color: rgba(148, 163, 184, 0.32);
  font-size: 0.8125rem;
}

.diagram-panel__caption {
  margin: 0.625rem 0 0;
  padding: 0.55rem 0.7rem;
  font-size: 0.8125rem;
  line-height: 1.45;
  color: #9fb0c7;
  background: rgba(7, 11, 22, 0.5);
  border-left: 3px solid var(--vg-blue-bright, #60a5fa);
  border-radius: 0 0.5rem 0.5rem 0;
}

.diagram-panel__notes {
  border: 1px solid rgba(51, 65, 85, 0.8);
  border-radius: 0.625rem;
  background: rgba(7, 11, 22, 0.36);
}

.diagram-panel__notes-summary {
  display: flex;
  min-height: 2rem;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.4rem 0.65rem;
  color: #cbd5e1;
  cursor: pointer;
  font-size: 0.8125rem;
  font-weight: 700;
  list-style: none;
}

.diagram-panel__notes-summary::-webkit-details-marker {
  display: none;
}

.diagram-panel__notes-summary::before {
  content: '>';
  color: #93c5fd;
  font-size: 0.75rem;
  transition: transform 150ms ease;
}

.diagram-panel__notes[open] .diagram-panel__notes-summary::before {
  transform: rotate(90deg);
}

.diagram-panel__notes-summary span:first-child {
  margin-right: auto;
}

.diagram-panel__notes-count {
  padding: 0.15rem 0.45rem;
  border: 1px solid rgba(234, 179, 8, 0.35);
  border-radius: 999px;
  color: #fde68a;
  font-size: 0.75rem;
  font-weight: 700;
}

.diagram-panel__notes:focus-within {
  border-color: rgba(96, 165, 250, 0.65);
}

.diagram-panel__caption strong {
  color: #e8edf6;
}

.diagram-panel__warnings {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  max-height: 5.75rem;
  margin: 0.625rem;
  overflow: auto;
  padding: 0.625rem 0.75rem 0.625rem 1.5rem;
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
