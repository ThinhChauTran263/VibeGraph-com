import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createWebHistory } from 'vue-router'
import UserLayout from '../UserLayout.vue'
import i18n, { setLocale } from '@/language'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/dashboard', component: { template: '<div>Overview page</div>' } },
  { path: '/projects', component: { template: '<div>Repositories page</div>' } },
  { path: '/api-keys', component: { template: '<div>API Keys page</div>' } },
  { path: '/usage', component: { template: '<div>Usage page</div>' } },
  { path: '/subscription', component: { template: '<div>Subscription page</div>' } },
  { path: '/reports', name: 'reports', component: { template: '<div>Reports page</div>' } },
  { path: '/settings', component: { template: '<div>Settings page</div>' } },
  { path: '/login', name: 'login', component: { template: '<div>Login page</div>' } },
]

function makeRouter() {
  return createRouter({ history: createWebHistory(), routes })
}

interface MountLayoutOptions {
  sessionState?: Record<string, unknown> | null
  sessionStateError?: Error
}

async function mountLayout(options: MountLayoutOptions = {}) {
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
  const account = useAccountStore(pinia)
  if (options.sessionState !== undefined) account.sessionState = options.sessionState as typeof account.sessionState
  if (options.sessionStateError) vi.mocked(account.fetchSessionState).mockRejectedValueOnce(options.sessionStateError)
  const wrapper = mount(UserLayout, {
    attachTo: document.body,
    global: { plugins: [router, pinia, i18n] },
  })
  await flushPromises()
  return { wrapper, router, pinia }
}

describe('UserLayout', () => {
  beforeEach(() => {
    localStorage.clear()
    setLocale('en-US')
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
      'Notifications',
      'Reports',
      'Settings',
      'Sign Out',
    ]) {
      expect(navigation.text()).toContain(label)
    }
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

  it('shows the drawer close button only while the mobile drawer is open', async () => {
    const { wrapper } = await mountLayout()

    // On desktop the collapse toggle is the only header control; a close button here
    // would have nothing to close.
    expect(wrapper.find('button[aria-label="Close navigation"]').exists()).toBe(false)

    await wrapper.get('button[aria-label="Open navigation"]').trigger('click')
    await nextTick()
    expect(wrapper.find('button[aria-label="Close navigation"]').exists()).toBe(true)

    await wrapper.get('button[aria-label="Close navigation"]').trigger('click')
    await nextTick()
    expect(wrapper.find('button[aria-label="Close navigation"]').exists()).toBe(false)
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

  it('fails closed with a retry affordance while account access cannot be verified', async () => {
    const { wrapper } = await mountLayout({
      sessionState: null,
      sessionStateError: new Error('Session state unavailable'),
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('Cannot verify account access')
    expect(wrapper.findAll('button').some((button) => button.text().includes('Retry'))).toBe(true)
    expect(wrapper.text()).not.toContain('Overview page')
  })

  it('keeps support reports usable and explains disabled routes for restricted accounts', async () => {
    const { wrapper, router } = await mountLayout({
      sessionState: {
        id: 'user-1',
        email: 'user@vibegraph.io',
        displayName: 'User One',
        role: 'USER',
        accountStatus: 'BLOCKED',
        safeReason: 'Contact support to restore access.',
      },
    })
    await nextTick()

    expect(wrapper.get('a[aria-label="Reports"]').attributes('href')).toBe('/reports')
    const disabledRepositories = wrapper.get('[aria-label="Repositories unavailable"]')
    expect(disabledRepositories.attributes('aria-disabled')).toBe('true')
    expect(disabledRepositories.attributes('title')).toBe('Contact support to restore access.')
    expect(wrapper.text()).toContain('Contact support')

    await router.push('/reports')
    await flushPromises()
    expect(wrapper.text()).toContain('Reports page')
  })
})
