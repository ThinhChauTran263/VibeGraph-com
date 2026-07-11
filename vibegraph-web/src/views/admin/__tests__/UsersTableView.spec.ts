import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UsersTableView from '../UsersTableView.vue'

describe('Admin UsersTableView', () => {
  it('renders users list correctly', async () => {
    const wrapper = mount(UsersTableView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              admin: {
                users: [
                  { id: 'usr-1', email: 'alice@example.com', displayName: 'Alice', role: 'user', status: 'active' },
                  { id: 'usr-2', email: 'bob@example.com', displayName: 'Bob', role: 'admin', status: 'blocked', safeReason: 'Spam' }
                ]
              }
            }
          })
        ]
      }
    })

    await flushPromises()
    expect(wrapper.text()).toContain('alice@example.com')
    expect(wrapper.text()).toContain('Bob')
    expect(wrapper.text()).toContain('blocked')
  })
})
