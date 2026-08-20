// src/types/api.ts
// Mirrors backend DTOs in com.vibegraph.auth.dto.*

// ─── Feedback enums ────────────────────────────────────────────────────────────

export type FeedbackCategory = 'BUG' | 'PROJECT' | 'QUOTA' | 'FEATURE' | 'OTHER'

export type KnownApiErrorCode =
  | 'ACCOUNT_BLOCKED'
  | 'ACCOUNT_DEACTIVATED'
  | 'FEATURE_DISABLED'
  | 'QUOTA_EXCEEDED'
  | 'CREDIT_EXHAUSTED'
  | 'CONCURRENT_IMPORT_LIMIT'
  | 'TOO_MANY_REQUESTS'
  | 'IP_BLOCKED'

/** Backend error codes are extensible; known Phase 7 codes are provided for narrowing. */
export type ApiErrorCode = KnownApiErrorCode | (string & {})

export interface ApiErrorPayload {
  code: ApiErrorCode
  message: string
  details?: string | null
}

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

/** Safe account state returned by GET /api/account/session-state. */
export interface FeatureCapability {
  enabled: boolean
  reason: string | null
}

export interface AccountSessionState {
  id: string
  email: string
  displayName: string
  role: string
  accountStatus: string
  safeReason: string | null
  features: Record<string, FeatureCapability>
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
  storageQuotaOverrideMb?: number | null
  quotaMb?: number
  usedMb?: number
  /** Compatibility fields for older running backend images. */
  storageQuotaOverrideBytes?: number | null
  quotaBytes?: number
  usedBytes?: number
  creditQuotaOverride: number | null
  apiKeyCreationDisabled: boolean
}

/** Usage quota returned by GET /api/account/usage (AccountUsageResponse). */
export interface UserUsage {
  usedMb: number
  limitMb: number
  remainingMb: number
  planCode: string
  planName: string
  quotaOverrideMb: number | null
  creditsUsed: number
  creditsLimit: number
  creditsRemaining: number
  /** Compatibility fields for older running backend images. */
  usedBytes?: number
  limitBytes?: number
  remainingBytes?: number
  quotaOverrideBytes?: number | null
  /** Compatibility aliases populated by the account store. */
  sourceStorageUsed?: number
  sourceStorageLimit?: number
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

export interface ApiKeyProjectBinding {
  id: string
  name: string
  sourceType: string | null
  status: string | null
}

export interface ApiKeyCreateRequest {
  name: string
  projectId: string
}

/** API key metadata returned by list endpoints (ApiKeyResponse). */
export interface ApiKey {
  id: string
  /** The masked prefix shown to users, e.g. "vg-abc12" */
  keyPrefix: string
  name: string
  project: ApiKeyProjectBinding | null
  createdAt: string
  lastUsedAt: string | null
  expiresAt: string | null
  /** Non-null means the key is disabled */
  disabledAt: string | null
  disabledBy?: 'USER' | 'ADMIN' | null
  disabledReason?: string | null
  lockedAt?: string | null
  lockedBy?: string | null
  locked?: boolean
  deletedAt?: string | null
  canDelete?: boolean
  /** Convenience getter derived by the frontend store. */
  disabled: boolean
}

/** Response from POST /api/account/api-keys — includes the one-time secret. */
export interface ApiKeyCreated {
  id: string
  keyPrefix: string
  name: string
  project: ApiKeyProjectBinding
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
  onlineUserHistory?: AdminSeriesPoint[]
}

export interface AdminSeriesPoint {
  label: string
  value: number
  period?: 'month' | 'quarter' | 'year' | string
}

/** Live online-users snapshot pushed over /topic/admin/online-users (OnlineUsersEvent). */
export interface AdminOnlineUsersEvent {
  onlineUsers: number
  capturedAt: string
  samples: AdminSeriesPoint[]
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
  createdByUserId: string | null
  creatorDisplayName: string | null
  creatorEmail: string | null
  createdAt: string | null
  /** @deprecated Use creatorDisplayName or creatorEmail. */
  creatorName?: string | null
}

/** Persisted user notification materialized from an announcement. */
export interface UserNotification {
  id: string
  announcementId: string
  title: string
  body: string
  creatorName: string
  creatorDisplayName: string | null
  creatorEmail: string | null
  createdAt: string
  severity: string
  type: string
  dismissible: boolean
  read: boolean
  readAt: string | null
  dismissedAt: string | null
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

export interface AdminCreditLedgerEntry {
  id: string
  userId: string
  projectId: string | null
  balanceId: string | null
  source: string
  operationCode: string
  creditsDelta: number
  metadata: string
  createdAt: string | null
}

export interface AdminCreditOverview {
  userId: string
  currentCreditsLimit: number
  creditsUsed: number
  creditsAdjustment: number
  creditBalance: number
  ledgerHistory: AdminCreditLedgerEntry[]
}

export interface AdminRequestEvent {
  id: string
  userId: string | null
  userDisplayName: string | null
  userEmail: string | null
  apiKeyRef: string | null
  ipAddress: string | null
  route: string
  method: string
  status: number
  eventType: string
  occurredAt: string
}

export interface AdminRequestAggregate {
  userId: string | null
  userDisplayName: string | null
  userEmail: string | null
  apiKeyRef: string | null
  ipAddress: string | null
  minuteBucket: string
  requestsPerMinute: number
}

export interface AdminNetworkBreakdown {
  userId: string | null
  userDisplayName: string | null
  userEmail: string | null
  apiKeyRef: string | null
  requests: number
}

export interface AdminSuspiciousNetwork {
  ipAddress: string
  minuteBucket: string
  totalRequests: number
  uniqueUsers: number
  uniqueApiKeys: number
  breakdown: AdminNetworkBreakdown[]
}

export interface AdminIpBlockRequest {
  ipAddress: string
  safeReason: string
  expiresAt: string | null
  active: boolean
}

export interface AdminIpBlock extends AdminIpBlockRequest {
  id: string
  createdBy: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminAuditLogQuery {
  action?: string
  outcome?: string
  actorUserId?: string
  targetUserId?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface AdminAuditLog {
  id: string
  action: string
  actorUserId: string | null
  actorDisplayName?: string | null
  targetUserId: string | null
  targetUserDisplayName?: string | null
  targetType: string | null
  targetId: string | null
  outcome: string
  ipAddress: string | null
  details: string | null
  createdAt: string | null
}

export interface AdminAuditRetention {
  retentionDays: number
  updatedBy: string | null
  updatedAt: string | null
}

export interface AdminPlan {
  code: string
  name: string
  storageLimitMb: number
  /** Compatibility field for pre-Phase-7 payloads. */
  storageLimitBytes?: number
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

export type AdminPricingRuleRequest = AdminPricingRule

// ─── Import pricing tiers ──────────────────────────────────────────────────

/**
 * One size tier of the tiered import billing model.
 * `maxFiles === null` marks the unlimited top tier (e.g. "xlarge").
 */
export interface AdminImportPricingTier {
  tierCode: string
  maxFiles: number | null
  credits: number
}

/** One import method's complete tier configuration. */
export interface AdminImportPricing {
  operationCode: string
  tiers: AdminImportPricingTier[]
}

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
