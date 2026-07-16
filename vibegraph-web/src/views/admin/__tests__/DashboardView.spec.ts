import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import DashboardView from '../DashboardView.vue'

vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    props: ['option', 'autoresize'],
    template: '<div class="echart-mock" data-test="echart"></div>',
  },
}))

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
                  totalProjects: 350,
                  totalReports: 0,
                  openReports: 0,
                  blockedUsers: 2,
                  timestamp: null,
                },
              },
            },
          }),
        ],
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('1,500')
    expect(wrapper.text()).toContain('42')
    expect(wrapper.text()).toContain('350')
    expect(wrapper.text()).toContain('Platform Analytics')
    expect(wrapper.text()).toContain('Total Users')
    expect(wrapper.text()).toContain('Online Users')
    expect(wrapper.findAll('[data-test="echart"]').length).toBeGreaterThan(0)
  })
})
