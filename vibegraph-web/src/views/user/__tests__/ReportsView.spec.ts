import { describe, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ReportsView from '../ReportsView.vue'

describe('User ReportsView', () => {
  it('renders a form to submit new reports', async () => {
    const wrapper = mount(ReportsView)
    await flushPromises()
    
    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.text()).toContain('Submit Report')
  })

  it('adds a new report to the list when submitted', async () => {
    const wrapper = mount(ReportsView)
    await flushPromises()
    
    const subjectInput = wrapper.find('input[type="text"]')
    await subjectInput.setValue('Test Subject')
    const messageInput = wrapper.find('textarea')
    await messageInput.setValue('Test Message')
    
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    
    expect(wrapper.text()).toContain('Test Subject')
    expect(wrapper.text()).toContain('open')
  })
})
