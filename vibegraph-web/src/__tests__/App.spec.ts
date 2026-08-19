import { describe, it, expect, vi } from 'vitest'

import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'

import App from '../App.vue'
import i18n from '@/language'

describe('App', () => {
  it('renders the router outlet (not the Vue starter page)', () => {
    // Stub RouterView so this stays a unit test of the app entrypoint:
    // it must delegate to the router outlet, without booting a real router
    // (which pulls in vue-router devtools + localStorage in the test runtime).
    const wrapper = mount(App, {
      global: {
        plugins: [i18n, createTestingPinia({ createSpy: vi.fn })],
        stubs: {
          RouterView: {
            template: '<div class="route-content">routed content</div>',
          },
        },
      },
    })

    // The app must render the router outlet's content...
    expect(wrapper.find('.route-content').exists()).toBe(true)
    expect(wrapper.text()).toContain('routed content')
    // ...and the Vue scaffolding starter page must be gone.
    expect(wrapper.text()).not.toContain('You did it!')
  })
})
