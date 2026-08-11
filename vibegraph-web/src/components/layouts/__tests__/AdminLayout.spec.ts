import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AdminLayout from '../AdminLayout.vue'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia } from 'pinia'
import i18n, { setLocale } from '@/language'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/admin',
      component: { template: '<div>Admin Home</div>' },
      children: [{ path: '', component: { template: '<div>Admin Home</div>' } }],
    },
  ],
})

describe('AdminLayout', () => {
  it('renders admin navigation and router-view', async () => {
    setLocale('en-US')
    router.push('/admin')
    await router.isReady()
    const wrapper = mount(AdminLayout, {
      global: {
        plugins: [router, createPinia(), i18n],
      },
    })

    expect(wrapper.find('nav').exists()).toBe(true)
    expect(wrapper.text()).toContain('Overview')
    expect(wrapper.text()).toContain('Feedback / Reports')
    expect(wrapper.text()).toContain('Plans & Credits')
    expect(wrapper.text()).toContain('System')
    expect(wrapper.text()).toContain('Announcements')
    expect(wrapper.text()).toContain('Sign Out')
    expect(wrapper.text()).not.toContain('Workspaces')
    expect(wrapper.text()).not.toContain('Spec Designer')
    expect(wrapper.html()).toContain('Admin Home')
  })
})
