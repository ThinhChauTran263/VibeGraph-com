import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAccountStore } from '../account'
import * as apiModule from '../../lib/api'
import type {
  AccountSessionState,
  ApiKeyCreated,
  ApiKey,
  UserProfile,
  UserUsage,
  Report,
  UserNotification,
} from '../../types/api'

// Mock the accountApi used by the store
vi.mock('../../lib/api', async (importOriginal) => {
  const original = await importOriginal<typeof apiModule>()
  return {
    ...original,
    accountApi: {
      getSessionState: vi.fn(),
      getProfile: vi.fn(),
      updateProfile: vi.fn(),
      changePassword: vi.fn(),
      getUsage: vi.fn(),
      getCreditLedger: vi.fn(),
      getProjects: vi.fn(),
      listApiKeys: vi.fn(),
      createApiKey: vi.fn(),
      disableApiKey: vi.fn(),
      enableApiKey: vi.fn(),
      deleteApiKey: vi.fn(),
      listReports: vi.fn(),
      createReport: vi.fn(),
      getReportDetail: vi.fn(),
      addMessage: vi.fn(),
      closeReport: vi.fn(),
      listNotifications: vi.fn(),
      listAnnouncements: vi.fn(),
      getNotification: vi.fn(),
      markNotificationRead: vi.fn(),
      dismissNotification: vi.fn(),
    },
  }
})

const mockAccountApi = vi.mocked(apiModule.accountApi)

const notification: UserNotification = {
  id: 'notification-1',
  announcementId: 'announcement-1',
  title: 'Maintenance',
  body: 'Planned maintenance window.',
  creatorName: 'Admin',
  creatorDisplayName: 'VibeGraph Admin',
  creatorEmail: 'admin@example.com',
  createdAt: '2026-07-17T09:00:00Z',
  severity: 'WARNING',
  type: 'MAINTENANCE',
  dismissible: true,
  read: false,
  readAt: null,
  dismissedAt: null,
}

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
    expect(store.projectsLoaded).toBe(false)
    expect(store.apiKeys).toEqual([])
    expect(store.apiKeysLoaded).toBe(false)
    expect(store.reports).toEqual([])
    expect(store.notifications).toEqual([])
    expect(store.notificationDetail).toBeNull()
    expect(store.unreadNotifications).toEqual([])
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('fetchSessionState exposes restriction status and safe reason', async () => {
    const state: AccountSessionState = {
      id: 'usr-1',
      email: 'blocked@example.com',
      displayName: 'Blocked User',
      role: 'USER',
      accountStatus: 'BLOCKED',
      safeReason: 'Access is temporarily restricted.',
      features: {
        'import.local': { enabled: false, reason: 'Access is temporarily restricted.' },
      },
    }
    mockAccountApi.getSessionState.mockResolvedValueOnce(state)

    const store = useAccountStore()
    await store.fetchSessionState()

    expect(store.accountRestricted).toBe(true)
    expect(store.restrictionReason).toBe(state.safeReason)
    expect(store.sessionState).toEqual(state)
    expect(store.getFeatureCapability('import.local')).toEqual(state.features['import.local'])
    expect(store.getFeatureCapability('import.github').enabled).toBe(false)
  })

  it('clears previously enabled capabilities when session refresh fails', async () => {
    mockAccountApi.getSessionState
      .mockResolvedValueOnce({
        id: 'usr-1',
        email: 'user@example.com',
        displayName: 'User',
        role: 'USER',
        accountStatus: 'ACTIVE',
        safeReason: null,
        features: {
          'import.github': { enabled: true, reason: null },
        },
      })
      .mockRejectedValueOnce(new Error('Session unavailable'))

    const store = useAccountStore()
    await store.fetchSessionState()
    expect(store.getFeatureCapability('import.github').enabled).toBe(true)

    await expect(store.fetchSessionState()).rejects.toThrow('Session unavailable')
    expect(store.getFeatureCapability('import.github').enabled).toBe(false)
  })

  it('does not restore stale capabilities after a newer session refresh fails', async () => {
    let resolveOlder!: (state: AccountSessionState) => void
    let rejectLatest!: (reason: Error) => void
    const older = new Promise<AccountSessionState>((resolve) => {
      resolveOlder = resolve
    })
    const latest = new Promise<AccountSessionState>((_, reject) => {
      rejectLatest = reject
    })
    mockAccountApi.getSessionState.mockReturnValueOnce(older).mockReturnValueOnce(latest)

    const store = useAccountStore()
    const olderRequest = store.fetchSessionState()
    const latestRequest = store.fetchSessionState()
    rejectLatest(new Error('Session unavailable'))
    await expect(latestRequest).rejects.toThrow('Session unavailable')
    resolveOlder({
      id: 'usr-1',
      email: 'user@example.com',
      displayName: 'User',
      role: 'USER',
      accountStatus: 'ACTIVE',
      safeReason: null,
      features: {
        'import.github': { enabled: true, reason: null },
      },
    })
    await olderRequest

    expect(store.getFeatureCapability('import.github').enabled).toBe(false)
  })

  it('fetches notifications and refreshes canonical data after marking read', async () => {
    const readNotification = { ...notification, read: true, readAt: '2026-07-17T09:01:00Z' }
    mockAccountApi.listNotifications
      .mockResolvedValueOnce([notification])
      .mockResolvedValueOnce([readNotification])
    mockAccountApi.markNotificationRead.mockResolvedValueOnce(readNotification)

    const store = useAccountStore()
    await store.fetchNotifications(25)
    const updated = await store.markNotificationRead(notification.id)

    expect(mockAccountApi.listNotifications).toHaveBeenNthCalledWith(1, 25)
    expect(mockAccountApi.listNotifications).toHaveBeenNthCalledWith(2, 25)
    expect(updated).toEqual(readNotification)
    expect(store.notifications).toEqual([readNotification])
    expect(store.unreadNotifications).toEqual([])
  })

  it('dismisses a notification and removes it after refreshing the inbox', async () => {
    mockAccountApi.listNotifications.mockResolvedValueOnce([notification]).mockResolvedValueOnce([])
    mockAccountApi.getNotification.mockResolvedValueOnce(notification)
    mockAccountApi.dismissNotification.mockResolvedValueOnce({
      ...notification,
      dismissedAt: '2026-07-17T09:02:00Z',
    })

    const store = useAccountStore()
    await store.fetchNotifications()
    await store.fetchNotification(notification.id)
    await store.dismissNotification(notification.id)

    expect(store.notifications).toEqual([])
    expect(store.notificationDetail).toBeNull()
  })

  it('stores and rethrows notification errors', async () => {
    const failure = new Error('Notifications unavailable')
    mockAccountApi.listNotifications.mockRejectedValueOnce(failure)

    const store = useAccountStore()
    await expect(store.fetchNotifications()).rejects.toBe(failure)
    expect(store.error).toBe(failure)
    expect(store.loading).toBe(false)
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

  it('fetchUsage preserves the Phase 7 MB quota and credit contract', async () => {
    const mockUsage: UserUsage = {
      usedMb: 10,
      limitMb: 100,
      remainingMb: 90,
      planCode: 'FREE',
      planName: 'Free Tier',
      quotaOverrideMb: null,
      creditsUsed: 25,
      creditsLimit: 100,
      creditsRemaining: 75,
    }
    mockAccountApi.getUsage.mockResolvedValueOnce(mockUsage)

    const store = useAccountStore()
    await store.fetchUsage()

    expect(store.usage).toMatchObject({
      usedMb: 10,
      limitMb: 100,
      remainingMb: 90,
      sourceStorageUsed: 10,
      sourceStorageLimit: 100,
      creditsUsed: 25,
      creditsLimit: 100,
      creditsRemaining: 75,
    })
    expect(store.usage?.sourceStorageUsed).not.toBeNaN()
    expect(store.usage?.sourceStorageLimit).not.toBeNaN()
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

  it('fetchProjects loads every repository page for API-key binding', async () => {
    mockAccountApi.getProjects
      .mockResolvedValueOnce({
        content: [{ id: 'project-1', name: 'One', sourceType: 'GITHUB', sizeBytes: 1, status: 'READY', createdAt: null, updatedAt: null, lastAnalyzedAt: null }],
        totalElements: 2,
        totalPages: 2,
        pageNumber: 0,
        pageSize: 100,
      })
      .mockResolvedValueOnce({
        content: [{ id: 'project-2', name: 'Two', sourceType: 'LOCAL', sizeBytes: 1, status: 'READY', createdAt: null, updatedAt: null, lastAnalyzedAt: null }],
        totalElements: 2,
        totalPages: 2,
        pageNumber: 1,
        pageSize: 100,
      })

    const store = useAccountStore()
    await store.fetchProjects()

    expect(mockAccountApi.getProjects).toHaveBeenNthCalledWith(1, 0, 100)
    expect(mockAccountApi.getProjects).toHaveBeenNthCalledWith(2, 1, 100)
    expect(store.projects.map((project) => project.id)).toEqual(['project-1', 'project-2'])
    expect(store.projectsLoaded).toBe(true)
  })

  it('reuses the loaded repository cache unless a refresh is forced', async () => {
    mockAccountApi.getProjects.mockResolvedValue({
      content: [
        {
          id: 'project-1',
          name: 'One',
          sourceType: 'GITHUB',
          sizeBytes: 1,
          status: 'READY',
          createdAt: null,
          updatedAt: null,
          lastAnalyzedAt: null,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 100,
    })

    const store = useAccountStore()
    await store.fetchProjects()
    await store.fetchProjects()
    await store.fetchProjects({ force: true })

    expect(mockAccountApi.getProjects).toHaveBeenCalledTimes(2)
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
      project: {
        id: 'project-1',
        name: 'VibeGraph',
        sourceType: 'GITHUB',
        status: 'READY',
      },
      createdAt: new Date().toISOString(),
      expiresAt: null,
    }
    mockAccountApi.createApiKey.mockResolvedValueOnce(created)

    const store = useAccountStore()
    const result = await store.createApiKey('My Key', 'project-1')

    // Must return the full create response with secret
    expect(mockAccountApi.createApiKey).toHaveBeenCalledWith({
      name: 'My Key',
      projectId: 'project-1',
    })
    expect(result.secretKey).toBe('vg-supersecretvalue')
    // Key should be in the list
    expect(store.apiKeys.length).toBe(1)
    expect(store.apiKeys[0]?.name).toBe('My Key')
    // List entry must NOT expose secretKey
    expect((store.apiKeys[0] as ApiKey & { secretKey?: string }).secretKey).toBeUndefined()
    expect(store.apiKeys[0]?.disabled).toBe(false)
    expect(store.apiKeys[0]?.project?.id).toBe('project-1')
  })

  it('disableApiKey calls API and marks key disabled', async () => {
    const created: ApiKeyCreated = {
      id: 'key-1',
      keyPrefix: 'vg-abc12',
      name: 'To Disable',
      secretKey: 'secret',
      project: {
        id: 'project-1',
        name: 'VibeGraph',
        sourceType: 'GITHUB',
        status: 'READY',
      },
      createdAt: new Date().toISOString(),
      expiresAt: null,
    }
    mockAccountApi.createApiKey.mockResolvedValueOnce(created)
    mockAccountApi.disableApiKey.mockResolvedValueOnce(undefined)

    const store = useAccountStore()
    await store.createApiKey('To Disable', 'project-1')
    await store.disableApiKey('key-1')

    expect(store.apiKeys[0]?.disabled).toBe(true)
    expect(store.apiKeys[0]?.disabledBy).toBe('USER')
  })

  it('enableApiKey calls API and marks a user-disabled key active', async () => {
    const key: ApiKey = {
      id: 'key-1',
      keyPrefix: 'vg-abc12',
      name: 'To Enable',
      project: {
        id: 'project-1',
        name: 'VibeGraph',
        sourceType: 'GITHUB',
        status: 'READY',
      },
      createdAt: new Date().toISOString(),
      lastUsedAt: null,
      expiresAt: null,
      disabledAt: new Date().toISOString(),
      disabledBy: 'USER',
      disabledReason: null,
      lockedAt: null,
      lockedBy: null,
      locked: false,
      deletedAt: null,
      canDelete: true,
      disabled: true,
    }
    mockAccountApi.listApiKeys.mockResolvedValueOnce([key])
    mockAccountApi.enableApiKey.mockResolvedValueOnce(undefined)

    const store = useAccountStore()
    await store.fetchApiKeys()
    await store.enableApiKey('key-1')

    expect(mockAccountApi.enableApiKey).toHaveBeenCalledWith('key-1')
    expect(store.apiKeys[0]?.disabled).toBe(false)
    expect(store.apiKeys[0]?.disabledAt).toBeNull()
    expect(store.apiKeys[0]?.disabledBy).toBeNull()
    expect(store.apiKeysLoaded).toBe(true)
  })

  it('reuses the loaded API key cache until forced', async () => {
    mockAccountApi.listApiKeys.mockResolvedValue([])

    const store = useAccountStore()
    await store.fetchApiKeys()
    await store.fetchApiKeys()
    await store.fetchApiKeys({ force: true })

    expect(mockAccountApi.listApiKeys).toHaveBeenCalledTimes(2)
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
