import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import {
  useImportTracker,
  withInFlightCards,
  __clearImportTrackerDeps,
  __setImportTrackerDeps,
} from '../importTracker'
import { useToasts } from '../toasts'
import { useProjectStore } from '../project'
import type { Project, ProjectStatusEvent } from '@/lib/api'
import type { UseWebSocketReturn } from '@/composables/useWebSocket'

function fakeProject(overrides: Partial<Project> = {}): Project {
  return {
    id: 'p-1',
    name: 'svc-alpha',
    status: 'ANALYZING',
    totalFiles: 0,
    totalNodes: 0,
    totalEdges: 0,
    progress: 5,
    ...overrides,
  }
}

interface FakeWs extends UseWebSocketReturn {
  handlers: Map<string, (event: ProjectStatusEvent) => void>
  fire: (event: ProjectStatusEvent) => void
}

function makeFakeWs(): FakeWs {
  const handlers = new Map<string, (event: ProjectStatusEvent) => void>()
  const ws: FakeWs = {
    status: ref('disconnected'),
    error: ref(null),
    connect: () => Promise.resolve(),
    disconnect: () => Promise.resolve(),
    subscribe: <T>(topic: string, callback: (payload: T) => void) => {
      handlers.set(topic, callback as (event: ProjectStatusEvent) => void)
      return { active: ref(true), unsubscribe: () => handlers.delete(topic) }
    },
    handlers,
    fire: (event) => {
      handlers.get(`/topic/projects/${event.projectId}/status`)?.(event)
    },
  }
  return ws
}

describe('useImportTracker', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })
  afterEach(() => {
    __clearImportTrackerDeps()
  })

  it('tracks an accepted import without a toast and applies monotonic progress', async () => {
    const ws = makeFakeWs()
    let pollCalls = 0
    __setImportTrackerDeps({
      ws,
      pollIntervalMs: 100_000,
      poll: async () => {
        pollCalls += 1
        return fakeProject()
      },
    })
    const tracker = useImportTracker()
    const toasts = useToasts()

    tracker.track(fakeProject())
    await flushPromises()

    expect(tracker.isActive('p-1')).toBe(true)
    // The analyzing state lives on the project card; no toast while running.
    expect(toasts.toasts).toHaveLength(0)

    // Live progress moves forward and is monotonic.
    ws.fire({
      projectId: 'p-1',
      status: 'ANALYZING',
      progress: 40,
      message: 'building graph',
      timestamp: 't',
    })
    expect(tracker.get('p-1')?.progress).toBe(40)
    expect(tracker.get('p-1')?.message).toBe('building graph')

    // A late lower snapshot never moves progress backwards.
    ws.fire({ projectId: 'p-1', status: 'ANALYZING', progress: 30, message: null, timestamp: 't' })
    expect(tracker.get('p-1')?.progress).toBe(40)

    // The immediate poll ran once (interval is far in the future).
    expect(pollCalls).toBe(1)
  })

  it('finalizes on ANALYZED: pushes a success toast and patches the project list', async () => {
    const ws = makeFakeWs()
    __setImportTrackerDeps({
      ws,
      pollIntervalMs: 100_000,
      poll: async () => fakeProject(),
    })
    const tracker = useImportTracker()
    const toasts = useToasts()
    const projects = useProjectStore()
    projects.projects = [fakeProject()]

    tracker.track(fakeProject())
    await flushPromises()

    ws.fire({ projectId: 'p-1', status: 'ANALYZED', progress: 100, message: null, timestamp: 't' })

    expect(tracker.isActive('p-1')).toBe(false)
    expect(toasts.toasts).toHaveLength(1)
    expect(toasts.toasts[0]?.kind).toBe('success')
    expect(toasts.toasts[0]?.durationMs).toBeGreaterThan(0)
    expect(projects.projects[0]?.status).toBe('ANALYZED')
  })

  it('finalizes on FAILED with an error toast carrying the server message', async () => {
    const ws = makeFakeWs()
    __setImportTrackerDeps({
      ws,
      pollIntervalMs: 100_000,
      poll: async () => fakeProject(),
    })
    const tracker = useImportTracker()
    const toasts = useToasts()

    tracker.track(fakeProject())
    await flushPromises()

    ws.fire({ projectId: 'p-1', status: 'FAILED', progress: 12, message: 'boom', timestamp: 't' })

    expect(tracker.isActive('p-1')).toBe(false)
    expect(toasts.toasts[0]?.kind).toBe('error')
    expect(toasts.toasts[0]?.message).toBe('boom')
  })

  it('ignores duplicate track calls and non-analyzing projects', async () => {
    const ws = makeFakeWs()
    __setImportTrackerDeps({ ws, pollIntervalMs: 100_000, poll: async () => fakeProject() })
    const tracker = useImportTracker()
    const toasts = useToasts()

    tracker.track(fakeProject())
    tracker.track(fakeProject())
    tracker.track(fakeProject({ id: 'p-2', name: 'done', status: 'ANALYZED' }))
    await flushPromises()

    expect(Object.keys(tracker.tracked)).toEqual(['p-1'])
    // No toast while analyzing; only a terminal outcome raises one.
    expect(toasts.toasts).toHaveLength(0)
  })

  it('trackPending shows a provisional card and track swaps it for the real project', async () => {
    const ws = makeFakeWs()
    __setImportTrackerDeps({ ws, pollIntervalMs: 100_000, poll: async () => fakeProject() })
    const tracker = useImportTracker()
    const projects = useProjectStore()

    tracker.trackPending('acme/demo', 'main')
    await flushPromises()

    expect(projects.projects).toHaveLength(1)
    const provisional = projects.projects[0]!
    expect(provisional.id.startsWith('pending-')).toBe(true)
    expect(provisional.status).toBe('ANALYZING')
    expect(provisional.sourceBranch).toBe('main')
    expect(tracker.isActive(provisional.id)).toBe(true)

    // The 202 arrives: the real project replaces the provisional card.
    tracker.track(fakeProject({ id: 'p-real', name: 'acme/demo' }))
    await flushPromises()

    expect(projects.projects).toHaveLength(1)
    expect(projects.projects[0]?.id).toBe('p-real')
    expect(tracker.isActive('p-real')).toBe(true)
    expect(tracker.get(provisional.id)).toBeUndefined()
  })

  it('failPending turns the provisional card into an error card', async () => {
    const ws = makeFakeWs()
    __setImportTrackerDeps({ ws, pollIntervalMs: 100_000, poll: async () => fakeProject() })
    const tracker = useImportTracker()
    const projects = useProjectStore()

    tracker.trackPending('acme/demo')
    tracker.failPending('Private GitHub repositories are not supported')
    await flushPromises()

    const card = projects.projects[0]!
    expect(card.id.startsWith('pending-')).toBe(true)
    expect(card.status).toBe('FAILED')
    expect(tracker.get(card.id)?.message).toBe('Private GitHub repositories are not supported')
  })

  it('keeps the provisional card across a silent reconcile until the 202 arrives', async () => {
    const ws = makeFakeWs()
    __setImportTrackerDeps({ ws, pollIntervalMs: 100_000, poll: async () => fakeProject() })
    const tracker = useImportTracker()
    const projects = useProjectStore()

    tracker.trackPending('acme/demo', 'main')
    await flushPromises()
    const provisionalId = projects.projects[0]!.id

    // Simulate the KeepAlive re-activation reconcile: the server list has no
    // row for the pre-202 card yet.
    const reconcile = (serverList: Project[]) => {
      projects.projects = withInFlightCards(serverList, projects.projects, (id) =>
        Boolean(tracker.get(id)),
      )
    }
    reconcile([fakeProject({ id: 'p-other', name: 'other', status: 'ANALYZED' })])

    expect(projects.projects.map((p) => p.id)).toEqual([provisionalId, 'p-other'])

    // The 202 arrives; the next reconcile serves only server rows.
    tracker.track(fakeProject({ id: 'p-real', name: 'acme/demo' }))
    await flushPromises()
    reconcile([
      fakeProject({ id: 'p-real', name: 'acme/demo' }),
      fakeProject({ id: 'p-other', name: 'other', status: 'ANALYZED' }),
    ])

    expect(projects.projects.map((p) => p.id)).toEqual(['p-real', 'p-other'])
  })
})

describe('withInFlightCards', () => {
  const pending = fakeProject({ id: 'pending-x', name: 'acme/demo' })
  const server = fakeProject({ id: 'p-1', name: 'other', status: 'ANALYZED' })

  it('keeps tracked provisional cards missing from the server list', () => {
    const merged = withInFlightCards([server], [pending, server], () => true)
    expect(merged.map((p) => p.id)).toEqual(['pending-x', 'p-1'])
  })

  it('drops provisional cards that are no longer tracked', () => {
    const merged = withInFlightCards([server], [pending, server], () => false)
    expect(merged.map((p) => p.id)).toEqual(['p-1'])
  })

  it('never duplicates a provisional card once the server knows it', () => {
    const merged = withInFlightCards([pending, server], [pending, server], () => true)
    expect(merged.map((p) => p.id)).toEqual(['pending-x', 'p-1'])
  })

  it('ignores non-pending local rows', () => {
    const local = fakeProject({ id: 'p-local', name: 'local' })
    const merged = withInFlightCards([server], [local, server], () => true)
    expect(merged.map((p) => p.id)).toEqual(['p-1'])
  })
})
