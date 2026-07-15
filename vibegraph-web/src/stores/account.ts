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
  const apiKeys = ref<ApiKey[]>([])
  const reports = ref<Report[]>([])

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

  // ─── Usage ──────────────────────────────────────────────────────────────────

  async function fetchUsage(): Promise<void> {
    const data = await accountApi.getUsage()
    // Expose MB-based helpers alongside raw bytes for backward compat
    const MB = 1024 * 1024
    usage.value = {
      ...data,
      sourceStorageUsed: Math.round(data.usedBytes / MB),
      sourceStorageLimit: Math.round(data.limitBytes / MB),
    }
  }

  async function fetchCreditLedger(limit = 10): Promise<void> {
    creditLedger.value = await accountApi.getCreditLedger(limit)
  }

  // ─── Projects ───────────────────────────────────────────────────────────────

  async function fetchProjects(): Promise<void> {
    const page = await accountApi.getProjects(0, 100)
    const projectItems = page.items ?? page.content ?? []
    projects.value = projectItems.map((p) => ({
      ...p,
      // backwards-compat alias
      lastAnalyzedAt: p.updatedAt,
    }))
  }

  // ─── API Keys ────────────────────────────────────────────────────────────────

  async function fetchApiKeys(): Promise<void> {
    const keys = await accountApi.listApiKeys()
    apiKeys.value = keys.map((k) => ({
      ...k,
      disabled: k.disabledAt !== null,
    }))
  }

  /**
   * Creates an API key and prepends it to the list.
   * Returns the full create response — caller must display the `secretKey` immediately.
   */
  async function createApiKey(name: string, projectId?: string): Promise<ApiKeyCreated> {
    const created = await accountApi.createApiKey(name, projectId)
    // Add a display entry to the list (without secret — already gone)
    const listEntry: ApiKey = {
      id: created.id,
      projectId: created.projectId ?? projectId,
      projectName:
        created.projectName ??
        projects.value.find((project) => project.id === projectId)?.name ??
        null,
      keyPrefix: created.keyPrefix,
      name: created.name,
      createdAt: created.createdAt,
      lastUsedAt: null,
      expiresAt: created.expiresAt,
      disabledAt: null,
      disabled: false,
    }
    apiKeys.value = [listEntry, ...apiKeys.value]
    return created
  }

  async function disableApiKey(id: string): Promise<void> {
    await accountApi.disableApiKey(id)
    const key = apiKeys.value.find((k) => k.id === id)
    if (key) {
      key.disabledAt = new Date().toISOString()
      key.disabled = true
    }
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

  async function closeReport(reportId: string): Promise<void> {
    await accountApi.closeReport(reportId)
    const report = reports.value.find((r) => r.id === reportId)
    if (report) report.status = 'CLOSED'
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
    usage,
    creditLedger,
    projects,
    apiKeys,
    reports,
    planName,
    fetchProfile,
    updateDisplayName,
    changePassword,
    fetchUsage,
    fetchCreditLedger,
    fetchProjects,
    fetchApiKeys,
    createApiKey,
    disableApiKey,
    fetchReports,
    createReport,
    fetchReportDetail,
    addMessage,
    closeReport,
  }
})
