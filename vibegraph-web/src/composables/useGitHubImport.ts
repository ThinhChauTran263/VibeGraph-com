import { computed, ref } from 'vue'
import {
  ApiError,
  importApi,
  projectApi,
  type Project,
  type ProjectStatusEvent,
} from '@/lib/api'
import { useWebSocket, type UseWebSocketReturn } from '@/composables/useWebSocket'

const GITHUB_REPO_URL_PATTERN = /^https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+(?:\.git)?\/?$/
const GENERIC_GITHUB_IMPORT_ERROR = 'Import failed. Verify the repository is public and try again.'
const SERVER_UNREACHABLE_ERROR =
  'Cannot reach the server. Make sure the VibeGraph backend is running, then try again.'
const SAFE_ERROR_PATTERNS = [
  /required/i,
  /must match/i,
  /public/i,
  /private/i,
  /not found/i,
  /too large/i,
  /rate limit/i,
  /still analyzing/i,
  /taking longer than expected/i,
]
const GITHUB_IMPORT_POLL_INTERVAL_MS = 1_000
const GITHUB_IMPORT_MAX_POLLS = 180

export type GitHubImportStatus = 'idle' | 'importing' | 'success' | 'error'

export interface UseGitHubImportOptions {
  /**
   * WebSocket transport for live progress. Defaults to a fresh `useWebSocket`.
   * A test seam: unit tests inject a fake (or omit live updates entirely) so
   * progress can be driven deterministically without a real socket.
   */
  ws?: UseWebSocketReturn
}

function getGitHubImportError(error: unknown): string {
  // A failed fetch (backend down, DNS/CORS failure) rejects with a TypeError, not an
  // ApiError. Don't blame the repository for what is actually a connectivity problem.
  if (!(error instanceof ApiError)) {
    return SERVER_UNREACHABLE_ERROR
  }

  // status 0 indicates a network-level failure surfaced as an ApiError.
  if (error.status === 0) {
    return SERVER_UNREACHABLE_ERROR
  }

  const message = error.message.trim()
  if (!message) {
    return GENERIC_GITHUB_IMPORT_ERROR
  }

  return SAFE_ERROR_PATTERNS.some((pattern) => pattern.test(message)) ? message : GENERIC_GITHUB_IMPORT_ERROR
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

/**
 * Builds a human-readable timeout message that includes how far the analysis
 * had progressed, so the user understands the import is advancing (not stuck).
 */
function timeoutMessage(progress: number): string {
  const pct = Math.round(progress)
  const suffix = pct > 0 && pct < 100 ? ` (reached ${pct}%)` : ''
  return `Analysis is taking longer than expected${suffix}. It keeps running in the background — open the project again shortly to continue.`
}

async function waitForGitHubAnalysis(project: Project, onProgress: (value: number) => void): Promise<Project> {
  let lastProgress = project.progress ?? 0

  if (project.status !== 'ANALYZING') {
    onProgress(100)
    return project
  }

  onProgress(lastProgress)

  for (let attempt = 0; attempt < GITHUB_IMPORT_MAX_POLLS; attempt += 1) {
    await delay(GITHUB_IMPORT_POLL_INTERVAL_MS)
    const latestProject = await projectApi.get(project.id)

    if (typeof latestProject.progress === 'number') {
      lastProgress = latestProject.progress
      onProgress(latestProject.progress)
    }

    if (latestProject.status === 'ANALYZED') {
      // ANALYZED is terminal success. Do NOT additionally wait for nodes/edges > 0 — a valid repo
      // that parses to an empty graph would otherwise loop to a false timeout.
      onProgress(100)
      return latestProject
    }

    if (latestProject.status === 'FAILED') {
      throw new ApiError(400, 'Import Failed', 'Import failed. Verify the repository is public and try again.')
    }
  }

  throw new ApiError(408, 'Import Timeout', timeoutMessage(lastProgress))
}

export function validateGitHubRepoUrl(url: string): string | null {
  const trimmed = url.trim()
  if (!trimmed) {
    return 'GitHub repository URL is required.'
  }

  if (!GITHUB_REPO_URL_PATTERN.test(trimmed)) {
    return 'URL must match https://github.com/{owner}/{repo}.'
  }

  return null
}

export function useGitHubImport(options: UseGitHubImportOptions = {}) {
  const status = ref<GitHubImportStatus>('idle')
  const errorMessage = ref<string | null>(null)
  const importedProject = ref<Project | null>(null)
  const progress = ref(0)

  const isImporting = computed(() => status.value === 'importing')

  // Progress only ever moves forward, so a slow/late backend poll can't make the
  // bar jump backwards and look broken.
  function setProgress(value: number): void {
    const clamped = Math.min(100, Math.max(0, value))
    if (clamped > progress.value) {
      progress.value = clamped
    }
  }

  /**
   * Opens a best-effort live progress channel for an analyzing project.
   *
   * Polling in `waitForGitHubAnalysis` still drives the terminal outcome; this
   * WebSocket only pushes finer-grained `progress` updates between polls. A
   * connection failure is therefore non-fatal — polling covers it. Returns a
   * teardown function that unsubscribes and closes the socket.
   */
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
        // WebSocket unavailable: polling + watchdog still drive progress/outcome.
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

  async function importGithub(url: string): Promise<Project | null> {
    const trimmedUrl = url.trim()
    const validationError = validateGitHubRepoUrl(trimmedUrl)
    if (validationError) {
      status.value = 'error'
      errorMessage.value = validationError
      importedProject.value = null
      return null
    }

    status.value = 'importing'
    errorMessage.value = null
    importedProject.value = null
    progress.value = 0

    try {
      const project = await importApi.importGithub(trimmedUrl)
      const stopLive = project.status === 'ANALYZING' ? startLiveProgress(project.id) : null
      try {
        const analyzedProject = await waitForGitHubAnalysis(project, setProgress)
        progress.value = 100
        status.value = 'success'
        importedProject.value = analyzedProject
        return analyzedProject
      } finally {
        stopLive?.()
      }
    } catch (error: unknown) {
      status.value = 'error'
      errorMessage.value = getGitHubImportError(error)
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
    importGithub,
    reset,
  }
}
