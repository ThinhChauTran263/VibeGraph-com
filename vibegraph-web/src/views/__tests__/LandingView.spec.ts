import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import LandingView from '../LandingView.vue'
import { useAuthStore } from '@/stores/auth'
import i18n from '@/language'

// F-L1/F-L2: LandingView schedules self-rescheduling timers (terminal typing,
// impact propagation, tour loop) and four window listeners on mount. None of
// them may keep firing after unmount.
describe('LandingView lifecycle cleanup', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function mountLanding() {
    return mount(LandingView, {
      global: {
        plugins: [i18n],
        stubs: {
          RouterLink: { template: '<a :href="to"><slot /></a>', props: ['to'] },
          LanguageSelector: true,
        },
      },
    })
  }

  it('F-L1: terminal typing chain stops at unmount and never writes again', () => {
    const warn = vi.spyOn(console, 'error').mockImplementation(() => {})
    const auth = useAuthStore()
    vi.spyOn(auth, 'refreshPublicSession').mockResolvedValue()
    const wrapper = mountLanding()
    const vm = wrapper.vm as unknown as { terminalInput: string }

    // Let a few typing ticks run so the chain is demonstrably alive.
    vi.advanceTimersByTime(100)
    const inputAtUnmount = vm.terminalInput
    expect(inputAtUnmount.length).toBeGreaterThan(0)

    wrapper.unmount()
    vi.advanceTimersByTime(5000)

    expect(vm.terminalInput).toBe(inputAtUnmount)
    warn.mockRestore()
  })

  it('F-L2: all four tour listeners are removed on unmount', () => {
    const removeSpy = vi.spyOn(window, 'removeEventListener')
    const auth = useAuthStore()
    vi.spyOn(auth, 'refreshPublicSession').mockResolvedValue()
    const wrapper = mountLanding()

    wrapper.unmount()

    const removedTypes = removeSpy.mock.calls.map((call) => call[0])
    for (const type of ['scroll', 'mousemove', 'mousedown', 'keydown']) {
      expect(removedTypes).toContain(type)
    }
    removeSpy.mockRestore()
  })

  it('F-L2: dispatching a tour event after unmount does not run stopAutoTour work', () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'refreshPublicSession').mockResolvedValue()
    const wrapper = mountLanding()
    wrapper.unmount()

    // No listener may remain to handle this; if one did, jsdom would route it
    // into the unmounted component. Asserting zero listeners via a spy on the
    // handler path is impractical, so assert the dispatch itself is a no-op
    // that throws nothing and leaves no timers scheduled.
    expect(() => window.dispatchEvent(new Event('keydown'))).not.toThrow()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('shows login for anonymous visitors and links the public docs', () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'refreshPublicSession').mockResolvedValue()
    const wrapper = mountLanding()

    expect(wrapper.find('.lp-nav__actions .btn--primary').text()).toContain('Log in')
    expect(wrapper.find('.lp-nav__actions .btn--primary').attributes('href')).toBe('/login')
    expect(wrapper.find('a[href="/docs"]').exists()).toBe(true)
  })

  it('shows the dashboard CTA for an authenticated visitor', () => {
    const auth = useAuthStore()
    auth.user = { id: 'u1', email: 'user@example.test', displayName: 'User', role: 'USER' }
    vi.spyOn(auth, 'refreshPublicSession').mockResolvedValue()
    const wrapper = mountLanding()

    expect(wrapper.find('.lp-nav__actions .btn--primary').text()).toContain('Open dashboard')
    expect(wrapper.find('.lp-nav__actions .btn--primary').attributes('href')).toBe('/dashboard')
  })

  it('uses repository-backed capability labels instead of fabricated landing metrics', () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'refreshPublicSession').mockResolvedValue()
    const text = mountLanding().text()

    expect(text).toContain('Java')
    expect(text).toContain('Neo4j')
    expect(text).toContain('18')
    expect(text).not.toContain('4K+')
    expect(text).not.toContain('10K+')
    expect(text).not.toContain('300+')
    expect(text).not.toContain('12 downstream callers')
    expect(text).not.toContain('ProjectRepository.java')
    expect(text).not.toContain('Database (MySQL)')
  })

  it('shows the production CLI flow and authenticated MCP example', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'refreshPublicSession').mockResolvedValue()
    const wrapper = mountLanding()
    const cliText = wrapper.text()
    expect(cliText).toContain('vibegraph login')
    expect(cliText).toContain('vibegraph push --root ./your-project')

    await wrapper.findAll('.guide-tab')[2]?.trigger('click')
    await nextTick()
    const text = wrapper.text()

    expect(text).toContain('https://vibegraph.tech/mcp')
    expect(text).toContain('PROJECT_API_KEY')
    expect(text).not.toContain('http://localhost:8080/mcp')
  })
})
