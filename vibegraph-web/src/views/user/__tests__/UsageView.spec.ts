import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UsageView from '../UsageView.vue'

describe('UsageView', () => {
  it('renders plan, storage quota, credit balance, and recent ledger entries', async () => {
    const wrapper = mount(UsageView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                usage: {
                  usedBytes: 250 * 1024 * 1024,
                  limitBytes: 1000 * 1024 * 1024,
                  remainingBytes: 750 * 1024 * 1024,
                  planCode: 'PRO',
                  planName: 'Pro Tier',
                  quotaOverrideBytes: null,
                  sourceStorageUsed: 250,
                  sourceStorageLimit: 1000,
                  creditsUsed: 120,
                  creditsLimit: 500,
                },
                creditLedger: [
                  {
                    id: 'ledger-1',
                    source: 'CLI',
                    operationCode: 'CLI_PUSH',
                    creditsDelta: -2,
                    projectId: 'project-1',
                    createdAt: '2026-07-14T12:00:00Z',
                  },
                ],
              },
            },
          }),
        ],
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('Pro Tier')
    expect(wrapper.text()).toContain('250MB / 1000MB used')
    expect(wrapper.text()).toContain('750MB remaining')
    expect(wrapper.text()).toContain('380 credits')
    expect(wrapper.text()).toContain('120 / 500 credits used this cycle')
    expect(wrapper.text()).toContain('Cli Push')
    expect(wrapper.text()).toContain('-2 credits')
    expect(wrapper.text()).not.toContain('Credit ledger history is not available')
  })

  it('renders an empty state when there is no credit activity', async () => {
    const wrapper = mount(UsageView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                usage: {
                  usedBytes: 0,
                  limitBytes: 100 * 1024 * 1024,
                  remainingBytes: 100 * 1024 * 1024,
                  planCode: 'FREE',
                  planName: 'Free',
                  quotaOverrideBytes: null,
                  sourceStorageUsed: 0,
                  sourceStorageLimit: 100,
                  creditsUsed: 0,
                  creditsLimit: 100,
                },
                creditLedger: [],
              },
            },
          }),
        ],
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('No credit activity yet.')
  })
})
