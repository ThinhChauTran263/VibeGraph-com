import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import LanguageSelector from '../LanguageSelector.vue'
import i18n, { LOCALE_STORAGE_KEY, setLocale } from '@/language'

describe('LanguageSelector', () => {
  beforeEach(() => {
    localStorage.clear()
    setLocale('en-US')
  })

  it('switches the global locale and persists the selection', async () => {
    const wrapper = mount(LanguageSelector, { global: { plugins: [i18n] } })

    expect(wrapper.get('button').text()).toBe('US')

    await wrapper.get('button').trigger('click')
    // vi-VN loads lazily on first use (F-M4): the toggle fires an async setLocale whose
    // dynamic-import chain outlives a single microtask flush, so poll until it applies.
    await vi.waitFor(() => expect(i18n.global.locale.value).toBe('vi-VN'))

    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('vi-VN')
    expect(document.documentElement.lang).toBe('vi-VN')
    await vi.waitFor(() => expect(wrapper.get('button').text()).toBe('VN'))
  })
})
