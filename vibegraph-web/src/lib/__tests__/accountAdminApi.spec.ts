import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { accountApi, adminApi, ApiError, api, diagramApi } from '../api'

function success(data: unknown): Response {
  return {
    ok: true,
    status: 200,
    statusText: 'OK',
    json: async () => ({ success: true, data, error: null }),
    text: async () => JSON.stringify({ success: true, data, error: null }),
  } as unknown as Response
}

function failure(status: number, code: string, message: string, details?: string): Response {
  return {
    ok: false,
    status,
    statusText: 'Request failed',
    text: async () =>
      JSON.stringify({ success: false, data: null, error: { code, message, details } }),
  } as unknown as Response
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Phase 7 account API contract', () => {
  it('creates project-bound API keys with the repository id', async () => {
    fetchMock.mockResolvedValueOnce(
      success({
        id: 'key-1',
        keyPrefix: 'vg-abc12',
        name: 'CLI key',
        secretKey: 'secret',
        project: { id: 'project-1', name: 'VibeGraph', sourceType: 'GITHUB', status: 'READY' },
        createdAt: '2026-07-17T09:00:00Z',
        expiresAt: null,
      }),
    )

    const created = await accountApi.createApiKey({
      name: 'CLI key',
      projectId: 'project-1',
    })

    expect(fetchMock.mock.calls[0]?.[1]?.body).toBe(
      JSON.stringify({ name: 'CLI key', projectId: 'project-1' }),
    )
    expect(created.project).toEqual({
      id: 'project-1',
      name: 'VibeGraph',
      sourceType: 'GITHUB',
      status: 'READY',
    })
  })

  it('uses the real notification and announcement routes with cookie authentication', async () => {
    fetchMock.mockResolvedValue(success([]))

    await accountApi.listNotifications(25)
    await accountApi.listAnnouncements(10)
    await accountApi.getNotification('notification/1')
    await accountApi.markNotificationRead('notification/1')
    await accountApi.dismissNotification('notification/1')

    const urls = fetchMock.mock.calls.map(([url]) => String(url))
    expect(urls[0]).toMatch(/\/api\/account\/notifications\?limit=25$/)
    expect(urls[1]).toMatch(/\/api\/account\/announcements\?limit=10$/)
    expect(urls[2]).toMatch(/\/api\/account\/notifications\/notification%2F1$/)
    expect(urls[3]).toMatch(/\/api\/account\/notifications\/notification%2F1\/read$/)
    expect(urls[4]).toMatch(/\/api\/account\/notifications\/notification%2F1\/dismiss$/)

    for (const [, init] of fetchMock.mock.calls) {
      expect(init?.credentials).toBe('include')
      expect(init?.headers).toMatchObject({ 'X-VibeGraph-Client': 'web' })
      expect(init?.headers).not.toHaveProperty('Authorization')
    }
  })

  it('uses the real owner API key enable route', async () => {
    fetchMock.mockResolvedValue(success(null))

    await accountApi.enableApiKey('key/1')

    expect(String(fetchMock.mock.calls[0]?.[0])).toMatch(
      /\/api\/account\/api-keys\/key%2F1\/enable$/,
    )
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('PATCH')
    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe('include')
    expect(fetchMock.mock.calls[0]?.[1]?.headers).not.toHaveProperty('Authorization')
  })
})

describe('Phase 7 admin security and audit API contract', () => {
  it('does not expose an admin API key creation contract', () => {
    expect(adminApi).not.toHaveProperty('createApiKey')
    expect(adminApi).not.toHaveProperty('createApiKeyForUser')
  })

  it('never posts to the admin API key collection', async () => {
    fetchMock.mockResolvedValue(success([]))
    const postSpy = vi.spyOn(api, 'post')

    await adminApi.listApiKeysForUser('user-1')
    await adminApi.disableApiKey('key-1')
    await adminApi.lockApiKey('key-1')
    await adminApi.unlockApiKey('key-1')

    expect(postSpy).not.toHaveBeenCalled()
    expect(fetchMock.mock.calls.map(([, init]) => init?.method)).not.toContain('POST')
  })

  it('sends exact security queries and IP block mutations', async () => {
    fetchMock.mockResolvedValue(success([]))
    const block = {
      ipAddress: '203.0.113.10',
      safeReason: 'Excessive requests',
      expiresAt: null,
      active: true,
    }

    await adminApi.listRequestEvents(75)
    await adminApi.listTopUsers(30, 5)
    await adminApi.listTopIps(45, 7)
    await adminApi.createIpBlock(block)
    await adminApi.updateIpBlock('block/1', block)

    const urls = fetchMock.mock.calls.map(([url]) => String(url))
    expect(urls[0]).toMatch(/request-events\?limit=75$/)
    expect(urls[1]).toMatch(/top-users\?minutes=30&limit=5$/)
    expect(urls[2]).toMatch(/suspicious-networks\?minutes=45&limit=7$/)
    expect(urls[3]).toMatch(/\/api\/admin\/security\/ip-blocks$/)
    expect(urls[4]).toMatch(/\/api\/admin\/security\/ip-blocks\/block%2F1$/)
    expect(fetchMock.mock.calls[3]?.[1]?.body).toBe(JSON.stringify(block))
    expect(fetchMock.mock.calls[4]?.[1]?.method).toBe('PATCH')
  })

  it('encodes audit filters and retention updates', async () => {
    fetchMock.mockResolvedValue(success({ content: [], totalElements: 0, totalPages: 0 }))

    await adminApi.listAuditLogs({
      action: 'IP BLOCK',
      outcome: 'SUCCESS',
      actorUserId: 'actor/1',
      page: 2,
      size: 25,
    })
    await adminApi.updateAuditRetention(180)

    const auditUrl = new URL(String(fetchMock.mock.calls[0]?.[0]))
    expect(auditUrl.pathname).toBe('/api/admin/audit-logs')
    expect(auditUrl.searchParams.get('action')).toBe('IP BLOCK')
    expect(auditUrl.searchParams.get('actorUserId')).toBe('actor/1')
    expect(auditUrl.searchParams.get('page')).toBe('2')
    expect(fetchMock.mock.calls[1]?.[1]?.method).toBe('PUT')
    expect(fetchMock.mock.calls[1]?.[1]?.body).toBe(JSON.stringify({ retentionDays: 180 }))
  })
})

describe('diagram API contract', () => {
  it('only exposes the supported use-case diagram endpoint', () => {
    expect(Object.keys(diagramApi).sort()).toEqual(['umlUseCase'])
  })
})

describe('typed API errors', () => {
  it.each([
    ['ACCOUNT_BLOCKED', 403],
    ['ACCOUNT_DEACTIVATED', 403],
    ['FEATURE_DISABLED', 403],
    ['QUOTA_EXCEEDED', 413],
    ['CREDIT_EXHAUSTED', 402],
    ['CONCURRENT_IMPORT_LIMIT', 409],
    ['TOO_MANY_REQUESTS', 429],
    ['IP_BLOCKED', 403],
  ])('preserves %s metadata', async (code, status) => {
    fetchMock.mockResolvedValueOnce(failure(status, code, 'Safe message', 'Safe details'))

    const error = await api.get('/api/test').catch((cause: unknown) => cause)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status, code, message: 'Safe message', details: 'Safe details' })
  })

  it('preserves a typed error in a successful HTTP envelope', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      status: 200,
      statusText: 'OK',
      json: async () => ({
        success: false,
        data: null,
        error: { code: 'FEATURE_DISABLED', message: 'Feature is disabled.' },
      }),
    } as unknown as Response)

    await expect(api.get('/api/test')).rejects.toMatchObject({
      status: 400,
      code: 'FEATURE_DISABLED',
      message: 'Feature is disabled.',
    })
  })
})
