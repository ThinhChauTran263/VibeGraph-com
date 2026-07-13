import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UsageView from '../UsageView.vue'

describe('UsageView', () => {
  it('renders usage information correctly', async () => {
    const wrapper = mount(UsageView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                usage: {
                  planId: 'pro',
                  planName: 'Pro Tier',
                  sourceStorageUsed: 250,
                  sourceStorageLimit: 1000,
                  creditsUsed: 10,
                  creditsLimit: 50,
                  apiKeyLimit: 5,
                  apiKeysDisabled: false
                }
              }
            }
          })
        ]
      }
    })

    await flushPromises()
    expect(wrapper.text()).toContain('Pro Tier')
    // Check if QuotaMeter receives the right props or renders the text
    expect(wrapper.text()).toContain('250MB / 1000MB used')
    expect(wrapper.text()).toContain('750MB remaining')
  })
})
