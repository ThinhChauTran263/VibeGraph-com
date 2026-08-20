import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { ApiError, type Project, type ProjectStatusEvent } from '@/lib/api'
import type { GraphData } from '@/types/graph'
import type { UseWebSocketReturn } from '../useWebSocket'
import { useGitHubImport, validateGitHubRepoUrl } from '../useGitHubImport'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    fetchFullGraph: vi.fn<typeof actual.fetchFullGraph>(),
    importApi: {
      ...actual.importApi,
      importGithub: vi.fn<(url: string) => Promise<Project>>(),
    },
    projectApi: {
      ...actual.projectApi,
      get: vi.fn<(id: string) => Promise<Project>>(),
    },
  }
})

const { fetchFullGraph, importApi, projectApi } = await import('@/lib/api')
const fetchFullGraphMock = fetchFullGraph as ReturnType<typeof vi.fn>
const importGithubMock = importApi.importGithub as ReturnType<typeof vi.fn>
const getProjectMock = projectApi.get as ReturnType<typeof vi.fn>

function fakeProject(overrides: Partial<Project> = {}): Project {
  return {
    id: 'gh-1',
    name: 'repo',
    totalFiles: 0,
    totalNodes: 0,
    totalEdges: 0,
    status: 'ANALYZING',
    ...overrides,
  }
}

function fakeGraph(overrides: Partial<GraphData> = {}): GraphData {
  return {
    nodes: [
      {
        id: 'node-1',
        type: 'Class',
        name: 'Sample',
        fullName: 'com.example.Sample',
        filePath: 'src/Sample.java',
        lineNumber: 1,
        properties: {},
      },
    ],
    edges: [],
    nodeStats: { Class: 1 } as GraphData['nodeStats'],
    edgeStats: {} as GraphData['edgeStats'],
    ...overrides,
  }
}

/**
 * Fake WebSocket transport for analysis tests. Captures the subscribed topic +
 * callback so tests can synchronously push live progress events. `connect`
 * resolves (or rejects) per the configured behavior; failure must be non-fatal
 * because polling drives the terminal outcome.
 */
function makeFakeWs(opts: { connectRejects?: boolean } = {}) {
  let captured: { topic: string; cb: (e: ProjectStatusEvent) => void } | null = null
  let unsubscribed = false

  const subscribe = <T>(topic: string, cb: (payload: T) => void) => {
    const active = ref(true)
    captured = { topic, cb: cb as unknown as (e: ProjectStatusEvent) => void }
    return {
      active,
      unsubscribe: () => {
        active.value = false
        unsubscribed = true
      },
    }
  }

  const ws: UseWebSocketReturn = {
    status: ref('disconnected'),
    error: ref<string | null>(null),
    connect: vi.fn<() => Promise<void>>(async () => {
      if (opts.connectRejects) {
        ws.status.value = 'error'
        throw new Error('WebSocket connection failed.')
      }
      ws.status.value = 'connected'
    }),
    disconnect: vi.fn<() => Promise<void>>(async () => {
      ws.status.value = 'disconnected'
    }),
    subscribe,
  }

  return {
    ws,
    emit: (event: ProjectStatusEvent) => captured?.cb(event),
    getTopic: () => captured?.topic,
    wasUnsubscribed: () => unsubscribed,
  }
}

beforeEach(() => {
  fetchFullGraphMock.mockReset()
  importGithubMock.mockReset()
  getProjectMock.mockReset()
})

describe('validateGitHubRepoUrl', () => {
  it('accepts public GitHub HTTPS repository URLs', () => {
    expect(validateGitHubRepoUrl('https://github.com/spring-projects/spring-petclinic')).toBeNull()
    expect(validateGitHubRepoUrl('https://github.com/owner/repo.git')).toBeNull()
  })

  it('rejects blank or non-GitHub URLs', () => {
    expect(validateGitHubRepoUrl('   ')).toMatch(/required/i)
    expect(validateGitHubRepoUrl('https://example.com/owner/repo')).toMatch(/github/i)
  })
})

describe('useGitHubImport', () => {
  it('validates URL before calling the API', async () => {
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://example.com/not/github')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toMatch(/github/i)
    expect(importGithubMock).not.toHaveBeenCalled()
  })

  it('imports a valid repo and exposes the returned project', async () => {
    const project = fakeProject({ id: 'gh-2', name: 'spring-petclinic', status: 'ANALYZED' })
    importGithubMock.mockResolvedValueOnce(project)
    const composable = useGitHubImport()

    const result = await composable.importGithub(
      '  https://github.com/spring-projects/spring-petclinic  ',
    )

    expect(importGithubMock).toHaveBeenCalledWith(
      'https://github.com/spring-projects/spring-petclinic',
      undefined,
    )
    expect(result).toEqual(project)
    expect(composable.status.value).toBe('success')
    expect(composable.importedProject.value).toEqual(project)
  })

  it('forwards the selected branch to the API', async () => {
    const project = fakeProject({ id: 'gh-2b', status: 'ANALYZED' })
    importGithubMock.mockResolvedValueOnce(project)
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/repo', ' develop ')

    expect(importGithubMock).toHaveBeenCalledWith('https://github.com/owner/repo', 'develop')
    expect(result).toEqual(project)
  })

  it('rejects an invalid branch name without calling the API', async () => {
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/repo', 'feature..x')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toMatch(/branch/i)
    expect(importGithubMock).not.toHaveBeenCalled()
  })

  it('fires onSubmitted when the request goes out and onAccepted on a 202', async () => {
    const analyzingProject = fakeProject({ id: 'gh-hook', status: 'ANALYZING' })
    const analyzedProject = fakeProject({ id: 'gh-hook', status: 'ANALYZED' })
    importGithubMock.mockResolvedValueOnce(analyzingProject)
    getProjectMock.mockResolvedValueOnce(analyzedProject)
    const onSubmitted = vi.fn()
    const onAccepted = vi.fn()
    const onRejected = vi.fn()
    const { ws } = makeFakeWs({ connectRejects: true })
    const composable = useGitHubImport({ ws, onSubmitted, onAccepted, onRejected })

    await composable.importGithub('https://github.com/owner/repo', 'main')

    expect(onSubmitted).toHaveBeenCalledWith('https://github.com/owner/repo', 'main')
    expect(onAccepted).toHaveBeenCalledTimes(1)
    expect(onRejected).not.toHaveBeenCalled()
  })

  it('fires onRejected with the user-facing message when the request fails', async () => {
    importGithubMock.mockRejectedValueOnce(
      new ApiError(422, 'Unprocessable Entity', 'Private GitHub repositories are not supported'),
    )
    const onSubmitted = vi.fn()
    const onRejected = vi.fn()
    const composable = useGitHubImport({ onSubmitted, onRejected })

    await composable.importGithub('https://github.com/owner/private')

    expect(onSubmitted).toHaveBeenCalled()
    expect(onRejected).toHaveBeenCalledWith('Private GitHub repositories are not supported')
  })

  it('waits for an async import to finish before exposing success', async () => {
    vi.useFakeTimers()
    const analyzingProject = fakeProject({
      id: 'gh-3',
      name: 'lab7',
      status: 'ANALYZING',
      progress: 20,
    })
    const analyzedProject = fakeProject({
      id: 'gh-3',
      name: 'lab7',
      status: 'ANALYZED',
      progress: 100,
      totalFiles: 24,
      totalNodes: 133,
      totalEdges: 434,
    })
    importGithubMock.mockResolvedValueOnce(analyzingProject)
    getProjectMock.mockResolvedValueOnce(analyzedProject)
    const { ws } = makeFakeWs({ connectRejects: true })
    const composable = useGitHubImport({ ws })

    try {
      const resultPromise = composable.importGithub('https://github.com/owner/lab7')
      await vi.advanceTimersByTimeAsync(1_000)
      const result = await resultPromise

      expect(getProjectMock).toHaveBeenCalledWith('gh-3')
      // ANALYZED is terminal success; the import no longer waits on a full-graph fetch.
      expect(fetchFullGraphMock).not.toHaveBeenCalled()
      expect(result).toEqual(analyzedProject)
      expect(composable.status.value).toBe('success')
      expect(composable.importedProject.value).toEqual(analyzedProject)
    } finally {
      vi.useRealTimers()
    }
  })

  it('updates progress live from WebSocket events during analysis', async () => {
    vi.useFakeTimers()
    const analyzingProject = fakeProject({
      id: 'gh-live',
      name: 'live',
      status: 'ANALYZING',
      progress: 5,
    })
    const analyzedProject = fakeProject({
      id: 'gh-live',
      name: 'live',
      status: 'ANALYZED',
      progress: 100,
    })
    importGithubMock.mockResolvedValueOnce(analyzingProject)
    getProjectMock.mockResolvedValueOnce(analyzedProject)
    fetchFullGraphMock.mockResolvedValueOnce(fakeGraph())
    const { ws, emit, getTopic, wasUnsubscribed } = makeFakeWs()
    const composable = useGitHubImport({ ws })

    try {
      const resultPromise = composable.importGithub('https://github.com/owner/live')
      // Flush importApi + ws.connect + subscribe microtasks.
      await vi.advanceTimersByTimeAsync(0)

      expect(getTopic()).toBe('/topic/projects/gh-live/status')

      emit({
        projectId: 'gh-live',
        status: 'ANALYZING',
        progress: 42,
        message: null,
        timestamp: '2026-01-01T00:00:00Z',
      })
      expect(composable.progress.value).toBe(42)

      // A stale event for another project must be ignored.
      emit({
        projectId: 'other',
        status: 'ANALYZING',
        progress: 99,
        message: null,
        timestamp: '2026-01-01T00:00:01Z',
      })
      expect(composable.progress.value).toBe(42)

      await vi.advanceTimersByTimeAsync(1_000)
      const result = await resultPromise

      expect(result).toEqual(analyzedProject)
      expect(composable.progress.value).toBe(100)
      expect(wasUnsubscribed()).toBe(true)
    } finally {
      vi.useRealTimers()
    }
  })

  it('keeps waiting when GitHub analysis takes longer than one minute', async () => {
    vi.useFakeTimers()
    const analyzingProject = fakeProject({
      id: 'gh-slow',
      name: 'spx-tracking',
      status: 'ANALYZING',
    })
    const analyzedProject = fakeProject({
      id: 'gh-slow',
      name: 'spx-tracking',
      status: 'ANALYZED',
      progress: 100,
      totalFiles: 39,
      totalNodes: 578,
      totalEdges: 1852,
    })
    importGithubMock.mockResolvedValueOnce(analyzingProject)
    getProjectMock.mockImplementation(async () =>
      getProjectMock.mock.calls.length >= 65 ? analyzedProject : analyzingProject,
    )
    const { ws } = makeFakeWs({ connectRejects: true })
    const composable = useGitHubImport({ ws })

    try {
      const resultPromise = composable.importGithub('https://github.com/owner/spx-tracking')
      await vi.advanceTimersByTimeAsync(65_000)
      const result = await resultPromise

      expect(getProjectMock).toHaveBeenCalledTimes(65)
      // ANALYZED is terminal success; no full-graph fetch is required.
      expect(fetchFullGraphMock).not.toHaveBeenCalled()
      expect(result).toEqual(analyzedProject)
      expect(composable.status.value).toBe('success')
      expect(composable.errorMessage.value).toBeNull()
    } finally {
      vi.useRealTimers()
    }
  })

  it('maps safe API errors to user-visible error state', async () => {
    importGithubMock.mockRejectedValueOnce(
      new ApiError(400, 'Bad Request', 'Repository is private.'),
    )
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/private-repo')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe('Repository is private.')
  })

  it('shows the bounded retry failure returned by the backend', async () => {
    const retryMessage =
      'Failed to download GitHub tarball after 3 attempts: HTTP connect timed out'
    importGithubMock.mockRejectedValueOnce(
      new ApiError(422, 'Unprocessable Entity', retryMessage),
    )
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/large-repo')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe(retryMessage)
  })

  it('names the real reason when a non-Java repository has no .java files', async () => {
    importGithubMock.mockRejectedValueOnce(
      new ApiError(400, 'Bad Request', 'Archive contains no .java files', 'ARCHIVE_EMPTY_ARCHIVE'),
    )
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/js-project')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe(
      'This repository contains no .java files. VibeGraph currently analyzes Java projects only.',
    )
  })

  it('surfaces curated 422 reasons verbatim instead of the generic fallback', async () => {
    const quotaMessage =
      "GitHub repository is larger than the server's maximum import size (100MB)"
    importGithubMock.mockRejectedValueOnce(
      new ApiError(422, 'Unprocessable Entity', quotaMessage, 'GITHUB_IMPORT_ERROR'),
    )
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/huge-repo')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe(quotaMessage)
  })

  it('surfaces the curated SERVICE_BUSY reason instead of the generic fallback', async () => {
    const busyMessage = 'Server is busy analyzing other projects. Please retry shortly.'
    importGithubMock.mockRejectedValueOnce(
      new ApiError(503, 'Service Unavailable', busyMessage, 'SERVICE_BUSY'),
    )
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/repo')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe(busyMessage)
  })

  it('reports an accurate message when background analysis fails', async () => {
    vi.useFakeTimers()
    const analyzingProject = fakeProject({ id: 'gh-fail', status: 'ANALYZING' })
    const failedProject = fakeProject({ id: 'gh-fail', status: 'FAILED' })
    importGithubMock.mockResolvedValueOnce(analyzingProject)
    getProjectMock.mockResolvedValueOnce(failedProject)
    const { ws } = makeFakeWs({ connectRejects: true })
    const composable = useGitHubImport({ ws })

    try {
      const resultPromise = composable.importGithub('https://github.com/owner/broken')
      await vi.advanceTimersByTimeAsync(1_000)
      const result = await resultPromise

      expect(result).toBeNull()
      expect(composable.status.value).toBe('error')
      expect(composable.errorMessage.value).toMatch(/analysis failed/i)
      expect(composable.errorMessage.value).not.toMatch(/verify the repository is public/i)
    } finally {
      vi.useRealTimers()
    }
  })

  it('uses a generic message for unexpected API errors', async () => {
    importGithubMock.mockRejectedValueOnce(
      new ApiError(500, 'Internal Server Error', 'Stack trace: /srv/app/importer'),
    )
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/repo')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe(
      'Import failed. Verify the repository is public and try again.',
    )
  })

  it('reports a connectivity error (not a repo error) when the backend is unreachable', async () => {
    // A failed fetch rejects with a TypeError, not an ApiError.
    importGithubMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/repo')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toMatch(/cannot reach the server/i)
  })
})
