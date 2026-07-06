/**
 * HTTP API client for VibeGraph backend.
 * All responses are wrapped in ApiResponse<T> = { success, data, error }.
 */

import { API_BASE_URL } from './constants'
import http from './http'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types/auth'
import type { GraphData } from '@/types/graph'

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
  ) {
    super(message ?? `HTTP ${status}: ${statusText}`)
    this.name = 'ApiError'
  }
}

interface ApiResponse<T> {
  success: boolean
  data: T
  error?: { code: string; message: string }
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
    // Try to extract a structured error message from the response body
    // before falling back to the raw text or HTTP status.
    const message = await extractErrorMessage(res)
    throw new ApiError(res.status, res.statusText, message)
  }
  const json = (await res.json()) as ApiResponse<T>
  if (!json.success) {
    throw new ApiError(400, 'API Error', json.error?.message ?? 'Unknown error')
  }
  return json.data
}

async function extractErrorMessage(res: Response): Promise<string | undefined> {
  const text = await res.text().catch(() => '')
  if (!text) return undefined
  try {
    const parsed = JSON.parse(text) as Partial<ApiResponse<unknown>>
    return parsed?.error?.message ?? text
  } catch {
    return text
  }
}

export const api = {
  baseUrl: API_BASE_URL,

  /** Build auth headers if a token is present. */
  _authHeaders(): Record<string, string> {
    const token = localStorage.getItem('vg_token')
    return token ? { Authorization: `Bearer ${token}` } : {}
  },

  async get<T>(path: string): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      headers: { ...this._authHeaders() },
    })
    return unwrap<T>(res)
  },

  async post<T>(path: string, body?: unknown): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...this._authHeaders() },
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
    const hasAuth = Object.keys(authHeaders).length > 0
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      ...(hasAuth ? { headers: authHeaders } : {}),
      body: form,
    })
    return unwrap<T>(res)
  },

  async delete(path: string): Promise<void> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'DELETE',
      headers: { ...this._authHeaders() },
    })
    if (!res.ok) {
      if (res.status === 401) {
        handleUnauthorized()
      }
      const message = await extractErrorMessage(res)
      throw new ApiError(res.status, res.statusText, message)
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
    return api.post<Project>('/api/projects/import-local', { path, name: name?.trim() || undefined })
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
  getImpact: (projectId: string, nodeId: string, depth: number, profile: ImpactProfile = 'dependency') => {
    const query = new URLSearchParams({ nodeId, depth: String(depth), profile })
    return api.get<ImpactAnalysisResponse>(
      `/api/projects/${projectId}/graph/impact?${query}`,
    )
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
    return api.get<SourceContent>(
      `/api/projects/${encodeURIComponent(projectId)}/source?${params}`,
    )
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
    return api.get<DiagramResponse>(`/api/projects/${encodeURIComponent(projectId)}/diagrams/class${query}`)
  },
  sequence: (projectId: string, entry: string) => {
    const query = new URLSearchParams({ entry })
    return api.get<DiagramResponse>(`/api/projects/${encodeURIComponent(projectId)}/diagrams/sequence?${query}`)
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

  /** Fetch current user profile; requires a valid token. */
  async me(): Promise<User> {
    const res = await http.get<{ success: boolean; data: User }>('/api/auth/me')
    return res.data.data
  },
}
