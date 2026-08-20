import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import LogoSpinner from '../LogoSpinner.vue'

function mockReducedMotion(matches: boolean): void {
  // jsdom does not implement matchMedia; assign a stub directly.
  window.matchMedia = vi.fn().mockImplementation(
    () =>
      ({
        matches,
        media: '',
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }) as MediaQueryList,
  ) as unknown as typeof window.matchMedia
}

describe('LogoSpinner', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders a canvas sized to the size prop', () => {
    const wrapper = mount(LogoSpinner, { props: { size: 80 } })
    const canvas = wrapper.find('canvas')
    expect(canvas.exists()).toBe(true)
    expect(wrapper.attributes('style')).toContain('width: 80px')
  })

  it('animates with requestAnimationFrame by default and cancels on unmount', () => {
    const raf = vi.spyOn(window, 'requestAnimationFrame').mockImplementation(() => 1)
    const caf = vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => undefined)

    const wrapper = mount(LogoSpinner, { props: { size: 40 } })
    expect(raf).toHaveBeenCalled()

    wrapper.unmount()
    expect(caf).toHaveBeenCalled()
  })

  it('renders a static frame (no animation loop) under prefers-reduced-motion', () => {
    mockReducedMotion(true)
    const raf = vi.spyOn(window, 'requestAnimationFrame').mockImplementation(() => 1)

    mount(LogoSpinner, { props: { size: 40 } })
    expect(raf).not.toHaveBeenCalled()
  })

  it('shows the optional label', () => {
    const wrapper = mount(LogoSpinner, { props: { size: 40, label: 'Indexing…' } })
    expect(wrapper.text()).toContain('Indexing…')
  })
})
