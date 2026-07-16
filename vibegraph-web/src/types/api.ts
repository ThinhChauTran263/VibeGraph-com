// src/types/api.ts
// Mirrors backend DTOs in com.vibegraph.auth.dto.*

// ─── Feedback enums ────────────────────────────────────────────────────────────

export type FeedbackCategory = 'BUG' | 'PROJECT' | 'QUOTA' | 'FEATURE' | 'OTHER'

/** Matches Java FeedbackReportStatus */
export type FeedbackReportStatus = 'OPEN' | 'CLOSED'

/** Matches Java FeedbackSenderRole */
export type FeedbackSenderRole = 'USER' | 'ADMIN'

// ─── Pagination ────────────────────────────────────────────────────────────────

/** Generic paginated response matching AdminPageResponse<T> and similar */
export interface PagedResponse<T> {
  content?: T[]
  items?: T[]
  totalElements: number
  totalPages: number
  pageNumber?: number
  pageSize?: number
  page?: number
  size?: number
}

// ─── User / Account types ──────────────────────────────────────────────────────

/**
 * User profile returned by GET /api/account/profile (UserResponse).
 * Also used as a lightweight shape in list/detail views.
 */
export interface UserProfile {
  id: string
  email: string
  displayName: string
  role: string
  /** Computed account status from backend: ACTIVE | BLOCKED | DEACTIVATED */
  accountStatus?: string
  /**
   * @deprecated Use accountStatus. Kept for backwards compat with older views.
   * Maps to accountStatus.toLowerCase() when fetched from the server.
   */
  status: 'active' | 'blocked' | 'deactivated'
  /** Public-facing reason shown to the user (safe, no internal info) */
  safeReason?: string
}

/**
 * Full user detail returned by admin endpoints (AdminUserResponse).
 * Mirrors the backend AdminUserResponse record exactly.
 */
export interface AdminUserResponse {
  id: string
  email: string
  displayName: string
  role: string
  deactivated: boolean
  deactivationReason: string | null
  deactivationReasonSafe: string | null
  blocked: boolean
  blockedReason: string | null
  blockedReasonSafe: string | null
  planCode: string
  storageQuotaOverrideBytes: number | null
  creditQuotaOverride: number | null
  /** Effective quota in bytes (plan default or override) */
  quotaBytes: number
  /** Currently used bytes */
  usedBytes: number
  apiKeyCreationDisabled: boolean
}

/**
 * Usage quota for the current user (AccountUsageResponse).
 * Backend returns byte-based values; convenience MB helpers are computed in the store.
 */
export interface UserUsage {
  usedBytes: number
  limitBytes: number
  remainingBytes: number
  planCode: string
  planName: string
  quotaOverrideBytes: number | null
  // Derived MB helpers (populated by store)
  sourceStorageUsed?: number
  sourceStorageLimit?: number
  creditsUsed?: number
  creditsLimit?: number
  apiKeyLimit?: number
  apiKeysDisabled?: boolean
}

/** Recent credit ledger row returned by GET /api/account/usage/ledger. */
export interface CreditLedgerEntry {
  id: string
  source: string
  operationCode: string
  creditsDelta: number
  projectId: string | null
  createdAt: string | null
}

/** Imported project owned by the current user (AccountProjectResponse). */
export interface Project {
  id: string
  name: string
  sourceType: string | null
  sizeBytes: number
  status: string | null
  createdAt: string | null
  updatedAt: string | null
  /** @deprecated use status. Kept for backwards compat. */
  lastAnalyzedAt: string | null
}

// ─── API Keys ──────────────────────────────────────────────────────────────────

/** API key metadata returned by list endpoints (ApiKeyResponse). */
export interface ApiKey {
  id: string
  projectId?: string | null
  projectName?: string | null
  /** The masked prefix shown to users, e.g. "vg-abc12" */
  keyPrefix: string
  name: string
  createdAt: string
  lastUsedAt: string | null
  expiresAt: string | null
  /** Non-null means the key is disabled */
  disabledAt: string | null
  /** Convenience getter: true when disabledAt is set */
  disabled: boolean
}

/** Response from POST /api/account/api-keys — includes the one-time secret. */
export interface ApiKeyCreated {
  id: string
  projectId?: string | null
  projectName?: string | null
  keyPrefix: string
  name: string
  /** The full secret — shown exactly once, never retrievable again */
  secretKey: string
  createdAt: string
  expiresAt: string | null
}

// ─── Admin Overview ────────────────────────────────────────────────────────────

/** Admin dashboard stats (AdminOverviewResponse). */
export interface AdminOverview {
  totalUsers: number
  onlineUsers: number
  totalProjects: number
  totalReports: number
  openReports: number
  blockedUsers: number
  timestamp: string | null
  userGrowth?: AdminSeriesPoint[]
  creditConsumption?: AdminSeriesPoint[]
  storage?: AdminStorageOverview | null
  planDistribution?: AdminDistributionPoint[]
  topStorageUsers?: AdminStorageSubject[]
  topStorageProjects?: AdminStorageSubject[]
  securityAlerts?: AdminSecurityAlert[]
}

export interface AdminSeriesPoint {
  label: string
  value: number
  period?: 'month' | 'quarter' | 'year' | string
}

export interface AdminDistributionPoint {
  label: string
  value: number
}

export interface AdminStorageOverview {
  usedBytes: number
  totalBytes: number
  sourceLabel?: string | null
  mountPath?: string | null
}

export interface AdminStorageSubject {
  id: string
  name: string
  ownerEmail?: string | null
  usedBytes: number
}

export interface AdminSecurityAlert {
  id?: string
  type: string
  severity?: string | null
  summary: string
  createdAt?: string | null
}

export interface AdminFeatureFlag {
  key: string
  scope: 'GLOBAL' | 'MCP_TOOL' | string
  displayName: string
  enabled: boolean
  description: string | null
  updatedAt: string | null
}

export interface AdminFeatureFlagRequest {
  key: string
  scope: 'GLOBAL' | 'MCP_TOOL'
  displayName: string
  enabled: boolean
  description?: string | null
}

export interface AdminAnnouncement {
  id: string
  type:
    | 'MAINTENANCE'
    | 'PLAN_CHANGE'
    | 'DISK_WARNING'
    | 'CLI_UPDATE'
    | 'SECURITY'
    | 'GENERAL'
    | string
  severity: 'INFO' | 'WARNING' | 'CRITICAL' | string
  target: 'ALL' | 'USER' | 'ADMIN' | string
  title: string
  body: string
  startsAt: string | null
  endsAt: string | null
  dismissible: boolean
  active: boolean
}

export interface AdminAnnouncementRequest {
  type: 'MAINTENANCE' | 'PLAN_CHANGE' | 'DISK_WARNING' | 'CLI_UPDATE' | 'SECURITY' | 'GENERAL'
  severity: 'INFO' | 'WARNING' | 'CRITICAL'
  target: 'ALL' | 'USER' | 'ADMIN'
  title: string
  body: string
  startsAt?: string | null
  endsAt?: string | null
  dismissible: boolean
  active: boolean
}

export interface AdminSecurityEvent {
  id: string
  eventType: string
  severity: string
  subjectUserId: string | null
  apiKeyRef: string | null
  source: string | null
  description: string
  createdAt: string | null
}

export interface AdminPlan {
  code: string
  name: string
  storageLimitBytes: number
  apiKeyLimit: number
  monthlyCreditLimit: number
  contactSalesRequired: boolean
}

export interface AdminPlanRequest extends AdminPlan {
  active: boolean
  sortOrder: number
}

export interface AdminPricingRule {
  operationCode: string
  displayName: string
  baseCredits: number | string
  perFileCredits: number | string
  perMbCredits: number | string
  per1kNodesCredits: number | string
  minimumCredits: number
  active: boolean
}

export interface AdminPricingRuleRequest extends AdminPricingRule {}

// ─── Reports / Feedback ────────────────────────────────────────────────────────

/** A message in a report thread (FeedbackMessageResponse). */
export interface ReportMessage {
  id: string
  senderRole: FeedbackSenderRole
  /** Message content */
  body: string
  createdAt: string
  // Computed by frontend for display
  isAdmin: boolean
  senderName: string
}

export type ReportRealtimeEventType = 'REPORT_MESSAGE_ADDED' | 'REPORT_CLOSED'

/** Lightweight report summary used by realtime websocket events. */
export interface ReportSummary {
  id: string
  status: FeedbackReportStatus
  category: FeedbackCategory
  title: string
  createdAt: string
  closedAt: string | null
  deletesAfter: string | null
}

export interface ReportRealtimeEvent {
  type: ReportRealtimeEventType
  reportId: string
  report?: ReportSummary | null
  message?: ReportMessage | null
  timestamp: string
}

/** Report summary (FeedbackReportResponse). */
export interface Report {
  id: string
  status: FeedbackReportStatus
  category: FeedbackCategory
  /** Report title / subject */
  title: string
  createdAt: string
  closedAt: string | null
  deletesAfter: string | null
  /** Populated after fetching detail */
  messages: ReportMessage[]
  // Aliases for backwards compat
  /** @deprecated use title */
  subject?: string
  updatedAt?: string
}

/** Admin-side report summary (AdminFeedbackResponse). */
export interface AdminReport {
  id: string
  userId: string
  status: string
  category: string
  title: string
  createdAt: string
  closedAt: string | null
  deleteAfter: string | null
  /** Populated after fetching detail */
  messages: ReportMessage[]
}
