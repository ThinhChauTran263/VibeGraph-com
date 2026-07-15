import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusChip from '../StatusChip.vue'

describe('StatusChip', () => {
  it('renders the label correctly', () => {
    const wrapper = mount(StatusChip, {
      props: {
        status: 'active',
        label: 'Active',
      },
    })

    expect(wrapper.text()).toContain('Active')
    expect(wrapper.classes()).toContain('status-active')
  })
})
