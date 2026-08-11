import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ImportProjectPanel from '../ImportProjectPanel.vue'
import i18n from '@/language'

const formStubs = {
  AddProjectCli: { template: '<div data-test="cli-form" />' },
  AddProjectArchive: { template: '<div data-test="archive-form" />' },
  GitHubImportForm: { template: '<div data-test="github-form" />' },
}

describe('ImportProjectPanel', () => {
  it('selects the first enabled method instead of rendering a disabled form', async () => {
    const wrapper = mount(ImportProjectPanel, {
      props: { disabledMethods: { cli: 'CLI push is disabled.' } },
      global: { plugins: [i18n], stubs: formStubs },
    })

    expect(wrapper.get('[data-test="import-tab-cli"]').attributes()).toHaveProperty('disabled')
    expect(wrapper.find('[data-test="cli-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="archive-form"]').exists()).toBe(true)
  })

  it('renders a clear blocker and no import form when every method is disabled', () => {
    const wrapper = mount(ImportProjectPanel, {
      props: {
        disabledMethods: {
          cli: 'Capability unavailable.',
          archive: 'Capability unavailable.',
          github: 'Capability unavailable.',
        },
      },
      global: { plugins: [i18n], stubs: formStubs },
    })

    expect(wrapper.get('[role="status"]').text()).toContain('No import method is currently available')
    expect(wrapper.find('[data-test="cli-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="archive-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="github-form"]').exists()).toBe(false)
  })
})
