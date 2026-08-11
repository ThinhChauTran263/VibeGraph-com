import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ApiKeysView from '../ApiKeysView.vue'
import { useAccountStore } from '@/stores/account'
import i18n from '@/language'

const featureMocks = vi.hoisted(() => ({
  enabled: true,
  reason: null as string | null,
}))

vi.mock('@/lib/featureAvailability', async () => {
  const { computed, ref } = await import('vue')
  return {
    featureAvailabilityContract: ref(true),
    refreshFeatureAvailability: vi.fn().mockResolvedValue(undefined),
    useFeatureAvailability: (key: string) =>
      computed(() => ({
        key,
        enabled: featureMocks.enabled,
        reason: featureMocks.reason,
      })),
  }
})

const dialogStub = {
  props: ['open', 'title'],
  emits: ['confirm', 'cancel'],
  template:
    '<button v-if="open" :data-test="title === \'Delete API key\' ? \'confirm-delete\' : \'confirm-disable\'" @click="$emit(\'confirm\')">Confirm {{ title }}</button>',
}

function mountView(projectsError?: Error, loaded = false) {
  const pinia = createTestingPinia({
    createSpy: vi.fn,
    initialState: {
      account: {
        projectsLoaded: loaded,
        apiKeysLoaded: loaded,
        projects: [
          { id: 'project-1', name: 'VibeGraph Web' },
          { id: 'project-2', name: 'Fresh Project' },
        ],
        apiKeys: [
          {
            id: 'key-1',
            keyPrefix: 'vbg_live_12',
            name: 'Production CLI',
            project: {
              id: 'project-1',
              name: 'VibeGraph Web',
              sourceType: 'GITHUB',
              status: 'READY',
            },
            createdAt: '2026-07-17T10:00:00Z',
            lastUsedAt: null,
            expiresAt: null,
            disabledAt: null,
            disabled: false,
          },
          {
            id: 'key-2',
            keyPrefix: 'vbg_old_34',
            name: 'Legacy unbound key',
            project: null,
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
  if (projectsError) {
    vi.mocked(useAccountStore(pinia).fetchProjects).mockRejectedValueOnce(projectsError)
  }
  return {
    wrapper: mount(ApiKeysView, {
      global: { plugins: [pinia, i18n], stubs: { AdminConfirmDialog: dialogStub } },
    }),
    pinia,
  }
}

describe('ApiKeysView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    featureMocks.enabled = true
    featureMocks.reason = null
  })

  it('renders repository bindings without exposing a list secret', async () => {
    const { wrapper } = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Production CLI')
    expect(wrapper.text()).toContain('vbg_live_12')
    expect(wrapper.text()).toContain('Repository: VibeGraph Web')
    expect(wrapper.text()).toContain('No repository binding')
    expect(wrapper.text()).not.toContain('secretKey')
  })

  it('refreshes keys while reusing cached repositories when returning to the page', async () => {
    const { wrapper, pinia } = mountView(undefined, true)
    const store = useAccountStore(pinia)
    await flushPromises()

    expect(store.fetchApiKeys).toHaveBeenCalledWith({ force: true })
    expect(store.fetchProjects).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Production CLI')
    expect(wrapper.text()).toContain('Repository: VibeGraph Web')
  })

  it('selects a repository before creating a project-bound key', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    vi.mocked(store.createApiKey).mockResolvedValueOnce({
      id: 'key-new',
      keyPrefix: 'vbg_new_56',
      name: 'CI key',
      secretKey: 'vbg-secret',
      project: {
        id: 'project-2',
        name: 'Fresh Project',
        sourceType: 'GITHUB',
        status: 'READY',
      },
      createdAt: '2026-07-18T10:00:00Z',
      expiresAt: null,
    })
    await flushPromises()

    await wrapper.get('button[data-test="create-api-key"]').trigger('click')
    await wrapper.get('#key-name').setValue('  CI key  ')
    expect(wrapper.get('form button[type="submit"]').attributes()).toHaveProperty('disabled')
    await wrapper.get('#key-project').setValue('project-2')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(store.createApiKey).toHaveBeenCalledWith('CI key', 'project-2')
    expect(wrapper.text()).toContain('vbg-secret')
  })

  it('fails closed when repository availability cannot be refreshed', async () => {
    const { wrapper, pinia } = mountView(new Error('Repositories unavailable'))
    const store = useAccountStore(pinia)
    await flushPromises()

    const createButton = wrapper.get('button[data-test="create-api-key"]')
    expect(createButton.attributes()).toHaveProperty('disabled')
    expect(wrapper.get('#key-disabled').text()).toContain('Repositories unavailable')
    expect(store.projects).toHaveLength(2)
    await createButton.trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('fails closed when API key capability is unavailable', async () => {
    featureMocks.enabled = false
    featureMocks.reason = 'API key creation is disabled by an administrator.'
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    await flushPromises()

    const createButton = wrapper.get('button[data-test="create-api-key"]')
    expect(createButton.attributes()).toHaveProperty('disabled')
    expect(wrapper.get('#key-disabled').text()).toContain('disabled by an administrator')
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

  it('blocks creation until the existing project key is deleted', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    await flushPromises()

    await wrapper.get('button[data-test="create-api-key"]').trigger('click')
    await wrapper.get('#key-name').setValue('Replacement')
    await wrapper.get('#key-project').setValue('project-1')

    expect(wrapper.get('[data-test="duplicate-project-reason"]').text()).toContain(
      'Delete the existing key',
    )
    expect(wrapper.get('form button[type="submit"]').attributes()).toHaveProperty('disabled')
    expect(store.createApiKey).not.toHaveBeenCalled()
  })

  it('deletes a key through a custom confirmation dialog', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    await flushPromises()

    await wrapper.get('button[data-test="delete-key-key-1"]').trigger('click')
    await wrapper.get('[data-test="confirm-delete"]').trigger('click')

    expect(store.deleteApiKey).toHaveBeenCalledWith('key-1')
  })

  it('lets users enable their own disabled API key', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    await flushPromises()

    await wrapper.get('button[data-test="enable-key-key-2"]').trigger('click')
    await flushPromises()

    expect(store.enableApiKey).toHaveBeenCalledWith('key-2')
  })

  it('prevents deletion and replacement of an admin-locked key', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    store.apiKeys[0] = {
      ...store.apiKeys[0]!,
      disabled: true,
      disabledAt: '2026-07-17T09:00:00Z',
      disabledBy: 'ADMIN',
      locked: true,
      lockedBy: 'admin@example.com',
    }
    await flushPromises()

    expect(wrapper.text()).toContain('Admin locked')
    expect(wrapper.get('button[data-test="delete-key-key-1"]').attributes()).toHaveProperty(
      'disabled',
    )
    expect(wrapper.find('button[data-test="enable-key-key-1"]').exists()).toBe(false)
    await wrapper.get('button[data-test="create-api-key"]').trigger('click')
    await wrapper.get('#key-name').setValue('Replacement')
    await wrapper.get('#key-project').setValue('project-1')
    expect(wrapper.get('[data-test="duplicate-project-reason"]').text()).toContain(
      'admin-locked key',
    )
    expect(store.deleteApiKey).not.toHaveBeenCalled()
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
