import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import AdminReportsView from '../AdminReportsView.vue'

describe('Admin AdminReportsView', () => {
  it('renders a list of reports', async () => {
    const wrapper = mount(AdminReportsView, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Admin Reports Management')
  })
})
