import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import {
  useImportTracker,
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
    ws.fire({ projectId: 'p-1', status: 'ANALYZING', progress: 40, message: 'building graph', timestamp: 't' })
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
})
