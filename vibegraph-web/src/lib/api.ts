/**
 * HTTP API client for VibeGraph backend.
 *
 * TODO:
 * - Use axios or fetch
 * - Handle errors
 * - Type-safe responses
 * - Auth header (for SaaS phase)
 */

import { API_BASE_URL } from './constants'

export const api = {
  baseUrl: API_BASE_URL,

  async get<T>(path: string): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json() as Promise<T>
  },

  async post<T>(path: string, body: unknown): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json() as Promise<T>
  },

  // TODO: put, delete, with-auth wrapper
}

// Project endpoints
export const projectApi = {
  list: () => api.get('/api/projects'),
  create: (data: unknown) => api.post('/api/projects', data),
  // TODO: more endpoints
}

// Graph endpoints
export const graphApi = {
  getGraph: (projectId: string) => api.get(`/api/projects/${projectId}/graph`),
  getNeighbors: (projectId: string, nodeId: string, hops: number) =>
    api.get(`/api/projects/${projectId}/graph/neighbors/${nodeId}?hops=${hops}`),
  // TODO: more endpoints
}

// Diagram endpoints
export const diagramApi = {
  useCase: (projectId: string) => api.get(`/api/projects/${projectId}/diagrams/usecase`),
  classDiagram: (projectId: string, pkg?: string) =>
    api.get(`/api/projects/${projectId}/diagrams/class${pkg ? `?package=${pkg}` : ''}`),
  sequence: (projectId: string, entry: string) =>
    api.get(`/api/projects/${projectId}/diagrams/sequence?entry=${entry}`),
}
