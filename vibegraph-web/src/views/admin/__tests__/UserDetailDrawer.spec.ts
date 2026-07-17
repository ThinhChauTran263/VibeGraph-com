import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UserDetailDrawer from '../UserDetailDrawer.vue'
import type { AdminUserResponse } from '@/types/api'

const stubs = {
  StatusChip: {
    template: '<span class="status-chip">{{ label }}</span>',
    props: ['status', 'label'],
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
    return mount(UserDetailDrawer, {
      global: {
        stubs,
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            stubActions: true,
          }),
        ],
      },
      props: {
        isOpen: true,
        user,
      },
    })
  }

  it('renders user information when open', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    expect(wrapper.text()).toContain('test@example.com')
    expect(wrapper.text()).toContain('Test User')
  })

  it('emits close event when close button clicked', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    await wrapper.find('.close-btn').trigger('click')
    expect(wrapper.emitted()).toHaveProperty('close')
  })

  it('validates quota override cannot be below currently used', async () => {
    const wrapper = mountDrawer(
      makeUser({ usedBytes: 100 * 1024 * 1024, quotaBytes: 500 * 1024 * 1024 }),
    )
    await flushPromises()

    // Set quota below used (100 MB used, try setting to 10 MB)
    const input = wrapper.find('#quotaLimit')
    await input.setValue('10')
    await wrapper.find('.storage-override-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Cannot set quota lower than currently used')
  })

  it('shows Block User button for active users', async () => {
    const wrapper = mountDrawer(makeUser({ blocked: false }))
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const blockBtn = buttons.find((b) => b.text().includes('Block User'))
    expect(blockBtn).toBeDefined()
  })

  it('shows Unblock User button for blocked users', async () => {
    const wrapper = mountDrawer(makeUser({ blocked: true, blockedReasonSafe: 'Spam' }))
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const unblockBtn = buttons.find((b) => b.text().includes('Unblock User'))
    expect(unblockBtn).toBeDefined()
  })

  it('shows API key creation toggle', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    expect(wrapper.text()).toContain('API Key Creation')
  })

  it('shows credits remaining in account state', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    expect(wrapper.text()).toContain('Credits remaining')
  })

  it('does not render when closed', () => {
    const wrapper = mount(UserDetailDrawer, {
      global: { stubs, plugins: [createTestingPinia({ createSpy: vi.fn })] },
      props: { isOpen: false, user: makeUser() },
    })
    expect(wrapper.find('.drawer-overlay').exists()).toBe(false)
  })
})
