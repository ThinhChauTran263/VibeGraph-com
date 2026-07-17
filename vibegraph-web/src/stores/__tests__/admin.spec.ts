import { describe, it, expect, beforeEach, vi } from 'vitest'
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
      createApiKeyForUser: vi.fn(),
      disableApiKey: vi.fn(),
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

describe('Admin Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
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
    mockAdminApi.listTopIps.mockResolvedValueOnce([])
    mockAdminApi.listIpBlocks.mockResolvedValueOnce([])

    const store = useAdminStore()
    await store.fetchSecurityData(75)

    expect(mockAdminApi.listRequestEvents).toHaveBeenCalledWith(75)
    expect(store.requestEvents[0]?.status).toBe(429)
    expect(store.loading).toBe(false)
  })

  it('refreshes security state after IP block mutations', async () => {
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
    mockAdminApi.listSecurityEvents.mockResolvedValue([])
    mockAdminApi.listRequestEvents.mockResolvedValue([])
    mockAdminApi.listTopUsers.mockResolvedValue([])
    mockAdminApi.listTopIps.mockResolvedValue([])
    mockAdminApi.listIpBlocks.mockResolvedValueOnce([block])

    const store = useAdminStore()
    await store.createIpBlock({
      ipAddress: block.ipAddress,
      safeReason: block.safeReason,
      expiresAt: null,
      active: true,
    })

    expect(mockAdminApi.createIpBlock).toHaveBeenCalledOnce()
    expect(mockAdminApi.listIpBlocks).toHaveBeenCalled()
    expect(store.ipBlocks).toEqual([block])
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
