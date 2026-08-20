import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import QuotaMeter from '../QuotaMeter.vue'
import i18n from '@/language'

const KiB = 1024
const MiB = 1024 * 1024

describe('QuotaMeter', () => {
  it('renders byte-accurate sizes with adaptive units', () => {
    const wrapper = mount(QuotaMeter, {
      global: { plugins: [i18n] },
      props: {
        usedBytes: 850 * KiB + 808,
        totalBytes: 100 * MiB,
      },
    })

    expect(wrapper.text()).toContain('850.8 KB / 100.0 MB used')
    expect(wrapper.text()).toContain('99.2 MB remaining')
  })

  it('handles zero remaining correctly', () => {
    const wrapper = mount(QuotaMeter, {
      global: { plugins: [i18n] },
      props: {
        usedBytes: 500 * MiB,
        totalBytes: 500 * MiB,
      },
    })

    expect(wrapper.text()).toContain('500.0 MB / 500.0 MB used')
    expect(wrapper.text()).toContain('0 B remaining')
  })

  it('uses status semantics when a determinate quota is unavailable', () => {
    const wrapper = mount(QuotaMeter, {
      global: { plugins: [i18n] },
      props: {
        usedBytes: 0,
        totalBytes: 0,
      },
    })

    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)
    expect(wrapper.get('[role="status"]').text()).toContain('Quota unavailable')
  })
})
