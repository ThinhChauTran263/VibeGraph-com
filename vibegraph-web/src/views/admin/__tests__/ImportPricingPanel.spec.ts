import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ImportPricingPanel from '../ImportPricingPanel.vue'
import { useAdminStore } from '@/stores/admin'
import i18n, { setLocale } from '@/language'

// Một operation đủ 4 tier; tier cao nhất maxFiles = null (không giới hạn).
const makePricing = () => [
  {
    operationCode: 'IMPORT_ARCHIVE',
    tiers: [
      { tierCode: 'SMALL', maxFiles: 100, credits: 2 },
      { tierCode: 'MEDIUM', maxFiles: 500, credits: 5 },
      { tierCode: 'LARGE', maxFiles: 2000, credits: 15 },
      { tierCode: 'XLARGE', maxFiles: null, credits: 40 },
    ],
  },
]

const mountPanel = () =>
  mount(ImportPricingPanel, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: { admin: { importPricing: makePricing() } },
        }),
        i18n,
      ],
    },
  })

describe('Admin ImportPricingPanel', () => {
  beforeEach(() => setLocale('en-US'))

  // Không còn ký hiệu ∞: tier cao nhất là ô nhập liệu để trống, chỉnh được như các tier khác.
  it('renders the unlimited top tier as an editable, empty max-files input', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).not.toContain('∞')
    const inputs = wrapper.findAll('input[type="number"]')
    // 4 tier × 2 ô (maxFiles + credits)
    expect(inputs.length).toBe(8)

    const topMaxFiles = inputs[6]?.element as HTMLInputElement
    expect(topMaxFiles.value).toBe('')
  })

  // Nhập số vào tier cao nhất rồi Save → gửi maxFiles dạng số lên store.
  it('saves a typed bound for the top tier and clears back to unlimited', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    const adminStore = useAdminStore()
    const saveSpy = adminStore.saveImportPricing as unknown as ReturnType<typeof vi.fn>

    const inputs = wrapper.findAll('input[type="number"]')
    const topMaxFiles = inputs[6]!
    await topMaxFiles.setValue('10000')

    await wrapper.find('button.import-pricing__save').trigger('click')
    await flushPromises()
    expect(saveSpy).toHaveBeenCalledWith(
      'IMPORT_ARCHIVE',
      expect.arrayContaining([
        expect.objectContaining({ tierCode: 'XLARGE', maxFiles: 10000, credits: 40 }),
      ]),
    )

    // Xoá trắng → trở lại null (không giới hạn) và Save vẫn hợp lệ.
    saveSpy.mockClear()
    await topMaxFiles.setValue('')
    await wrapper.find('button.import-pricing__save').trigger('click')
    await flushPromises()
    expect(saveSpy).toHaveBeenCalledWith(
      'IMPORT_ARCHIVE',
      expect.arrayContaining([expect.objectContaining({ tierCode: 'XLARGE', maxFiles: null })]),
    )
  })
})
