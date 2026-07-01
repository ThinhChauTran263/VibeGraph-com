import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { computed, ref } from 'vue'
import NodeDetailPanel from '../NodeDetailPanel.vue'
import type { GraphData, GraphNode } from '@/types/graph'

const selectedNode = ref<GraphNode | null>(null)
const graphData = ref<GraphData>({
  nodes: [],
  edges: [],
  nodeStats: {} as GraphData['nodeStats'],
  edgeStats: {} as GraphData['edgeStats'],
})
const clearSelection = vi.fn<() => void>(() => {
  selectedNode.value = null
})

function node(overrides: Partial<GraphNode>): GraphNode {
  return {
    id: 'node-1',
    type: 'Class',
    name: 'OrderService',
    fullName: 'com.example.OrderService',
    filePath: 'src/main/java/com/example/OrderService.java',
    lineNumber: 42,
    properties: { visibility: 'public' },
    ...overrides,
  }
}

vi.mock('@/composables/useGraphData', () => ({
  useGraphData: () => ({
    selectedNode: computed(() => selectedNode.value),
    filteredGraphData: computed(() => graphData.value),
    clearSelection,
  }),
}))

beforeEach(() => {
  selectedNode.value = null
  graphData.value = {
    nodes: [],
    edges: [],
    nodeStats: {} as GraphData['nodeStats'],
    edgeStats: {} as GraphData['edgeStats'],
  }
  clearSelection.mockClear()
})

describe('NodeDetailPanel', () => {
  it('shows an accessible empty state when no node is selected', () => {
    const wrapper = mount(NodeDetailPanel)

    expect(wrapper.text()).toContain('Select a node to inspect details.')
    expect(wrapper.get('aside').attributes('aria-labelledby')).toBe('node-detail-empty-heading')
    expect(wrapper.get('#node-detail-empty-heading').text()).toBe('Node detail')
  })

  it('renders selected node metadata and primitive properties', () => {
    selectedNode.value = node({
      properties: {
        visibility: 'public',
        springLayer: 'service',
        metadata: { owner: 'team-a' },
        apiKey: 'sk-test',
        accessToken: 'token-value',
      },
    })

    const wrapper = mount(NodeDetailPanel)

    expect(wrapper.text()).toContain('OrderService')
    expect(wrapper.text()).toContain('Class')
    expect(wrapper.text()).toContain('com.example.OrderService')
    // The path is collapsed to its trailing segments by default; the full path is one click away.
    expect(wrapper.text()).toContain('…/example/OrderService.java:42')
    expect(wrapper.text()).toContain('visibility')
    expect(wrapper.text()).toContain('public')
    expect(wrapper.text()).toContain('springLayer')
    expect(wrapper.text()).not.toContain('metadata')
    expect(wrapper.text()).not.toContain('[object Object]')
    expect(wrapper.text()).not.toContain('apiKey')
    expect(wrapper.text()).not.toContain('sk-test')
    expect(wrapper.text()).not.toContain('accessToken')
    expect(wrapper.text()).not.toContain('token-value')
  })

  it('expands the collapsed file path to the full path on demand', async () => {
    selectedNode.value = node({})
    const wrapper = mount(NodeDetailPanel)

    // Collapsed by default; full path hidden until requested.
    expect(wrapper.text()).toContain('…/example/OrderService.java:42')
    expect(wrapper.text()).not.toContain('src/main/java/com/example/OrderService.java:42')

    await wrapper.get('.file-path').trigger('click')

    expect(wrapper.text()).toContain('src/main/java/com/example/OrderService.java:42')
  })

  it('renders incoming and outgoing relationships for the selected node', () => {
    const selected = node({ id: 'service', name: 'OrderService' })
    const controller = node({ id: 'controller', type: 'Class', name: 'OrderController' })
    const repository = node({ id: 'repo', type: 'Interface', name: 'OrderRepository' })
    selectedNode.value = selected
    graphData.value = {
      nodes: [selected, controller, repository],
      edges: [
        { id: 'controller|CALLS|service', source: 'controller', target: 'service', type: 'CALLS' },
        { id: 'service|INJECTS|repo', source: 'service', target: 'repo', type: 'INJECTS' },
      ],
      nodeStats: {} as GraphData['nodeStats'],
      edgeStats: {} as GraphData['edgeStats'],
    }

    const wrapper = mount(NodeDetailPanel)

    expect(wrapper.text()).toContain('Incoming (1)')
    expect(wrapper.text()).toContain('OrderController')
    expect(wrapper.text()).toContain('CALLS')
    expect(wrapper.text()).toContain('Outgoing (1)')
    expect(wrapper.text()).toContain('OrderRepository')
    expect(wrapper.text()).toContain('INJECTS')
  })

  it('caps rendered connection lists', () => {
    const selected = node({ id: 'service', name: 'OrderService' })
    selectedNode.value = selected
    const callers = Array.from({ length: 60 }, (_, index) => node({ id: `caller-${index}`, name: `Caller${index}` }))
    graphData.value = {
      nodes: [selected, ...callers],
      edges: callers.map((caller) => ({
        id: `${caller.id}|CALLS|service`,
        source: caller.id,
        target: 'service',
        type: 'CALLS' as const,
      })),
      nodeStats: {} as GraphData['nodeStats'],
      edgeStats: {} as GraphData['edgeStats'],
    }

    const wrapper = mount(NodeDetailPanel)

    expect(wrapper.text()).toContain('Incoming (50)')
    expect(wrapper.text()).toContain('Caller49')
    expect(wrapper.text()).not.toContain('Caller50')
  })

  it('clears the selected node when close is clicked', async () => {
    selectedNode.value = node({})
    const wrapper = mount(NodeDetailPanel)

    await wrapper.get('button[aria-label="Close node details"]').trigger('click')

    expect(clearSelection).toHaveBeenCalledTimes(1)
  })

  it('emits relationHover with the connecting edge and counterpart on hover, and null on leave', async () => {
    const selected = node({ id: 'service', name: 'OrderService' })
    const controller = node({ id: 'controller', type: 'Class', name: 'OrderController' })
    selectedNode.value = selected
    graphData.value = {
      nodes: [selected, controller],
      edges: [{ id: 'controller|CALLS|service', source: 'controller', target: 'service', type: 'CALLS' }],
      nodeStats: {} as GraphData['nodeStats'],
      edgeStats: {} as GraphData['edgeStats'],
    }

    const wrapper = mount(NodeDetailPanel)
    const button = wrapper.get('.node-detail-panel__connection')

    await button.trigger('mouseenter')
    expect(wrapper.emitted('relationHover')?.[0]).toEqual([
      { edgeId: 'controller|CALLS|service', counterpartNodeId: 'controller' },
    ])

    await button.trigger('mouseleave')
    expect(wrapper.emitted('relationHover')?.[1]).toEqual([null])
  })

  it('emits relationSelect when a connection is clicked', async () => {
    const selected = node({ id: 'service', name: 'OrderService' })
    const repository = node({ id: 'repo', type: 'Interface', name: 'OrderRepository' })
    selectedNode.value = selected
    graphData.value = {
      nodes: [selected, repository],
      edges: [{ id: 'service|INJECTS|repo', source: 'service', target: 'repo', type: 'INJECTS' }],
      nodeStats: {} as GraphData['nodeStats'],
      edgeStats: {} as GraphData['edgeStats'],
    }

    const wrapper = mount(NodeDetailPanel)

    await wrapper.get('.node-detail-panel__connection').trigger('click')

    expect(wrapper.emitted('relationSelect')?.[0]).toEqual([
      { edgeId: 'service|INJECTS|repo', counterpartNodeId: 'repo' },
    ])
  })
})
