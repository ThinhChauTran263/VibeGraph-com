import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UserLayout from '../UserLayout.vue'
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/dashboard', component: { template: '<div>Overview page</div>' } },
  { path: '/projects', component: { template: '<div>Repositories page</div>' } },
  { path: '/api-keys', component: { template: '<div>API Keys page</div>' } },
  { path: '/usage', component: { template: '<div>Usage page</div>' } },
  { path: '/subscription', component: { template: '<div>Subscription page</div>' } },
  { path: '/reports', component: { template: '<div>Reports page</div>' } },
  { path: '/tutorial', component: { template: '<div>Tutorial page</div>' } },
  { path: '/settings', component: { template: '<div>Settings page</div>' } },
  { path: '/login', name: 'login', component: { template: '<div>Login page</div>' } },
]

function makeRouter() {
  return createRouter({
    history: createWebHistory(),
    routes,
  })
}

describe('UserLayout', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('renders the user sidebar links and router-view', async () => {
    const router = makeRouter()
    router.push('/dashboard')
    await router.isReady()

    const wrapper = mount(UserLayout, {
      global: {
        plugins: [
          router,
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              auth: { user: { email: 'user@vibegraph.io', displayName: 'User One' } },
              account: {
                profile: {
                  email: 'user@vibegraph.io',
                  displayName: 'User One',
                  role: 'USER',
                  status: 'active',
                },
                usage: { planName: 'Free', planCode: 'FREE' },
              },
            },
          }),
        ],
      },
    })

    expect(wrapper.find('aside[aria-label="User navigation"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Overview')
    expect(wrapper.text()).toContain('Repositories')
    expect(wrapper.text()).toContain('Subscription')
    expect(wrapper.text()).toContain('Sign Out')
    expect(wrapper.text()).toContain('Overview page')
  })

  it('persists collapsed sidebar state and keeps collapsed labels accessible', async () => {
    const router = makeRouter()
    router.push('/dashboard')
    await router.isReady()

    const wrapper = mount(UserLayout, {
      global: {
        plugins: [
          router,
          createTestingPinia({
            createSpy: vi.fn,
            initialState: { account: { usage: { planName: 'Free', planCode: 'FREE' } } },
          }),
        ],
      },
    })

    await wrapper.find('.sidebar__toggle').trigger('click')

    expect(localStorage.getItem('vg_user_sidebar_collapsed')).toBe('true')
    const repositoriesLink = wrapper.find('a[title="Repositories"]')
    expect(repositoriesLink.exists()).toBe(true)
    expect(repositoriesLink.attributes('aria-label')).toBe('Repositories')
  })
})
