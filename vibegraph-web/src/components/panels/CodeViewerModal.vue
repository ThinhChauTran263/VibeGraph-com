<script setup lang="ts">
/**
 * CodeViewerModal — read-only source viewer for a selected graph node's file.
 *
 * Opens over the graph (Teleported to body) so it reads comfortably on every device:
 * a large centered panel on desktop, a full-screen sheet on phones/tablets. Source is
 * fetched in line windows via `useSourceCode` and syntax-highlighted with a lazily
 * imported highlight.js core (kept out of the initial bundle).
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useSourceCode } from '@/composables/useSourceCode'

/** Minimal shape needed to read a file — satisfied by both GraphNode and ImpactNode. */
interface SourceNode {
  filePath: string
  name: string
  lineNumber?: number | null
}

const props = defineProps<{
  projectId: string
  node: SourceNode
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const {
  status,
  errorMessage,
  content,
  relativePath,
  language,
  totalLines,
  loadedToLine,
  found,
  notServedReason,
  warnings,
  isLoading,
  isLoadingMore,
  hasMore,
  load,
  loadMore,
  reset,
} = useSourceCode()

const dialogRef = ref<HTMLElement | null>(null)
const scrollRef = ref<HTMLElement | null>(null)
const copied = ref(false)

// highlight.js is loaded on demand. `highlightedHtml` holds the rendered markup; until the
// highlighter resolves (or for unknown languages) we fall back to escaped plain text.
const highlightedHtml = shallowRef<string | null>(null)

// Map the backend's language hint to a highlight.js grammar. Anything unmapped renders as
// plain (escaped) text rather than risking a wrong grammar.
const HLJS_LANGUAGES: Record<string, () => Promise<{ default: unknown }>> = {
  java: () => import('highlight.js/lib/languages/java'),
  kotlin: () => import('highlight.js/lib/languages/kotlin'),
  xml: () => import('highlight.js/lib/languages/xml'),
  yaml: () => import('highlight.js/lib/languages/yaml'),
  sql: () => import('highlight.js/lib/languages/sql'),
  properties: () => import('highlight.js/lib/languages/properties'),
  markdown: () => import('highlight.js/lib/languages/markdown'),
  gradle: () => import('highlight.js/lib/languages/gradle'),
}

const lineNumbers = computed(() => {
  const count = content.value ? content.value.split('\n').length : 0
  return Array.from({ length: count }, (_, i) => i + 1)
})

const targetLine = computed(() =>
  typeof props.node.lineNumber === 'number' && props.node.lineNumber > 0
    ? props.node.lineNumber
    : null,
)

const fileName = computed(() => {
  const p = relativePath.value || props.node.filePath || props.node.name
  return p.split(/[\\/]/).pop() || props.node.name
})

async function highlight(): Promise<void> {
  highlightedHtml.value = null
  const code = content.value
  if (!code) return
  const langKey = (language.value || '').toLowerCase()
  const loader = HLJS_LANGUAGES[langKey]
  if (!loader) return // unknown language → plain text fallback in template

  try {
    const [{ default: hljs }, langModule] = await Promise.all([
      import('highlight.js/lib/core'),
      loader(),
    ])
    if (!hljs.getLanguage(langKey)) {
      hljs.registerLanguage(langKey, langModule.default as never)
    }
    highlightedHtml.value = hljs.highlight(code, { language: langKey, ignoreIllegals: true }).value
  } catch {
    highlightedHtml.value = null // fall back to plain text on any failure
  }
}

function scrollToTarget(): void {
  const el = scrollRef.value
  if (!el || !targetLine.value) return
  // Measure a real rendered line so the offset is in pixels (the CSS line-height is in rem,
  // which parseFloat would misread). Gutter and code share the same line box height.
  const lineEl = el.querySelector<HTMLElement>('.code-viewer__gutter li')
  const lineHeight = lineEl?.getBoundingClientRect().height || 20
  el.scrollTop = Math.max(0, (targetLine.value - 3) * lineHeight)
}

async function onLoaded(): Promise<void> {
  await highlight()
  await nextTick()
  scrollToTarget()
}

watch(content, () => {
  void highlight()
})

watch(status, (s) => {
  if (s === 'success') void nextTick(scrollToTarget)
})

async function copyCode(): Promise<void> {
  if (!content.value) return
  try {
    await navigator.clipboard.writeText(content.value)
    copied.value = true
    window.setTimeout(() => (copied.value = false), 1600)
  } catch {
    copied.value = false
  }
}

function onLoadMore(): void {
  void loadMore(props.projectId)
}

function retry(): void {
  void load(props.projectId, props.node.filePath).then(onLoaded)
}

function close(): void {
  emit('close')
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    e.stopPropagation()
    close()
  }
}

onMounted(async () => {
  document.addEventListener('keydown', onKeydown, true)
  // Lock background scroll while the sheet/modal is open.
  document.body.style.overflow = 'hidden'
  await load(props.projectId, props.node.filePath)
  await onLoaded()
  dialogRef.value?.focus()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown, true)
  document.body.style.overflow = ''
  reset()
})
</script>

<template>
  <Teleport to="body">
    <div class="code-viewer" @click.self="close">
      <section
        ref="dialogRef"
        class="code-viewer__dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="`Source code for ${fileName}`"
        tabindex="-1"
      >
        <header class="code-viewer__header">
          <div class="code-viewer__title">
            <span class="code-viewer__file">{{ fileName }}</span>
            <span class="code-viewer__path" :title="relativePath || node.filePath">
              {{ relativePath || node.filePath }}
            </span>
          </div>
          <div class="code-viewer__actions">
            <span v-if="language && found" class="code-viewer__lang">{{ language }}</span>
            <button class="code-viewer__btn" type="button" :disabled="!content" @click="copyCode">
              {{ copied ? 'Copied' : 'Copy' }}
            </button>
            <button
              class="code-viewer__btn code-viewer__btn--icon"
              type="button"
              aria-label="Close source viewer"
              @click="close"
            >
              <span aria-hidden="true">×</span>
            </button>
          </div>
        </header>

        <div class="code-viewer__body">
          <div v-if="isLoading" class="code-viewer__state" role="status">
            <div class="code-viewer__spinner" aria-hidden="true"></div>
            <p>Loading source…</p>
          </div>

          <div
            v-else-if="status === 'error'"
            class="code-viewer__state code-viewer__state--error"
            role="alert"
          >
            <p>{{ errorMessage }}</p>
            <button class="code-viewer__btn" type="button" @click="retry">Retry</button>
          </div>

          <div v-else-if="!found" class="code-viewer__state" role="status">
            <p>{{ notServedReason }}</p>
          </div>

          <div v-else ref="scrollRef" class="code-viewer__scroll">
            <div class="code-viewer__code-row">
              <ol class="code-viewer__gutter" aria-hidden="true">
                <li
                  v-for="n in lineNumbers"
                  :key="n"
                  :class="{ 'code-viewer__gutter-line--target': n === targetLine }"
                >
                  {{ n }}
                </li>
              </ol>
              <pre class="code-viewer__pre"><code
                v-if="highlightedHtml"
                class="hljs"
                v-html="highlightedHtml"
              ></code><code v-else class="hljs">{{ content }}</code></pre>
            </div>

            <div v-if="hasMore || warnings.length" class="code-viewer__more">
              <p v-if="warnings.length" class="code-viewer__warn">{{ warnings.join(' · ') }}</p>
              <template v-if="hasMore">
                <span class="code-viewer__count">
                  Showing {{ loadedToLine }} of {{ totalLines }} lines
                </span>
                <button
                  class="code-viewer__btn"
                  type="button"
                  :disabled="isLoadingMore"
                  @click="onLoadMore"
                >
                  {{ isLoadingMore ? 'Loading…' : 'Load more' }}
                </button>
              </template>
            </div>
          </div>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.code-viewer {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(0px, 4vw, 2.5rem);
  background: rgba(2, 6, 23, 0.72);
  backdrop-filter: blur(4px);
}

.code-viewer__dialog {
  display: flex;
  flex-direction: column;
  width: min(1000px, 100%);
  height: min(86vh, 100%);
  border: 1px solid rgba(96, 165, 250, 0.28);
  border-radius: 1rem;
  background: #0b1120;
  color: #e5e7eb;
  box-shadow: 0 28px 80px rgba(0, 0, 0, 0.55);
  overflow: hidden;
  outline: none;
}

.code-viewer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.6);
}

.code-viewer__title {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.code-viewer__file {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.95rem;
  font-weight: 700;
  color: #f8fafc;
}

.code-viewer__path {
  font-size: 0.75rem;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  direction: rtl;
  text-align: left;
}

.code-viewer__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 0 0 auto;
}

.code-viewer__lang {
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.22);
  color: #bfdbfe;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.code-viewer__btn {
  min-height: 2rem;
  padding: 0 0.7rem;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 0.5rem;
  background: rgba(30, 41, 59, 0.9);
  color: #e5e7eb;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color 150ms ease,
    background-color 150ms ease,
    color 150ms ease;
}

.code-viewer__btn:hover:not(:disabled),
.code-viewer__btn:focus-visible {
  border-color: rgba(96, 165, 250, 0.7);
  background: rgba(37, 99, 235, 0.28);
  outline: none;
}

.code-viewer__btn:disabled {
  opacity: 0.55;
  cursor: default;
}

.code-viewer__btn--icon {
  min-width: 2rem;
  padding: 0;
  font-size: 1.25rem;
  line-height: 1;
}

.code-viewer__body {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.code-viewer__state {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 2rem;
  color: #94a3b8;
  font-size: 0.875rem;
  text-align: center;
}

.code-viewer__state--error p {
  color: #fecaca;
}

.code-viewer__spinner {
  width: 30px;
  height: 30px;
  border: 3px solid rgba(148, 163, 184, 0.25);
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: code-viewer-spin 0.8s linear infinite;
}

@keyframes code-viewer-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .code-viewer__spinner {
    animation-duration: 2.4s;
  }
}

.code-viewer__scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  --code-line-height: 1.45rem;
  background: #0b1120;
}

.code-viewer__code-row {
  display: flex;
  align-items: flex-start;
  min-height: 100%;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.8125rem;
}

.code-viewer__gutter {
  position: sticky;
  left: 0;
  z-index: 1;
  flex: 0 0 auto;
  margin: 0;
  padding: 0.75rem 0.5rem 0.75rem 0;
  list-style: none;
  text-align: right;
  color: #475569;
  background: #0b1120;
  border-right: 1px solid rgba(148, 163, 184, 0.12);
  user-select: none;
}

.code-viewer__gutter li {
  padding: 0 0.6rem 0 0.9rem;
  line-height: var(--code-line-height);
  font-variant-numeric: tabular-nums;
}

.code-viewer__gutter-line--target {
  color: #fbbf24;
  font-weight: 700;
}

.code-viewer__pre {
  flex: 1 1 auto;
  margin: 0;
  padding: 0.75rem 1rem;
  overflow: visible;
}

.code-viewer__pre code {
  display: block;
  white-space: pre;
  line-height: var(--code-line-height);
  background: transparent;
  padding: 0;
  color: #e2e8f0;
}

.code-viewer__more {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem 1rem;
}

.code-viewer__count {
  font-size: 0.75rem;
  color: #94a3b8;
}

.code-viewer__warn {
  width: 100%;
  margin: 0;
  font-size: 0.72rem;
  color: #fcd34d;
  text-align: center;
}

/* Minimal one-dark-ish token palette for the lazily highlighted code. */
.code-viewer__pre :deep(.hljs-keyword),
.code-viewer__pre :deep(.hljs-built_in),
.code-viewer__pre :deep(.hljs-literal) {
  color: #c792ea;
}
.code-viewer__pre :deep(.hljs-string),
.code-viewer__pre :deep(.hljs-meta-string) {
  color: #c3e88d;
}
.code-viewer__pre :deep(.hljs-comment),
.code-viewer__pre :deep(.hljs-quote) {
  color: #5c6370;
  font-style: italic;
}
.code-viewer__pre :deep(.hljs-number),
.code-viewer__pre :deep(.hljs-symbol) {
  color: #f78c6c;
}
.code-viewer__pre :deep(.hljs-title),
.code-viewer__pre :deep(.hljs-title.function_),
.code-viewer__pre :deep(.hljs-section) {
  color: #82aaff;
}
.code-viewer__pre :deep(.hljs-type),
.code-viewer__pre :deep(.hljs-class .hljs-title),
.code-viewer__pre :deep(.hljs-title.class_) {
  color: #ffcb6b;
}
.code-viewer__pre :deep(.hljs-meta),
.code-viewer__pre :deep(.hljs-attr),
.code-viewer__pre :deep(.hljs-attribute) {
  color: #ffcb6b;
}

/* Full-screen sheet on phones and tablets. */
@media (max-width: 48rem) {
  .code-viewer {
    padding: 0;
  }

  .code-viewer__dialog {
    width: 100%;
    height: 100%;
    border: none;
    border-radius: 0;
  }
}
</style>
