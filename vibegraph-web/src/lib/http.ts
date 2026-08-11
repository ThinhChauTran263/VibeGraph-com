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
import { clearStoredSession, redirectToLogin, refreshBrowserSession } from './authRefresh'

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
  async (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const config = error.config as (typeof error.config & { _refreshAttempt?: boolean }) | undefined
      const path = config?.url ?? ''
      const lifecycleRequest = [
        '/api/auth/login',
        '/api/auth/register',
        '/api/auth/refresh',
        '/api/auth/logout',
      ].some((candidate) => path.includes(candidate))
      if (config && !config._refreshAttempt && !lifecycleRequest) {
        config._refreshAttempt = true
        if (await refreshBrowserSession()) {
          return http(config)
        }
      }
      clearStoredSession()
      redirectToLogin()
    }
    return Promise.reject(error)
  },
)

export default http
