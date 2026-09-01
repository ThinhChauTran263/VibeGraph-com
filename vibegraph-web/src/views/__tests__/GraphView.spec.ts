import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import GraphView from '../GraphView.vue'

const routeParams = vi.hoisted(() => ({ projectId: undefined as string | undefined }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams }),
}))

vi.mock('@/components/graph/GraphCanvas.vue', () => ({
  default: {
    props: ['projectId', 'sidebarWidth', 'sidebarCollapsed'],
    emits: ['update:sidebarCollapsed'],
    template: `<div data-testid="graph-canvas">
      {{ projectId }}
      <button data-testid="collapse-sidebar" @click="$emit('update:sidebarCollapsed', true)">Collapse</button>
    </div>`,
  },
}))

vi.mock('@/components/diagram/DiagramPanel.vue', () => ({
  default: {
    props: ['projectId'],
    template: '<div data-testid="diagram-panel">{{ projectId }}</div>',
  },
}))

describe('GraphView', () => {
  afterEach(() => {
    routeParams.projectId = undefined
    localStorage.clear()
  })

  function mountView() {
    return mount(GraphView, {
      global: {
        stubs: {
          BrandMark: true,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
  }

  it('does not mount a project visualization while the route has no project ID', () => {
    const wrapper = mountView()

    expect(wrapper.find('[data-testid="graph-canvas"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="diagram-panel"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('default')
  })

  it('passes the route project ID to the graph canvas', () => {
    routeParams.projectId = 'project-1'

    const wrapper = mountView()

    expect(wrapper.get('[data-testid="graph-canvas"]').text()).toContain('project-1')
  })

  it('keeps visualization tabs anchored when the graph sidebar collapses', async () => {
    routeParams.projectId = 'project-1'
    localStorage.setItem('vibegraph.sidebarWidth', '420')
    const wrapper = mountView()

    expect(wrapper.get('.graph-view').attributes('style')).toContain('--sidebar-width: 420px')

    await wrapper.get('[data-testid="collapse-sidebar"]').trigger('click')

    expect(wrapper.get('.graph-view').attributes('style')).toContain('--sidebar-width: 420px')
  })
})
