import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import type { AuthResponse, User } from '@/types/auth'

// Mock the authApi module (imported by the store via '@/lib/api')
vi.mock('@/lib/api', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number
    statusText: string
    constructor(status: number, statusText: string, message?: string) {
      super(message ?? `HTTP ${status}: ${statusText}`)
      this.status = status
      this.statusText = statusText
    }
  },
}))

// Get a reference to the mocked module for assertions / controlling return values
import { authApi } from '@/lib/api'
const mockAuthApi = authApi as {
  login: ReturnType<typeof vi.fn>
  register: ReturnType<typeof vi.fn>
  logout: ReturnType<typeof vi.fn>
  me: ReturnType<typeof vi.fn>
}

const fakeUser: User = {
  id: 'u-1',
  email: 'dev@vibegraph.io',
  displayName: 'Developer',
  role: 'USER',
}

const fakeAuthResponse: AuthResponse = {
  token: null,
  user: fakeUser,
}

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  // ─── Initial state ───────────────────────────────────────────────────────────

  describe('initial state', () => {
    it('starts unauthenticated when localStorage is empty', () => {
      const store = useAuthStore()
      expect(store.isAuthenticated).toBe(false)
      expect(store.token).toBeNull()
      expect(store.user).toBeNull()
      expect(store.userEmail).toBe('')
      expect(store.userDisplayName).toBe('')
    })

    it('restores user from localStorage without requiring readable token storage', () => {
      localStorage.setItem('vg_user', JSON.stringify(fakeUser))

      // Must create a new pinia and a new store instance AFTER setting localStorage
      setActivePinia(createPinia())
      const store = useAuthStore()

      expect(store.isAuthenticated).toBe(true)
      expect(store.token).toBeNull()
      expect(store.user).toEqual(fakeUser)
      expect(store.userEmail).toBe(fakeUser.email)
    })

    it('handles corrupted user JSON gracefully', () => {
      localStorage.setItem('vg_user', '{invalid-json')

      setActivePinia(createPinia())
      const store = useAuthStore()

      expect(store.token).toBeNull()
      expect(store.user).toBeNull()
    })
  })

  // ─── Login action ────────────────────────────────────────────────────────────

  describe('login', () => {
    it('stores only user profile on successful login', async () => {
      mockAuthApi.login.mockResolvedValue(fakeAuthResponse)
      const store = useAuthStore()

      await store.login({ email: 'dev@vibegraph.io', password: 'secret123' })

      expect(store.isAuthenticated).toBe(true)
      expect(store.token).toBeNull()
      expect(store.user).toEqual(fakeUser)
      expect(localStorage.getItem('vg_token')).toBeNull()
      expect(localStorage.getItem('vg_user')).toBe(JSON.stringify(fakeUser))
    })

    it('propagates errors from authApi.login', async () => {
      mockAuthApi.login.mockRejectedValue(new Error('Invalid credentials'))
      const store = useAuthStore()

      await expect(store.login({ email: 'x', password: 'y' })).rejects.toThrow(
        'Invalid credentials',
      )
      expect(store.isAuthenticated).toBe(false)
    })
  })

  // ─── Register action ─────────────────────────────────────────────────────────

  describe('register', () => {
    it('stores only user profile on successful registration', async () => {
      mockAuthApi.register.mockResolvedValue(fakeAuthResponse)
      const store = useAuthStore()

      await store.register({
        email: 'dev@vibegraph.io',
        password: 'secret123',
        displayName: 'Developer',
      })

      expect(store.isAuthenticated).toBe(true)
      expect(store.token).toBeNull()
      expect(store.user).toEqual(fakeUser)
      expect(localStorage.getItem('vg_token')).toBeNull()
    })

    it('propagates errors from authApi.register', async () => {
      mockAuthApi.register.mockRejectedValue(new Error('Email already exists'))
      const store = useAuthStore()

      await expect(
        store.register({ email: 'dup@x.com', password: 'p', displayName: 'D' }),
      ).rejects.toThrow('Email already exists')
      expect(store.isAuthenticated).toBe(false)
    })
  })

  // ─── Logout action ───────────────────────────────────────────────────────────

  describe('logout', () => {
    it('clears user profile and stale token storage', async () => {
      localStorage.setItem('vg_token', 'tk')
      localStorage.setItem('vg_user', JSON.stringify(fakeUser))
      mockAuthApi.logout.mockResolvedValue(undefined)
      setActivePinia(createPinia())
      const store = useAuthStore()

      expect(store.isAuthenticated).toBe(true)

      await store.logout()

      expect(store.isAuthenticated).toBe(false)
      expect(store.token).toBeNull()
      expect(store.user).toBeNull()
      expect(localStorage.getItem('vg_token')).toBeNull()
      expect(localStorage.getItem('vg_user')).toBeNull()
    })
  })

  // ─── fetchCurrentUser ────────────────────────────────────────────────────────

  describe('fetchCurrentUser', () => {
    it('updates user from /api/auth/me response', async () => {
      setActivePinia(createPinia())
      const store = useAuthStore()

      const updatedUser: User = { ...fakeUser, displayName: 'Updated Name' }
      mockAuthApi.me.mockResolvedValue(updatedUser)

      await store.fetchCurrentUser()

      expect(store.user).toEqual(updatedUser)
      expect(JSON.parse(localStorage.getItem('vg_user')!)).toEqual(updatedUser)
    })

    it('clears session if me() throws (e.g. expired token)', async () => {
      localStorage.setItem('vg_user', JSON.stringify(fakeUser))
      setActivePinia(createPinia())
      const store = useAuthStore()

      mockAuthApi.me.mockRejectedValue(new Error('401'))

      await store.fetchCurrentUser()

      expect(store.isAuthenticated).toBe(false)
      expect(store.token).toBeNull()
      expect(localStorage.getItem('vg_token')).toBeNull()
    })
  })
})
