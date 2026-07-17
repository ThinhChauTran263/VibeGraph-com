import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { importApi, ApiError } from '../api'

/**
 * These tests exercise the real `importApi` against a mocked global `fetch`,
 * verifying the exact URL, method, and multipart body fields per the backend
 * contract. The sync vs async distinction is purely the `?async=true` query.
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

function makeFile(name: string): File {
  return new File([new Uint8Array([1, 2, 3])], name, { type: 'application/zip' })
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('importApi.uploadArchive (sync)', () => {
  it('POSTs to /api/projects/import-archive WITHOUT the async query', async () => {
    fetchMock.mockResolvedValueOnce(okJson({ id: 'p1', status: 'ANALYZED', progress: 100 }))

    await importApi.uploadArchive('demo', makeFile('demo.zip'))

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const call = fetchMock.mock.calls[0]!
    const url = call[0]
    const init = call[1]!
    expect(String(url)).toMatch(/\/api\/projects\/import-archive$/)
    expect(String(url)).not.toContain('async=true')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
  })

  it('sends name and file as multipart fields and does not set Content-Type', async () => {
    fetchMock.mockResolvedValueOnce(okJson({ id: 'p1', status: 'ANALYZED' }))

    await importApi.uploadArchive('my-svc', makeFile('a.zip'))

    const init = fetchMock.mock.calls[0]![1]!
    const form = init.body as FormData
    expect(form.get('name')).toBe('my-svc')
    expect(form.get('file')).toBeInstanceOf(File)
    // Content-Type must be left for the browser to compute (multipart boundary).
    expect(init.headers).toMatchObject({ 'X-VibeGraph-Client': 'web' })
    expect(init.headers).not.toHaveProperty('Content-Type')
  })
})

describe('importApi.importGithub', () => {
  it('POSTs to /api/projects/import-github with a JSON URL payload', async () => {
    fetchMock.mockResolvedValueOnce(okJson({ id: 'gh-1', status: 'ANALYZING' }))

    await importApi.importGithub('https://github.com/owner/repo')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const call = fetchMock.mock.calls[0]!
    const url = call[0]
    const init = call[1]!
    expect(String(url)).toMatch(/\/api\/projects\/import-github$/)
    expect(init.method).toBe('POST')
    expect(init.headers).toEqual({
      'Content-Type': 'application/json',
      'X-VibeGraph-Client': 'web',
    })
    expect(init.body).toBe(JSON.stringify({ url: 'https://github.com/owner/repo' }))
  })

  it('throws ApiError with the safe backend GitHub import message', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 422,
      statusText: 'Unprocessable Entity',
      text: async () =>
        JSON.stringify({
          success: false,
          error: {
            code: 'GITHUB_IMPORT_ERROR',
            message: 'GitHub repository is private or not found',
          },
        }),
    } as unknown as Response)

    await expect(importApi.importGithub('https://github.com/owner/private')).rejects.toMatchObject({
      status: 422,
      message: 'GitHub repository is private or not found',
    })
  })
})

describe('importApi.uploadArchiveAsync', () => {
  it('POSTs to /api/projects/import-archive?async=true', async () => {
    fetchMock.mockResolvedValueOnce(okJson({ id: 'p2', status: 'ANALYZING', progress: 0 }))

    await importApi.uploadArchiveAsync('demo', makeFile('demo.zip'))

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const call = fetchMock.mock.calls[0]!
    const url = call[0]
    const init = call[1]!
    expect(String(url)).toContain('/api/projects/import-archive?async=true')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
  })

  it('returns the parsed ANALYZING project with progress', async () => {
    fetchMock.mockResolvedValueOnce(
      okJson({ id: 'p2', name: 'demo', status: 'ANALYZING', progress: 0 }),
    )

    const project = await importApi.uploadArchiveAsync('demo', makeFile('demo.zip'))

    expect(project.id).toBe('p2')
    expect(project.status).toBe('ANALYZING')
    expect(project.progress).toBe(0)
  })

  it('throws ApiError when the server rejects the async upload', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      text: async () => JSON.stringify({ success: false, error: { message: 'no java files' } }),
    } as unknown as Response)

    await expect(importApi.uploadArchiveAsync('demo', makeFile('demo.zip'))).rejects.toBeInstanceOf(
      ApiError,
    )
  })
})
