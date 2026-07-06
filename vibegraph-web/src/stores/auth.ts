/**
 * Auth store — manages JWT token, user profile, and auth lifecycle.
 *
 * Persistence: token + user JSON are stored in localStorage so a page refresh
 * (F5) keeps the session alive until token expiration.
 *
 * The store exposes reactive state for the router guard, HeaderBar, and any
 * component that needs the current user context.
 */

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types/auth'
import { authApi } from '@/lib/api'

const TOKEN_KEY = 'vg_token'
const USER_KEY = 'vg_user'

export const useAuthStore = defineStore('auth', () => {
  // ─── State ───────────────────────────────────────────────────────────────────
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<User | null>(loadUser())

  // ─── Getters ─────────────────────────────────────────────────────────────────
  const isAuthenticated = computed(() => !!token.value)
  const userEmail = computed(() => user.value?.email ?? '')
  const userDisplayName = computed(() => user.value?.displayName ?? '')

  // ─── Actions ─────────────────────────────────────────────────────────────────

  /** Register a new account → auto-login on success. */
  async function register(request: RegisterRequest): Promise<void> {
    const response = await authApi.register(request)
    setSession(response)
  }

  /** Login with email + password. */
  async function login(request: LoginRequest): Promise<void> {
    const response = await authApi.login(request)
    setSession(response)
  }

  /** Clear local session and redirect handled by the caller (router guard or interceptor). */
  function logout(): void {
    clearSession()
  }

  /**
   * Re-validate token by calling `GET /api/auth/me`.
   * Useful on app boot to ensure the stored token is still valid.
   * Silently logs out if the server responds 401 (interceptor handles redirect).
   */
  async function fetchCurrentUser(): Promise<void> {
    try {
      const me = await authApi.me()
      user.value = me
      localStorage.setItem(USER_KEY, JSON.stringify(me))
    } catch {
      // Token expired or invalid — clear session
      clearSession()
    }
  }

  // ─── Internal helpers ────────────────────────────────────────────────────────

  function setSession(response: AuthResponse): void {
    token.value = response.token
    user.value = response.user
    localStorage.setItem(TOKEN_KEY, response.token)
    localStorage.setItem(USER_KEY, JSON.stringify(response.user))
  }

  function clearSession(): void {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  function loadUser(): User | null {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as User
    } catch {
      return null
    }
  }

  return {
    // state
    token,
    user,
    // getters
    isAuthenticated,
    userEmail,
    userDisplayName,
    // actions
    register,
    login,
    logout,
    fetchCurrentUser,
  }
})
