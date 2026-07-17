import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import QuotaMeter from '../QuotaMeter.vue'

describe('QuotaMeter', () => {
  it('renders correctly with given used and total limits', () => {
    const wrapper = mount(QuotaMeter, {
      props: {
        used: 100,
        total: 500,
        unit: 'MB',
      },
    })

    expect(wrapper.text()).toContain('100MB / 500MB used')
    expect(wrapper.text()).toContain('400MB remaining')
  })

  it('handles zero remaining correctly', () => {
    const wrapper = mount(QuotaMeter, {
      props: {
        used: 500,
        total: 500,
        unit: 'MB',
      },
    })

    expect(wrapper.text()).toContain('500MB / 500MB used')
    expect(wrapper.text()).toContain('0MB remaining')
  })

  it('uses status semantics when a determinate quota is unavailable', () => {
    const wrapper = mount(QuotaMeter, {
      props: {
        used: 0,
        total: 0,
        unit: 'MB',
      },
    })

    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)
    expect(wrapper.get('[role="status"]').text()).toContain('Quota unavailable')
  })
})
