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

  it('keeps one mobile menu control and restores the wordmark after reopening', async () => {
    localStorage.setItem('vg_admin_sidebar_collapsed', 'true')
    setLocale('en-US')
    const wrapper = mount(AdminLayout, {
      global: {
        plugins: [router, createPinia(), i18n],
      },
    })

    await wrapper.get('.mobile-menu').trigger('click')

    expect(wrapper.get('.brand__word').text()).toBe('VibeGraph')
    expect(wrapper.findAll('.mobile-menu')).toHaveLength(0)
    expect(wrapper.get('.admin-sidebar').classes()).not.toContain('is-collapsed')

    await wrapper.get('.sidebar-toggle').trigger('click')

    expect(wrapper.findAll('.mobile-menu')).toHaveLength(1)
    expect(wrapper.get('.admin-sidebar').classes()).not.toContain('is-mobile-open')

    await wrapper.get('.mobile-menu').trigger('click')

    expect(wrapper.get('.brand__word').text()).toBe('VibeGraph')
    expect(wrapper.findAll('.mobile-menu')).toHaveLength(0)
    expect(wrapper.get('.admin-sidebar').classes()).not.toContain('is-collapsed')

    await wrapper.get('.sidebar-toggle').trigger('click')

    expect(wrapper.find('.brand__word').exists()).toBe(false)
    expect(wrapper.get('.admin-sidebar').classes()).toContain('is-collapsed')

    localStorage.removeItem('vg_admin_sidebar_collapsed')
  })
})
