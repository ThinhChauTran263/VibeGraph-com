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
  ApiKeyCreated,
  AdminReport,
  ReportMessage,
  PagedResponse,
  AdminFeatureFlag,
  AdminFeatureFlagRequest,
  AdminAnnouncement,
  AdminAnnouncementRequest,
  AdminSecurityEvent,
} from '../types/api'
import { adminApi } from '../lib/api'

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
  const reportsPagination = ref<Omit<PagedResponse<unknown>, 'content'>>({
    totalElements: 0,
    totalPages: 0,
    pageNumber: 0,
    pageSize: 20,
  })

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
    const { content: _content, items: _items, ...meta } = result
    usersPagination.value = meta
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

  async function updateApiKeyCreation(userId: string, disabled: boolean): Promise<void> {
    const updated = await adminApi.updateApiKeyCreation(userId, disabled)
    replaceUser(updated)
  }

  // ─── Admin API Keys ───────────────────────────────────────────────────────────

  async function listApiKeysForUser(userId: string): Promise<ApiKey[]> {
    const keys = await adminApi.listApiKeysForUser(userId)
    return keys.map((k) => ({ ...k, disabled: k.disabledAt !== null }))
  }

  async function createApiKeyForUser(userId: string, name: string): Promise<ApiKeyCreated> {
    return adminApi.createApiKeyForUser(userId, name)
  }

  async function disableApiKey(id: string): Promise<void> {
    await adminApi.disableApiKey(id)
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
    const { content: _content, items: _items, ...meta } = result
    reportsPagination.value = meta
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

  async function disableAnnouncement(id: string): Promise<void> {
    const updated = await adminApi.disableAnnouncement(id)
    const idx = announcements.value.findIndex((announcement) => announcement.id === id)
    if (idx >= 0) announcements.value[idx] = updated
  }

  async function fetchSecurityEvents(limit = 50): Promise<void> {
    securityEvents.value = await adminApi.listSecurityEvents(limit)
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────────

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
    updateApiKeyCreation,
    listApiKeysForUser,
    createApiKeyForUser,
    disableApiKey,
    fetchReports,
    fetchReportDetail,
    replyToReport,
    closeReport,
    fetchFeatureFlags,
    upsertFeatureFlag,
    setFeatureFlagEnabled,
    fetchAnnouncements,
    createAnnouncement,
    disableAnnouncement,
    fetchSecurityEvents,
  }
})
