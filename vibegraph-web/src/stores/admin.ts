import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  AdminOverview,
  AdminPlan,
  AdminPlanRequest,
  AdminPricingRule,
  AdminPricingRuleRequest,
  AdminUserResponse,
  ApiKey,
  AdminReport,
  ReportMessage,
  PagedResponse,
  AdminFeatureFlag,
  AdminFeatureFlagRequest,
  AdminAnnouncement,
  AdminAnnouncementRequest,
  AdminSecurityEvent,
  AdminCreditOverview,
  AdminRequestEvent,
  AdminRequestAggregate,
  AdminSuspiciousNetwork,
  AdminIpBlock,
  AdminIpBlockRequest,
  AdminAuditLog,
  AdminAuditRetention,
  AdminAuditLogQuery,
} from '../types/api'
import { adminApi, api } from '../lib/api'

export type SecurityLiveStatus = 'connected' | 'reconnecting' | 'paused'

const SECURITY_EVENT_LIMIT = 100
const SECURITY_AGGREGATE_DEBOUNCE_MS = 1500

export const useAdminStore = defineStore('admin', () => {
  // ─── State ────────────────────────────────────────────────────────────────────
  const overview = ref<AdminOverview | null>(null)
  const plans = ref<AdminPlan[]>([])
  const pricingRules = ref<AdminPricingRule[]>([])
  const users = ref<AdminUserResponse[]>([])
  const usersPagination = ref<Omit<PagedResponse<unknown>, 'content'>>({
    totalElements: 0,
    totalPages: 0,
    pageNumber: 0,
    pageSize: 20,
  })
  const reports = ref<AdminReport[]>([])
  const featureFlags = ref<AdminFeatureFlag[]>([])
  const announcements = ref<AdminAnnouncement[]>([])
  const securityEvents = ref<AdminSecurityEvent[]>([])
  const requestEvents = ref<AdminRequestEvent[]>([])
  const securityLiveStatus = ref<SecurityLiveStatus>('paused')
  const topUsers = ref<AdminRequestAggregate[]>([])
  const topIps = ref<AdminSuspiciousNetwork[]>([])
  const ipBlocks = ref<AdminIpBlock[]>([])
  const creditOverviews = ref<Record<string, AdminCreditOverview>>({})
  const auditLogs = ref<AdminAuditLog[]>([])
  const auditLogDetail = ref<AdminAuditLog | null>(null)
  const auditRetention = ref<AdminAuditRetention | null>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  const securityLimits = {
    events: 50,
    requestEvents: 100,
    topMinutes: 60,
    topLimit: 20,
    ipBlocks: 100,
  }
  let auditQuery: AdminAuditLogQuery = { page: 0, size: 50 }
  const auditPagination = ref<Omit<PagedResponse<unknown>, 'content'>>({
    totalElements: 0,
    totalPages: 0,
    pageNumber: 0,
    pageSize: 50,
  })
  const reportsPagination = ref<Omit<PagedResponse<unknown>, 'content'>>({
    totalElements: 0,
    totalPages: 0,
    pageNumber: 0,
    pageSize: 20,
  })
  let securityEventSource: EventSource | null = null
  let securityAggregateRefreshTimer: ReturnType<typeof setTimeout> | null = null

  // ─── Overview ─────────────────────────────────────────────────────────────────

  async function fetchOverview(): Promise<void> {
    overview.value = await adminApi.getOverview()
  }

  async function fetchPlans(): Promise<void> {
    plans.value = await adminApi.listPlans()
  }

  async function fetchPricingRules(): Promise<void> {
    pricingRules.value = await adminApi.listPricingRules()
  }

  async function savePlan(data: AdminPlanRequest, originalCode?: string): Promise<void> {
    const saved = originalCode
      ? await adminApi.updateCatalogPlan(originalCode, data)
      : await adminApi.createPlan(data)
    const idx = plans.value.findIndex((plan) => plan.code === (originalCode ?? saved.code))
    if (idx >= 0) {
      plans.value[idx] = saved
    } else {
      plans.value = [saved, ...plans.value]
    }
  }

  async function deletePlan(code: string): Promise<void> {
    await adminApi.deleteCatalogPlan(code)
    plans.value = plans.value.filter((plan) => plan.code !== code)
  }

  async function savePricingRule(
    data: AdminPricingRuleRequest,
    originalOperationCode?: string,
  ): Promise<void> {
    const saved = originalOperationCode
      ? await adminApi.updatePricingRule(originalOperationCode, data)
      : await adminApi.createPricingRule(data)
    const idx = pricingRules.value.findIndex(
      (rule) => rule.operationCode === (originalOperationCode ?? saved.operationCode),
    )
    if (idx >= 0) {
      pricingRules.value[idx] = saved
    } else {
      pricingRules.value = [saved, ...pricingRules.value]
    }
  }

  async function deletePricingRule(operationCode: string): Promise<void> {
    await adminApi.deletePricingRule(operationCode)
    pricingRules.value = pricingRules.value.filter((rule) => rule.operationCode !== operationCode)
  }

  // ─── Users ────────────────────────────────────────────────────────────────────

  async function fetchUsers(
    params: {
      search?: string
      status?: string
      plan?: string
      page?: number
      size?: number
    } = {},
  ): Promise<void> {
    const result = await adminApi.listUsers(params)
    users.value = result.items ?? result.content ?? []
    usersPagination.value = paginationMeta(result)
  }

  async function getUserDetail(userId: string): Promise<AdminUserResponse> {
    return adminApi.getUserDetail(userId)
  }

  async function createUser(data: {
    email: string
    displayName: string
    role: string
    planCode: string
    temporaryPassword: string
  }): Promise<AdminUserResponse> {
    const created = await adminApi.createUser(data)
    users.value = [created, ...users.value]
    return created
  }

  async function blockUser(userId: string, reason: string, safeReason: string): Promise<void> {
    const updated = await adminApi.blockUser(userId, reason, safeReason)
    replaceUser(updated)
  }

  async function unblockUser(userId: string): Promise<void> {
    const updated = await adminApi.unblockUser(userId)
    replaceUser(updated)
  }

  async function deactivateUser(userId: string, reason: string, safeReason: string): Promise<void> {
    const updated = await adminApi.deactivateUser(userId, reason, safeReason)
    replaceUser(updated)
  }

  async function updatePlan(userId: string, planCode: string): Promise<void> {
    const updated = await adminApi.updatePlan(userId, planCode)
    replaceUser(updated)
  }

  async function updateQuota(
    userId: string,
    storageQuotaOverrideMb: number | null,
    creditQuotaOverride: number | null,
  ): Promise<void> {
    const updated = await adminApi.updateQuota(userId, storageQuotaOverrideMb, creditQuotaOverride)
    replaceUser(updated)
  }

  async function fetchCreditOverview(userId: string): Promise<AdminCreditOverview> {
    const overview = await adminApi.getCreditOverview(userId)
    creditOverviews.value = { ...creditOverviews.value, [userId]: overview }
    return overview
  }

  async function adjustCredits(
    userId: string,
    creditsDelta: number,
    reason: string,
  ): Promise<void> {
    await adminApi.adjustCredits(userId, creditsDelta, reason)
    await fetchCreditOverview(userId)
  }

  async function updateApiKeyCreation(userId: string, disabled: boolean): Promise<void> {
    const updated = await adminApi.updateApiKeyCreation(userId, disabled)
    replaceUser(updated)
  }

  // ─── Admin API Keys ───────────────────────────────────────────────────────────

  async function listApiKeysForUser(userId: string): Promise<ApiKey[]> {
    const keys = await adminApi.listApiKeysForUser(userId)
    return keys.map((k) => ({ ...k, disabled: k.disabledAt !== null }))
  }

  async function disableApiKey(id: string): Promise<void> {
    await adminApi.disableApiKey(id)
  }

  async function lockApiKey(id: string): Promise<void> {
    await adminApi.lockApiKey(id)
  }

  async function unlockApiKey(id: string): Promise<void> {
    await adminApi.unlockApiKey(id)
  }

  // ─── Admin Reports ────────────────────────────────────────────────────────────

  async function fetchReports(
    params: {
      status?: string
      q?: string
      page?: number
      size?: number
    } = {},
  ): Promise<void> {
    const result = await adminApi.listReports(params)
    reports.value = (result.items ?? result.content ?? []).map((r) => ({ ...r, messages: [] }))
    reportsPagination.value = paginationMeta(result)
  }

  async function fetchReportDetail(
    reportId: string,
  ): Promise<{ report: AdminReport; messages: ReportMessage[] }> {
    const data = await adminApi.getReportDetail(reportId)
    const messages = data.messages.map((m) => ({
      ...m,
      isAdmin: m.senderRole === 'ADMIN',
      senderName: m.senderRole === 'ADMIN' ? 'Admin' : 'User',
    }))
    const idx = reports.value.findIndex((r) => r.id === reportId)
    if (idx >= 0) {
      reports.value[idx] = { ...data.report, messages }
    }
    return { report: data.report, messages }
  }

  async function replyToReport(reportId: string, body: string): Promise<void> {
    await adminApi.replyToReport(reportId, body)
  }

  async function closeReport(reportId: string): Promise<void> {
    await adminApi.closeReport(reportId)
    const report = reports.value.find((r) => r.id === reportId)
    if (report) report.status = 'CLOSED'
  }

  async function fetchFeatureFlags(): Promise<void> {
    featureFlags.value = await adminApi.listFeatureFlags()
  }

  async function upsertFeatureFlag(data: AdminFeatureFlagRequest): Promise<void> {
    const exists = featureFlags.value.some((flag) => flag.key === data.key)
    const updated = exists
      ? await adminApi.updateFeatureFlag(data.key, data)
      : await adminApi.createFeatureFlag(data)
    const idx = featureFlags.value.findIndex((flag) => flag.key === updated.key)
    if (idx >= 0) {
      featureFlags.value[idx] = updated
    } else {
      featureFlags.value = [updated, ...featureFlags.value]
    }
  }

  async function setFeatureFlagEnabled(flag: AdminFeatureFlag, enabled: boolean): Promise<void> {
    await upsertFeatureFlag({
      key: flag.key,
      scope: flag.scope === 'MCP_TOOL' ? 'MCP_TOOL' : 'GLOBAL',
      displayName: flag.displayName,
      enabled,
      description: flag.description,
    })
  }

  async function fetchAnnouncements(): Promise<void> {
    announcements.value = await adminApi.listAnnouncements()
  }

  async function createAnnouncement(data: AdminAnnouncementRequest): Promise<void> {
    const created = await adminApi.createAnnouncement(data)
    announcements.value = [created, ...announcements.value]
  }

  async function updateAnnouncement(id: string, data: AdminAnnouncementRequest): Promise<void> {
    const updated = await adminApi.updateAnnouncement(id, data)
    const index = announcements.value.findIndex((announcement) => announcement.id === id)
    if (index >= 0) announcements.value[index] = updated
  }

  async function disableAnnouncement(id: string): Promise<void> {
    const updated = await adminApi.disableAnnouncement(id)
    const idx = announcements.value.findIndex((announcement) => announcement.id === id)
    if (idx >= 0) announcements.value[idx] = updated
  }

  async function deleteAnnouncement(id: string): Promise<void> {
    await adminApi.deleteAnnouncement(id)
    announcements.value = announcements.value.filter((announcement) => announcement.id !== id)
  }

  async function withAdminState<T>(request: () => Promise<T>): Promise<T> {
    loading.value = true
    error.value = null
    try {
      return await request()
    } catch (cause) {
      error.value = cause instanceof Error ? cause : new Error('Admin request failed.')
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function fetchSecurityEvents(limit = 50): Promise<void> {
    securityLimits.events = limit
    await withAdminState(async () => {
      securityEvents.value = await adminApi.listSecurityEvents(limit)
    })
  }

  async function fetchRequestEvents(limit = 100): Promise<void> {
    securityLimits.requestEvents = limit
    await withAdminState(async () => {
      requestEvents.value = await adminApi.listRequestEvents(limit)
    })
  }

  async function fetchTopUsers(minutes = 60, limit = 20): Promise<void> {
    securityLimits.topMinutes = minutes
    securityLimits.topLimit = limit
    await withAdminState(async () => {
      topUsers.value = await adminApi.listTopUsers(minutes, limit)
    })
  }

  async function fetchTopIps(minutes = 60, limit = 20): Promise<void> {
    securityLimits.topMinutes = minutes
    securityLimits.topLimit = limit
    await withAdminState(async () => {
      topIps.value = await adminApi.listTopIps(minutes, limit)
    })
  }

  async function fetchIpBlocks(limit = 100): Promise<void> {
    securityLimits.ipBlocks = limit
    await withAdminState(async () => {
      ipBlocks.value = await adminApi.listIpBlocks(limit)
    })
  }

  async function refreshSecurityAggregates(): Promise<void> {
    const results = await Promise.allSettled([
      adminApi.listTopUsers(securityLimits.topMinutes, securityLimits.topLimit),
      adminApi.listTopIps(securityLimits.topMinutes, securityLimits.topLimit),
    ])
    if (results[0]?.status === 'fulfilled') topUsers.value = results[0].value
    if (results[1]?.status === 'fulfilled') topIps.value = results[1].value
  }

  function startSecurityStream(): void {
    if (securityEventSource) return
    if (typeof EventSource === 'undefined') {
      securityLiveStatus.value = 'paused'
      return
    }
    securityLiveStatus.value = 'reconnecting'
    const source = new EventSource(`${api.baseUrl}/api/admin/security/stream`, {
      withCredentials: true,
    })
    securityEventSource = source
    source.onopen = () => {
      if (securityEventSource === source) securityLiveStatus.value = 'connected'
    }
    source.onerror = () => {
      if (securityEventSource !== source) return
      securityLiveStatus.value =
        source.readyState === EventSource.CLOSED ? 'paused' : 'reconnecting'
    }
    source.addEventListener('request-event', handleLiveRequestEvent)
  }

  function stopSecurityStream(): void {
    securityEventSource?.close()
    securityEventSource = null
    if (securityAggregateRefreshTimer) clearTimeout(securityAggregateRefreshTimer)
    securityAggregateRefreshTimer = null
    securityLiveStatus.value = 'paused'
  }

  function handleLiveRequestEvent(event: Event): void {
    const data = 'data' in event && typeof event.data === 'string' ? event.data : null
    if (!data) return
    try {
      const parsed: unknown = JSON.parse(data)
      if (!isAdminRequestEvent(parsed)) return
      requestEvents.value = [
        parsed,
        ...requestEvents.value.filter((existing) => existing.id !== parsed.id),
      ].slice(0, SECURITY_EVENT_LIMIT)
      scheduleSecurityAggregateRefresh()
    } catch {
      // Ignore malformed stream frames while keeping the connection alive.
    }
  }

  function scheduleSecurityAggregateRefresh(): void {
    if (securityAggregateRefreshTimer) clearTimeout(securityAggregateRefreshTimer)
    securityAggregateRefreshTimer = setTimeout(() => {
      securityAggregateRefreshTimer = null
      void refreshSecurityAggregates()
    }, SECURITY_AGGREGATE_DEBOUNCE_MS)
  }

  function isAdminRequestEvent(value: unknown): value is AdminRequestEvent {
    if (!isRecord(value)) return false
    return (
      typeof value.id === 'string' &&
      isNullableString(value.userId) &&
      isNullableString(value.userDisplayName) &&
      isNullableString(value.userEmail) &&
      isNullableString(value.apiKeyRef) &&
      isNullableString(value.ipAddress) &&
      typeof value.route === 'string' &&
      typeof value.method === 'string' &&
      typeof value.status === 'number' &&
      typeof value.eventType === 'string' &&
      typeof value.occurredAt === 'string'
    )
  }

  function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
  }

  function isNullableString(value: unknown): value is string | null {
    return value === null || typeof value === 'string'
  }

  async function refreshSecurity(): Promise<void> {
    const [events, requestRows, usersByRate, ipsByRate, blocks] = await Promise.all([
      adminApi.listSecurityEvents(securityLimits.events),
      adminApi.listRequestEvents(securityLimits.requestEvents),
      adminApi.listTopUsers(securityLimits.topMinutes, securityLimits.topLimit),
      adminApi.listTopIps(securityLimits.topMinutes, securityLimits.topLimit),
      adminApi.listIpBlocks(securityLimits.ipBlocks),
    ])
    securityEvents.value = events
    requestEvents.value = requestRows
    topUsers.value = usersByRate
    topIps.value = ipsByRate
    ipBlocks.value = blocks
  }

  async function fetchSecurityData(limit = 100): Promise<string[]> {
    securityLimits.requestEvents = limit
    return withAdminState(async () => {
      const results = await Promise.allSettled([
        adminApi.listSecurityEvents(securityLimits.events),
        adminApi.listRequestEvents(securityLimits.requestEvents),
        adminApi.listTopUsers(securityLimits.topMinutes, securityLimits.topLimit),
        adminApi.listTopIps(securityLimits.topMinutes, securityLimits.topLimit),
        adminApi.listIpBlocks(securityLimits.ipBlocks),
      ])
      const labels = ['security events', 'request events', 'top users', 'suspicious networks', 'IP blocks']
      if (results[0]?.status === 'fulfilled') securityEvents.value = results[0].value
      if (results[1]?.status === 'fulfilled') requestEvents.value = results[1].value
      if (results[2]?.status === 'fulfilled') topUsers.value = results[2].value
      if (results[3]?.status === 'fulfilled') topIps.value = results[3].value
      if (results[4]?.status === 'fulfilled') ipBlocks.value = results[4].value
      return results.flatMap((result, index) =>
        result.status === 'rejected' ? [labels[index] ?? 'security data'] : [],
      )
    })
  }

  type IpBlockMutationResult = { refreshFailed: boolean }

  async function refreshIpBlocksAfterMutation(fallback: AdminIpBlock[]): Promise<IpBlockMutationResult> {
    try {
      ipBlocks.value = await adminApi.listIpBlocks(securityLimits.ipBlocks)
      return { refreshFailed: false }
    } catch {
      ipBlocks.value = fallback
      return { refreshFailed: true }
    }
  }

  async function createIpBlock(data: AdminIpBlockRequest): Promise<IpBlockMutationResult> {
    return withAdminState(async () => {
      const created = await adminApi.createIpBlock(data)
      return refreshIpBlocksAfterMutation([...ipBlocks.value, created])
    })
  }

  async function updateIpBlock(id: string, data: AdminIpBlockRequest): Promise<IpBlockMutationResult> {
    return withAdminState(async () => {
      const updated = await adminApi.updateIpBlock(id, data)
      return refreshIpBlocksAfterMutation(
        ipBlocks.value.some((block) => block.id === id)
          ? ipBlocks.value.map((block) => (block.id === id ? updated : block))
          : [...ipBlocks.value, updated],
      )
    })
  }

  async function deleteIpBlock(id: string): Promise<IpBlockMutationResult> {
    return withAdminState(async () => {
      await adminApi.deleteIpBlock(id)
      return refreshIpBlocksAfterMutation(ipBlocks.value.filter((block) => block.id !== id))
    })
  }

  async function fetchAuditLogs(params: AdminAuditLogQuery = {}): Promise<void> {
    auditQuery = { ...auditQuery, ...params }
    const result = await adminApi.listAuditLogs(auditQuery)
    auditLogs.value = result.items ?? result.content ?? []
    auditPagination.value = paginationMeta(result)
  }

  async function refreshAudit(): Promise<void> {
    await Promise.all([fetchAuditLogs(auditQuery), fetchAuditRetention()])
  }

  async function fetchAuditLogDetail(id: string): Promise<AdminAuditLog> {
    return withAdminState(async () => {
      const detail = await adminApi.getAuditLog(id)
      auditLogDetail.value = detail
      return detail
    })
  }

  async function fetchAuditRetention(): Promise<void> {
    auditRetention.value = await adminApi.getAuditRetention()
  }

  async function updateAuditRetention(retentionDays: number): Promise<void> {
    await withAdminState(async () => {
      await adminApi.updateAuditRetention(retentionDays)
      await refreshAudit()
    })
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────────

  function paginationMeta<T>(page: PagedResponse<T>): Omit<PagedResponse<unknown>, 'content'> {
    return {
      totalElements: page.totalElements,
      totalPages: page.totalPages,
      pageNumber: page.pageNumber,
      pageSize: page.pageSize,
      page: page.page,
      size: page.size,
    }
  }

  function replaceUser(updated: AdminUserResponse): void {
    const idx = users.value.findIndex((u) => u.id === updated.id)
    if (idx >= 0) {
      users.value[idx] = updated
    }
  }

  return {
    // state
    overview,
    plans,
    pricingRules,
    users,
    usersPagination,
    reports,
    reportsPagination,
    featureFlags,
    announcements,
    securityEvents,
    requestEvents,
    securityLiveStatus,
    topUsers,
    topIps,
    ipBlocks,
    creditOverviews,
    auditLogs,
    auditLogDetail,
    auditRetention,
    auditPagination,
    loading,
    error,
    // actions
    fetchOverview,
    fetchPlans,
    fetchPricingRules,
    savePlan,
    deletePlan,
    savePricingRule,
    deletePricingRule,
    fetchUsers,
    getUserDetail,
    createUser,
    blockUser,
    unblockUser,
    deactivateUser,
    updatePlan,
    updateQuota,
    fetchCreditOverview,
    adjustCredits,
    updateApiKeyCreation,
    listApiKeysForUser,
    disableApiKey,
    lockApiKey,
    unlockApiKey,
    fetchReports,
    fetchReportDetail,
    replyToReport,
    closeReport,
    fetchFeatureFlags,
    upsertFeatureFlag,
    setFeatureFlagEnabled,
    fetchAnnouncements,
    createAnnouncement,
    updateAnnouncement,
    disableAnnouncement,
    deleteAnnouncement,
    fetchSecurityEvents,
    fetchRequestEvents,
    fetchTopUsers,
    fetchTopIps,
    fetchIpBlocks,
    refreshSecurityAggregates,
    startSecurityStream,
    stopSecurityStream,
    fetchSecurityData,
    refreshSecurity,
    createIpBlock,
    updateIpBlock,
    deleteIpBlock,
    fetchAuditLogs,
    refreshAudit,
    fetchAuditLogDetail,
    fetchAuditRetention,
    updateAuditRetention,
  }
})
