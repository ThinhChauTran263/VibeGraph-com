import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { ApiError, type Project, type ProjectStatusEvent } from '@/lib/api'
import { useArchiveImport } from '../useArchiveImport'
import type { UseWebSocketReturn } from '../useWebSocket'

/**
 * The composable calls `importApi.uploadArchive` directly. We mock the
 * module so the unit test never hits `fetch` and never depends on the
 * backend being live.
 */
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    importApi: {
      uploadArchive: vi.fn<(name: string, file: File) => Promise<Project>>(),
      uploadArchiveAsync: vi.fn<(name: string, file: File) => Promise<Project>>(),
    },
  }
})

// Re-import after the mock so we get the hoisted mocked module.
const { importApi } = await import('@/lib/api')
const uploadArchiveMock = importApi.uploadArchive as ReturnType<typeof vi.fn>
const uploadArchiveAsyncMock = importApi.uploadArchiveAsync as ReturnType<typeof vi.fn>

function makeFile(name: string, size: number): File {
  const bytes = size > 0 ? new Uint8Array(Math.min(size, 64)) : new Uint8Array(0)
  const file = new File([bytes], name, { type: 'application/octet-stream' })
  if (file.size !== size) {
    Object.defineProperty(file, 'size', { value: size, configurable: true })
  }
  return file
}

function fakeProject(overrides: Partial<Project> = {}): Project {
  return {
    id: 'p-123',
    name: 'sample',
    totalFiles: 0,
    totalNodes: 0,
    totalEdges: 0,
    status: 'ANALYZING',
    ...overrides,
  }
}

beforeEach(() => {
  uploadArchiveMock.mockReset()
  uploadArchiveAsyncMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('useArchiveImport - validation guards', () => {
  it('starts in idle state with no error and no project', () => {
    const composable = useArchiveImport()
    expect(composable.status.value).toBe('idle')
    expect(composable.errorMessage.value).toBeNull()
    expect(composable.importedProject.value).toBeNull()
    expect(composable.isUploading.value).toBe(false)
  })

  it('rejects a blank project name without calling the API', async () => {
    const composable = useArchiveImport()
    const result = await composable.uploadArchive('   ', makeFile('a.zip', 1024))
    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toContain('name')
    expect(uploadArchiveMock).not.toHaveBeenCalled()
  })

  it('rejects an invalid file extension without calling the API', async () => {
    const composable = useArchiveImport()
    const result = await composable.uploadArchive('demo', makeFile('not-an-archive.rar', 1024))
    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toContain('Unsupported archive type')
    expect(uploadArchiveMock).not.toHaveBeenCalled()
  })

  it('allows a large file to reach the backend quota check', async () => {
    uploadArchiveMock.mockResolvedValueOnce(fakeProject())
    const composable = useArchiveImport()
    const huge = makeFile('huge.zip', 200 * 1024 * 1024)
    const result = await composable.uploadArchive('demo', huge)
    expect(result).not.toBeNull()
    expect(uploadArchiveMock).toHaveBeenCalledWith('demo', huge)
  })
})

describe('useArchiveImport - successful upload', () => {
  it('forwards the trimmed name and file to importApi.uploadArchive', async () => {
    uploadArchiveMock.mockResolvedValueOnce(fakeProject())
    const composable = useArchiveImport()
    const file = makeFile('proj.tar.gz', 4096)

    await composable.uploadArchive('  my-svc  ', file)

    expect(uploadArchiveMock).toHaveBeenCalledTimes(1)
    expect(uploadArchiveMock).toHaveBeenCalledWith('my-svc', file)
  })

  it('moves to status=success and exposes the imported project', async () => {
    const project = fakeProject({ id: 'abc', name: 'abc-svc', status: 'ANALYZED' })
    uploadArchiveMock.mockResolvedValueOnce(project)
    const composable = useArchiveImport()

    const result = await composable.uploadArchive('abc-svc', makeFile('abc.zip', 1024))

    expect(result).toEqual(project)
    expect(composable.status.value).toBe('success')
    expect(composable.errorMessage.value).toBeNull()
    expect(composable.importedProject.value).toEqual(project)
  })

  it('reset() returns the composable to idle', async () => {
    uploadArchiveMock.mockResolvedValueOnce(fakeProject())
    const composable = useArchiveImport()
    await composable.uploadArchive('demo', makeFile('demo.zip', 1024))
    expect(composable.status.value).toBe('success')

    composable.reset()

    expect(composable.status.value).toBe('idle')
    expect(composable.errorMessage.value).toBeNull()
    expect(composable.importedProject.value).toBeNull()
  })
})

describe('useArchiveImport - error mapping', () => {
  it('surfaces the backend quota figures on ApiError(413)', async () => {
    uploadArchiveMock.mockRejectedValueOnce(
      new ApiError(
        413,
        'Payload Too Large',
        "Your source code occupies 3.0 MB, which exceeds the account's remaining storage quota (1.0 MB). Free up storage or ask an admin for a quota override.",
      ),
    )
    const composable = useArchiveImport()

    const result = await composable.uploadArchive('demo', makeFile('demo.zip', 1024))

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toContain('3.0 MB')
    expect(composable.errorMessage.value).toContain('remaining storage quota (1.0 MB)')
  })

  it('maps server 5xx to an "unavailable" message when no body text', async () => {
    uploadArchiveMock.mockRejectedValueOnce(new ApiError(500, 'Server Error', ''))
    const composable = useArchiveImport()

    await composable.uploadArchive('demo', makeFile('demo.zip', 1024))

    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toMatch(/unavailable|Server Error/i)
  })

  it('keeps the original ApiError message for client errors (e.g. 400)', async () => {
    uploadArchiveMock.mockRejectedValueOnce(new ApiError(400, 'Bad Request', 'unsafe path entry'))
    const composable = useArchiveImport()

    await composable.uploadArchive('demo', makeFile('demo.zip', 1024))

    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe('unsafe path entry')
  })

  it('maps an archive without .java files to a Java-only message', async () => {
    uploadArchiveMock.mockRejectedValueOnce(
      new ApiError(400, 'Bad Request', 'Archive contains no .java files', 'ARCHIVE_EMPTY_ARCHIVE'),
    )
    const composable = useArchiveImport()

    await composable.uploadArchive('demo', makeFile('demo.zip', 1024))

    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe(
      'This archive contains no .java files. VibeGraph currently analyzes Java projects only.',
    )
  })

  it('maps a non-ApiError thrown value to a generic upload-failed message', async () => {
    uploadArchiveMock.mockRejectedValueOnce(new Error(''))
    const composable = useArchiveImport()

    await composable.uploadArchive('demo', makeFile('demo.zip', 1024))

    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe('Upload failed.')
  })
})

/**
 * Fake WebSocket transport for async-import tests. Captures the subscribed
 * topic + callback so tests can synchronously drive status events. `connect`
 * resolves (or rejects) per the configured behavior.
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

/**
 * A poll stub that, by default, always reports ANALYZING (so the WebSocket
 * channel drives the outcome in most tests). Tests that exercise the polling
 * fallback override the resolved value.
 */
function makePoll(project: Project) {
  return vi.fn<() => Promise<Project>>(async () => project)
}

// Long poll interval + timeout so the fallback/watchdog never fire in
// WS-driven tests. Poll-specific tests use short values explicitly.
const STABLE = { pollIntervalMs: 100_000, analysisTimeoutMs: 100_000 }

describe('useArchiveImport - async upload', () => {
  it('rejects invalid input before calling the async API', async () => {
    const { ws } = makeFakeWs()
    const composable = useArchiveImport({ ws, ...STABLE, poll: makePoll(fakeProject()) })

    const result = await composable.uploadArchiveAsync('  ', makeFile('a.zip', 1024))

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(uploadArchiveAsyncMock).not.toHaveBeenCalled()
  })

  it('calls the async API and enters analyzing state with initial progress', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'async-1', status: 'ANALYZING', progress: 0 }),
    )
    const { ws, getTopic } = makeFakeWs()
    const composable = useArchiveImport({
      ws,
      ...STABLE,
      poll: makePoll(fakeProject({ id: 'async-1', status: 'ANALYZING', progress: 0 })),
    })

    // Do not await yet - inspect the intermediate analyzing state.
    const pending = composable.uploadArchiveAsync('demo', makeFile('demo.zip', 1024))
    // Let the upload promise resolve and the subscribe happen.
    await Promise.resolve()
    await Promise.resolve()

    expect(uploadArchiveAsyncMock).toHaveBeenCalledWith('demo', expect.any(File))
    expect(composable.status.value).toBe('analyzing')
    expect(composable.progress.value).toBe(0)
    expect(getTopic()).toBe('/topic/projects/async-1/status')

    // Settle so the test doesn't leak the pending promise/timers.
    composable.reset()
    void pending
  })

  it('on ANALYZED event resolves with success and the final project', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'async-2', name: 'svc', status: 'ANALYZING', progress: 0 }),
    )
    const { ws, emit } = makeFakeWs()
    const composable = useArchiveImport({
      ws,
      ...STABLE,
      poll: makePoll(fakeProject({ id: 'async-2', status: 'ANALYZING', progress: 0 })),
    })

    const pending = composable.uploadArchiveAsync('svc', makeFile('svc.zip', 1024))
    await Promise.resolve()
    await Promise.resolve()

    emit({
      projectId: 'async-2',
      status: 'ANALYZED',
      progress: 100,
      message: null,
      timestamp: '2026-06-01T00:00:00Z',
    })

    const result = await pending
    expect(result).not.toBeNull()
    expect(result?.status).toBe('ANALYZED')
    expect(composable.status.value).toBe('success')
    expect(composable.progress.value).toBe(100)
    expect(composable.importedProject.value?.id).toBe('async-2')
  })

  it('updates progress on intermediate ANALYZING events without settling', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'async-3', status: 'ANALYZING', progress: 0 }),
    )
    const { ws, emit } = makeFakeWs()
    const composable = useArchiveImport({
      ws,
      ...STABLE,
      poll: makePoll(fakeProject({ id: 'async-3', status: 'ANALYZING', progress: 0 })),
    })

    const pending = composable.uploadArchiveAsync('demo', makeFile('demo.zip', 1024))
    await Promise.resolve()
    await Promise.resolve()

    emit({
      projectId: 'async-3',
      status: 'ANALYZING',
      progress: 55,
      message: 'parsing',
      timestamp: '2026-06-01T00:00:00Z',
    })

    expect(composable.status.value).toBe('analyzing')
    expect(composable.progress.value).toBe(55)

    // Finish so the promise resolves.
    emit({
      projectId: 'async-3',
      status: 'ANALYZED',
      progress: 100,
      message: null,
      timestamp: '2026-06-01T00:00:01Z',
    })
    await pending
  })

  it('ignores status events for a different project id', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'mine', status: 'ANALYZING', progress: 0 }),
    )
    const { ws, emit } = makeFakeWs()
    const composable = useArchiveImport({
      ws,
      ...STABLE,
      poll: makePoll(fakeProject({ id: 'mine', status: 'ANALYZING', progress: 0 })),
    })

    const pending = composable.uploadArchiveAsync('demo', makeFile('demo.zip', 1024))
    await Promise.resolve()
    await Promise.resolve()

    emit({
      projectId: 'someone-else',
      status: 'ANALYZED',
      progress: 100,
      message: null,
      timestamp: '2026-06-01T00:00:00Z',
    })

    // Still analyzing - the foreign event was ignored.
    expect(composable.status.value).toBe('analyzing')

    emit({
      projectId: 'mine',
      status: 'ANALYZED',
      progress: 100,
      message: null,
      timestamp: '2026-06-01T00:00:01Z',
    })
    await pending
    expect(composable.status.value).toBe('success')
  })

  it('on FAILED event sets an error message and does not succeed', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'async-4', status: 'ANALYZING', progress: 0 }),
    )
    const { ws, emit } = makeFakeWs()
    const composable = useArchiveImport({
      ws,
      ...STABLE,
      poll: makePoll(fakeProject({ id: 'async-4', status: 'ANALYZING', progress: 0 })),
    })

    const pending = composable.uploadArchiveAsync('demo', makeFile('demo.zip', 1024))
    await Promise.resolve()
    await Promise.resolve()

    emit({
      projectId: 'async-4',
      status: 'FAILED',
      progress: 30,
      message: 'No parseable .java files.',
      timestamp: '2026-06-01T00:00:00Z',
    })

    const result = await pending
    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe('No parseable .java files.')
  })

  it('still succeeds via polling when the WebSocket never connects', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'async-5', status: 'ANALYZING', progress: 0 }),
    )
    const { ws } = makeFakeWs({ connectRejects: true })
    // Poll reports ANALYZED - the fallback should drive success despite WS fail.
    const poll = makePoll(fakeProject({ id: 'async-5', status: 'ANALYZED', progress: 100 }))
    const composable = useArchiveImport({ ws, ...STABLE, poll })

    const result = await composable.uploadArchiveAsync('demo', makeFile('demo.zip', 1024))

    expect(result).not.toBeNull()
    expect(composable.status.value).toBe('success')
    expect(poll).toHaveBeenCalled()
  })

  it('succeeds via polling when the WS event is missed (race)', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'race-1', status: 'ANALYZING', progress: 0 }),
    )
    const { ws } = makeFakeWs()
    // WS connects but never emits; the immediate poll catches ANALYZED.
    const poll = makePoll(fakeProject({ id: 'race-1', status: 'ANALYZED', progress: 100 }))
    const composable = useArchiveImport({ ws, ...STABLE, poll })

    const result = await composable.uploadArchiveAsync('demo', makeFile('demo.zip', 1024))

    expect(result?.status).toBe('ANALYZED')
    expect(composable.status.value).toBe('success')
  })

  it('fails via polling when the poll reports FAILED', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'race-2', status: 'ANALYZING', progress: 0 }),
    )
    const { ws } = makeFakeWs()
    const poll = makePoll(fakeProject({ id: 'race-2', status: 'FAILED', progress: 40 }))
    const composable = useArchiveImport({ ws, ...STABLE, poll })

    const result = await composable.uploadArchiveAsync('demo', makeFile('demo.zip', 1024))

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toMatch(/failed/i)
  })

  it('times out with a clear error if no terminal status arrives', async () => {
    vi.useFakeTimers()
    try {
      uploadArchiveAsyncMock.mockResolvedValueOnce(
        fakeProject({ id: 'timeout-1', status: 'ANALYZING', progress: 0 }),
      )
      const { ws } = makeFakeWs()
      // Poll always reports ANALYZING - never terminal.
      const poll = makePoll(fakeProject({ id: 'timeout-1', status: 'ANALYZING', progress: 10 }))
      const composable = useArchiveImport({
        ws,
        poll,
        pollIntervalMs: 1000,
        analysisTimeoutMs: 5000,
      })

      const pending = composable.uploadArchiveAsync('demo', makeFile('demo.zip', 1024))
      // Flush the upload promise microtasks.
      await vi.advanceTimersByTimeAsync(0)
      expect(composable.status.value).toBe('analyzing')

      // Advance past the watchdog timeout.
      await vi.advanceTimersByTimeAsync(5000)
      const result = await pending

      expect(result).toBeNull()
      expect(composable.status.value).toBe('error')
      expect(composable.errorMessage.value).toMatch(/timed out/i)
    } finally {
      vi.useRealTimers()
    }
  })

  it('surfaces the backend quota figures on an async upload ApiError(413)', async () => {
    uploadArchiveAsyncMock.mockRejectedValueOnce(
      new ApiError(
        413,
        'Payload Too Large',
        "Your source code occupies 3.0 MB, which exceeds the account's remaining storage quota (1.0 MB). Free up storage or ask an admin for a quota override.",
      ),
    )
    const { ws } = makeFakeWs()
    const composable = useArchiveImport({ ws, ...STABLE, poll: makePoll(fakeProject()) })

    const result = await composable.uploadArchiveAsync('demo', makeFile('demo.zip', 1024))

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toContain('remaining storage quota (1.0 MB)')
    expect(ws.connect).not.toHaveBeenCalled()
  })
})
