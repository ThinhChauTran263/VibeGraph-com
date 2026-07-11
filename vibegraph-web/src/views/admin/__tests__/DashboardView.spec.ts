import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import DashboardView from '../DashboardView.vue'

describe('Admin DashboardView', () => {
  it('renders overview metrics correctly', async () => {
    const wrapper = mount(DashboardView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              admin: {
                overview: {
                  totalUsers: 1500,
                  onlineUsers: 42,
                  totalProjects: 350
                }
              }
            }
          })
        ]
      }
    })

    await flushPromises()
    expect(wrapper.text()).toContain('1500') // totalUsers
    expect(wrapper.text()).toContain('42')   // onlineUsers
    expect(wrapper.text()).toContain('350')  // totalProjects
  })
})
