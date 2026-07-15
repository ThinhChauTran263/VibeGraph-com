import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import SubscriptionView from '../SubscriptionView.vue'

describe('SubscriptionView', () => {
  it('renders current plan without a hardcoded plan catalog', async () => {
    const wrapper = mount(SubscriptionView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                usage: {
                  usedBytes: 0,
                  limitBytes: 500 * 1024 * 1024,
                  remainingBytes: 500 * 1024 * 1024,
                  planCode: 'PRO',
                  planName: 'Pro',
                  quotaOverrideBytes: null,
                },
              },
            },
          }),
        ],
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('Current plan')
    expect(wrapper.text()).toContain('Pro')
    expect(wrapper.text()).toContain('PRO')
    expect(wrapper.text()).toContain('500 MB')
    expect(wrapper.text()).not.toContain('Pro Plus')
    expect(wrapper.text()).not.toContain('Enterprise')
    expect(wrapper.text()).toContain('The user app does not expose a public plan catalog')
  })
})
