import { API_BASE_URL } from './constants'

const REFRESH_PATH = '/api/auth/refresh'
const LOGIN_PATH = '/api/auth/login'
const REGISTER_PATH = '/api/auth/register'
const LOGOUT_PATH = '/api/auth/logout'

let refreshPromise: Promise<boolean> | null = null

/** Refresh the browser session once; concurrent 401 responses share this request. */
export function refreshBrowserSession(): Promise<boolean> {
  if (refreshPromise) return refreshPromise

  refreshPromise = fetch(`${API_BASE_URL}${REFRESH_PATH}`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'X-VibeGraph-Client': 'web' },
  })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

/** Fetch with one refresh-and-retry cycle for protected browser requests. */
export async function fetchWithSessionRefresh(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  const response = await fetch(input, init)
  if (response.status !== 401 || isAuthLifecyclePath(input)) {
    return response
  }
  if (!(await refreshBrowserSession())) {
    return response
  }
  return fetch(input, init)
}

/** Clear only non-sensitive client hints; auth cookies are cleared by the backend. */
export function clearStoredSession(): void {
  if (typeof localStorage === 'undefined') return
  localStorage.removeItem('vg_token')
  localStorage.removeItem('vg_user')
}

/** Redirect the browser to login without assuming a router import. */
export function redirectToLogin(): void {
  if (typeof window === 'undefined') return
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

function isAuthLifecyclePath(input: RequestInfo | URL): boolean {
  const raw = input instanceof Request ? input.url : input.toString()
  let pathname: string
  try {
    pathname = new URL(raw, typeof window === 'undefined' ? 'http://localhost' : window.location.origin)
      .pathname
  } catch {
    pathname = raw
  }
  return [REFRESH_PATH, LOGIN_PATH, REGISTER_PATH, LOGOUT_PATH].includes(pathname)
}
