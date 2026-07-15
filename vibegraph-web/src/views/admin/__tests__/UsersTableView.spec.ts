import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UsersTableView from '../UsersTableView.vue'
import type { AdminUserResponse } from '@/types/api'

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
  })

  it('shows Create User button', async () => {
    const wrapper = mount(UsersTableView, {
      global: {
        stubs,
        plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { admin: { users: [] } } })],
      },
    })
    await flushPromises()
    expect(wrapper.find('button[aria-label="Create User"]').exists()).toBe(true)
  })

  it('renders search and filter controls', async () => {
    const wrapper = mount(UsersTableView, {
      global: {
        stubs,
        plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { admin: { users: [] } } })],
      },
    })
    await flushPromises()
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.findAll('select').length).toBeGreaterThanOrEqual(2)
  })
})
