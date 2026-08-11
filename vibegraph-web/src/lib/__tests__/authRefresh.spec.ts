import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchWithSessionRefresh, refreshBrowserSession } from '@/lib/authRefresh'

describe('auth refresh', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('uses one in-flight refresh request for concurrent callers', async () => {
    let resolveRequest: ((response: Response) => void) | undefined
    const fetchMock = vi.fn<typeof fetch>(
      () => new Promise<Response>((resolve) => {
        resolveRequest = resolve
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const first = refreshBrowserSession()
    const second = refreshBrowserSession()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    resolveRequest?.(new Response(JSON.stringify({ success: true }), { status: 200 }))
    await expect(Promise.all([first, second])).resolves.toEqual([true, true])
  })

  it('returns false when the refresh endpoint rejects the session', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(new Response('', { status: 401 })),
    )

    await expect(refreshBrowserSession()).resolves.toBe(false)
  })

  it('retries a protected request once after a successful refresh', async () => {
    const fetchMock = vi.fn<typeof fetch>()
    fetchMock
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response('', { status: 200 }))
      .mockResolvedValueOnce(new Response('{"success":true}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await fetchWithSessionRefresh('/api/projects', {
      credentials: 'include',
      headers: { 'X-VibeGraph-Client': 'web' },
    })

    expect(response.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('returns the original 401 when refresh fails', async () => {
    const original = new Response('', { status: 401 })
    const fetchMock = vi.fn<typeof fetch>()
    fetchMock
      .mockResolvedValueOnce(original)
      .mockResolvedValueOnce(new Response('', { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchWithSessionRefresh('/api/projects')).resolves.toBe(original)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
