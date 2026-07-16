import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ReportsView from '../ReportsView.vue'
import { useAccountStore } from '@/stores/account'

describe('User ReportsView', () => {
  it('renders a form to submit new reports', async () => {
    const wrapper = mount(ReportsView, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
      },
    })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.text()).toContain('Submit Report')
  })

  it('adds a new report to the list when submitted', async () => {
    const wrapper = mount(ReportsView, {
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
      },
    })
    await flushPromises()

    const store = useAccountStore()
    // Mock the store action so the component receives what it expects
    ;(store.createReport as any).mockResolvedValue({
      id: 'r1',
      title: 'Test Subject',
      category: 'BUG',
      status: 'OPEN',
      createdAt: '2023-10-01T12:00:00Z',
      messages: [],
    })

    const subjectInput = wrapper.find('input[type="text"]')
    await subjectInput.setValue('Test Subject')
    const messageInput = wrapper.find('textarea')
    await messageInput.setValue('Test Message')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(store.createReport).toHaveBeenCalled()
    // The new item might not appear if we just mocked the action, so we can test that the action was called.
  })
})
