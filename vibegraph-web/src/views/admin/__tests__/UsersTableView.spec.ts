import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UsersTableView from '../UsersTableView.vue'
import { useAdminStore } from '@/stores/admin'
import type { AdminUserResponse } from '@/types/api'
import i18n, { setLocale } from '@/language'

// Stub child components to keep tests focused on UsersTableView logic
const stubs = {
  StatusChip: {
    template: '<span class="status-chip">{{ label }}</span>',
    props: ['status', 'label'],
  },
  UserDetailDrawer: { template: '<div />', props: ['isOpen', 'user'], emits: ['close', 'updated'] },
}

const makeUser = (overrides: Partial<AdminUserResponse> = {}): AdminUserResponse => ({
  id: 'usr-1',
  email: 'alice@example.com',
  displayName: 'Alice',
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
  usedBytes: 50 * 1024 * 1024,
  apiKeyCreationDisabled: false,
  ...overrides,
})

describe('Admin UsersTableView', () => {
  beforeEach(() => setLocale('en-US'))

  it('renders users list correctly', async () => {
    const wrapper = mount(UsersTableView, {
      global: {
        stubs,
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              admin: {
                users: [
                  makeUser({ id: 'usr-1', email: 'alice@example.com', displayName: 'Alice' }),
                  makeUser({
                    id: 'usr-2',
                    email: 'bob@example.com',
                    displayName: 'Bob',
                    blocked: true,
                    blockedReasonSafe: 'Spam',
                  }),
                ],
              },
            },
          }),
          i18n,
        ],
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('alice@example.com')
    expect(wrapper.text()).toContain('Bob')
    // Blocked user shows Unblock button
    const buttons = wrapper.findAll('button')
    const unblockBtn = buttons.find((b) => b.text() === 'Unblock')
    expect(unblockBtn).toBeDefined()
    expect(unblockBtn?.classes()).toContain('action-button')
    expect(wrapper.get('th.actions-cell').classes()).toContain('cell-center')
    expect(wrapper.findAll('.row-actions .action-button')).toHaveLength(4)
  })

  it('shows Create User button', async () => {
    const wrapper = mount(UsersTableView, {
      global: {
        stubs,
        plugins: [
          createTestingPinia({ createSpy: vi.fn, initialState: { admin: { users: [] } } }),
          i18n,
        ],
      },
    })
    await flushPromises()
    expect(wrapper.find('button[aria-label="Create User"]').exists()).toBe(true)
  })

  it('renders search and filter controls', async () => {
    const wrapper = mount(UsersTableView, {
      global: {
        stubs,
        plugins: [
          createTestingPinia({ createSpy: vi.fn, initialState: { admin: { users: [] } } }),
          i18n,
        ],
      },
    })
    await flushPromises()
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.get('#adminUserStatusFilter').attributes('aria-haspopup')).toBe('listbox')
    expect(wrapper.get('#adminUserPlanFilter').attributes('aria-haspopup')).toBe('listbox')
    expect(wrapper.get('#adminUserStatusFilter-listbox').findAll('[role="option"]')).toHaveLength(4)
    expect(wrapper.get('#adminUserPlanFilter-listbox').findAll('[role="option"]')).toHaveLength(1)
  })

  // Gõ tới đâu lọc tới đó: debounce ~300ms rồi mới bắn request tìm kiếm.
  it('live-filters users as the admin types (debounced)', async () => {
    const wrapper = mount(UsersTableView, {
      global: {
        stubs,
        plugins: [
          createTestingPinia({ createSpy: vi.fn, initialState: { admin: { users: [] } } }),
          i18n,
        ],
      },
    })
    await flushPromises()
    const adminStore = useAdminStore()
    const fetchSpy = adminStore.fetchUsers as unknown as ReturnType<typeof vi.fn>
    fetchSpy.mockClear()

    await wrapper.find('input[type="text"]').setValue('alice')
    // Chưa hết khoảng debounce → chưa bắn request
    expect(fetchSpy).not.toHaveBeenCalled()

    await new Promise((resolve) => setTimeout(resolve, 350))
    await flushPromises()
    expect(fetchSpy).toHaveBeenCalledWith(expect.objectContaining({ search: 'alice', page: 0 }))
  })
})
