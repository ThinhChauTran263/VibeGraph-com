import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ProfileView from '../ProfileView.vue'
import { useAccountStore } from '@/stores/account'

describe('ProfileView', () => {
  it('renders profile information correctly', async () => {
    const wrapper = mount(ProfileView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                profile: {
                  email: 'test@example.com',
                  displayName: 'John Doe',
                  role: 'admin',
                  status: 'active'
                }
              }
            }
          })
        ]
      }
    })

    await flushPromises()
    expect(wrapper.text()).toContain('test@example.com')
    expect(wrapper.text()).toContain('admin')
    const input = wrapper.find('input[type="text"]')
    expect((input.element as HTMLInputElement).value).toBe('John Doe')
  })

  it('calls updateDisplayName when form is submitted', async () => {
    const wrapper = mount(ProfileView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                profile: {
                  email: 'test@example.com',
                  displayName: 'John Doe',
                  role: 'admin',
                  status: 'active'
                }
              }
            }
          })
        ]
      }
    })

    const store = useAccountStore()
    const input = wrapper.find('input[type="text"]')
    await input.setValue('Jane Doe')
    await wrapper.find('form').trigger('submit')

    expect(store.updateDisplayName).toHaveBeenCalledWith('Jane Doe')
  })
})
