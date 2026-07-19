import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAdminStore } from '../admin'
import * as apiModule from '../../lib/api'
import type { AdminOverview, AdminUserResponse, PagedResponse } from '../../types/api'

vi.mock('../../lib/api', async (importOriginal) => {
  const original = await importOriginal<typeof apiModule>()
  return {
    ...original,
    adminApi: {
      getOverview: vi.fn(),
      listPlans: vi.fn(),
      createPlan: vi.fn(),
      updateCatalogPlan: vi.fn(),
      deleteCatalogPlan: vi.fn(),
      listPricingRules: vi.fn(),
      createPricingRule: vi.fn(),
      updatePricingRule: vi.fn(),
      deletePricingRule: vi.fn(),
      listUsers: vi.fn(),
      getUserDetail: vi.fn(),
      createUser: vi.fn(),
      blockUser: vi.fn(),
      unblockUser: vi.fn(),
      deactivateUser: vi.fn(),
      updatePlan: vi.fn(),
      updateQuota: vi.fn(),
      updateApiKeyCreation: vi.fn(),
      listApiKeysForUser: vi.fn(),
      disableApiKey: vi.fn(),
      lockApiKey: vi.fn(),
      unlockApiKey: vi.fn(),
      getCreditOverview: vi.fn(),
      adjustCredits: vi.fn(),
      listReports: vi.fn(),
      getReportDetail: vi.fn(),
      replyToReport: vi.fn(),
      closeReport: vi.fn(),
      listFeatureFlags: vi.fn(),
      createFeatureFlag: vi.fn(),
      updateFeatureFlag: vi.fn(),
      deleteFeatureFlag: vi.fn(),
      listAnnouncements: vi.fn(),
      createAnnouncement: vi.fn(),
      updateAnnouncement: vi.fn(),
      disableAnnouncement: vi.fn(),
      deleteAnnouncement: vi.fn(),
      listSecurityEvents: vi.fn(),
      listRequestEvents: vi.fn(),
      listTopUsers: vi.fn(),
      listTopIps: vi.fn(),
      listIpBlocks: vi.fn(),
      createIpBlock: vi.fn(),
      updateIpBlock: vi.fn(),
      deleteIpBlock: vi.fn(),
      listAuditLogs: vi.fn(),
      getAuditLog: vi.fn(),
      getAuditRetention: vi.fn(),
      updateAuditRetention: vi.fn(),
    },
  }
})

const mockAdminApi = vi.mocked(apiModule.adminApi)

class MockEventSource {
  static readonly instances: MockEventSource[] = []
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2
  readonly listeners = new Map<string, EventListener[]>()
  readonly close = vi.fn(() => {
    this.readyState = MockEventSource.CLOSED
  })
  readyState = MockEventSource.CONNECTING
  onopen: ((event: Event) => void) | null = null
  onerror: ((event: Event) => void) | null = null

  constructor(
    readonly url: string,
    readonly options?: EventSourceInit,
  ) {
    MockEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: EventListener): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  emitOpen(): void {
    this.readyState = MockEventSource.OPEN
    this.onopen?.(new Event('open'))
  }

  emitError(): void {
    this.onError(new Event('error'))
  }

  emitRequest(data: string): void {
    const event = new MessageEvent('request-event', { data })
    for (const listener of this.listeners.get('request-event') ?? []) listener(event)
  }

  emitAudit(data: string): void {
    const event = new MessageEvent('audit-log', { data })
    for (const listener of this.listeners.get('audit-log') ?? []) listener(event)
  }

  private onError(event: Event): void {
    this.onerror?.(event)
  }
}

describe('Admin Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    MockEventSource.instances.length = 0
    vi.stubGlobal('EventSource', MockEventSource)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('initializes with default state', () => {
    const store = useAdminStore()
    expect(store.overview).toBeNull()
    expect(store.plans).toEqual([])
    expect(store.pricingRules).toEqual([])
    expect(store.users).toEqual([])
    expect(store.reports).toEqual([])
    expect(store.requestEvents).toEqual([])
    expect(store.topUsers).toEqual([])
    expect(store.topIps).toEqual([])
    expect(store.ipBlocks).toEqual([])
    expect(store.auditLogs).toEqual([])
    expect(store.auditLogDetail).toBeNull()
    expect(store.auditRetention).toBeNull()
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('fetchSecurityData populates all security panels', async () => {
    mockAdminApi.listSecurityEvents.mockResolvedValueOnce([])
    mockAdminApi.listRequestEvents.mockResolvedValueOnce([
      {
        id: 'request-1',
        userId: 'user-1',
        userDisplayName: 'Alice',
        userEmail: 'alice@example.com',
        apiKeyRef: null,
        ipAddress: '203.0.113.10',
        route: '/api/projects',
        method: 'GET',
        status: 429,
        eventType: 'RATE_LIMITED',
        occurredAt: '2026-07-17T09:00:00Z',
      },
    ])
    mockAdminApi.listTopUsers.mockResolvedValueOnce([])
    mockAdminApi.listTopIps.mockResolvedValueOnce([
      {
        ipAddress: '203.0.113.10',
        minuteBucket: '2026-07-17T09:00:00Z',
        totalRequests: 42,
        uniqueUsers: 2,
        uniqueApiKeys: 1,
        breakdown: [],
      },
    ])
    mockAdminApi.listIpBlocks.mockResolvedValueOnce([])

    const store = useAdminStore()
    await store.fetchSecurityData(75)

    expect(mockAdminApi.listRequestEvents).toHaveBeenCalledWith(75)
    expect(store.requestEvents[0]?.status).toBe(429)
    expect(store.topIps[0]?.uniqueUsers).toBe(2)
    expect(store.loading).toBe(false)
  })

  it('reports suspicious networks independently when their aggregate request fails', async () => {
    mockAdminApi.listSecurityEvents.mockResolvedValueOnce([])
    mockAdminApi.listRequestEvents.mockResolvedValueOnce([])
    mockAdminApi.listTopUsers.mockResolvedValueOnce([])
    mockAdminApi.listTopIps.mockRejectedValueOnce(new Error('Network aggregate unavailable'))
    mockAdminApi.listIpBlocks.mockResolvedValueOnce([])

    const store = useAdminStore()

    await expect(store.fetchSecurityData()).resolves.toContain('suspicious networks')
  })

  it('prepends live request events and keeps at most 100 rows', () => {
    const store = useAdminStore()
    store.requestEvents = Array.from({ length: 100 }, (_, index) => ({
      id: `existing-${index}`,
      userId: null,
      userDisplayName: null,
      userEmail: null,
      apiKeyRef: null,
      ipAddress: '203.0.113.10',
      route: '/api/projects',
      method: 'GET',
      status: 200,
      eventType: 'REQUEST',
      occurredAt: `2026-07-19T10:${String(index).padStart(2, '0')}:00Z`,
    }))

    store.startSecurityStream()
    const source = MockEventSource.instances[0]
    source?.emitRequest(JSON.stringify({
      id: 'live-1',
      userId: 'user-1',
      userDisplayName: 'Alice',
      userEmail: 'alice@example.com',
      apiKeyRef: 'vbg_live1****',
      ipAddress: '203.0.113.11',
      route: '/api/projects',
      method: 'POST',
      status: 201,
      eventType: 'REQUEST',
      occurredAt: '2026-07-19T11:00:00Z',
    }))

    expect(store.requestEvents).toHaveLength(100)
    expect(store.requestEvents[0]?.id).toBe('live-1')
    expect(store.requestEvents.some((event) => event.id === 'existing-0')).toBe(true)
    expect(store.requestEvents.some((event) => event.id === 'existing-99')).toBe(false)
  })

  it('debounces aggregate refresh while request events arrive in a burst', async () => {
    vi.useFakeTimers()
    mockAdminApi.listTopUsers.mockResolvedValue([])
    mockAdminApi.listTopIps.mockResolvedValue([])
    const store = useAdminStore()
    store.startSecurityStream()
    const source = MockEventSource.instances[0]
    const event = {
      id: 'live-debounce',
      userId: null,
      userDisplayName: null,
      userEmail: null,
      apiKeyRef: null,
      ipAddress: '203.0.113.10',
      route: '/api/projects',
      method: 'GET',
      status: 200,
      eventType: 'REQUEST',
      occurredAt: '2026-07-19T11:00:00Z',
    }
    source?.emitRequest(JSON.stringify(event))
    source?.emitRequest(JSON.stringify({ ...event, id: 'live-debounce-2' }))

    await vi.advanceTimersByTimeAsync(1499)
    expect(mockAdminApi.listTopUsers).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(mockAdminApi.listTopUsers).toHaveBeenCalledTimes(1)
    expect(mockAdminApi.listTopIps).toHaveBeenCalledTimes(1)
  })

  it('tracks reconnecting and paused states and closes the stream on stop', () => {
    const store = useAdminStore()
    store.startSecurityStream()
    const source = MockEventSource.instances[0]
    source?.emitOpen()
    expect(store.securityLiveStatus).toBe('connected')
    source?.emitError()
    expect(store.securityLiveStatus).toBe('reconnecting')

    store.stopSecurityStream()

    expect(source?.close).toHaveBeenCalledTimes(1)
    expect(store.securityLiveStatus).toBe('paused')
  })

  it('pauses live state when EventSource is unavailable', () => {
    vi.stubGlobal('EventSource', undefined)
    const store = useAdminStore()

    store.startSecurityStream()

    expect(store.securityLiveStatus).toBe('paused')
  })

  it('refreshes only IP blocks after create, update, and delete mutations', async () => {
    const block = {
      id: 'block-1',
      ipAddress: '203.0.113.10',
      safeReason: 'Excessive requests',
      expiresAt: null,
      active: true,
      createdBy: null,
      createdAt: null,
      updatedAt: null,
    }
    const updated = { ...block, safeReason: 'Repeated excessive requests' }
    mockAdminApi.createIpBlock.mockResolvedValueOnce(block)
    mockAdminApi.updateIpBlock.mockResolvedValueOnce(updated)
    mockAdminApi.deleteIpBlock.mockResolvedValueOnce(undefined)
    mockAdminApi.listIpBlocks
      .mockResolvedValueOnce([block])
      .mockResolvedValueOnce([updated])
      .mockResolvedValueOnce([])

    const store = useAdminStore()
    const payload = {
      ipAddress: block.ipAddress,
      safeReason: block.safeReason,
      expiresAt: null,
      active: true,
    }

    await expect(store.createIpBlock(payload)).resolves.toEqual({ refreshFailed: false })
    await expect(store.updateIpBlock(block.id, { ...payload, safeReason: updated.safeReason })).resolves.toEqual({ refreshFailed: false })
    await expect(store.deleteIpBlock(block.id)).resolves.toEqual({ refreshFailed: false })

    expect(mockAdminApi.listIpBlocks).toHaveBeenCalledTimes(3)
    expect(mockAdminApi.listSecurityEvents).not.toHaveBeenCalled()
    expect(mockAdminApi.listRequestEvents).not.toHaveBeenCalled()
    expect(mockAdminApi.listTopUsers).not.toHaveBeenCalled()
    expect(mockAdminApi.listTopIps).not.toHaveBeenCalled()
    expect(store.ipBlocks).toEqual([])
  })

  it('keeps a successful IP block write successful when its collection refresh fails', async () => {
    const block = {
      id: 'block-1',
      ipAddress: '203.0.113.10',
      safeReason: 'Excessive requests',
      expiresAt: null,
      active: true,
      createdBy: null,
      createdAt: null,
      updatedAt: null,
    }
    mockAdminApi.createIpBlock.mockResolvedValueOnce(block)
    mockAdminApi.listIpBlocks.mockRejectedValueOnce(new Error('IP block refresh unavailable'))

    const store = useAdminStore()
    await expect(
      store.createIpBlock({
        ipAddress: block.ipAddress,
        safeReason: block.safeReason,
        expiresAt: null,
        active: true,
      }),
    ).resolves.toEqual({ refreshFailed: true })

    expect(store.ipBlocks).toEqual([block])
    expect(store.error).toBeNull()
  })

  it('refreshes audit logs and retention after retention mutation', async () => {
    mockAdminApi.updateAuditRetention.mockResolvedValueOnce({
      retentionDays: 180,
      updatedBy: 'admin-1',
      updatedAt: '2026-07-17T09:00:00Z',
    })
    mockAdminApi.listAuditLogs.mockResolvedValueOnce({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageNumber: 0,
      pageSize: 50,
    })
    mockAdminApi.getAuditRetention.mockResolvedValueOnce({
      retentionDays: 180,
      updatedBy: 'admin-1',
      updatedAt: '2026-07-17T09:00:00Z',
    })

    const store = useAdminStore()
    await store.updateAuditRetention(180)

    expect(mockAdminApi.updateAuditRetention).toHaveBeenCalledWith(180)
    expect(mockAdminApi.listAuditLogs).toHaveBeenCalled()
    expect(mockAdminApi.getAuditRetention).toHaveBeenCalled()
    expect(store.auditRetention?.retentionDays).toBe(180)
  })

  it('prepends matching live audit rows only on the first filtered page', async () => {
    const existing = {
      id: 'audit-existing',
      action: 'USER_BLOCK',
      actorUserId: 'admin-1',
      targetUserId: 'user-1',
      targetType: 'USER',
      targetId: 'user-1',
      outcome: 'SUCCESS',
      ipAddress: '127.0.0.1',
      details: '{}',
      createdAt: '2026-07-19T09:00:00Z',
    }
    mockAdminApi.listAuditLogs.mockResolvedValueOnce({
      content: [existing],
      totalElements: 1,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 2,
    })
    const store = useAdminStore()
    await store.fetchAuditLogs({ action: 'USER_BLOCK', outcome: 'SUCCESS', page: 0, size: 2 })
    store.auditLogDetail = existing
    store.startAuditStream()
    const source = MockEventSource.instances[0]
    const live = {
      ...existing,
      id: 'audit-live',
      targetUserId: 'user-2',
      targetId: 'user-2',
      details: null,
      createdAt: '2026-07-19T10:00:00Z',
    }

    source?.emitAudit(JSON.stringify(live))

    expect(store.auditLogs.map((log) => log.id)).toEqual(['audit-live', 'audit-existing'])
    expect(store.auditPagination.totalElements).toBe(2)
    expect(store.auditLogDetail?.id).toBe('audit-existing')

    source?.emitAudit(JSON.stringify({ ...live, id: 'audit-nonmatch', action: 'USER_UNBLOCK' }))
    expect(store.auditLogs.map((log) => log.id)).toEqual(['audit-live', 'audit-existing'])
  })

  it('keeps later audit pages unchanged when a live row arrives', async () => {
    const pageRow = {
      id: 'audit-page-two',
      action: 'USER_BLOCK',
      actorUserId: 'admin-1',
      targetUserId: 'user-3',
      targetType: 'USER',
      targetId: 'user-3',
      outcome: 'SUCCESS',
      ipAddress: '127.0.0.1',
      details: '{}',
      createdAt: '2026-07-18T10:00:00Z',
    }
    mockAdminApi.listAuditLogs.mockResolvedValueOnce({
      content: [pageRow],
      totalElements: 51,
      totalPages: 2,
      pageNumber: 1,
      pageSize: 50,
    })
    const store = useAdminStore()
    await store.fetchAuditLogs({ action: 'USER_BLOCK', page: 1, size: 50 })
    store.startAuditStream()

    MockEventSource.instances[0]?.emitAudit(JSON.stringify({ ...pageRow, id: 'audit-live' }))

    expect(store.auditLogs).toEqual([pageRow])
    expect(store.auditPagination.pageNumber).toBe(1)
  })

  it('tracks audit stream connection state, falls back to polling, and closes it on stop', async () => {
    vi.useFakeTimers()
    mockAdminApi.listAuditLogs.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageNumber: 0,
      pageSize: 50,
    })
    const store = useAdminStore()
    store.startAuditStream()
    const source = MockEventSource.instances[0]
    source?.emitOpen()
    expect(store.auditLiveStatus).toBe('connected')

    source?.emitError()
    expect(store.auditLiveStatus).toBe('polling')
    await vi.advanceTimersByTimeAsync(10000)
    expect(mockAdminApi.listAuditLogs).toHaveBeenCalledWith({ page: 0, size: 50 })

    store.stopAuditStream()
    expect(source?.close).toHaveBeenCalledTimes(1)
    expect(store.auditLiveStatus).toBe('paused')
  })

  it('uses audit polling when EventSource is unavailable', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('EventSource', undefined)
    mockAdminApi.listAuditLogs.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageNumber: 0,
      pageSize: 50,
    })
    const store = useAdminStore()

    store.startAuditStream()

    expect(store.auditLiveStatus).toBe('polling')
    await vi.advanceTimersByTimeAsync(10000)
    expect(mockAdminApi.listAuditLogs).toHaveBeenCalledWith({ page: 0, size: 50 })
    store.stopAuditStream()
  })

  it('stores and rethrows admin request errors', async () => {
    const failure = new Error('Security unavailable')
    mockAdminApi.listSecurityEvents.mockRejectedValueOnce(failure)

    const store = useAdminStore()
    await expect(store.fetchSecurityEvents()).rejects.toBe(failure)
    expect(store.error).toBe(failure)
    expect(store.loading).toBe(false)
  })

  it('fetchPlans and fetchPricingRules populate read-only admin catalogs', async () => {
    mockAdminApi.listPlans.mockResolvedValueOnce([
      {
        code: 'FREE',
        name: 'Free',
        storageLimitMb: 500,
        apiKeyLimit: 3,
        monthlyCreditLimit: 100,
        contactSalesRequired: false,
      },
    ])
    mockAdminApi.listPricingRules.mockResolvedValueOnce([
      {
        operationCode: 'IMPORT_REPOSITORY',
        displayName: 'Import repository',
        baseCredits: 1,
        perFileCredits: 0.01,
        perMbCredits: 0.1,
        per1kNodesCredits: 0.5,
        minimumCredits: 1,
        active: true,
      },
    ])

    const store = useAdminStore()
    await store.fetchPlans()
    await store.fetchPricingRules()

    expect(store.plans[0]?.code).toBe('FREE')
    expect(store.pricingRules[0]?.operationCode).toBe('IMPORT_REPOSITORY')
  })

  it('savePlan creates, updates, and deletes via admin catalog CRUD', async () => {
    const created = {
      code: 'TEAM',
      name: 'Team',
      storageLimitMb: 1024,
      apiKeyLimit: 5,
      monthlyCreditLimit: 1000,
      contactSalesRequired: false,
    }
    mockAdminApi.createPlan.mockResolvedValueOnce(created)
    mockAdminApi.updateCatalogPlan.mockResolvedValueOnce({ ...created, name: 'Team Plus' })
    mockAdminApi.deleteCatalogPlan.mockResolvedValueOnce(undefined)

    const store = useAdminStore()
    await store.savePlan({ ...created, active: true, sortOrder: 1 })
    await store.savePlan({ ...created, name: 'Team Plus', active: true, sortOrder: 1 }, 'TEAM')
    await store.deletePlan('TEAM')

    expect(mockAdminApi.createPlan).toHaveBeenCalledWith({ ...created, active: true, sortOrder: 1 })
    expect(mockAdminApi.updateCatalogPlan).toHaveBeenCalledWith('TEAM', {
      ...created,
      name: 'Team Plus',
      active: true,
      sortOrder: 1,
    })
    expect(store.plans).toEqual([])
  })

  it('savePricingRule creates, updates, and deletes via admin pricing CRUD', async () => {
    const created = {
      operationCode: 'IMPORT_REPOSITORY',
      displayName: 'Import repository',
      baseCredits: 1,
      perFileCredits: 0.01,
      perMbCredits: 0.1,
      per1kNodesCredits: 0.5,
      minimumCredits: 1,
      active: true,
    }
    mockAdminApi.createPricingRule.mockResolvedValueOnce(created)
    mockAdminApi.updatePricingRule.mockResolvedValueOnce({ ...created, active: false })
    mockAdminApi.deletePricingRule.mockResolvedValueOnce(undefined)

    const store = useAdminStore()
    await store.savePricingRule(created)
    await store.savePricingRule({ ...created, active: false }, 'IMPORT_REPOSITORY')
    await store.deletePricingRule('IMPORT_REPOSITORY')

    expect(mockAdminApi.createPricingRule).toHaveBeenCalledWith(created)
    expect(mockAdminApi.updatePricingRule).toHaveBeenCalledWith('IMPORT_REPOSITORY', {
      ...created,
      active: false,
    })
    expect(store.pricingRules).toEqual([])
  })

  it('fetchOverview populates overview from API', async () => {
    const mockOverview: AdminOverview = {
      totalUsers: 150,
      onlineUsers: 5,
      totalProjects: 320,
      totalReports: 10,
      openReports: 3,
      blockedUsers: 2,
      timestamp: new Date().toISOString(),
    }
    mockAdminApi.getOverview.mockResolvedValueOnce(mockOverview)

    const store = useAdminStore()
    await store.fetchOverview()

    expect(store.overview?.totalUsers).toBe(150)
    expect(store.overview?.onlineUsers).toBe(5)
    expect(store.overview?.openReports).toBe(3)
  })

  it('fetchUsers populates users list with pagination meta', async () => {
    const mockUser: AdminUserResponse = {
      id: 'usr-1',
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'USER',
      deactivated: false,
      deactivationReason: null,
      deactivationReasonSafe: null,
      blocked: false,
      blockedReason: null,
      blockedReasonSafe: null,
      planCode: 'FREE',
      storageQuotaOverrideBytes: null,
      creditQuotaOverride: null,
      quotaBytes: 500 * 1024 * 1024,
      usedBytes: 50 * 1024 * 1024,
      apiKeyCreationDisabled: false,
    }
    const mockPage: PagedResponse<AdminUserResponse> = {
      content: [mockUser],
      totalElements: 1,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 20,
    }
    mockAdminApi.listUsers.mockResolvedValueOnce(mockPage)

    const store = useAdminStore()
    await store.fetchUsers()

    expect(store.users.length).toBe(1)
    expect(store.users[0]?.email).toBe('alice@example.com')
    expect(store.usersPagination.totalElements).toBe(1)
  })

  it('blockUser updates user in list', async () => {
    const original: AdminUserResponse = {
      id: 'usr-1',
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'USER',
      deactivated: false,
      deactivationReason: null,
      deactivationReasonSafe: null,
      blocked: false,
      blockedReason: null,
      blockedReasonSafe: null,
      planCode: 'FREE',
      storageQuotaOverrideBytes: null,
      creditQuotaOverride: null,
      quotaBytes: 500 * 1024 * 1024,
      usedBytes: 0,
      apiKeyCreationDisabled: false,
    }
    const blocked: AdminUserResponse = { ...original, blocked: true, blockedReasonSafe: 'Spam' }

    mockAdminApi.listUsers.mockResolvedValueOnce({
      content: [original],
      totalElements: 1,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 20,
    })
    mockAdminApi.blockUser.mockResolvedValueOnce(blocked)

    const store = useAdminStore()
    await store.fetchUsers()
    await store.blockUser('usr-1', 'Spam - internal', 'Spam')

    expect(store.users[0]?.blocked).toBe(true)
    expect(store.users[0]?.blockedReasonSafe).toBe('Spam')
  })
})
