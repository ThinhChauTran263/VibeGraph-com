import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { graphApi, ApiError, type ImpactAnalysisResponse } from '../api'

/**
 * Exercises `graphApi.getImpact` against a mocked global `fetch`, verifying
 * the exact URL + encoded query params per the backend contract:
 *   GET /api/projects/{projectId}/graph/impact?nodeId=...&depth=...
 */

function okJson(data: unknown): Response {
  return {
    ok: true,
    status: 200,
    statusText: 'OK',
    json: async () => ({ success: true, data, error: null }),
    text: async () => JSON.stringify({ success: true, data }),
  } as unknown as Response
}

function fakeImpact(): ImpactAnalysisResponse {
  return {
    target: {
      id: 'n1',
      type: 'Class',
      name: 'OrderService',
      fullName: 'com.example.OrderService',
      filePath: 'src/OrderService.java',
      lineNumber: 10,
    },
    riskLevel: 'HIGH',
    directDependents: 8,
    totalDependents: 12,
    willBreak: [],
    likelyAffected: [],
    mayNeedTesting: [],
  }
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('graphApi.getImpact', () => {
  it('GETs the impact endpoint with nodeId and depth query params', async () => {
    fetchMock.mockResolvedValueOnce(okJson(fakeImpact()))

    await graphApi.getImpact('p1', 'com.example.OrderService', 3)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const url = String(fetchMock.mock.calls[0]![0])
    expect(url).toContain('/api/projects/p1/graph/impact?')
    expect(url).toContain('nodeId=com.example.OrderService')
    expect(url).toContain('depth=3')
  })

  it('URL-encodes node identifiers with special characters', async () => {
    fetchMock.mockResolvedValueOnce(okJson(fakeImpact()))

    await graphApi.getImpact('p1', 'com.example.Foo#bar(int)', 1)

    const url = String(fetchMock.mock.calls[0]![0])
    // URLSearchParams must percent-encode '#', '(' and ')'.
    expect(url).toContain('nodeId=com.example.Foo%23bar%28int%29')
    expect(url).not.toContain('#bar')
  })

  it('unwraps and returns the typed ImpactAnalysisResponse', async () => {
    fetchMock.mockResolvedValueOnce(okJson(fakeImpact()))

    const result = await graphApi.getImpact('p1', 'n1', 2)

    expect(result.riskLevel).toBe('HIGH')
    expect(result.directDependents).toBe(8)
    expect(result.totalDependents).toBe(12)
  })

  it('throws ApiError when the backend rejects the request', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      text: async () => JSON.stringify({ success: false, error: { message: 'depth must be one of 1, 2, 3, 5' } }),
    } as unknown as Response)

    await expect(graphApi.getImpact('p1', 'n1', 99)).rejects.toBeInstanceOf(ApiError)
  })
})
