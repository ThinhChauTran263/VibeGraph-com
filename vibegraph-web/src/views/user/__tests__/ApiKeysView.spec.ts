import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ApiKeysView from '../ApiKeysView.vue'
import { useAccountStore } from '@/stores/account'

vi.mock('@/lib/featureAvailability', async () => {
  const { computed, ref } = await import('vue')
  return {
    featureAvailabilityContract: ref(false),
    refreshFeatureAvailability: vi.fn().mockResolvedValue(undefined),
    useFeatureAvailability: (key: string) =>
      computed(() => ({
        key,
        enabled: false,
        reason: 'Repository-bound API key creation is not supported by the current backend contract.',
      })),
  }
})

const dialogStub = {
  props: ['open'],
  emits: ['confirm', 'cancel'],
  template: '<button v-if="open" data-test="confirm-disable" @click="$emit(\'confirm\')">Confirm disable</button>',
}

function mountView() {
  const pinia = createTestingPinia({
    createSpy: vi.fn,
    initialState: {
      account: {
        projects: [{ id: 'project-1', name: 'VibeGraph Web' }],
        apiKeys: [
          {
            id: 'key-1',
            keyPrefix: 'vbg_live_12',
            name: 'Production CLI',
            createdAt: '2026-07-17T10:00:00Z',
            lastUsedAt: null,
            expiresAt: null,
            disabledAt: null,
            disabled: false,
          },
          {
            id: 'key-2',
            keyPrefix: 'vbg_old_34',
            name: 'Disabled key',
            createdAt: '2026-07-16T10:00:00Z',
            lastUsedAt: null,
            expiresAt: null,
            disabledAt: '2026-07-17T09:00:00Z',
            disabled: true,
          },
        ],
      },
    },
  })
  return {
    wrapper: mount(ApiKeysView, {
      global: { plugins: [pinia], stubs: { AdminConfirmDialog: dialogStub } },
    }),
    pinia,
  }
}

describe('ApiKeysView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders key metadata without exposing a list secret or invented project binding', async () => {
    const { wrapper } = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Production CLI')
    expect(wrapper.text()).toContain('vbg_live_12')
    expect(wrapper.text()).toContain('Active')
    expect(wrapper.text()).toContain('Disabled')
    expect(wrapper.text()).toContain('No repository binding in the current API contract')
    expect(wrapper.text()).not.toContain('VibeGraph Web')
    expect(wrapper.text()).not.toContain('secretKey')
  })

  it('keeps project-bound key creation visibly disabled and non-interactive', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    await flushPromises()

    const createButton = wrapper.get('button[data-test="create-api-key"]')
    expect(createButton.attributes()).toHaveProperty('disabled')
    expect(wrapper.get('#key-disabled').text()).toContain(
      'Repository-bound API key creation is not supported by the current backend contract.',
    )
    await createButton.trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(store.createApiKey).not.toHaveBeenCalled()
  })

  it('disables an existing key through the confirmation dialog', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    await flushPromises()

    await wrapper.get('button[data-test="disable-key-key-1"]').trigger('click')
    await wrapper.get('[data-test="confirm-disable"]').trigger('click')

    expect(store.disableApiKey).toHaveBeenCalledWith('key-1')
  })

  it('keeps the disable dialog recoverable when the request fails', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    vi.mocked(store.disableApiKey).mockRejectedValueOnce(new Error('Could not disable this key.'))
    await flushPromises()

    await wrapper.get('button[data-test="disable-key-key-1"]').trigger('click')
    await wrapper.get('[data-test="confirm-disable"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="status"]').text()).toContain('Could not disable this key.')
    expect(wrapper.find('[data-test="confirm-disable"]').exists()).toBe(true)
  })
})
