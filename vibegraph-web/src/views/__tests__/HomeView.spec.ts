import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import HomeView from '../HomeView.vue'

const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

function mountHome() {
  return mount(HomeView, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: {
            account: {
              profile: {
                id: 'user-1',
                email: 'user@example.com',
                displayName: 'User One',
                role: 'USER',
                status: 'active',
              },
              projects: [
                { id: 'project-1', name: 'One' },
                { id: 'project-2', name: 'Two' },
              ],
              usage: {
                planCode: 'PRO',
                planName: 'Pro',
                creditsUsed: 120,
                creditsLimit: 1000,
                creditsRemaining: 880,
              },
            },
          },
        }),
      ],
    },
  })
}

describe('HomeView', () => {
  it('renders repository count, remaining credits, and real plan data', async () => {
    const wrapper = mountHome()
    await flushPromises()

    expect(wrapper.text()).toContain('Welcome back, User One')
    expect(wrapper.get('section[aria-label="Workspace summary"]').text()).toContain('2')
    expect(wrapper.text()).toContain('880')
    expect(wrapper.text()).toContain('Pro')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('navigates the three required quick actions', async () => {
    push.mockReset()
    const wrapper = mountHome()

    await wrapper.get('button[data-test="quick-repositories"]').trigger('click')
    await wrapper.get('button[data-test="quick-api-keys"]').trigger('click')
    await wrapper.get('button[data-test="quick-reports"]').trigger('click')

    expect(push).toHaveBeenNthCalledWith(1, { name: 'projects', query: { import: 'new' } })
    expect(push).toHaveBeenNthCalledWith(2, { name: 'api-keys' })
    expect(push).toHaveBeenNthCalledWith(3, { name: 'reports' })
  })
})
