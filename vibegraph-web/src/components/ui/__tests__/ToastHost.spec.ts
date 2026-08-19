import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ToastHost from '../ToastHost.vue'
import { useToasts } from '@/stores/toasts'
import i18n from '@/language'

describe('ToastHost', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders toasts with message and a view-project action', async () => {
    const toasts = useToasts()
    toasts.push({
      kind: 'success',
      title: 'svc-alpha is ready',
      message: 'Explore the graph now.',
      durationMs: 0,
      actionLabel: 'View project',
      actionRoute: { name: 'graph', params: { projectId: 'p-1' } },
    })

    const wrapper = mount(ToastHost, {
      global: { plugins: [i18n], stubs: { RouterLink: true } },
    })

    const toast = wrapper.find('.toast--success')
    expect(toast.exists()).toBe(true)
    expect(toast.text()).toContain('svc-alpha is ready')
    expect(toast.text()).toContain('Explore the graph now.')
    expect(toast.find('.toast__actions').exists()).toBe(true)
  })

  it('dismisses a toast via its close button', async () => {
    const toasts = useToasts()
    toasts.push({ kind: 'success', title: 'Done', durationMs: 0 })

    const wrapper = mount(ToastHost, {
      global: { plugins: [i18n], stubs: { RouterLink: true } },
    })

    await wrapper.find('.toast__close').trigger('click')
    expect(toasts.toasts).toHaveLength(0)
  })
})
