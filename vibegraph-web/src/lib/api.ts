/**
 * HTTP API client for VibeGraph backend.
 * All responses are wrapped in ApiResponse<T> = { success, data, error }.
 */

import { API_BASE_URL } from './constants'
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

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
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

  async get<T>(path: string): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`)
    return unwrap<T>(res)
  },

  async post<T>(path: string, body?: unknown): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
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
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      body: form,
    })
    return unwrap<T>(res)
  },

  async delete(path: string): Promise<void> {
    const res = await fetch(`${this.baseUrl}${path}`, { method: 'DELETE' })
    if (!res.ok) {
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
}

// Graph endpoints
export const graphApi = {
  getGraph: (projectId: string) => fetchFullGraph(projectId),
  getNeighbors: (projectId: string, nodeId: string, hops: number) =>
    api.get(`/api/projects/${projectId}/graph/neighbors?${new URLSearchParams({ nodeId, hops: String(hops) })}`),

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
}

export interface UseCaseResponse {
  projectId: string
  mermaid: string
}

export interface DiagramResponse {
  projectId: string
  diagramType: string
  mermaid: string
}

// Diagram endpoints
export const diagramApi = {
  useCase: (projectId: string) =>
    api.get<UseCaseResponse>(`/api/projects/${encodeURIComponent(projectId)}/diagrams/usecase`),
  classDiagram: (projectId: string, pkg?: string) => {
    const query = pkg ? `?${new URLSearchParams({ package: pkg })}` : ''
    return api.get<DiagramResponse>(`/api/projects/${encodeURIComponent(projectId)}/diagrams/class${query}`)
  },
  sequence: (projectId: string, entry: string) => {
    const query = new URLSearchParams({ entry })
    return api.get<DiagramResponse>(`/api/projects/${encodeURIComponent(projectId)}/diagrams/sequence?${query}`)
  },
}
