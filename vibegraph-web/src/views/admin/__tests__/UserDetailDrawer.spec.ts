import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UserDetailDrawer from '../UserDetailDrawer.vue'
import type { AdminUserResponse } from '@/types/api'
import { useAdminStore } from '@/stores/admin'

const stubs = {
  StatusChip: {
    template: '<span class="status-chip">{{ label }}</span>',
    props: ['status', 'label'],
  },
  AdminReasonDialog: true,
  AdminConfirmDialog: {
    props: ['open'],
    emits: ['confirm'],
    template: '<button v-if="open" data-test="confirm-key-action" @click="$emit(\'confirm\')">Confirm</button>',
  },
}

const makeUser = (overrides: Partial<AdminUserResponse> = {}): AdminUserResponse => ({
  id: 'usr-1',
  email: 'test@example.com',
  displayName: 'Test User',
  role: 'USER',
  deactivated: false,
  deactivationReason: null,
  deactivationReasonSafe: null,
  blocked: false,
  blockedReason: null,
  blockedReasonSafe: null,
  planCode: 'FREE',
  storageQuotaOverrideBytes: null,
  creditQuotaOverride: null,
  quotaBytes: 500 * 1024 * 1024,
  usedBytes: 100 * 1024 * 1024,
  apiKeyCreationDisabled: false,
  ...overrides,
})

describe('Admin UserDetailDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const mountDrawer = (user: AdminUserResponse | null = makeUser()) => {
    const pinia = createTestingPinia({ createSpy: vi.fn, stubActions: true })
    const store = useAdminStore(pinia)
    vi.mocked(store.listApiKeysForUser).mockResolvedValue([])
    return { wrapper: mount(UserDetailDrawer, {
      global: {
        stubs,
        plugins: [pinia],
      },
      props: {
        isOpen: true,
        user,
      },
    }), store }
  }

  it('renders user information when open', async () => {
    const { wrapper } = mountDrawer()
    await flushPromises()
    expect(wrapper.text()).toContain('test@example.com')
    expect(wrapper.text()).toContain('Test User')
  })

  it('emits close event when close button clicked', async () => {
    const { wrapper } = mountDrawer()
    await flushPromises()
    await wrapper.find('.close-btn').trigger('click')
    expect(wrapper.emitted()).toHaveProperty('close')
  })

  it('shows Block User button for active users', async () => {
    const { wrapper } = mountDrawer(makeUser({ blocked: false }))
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const blockBtn = buttons.find((b) => b.text().includes('Block user'))
    expect(blockBtn).toBeDefined()
  })

  it('shows Unblock User button for blocked users', async () => {
    const { wrapper } = mountDrawer(makeUser({ blocked: true, blockedReasonSafe: 'Spam' }))
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const unblockBtn = buttons.find((b) => b.text().includes('Unblock user'))
    expect(unblockBtn).toBeDefined()
  })

  it('does not render API key creation UI', async () => {
    const { wrapper } = mountDrawer()
    await flushPromises()
    expect(wrapper.text()).toContain('API key metadata')
    expect(wrapper.text()).not.toContain('Create API key')
    expect(wrapper.find('input[name="apiKeyName"]').exists()).toBe(false)
  })

  it('lists key metadata and disables a specific key', async () => {
    const { wrapper, store } = mountDrawer()
    vi.mocked(store.listApiKeysForUser).mockResolvedValueOnce([
      {
        id: 'key-1', keyPrefix: 'vg-key', name: 'CLI', project: { id: 'project-1', name: 'VibeGraph', sourceType: 'GITHUB', status: 'READY' },
        createdAt: '2026-07-18T10:00:00Z', lastUsedAt: null, expiresAt: null, disabledAt: null, disabled: false,
      },
    ])
    await wrapper.setProps({ user: makeUser({ id: 'usr-2' }) })
    await flushPromises()
    expect(wrapper.text()).toContain('VibeGraph')
    await wrapper.get('.key-row button').trigger('click')
    await wrapper.get('[data-test="confirm-key-action"]').trigger('click')
    await flushPromises()
    expect(store.disableApiKey).toHaveBeenCalledWith('key-1')
  })

  it('unlocks an administrator-locked key', async () => {
    const { wrapper, store } = mountDrawer()
    vi.mocked(store.listApiKeysForUser).mockResolvedValueOnce([
      {
        id: 'key-locked', keyPrefix: 'vg-lock', name: 'Locked CLI', project: { id: 'project-1', name: 'VibeGraph', sourceType: 'GITHUB', status: 'READY' },
        createdAt: '2026-07-18T10:00:00Z', lastUsedAt: null, expiresAt: null,
        disabledAt: '2026-07-18T11:00:00Z', disabledBy: 'ADMIN', disabledReason: 'Disabled by administrator',
        lockedAt: '2026-07-18T11:00:00Z', lockedBy: 'admin@example.com', locked: true,
        deletedAt: null, canDelete: false, disabled: true,
      },
    ])
    await wrapper.setProps({ user: makeUser({ id: 'usr-locked' }) })
    await flushPromises()

    const unlock = wrapper.findAll('button').find((button) => button.text() === 'Unlock')
    expect(unlock).toBeDefined()
    await unlock!.trigger('click')
    await wrapper.get('[data-test="confirm-key-action"]').trigger('click')
    await flushPromises()

    expect(store.unlockApiKey).toHaveBeenCalledWith('key-locked')
  })

  it('does not render when closed', () => {
    const wrapper = mount(UserDetailDrawer, {
      global: { stubs, plugins: [createTestingPinia({ createSpy: vi.fn })] },
      props: { isOpen: false, user: makeUser() },
    })
    expect(wrapper.find('.drawer-overlay').exists()).toBe(false)
  })
})
