import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { computed, ref } from 'vue'
import FocusDepthControl from '../FocusDepthControl.vue'

const focusDepth = ref(-1)
const noop = (): void => {}
const setFocusDepth = vi.fn<(depth: number) => void>((depth: number) => {
  focusDepth.value = depth
})

vi.mock('@/stores/filter', () => ({
  useFilterStore: () => ({
    hiddenNodeTypes: new Set(),
    hiddenEdgeTypes: new Set(),
    hasActiveFilters: computed(() => focusDepth.value !== -1),
    focusDepth: focusDepth.value,
    searchQuery: '',
    toggleNodeType: vi.fn<() => void>(noop),
    toggleEdgeType: vi.fn<() => void>(noop),
    showAllNodeTypes: vi.fn<() => void>(noop),
    showAllEdgeTypes: vi.fn<() => void>(noop),
    setFocusDepth,
    reset: vi.fn<() => void>(noop),
  }),
}))

beforeEach(() => {
  focusDepth.value = -1
  setFocusDepth.mockClear()
})

describe('FocusDepthControl', () => {
  it('renders all focus depth choices', () => {
    const wrapper = mount(FocusDepthControl)

    expect(wrapper.text()).toContain('All')
    expect(wrapper.text()).toContain('1 hop')
    expect(wrapper.text()).toContain('2 hops')
    expect(wrapper.text()).toContain('3 hops')
    expect(wrapper.text()).toContain('5 hops')
  })

  it('updates focus depth when a choice is selected', async () => {
    const wrapper = mount(FocusDepthControl)

    await wrapper.findAll('button').find((button) => button.text().includes('2 hops'))!.trigger('click')

    expect(setFocusDepth).toHaveBeenCalledWith(2)
  })

  it('marks the current focus depth as pressed', () => {
    focusDepth.value = 1

    const wrapper = mount(FocusDepthControl)
    const selected = wrapper.findAll('button').find((button) => button.text().includes('1 hop'))!

    expect(selected.attributes('aria-pressed')).toBe('true')
  })
})
