import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import ThemedSelect from '../ThemedSelect.vue'

const options = [
  { value: 'all', label: 'All outcomes' },
  { value: 'success', label: 'Success' },
  { value: 'failure', label: 'Failure' },
]

describe('ThemedSelect', () => {
  it('opens, selects an option, and preserves form semantics', async () => {
    const wrapper = mount(ThemedSelect, {
      props: {
        modelValue: 'all',
        options,
        inputId: 'outcome-select',
        ariaLabel: 'Filter by outcome',
        name: 'outcome',
      },
    })

    const trigger = wrapper.get('#outcome-select')
    expect(trigger.attributes('aria-label')).toBe('Filter by outcome')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('input[type="hidden"]').attributes('name')).toBe('outcome')

    await trigger.trigger('click')
    expect(trigger.attributes('aria-expanded')).toBe('true')
    await wrapper.get('#outcome-select-listbox-option-1').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['success']])
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })

  it('supports keyboard navigation and escape', async () => {
    const wrapper = mount(ThemedSelect, {
      props: { modelValue: 'all', options, inputId: 'keyboard-select' },
    })
    const trigger = wrapper.get('#keyboard-select')

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(trigger.attributes('aria-activedescendant')).toBe('keyboard-select-listbox-option-1')
    await trigger.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')).toEqual([['success']])

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'Escape' })
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })

  it('generates unique ids and safely ignores empty or disabled selects', async () => {
    const host = mount(
      defineComponent({
        components: { ThemedSelect },
        setup: () => ({ emptyOptions: [] }),
        template: `
          <ThemedSelect model-value="" :options="emptyOptions" />
          <ThemedSelect model-value="" :options="emptyOptions" disabled />
        `,
      }),
    )
    const [firstTrigger, secondTrigger] = host.findAll('button')
    if (!firstTrigger || !secondTrigger) {
      throw new Error('Expected both select triggers to render')
    }

    expect(firstTrigger.attributes('id')).not.toBe(secondTrigger.attributes('id'))
    await firstTrigger.trigger('click')
    expect(firstTrigger.attributes('aria-expanded')).toBe('false')
    expect(secondTrigger.attributes('disabled')).toBeDefined()
  })
})
