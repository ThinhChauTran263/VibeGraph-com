import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UserDetailDrawer from '../UserDetailDrawer.vue'
import type { UserProfile } from '@/types/api'

describe('Admin UserDetailDrawer', () => {
  const mockUser: UserProfile = {
    id: 'usr-1',
    email: 'test@example.com',
    displayName: 'Test',
    role: 'user',
    status: 'active'
  }

  it('renders user information', async () => {
    const wrapper = mount(UserDetailDrawer, {
      props: {
        isOpen: true,
        user: mockUser
      }
    })
    
    expect(wrapper.text()).toContain('test@example.com')
  })

  it('emits close event when close button clicked', async () => {
    const wrapper = mount(UserDetailDrawer, {
      props: {
        isOpen: true,
        user: mockUser
      }
    })
    
    await wrapper.find('.close-btn').trigger('click')
    expect(wrapper.emitted()).toHaveProperty('close')
  })

  it('validates quota override', async () => {
    const wrapper = mount(UserDetailDrawer, {
      props: {
        isOpen: true,
        user: mockUser
      }
    })
    
    // Set a very low quota
    const input = wrapper.find('input[type="number"]')
    await input.setValue('10') // assuming used is 50
    await wrapper.find('.quota-form').trigger('submit')
    
    await flushPromises()
    expect(wrapper.text()).toContain('Cannot set quota lower than currently used')
  })
})
