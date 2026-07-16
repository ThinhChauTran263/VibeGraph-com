/**
 * Axios HTTP client with auth interceptors.
 *
 * - Request interceptor: attaches `Authorization: Bearer <token>` from localStorage.
 * - Response interceptor: on 401, clears the session and redirects to /login.
 *
 * Other modules can import `http` for authenticated requests, or `httpRaw` for
 * requests that should NOT trigger the 401 redirect (e.g. the login call itself).
 */

import axios from 'axios'
import { API_BASE_URL } from './constants'

const TOKEN_KEY = 'vg_token'

/**
 * Main axios instance — every request going through this will get the Bearer
 * token attached (if present) and will redirect to /login on 401.
 */
export const http = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

// ─── Request interceptor: attach Bearer token ─────────────────────────────────

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ─── Response interceptor: handle 401 → logout + redirect ────────────────────

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      // Clear stored session
      localStorage.removeItem(TOKEN_KEY)
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
