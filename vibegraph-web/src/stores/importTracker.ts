import { defineStore } from 'pinia'
import { ref } from 'vue'
import i18n from '@/language'
import { projectApi, type Project, type ProjectStatusEvent } from '@/lib/api'
import { useWebSocket, type UseWebSocketReturn } from '@/composables/useWebSocket'
import { useToasts } from '@/stores/toasts'
import { useProjectStore } from '@/stores/project'
import { toAccountProject, useAccountStore } from '@/stores/account'

/** Poll cadence for the background tracker (ms). */
const TRACKER_POLL_INTERVAL_MS = 3000

export interface TrackedImport {
  projectId: string
  name: string
  status: string
  /** Monotonic 0-100 progress (never moves backwards). */
  progress: number
  message: string | null
  updatedAt: number
}

/** Test seams so specs can drive WS events / polls deterministically. */
interface TrackerDeps {
  ws?: UseWebSocketReturn
  poll?: (projectId: string) => Promise<Project>
  pollIntervalMs?: number
}
let deps: TrackerDeps = {}
export function __setImportTrackerDeps(next: TrackerDeps): void {
  deps = next
}
export function __clearImportTrackerDeps(): void {
  deps = {}
}

const t = i18n.global.t

/**
 * useImportTracker - global owner of in-flight import tracking.
 *
 * Import analysis runs on the backend regardless of what the UI does; this
 * store makes the FRONTEND tracking equally independent of component
 * lifecycles. When an import is accepted (202 ANALYZING), any surface calls
 * `track(project)`; the store then follows the project over the STOMP status
 * topic plus a polling fallback and emits terminal updates so the project
 * list and the graph view can react — even if the user closed the import
 * modal and navigated elsewhere (the H10 orphaned polling problem). The
 * analyzing state is surfaced on the project card itself, so no background
 * toast is raised while the import runs; only the terminal outcome (ready /
 * failed) produces a notification.
 */
export const useImportTracker = defineStore('importTracker', () => {
  const tracked = ref<Record<string, TrackedImport>>({})
  /** Bumped on every snapshot so views can watch for changes cheaply. */
  const version = ref(0)

  const pollTimers = new Map<string, ReturnType<typeof setInterval>>()
  const subscriptions = new Map<string, { unsubscribe: () => void }>()
  let socket: UseWebSocketReturn | null = null

  const toasts = useToasts()
  const projectStore = useProjectStore()
  const accountStore = useAccountStore()

  function pollFn(projectId: string): Promise<Project> {
    return (deps.poll ?? ((id: string) => projectApi.get(id)))(projectId)
  }

  function bump(): void {
    version.value += 1
  }

  function get(projectId: string): TrackedImport | undefined {
    return tracked.value[projectId]
  }

  function isActive(projectId: string): boolean {
    return tracked.value[projectId]?.status === 'ANALYZING'
  }

  function stopChannels(projectId: string): void {
    const timer = pollTimers.get(projectId)
    if (timer) {
      clearInterval(timer)
      pollTimers.delete(projectId)
    }
    const subscription = subscriptions.get(projectId)
    if (subscription) {
      subscription.unsubscribe()
      subscriptions.delete(projectId)
    }
  }

  function patchProjectList(projectId: string, status: string, progress: number): void {
    const entry = projectStore.projects.find((project) => project.id === projectId)
    if (entry) {
      entry.status = status
      entry.progress = progress
    }
  }

  /**
   * Mirror the current project list into the account store so the Home
   * overview count agrees with the Repositories page.
   */
  function syncAccountProjects(): void {
    accountStore.setProjects(projectStore.projects.map(toAccountProject))
  }

  /**
   * Ensure an accepted (202 ANALYZING) project is visible in the repository
   * list right away. Without this the card only appears after a terminal list
   * reload — or never until a manual page reload when the import fails or the
   * user backgrounded the dialog, because ProjectsView serves its cached list.
   */
  function ensureInProjectList(project: Project): void {
    const existing = projectStore.projects.find((item) => item.id === project.id)
    if (existing) {
      existing.status = project.status
      existing.progress = project.progress ?? existing.progress
      return
    }
    projectStore.projects = [project, ...projectStore.projects]
    projectStore.projectsLoaded = true
    syncAccountProjects()
  }

  function finalize(projectId: string, status: 'ANALYZED' | 'FAILED', message: string | null): void {
    const entry = tracked.value[projectId]
    if (!entry || entry.status !== 'ANALYZING') return
    entry.status = status
    entry.message = message ?? entry.message
    if (status === 'ANALYZED') entry.progress = 100
    entry.updatedAt = Date.now()
    stopChannels(projectId)

    patchProjectList(projectId, status, entry.progress)

    if (status === 'ANALYZED') {
      // Reload the list so cards show the real file/node counts right away.
      void projectApi
        .list()
        .then((list) => {
          projectStore.projects = list
          syncAccountProjects()
        })
        .catch(() => undefined)
      toasts.push({
        kind: 'success',
        title: t('toasts.importReady', { name: entry.name }),
        durationMs: 8000,
        actionLabel: t('toasts.viewProject'),
        actionRoute: { name: 'graph', params: { projectId } },
      })
    } else {
      toasts.push({
        kind: 'error',
        title: t('toasts.importFailed', { name: entry.name }),
        message: message ?? undefined,
        durationMs: 10000,
      })
    }
    bump()
  }

  function applySnapshot(
    projectId: string,
    status: string,
    progress: number | undefined,
    message: string | null,
  ): void {
    const entry = tracked.value[projectId]
    if (!entry || entry.status !== 'ANALYZING') return
    if (typeof progress === 'number' && progress > entry.progress) {
      entry.progress = Math.min(100, progress)
    }
    if (message) entry.message = message
    entry.updatedAt = Date.now()
    if (status === 'ANALYZED') {
      finalize(projectId, 'ANALYZED', message)
      return
    }
    if (status === 'FAILED') {
      finalize(projectId, 'FAILED', message)
      return
    }
    bump()
  }

  async function pollOnce(projectId: string): Promise<void> {
    if (!isActive(projectId)) return
    try {
      const project = await pollFn(projectId)
      applySnapshot(projectId, project.status, project.progress, null)
    } catch {
      // Transient poll failure: the next tick / WS still covers the outcome.
    }
  }

  function startChannels(projectId: string): void {
    if (!pollTimers.has(projectId)) {
      pollTimers.set(
        projectId,
        setInterval(() => {
          void pollOnce(projectId)
        }, deps.pollIntervalMs ?? TRACKER_POLL_INTERVAL_MS),
      )
      void pollOnce(projectId)
    }

    if (subscriptions.has(projectId)) return
    socket = socket ?? deps.ws ?? useWebSocket()
    const activeSocket = socket
    activeSocket
      .connect()
      .then(() => {
        if (subscriptions.has(projectId) || !isActive(projectId)) return
        subscriptions.set(
          projectId,
          activeSocket.subscribe<ProjectStatusEvent>(`/topic/projects/${projectId}/status`, (event) => {
            if (event.projectId !== projectId) return
            applySnapshot(projectId, event.status, event.progress, event.message)
          }),
        )
      })
      .catch(() => {
        // WebSocket unavailable: polling alone still drives progress/outcome.
      })
  }

  /**
   * Register a project for background tracking. Safe to call multiple times
   * (project list mount, graph view, import form) — only the first call while
   * ANALYZING opens channels. The analyzing state is rendered by the project
   * card; no toast is raised until the import reaches a terminal state.
   */
  function track(project: Project): void {
    if (project.status !== 'ANALYZING') return
    if (isActive(project.id)) return

    ensureInProjectList(project)
    tracked.value[project.id] = {
      projectId: project.id,
      name: project.name,
      status: 'ANALYZING',
      progress: project.progress ?? 0,
      message: null,
      updatedAt: Date.now(),
    }
    bump()
    startChannels(project.id)
  }

  /** Drop a terminal entry once no surface needs it anymore. */
  function forget(projectId: string): void {
    stopChannels(projectId)
    delete tracked.value[projectId]
    bump()
  }

  return { tracked, version, track, get, isActive, forget }
})
