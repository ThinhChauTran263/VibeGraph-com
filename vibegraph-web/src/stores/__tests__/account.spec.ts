import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAccountStore } from '../account'
import * as apiModule from '../../lib/api'
import type { ApiKeyCreated, ApiKey, UserProfile, UserUsage, Report } from '../../types/api'

// Mock the accountApi used by the store
vi.mock('../../lib/api', async (importOriginal) => {
  const original = await importOriginal<typeof apiModule>()
  return {
    ...original,
    accountApi: {
      getProfile: vi.fn(),
      updateProfile: vi.fn(),
      changePassword: vi.fn(),
      getUsage: vi.fn(),
      getCreditLedger: vi.fn(),
      getProjects: vi.fn(),
      listApiKeys: vi.fn(),
      createApiKey: vi.fn(),
      disableApiKey: vi.fn(),
      listReports: vi.fn(),
      createReport: vi.fn(),
      getReportDetail: vi.fn(),
      addMessage: vi.fn(),
      closeReport: vi.fn(),
    },
  }
})

const mockAccountApi = vi.mocked(apiModule.accountApi)

describe('Account Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('initializes with default state', () => {
    const store = useAccountStore()
    expect(store.profile).toBeNull()
    expect(store.usage).toBeNull()
    expect(store.creditLedger).toEqual([])
    expect(store.projects).toEqual([])
    expect(store.apiKeys).toEqual([])
    expect(store.reports).toEqual([])
  })

  it('fetchProfile populates profile from API', async () => {
    const mockProfile: UserProfile = {
      id: 'usr-1',
      email: 'test@example.com',
      displayName: 'Test User',
      role: 'USER',
      accountStatus: 'ACTIVE',
      status: 'active',
    }
    mockAccountApi.getProfile.mockResolvedValueOnce(mockProfile)

    const store = useAccountStore()
    await store.fetchProfile()

    expect(store.profile).not.toBeNull()
    expect(store.profile?.email).toBe('test@example.com')
    expect(store.profile?.status).toBe('active')
  })

  it('fetchUsage populates usage and derives MB helpers', async () => {
    const mockUsage: UserUsage = {
      usedBytes: 10 * 1024 * 1024,
      limitBytes: 100 * 1024 * 1024,
      remainingBytes: 90 * 1024 * 1024,
      planCode: 'FREE',
      planName: 'Free Tier',
      quotaOverrideBytes: null,
    }
    mockAccountApi.getUsage.mockResolvedValueOnce(mockUsage)

    const store = useAccountStore()
    await store.fetchUsage()

    expect(store.usage?.planName).toBe('Free Tier')
    expect(store.usage?.sourceStorageUsed).toBe(10)
    expect(store.usage?.sourceStorageLimit).toBe(100)
    expect(store.usage?.creditsUsed).toBeUndefined()
    expect(store.usage?.creditsLimit).toBeUndefined()
  })

  it('fetchCreditLedger populates recent credit activity', async () => {
    mockAccountApi.getCreditLedger.mockResolvedValueOnce([
      {
        id: 'ledger-1',
        source: 'CLI',
        operationCode: 'CLI_PUSH',
        creditsDelta: -2,
        projectId: 'project-1',
        createdAt: '2026-07-14T12:00:00Z',
      },
    ])

    const store = useAccountStore()
    await store.fetchCreditLedger(10)

    expect(mockAccountApi.getCreditLedger).toHaveBeenCalledWith(10)
    expect(store.creditLedger).toHaveLength(1)
    expect(store.creditLedger[0]?.operationCode).toBe('CLI_PUSH')
  })

  it('changePassword forwards old, new, and confirmation passwords to the API', async () => {
    mockAccountApi.changePassword.mockResolvedValueOnce(undefined)

    const store = useAccountStore()
    await store.changePassword('old-password', 'new-password', 'new-password')

    expect(mockAccountApi.changePassword).toHaveBeenCalledWith(
      'old-password',
      'new-password',
      'new-password',
    )
  })

  it('createApiKey calls API, prepends key to list, returns secret', async () => {
    const created: ApiKeyCreated = {
      id: 'key-1',
      keyPrefix: 'vg-abc12',
      name: 'My Key',
      secretKey: 'vg-supersecretvalue',
      createdAt: new Date().toISOString(),
      expiresAt: null,
    }
    mockAccountApi.createApiKey.mockResolvedValueOnce(created)

    const store = useAccountStore()
    const result = await store.createApiKey('My Key')

    // Must return the full create response with secret
    expect(result.secretKey).toBe('vg-supersecretvalue')
    // Key should be in the list
    expect(store.apiKeys.length).toBe(1)
    expect(store.apiKeys[0]?.name).toBe('My Key')
    // List entry must NOT expose secretKey
    expect((store.apiKeys[0] as ApiKey & { secretKey?: string }).secretKey).toBeUndefined()
    expect(store.apiKeys[0]?.disabled).toBe(false)
  })

  it('disableApiKey calls API and marks key disabled', async () => {
    const created: ApiKeyCreated = {
      id: 'key-1',
      keyPrefix: 'vg-abc12',
      name: 'To Disable',
      secretKey: 'secret',
      createdAt: new Date().toISOString(),
      expiresAt: null,
    }
    mockAccountApi.createApiKey.mockResolvedValueOnce(created)
    mockAccountApi.disableApiKey.mockResolvedValueOnce(undefined)

    const store = useAccountStore()
    await store.createApiKey('To Disable')
    await store.disableApiKey('key-1')

    expect(store.apiKeys[0]?.disabled).toBe(true)
  })

  it('fetchReports populates reports list', async () => {
    const mockReports: Report[] = [
      {
        id: 'rep-1',
        status: 'OPEN',
        category: 'BUG',
        title: 'Test bug',
        createdAt: new Date().toISOString(),
        closedAt: null,
        deletesAfter: null,
        messages: [],
      },
    ]
    mockAccountApi.listReports.mockResolvedValueOnce(mockReports)

    const store = useAccountStore()
    await store.fetchReports()

    expect(store.reports.length).toBe(1)
    expect(store.reports[0]?.title).toBe('Test bug')
    expect(store.reports[0]?.subject).toBe('Test bug') // backward-compat alias
  })
})
