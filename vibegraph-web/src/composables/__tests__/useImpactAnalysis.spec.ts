import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type ImpactAnalysisResponse } from '@/lib/api'
import { useImpactAnalysis } from '../useImpactAnalysis'

/**
 * The composable calls `graphApi.getImpact` directly. Mock the api module so
 * the unit test never touches `fetch` or the backend.
 */
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    graphApi: {
      ...actual.graphApi,
      getImpact:
        vi.fn<(projectId: string, nodeId: string, depth: number) => Promise<ImpactAnalysisResponse>>(),
    },
  }
})

const { graphApi } = await import('@/lib/api')
const getImpactMock = graphApi.getImpact as ReturnType<typeof vi.fn>

function fakeImpact(overrides: Partial<ImpactAnalysisResponse> = {}): ImpactAnalysisResponse {
  return {
    target: {
      id: 'n1',
      type: 'Class',
      name: 'OrderService',
      fullName: 'com.example.OrderService',
      filePath: 'src/OrderService.java',
      lineNumber: 10,
    },
    riskLevel: 'MEDIUM',
    directDependents: 6,
    totalDependents: 9,
    willBreak: [],
    likelyAffected: [],
    mayNeedTesting: [],
    ...overrides,
  }
}

beforeEach(() => {
  getImpactMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('useImpactAnalysis - initial state', () => {
  it('starts idle with no result and no error', () => {
    const composable = useImpactAnalysis()
    expect(composable.status.value).toBe('idle')
    expect(composable.result.value).toBeNull()
    expect(composable.errorMessage.value).toBeNull()
    expect(composable.selectedDepth.value).toBe(1)
  })
})

describe('useImpactAnalysis - validation guards', () => {
  it('rejects a blank projectId without calling the API', async () => {
    const composable = useImpactAnalysis()
    const result = await composable.loadImpact('   ', 'n1', 1)
    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toContain('projectId')
    expect(getImpactMock).not.toHaveBeenCalled()
  })

  it('rejects a blank nodeId without calling the API', async () => {
    const composable = useImpactAnalysis()
    const result = await composable.loadImpact('p1', '  ', 1)
    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toMatch(/node/i)
    expect(getImpactMock).not.toHaveBeenCalled()
  })

  it('rejects a depth outside the backend whitelist', async () => {
    const composable = useImpactAnalysis()
    const result = await composable.loadImpact('p1', 'n1', 4)
    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toMatch(/depth/i)
    expect(getImpactMock).not.toHaveBeenCalled()
  })
})

describe('useImpactAnalysis - successful load', () => {
  it('forwards trimmed identifiers and depth to graphApi.getImpact', async () => {
    getImpactMock.mockResolvedValueOnce(fakeImpact())
    const composable = useImpactAnalysis()

    await composable.loadImpact('  p1  ', '  com.example.OrderService  ', 2)

    expect(getImpactMock).toHaveBeenCalledTimes(1)
    expect(getImpactMock).toHaveBeenCalledWith('p1', 'com.example.OrderService', 2)
  })

  it('moves to success and exposes the result + selected depth', async () => {
    const impact = fakeImpact({ riskLevel: 'HIGH', directDependents: 20 })
    getImpactMock.mockResolvedValueOnce(impact)
    const composable = useImpactAnalysis()

    const result = await composable.loadImpact('p1', 'n1', 3)

    expect(result).toEqual(impact)
    expect(composable.status.value).toBe('success')
    expect(composable.result.value).toEqual(impact)
    expect(composable.errorMessage.value).toBeNull()
    expect(composable.selectedDepth.value).toBe(3)
  })

  it('uses selectedDepth when depth arg is omitted', async () => {
    getImpactMock.mockResolvedValue(fakeImpact())
    const composable = useImpactAnalysis()
    composable.selectedDepth.value = 5

    await composable.loadImpact('p1', 'n1')

    expect(getImpactMock).toHaveBeenCalledWith('p1', 'n1', 5)
  })
})

describe('useImpactAnalysis - error mapping', () => {
  it('maps an ApiError message to a user-visible error', async () => {
    getImpactMock.mockRejectedValueOnce(new ApiError(404, 'Not Found', 'Node not found'))
    const composable = useImpactAnalysis()

    const result = await composable.loadImpact('p1', 'missing', 1)

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.result.value).toBeNull()
    expect(composable.errorMessage.value).toBe('Node not found')
  })

  it('maps a non-ApiError to a generic message', async () => {
    getImpactMock.mockRejectedValueOnce(new Error(''))
    const composable = useImpactAnalysis()

    await composable.loadImpact('p1', 'n1', 1)

    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe('Failed to load impact analysis.')
  })
})

describe('useImpactAnalysis - stale-response race', () => {
  /** A deferred promise whose resolution we control from the test. */
  function deferred<T>() {
    let resolve!: (value: T) => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<T>((res, rej) => {
      resolve = res
      reject = rej
    })
    return { promise, resolve, reject }
  }

  it('ignores a stale success that resolves after reset() (node change)', async () => {
    const slow = deferred<ImpactAnalysisResponse>()
    getImpactMock.mockReturnValueOnce(slow.promise)
    const composable = useImpactAnalysis()

    // Request for node X is in-flight (not yet resolved).
    const pending = composable.loadImpact('p1', 'nodeX', 1)
    expect(composable.status.value).toBe('loading')

    // User selects node Y → panel resets.
    composable.reset()
    expect(composable.status.value).toBe('idle')

    // The old request for X resolves late.
    slow.resolve(fakeImpact({ riskLevel: 'CRITICAL' }))
    const result = await pending

    // Stale response must be dropped: no result written, still idle.
    expect(result).toBeNull()
    expect(composable.status.value).toBe('idle')
    expect(composable.result.value).toBeNull()
  })

  it('ignores a stale error that rejects after reset()', async () => {
    const slow = deferred<ImpactAnalysisResponse>()
    getImpactMock.mockReturnValueOnce(slow.promise)
    const composable = useImpactAnalysis()

    const pending = composable.loadImpact('p1', 'nodeX', 1)
    composable.reset()

    slow.reject(new ApiError(500, 'Server Error', 'boom'))
    const result = await pending

    expect(result).toBeNull()
    expect(composable.status.value).toBe('idle')
    expect(composable.errorMessage.value).toBeNull()
  })

  it('keeps only the latest request when a newer one supersedes an older in-flight one', async () => {
    const first = deferred<ImpactAnalysisResponse>()
    const second = deferred<ImpactAnalysisResponse>()
    getImpactMock.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const composable = useImpactAnalysis()

    // Request 1 (node X) in-flight.
    const pendingX = composable.loadImpact('p1', 'nodeX', 1)
    // Request 2 (node Y) starts before request 1 resolves.
    const pendingY = composable.loadImpact('p1', 'nodeY', 2)

    // Request 2 resolves first and wins.
    const impactY = fakeImpact({ riskLevel: 'LOW', directDependents: 1 })
    second.resolve(impactY)
    await pendingY
    expect(composable.status.value).toBe('success')
    expect(composable.result.value).toEqual(impactY)

    // Request 1 (stale) resolves late and must NOT overwrite Y's result.
    first.resolve(fakeImpact({ riskLevel: 'CRITICAL', directDependents: 99 }))
    await pendingX

    expect(composable.result.value).toEqual(impactY)
    expect(composable.status.value).toBe('success')
  })
})

describe('useImpactAnalysis - reset', () => {
  it('returns to idle and clears the result', async () => {
    getImpactMock.mockResolvedValueOnce(fakeImpact())
    const composable = useImpactAnalysis()
    await composable.loadImpact('p1', 'n1', 1)
    expect(composable.status.value).toBe('success')

    composable.reset()

    expect(composable.status.value).toBe('idle')
    expect(composable.result.value).toBeNull()
    expect(composable.errorMessage.value).toBeNull()
  })
})
