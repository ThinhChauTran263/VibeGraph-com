/**
 * Axios HTTP client with auth interceptors.
 *
 * - Request interceptor: marks browser requests; auth uses the HttpOnly cookie.
 * - Response interceptor: on 401, clears the session and redirects to /login.
 *
 * Other modules can import `http` for authenticated requests, or `httpRaw` for
 * requests that should NOT trigger the 401 redirect (e.g. the login call itself).
 */

import axios from 'axios'
import { API_BASE_URL } from './constants'

/**
 * Main axios instance — every request includes browser cookies and redirects to /login on 401.
 */
export const http = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

// ─── Request interceptor: mark browser client ─────────────────────────────────

http.interceptors.request.use((config) => {
  config.headers['X-VibeGraph-Client'] = 'web'
  return config
})

// ─── Response interceptor: handle 401 → logout + redirect ────────────────────

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      // Clear stored session
      localStorage.removeItem('vg_token')
      localStorage.removeItem('vg_user')

      // Redirect to login page. We access the router lazily to avoid circular
      // imports (router depends on stores which depend on api which depends on router).
      // Using window.location as a simple, framework-agnostic redirect.
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

export default http
