import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createWebHistory } from 'vue-router'
import CliAuthorizeView from '../CliAuthorizeView.vue'
import { useAuthStore } from '@/stores/auth'

const cliMocks = vi.hoisted(() => ({
  projects: vi.fn(),
  keys: vi.fn(),
  approve: vi.fn(),
}))

vi.mock('@/lib/cliAuthorization', () => ({ cliAuthorizationApi: cliMocks }))

async function mountAuthorization(signedIn = true) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/cli/authorize', name: 'cli-authorize', component: CliAuthorizeView },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
  await router.push('/cli/authorize?request=request-1')
  await router.isReady()
  sessionStorage.setItem('vibegraph.cli.authorization.request-1', 'browser-secret')
  const pinia = createTestingPinia({ createSpy: vi.fn })
  const auth = useAuthStore(pinia)
  auth.user = signedIn
    ? { id: 'user-1', email: 'user@example.com', displayName: 'VibeGraph User', role: 'USER' }
    : null
  const wrapper = mount(CliAuthorizeView, { global: { plugins: [router, pinia] } })
  await flushPromises()
  return { wrapper, router, auth }
}

describe('CliAuthorizeView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    cliMocks.projects.mockReset()
    cliMocks.keys.mockReset()
    cliMocks.approve.mockReset()
    cliMocks.projects.mockResolvedValue([{ id: 'project-1', name: 'Backend', sourceType: null, sizeBytes: 0, status: 'CREATED', createdAt: null, updatedAt: null, lastAnalyzedAt: null }])
    cliMocks.keys.mockResolvedValue([])
  })

  it('asks for account confirmation before loading project access', async () => {
    const { wrapper } = await mountAuthorization()

    expect(wrapper.text()).toContain('Approve sign in')
    expect(wrapper.text()).toContain('VibeGraph User')
    expect(wrapper.text()).toContain('Continue')
    expect(cliMocks.projects).not.toHaveBeenCalled()
  })

  it('sends signed-out users to login and preserves the CLI authorization route', async () => {
    const { router } = await mountAuthorization(false)

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/cli/authorize?request=request-1')
  })

  it('returns to project selection after the signed-in account is confirmed', async () => {
    const { wrapper } = await mountAuthorization()

    await wrapper.get('.cli-auth__confirm .cli-auth__approve').trigger('click')
    await flushPromises()

    expect(cliMocks.projects).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('Create key for project')
    expect(wrapper.text()).toContain('Backend')
  })

  it('shows a success state after the CLI is authorized', async () => {
    cliMocks.approve.mockResolvedValue({
      status: 'APPROVED',
      projectId: 'project-1',
      projectName: 'Backend',
      expiresAt: '2026-08-21T10:00:00Z',
    })
    const { wrapper } = await mountAuthorization()

    await wrapper.get('.cli-auth__confirm .cli-auth__approve').trigger('click')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Sign in successful')
    expect(wrapper.text()).toContain('Connected to Backend')
    expect(wrapper.text()).toContain('return to your terminal')
  })
})
