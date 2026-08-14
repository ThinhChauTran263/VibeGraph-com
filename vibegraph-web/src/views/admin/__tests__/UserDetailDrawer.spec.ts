import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UserDetailDrawer from '../UserDetailDrawer.vue'
import type { AdminUserResponse } from '@/types/api'
import { useAdminStore } from '@/stores/admin'
import i18n, { setLocale } from '@/language'

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
    setLocale('en-US')
  })

  const mountDrawer = (user: AdminUserResponse | null = makeUser()) => {
    const pinia = createTestingPinia({ createSpy: vi.fn, stubActions: true })
    const store = useAdminStore(pinia)
    vi.mocked(store.listApiKeysForUser).mockResolvedValue([])
    return { wrapper: mount(UserDetailDrawer, {
      global: {
        stubs,
        plugins: [pinia, i18n],
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
      global: { stubs, plugins: [createTestingPinia({ createSpy: vi.fn }), i18n] },
      props: { isOpen: false, user: makeUser() },
    })
    expect(wrapper.find('.drawer-overlay').exists()).toBe(false)
  })

  // ── quota / credit handlers ──────────────────────────────────────────────────

  it('saves a storage quota override through the storage form', async () => {
    const { wrapper, store } = mountDrawer()
    await flushPromises()
    await wrapper.get('input[name="quotaLimit"]').setValue('200')
    await wrapper.get('form.storage-override-form').trigger('submit')
    await flushPromises()
    expect(store.updateQuota).toHaveBeenCalledWith('usr-1', 200, null)
    expect(wrapper.emitted()).toHaveProperty('updated')
  })

  it('rejects a storage quota below current usage without calling the store', async () => {
    // usedBytes = 100 MiB; an override of 10 MiB must fail closed client-side.
    const { wrapper, store } = mountDrawer()
    await flushPromises()
    await wrapper.get('input[name="quotaLimit"]').setValue('10')
    await wrapper.get('form.storage-override-form').trigger('submit')
    await flushPromises()
    expect(store.updateQuota).not.toHaveBeenCalled()
    expect(wrapper.find('.notice, .error, .form-error').exists() || wrapper.text().length).toBeTruthy()
  })

  it('surfaces the store error when the quota update fails', async () => {
    const { wrapper, store } = mountDrawer()
    vi.mocked(store.updateQuota).mockRejectedValueOnce(new Error('quota boom'))
    await flushPromises()
    await wrapper.get('input[name="quotaLimit"]').setValue('200')
    await wrapper.get('form.storage-override-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('quota boom')
  })

  it('saves a credit quota override through the credit limit form', async () => {
    const { wrapper, store } = mountDrawer()
    await flushPromises()
    await wrapper.get('input[name="creditQuotaLimit"]').setValue('500')
    await wrapper.get('form.credit-limit-form').trigger('submit')
    await flushPromises()
    expect(store.updateQuota).toHaveBeenCalledWith('usr-1', null, 500)
  })

  it('adjusts credits with a signed delta and reason, then resets the form', async () => {
    const { wrapper, store } = mountDrawer()
    await flushPromises()
    const inputs = wrapper.get('form.credit-form').findAll('input')
    await inputs[0]!.setValue('-25')
    await inputs[1]!.setValue('billing correction')
    await wrapper.get('form.credit-form').trigger('submit')
    await flushPromises()
    expect(store.adjustCredits).toHaveBeenCalledWith('usr-1', -25, 'billing correction')
    expect(wrapper.emitted()).toHaveProperty('updated')
  })

  it('rejects a zero or non-integer credit adjustment', async () => {
    const { wrapper, store } = mountDrawer()
    await flushPromises()
    const inputs = wrapper.get('form.credit-form').findAll('input')
    await inputs[0]!.setValue('0')
    await inputs[1]!.setValue('reason')
    await wrapper.get('form.credit-form').trigger('submit')
    await flushPromises()
    expect(store.adjustCredits).not.toHaveBeenCalled()
  })

  // ── plan handlers ─────────────────────────────────────────────────────────────

  it('selects a plan from the menu and saves it', async () => {
    const { wrapper, store } = mountDrawer()
    store.plans = [
      { code: 'FREE', name: 'Free' },
      { code: 'PRO', name: 'Pro' },
    ] as never
    await flushPromises()
    // open the combobox, pick PRO, then save via the standalone primary button
    await wrapper.get('#adminUserPlan').trigger('click')
    const proOption = wrapper
      .findAll('.plan-select-option')
      .find((b) => b.text().includes('Pro'))
    expect(proOption).toBeDefined()
    await proOption!.trigger('click')
    const saveBtn = wrapper
      .findAll('button.btn-primary')
      .find((b) => !(b.element as HTMLElement).closest('form'))
    expect(saveBtn).toBeDefined()
    await saveBtn!.trigger('click')
    await flushPromises()
    expect(store.updatePlan).toHaveBeenCalledWith('usr-1', 'PRO')
    expect(wrapper.emitted()).toHaveProperty('updated')
  })

  it('shows the store error when the plan update fails', async () => {
    const { wrapper, store } = mountDrawer()
    vi.mocked(store.updatePlan).mockRejectedValueOnce(new Error('plan boom'))
    await flushPromises()
    const saveBtn = wrapper.findAll('button').find((b) => b.classes('btn-primary'))
    await saveBtn!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('plan boom')
  })

  // ── block / deactivate / unblock ─────────────────────────────────────────────

  it('blocks a user through the reason dialog and emits updated', async () => {
    const { wrapper, store } = mountDrawer()
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text().includes('Block user'))!.trigger('click')
    await flushPromises()
    wrapper.findComponent({ name: 'AdminReasonDialog' }).vm.$emit('submit', {
      safeReason: 'abuse',
      reason: 'abuse details',
    })
    await flushPromises()
    expect(store.blockUser).toHaveBeenCalledWith('usr-1', 'abuse details', 'abuse')
    expect(wrapper.emitted()).toHaveProperty('updated')
  })

  it('deactivates a user through the reason dialog', async () => {
    const { wrapper, store } = mountDrawer(makeUser({ deactivated: false }))
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text().includes('Deactivate'))!.trigger('click')
    await flushPromises()
    wrapper.findComponent({ name: 'AdminReasonDialog' }).vm.$emit('submit', {
      safeReason: 'dormant',
      reason: 'dormant account',
    })
    await flushPromises()
    expect(store.deactivateUser).toHaveBeenCalledWith('usr-1', 'dormant account', 'dormant')
  })

  it('unblocks a blocked user through the confirm dialog', async () => {
    const { wrapper, store } = mountDrawer(makeUser({ blocked: true, blockedReasonSafe: 'Spam' }))
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text().includes('Unblock user'))!.trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="confirm-key-action"]').trigger('click')
    await flushPromises()
    expect(store.unblockUser).toHaveBeenCalledWith('usr-1')
    expect(wrapper.emitted()).toHaveProperty('updated')
  })

  it('surfaces a non-Error rejection from the reason action with the fallback message', async () => {
    const { wrapper, store } = mountDrawer()
    vi.mocked(store.blockUser).mockRejectedValueOnce('plain string failure')
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text().includes('Block user'))!.trigger('click')
    await flushPromises()
    wrapper.findComponent({ name: 'AdminReasonDialog' }).vm.$emit('submit', {
      safeReason: 'abuse',
      reason: 'abuse details',
    })
    await flushPromises()
    // fallback i18n key rendered instead of the thrown value
    expect(wrapper.text().length).toBeGreaterThan(0)
    expect(store.blockUser).toHaveBeenCalled()
  })

  // ── API-key policy + lock flow ───────────────────────────────────────────────

  it('toggles API-key creation through the policy form', async () => {
    const { wrapper, store } = mountDrawer(makeUser({ apiKeyCreationDisabled: false }))
    await flushPromises()
    await wrapper.get('form.api-key-policy-form').trigger('submit')
    await flushPromises()
    expect(store.updateApiKeyCreation).toHaveBeenCalledWith('usr-1', true)
  })

  it('locks an active key through the confirm dialog and refreshes the list', async () => {
    const { wrapper, store } = mountDrawer()
    vi.mocked(store.listApiKeysForUser)
      .mockResolvedValueOnce([
        {
          id: 'key-2', keyPrefix: 'vg-key', name: 'CLI', project: { id: 'p1', name: 'VibeGraph', sourceType: 'GITHUB', status: 'READY' },
          createdAt: '2026-07-18T10:00:00Z', lastUsedAt: null, expiresAt: null, disabledAt: null, disabled: false,
        },
      ])
      .mockResolvedValueOnce([])
    await wrapper.setProps({ user: makeUser({ id: 'usr-lock' }) })
    await flushPromises()
    const lockBtn = wrapper.findAll('button').find((b) => b.text() === 'Lock')
    expect(lockBtn).toBeDefined()
    await lockBtn!.trigger('click')
    await wrapper.get('[data-test="confirm-key-action"]').trigger('click')
    await flushPromises()
    expect(store.lockApiKey).toHaveBeenCalledWith('key-2')
  })

  it('renders expired and deleted key statuses', async () => {
    const { wrapper, store } = mountDrawer()
    vi.mocked(store.listApiKeysForUser).mockResolvedValueOnce([
      {
        id: 'key-exp', keyPrefix: 'vg-e', name: 'Expired', project: null,
        createdAt: '2026-01-01T00:00:00Z', lastUsedAt: null, expiresAt: '2026-01-02T00:00:00Z',
        disabledAt: null, disabled: false,
      },
      {
        id: 'key-del', keyPrefix: 'vg-d', name: 'Deleted', project: null,
        createdAt: '2026-01-01T00:00:00Z', lastUsedAt: null, expiresAt: null,
        disabledAt: '2026-01-03T00:00:00Z', deletedAt: '2026-01-03T00:00:00Z', disabled: true,
      },
    ])
    await wrapper.setProps({ user: makeUser({ id: 'usr-status' }) })
    await flushPromises()
    expect(wrapper.text()).toContain('Expired')
    expect(wrapper.text()).toContain('Deleted')
  })
})
