import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AdminLayout from '../AdminLayout.vue'
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/admin', component: { template: '<div>Admin Home</div>' } }]
})

describe('AdminLayout', () => {
  it('renders admin navigation and router-view', async () => {
    router.push('/admin')
    await router.isReady()
    const wrapper = mount(AdminLayout, {
      global: {
        plugins: [router]
      }
    })
    // Expect to have a nav element specific for admin
    expect(wrapper.find('nav').exists()).toBe(true)
    // Expect to render router-view content
    expect(wrapper.html()).toContain('Admin Home')
  })
})
