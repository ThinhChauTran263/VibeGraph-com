import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import LandingView from '../LandingView.vue'
import i18n from '@/language'

// F-L1/F-L2: LandingView schedules self-rescheduling timers (terminal typing,
// impact propagation, tour loop) and four window listeners on mount. None of
// them may keep firing after unmount.
describe('LandingView lifecycle cleanup', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('F-L1: terminal typing chain stops at unmount and never writes again', () => {
    const warn = vi.spyOn(console, 'error').mockImplementation(() => {})
    const wrapper = mount(LandingView, { global: { plugins: [i18n] } })
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
    const wrapper = mount(LandingView, { global: { plugins: [i18n] } })

    wrapper.unmount()

    const removedTypes = removeSpy.mock.calls.map((call) => call[0])
    for (const type of ['scroll', 'mousemove', 'mousedown', 'keydown']) {
      expect(removedTypes).toContain(type)
    }
    removeSpy.mockRestore()
  })

  it('F-L2: dispatching a tour event after unmount does not run stopAutoTour work', () => {
    const wrapper = mount(LandingView, { global: { plugins: [i18n] } })
    wrapper.unmount()

    // No listener may remain to handle this; if one did, jsdom would route it
    // into the unmounted component. Asserting zero listeners via a spy on the
    // handler path is impractical, so assert the dispatch itself is a no-op
    // that throws nothing and leaves no timers scheduled.
    expect(() => window.dispatchEvent(new Event('keydown'))).not.toThrow()
    expect(vi.getTimerCount()).toBe(0)
  })
})
