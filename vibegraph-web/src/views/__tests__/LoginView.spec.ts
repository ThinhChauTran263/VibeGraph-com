import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../LoginView.vue'
import { useAuthStore } from '@/stores/auth'
import i18n, { setLocale } from '@/language'

async function mountLogin(path = '/login') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: LoginView },
      { path: '/register', name: 'register', component: { template: '<div />' } },
      { path: '/dashboard', component: { template: '<div />' } },
      { path: '/admin', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const pinia = createTestingPinia({ createSpy: vi.fn })
  const wrapper = mount(LoginView, { global: { plugins: [router, pinia, i18n] } })
  return { wrapper, router, pinia }
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setLocale('en-US')
  })

  it('shows the VibeGraph logo and name in the top-left header', async () => {
    const { wrapper } = await mountLogin()

    const header = wrapper.get('.auth-page__header')
    expect(header.get('img[alt="VibeGraph logo"]').element).toBeInstanceOf(HTMLImageElement)
    expect(header.text()).toContain('VibeGraph')
  })

  it('renders Google and GitHub OAuth login links', async () => {
    const { wrapper } = await mountLogin()

    const google = wrapper.get('a[href="http://localhost:8080/oauth2/authorization/google"]')
    const github = wrapper.get('a[href="http://localhost:8080/oauth2/authorization/github"]')

    expect(google.text()).toContain('Google')
    expect(github.text()).toContain('GitHub')
    expect(google.find('img.oauth-button__icon').exists()).toBe(true)
    expect(github.find('img.oauth-button__icon').exists()).toBe(true)
    expect(wrapper.find('.oauth-button__badge').exists()).toBe(false)
  })

  it('validates required credentials before calling login', async () => {
    const { wrapper, pinia } = await mountLogin()
    const auth = useAuthStore(pinia)

    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toContain('Please enter email and password.')
    expect(auth.login).not.toHaveBeenCalled()
  })

  it('shows a safe OAuth error message returned by the backend', async () => {
    const { wrapper } = await mountLogin('/login?error=oauth_email_unavailable')

    expect(wrapper.get('[role="alert"]').text()).toContain('did not share an email address')
  })

  it('trims credentials and redirects a normal user to the dashboard', async () => {
    const { wrapper, router, pinia } = await mountLogin()
    const auth = useAuthStore(pinia)
    vi.mocked(auth.login).mockImplementation(async () => {
      auth.user = {
        id: 'user-1',
        email: 'user@example.com',
        displayName: 'User',
        role: 'USER',
      }
    })

    await wrapper.get('#login-email').setValue('  user@example.com  ')
    await wrapper.get('#login-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(auth.login).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'password123',
    })
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })
})
