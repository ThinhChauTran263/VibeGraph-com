import { beforeEach, describe, expect, it } from 'vitest'
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

    expect(i18n.global.locale.value).toBe('vi-VN')
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('vi-VN')
    expect(document.documentElement.lang).toBe('vi-VN')
    expect(wrapper.get('button').text()).toBe('VN')
  })
})
