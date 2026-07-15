import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ProfileView from '../ProfileView.vue'
import { useAccountStore } from '@/stores/account'

describe('ProfileView', () => {
  it('renders settings account information and password fields', async () => {
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
                  status: 'active',
                },
              },
            },
          }),
        ],
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('Settings')
    expect(wrapper.text()).toContain('test@example.com')
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.find('#current-password').exists()).toBe(true)
    expect(wrapper.find('#new-password').exists()).toBe(true)
    expect(wrapper.find('#confirm-new-password').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('OTP')
    expect(wrapper.text()).not.toContain('Send code')
  })

  it('calls updateDisplayName when the account form is submitted', async () => {
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
                  status: 'active',
                },
              },
            },
          }),
        ],
      },
    })

    const store = useAccountStore()
    const input = wrapper.find('#displayName')
    await input.setValue('Jane Doe')
    await wrapper.find('form.update-form').trigger('submit')

    expect(store.updateDisplayName).toHaveBeenCalledWith('Jane Doe')
  })

  it('validates password confirmation without sending OTP UI', async () => {
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
                  role: 'USER',
                  status: 'active',
                },
              },
            },
          }),
        ],
      },
    })

    await wrapper.find('#current-password').setValue('old-password')
    await wrapper.find('#new-password').setValue('new-password-1')
    await wrapper.find('#confirm-new-password').setValue('new-password-2')
    await wrapper.find('form.password-form').trigger('submit')

    expect(wrapper.text()).toContain('New password and confirmation do not match')
  })

  it('submits password change with current, new, and confirmation passwords', async () => {
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
                  role: 'USER',
                  status: 'active',
                },
              },
            },
          }),
        ],
      },
    })

    const store = useAccountStore()
    await wrapper.find('#current-password').setValue('old-password')
    await wrapper.find('#new-password').setValue('new-password')
    await wrapper.find('#confirm-new-password').setValue('new-password')
    await wrapper.find('form.password-form').trigger('submit')

    expect(store.changePassword).toHaveBeenCalledWith(
      'old-password',
      'new-password',
      'new-password',
    )
  })
})
