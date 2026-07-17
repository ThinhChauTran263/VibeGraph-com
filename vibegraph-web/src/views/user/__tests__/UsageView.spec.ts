import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UsageView from '../UsageView.vue'

function mountUsage(usage: Record<string, unknown> | null, creditLedger: Record<string, unknown>[] = []) {
  return mount(UsageView, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: { account: { usage, creditLedger } },
        }),
      ],
    },
  })
}

describe('UsageView', () => {
  it('renders Phase 7 MB quota, credits, and recent ledger entries', async () => {
    const wrapper = mountUsage(
      {
        usedMb: 250,
        limitMb: 1000,
        remainingMb: 750,
        planCode: 'PRO',
        planName: 'Pro Tier',
        quotaOverrideMb: null,
        creditsUsed: 120,
        creditsLimit: 500,
        creditsRemaining: 380,
      },
      [
        {
          id: 'ledger-1',
          source: 'CLI',
          operationCode: 'CLI_PUSH',
          creditsDelta: -2,
          projectId: 'project-1',
          createdAt: '2026-07-14T12:00:00Z',
        },
      ],
    )

    await flushPromises()
    expect(wrapper.text()).toContain('Pro Tier')
    expect(wrapper.text()).toContain('250MB / 1000MB used')
    expect(wrapper.text()).toContain('750MB remaining')
    expect(wrapper.text()).toContain('380 credits')
    expect(wrapper.text()).toContain('120 / 500 credits used this cycle')
    expect(wrapper.text()).toContain('Cli Push')
    expect(wrapper.text()).toContain('-2 credits')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('renders an empty ledger and loading state without fabricated numbers', async () => {
    const loaded = mountUsage({
      usedMb: 0,
      limitMb: 100,
      remainingMb: 100,
      planCode: 'FREE',
      planName: 'Free',
      quotaOverrideMb: null,
      creditsUsed: 0,
      creditsLimit: 100,
      creditsRemaining: 100,
    })
    await flushPromises()
    expect(loaded.text()).toContain('No credit activity yet.')

    const loading = mountUsage(null)
    expect(loading.text()).toContain('Loading usage data...')
    expect(loading.text()).not.toContain('NaN')
  })
})
