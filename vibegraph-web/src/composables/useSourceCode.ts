/**
 * Source-code reading composable.
 *
 * Wraps `graphApi.getSource` with explicit request state and windowed pagination:
 * the backend serves a bounded slice per call (capped lines/bytes), so large files
 * are read in successive line windows that this composable appends together.
 *
 * Backend contract: GET /api/projects/{projectId}/source?path=&startLine=&endLine=
 * returns `SourceContent` confined to the project source root (see SourceFileService).
 */
import { computed, readonly, ref } from 'vue'
import { ApiError, graphApi, type SourceContent } from '@/lib/api'

export type SourceStatus = 'idle' | 'loading' | 'loadingMore' | 'success' | 'error'

/** Lines fetched per window. The server caps its own window; this is just our request hint. */
const WINDOW_LINES = 300

function mapError(err: unknown): string {
  if (err instanceof ApiError) return err.message || `Request failed (${err.status}).`
  if (err instanceof Error && err.message) return err.message
  return 'Failed to load source code.'
}

export function useSourceCode() {
  const status = ref<SourceStatus>('idle')
  const errorMessage = ref<string | null>(null)

  // Accumulated content across windows, plus metadata from the latest response.
  const content = ref('')
  const relativePath = ref('')
  const language = ref('')
  const totalLines = ref(0)
  const loadedToLine = ref(0)
  const found = ref(true)
  const notServedReason = ref<string | null>(null)
  const warnings = ref<string[]>([])

  // Identifies the file currently being read so a late response for a previous file is dropped.
  let activePath = ''
  // Monotonic token guarding against out-of-order / stale responses.
  let seq = 0

  const isLoading = computed(() => status.value === 'loading')
  const isLoadingMore = computed(() => status.value === 'loadingMore')
  const hasMore = computed(() => found.value && loadedToLine.value < totalLines.value)

  function applyWindow(part: SourceContent, append: boolean): void {
    relativePath.value = part.relativePath
    language.value = part.language || 'plaintext'
    totalLines.value = part.totalLines
    found.value = part.found
    warnings.value = part.warnings ?? []

    if (!part.found) {
      content.value = ''
      loadedToLine.value = 0
      notServedReason.value = part.truncationReason || 'This file cannot be displayed as source.'
      return
    }

    notServedReason.value = null
    content.value = append && content.value ? `${content.value}\n${part.content}` : part.content
    loadedToLine.value = Math.max(loadedToLine.value, part.endLine)
  }

  function reset(): void {
    seq++
    activePath = ''
    status.value = 'idle'
    errorMessage.value = null
    content.value = ''
    relativePath.value = ''
    language.value = ''
    totalLines.value = 0
    loadedToLine.value = 0
    found.value = true
    notServedReason.value = null
    warnings.value = []
  }

  /** Load the first window (lines 1..WINDOW_LINES) of a file. */
  async function load(projectId: string, filePath: string): Promise<void> {
    if (!projectId || !filePath) return
    const token = ++seq
    activePath = filePath
    status.value = 'loading'
    errorMessage.value = null
    content.value = ''
    loadedToLine.value = 0

    try {
      const part = await graphApi.getSource(projectId, filePath, 1, WINDOW_LINES)
      if (token !== seq) return
      applyWindow(part, false)
      status.value = 'success'
    } catch (err) {
      if (token !== seq) return
      content.value = ''
      errorMessage.value = mapError(err)
      status.value = 'error'
    }
  }

  /** Append the next window for the currently-loaded file. */
  async function loadMore(projectId: string): Promise<void> {
    if (!hasMore.value || status.value === 'loadingMore' || !activePath) return
    const token = seq // do not bump: loadMore continues the same logical load
    const start = loadedToLine.value + 1
    const end = start + WINDOW_LINES - 1
    status.value = 'loadingMore'
    try {
      const part = await graphApi.getSource(projectId, activePath, start, end)
      if (token !== seq) return
      applyWindow(part, true)
      status.value = 'success'
    } catch (err) {
      if (token !== seq) return
      errorMessage.value = mapError(err)
      status.value = 'error'
    }
  }

  return {
    status: readonly(status),
    errorMessage: readonly(errorMessage),
    content: readonly(content),
    relativePath: readonly(relativePath),
    language: readonly(language),
    totalLines: readonly(totalLines),
    loadedToLine: readonly(loadedToLine),
    found: readonly(found),
    notServedReason: readonly(notServedReason),
    warnings: readonly(warnings),
    isLoading,
    isLoadingMore,
    hasMore,
    load,
    loadMore,
    reset,
  }
}
