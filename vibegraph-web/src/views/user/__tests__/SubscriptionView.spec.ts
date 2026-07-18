import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import SubscriptionView from '../SubscriptionView.vue'
import { useAccountStore } from '@/stores/account'

function mountEmptySubscription(usageError?: Error) {
  const pinia = createTestingPinia({ createSpy: vi.fn, initialState: { account: {} } })
  const store = useAccountStore(pinia)
  if (usageError) vi.mocked(store.fetchUsage).mockRejectedValueOnce(usageError)
  return { wrapper: mount(SubscriptionView, { global: { plugins: [pinia] } }), store }
}

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
    const { wrapper } = mountEmptySubscription()

    expect(wrapper.text()).toContain('Loading subscription data...')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('shows a retry action when account usage fails to load', async () => {
    const { wrapper, store } = mountEmptySubscription(new Error('Subscription unavailable'))

    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Subscription unavailable')
    vi.mocked(store.fetchUsage).mockResolvedValueOnce(undefined)
    await wrapper.get('[data-test="retry-subscription"]').trigger('click')
    await flushPromises()
    expect(store.fetchUsage).toHaveBeenCalledTimes(2)
  })
})
