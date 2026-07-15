import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SearchBar from '../SearchBar.vue'
import type { GraphNode } from '@/types/graph'

function node(overrides: Partial<GraphNode>): GraphNode {
  return {
    id: 'n1',
    type: 'Class',
    name: 'OrderService',
    fullName: 'com.example.OrderService',
    filePath: 'src/main/java/com/example/OrderService.java',
    lineNumber: 12,
    properties: {},
    ...overrides,
  }
}

const nodes: GraphNode[] = [
  node({ id: 'class-1', name: 'OrderService', fullName: 'com.example.OrderService' }),
  node({
    id: 'method-1',
    type: 'Method',
    name: 'placeOrder',
    fullName: 'com.example.OrderService.placeOrder',
  }),
]

describe('SearchBar', () => {
  it('filters nodes by name or fullName', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await wrapper.get('input[type="search"]').setValue('place')

    expect(wrapper.text()).toContain('placeOrder')
    expect(wrapper.text()).not.toContain('OrderService · com.example.OrderService')
  })

  it('emits select with the clicked node', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await wrapper.get('input[type="search"]').setValue('order')
    await wrapper.findAll('.search-bar__result')[0]!.trigger('click')

    expect(wrapper.emitted('select')?.[0]?.[0]).toEqual(nodes[0])
  })

  it('collapses the results dropdown after selecting a node but keeps the query', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await wrapper.get('input[type="search"]').setValue('order')
    await wrapper.findAll('.search-bar__result')[0]!.trigger('click')

    expect(wrapper.find('.search-bar__results').exists()).toBe(false)
    expect((wrapper.get('input[type="search"]').element as HTMLInputElement).value).toBe(
      'OrderService',
    )
    expect(wrapper.emitted('clear')).toBeUndefined()
  })

  it('reopens the dropdown when the input is focused again', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await wrapper.get('input[type="search"]').setValue('order')
    await wrapper.findAll('.search-bar__result')[0]!.trigger('click')
    expect(wrapper.find('.search-bar__results').exists()).toBe(false)

    await wrapper.get('input[type="search"]').trigger('focus')

    expect(wrapper.find('.search-bar__results').exists()).toBe(true)
  })

  it('shows an empty state when no nodes match', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await wrapper.get('input[type="search"]').setValue('missing')

    expect(wrapper.text()).toContain('No matching nodes.')
  })

  it('clears the query and emits clear', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await wrapper.get('input[type="search"]').setValue('order')
    await wrapper.get('button[aria-label="Clear search"]').trigger('click')

    expect((wrapper.get('input[type="search"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.emitted('clear')).toHaveLength(1)
  })
})
