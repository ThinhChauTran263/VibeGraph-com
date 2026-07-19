import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  UserProfile,
  UserUsage,
  CreditLedgerEntry,
  Project,
  ApiKey,
  ApiKeyCreated,
  Report,
  ReportMessage,
  AccountSessionState,
  FeatureCapability,
  UserNotification,
} from '../types/api'
import { accountApi } from '../lib/api'

/** Converts a backend UserResponse accountStatus to our legacy status field. */
function normalizeStatus(accountStatus?: string): 'active' | 'blocked' | 'deactivated' {
  if (!accountStatus) return 'active'
  const s = accountStatus.toUpperCase()
  if (s === 'BLOCKED') return 'blocked'
  if (s === 'DEACTIVATED') return 'deactivated'
  return 'active'
}

export const useAccountStore = defineStore('account', () => {
  const profile = ref<UserProfile | null>(null)
  const usage = ref<UserUsage | null>(null)
  const creditLedger = ref<CreditLedgerEntry[]>([])
  const projects = ref<Project[]>([])
  const projectsLoaded = ref(false)
  const apiKeys = ref<ApiKey[]>([])
  const apiKeysLoaded = ref(false)
  const reports = ref<Report[]>([])
  const sessionState = ref<AccountSessionState | null>(null)
  const notifications = ref<UserNotification[]>([])
  const notificationDetail = ref<UserNotification | null>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  let notificationLimit = 50
  let sessionStateRequestId = 0
  let projectsRequest: Promise<void> | null = null
  let apiKeysRequest: Promise<void> | null = null
  const unreadNotifications = computed(() => notifications.value.filter((item) => !item.read))
  const accountRestricted = computed(() => {
    const status = sessionState.value?.accountStatus?.toUpperCase()
    return status === 'BLOCKED' || status === 'DEACTIVATED'
  })
  const restrictionReason = computed(() => sessionState.value?.safeReason ?? null)
  function getFeatureCapability(key: string): FeatureCapability {
    return (
      sessionState.value?.features?.[key] ?? {
        enabled: false,
        reason: 'This feature is unavailable until account capabilities can be verified.',
      }
    )
  }

  /** Derived: current plan name from usage (empty string until loaded) */
  const planName = computed(() => usage.value?.planName ?? '')

  // ─── Profile ────────────────────────────────────────────────────────────────

  async function fetchProfile(): Promise<void> {
    const data = await accountApi.getProfile()
    // Normalize accountStatus → legacy status field
    profile.value = {
      ...data,
      status: normalizeStatus(data.accountStatus),
    }
  }

  async function fetchSessionState(): Promise<AccountSessionState> {
    const requestId = ++sessionStateRequestId
    try {
      const data = await accountApi.getSessionState()
      if (requestId !== sessionStateRequestId) return data
      sessionState.value = { ...data, features: data.features ?? {} }
      if (profile.value) {
        profile.value = {
          ...profile.value,
          displayName: data.displayName,
          email: data.email,
          role: data.role,
          accountStatus: data.accountStatus,
          safeReason: data.safeReason ?? undefined,
          status: normalizeStatus(data.accountStatus),
        }
      }
      return sessionState.value
    } catch (cause) {
      if (requestId === sessionStateRequestId && sessionState.value) {
        sessionState.value = { ...sessionState.value, features: {} }
      }
      throw cause
    }
  }

  async function updateDisplayName(newName: string): Promise<void> {
    const data = await accountApi.updateProfile(newName)
    profile.value = {
      ...data,
      status: normalizeStatus(data.accountStatus),
    }
  }

  async function changePassword(
    oldPassword: string,
    newPassword: string,
    confirmPassword: string,
  ): Promise<void> {
    await accountApi.changePassword(oldPassword, newPassword, confirmPassword)
  }

  // ─── Notifications ──────────────────────────────────────────────────────────

  async function withNotificationState<T>(request: () => Promise<T>): Promise<T> {
    loading.value = true
    error.value = null
    try {
      return await request()
    } catch (cause) {
      error.value = cause instanceof Error ? cause : new Error('Notification request failed.')
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function refreshNotifications(): Promise<void> {
    notifications.value = await accountApi.listNotifications(notificationLimit)
  }

  async function fetchNotifications(limit = 50): Promise<void> {
    notificationLimit = limit
    await withNotificationState(refreshNotifications)
  }

  async function fetchAnnouncements(limit = 50): Promise<void> {
    notificationLimit = limit
    await withNotificationState(async () => {
      notifications.value = await accountApi.listAnnouncements(limit)
    })
  }

  async function fetchNotification(id: string): Promise<UserNotification> {
    return withNotificationState(async () => {
      const detail = await accountApi.getNotification(id)
      notificationDetail.value = detail
      return detail
    })
  }

  async function markNotificationRead(id: string): Promise<UserNotification> {
    return withNotificationState(async () => {
      const updated = await accountApi.markNotificationRead(id)
      await refreshNotifications()
      notificationDetail.value = notifications.value.find((item) => item.id === id) ?? updated
      return notificationDetail.value
    })
  }

  async function dismissNotification(id: string): Promise<void> {
    await withNotificationState(async () => {
      await accountApi.dismissNotification(id)
      await refreshNotifications()
      if (notificationDetail.value?.id === id) notificationDetail.value = null
    })
  }

  // ─── Usage ──────────────────────────────────────────────────────────────────

  async function fetchUsage(): Promise<void> {
    const data = await accountApi.getUsage()
    const bytesPerMb = 1024 * 1024
    const usedMb = Number.isFinite(data.usedMb)
      ? data.usedMb
      : Math.round((data.usedBytes ?? 0) / bytesPerMb)
    const limitMb = Number.isFinite(data.limitMb)
      ? data.limitMb
      : Math.round((data.limitBytes ?? 0) / bytesPerMb)
    const remainingMb = Number.isFinite(data.remainingMb)
      ? data.remainingMb
      : Number.isFinite(data.remainingBytes)
        ? Math.round((data.remainingBytes ?? 0) / bytesPerMb)
        : Math.max(limitMb - usedMb, 0)

    usage.value = {
      ...data,
      usedMb,
      limitMb,
      remainingMb,
      quotaOverrideMb:
        data.quotaOverrideMb ??
        (typeof data.quotaOverrideBytes === 'number'
          ? Math.round(data.quotaOverrideBytes / bytesPerMb)
          : null),
      sourceStorageUsed: usedMb,
      sourceStorageLimit: limitMb,
    }
  }

  async function fetchCreditLedger(limit = 10): Promise<void> {
    creditLedger.value = await accountApi.getCreditLedger(limit)
  }

  // ─── Projects ───────────────────────────────────────────────────────────────

  async function fetchProjects(options: { force?: boolean } = {}): Promise<void> {
    if (projectsLoaded.value && !options.force) return
    if (projectsRequest) return projectsRequest

    const pageSize = 100
    projectsRequest = (async () => {
      const firstPage = await accountApi.getProjects(0, pageSize)
      const remainingPages = await Promise.all(
        Array.from({ length: Math.max(firstPage.totalPages - 1, 0) }, (_, index) =>
          accountApi.getProjects(index + 1, pageSize),
        ),
      )
      const projectItems = [firstPage, ...remainingPages].flatMap(
        (page) => page.items ?? page.content ?? [],
      )
      setProjects(
        projectItems.map((project) => ({
          ...project,
          lastAnalyzedAt: project.updatedAt,
        })),
      )
    })()

    try {
      await projectsRequest
    } finally {
      projectsRequest = null
    }
  }

  function setProjects(nextProjects: Project[]): void {
    projects.value = nextProjects
    projectsLoaded.value = true
  }

  // ─── API Keys ────────────────────────────────────────────────────────────────

  async function fetchApiKeys(options: { force?: boolean } = {}): Promise<void> {
    if (apiKeysLoaded.value && !options.force) return
    if (apiKeysRequest) return apiKeysRequest

    apiKeysRequest = (async () => {
      const keys = await accountApi.listApiKeys()
      apiKeys.value = keys.map((k) => ({
        ...k,
        disabled: k.disabledAt !== null,
      }))
      apiKeysLoaded.value = true
    })()

    try {
      await apiKeysRequest
    } finally {
      apiKeysRequest = null
    }
  }

  /**
   * Creates an API key and prepends it to the list.
   * Returns the full create response — caller must display the `secretKey` immediately.
   */
  async function createApiKey(name: string, projectId: string): Promise<ApiKeyCreated> {
    const created = await accountApi.createApiKey({ name, projectId })
    // Add a display entry to the list (without secret — already gone)
    const listEntry: ApiKey = {
      id: created.id,
      keyPrefix: created.keyPrefix,
      name: created.name,
      project: created.project,
      createdAt: created.createdAt,
      lastUsedAt: null,
      expiresAt: created.expiresAt,
      disabledAt: null,
      disabledBy: null,
      disabledReason: null,
      lockedAt: null,
      lockedBy: null,
      locked: false,
      deletedAt: null,
      canDelete: true,
      disabled: false,
    }
    apiKeys.value = [listEntry, ...apiKeys.value]
    apiKeysLoaded.value = true
    return created
  }

  async function disableApiKey(id: string): Promise<void> {
    await accountApi.disableApiKey(id)
    const disabledAt = new Date().toISOString()
    apiKeys.value = apiKeys.value.map((key) =>
      key.id === id
        ? { ...key, disabledAt, disabledBy: 'USER', disabledReason: null, disabled: true }
        : key,
    )
    apiKeysLoaded.value = true
  }

  async function enableApiKey(id: string): Promise<void> {
    await accountApi.enableApiKey(id)
    apiKeys.value = apiKeys.value.map((key) =>
      key.id === id
        ? {
            ...key,
            disabledAt: null,
            disabledBy: null,
            disabledReason: null,
            lockedAt: null,
            lockedBy: null,
            locked: false,
            disabled: false,
          }
        : key,
    )
    apiKeysLoaded.value = true
  }

  async function deleteApiKey(id: string): Promise<void> {
    await accountApi.deleteApiKey(id)
    apiKeys.value = apiKeys.value.filter((key) => key.id !== id)
    apiKeysLoaded.value = true
  }

  // ─── Reports ─────────────────────────────────────────────────────────────────

  async function fetchReports(): Promise<void> {
    const data = await accountApi.listReports()
    reports.value = data.map(normalizeReport)
  }

  async function createReport(
    category: import('../types/api').FeedbackCategory,
    title: string,
    body: string,
  ): Promise<Report> {
    const data = await accountApi.createReport(category, title, body)
    const report = normalizeReport(data)
    reports.value = [report, ...reports.value]
    return report
  }

  async function fetchReportDetail(reportId: string): Promise<Report> {
    const data = await accountApi.getReportDetail(reportId)
    const report = normalizeReport(data.report)
    report.messages = data.messages.map(normalizeMessage)
    // Update in list
    const idx = reports.value.findIndex((r) => r.id === reportId)
    if (idx >= 0) reports.value[idx] = report
    return report
  }

  async function addMessage(reportId: string, body: string): Promise<ReportMessage> {
    const msg = await accountApi.addMessage(reportId, body)
    return normalizeMessage(msg)
  }

  async function closeReport(reportId: string): Promise<Report> {
    const closed = normalizeReport(await accountApi.closeReport(reportId))
    reports.value = reports.value.map((report) => (report.id === reportId ? closed : report))
    return closed
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────────

  function normalizeReport(r: Report): Report {
    return {
      ...r,
      messages: r.messages ?? [],
      // backwards-compat aliases
      subject: r.title,
      updatedAt: r.closedAt ?? r.createdAt,
    }
  }

  function normalizeMessage(m: ReportMessage): ReportMessage {
    return {
      ...m,
      isAdmin: m.senderRole === 'ADMIN',
      senderName: m.senderRole === 'ADMIN' ? 'Support Team' : 'You',
    }
  }

  return {
    profile,
    sessionState,
    notifications,
    notificationDetail,
    unreadNotifications,
    loading,
    error,
    accountRestricted,
    restrictionReason,
    getFeatureCapability,
    usage,
    creditLedger,
    projects,
    projectsLoaded,
    apiKeys,
    apiKeysLoaded,
    reports,
    planName,
    fetchProfile,
    fetchSessionState,
    fetchNotifications,
    fetchAnnouncements,
    fetchNotification,
    markNotificationRead,
    dismissNotification,
    refreshNotifications,
    updateDisplayName,
    changePassword,
    fetchUsage,
    fetchCreditLedger,
    fetchProjects,
    setProjects,
    fetchApiKeys,
    createApiKey,
    disableApiKey,
    enableApiKey,
    deleteApiKey,
    fetchReports,
    createReport,
    fetchReportDetail,
    addMessage,
    closeReport,
  }
})
