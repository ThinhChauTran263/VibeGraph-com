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
      listReports: vi.fn(),
      getReportDetail: vi.fn(),
      replyToReport: vi.fn(),
      closeReport: vi.fn(),
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
  })

  it('fetchPlans and fetchPricingRules populate read-only admin catalogs', async () => {
    mockAdminApi.listPlans.mockResolvedValueOnce([
      {
        code: 'FREE',
        name: 'Free',
        storageLimitBytes: 500 * 1024 * 1024,
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
      storageLimitBytes: 1024,
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
