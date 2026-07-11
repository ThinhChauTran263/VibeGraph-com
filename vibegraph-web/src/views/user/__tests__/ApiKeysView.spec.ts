import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ApiKeysView from '../ApiKeysView.vue'
import { useAccountStore } from '@/stores/account'

describe('ApiKeysView', () => {
  it('renders api keys correctly', async () => {
    const wrapper = mount(ApiKeysView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                apiKeys: [
                  { id: 'key1', name: 'Test Key', disabled: false, createdAt: '2023-10-01T12:00:00Z' }
                ]
              }
            }
          })
        ]
      }
    })

    await flushPromises()
    expect(wrapper.text()).toContain('Test Key')
    expect(wrapper.text()).toContain('Active')
  })

  it('calls disableApiKey when disable button is clicked', async () => {
    const wrapper = mount(ApiKeysView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                apiKeys: [
                  { id: 'key1', name: 'Test Key', disabled: false, createdAt: '2023-10-01T12:00:00Z' }
                ]
              }
            }
          })
        ]
      }
    })

    await flushPromises()
    const store = useAccountStore()
    vi.stubGlobal('confirm', () => true)
    await wrapper.find('.btn-disable').trigger('click')
    expect(store.disableApiKey).toHaveBeenCalledWith('key1')
  })

  it('creates new api key', async () => {
    const wrapper = mount(ApiKeysView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                apiKeys: []
              }
            }
          })
        ]
      }
    })

    const store = useAccountStore()
    // Mock the return value of createApiKey
    // Type assertion is needed because store is mocked by testing pinia
    ;(store.createApiKey as any).mockResolvedValue({
      id: 'key2', name: 'New Key', secret: 'vg-secret123', disabled: false, createdAt: 'now'
    })

    const input = wrapper.find('input[type="text"]')
    await input.setValue('New Key')
    await wrapper.find('form').trigger('submit')
    
    await flushPromises()
    expect(store.createApiKey).toHaveBeenCalledWith('New Key')
    // We should see the secret since it was just created
    expect(wrapper.text()).toContain('vg-secret123')
  })
})
