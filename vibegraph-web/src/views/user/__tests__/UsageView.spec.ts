import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UsageView from '../UsageView.vue'
import { useAccountStore } from '@/stores/account'
import i18n from '@/language'

interface MountUsageOptions {
  usageError?: Error
  ledgerError?: Error
}

function mountUsage(
  usage: Record<string, unknown> | null,
  creditLedger: Record<string, unknown>[] = [],
  options: MountUsageOptions = {},
) {
  const pinia = createTestingPinia({
    createSpy: vi.fn,
    initialState: { account: { usage, creditLedger } },
  })
  const store = useAccountStore(pinia)
  if (options.usageError) vi.mocked(store.fetchUsage).mockRejectedValueOnce(options.usageError)
  if (options.ledgerError) {
    vi.mocked(store.fetchCreditLedger).mockRejectedValueOnce(options.ledgerError)
  }
  return { wrapper: mount(UsageView, { global: { plugins: [pinia, i18n] } }), store }
}

describe('UsageView', () => {
  it('renders Phase 7 MB quota, credits, and recent ledger entries', async () => {
    const { wrapper } = mountUsage(
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
    expect(wrapper.text()).toContain('CLI push')
    expect(wrapper.text()).toContain('-2 credits')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('renders an empty ledger and loading state without fabricated numbers', async () => {
    const { wrapper: loaded } = mountUsage({
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

    const { wrapper: loading } = mountUsage(null)
    expect(loading.text()).toContain('Loading usage data...')
    expect(loading.text()).not.toContain('NaN')
  })

  it('shows a retry action when the usage request fails', async () => {
    const { wrapper, store } = mountUsage(null, [], {
      usageError: new Error('Usage unavailable'),
    })

    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Usage unavailable')
    const retry = wrapper.get('[data-test="retry-usage"]')
    expect(retry.attributes('disabled')).toBeUndefined()

    vi.mocked(store.fetchUsage).mockResolvedValueOnce(undefined)
    await retry.trigger('click')
    await flushPromises()
    expect(store.fetchUsage).toHaveBeenCalledTimes(2)
  })

  it('keeps usage visible when the credit ledger request fails and offers retry', async () => {
    const { wrapper, store } = mountUsage(
      {
        usedMb: 1,
        limitMb: 10,
        remainingMb: 9,
        planCode: 'FREE',
        planName: 'Free',
        creditsUsed: 1,
        creditsLimit: 10,
        creditsRemaining: 9,
      },
      [],
      { ledgerError: new Error('Ledger unavailable') },
    )

    await flushPromises()

    expect(wrapper.text()).toContain('Free')
    expect(wrapper.get('[role="alert"]').text()).toContain('Ledger unavailable')
    await wrapper.get('[data-test="retry-ledger"]').trigger('click')
    await flushPromises()
    expect(store.fetchCreditLedger).toHaveBeenCalledWith(10)
  })
})
