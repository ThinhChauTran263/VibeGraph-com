import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ProfileView from '../ProfileView.vue'
import { useAccountStore } from '@/stores/account'
import i18n from '@/language'

interface MountViewOptions {
  withProfile?: boolean
  profileError?: Error
}

function mountView(options: MountViewOptions = {}) {
  const profile = options.withProfile === false
    ? null
    : {
        id: 'user-1',
        email: 'test@example.com',
        displayName: 'John Doe',
        role: 'USER',
        status: 'active',
      }
  const pinia = createTestingPinia({
    createSpy: vi.fn,
    initialState: { account: { profile } },
  })
  const store = useAccountStore(pinia)
  if (options.profileError) {
    vi.mocked(store.fetchProfile).mockRejectedValueOnce(options.profileError)
  }
  return { wrapper: mount(ProfileView, { global: { plugins: [pinia, i18n] } }), pinia }
}

describe('ProfileView', () => {
  it('renders account identity and password fields without OTP controls', async () => {
    const { wrapper } = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Settings')
    expect(wrapper.text()).toContain('test@example.com')
    expect(wrapper.get('#current-password').element).toBeInstanceOf(HTMLInputElement)
    expect(wrapper.get('#new-password').element).toBeInstanceOf(HTMLInputElement)
    expect(wrapper.get('#confirm-new-password').element).toBeInstanceOf(HTMLInputElement)
    expect(wrapper.text()).not.toContain('OTP')
    expect(wrapper.text()).not.toContain('Send code')
  })

  it('trims and submits a changed display name', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)

    await wrapper.get('#displayName').setValue('  Jane Doe  ')
    await wrapper.get('form.update-form').trigger('submit')

    expect(store.updateDisplayName).toHaveBeenCalledWith('Jane Doe')
  })

  it('shows a validation error instead of silently ignoring a blank display name', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)

    await wrapper.get('#displayName').setValue('   ')
    await wrapper.get('form.update-form').trigger('submit')

    expect(wrapper.get('[data-test="profile-message"]').attributes('role')).toBe('alert')
    expect(wrapper.get('[data-test="profile-message"]').text()).toContain('Display name is required')
    expect(wrapper.get('#displayName').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('#displayName').attributes('aria-describedby')).toBe('profile-message')
    expect(store.updateDisplayName).not.toHaveBeenCalled()
  })

  it('validates the backend minimum password length before calling the API', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)

    await wrapper.get('#current-password').setValue('old-password')
    await wrapper.get('#new-password').setValue('short')
    await wrapper.get('#confirm-new-password').setValue('short')
    await wrapper.get('form.password-form').trigger('submit')

    expect(wrapper.get('[data-test="password-message"]').attributes('role')).toBe('alert')
    expect(wrapper.get('[data-test="password-message"]').text()).toContain(
      'New password must be at least 8 characters',
    )
    expect(wrapper.get('#new-password').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('#new-password').attributes('aria-describedby')).toBe('password-message')
    expect(store.changePassword).not.toHaveBeenCalled()
  })

  it('validates password confirmation before calling the API', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)

    await wrapper.get('#current-password').setValue('old-password')
    await wrapper.get('#new-password').setValue('new-password-1')
    await wrapper.get('#confirm-new-password').setValue('new-password-2')
    await wrapper.get('form.password-form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'New password and confirmation do not match',
    )
    expect(store.changePassword).not.toHaveBeenCalled()
  })

  it('clears password fields only after a successful password change', async () => {
    const { wrapper, pinia } = mountView()
    const store = useAccountStore(pinia)
    vi.mocked(store.changePassword).mockResolvedValue(undefined)

    await wrapper.get('#current-password').setValue('old-password')
    await wrapper.get('#new-password').setValue('new-password')
    await wrapper.get('#confirm-new-password').setValue('new-password')
    await wrapper.get('form.password-form').trigger('submit')
    await flushPromises()

    expect(store.changePassword).toHaveBeenCalledWith(
      'old-password',
      'new-password',
      'new-password',
    )
    expect((wrapper.get('#current-password').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('#new-password').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('#confirm-new-password').element as HTMLInputElement).value).toBe('')
  })

  it('shows a retry action when the profile request fails', async () => {
    const { wrapper, pinia } = mountView({
      withProfile: false,
      profileError: new Error('Profile unavailable'),
    })
    const store = useAccountStore(pinia)

    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Profile unavailable')
    vi.mocked(store.fetchProfile).mockResolvedValueOnce(undefined)
    await wrapper.get('[data-test="retry-profile"]').trigger('click')
    await flushPromises()
    expect(store.fetchProfile).toHaveBeenCalledTimes(2)
  })
})
