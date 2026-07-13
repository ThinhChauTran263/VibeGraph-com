import { describe, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AdminReportsView from '../AdminReportsView.vue'

describe('Admin AdminReportsView', () => {
  it('renders a list of reports', async () => {
    const wrapper = mount(AdminReportsView)
    await flushPromises()
    
    expect(wrapper.text()).toContain('Admin Reports Management')
  })
})
