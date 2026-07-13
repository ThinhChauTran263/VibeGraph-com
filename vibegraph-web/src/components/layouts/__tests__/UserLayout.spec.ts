import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import UserLayout from '../UserLayout.vue'
import { createRouter, createWebHistory } from 'vue-router'
import { createTestingPinia } from '@pinia/testing'

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', component: { template: '<div>Home</div>' } }]
})

describe('UserLayout', () => {
  it('renders navigation links and router-view', async () => {
    router.push('/')
    await router.isReady()
    const wrapper = mount(UserLayout, {
      global: {
        plugins: [router, createTestingPinia({ createSpy: vi.fn })]
      }
    })
    // Expect to have a nav element
    expect(wrapper.find('nav').exists()).toBe(true)
    // Expect to render router-view content
    expect(wrapper.html()).toContain('Home')
  })
})
