import { computed, ref } from 'vue'
import {
  ApiError,
  fetchFullGraph,
  importApi,
  projectApi,
  type Project,
  type ProjectStatusEvent,
} from '@/lib/api'
import { useWebSocket, type UseWebSocketReturn } from '@/composables/useWebSocket'

const SERVER_UNREACHABLE_ERROR =
  'Cannot reach the server. Make sure the VibeGraph backend is running, then try again.'
const GENERIC_LOCAL_IMPORT_ERROR = 'Import failed. Check the folder path and try again.'
const SAFE_ERROR_PATTERNS = [
  /required/i,
  /directory/i,
  /path/i,
  /allowed/i,
  /not found/i,
  /taking longer than expected/i,
]
const POLL_INTERVAL_MS = 1_000
const MAX_POLLS = 600

export type LocalImportStatus = 'idle' | 'importing' | 'success' | 'error'

export interface UseLocalImportOptions {
  /** Test seam: inject a WebSocket transport so progress can be driven deterministically. */
  ws?: UseWebSocketReturn
}

function getLocalImportError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return SERVER_UNREACHABLE_ERROR
  }
  if (error.status === 0) {
    return SERVER_UNREACHABLE_ERROR
  }
  const message = error.message.trim()
  if (!message) {
    return GENERIC_LOCAL_IMPORT_ERROR
  }
  return SAFE_ERROR_PATTERNS.some((pattern) => pattern.test(message)) ? message : GENERIC_LOCAL_IMPORT_ERROR
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

function timeoutMessage(progress: number): string {
  const pct = Math.round(progress)
  const suffix = pct > 0 && pct < 100 ? ` (reached ${pct}%)` : ''
  return `Analysis is taking longer than expected${suffix}. It keeps running in the background — open the project again shortly to continue.`
}

async function waitForGraphData(project: Project, onProgress: (value: number) => void): Promise<void> {
  for (let attempt = 0; attempt < MAX_POLLS; attempt += 1) {
    const graph = await fetchFullGraph(project.id)
    if (graph.nodes.length > 0 || graph.edges.length > 0) {
      onProgress(100)
      return
    }
    onProgress(98)
    await delay(POLL_INTERVAL_MS)
  }
  throw new ApiError(408, 'Import Timeout', timeoutMessage(98))
}

async function waitForAnalysis(project: Project, onProgress: (value: number) => void): Promise<Project> {
  let lastProgress = project.progress ?? 0

  if (project.status !== 'ANALYZING') {
    onProgress(100)
    return project
  }

  onProgress(lastProgress)

  for (let attempt = 0; attempt < MAX_POLLS; attempt += 1) {
    await delay(POLL_INTERVAL_MS)
    const latestProject = await projectApi.get(project.id)

    if (typeof latestProject.progress === 'number') {
      lastProgress = latestProject.progress
      onProgress(latestProject.progress)
    }

    if (latestProject.status === 'ANALYZED') {
      await waitForGraphData(latestProject, onProgress)
      return latestProject
    }

    if (latestProject.status === 'FAILED') {
      throw new ApiError(400, 'Import Failed', 'Analysis failed. Check the folder contents and try again.')
    }
  }

  throw new ApiError(408, 'Import Timeout', timeoutMessage(lastProgress))
}

export function useLocalImport(options: UseLocalImportOptions = {}) {
  const status = ref<LocalImportStatus>('idle')
  const errorMessage = ref<string | null>(null)
  const importedProject = ref<Project | null>(null)
  const progress = ref(0)

  const isImporting = computed(() => status.value === 'importing')

  // Progress only ever moves forward so a late poll cannot rewind the bar.
  function setProgress(value: number): void {
    const clamped = Math.min(100, Math.max(0, value))
    if (clamped > progress.value) {
      progress.value = clamped
    }
  }

  /** Best-effort live progress over WebSocket; polling still drives the terminal outcome. */
  function startLiveProgress(projectId: string): () => void {
    const socket = options.ws ?? useWebSocket()
    let subscription: { unsubscribe: () => void } | null = null
    let stopped = false

    socket
      .connect()
      .then(() => {
        if (stopped) return
        subscription = socket.subscribe<ProjectStatusEvent>(
          `/topic/projects/${projectId}/status`,
          (event) => {
            if (event.projectId !== projectId) return
            if (typeof event.progress === 'number') {
              setProgress(event.progress)
            }
          },
        )
      })
      .catch(() => {
        // WebSocket unavailable: polling covers it.
      })

    return () => {
      stopped = true
      if (subscription) {
        subscription.unsubscribe()
        subscription = null
      }
      void socket.disconnect()
    }
  }

  async function importLocal(path: string, name?: string): Promise<Project | null> {
    const trimmedPath = path.trim()
    if (!trimmedPath) {
      status.value = 'error'
      errorMessage.value = 'A folder path is required.'
      importedProject.value = null
      return null
    }

    status.value = 'importing'
    errorMessage.value = null
    importedProject.value = null
    progress.value = 0

    try {
      const project = await importApi.importLocal(trimmedPath, name)
      const stopLive = project.status === 'ANALYZING' ? startLiveProgress(project.id) : null
      try {
        const analyzedProject = await waitForAnalysis(project, setProgress)
        progress.value = 100
        status.value = 'success'
        importedProject.value = analyzedProject
        return analyzedProject
      } finally {
        stopLive?.()
      }
    } catch (error: unknown) {
      status.value = 'error'
      errorMessage.value = getLocalImportError(error)
      importedProject.value = null
      return null
    }
  }

  function reset(): void {
    status.value = 'idle'
    errorMessage.value = null
    importedProject.value = null
    progress.value = 0
  }

  return {
    status,
    errorMessage,
    importedProject,
    progress,
    isImporting,
    importLocal,
    reset,
  }
}
