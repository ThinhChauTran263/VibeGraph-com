import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import SubscriptionView from '../SubscriptionView.vue'

describe('SubscriptionView', () => {
  it('renders the real current plan without a hardcoded user plan catalog', async () => {
    const wrapper = mount(SubscriptionView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                usage: {
                  usedMb: 125,
                  limitMb: 500,
                  remainingMb: 375,
                  planCode: 'PRO',
                  planName: 'Pro',
                  creditsUsed: 200,
                  creditsLimit: 1000,
                  creditsRemaining: 800,
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
    expect(wrapper.text()).toContain('375 MB')
    expect(wrapper.text()).toContain('800 credits')
    expect(wrapper.text()).not.toContain('Pro Plus')
    expect(wrapper.text()).toContain('A public plan catalog is not available')
    expect(wrapper.text()).toContain('Enterprise contact sales')
    expect(wrapper.text()).toContain('remain unavailable')
  })

  it('shows an honest unavailable state while account usage is missing', async () => {
    const wrapper = mount(SubscriptionView, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { account: {} } })],
      },
    })

    expect(wrapper.text()).toContain('Unavailable')
    expect(wrapper.text()).not.toContain('NaN')
  })
})
