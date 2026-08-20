import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RepositorySelect from '../RepositorySelect.vue'
import type { Project } from '@/types/api'

const projects: Project[] = [
  {
    id: 'project-1',
    name: 'ThinhChauTran263/SPX_Tracking',
    sourceType: 'GITHUB',
    sizeBytes: 0,
    status: 'READY',
    createdAt: null,
    updatedAt: null,
    lastAnalyzedAt: null,
  },
  {
    id: 'project-2',
    name: 'ThinhChauTran263/fatc-Grocery-Store-with-a-very-long-repository-name',
    sourceType: 'GITHUB',
    sizeBytes: 0,
    status: 'READY',
    createdAt: null,
    updatedAt: null,
    lastAnalyzedAt: null,
  },
]

function mountSelect() {
  return mount(RepositorySelect, {
    props: {
      modelValue: '',
      projects,
      label: 'Repository',
      placeholder: 'Select a repository',
      existingLabel: 'Existing key must be deleted',
      existingProjectIds: new Set(['project-2']),
    },
  })
}

describe('RepositorySelect', () => {
  it('renders the repository state as a separate badge and emits the selected id', async () => {
    const wrapper = mountSelect()

    await wrapper.get('[data-test="repository-select-trigger"]').trigger('click')
    const existingOption = wrapper.get('[data-test="repository-option-project-2"]')

    expect(existingOption.get('strong').text()).toBe(projects[1]!.name)
    expect(existingOption.get('small').text()).toBe('Existing key must be deleted')
    await existingOption.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['project-2']])
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  })

  it('supports arrow, enter, and escape without closing the parent dialog', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.get('[data-test="repository-select-trigger"]')

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(trigger.attributes('aria-activedescendant')).toContain('project-1')

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')).toEqual([['project-2']])

    await trigger.trigger('click')
    await trigger.trigger('keydown', { key: 'Escape' })
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })
})
