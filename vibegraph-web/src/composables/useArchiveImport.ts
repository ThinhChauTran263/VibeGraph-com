/**
 * useArchiveImport - composable that drives the archive upload flow.
 *
 * Responsibilities:
 *   - Track UI states: idle | uploading | analyzing | success | error.
 *   - Run client-side validation before the upload.
 *   - Sync path (`uploadArchive`): call `importApi.uploadArchive` and surface
 *     the terminal `Project`. Behavior unchanged from the baseline.
 *   - Async path (`uploadArchiveAsync`): call `importApi.uploadArchiveAsync`,
 *     receive an `ANALYZING` project, subscribe to the project status topic,
 *     and resolve when `ANALYZED` / fail on `FAILED`.
 *   - Translate `ApiError` instances into a human-readable message.
 *
 * Note: pure UI state. Persisting the imported project into the
 * `useProjectStore` and navigation are the caller's responsibility, so the
 * composable stays usable from any view without coupling to routing.
 */

import { computed, ref } from 'vue'
import { ApiError, importApi, projectApi, type Project, type ProjectStatusEvent } from '@/lib/api'
import { validateArchiveFile } from '@/lib/archiveUpload'
import { useWebSocket, type UseWebSocketReturn } from '@/composables/useWebSocket'

export type ArchiveImportStatus = 'idle' | 'uploading' | 'analyzing' | 'success' | 'error'

/** Default cadence for the polling fallback (ms). */
const DEFAULT_POLL_INTERVAL_MS = 2000
/** Default watchdog: give up waiting for a terminal status after this long (ms). */
const DEFAULT_ANALYSIS_TIMEOUT_MS = 120_000

export interface UseArchiveImportOptions {
  /**
   * WebSocket transport for async progress. Defaults to a fresh `useWebSocket`.
   * A test seam: unit tests inject a fake to drive status events deterministically.
   */
  ws?: UseWebSocketReturn
  /**
   * Polling fallback cadence in ms. The poll runs in parallel with the
   * WebSocket so a missed/raced terminal event is still caught.
   */
  pollIntervalMs?: number
  /**
   * Watchdog timeout in ms. If no terminal status (ANALYZED/FAILED) is seen
   * within this window, the import fails with a clear timeout error instead of
   * hanging forever.
   */
  analysisTimeoutMs?: number
  /**
   * Status poll function. Defaults to `projectApi.get`. Test seam so unit
   * tests can drive poll results without mocking the module.
   */
  poll?: (projectId: string) => Promise<Project>
}

export function useArchiveImport(options: UseArchiveImportOptions = {}) {
  const status = ref<ArchiveImportStatus>('idle')
  const errorMessage = ref<string | null>(null)
  const importedProject = ref<Project | null>(null)
  const progress = ref(0)

  // Only constructed if/when async is used, but allow injection for tests.
  const ws = options.ws
  const pollIntervalMs = options.pollIntervalMs ?? DEFAULT_POLL_INTERVAL_MS
  const analysisTimeoutMs = options.analysisTimeoutMs ?? DEFAULT_ANALYSIS_TIMEOUT_MS
  const pollFn = options.poll ?? ((id: string) => projectApi.get(id))

  const isUploading = computed(() => status.value === 'uploading')
  const isAnalyzing = computed(() => status.value === 'analyzing')
  const isBusy = computed(() => status.value === 'uploading' || status.value === 'analyzing')

  let statusSubscription: { unsubscribe: () => void } | null = null
  let activeWs: UseWebSocketReturn | null = null
  let pollTimer: ReturnType<typeof setInterval> | null = null
  let watchdogTimer: ReturnType<typeof setTimeout> | null = null

  function clearTimers(): void {
    if (pollTimer !== null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    if (watchdogTimer !== null) {
      clearTimeout(watchdogTimer)
      watchdogTimer = null
    }
  }

  function cleanupWs(): void {
    if (statusSubscription) {
      statusSubscription.unsubscribe()
      statusSubscription = null
    }
    if (activeWs) {
      void activeWs.disconnect()
      activeWs = null
    }
  }

  function reset(): void {
    clearTimers()
    cleanupWs()
    status.value = 'idle'
    errorMessage.value = null
    importedProject.value = null
    progress.value = 0
  }

  /** Shared pre-flight validation. Returns false (and sets error state) on failure. */
  function validate(name: string, file: File): boolean {
    const trimmedName = name.trim()
    if (trimmedName.length === 0) {
      status.value = 'error'
      errorMessage.value = 'Project name is required.'
      importedProject.value = null
      return false
    }

    const validationError = validateArchiveFile(file)
    if (validationError) {
      status.value = 'error'
      errorMessage.value = validationError.message
      importedProject.value = null
      return false
    }
    return true
  }

  /**
   * Validate and upload an archive synchronously.
   *
   * Returns the imported `Project` on success, or `null` on failure.
   * The caller can also read `status` / `errorMessage` reactively.
   */
  async function uploadArchive(name: string, file: File): Promise<Project | null> {
    if (!validate(name, file)) return null

    status.value = 'uploading'
    errorMessage.value = null
    importedProject.value = null
    progress.value = 0

    try {
      const project = await importApi.uploadArchive(name.trim(), file)
      status.value = 'success'
      progress.value = project.progress ?? 100
      importedProject.value = project
      return project
    } catch (err) {
      status.value = 'error'
      errorMessage.value = toUserMessage(err)
      return null
    }
  }

  /**
   * Validate and upload an archive asynchronously.
   *
   * Flow: POST `?async=true` -> `202 ANALYZING` project -> track terminal
   * status via two parallel channels:
   *   1. WebSocket subscription to `/topic/projects/{id}/status` (live push).
   *   2. Polling `GET /api/projects/{id}` on an interval (catches missed/raced
   *      events, and works even if the WebSocket never connects).
   * A watchdog timeout guards against hanging forever.
   *
   * Resolves with the final `Project` on ANALYZED, or `null` on FAILED/timeout.
   * The WebSocket failing is no longer fatal on its own: polling covers it.
   */
  async function uploadArchiveAsync(name: string, file: File): Promise<Project | null> {
    if (!validate(name, file)) return null

    status.value = 'uploading'
    errorMessage.value = null
    importedProject.value = null
    progress.value = 0

    let accepted: Project
    try {
      accepted = await importApi.uploadArchiveAsync(name.trim(), file)
    } catch (err) {
      status.value = 'error'
      errorMessage.value = toUserMessage(err)
      return null
    }

    // Upload accepted (202). Now track progress over WebSocket + polling.
    importedProject.value = accepted
    progress.value = accepted.progress ?? 0
    status.value = 'analyzing'

    return trackAnalysis(accepted)
  }

  function trackAnalysis(accepted: Project): Promise<Project | null> {
    return new Promise<Project | null>((resolve) => {
      let settled = false

      const finishSuccess = (finalProject: Project): void => {
        if (settled) return
        settled = true
        clearTimers()
        cleanupWs()
        importedProject.value = finalProject
        status.value = 'success'
        progress.value = finalProject.progress ?? 100
        resolve(finalProject)
      }

      const finishError = (message: string): void => {
        if (settled) return
        settled = true
        clearTimers()
        cleanupWs()
        status.value = 'error'
        errorMessage.value = message
        resolve(null)
      }

      // Apply a status snapshot from either channel (WS event or poll result).
      // Returns true if a terminal state was reached.
      const applyStatus = (
        statusValue: string,
        progressValue: number | undefined,
        message: string | null,
      ): boolean => {
        if (typeof progressValue === 'number') {
          progress.value = progressValue
        }
        if (statusValue === 'ANALYZED') {
          finishSuccess({
            ...accepted,
            status: 'ANALYZED',
            progress: progressValue ?? 100,
          })
          return true
        }
        if (statusValue === 'FAILED') {
          finishError(message ?? 'Analysis failed on the server.')
          return true
        }
        return false
      }

      // --- Watchdog timeout ---
      watchdogTimer = setTimeout(() => {
        finishError('Analysis timed out. The server did not report completion in time.')
      }, analysisTimeoutMs)

      // --- Polling fallback (parallel to WS) ---
      const pollOnce = async (): Promise<void> => {
        if (settled) return
        try {
          const project = await pollFn(accepted.id)
          applyStatus(project.status, project.progress, null)
        } catch {
          // Transient poll failure: ignore and let the next tick / WS / watchdog handle it.
        }
      }
      pollTimer = setInterval(() => {
        void pollOnce()
      }, pollIntervalMs)

      // --- WebSocket live channel ---
      const onEvent = (event: ProjectStatusEvent): void => {
        // Ignore events for other projects sharing the connection.
        if (event.projectId !== accepted.id) return
        applyStatus(event.status, event.progress, event.message)
      }

      const socket = ws ?? useWebSocket()
      activeWs = socket
      socket
        .connect()
        .then(() => {
          if (settled) return
          statusSubscription = socket.subscribe<ProjectStatusEvent>(
            `/topic/projects/${accepted.id}/status`,
            onEvent,
          )
        })
        .catch(() => {
          // WebSocket failed: not fatal. Polling + watchdog still drive the
          // outcome. Surface a non-blocking note only if we have no other info.
          activeWs = null
          if (!settled) {
            // Kick an immediate poll so we don't wait a full interval.
            void pollOnce()
          }
        })

      // Kick an immediate poll right away to catch a very fast backend.
      void pollOnce()
    })
  }

  return {
    status,
    progress,
    isUploading,
    isAnalyzing,
    isBusy,
    errorMessage,
    importedProject,
    reset,
    uploadArchive,
    uploadArchiveAsync,
  }
}

function toUserMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 413) {
      return 'The archive exceeds the account storage quota or the server safety limit.'
    }
    if (err.status === 0 || err.status >= 500) {
      return err.message || 'The server is unavailable. Please try again.'
    }
    return err.message || `Upload failed (HTTP ${err.status}).`
  }
  if (err instanceof Error) {
    return err.message || 'Upload failed.'
  }
  return 'Upload failed.'
}
