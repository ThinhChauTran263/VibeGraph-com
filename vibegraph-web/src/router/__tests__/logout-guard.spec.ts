import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { User } from '@/types/auth'

vi.mock('@/lib/api', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    meOptional: vi.fn(),
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

vi.mock('@/views/GraphView.vue', () => ({
  default: { template: '<div />' },
}))

import router from '@/router'
import { authApi } from '@/lib/api'
import { useAuthStore } from '@/stores/auth'

const mockAuthApi = authApi as {
  login: ReturnType<typeof vi.fn>
  register: ReturnType<typeof vi.fn>
  logout: ReturnType<typeof vi.fn>
  me: ReturnType<typeof vi.fn>
  meOptional: ReturnType<typeof vi.fn>
}

const fakeUser: User = {
  id: 'u-1',
  email: 'dev@vibegraph.io',
  displayName: 'Developer',
  role: 'USER',
}

describe('router logout guard', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.resetAllMocks()
    await router.replace('/')
  })

  it('does not revalidate the session while logout is in progress', async () => {
    localStorage.setItem('vg_user', JSON.stringify(fakeUser))
    mockAuthApi.me.mockResolvedValue(fakeUser)
    let resolveLogout!: () => void
    mockAuthApi.logout.mockReturnValue(
      new Promise<void>((resolve) => {
        resolveLogout = resolve
      }),
    )

    const store = useAuthStore()
    await router.replace('/dashboard')
    mockAuthApi.me.mockClear()

    const logoutPromise = store.logout()

    await router.push('/login')

    expect(store.isLoggingOut).toBe(true)
    expect(mockAuthApi.me).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('login')

    resolveLogout()
    await logoutPromise
  })
})
