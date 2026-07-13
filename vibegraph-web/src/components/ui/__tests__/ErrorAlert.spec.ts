import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ErrorAlert from '../ErrorAlert.vue'

describe('ErrorAlert', () => {
  it('renders the title and message correctly', () => {
    const wrapper = mount(ErrorAlert, {
      props: {
        title: 'Error Title',
        message: 'This is an error message'
      }
    })
    
    expect(wrapper.text()).toContain('Error Title')
    expect(wrapper.text()).toContain('This is an error message')
  })

  it('renders slot content', () => {
    const wrapper = mount(ErrorAlert, {
      slots: {
        default: '<button>Fix Issue</button>'
      }
    })
    
    expect(wrapper.find('button').exists()).toBe(true)
  })
})
