import { computed, getCurrentScope, onScopeDispose, ref } from 'vue'
import { ApiError, importApi, projectApi, type Project, type ProjectStatusEvent } from '@/lib/api'
import { useWebSocket, type UseWebSocketReturn } from '@/composables/useWebSocket'
import {
  IMPORT_POLL_INTERVAL_MS,
  IMPORT_STALL_TIMEOUT_MS,
  IMPORT_ABSOLUTE_TIMEOUT_MS,
} from '@/lib/runtimeConfig'

const GITHUB_REPO_URL_PATTERN =
  /^https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+(?:\.git)?\/?$/
const GENERIC_GITHUB_IMPORT_ERROR = 'Import failed. Verify the repository is public and try again.'
const NO_JAVA_FILES_ERROR =
  'This repository contains no .java files. VibeGraph currently analyzes Java projects only.'
const ANALYSIS_FAILED_ERROR =
  'Analysis failed for this repository on the server. The project was not processed — please try again.'
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
  /^Failed to download GitHub tarball after \d+ attempts:/i,
  /^GitHub tarball download failed with HTTP (?:408|429|500|502|503|504) after \d+ attempts$/i,
]
const GITHUB_IMPORT_POLL_INTERVAL_MS = IMPORT_POLL_INTERVAL_MS
// A large repo can analyze for many minutes, so there is no fixed poll cap. Instead we only give
// up when the backend stops making progress (a genuine stall) for this long. A project that keeps
// inching forward — even slowly — never trips this and analyzes to completion. Set generously so a
// heavy final phase that sits at 9x% for several minutes is not mistaken for a stuck backend.
const GITHUB_IMPORT_STALL_TIMEOUT_MS = IMPORT_STALL_TIMEOUT_MS
// Absolute safety ceiling so a pathological backend can't poll forever.
const GITHUB_IMPORT_ABSOLUTE_TIMEOUT_MS = IMPORT_ABSOLUTE_TIMEOUT_MS

export type GitHubImportStatus = 'idle' | 'importing' | 'success' | 'error'

export interface UseGitHubImportOptions {
  /**
   * WebSocket transport for live progress. Defaults to a fresh `useWebSocket`.
   * A test seam: unit tests inject a fake (or omit live updates entirely) so
   * progress can be driven deterministically without a real socket.
   */
  ws?: UseWebSocketReturn
  /**
   * Called the moment the backend accepts the import (202 + ANALYZING), before
   * the terminal wait resolves. Lets the host hand the project to the global
   * import tracker so tracking survives the form unmounting (background import).
   */
  onAccepted?: (project: Project) => void
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

  // Credit exhaustion (402): show the required/available amounts when the backend sent them.
  if (error.code === 'CREDIT_EXHAUSTED') {
    const amounts = error.details ? ` ${error.details}.` : ''
    return `Not enough credits to import this repository.${amounts} Upgrade your plan or wait for the next monthly reset.`
  }

  // A non-Java repository extracts to zero .java files. Show the real reason
  // instead of the generic "verify the repository is public" fallback.
  if (error.code === 'ARCHIVE_EMPTY_ARCHIVE' || /no \.java files/i.test(error.message)) {
    return NO_JAVA_FILES_ERROR
  }

  const message = error.message.trim()
  if (!message) {
    return GENERIC_GITHUB_IMPORT_ERROR
  }

  // 4xx responses carry user-facing messages curated by the backend exception
  // handlers (GithubImportException -> 422, ArchiveImportException -> 400, ...).
  // Surface them verbatim so the user sees the actual reason (oversize quota,
  // unsafe entry, unsupported URL, ...) instead of a misleading generic hint.
  if (error.status >= 400 && error.status < 500) {
    return message
  }

  // Curated 5xx codes (e.g. SERVICE_BUSY) are also written for end users.
  if (error.code === 'SERVICE_BUSY') {
    return message
  }

  // Anything else (5xx internals, non-JSON proxy bodies) stays whitelisted so
  // stack traces and server details never reach the UI.
  return SAFE_ERROR_PATTERNS.some((pattern) => pattern.test(message))
    ? message
    : GENERIC_GITHUB_IMPORT_ERROR
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

async function waitForGitHubAnalysis(
  project: Project,
  onProgress: (value: number) => void,
  isCancelled: () => boolean,
): Promise<Project | null> {
  let lastProgress = project.progress ?? 0

  if (project.status !== 'ANALYZING') {
    onProgress(100)
    return project
  }

  onProgress(lastProgress)

  const startTime = Date.now()
  let lastAdvanceTime = startTime

  // No fixed iteration cap: keep polling as long as the backend keeps advancing. This is what lets
  // a big repo finish (and auto-open its graph) instead of falsely erroring out at ~94%.
  for (;;) {
    // H10: the form unmounts on tab switch — stop polling instead of running orphaned.
    if (isCancelled()) return null
    await delay(GITHUB_IMPORT_POLL_INTERVAL_MS)
    if (isCancelled()) return null
    const latestProject = await projectApi.get(project.id)
    if (isCancelled()) return null

    if (typeof latestProject.progress === 'number') {
      if (latestProject.progress > lastProgress) {
        lastAdvanceTime = Date.now()
      }
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
      throw new ApiError(400, 'Import Failed', ANALYSIS_FAILED_ERROR)
    }

    // Only surface a timeout when the analysis is genuinely stuck (no progress for a long while)
    // or the absolute ceiling is reached — never just because a fixed timer elapsed.
    const now = Date.now()
    if (
      now - lastAdvanceTime >= GITHUB_IMPORT_STALL_TIMEOUT_MS ||
      now - startTime >= GITHUB_IMPORT_ABSOLUTE_TIMEOUT_MS
    ) {
      throw new ApiError(408, 'Import Timeout', timeoutMessage(lastProgress))
    }
  }
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
  /** The 202-accepted project while the terminal wait runs; null otherwise. */
  const acceptedProject = ref<Project | null>(null)
  const progress = ref(0)

  // H10: cancellation token for the polling loop. The import form renders via v-else in the
  // tabbed panel, so switching tabs unmounts it — without this, polling + WebSocket kept
  // running orphaned and kept writing to refs of a dead component.
  let cancelled = false
  function cancel(): void {
    cancelled = true
  }
  if (getCurrentScope()) {
    onScopeDispose(cancel)
  }

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
    acceptedProject.value = null
    progress.value = 0
    cancelled = false

    try {
      const project = await importApi.importGithub(trimmedUrl)
      if (cancelled) return null
      if (project.status === 'ANALYZING') {
        acceptedProject.value = project
        options.onAccepted?.(project)
      }
      const stopLive = project.status === 'ANALYZING' ? startLiveProgress(project.id) : null
      try {
        const analyzedProject = await waitForGitHubAnalysis(project, setProgress, () => cancelled)
        if (cancelled || analyzedProject === null) return null
        progress.value = 100
        status.value = 'success'
        importedProject.value = analyzedProject
        return analyzedProject
      } finally {
        stopLive?.()
      }
    } catch (error: unknown) {
      if (cancelled) return null
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
    acceptedProject.value = null
    progress.value = 0
  }

  return {
    status,
    errorMessage,
    importedProject,
    acceptedProject,
    progress,
    isImporting,
    importGithub,
    cancel,
    reset,
  }
}
