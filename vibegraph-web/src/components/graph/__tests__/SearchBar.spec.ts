import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import SearchBar from '../SearchBar.vue'
import searchBarSource from '../SearchBar.vue?raw'
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
  // F-L3: results render from the DEBOUNCED query, so every test that asserts on
  // results must let the debounce settle first.
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  async function typeAndSettle(wrapper: VueWrapper, text: string): Promise<void> {
    await wrapper.get('input[type="search"]').setValue(text)
    vi.advanceTimersByTime(200)
    await wrapper.vm.$nextTick()
  }

  it('filters nodes by name or fullName', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await typeAndSettle(wrapper, 'place')

    expect(wrapper.text()).toContain('placeOrder')
    expect(wrapper.text()).not.toContain('OrderService · com.example.OrderService')
  })

  it('emits select with the clicked node', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await typeAndSettle(wrapper, 'order')
    await wrapper.findAll('.search-bar__result')[0]!.trigger('click')

    expect(wrapper.emitted('select')?.[0]?.[0]).toEqual(nodes[0])
  })

  it('collapses the results dropdown after selecting a node but keeps the query', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await typeAndSettle(wrapper, 'order')
    await wrapper.findAll('.search-bar__result')[0]!.trigger('click')

    expect(wrapper.find('.search-bar__results').exists()).toBe(false)
    expect((wrapper.get('input[type="search"]').element as HTMLInputElement).value).toBe(
      'OrderService',
    )
    expect(wrapper.emitted('clear')).toBeUndefined()
  })

  it('reopens the dropdown when the input is focused again', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await typeAndSettle(wrapper, 'order')
    await wrapper.findAll('.search-bar__result')[0]!.trigger('click')
    expect(wrapper.find('.search-bar__results').exists()).toBe(false)

    await wrapper.get('input[type="search"]').trigger('focus')

    expect(wrapper.find('.search-bar__results').exists()).toBe(true)
  })

  it('shows an empty state when no nodes match', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await typeAndSettle(wrapper, 'missing')

    expect(wrapper.text()).toContain('No matching nodes.')
  })

  it('keeps results out of the toolbar layout flow', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await typeAndSettle(wrapper, 'order')

    expect(wrapper.find('.search-bar__results').exists()).toBe(true)
    expect(searchBarSource).toMatch(
      /\.search-bar__results\s*\{[\s\S]*?position:\s*absolute;[\s\S]*?top:\s*calc\(100% \+ 0\.5rem\);/,
    )
  })

  it('clears the query and emits clear', async () => {
    const wrapper = mount(SearchBar, { props: { nodes } })

    await typeAndSettle(wrapper, 'order')
    await wrapper.get('button[aria-label="Clear search"]').trigger('click')

    expect((wrapper.get('input[type="search"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.emitted('clear')).toHaveLength(1)
  })

  it('F-L3: burst typing collapses the O(nodes) filter to at most 2 runs, same final result', async () => {
    const bigNodes = Array.from({ length: 40 }, (_, i) =>
      node({ id: `n${i}`, name: `Node${i}`, fullName: `com.example.Node${i}` }),
    )
    const filterSpy = vi.spyOn(bigNodes, 'filter')
    const wrapper = mount(SearchBar, { props: { nodes: bigNodes } })

    // 10 keystrokes inside the debounce window (10ms apart < 150ms debounce).
    const input = wrapper.get('input[type="search"]')
    for (let i = 1; i <= 10; i++) {
      await input.setValue(`node${i % 4}`)
      vi.advanceTimersByTime(10)
    }
    const runsDuringBurst = filterSpy.mock.calls.length

    // Rest the input on a final term, let the debounce fire once.
    await input.setValue('node3')
    vi.advanceTimersByTime(200)
    await wrapper.vm.$nextTick()

    expect(runsDuringBurst).toBeLessThanOrEqual(2)
    expect(filterSpy.mock.calls.length).toBeLessThanOrEqual(2)
    // Final result identical to what eager filtering would have produced:
    // 'node3' substring-matches Node3 and Node33 only (not Node13).
    expect(wrapper.text()).toContain('Node3')
    expect(wrapper.text()).toContain('Node33')
    expect(wrapper.text()).not.toContain('Node13')
    filterSpy.mockRestore()
  })
})
