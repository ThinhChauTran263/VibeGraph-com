/**
 * HTTP API client for VibeGraph backend.
 * All responses are wrapped in ApiResponse<T> = { success, data, error }.
 */

import { API_BASE_URL } from './constants'
import type { GraphData } from '@/types/graph'

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

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new ApiError(res.status, res.statusText, body || undefined)
  }
  const json = (await res.json()) as ApiResponse<T>
  if (!json.success) {
    throw new ApiError(400, 'API Error', json.error?.message ?? 'Unknown error')
  }
  return json.data
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

  async delete(path: string): Promise<void> {
    const res = await fetch(`${this.baseUrl}${path}`, { method: 'DELETE' })
    if (!res.ok) {
      const body = await res.text().catch(() => '')
      throw new ApiError(res.status, res.statusText, body || undefined)
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
  list: () => api.get('/api/projects'),
  create: (data: unknown) => api.post('/api/projects', data),
}

// Graph endpoints
export const graphApi = {
  getGraph: (projectId: string) => fetchFullGraph(projectId),
  getNeighbors: (projectId: string, nodeId: string, hops: number) =>
    api.get(`/api/projects/${projectId}/graph/neighbors/${nodeId}?hops=${hops}`),
}

// Diagram endpoints
export const diagramApi = {
  useCase: (projectId: string) => api.get(`/api/projects/${projectId}/diagrams/usecase`),
  classDiagram: (projectId: string, pkg?: string) =>
    api.get(`/api/projects/${projectId}/diagrams/class${pkg ? `?package=${pkg}` : ''}`),
  sequence: (projectId: string, entry: string) =>
    api.get(`/api/projects/${projectId}/diagrams/sequence?entry=${entry}`),
}
