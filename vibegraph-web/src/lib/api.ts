/**
 * HTTP API client for VibeGraph backend.
 * All responses are wrapped in ApiResponse<T> = { success, data, error }.
 */

import { API_BASE_URL } from './constants'
import http from './http'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types/auth'
import type { GraphData } from '@/types/graph'
import type { ApiErrorCode, ApiErrorPayload } from '@/types/api'

/**
 * A node as returned inside an impact-analysis result. Mirrors the backend
 * `graph.dto.response.NodeDto` Java record. `lineNumber` is nullable on the
 * backend (`Integer`), so it is optional here.
 */
export interface ImpactNode {
  id: string
  type: string
  name: string
  fullName: string
  filePath: string
  lineNumber?: number | null
  properties?: Record<string, unknown>
}

/** Risk levels reported by the backend impact analysis. */
export type ImpactRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

/** One INCOMING/OUTGOING connection of a node. Mirrors `NodeDetailResponse.ConnectionDto`. */
export interface NodeDetailConnection {
  otherNode: ImpactNode
  relationshipType: string
  /** `INCOMING` or `OUTGOING`. */
  direction: string
}

/**
 * Node-with-neighbors detail. Mirrors the backend `NodeDetailResponse`. Used by the Node Detail
 * panel and by lazy graph expansion (pull a node's neighbors on demand instead of the full graph).
 */
export interface NodeDetailResponse {
  node: ImpactNode
  incoming: NodeDetailConnection[]
  outgoing: NodeDetailConnection[]
}

/**
 * Blast-radius analysis result.
 * Mirrors the backend `graph.dto.response.ImpactAnalysisResponse` record.
 *
 * Affected nodes are grouped by traversal depth:
 * - `willBreak`     — d=1 direct dependents (WILL BREAK)
 * - `likelyAffected`— d=2 indirect dependents (LIKELY AFFECTED)
 * - `mayNeedTesting`— d>=3 transitive dependents (MAY NEED TESTING)
 */
export interface ImpactAnalysisResponse {
  target: ImpactNode
  /** `LOW` | `MEDIUM` | `HIGH` | `CRITICAL`; typed loosely to tolerate backend drift. */
  riskLevel: string
  directDependents: number
  totalDependents: number
  willBreak: ImpactNode[]
  likelyAffected: ImpactNode[]
  mayNeedTesting: ImpactNode[]
}

/**
 * A bounded, redacted slice of a source file returned by the code viewer endpoint.
 * Mirrors the backend `SourceFileService.SourceContent` record.
 *
 * `found=false` means the file exists in the graph but cannot be served as source
 * (disallowed extension, missing on disk, binary, …) — `truncationReason` carries why.
 * Paths are always project-relative; an absolute host path is never returned.
 */
export interface SourceContent {
  found: boolean
  relativePath: string
  language: string
  startLine: number
  endLine: number
  totalLines: number
  content: string
  truncated: boolean
  truncationReason?: string | null
  warnings: string[]
}

/** Traversal depths the backend accepts (see GraphServiceImpl ALLOWED_IMPACT_DEPTHS). */
export const IMPACT_ALLOWED_DEPTHS = [1, 2, 3, 5] as const
export type ImpactDepth = (typeof IMPACT_ALLOWED_DEPTHS)[number]

/** Impact traversal profiles accepted by the backend. */
export const IMPACT_PROFILES = ['dependency', 'structural', 'type-data-flow'] as const
export type ImpactProfile = (typeof IMPACT_PROFILES)[number]

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly statusText: string,
    message?: string,
    public readonly code?: ApiErrorCode,
    public readonly details?: string | null,
  ) {
    super(message ?? `HTTP ${status}: ${statusText}`)
    this.name = 'ApiError'
  }
}

interface ApiResponse<T> {
  success: boolean
  data: T
  error?: ApiErrorPayload | null
}

/**
 * Project DTO returned by both `POST /api/projects` and
 * `POST /api/projects/import-archive`. Mirrors the backend
 * `graph.dto.response.ProjectResponse` Java record.
 */
export interface Project {
  id: string
  name: string
  /** ISO-8601 timestamp string (Java `Instant`). */
  createdAt?: string
  /** ISO-8601 timestamp string; absent until first analyze completes. */
  lastAnalyzedAt?: string
  totalFiles: number
  totalNodes: number
  totalEdges: number
  /** Backend status enum: `ANALYZING`, `ANALYZED`, or `FAILED`. */
  status: string
  /** Analysis progress 0-100. Present on async import (202) and status events. */
  progress?: number
}

/** Terminal + in-flight statuses pushed over the project-status WebSocket topic. */
export type ProjectStatus = 'ANALYZING' | 'ANALYZED' | 'FAILED'

/**
 * Status event pushed to `/topic/projects/{projectId}/status` while an async
 * archive import is analyzing. Mirrors the backend status payload.
 */
export interface ProjectStatusEvent {
  projectId: string
  status: ProjectStatus
  /** Progress 0-100. */
  progress: number
  /** Human-readable detail; null when the backend has nothing to add. */
  message: string | null
  /** ISO-8601 timestamp string. */
  timestamp: string
}

/**
 * Shared 401 handler for all fetch-based API calls.
 * Clears stored auth session and redirects to /login (unless already there).
 */
function handleUnauthorized(): void {
  localStorage.removeItem('vg_token')
  localStorage.removeItem('vg_user')
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
    if (res.status === 401) {
      handleUnauthorized()
    }
    const error = await extractApiError(res)
    throw new ApiError(res.status, res.statusText, error.message, error.code, error.details)
  }
  const json = (await res.json()) as ApiResponse<T>
  if (!json.success) {
    throw new ApiError(
      400,
      'API Error',
      json.error?.message ?? 'Unknown error',
      json.error?.code,
      json.error?.details,
    )
  }
  return json.data
}

interface ExtractedApiError {
  message?: string
  code?: ApiErrorCode
  details?: string | null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parseApiError(value: unknown): ExtractedApiError | null {
  if (!isRecord(value) || !isRecord(value.error)) return null
  const error = value.error
  return {
    message: typeof error.message === 'string' ? error.message : undefined,
    code: typeof error.code === 'string' ? error.code : undefined,
    details:
      typeof error.details === 'string' || error.details === null ? error.details : undefined,
  }
}

async function extractApiError(res: Response): Promise<ExtractedApiError> {
  const text = await res.text().catch(() => '')
  if (!text) return {}
  try {
    return parseApiError(JSON.parse(text)) ?? { message: text }
  } catch {
    return { message: text }
  }
}

export const api = {
  baseUrl: API_BASE_URL,

  /** Browser auth uses the HttpOnly cookie; CLI/API clients keep their own Bearer-token flow. */
  _authHeaders(): Record<string, string> {
    return {}
  },

  async get<T>(path: string): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      credentials: 'include',
      headers: { 'X-VibeGraph-Client': 'web', ...this._authHeaders() },
    })
    return unwrap<T>(res)
  },

  async post<T>(path: string, body?: unknown): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-VibeGraph-Client': 'web',
        ...this._authHeaders(),
      },
      body: body ? JSON.stringify(body) : undefined,
    })
    return unwrap<T>(res)
  },

  async patch<T>(path: string, body?: unknown): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'PATCH',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-VibeGraph-Client': 'web',
        ...this._authHeaders(),
      },
      body: body ? JSON.stringify(body) : undefined,
    })
    return unwrap<T>(res)
  },

  async put<T>(path: string, body?: unknown): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'PUT',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-VibeGraph-Client': 'web',
        ...this._authHeaders(),
      },
      body: body ? JSON.stringify(body) : undefined,
    })
    return unwrap<T>(res)
  },

  /**
   * POST a `multipart/form-data` request. Do NOT set `Content-Type` manually -
   * the browser must compute the multipart boundary. Setting it explicitly
   * breaks the upload.
   */
  async postMultipart<T>(path: string, form: FormData): Promise<T> {
    const authHeaders = this._authHeaders()
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-VibeGraph-Client': 'web', ...authHeaders },
      body: form,
    })
    return unwrap<T>(res)
  },

  async delete(path: string): Promise<void> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: { 'X-VibeGraph-Client': 'web', ...this._authHeaders() },
    })
    if (!res.ok) {
      if (res.status === 401) {
        handleUnauthorized()
      }
      const error = await extractApiError(res)
      throw new ApiError(res.status, res.statusText, error.message, error.code, error.details)
    }
  },
}

/**
 * Fetch the full graph for a project.
 * GET /api/projects/{projectId}/graph
 */
export async function fetchFullGraph(projectId: string): Promise<GraphData> {
  return api.get<GraphData>(`/api/projects/${projectId}/graph`)
}

// Project endpoints
export const projectApi = {
  list: () => api.get<Project[]>('/api/projects'),
  get: (id: string) => api.get<Project>(`/api/projects/${id}`),
  create: (data: unknown) => api.post<Project>('/api/projects', data),
  remove: (id: string) => api.delete(`/api/projects/${encodeURIComponent(id)}`),
}

/**
 * Project import endpoints.
 *
 * `POST /api/projects/import-archive` accepts a `multipart/form-data` body
 * with two fields:
 *   - `name` - string, the project name
 *   - `file` - the archive file (.zip, .tar, .tar.gz, .tgz)
 *
 * Two modes:
 *   - `uploadArchive` (default, sync): server analyzes inline and returns a
 *     terminal `ANALYZED` project. Behavior is unchanged from the baseline.
 *   - `uploadArchiveAsync`: adds `?async=true`, server returns `202 Accepted`
 *     with an `ANALYZING` project; progress arrives over the WebSocket
 *     `/topic/projects/{id}/status` topic.
 *
 * The frontend treats a network error or non-2xx response as an upload
 * failure (see `useArchiveImport`).
 */
export const importApi = {
  uploadArchive(name: string, file: File): Promise<Project> {
    const form = new FormData()
    form.append('name', name)
    form.append('file', file)
    return api.postMultipart<Project>('/api/projects/import-archive', form)
  },

  uploadArchiveAsync(name: string, file: File): Promise<Project> {
    const form = new FormData()
    form.append('name', name)
    form.append('file', file)
    return api.postMultipart<Project>('/api/projects/import-archive?async=true', form)
  },

  importGithub(url: string): Promise<Project> {
    return api.post<Project>('/api/projects/import-github', { url })
  },

  /**
   * Import an existing directory on the backend host in place (no upload). The backend
   * analyzes it and starts a file watcher so later edits stream realtime graph updates.
   */
  importLocal(path: string, name?: string): Promise<Project> {
    return api.post<Project>('/api/projects/import-local', {
      path,
      name: name?.trim() || undefined,
    })
  },
}

/** A sub-directory entry returned by the server-side directory browser. */
export interface DirectoryEntry {
  name: string
  path: string
  /** Best-effort hint that the directory holds `.java` sources. */
  containsJava: boolean
}

/** Result of browsing a directory on the backend host. */
export interface DirectoryListing {
  path: string
  /** Parent directory path, or `null` at the allowed base (cannot navigate above it). */
  parent: string | null
  entries: DirectoryEntry[]
}

/**
 * Server-side directory picker. Browsing is confined to a base directory on the backend
 * (the configured allowed-root, else the host user's home), so it never exposes the whole disk.
 */
export const browseApi = {
  browse(path?: string): Promise<DirectoryListing> {
    const query = path ? `?${new URLSearchParams({ path })}` : ''
    return api.get<DirectoryListing>(`/api/projects/browse${query}`)
  },
}

// Graph endpoints
export const graphApi = {
  getGraph: (projectId: string) => fetchFullGraph(projectId),
  getNeighbors: (projectId: string, nodeId: string, hops: number) =>
    api.get<NodeDetailResponse>(
      `/api/projects/${projectId}/graph/neighbors?${new URLSearchParams({ nodeId, hops: String(hops) })}`,
    ),

  /**
   * Fetch the blast radius (impact analysis) for a node.
   * GET /api/projects/{projectId}/graph/impact?nodeId=...&depth=...&profile=...
   *
   * `depth` must be one of {@link IMPACT_ALLOWED_DEPTHS}; the backend rejects
   * other values with a 400. Query params are encoded via URLSearchParams.
   */
  getImpact: (
    projectId: string,
    nodeId: string,
    depth: number,
    profile: ImpactProfile = 'dependency',
  ) => {
    const query = new URLSearchParams({ nodeId, depth: String(depth), profile })
    return api.get<ImpactAnalysisResponse>(`/api/projects/${projectId}/graph/impact?${query}`)
  },

  /**
   * Read a bounded slice of a source file for the in-app code viewer.
   * GET /api/projects/{projectId}/source?path=...&startLine=...&endLine=...
   *
   * `path` is typically the selected node's absolute `filePath`; the backend confines it to the
   * project source root and rejects traversal. `startLine`/`endLine` are 1-based; omit them to
   * read from the top (the server caps the window size and reports truncation).
   */
  getSource: (projectId: string, path: string, startLine?: number, endLine?: number) => {
    const params = new URLSearchParams({ path })
    if (startLine != null) params.set('startLine', String(startLine))
    if (endLine != null) params.set('endLine', String(endLine))
    return api.get<SourceContent>(`/api/projects/${encodeURIComponent(projectId)}/source?${params}`)
  },
}

export interface DiagramResponse {
  diagramType: string
  mermaidSyntax: string
  plantUmlSyntax?: string | null
  /** Distinct packages containing classifiers; used to drive the class-diagram package filter. */
  availablePackages?: string[]
}

/** UML Use Case layout mode. `detailed` is the default flat business-facing diagram. */
export type UmlUseCaseMode = 'detailed' | 'grouped'

/** Inferred business actor (e.g. `Admin`, `User`). Mirrors backend `UmlUseCaseResponse.Actor`. */
export interface UmlActor {
  id: string
  name: string
  source: string
  confidence: number
}

/** Inferred business use case (verb phrase). Mirrors backend `UmlUseCaseResponse.UseCaseElement`. */
export interface UmlUseCaseElement {
  id: string
  name: string
  domain: string
  /** `summary` (grouped) or `detail` (single CRUD action). */
  level: string
  source: string
  /** Originating endpoint id (e.g. `POST /api/products`); null for summary use cases. */
  sourceEndpoint?: string | null
  confidence: number
}

/** Edge between elements: actor-to-usecase association or summary-to-detail include. */
export interface UmlRelation {
  from: string
  to: string
  /** `association` or `include`. */
  type: string
  label?: string | null
  confidence: number
}

/**
 * A per-actor or per-domain projection of the same canonical UML model (R4). Pure projection of
 * the full diagram, so a view can never disagree with it. Mirrors backend `UseCaseView`.
 */
export interface UmlUseCaseView {
  /** `actor` or `domain`. */
  viewType: string
  /** The actor name or domain this view is scoped to. */
  title: string
  actors: UmlActor[]
  useCases: UmlUseCaseElement[]
  relations: UmlRelation[]
  mermaidSyntax?: string | null
  plantUmlSyntax?: string | null
}

/**
 * Business-level UML Use Case diagram. Mirrors the backend `UmlUseCaseResponse`.
 * Holds inferred actors and verb-phrased use cases, plus a Mermaid fallback and
 * a standard PlantUML source for proper UML render/copy.
 */
export interface UmlUseCaseResponse {
  diagramType: string
  style: string
  mode: string
  systemName: string
  actors: UmlActor[]
  useCases: UmlUseCaseElement[]
  relations: UmlRelation[]
  warnings: string[]
  mermaidSyntax: string
  plantUmlSyntax: string
  /** Per-actor and per-domain projections of the same model; optional/empty for non-UML styles. */
  views?: UmlUseCaseView[]
}

// Diagram endpoints
export const diagramApi = {
  /**
   * Inferred business UML Use Case diagram (`style=uml&mode=detailed|grouped`).
   */
  umlUseCase: (projectId: string, mode: UmlUseCaseMode = 'detailed') => {
    const query = new URLSearchParams({ style: 'uml', mode })
    return api.get<UmlUseCaseResponse>(
      `/api/projects/${encodeURIComponent(projectId)}/diagrams/usecase?${query}`,
    )
  },
  classDiagram: (projectId: string, pkg?: string) => {
    const query = pkg ? `?${new URLSearchParams({ package: pkg })}` : ''
    return api.get<DiagramResponse>(
      `/api/projects/${encodeURIComponent(projectId)}/diagrams/class${query}`,
    )
  },
  sequence: (projectId: string, entry: string) => {
    const query = new URLSearchParams({ entry })
    return api.get<DiagramResponse>(
      `/api/projects/${encodeURIComponent(projectId)}/diagrams/sequence?${query}`,
    )
  },
}

// ─── Auth API ──────────────────────────────────────────────────────────────────

/**
 * Auth endpoints. login/register use the base `api` object (no Bearer token needed,
 * no 401 redirect for invalid credentials). `me()` uses the authenticated `http` instance.
 */
export const authApi = {
  register(data: RegisterRequest): Promise<AuthResponse> {
    return api.post<AuthResponse>('/api/auth/register', data)
  },

  login(data: LoginRequest): Promise<AuthResponse> {
    return api.post<AuthResponse>('/api/auth/login', data)
  },

  logout(): Promise<void> {
    return api.post<void>('/api/auth/logout')
  },

  async me(): Promise<User> {
    const res = await http.get<{ success: boolean; data: User }>('/api/auth/me')
    // Tùy thuộc vào cấu trúc trả về của backend, có thể là res.data hoặc res.data.data
    return res.data.data
  },
}

// --- Account (user-side) API ---

import type {
  UserProfile,
  UserUsage,
  CreditLedgerEntry,
  Project as AccountProject,
  ApiKey,
  ApiKeyCreated,
  Report,
  ReportMessage,
  UserNotification,
  AccountSessionState,
  PagedResponse,
  FeedbackCategory,
  AdminOverview,
  AdminPlan,
  AdminPlanRequest,
  AdminPricingRule,
  AdminPricingRuleRequest,
  AdminUserResponse,
  AdminReport,
  AdminFeatureFlag,
  AdminFeatureFlagRequest,
  AdminAnnouncement,
  AdminAnnouncementRequest,
  AdminSecurityEvent,
  AdminCreditOverview,
  AdminRequestEvent,
  AdminRequestAggregate,
  AdminIpBlock,
  AdminIpBlockRequest,
  AdminAuditLog,
  AdminAuditRetention,
} from '@/types/api'

/**
 * All user-facing account endpoints under `/api/account/`.
 * Every method returns the unwrapped `data` payload from `ApiResponse<T>`.
 */
export const accountApi = {
  getSessionState(): Promise<AccountSessionState> {
    return api.get<AccountSessionState>('/api/account/session-state')
  },
  getProfile(): Promise<UserProfile> {
    return api.get<UserProfile>('/api/account/profile')
  },
  updateProfile(displayName: string): Promise<UserProfile> {
    return api.patch<UserProfile>('/api/account/profile', { displayName })
  },
  changePassword(oldPassword: string, newPassword: string, confirmPassword: string): Promise<void> {
    return api.patch<void>('/api/account/password', { oldPassword, newPassword, confirmPassword })
  },
  getUsage(): Promise<UserUsage> {
    return api.get<UserUsage>('/api/account/usage')
  },
  getCreditLedger(limit = 10): Promise<CreditLedgerEntry[]> {
    return api.get<CreditLedgerEntry[]>(`/api/account/usage/ledger?limit=${limit}`)
  },
  getProjects(page = 0, size = 20): Promise<PagedResponse<AccountProject>> {
    return api.get<PagedResponse<AccountProject>>(`/api/account/projects?page=${page}&size=${size}`)
  },
  listApiKeys(): Promise<ApiKey[]> {
    return api.get<ApiKey[]>('/api/account/api-keys')
  },
  createApiKey(name: string): Promise<ApiKeyCreated> {
    return api.post<ApiKeyCreated>('/api/account/api-keys', { name })
  },
  disableApiKey(id: string): Promise<void> {
    return api.patch<void>(`/api/account/api-keys/${encodeURIComponent(id)}/disable`, undefined)
  },
  listReports(): Promise<Report[]> {
    return api.get<Report[]>('/api/account/reports')
  },
  createReport(category: FeedbackCategory, title: string, body: string): Promise<Report> {
    return api.post<Report>('/api/account/reports', { category, title, body })
  },
  getReportDetail(reportId: string): Promise<{ report: Report; messages: ReportMessage[] }> {
    return api.get<{ report: Report; messages: ReportMessage[] }>(
      `/api/account/reports/${encodeURIComponent(reportId)}`,
    )
  },
  addMessage(reportId: string, body: string): Promise<ReportMessage> {
    return api.post<ReportMessage>(
      `/api/account/reports/${encodeURIComponent(reportId)}/messages`,
      { body },
    )
  },
  closeReport(reportId: string): Promise<Report> {
    return api.patch<Report>(
      `/api/account/reports/${encodeURIComponent(reportId)}/close`,
      undefined,
    )
  },
  listNotifications(limit = 50): Promise<UserNotification[]> {
    const query = new URLSearchParams({ limit: String(limit) })
    return api.get<UserNotification[]>(`/api/account/notifications?${query}`)
  },
  listAnnouncements(limit = 50): Promise<UserNotification[]> {
    const query = new URLSearchParams({ limit: String(limit) })
    return api.get<UserNotification[]>(`/api/account/announcements?${query}`)
  },
  getNotification(id: string): Promise<UserNotification> {
    return api.get<UserNotification>(`/api/account/notifications/${encodeURIComponent(id)}`)
  },
  markNotificationRead(id: string): Promise<UserNotification> {
    return api.patch<UserNotification>(
      `/api/account/notifications/${encodeURIComponent(id)}/read`,
      undefined,
    )
  },
  dismissNotification(id: string): Promise<UserNotification> {
    return api.patch<UserNotification>(
      `/api/account/notifications/${encodeURIComponent(id)}/dismiss`,
      undefined,
    )
  },
}

// --- Admin API ---

/**
 * All admin endpoints under `/api/admin/`.
 * Every method returns the unwrapped `data` payload from `ApiResponse<T>`.
 */
export const adminApi = {
  getOverview(): Promise<AdminOverview> {
    return api.get<AdminOverview>('/api/admin/overview')
  },
  listPlans(): Promise<AdminPlan[]> {
    return api.get<AdminPlan[]>('/api/admin/plans')
  },
  createPlan(data: AdminPlanRequest): Promise<AdminPlan> {
    return api.post<AdminPlan>('/api/admin/plans', data)
  },
  updateCatalogPlan(code: string, data: AdminPlanRequest): Promise<AdminPlan> {
    return api.put<AdminPlan>(`/api/admin/plans/${encodeURIComponent(code)}`, data)
  },
  deleteCatalogPlan(code: string): Promise<void> {
    return api.delete(`/api/admin/plans/${encodeURIComponent(code)}`)
  },
  listPricingRules(): Promise<AdminPricingRule[]> {
    return api.get<AdminPricingRule[]>('/api/admin/pricing-rules')
  },
  createPricingRule(data: AdminPricingRuleRequest): Promise<AdminPricingRule> {
    return api.post<AdminPricingRule>('/api/admin/pricing-rules', data)
  },
  updatePricingRule(
    operationCode: string,
    data: AdminPricingRuleRequest,
  ): Promise<AdminPricingRule> {
    return api.put<AdminPricingRule>(
      `/api/admin/pricing-rules/${encodeURIComponent(operationCode)}`,
      data,
    )
  },
  deletePricingRule(operationCode: string): Promise<void> {
    return api.delete(`/api/admin/pricing-rules/${encodeURIComponent(operationCode)}`)
  },
  listUsers(
    params: { search?: string; status?: string; plan?: string; page?: number; size?: number } = {},
  ): Promise<PagedResponse<AdminUserResponse>> {
    const q = new URLSearchParams()
    if (params.search) q.set('search', params.search)
    if (params.status) q.set('status', params.status)
    if (params.plan) q.set('plan', params.plan)
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PagedResponse<AdminUserResponse>>(`/api/admin/users?${q}`)
  },
  getUserDetail(userId: string): Promise<AdminUserResponse> {
    return api.get<AdminUserResponse>(`/api/admin/users/${encodeURIComponent(userId)}`)
  },
  createUser(data: {
    email: string
    displayName: string
    role: string
    planCode: string
    temporaryPassword: string
  }): Promise<AdminUserResponse> {
    return api.post<AdminUserResponse>('/api/admin/users', data)
  },
  blockUser(userId: string, reason: string, safeReason: string): Promise<AdminUserResponse> {
    return api.patch<AdminUserResponse>(`/api/admin/users/${encodeURIComponent(userId)}/block`, {
      reason,
      safeReason,
    })
  },
  unblockUser(userId: string): Promise<AdminUserResponse> {
    return api.patch<AdminUserResponse>(
      `/api/admin/users/${encodeURIComponent(userId)}/unblock`,
      undefined,
    )
  },
  deactivateUser(userId: string, reason: string, safeReason: string): Promise<AdminUserResponse> {
    return api.patch<AdminUserResponse>(
      `/api/admin/users/${encodeURIComponent(userId)}/deactivate`,
      { reason, safeReason },
    )
  },
  updatePlan(userId: string, planCode: string): Promise<AdminUserResponse> {
    return api.patch<AdminUserResponse>(`/api/admin/users/${encodeURIComponent(userId)}/plan`, {
      planCode,
    })
  },
  updateQuota(
    userId: string,
    storageQuotaOverrideMb: number | null,
    creditQuotaOverride: number | null,
  ): Promise<AdminUserResponse> {
    return api.patch<AdminUserResponse>(`/api/admin/users/${encodeURIComponent(userId)}/quota`, {
      storageQuotaOverrideMb,
      creditQuotaOverride,
    })
  },
  updateApiKeyCreation(userId: string, disabled: boolean): Promise<AdminUserResponse> {
    return api.patch<AdminUserResponse>(
      `/api/admin/users/${encodeURIComponent(userId)}/api-key-creation`,
      { disabled },
    )
  },
  listApiKeysForUser(userId: string): Promise<ApiKey[]> {
    return api.get<ApiKey[]>(`/api/admin/api-keys?userId=${encodeURIComponent(userId)}`)
  },
  createApiKeyForUser(userId: string, name: string): Promise<ApiKeyCreated> {
    return api.post<ApiKeyCreated>('/api/admin/api-keys', { userId, name })
  },
  disableApiKey(id: string): Promise<void> {
    return api.patch<void>(`/api/admin/api-keys/${encodeURIComponent(id)}/disable`, undefined)
  },
  listReports(
    params: { status?: string; q?: string; page?: number; size?: number } = {},
  ): Promise<PagedResponse<AdminReport>> {
    const qs = new URLSearchParams()
    if (params.status) qs.set('status', params.status)
    if (params.q) qs.set('q', params.q)
    qs.set('page', String(params.page ?? 0))
    qs.set('size', String(params.size ?? 20))
    return api.get<PagedResponse<AdminReport>>(`/api/admin/reports?${qs}`)
  },
  getReportDetail(reportId: string): Promise<{ report: AdminReport; messages: ReportMessage[] }> {
    return api.get<{ report: AdminReport; messages: ReportMessage[] }>(
      `/api/admin/reports/${encodeURIComponent(reportId)}`,
    )
  },
  replyToReport(reportId: string, body: string): Promise<void> {
    return api.post<void>(`/api/admin/reports/${encodeURIComponent(reportId)}/reply`, { body })
  },
  closeReport(reportId: string): Promise<void> {
    return api.patch<void>(`/api/admin/reports/${encodeURIComponent(reportId)}/close`, undefined)
  },
  listFeatureFlags(): Promise<AdminFeatureFlag[]> {
    return api.get<AdminFeatureFlag[]>('/api/admin/feature-flags')
  },
  createFeatureFlag(data: AdminFeatureFlagRequest): Promise<AdminFeatureFlag> {
    return api.post<AdminFeatureFlag>('/api/admin/feature-flags', data)
  },
  updateFeatureFlag(key: string, data: AdminFeatureFlagRequest): Promise<AdminFeatureFlag> {
    return api.put<AdminFeatureFlag>(`/api/admin/feature-flags/${encodeURIComponent(key)}`, data)
  },
  deleteFeatureFlag(key: string): Promise<void> {
    return api.delete(`/api/admin/feature-flags/${encodeURIComponent(key)}`)
  },
  listAnnouncements(): Promise<AdminAnnouncement[]> {
    return api.get<AdminAnnouncement[]>('/api/admin/announcements')
  },
  createAnnouncement(data: AdminAnnouncementRequest): Promise<AdminAnnouncement> {
    return api.post<AdminAnnouncement>('/api/admin/announcements', data)
  },
  updateAnnouncement(id: string, data: AdminAnnouncementRequest): Promise<AdminAnnouncement> {
    return api.put<AdminAnnouncement>(`/api/admin/announcements/${encodeURIComponent(id)}`, data)
  },
  disableAnnouncement(id: string): Promise<AdminAnnouncement> {
    return api.patch<AdminAnnouncement>(
      `/api/admin/announcements/${encodeURIComponent(id)}/disable`,
      undefined,
    )
  },
  deleteAnnouncement(id: string): Promise<void> {
    return api.delete(`/api/admin/announcements/${encodeURIComponent(id)}`)
  },
  getCreditOverview(userId: string): Promise<AdminCreditOverview> {
    return api.get<AdminCreditOverview>(`/api/admin/credits/users/${encodeURIComponent(userId)}`)
  },
  adjustCredits(userId: string, creditsDelta: number, reason: string): Promise<void> {
    return api.post<void>(`/api/admin/credits/users/${encodeURIComponent(userId)}/adjust`, {
      creditsDelta,
      reason,
    })
  },
  listSecurityEvents(limit = 50): Promise<AdminSecurityEvent[]> {
    return api.get<AdminSecurityEvent[]>(`/api/admin/security/events?limit=${limit}`)
  },
  listRequestEvents(limit = 100): Promise<AdminRequestEvent[]> {
    return api.get<AdminRequestEvent[]>(`/api/admin/security/request-events?limit=${limit}`)
  },
  listTopUsers(minutes = 60, limit = 20): Promise<AdminRequestAggregate[]> {
    return api.get<AdminRequestAggregate[]>(
      `/api/admin/security/top-users?minutes=${minutes}&limit=${limit}`,
    )
  },
  listTopIps(minutes = 60, limit = 20): Promise<AdminRequestAggregate[]> {
    return api.get<AdminRequestAggregate[]>(
      `/api/admin/security/top-ips?minutes=${minutes}&limit=${limit}`,
    )
  },
  listIpBlocks(limit = 100): Promise<AdminIpBlock[]> {
    return api.get<AdminIpBlock[]>(`/api/admin/security/ip-blocks?limit=${limit}`)
  },
  createIpBlock(data: AdminIpBlockRequest): Promise<AdminIpBlock> {
    return api.post<AdminIpBlock>('/api/admin/security/ip-blocks', data)
  },
  updateIpBlock(id: string, data: AdminIpBlockRequest): Promise<AdminIpBlock> {
    return api.patch<AdminIpBlock>(`/api/admin/security/ip-blocks/${encodeURIComponent(id)}`, data)
  },
  deleteIpBlock(id: string): Promise<void> {
    return api.delete(`/api/admin/security/ip-blocks/${encodeURIComponent(id)}`)
  },
  listAuditLogs(
    params: {
      action?: string
      outcome?: string
      actorUserId?: string
      targetUserId?: string
      from?: string
      to?: string
      page?: number
      size?: number
    } = {},
  ): Promise<PagedResponse<AdminAuditLog>> {
    const query = new URLSearchParams()
    if (params.action) query.set('action', params.action)
    if (params.outcome) query.set('outcome', params.outcome)
    if (params.actorUserId) query.set('actorUserId', params.actorUserId)
    if (params.targetUserId) query.set('targetUserId', params.targetUserId)
    if (params.from) query.set('from', params.from)
    if (params.to) query.set('to', params.to)
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 50))
    return api.get<PagedResponse<AdminAuditLog>>(`/api/admin/audit-logs?${query}`)
  },
  getAuditLog(id: string): Promise<AdminAuditLog> {
    return api.get<AdminAuditLog>(`/api/admin/audit-logs/${encodeURIComponent(id)}`)
  },
  getAuditRetention(): Promise<AdminAuditRetention> {
    return api.get<AdminAuditRetention>('/api/admin/audit-logs/retention')
  },
  updateAuditRetention(retentionDays: number): Promise<AdminAuditRetention> {
    return api.put<AdminAuditRetention>('/api/admin/audit-logs/retention', { retentionDays })
  },
}
