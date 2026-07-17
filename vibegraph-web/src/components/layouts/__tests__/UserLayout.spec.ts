import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createWebHistory } from 'vue-router'
import UserLayout from '../UserLayout.vue'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/dashboard', component: { template: '<div>Overview page</div>' } },
  { path: '/projects', component: { template: '<div>Repositories page</div>' } },
  { path: '/api-keys', component: { template: '<div>API Keys page</div>' } },
  { path: '/usage', component: { template: '<div>Usage page</div>' } },
  { path: '/subscription', component: { template: '<div>Subscription page</div>' } },
  { path: '/reports', component: { template: '<div>Reports page</div>' } },
  { path: '/settings', component: { template: '<div>Settings page</div>' } },
  { path: '/login', name: 'login', component: { template: '<div>Login page</div>' } },
]

function makeRouter() {
  return createRouter({ history: createWebHistory(), routes })
}

async function mountLayout() {
  const router = makeRouter()
  await router.push('/dashboard')
  await router.isReady()
  const pinia = createTestingPinia({
    createSpy: vi.fn,
    initialState: {
      auth: {
        user: {
          id: 'user-1',
          email: 'user@vibegraph.io',
          displayName: 'User One',
          role: 'USER',
        },
      },
      account: {
        profile: {
          id: 'user-1',
          email: 'user@vibegraph.io',
          displayName: 'User One',
          role: 'USER',
          status: 'active',
        },
        usage: {
          planName: 'Pro',
          planCode: 'PRO',
          creditsRemaining: 880,
        },
      },
    },
  })
  const wrapper = mount(UserLayout, { attachTo: document.body, global: { plugins: [router, pinia] } })
  await flushPromises()
  return { wrapper, router, pinia }
}

describe('UserLayout', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('900px'),
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
  })

  it('renders only the approved user navigation and account summary', async () => {
    const { wrapper } = await mountLayout()

    const navigation = wrapper.get('aside[aria-label="User navigation"]')
    for (const label of [
      'Overview',
      'Repositories',
      'API Keys',
      'Usage',
      'Subscription',
      'Reports',
      'Settings',
      'Sign Out',
    ]) {
      expect(navigation.text()).toContain(label)
    }
    expect(navigation.text()).not.toContain('Notification')
    expect(navigation.text()).not.toContain('Tutorial')
    expect(navigation.text()).toContain('User One')
    expect(navigation.text()).toContain('user@vibegraph.io')
    expect(navigation.text()).toContain('Pro')
    expect(navigation.text()).toContain('880 credits')
  })

  it('collapses to accessible icon-only navigation and expands with the hamburger', async () => {
    const { wrapper } = await mountLayout()

    const collapseButton = wrapper.get('button[aria-label="Collapse sidebar"]')
    expect(collapseButton.attributes('aria-expanded')).toBe('true')
    await collapseButton.trigger('click')

    expect(wrapper.classes()).toContain('collapsed')
    const repositoriesLink = wrapper.get('a[aria-label="Repositories"]')
    expect(repositoriesLink.attributes('title')).toBe('Repositories')
    const expandButton = wrapper.get('button[aria-label="Expand sidebar"]')
    expect(expandButton.attributes('aria-expanded')).toBe('false')
    expect(localStorage.getItem('vg_user_sidebar_collapsed')).toBeNull()

    await expandButton.trigger('click')
    expect(wrapper.classes()).not.toContain('collapsed')
  })

  it('opens and closes the mobile drawer independently', async () => {
    const { wrapper } = await mountLayout()
    const menuButton = wrapper.get('button[aria-label="Open navigation"]')

    await menuButton.trigger('click')
    await nextTick()

    const sidebar = wrapper.get('aside[aria-label="User navigation"]')
    expect(sidebar.classes()).toContain('open')
    expect(wrapper.get('button[aria-label="Open navigation"]').attributes('aria-expanded')).toBe('true')
    expect(document.activeElement).toBe(sidebar.get('button[aria-label="Close navigation"]').element)

    await sidebar.trigger('keydown', { key: 'Escape' })
    await nextTick()
    expect(sidebar.classes()).not.toContain('open')
    expect(document.activeElement).toBe(menuButton.element)
  })

  it('signs out through the auth store and redirects to login', async () => {
    const { wrapper, router, pinia } = await mountLayout()
    const auth = useAuthStore(pinia)

    await wrapper.get('button[data-test="user-sign-out"]').trigger('click')
    await flushPromises()

    expect(auth.logout).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('login')
  })
})
