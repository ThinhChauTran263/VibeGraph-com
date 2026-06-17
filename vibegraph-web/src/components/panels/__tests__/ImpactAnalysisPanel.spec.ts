import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ImpactAnalysisPanel from '../ImpactAnalysisPanel.vue'
import type { GraphNode } from '@/types/graph'
import { ApiError, type ImpactAnalysisResponse, type ImpactProfile } from '@/lib/api'

/**
 * The panel drives requests through `useImpactAnalysis`, which calls
 * `graphApi.getImpact`. Mocking at the api layer keeps the component test
 * free from real fetches while exercising the real composable wiring.
 */
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    graphApi: {
      ...actual.graphApi,
      getImpact:
        vi.fn<(
          projectId: string,
          nodeId: string,
          depth: number,
          profile?: ImpactProfile,
        ) => Promise<ImpactAnalysisResponse>>(),
    },
  }
})

const { graphApi } = await import('@/lib/api')
const getImpactMock = graphApi.getImpact as ReturnType<typeof vi.fn>

function fakeNode(overrides: Partial<GraphNode> = {}): GraphNode {
  return {
    id: 'com.example.OrderService',
    type: 'Class',
    name: 'OrderService',
    fullName: 'com.example.OrderService',
    filePath: 'src/OrderService.java',
    lineNumber: 10,
    properties: {},
    ...overrides,
  }
}

function fakeImpact(overrides: Partial<ImpactAnalysisResponse> = {}): ImpactAnalysisResponse {
  return {
    target: {
      id: 'com.example.OrderService',
      type: 'Class',
      name: 'OrderService',
      fullName: 'com.example.OrderService',
      filePath: 'src/OrderService.java',
      lineNumber: 10,
    },
    riskLevel: 'HIGH',
    directDependents: 2,
    totalDependents: 3,
    willBreak: [
      {
        id: 'com.example.Caller',
        type: 'Class',
        name: 'Caller',
        fullName: 'com.example.Caller',
        filePath: 'src/Caller.java',
        lineNumber: 5,
      },
    ],
    likelyAffected: [],
    mayNeedTesting: [],
    ...overrides,
  }
}

beforeEach(() => {
  getImpactMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('ImpactAnalysisPanel', () => {
  it('renders an empty state when no node is selected', () => {
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: null },
    })
    expect(wrapper.text()).toContain('Select a node')
    expect(wrapper.find('#impact-depth').exists()).toBe(false)
    expect(getImpactMock).not.toHaveBeenCalled()
  })

  it('shows the target node and a depth selector when a node is selected', () => {
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode() },
    })
    expect(wrapper.text()).toContain('OrderService')
    const select = wrapper.get('#impact-depth')
    const options = select.findAll('option').map((o) => o.text())
    expect(options).toEqual(['1', '2', '3', '5'])
    const profiles = wrapper.get('#impact-profile').findAll('option').map((o) => o.text())
    expect(profiles).toEqual(['Dependency', 'Structural', 'Type/Data-flow'])
  })

  it('loads and displays the impact result on submit', async () => {
    getImpactMock.mockResolvedValueOnce(fakeImpact())
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode() },
    })

    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()

    expect(getImpactMock).toHaveBeenCalledWith('p1', 'com.example.OrderService', 1, 'dependency')
    expect(wrapper.text()).toContain('HIGH')
    expect(wrapper.text()).toContain('Will break')
    expect(wrapper.text()).toContain('Caller')
  })

  it('passes the chosen depth to the API', async () => {
    getImpactMock.mockResolvedValueOnce(fakeImpact())
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode() },
    })

    await wrapper.get('#impact-depth').setValue('3')
    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()

    expect(getImpactMock).toHaveBeenCalledWith('p1', 'com.example.OrderService', 3, 'dependency')
  })

  it('passes the chosen profile to the API', async () => {
    getImpactMock.mockResolvedValueOnce(fakeImpact())
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode({ type: 'Package' }) },
    })

    await wrapper.get('#impact-profile').setValue('structural')
    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()

    expect(getImpactMock).toHaveBeenCalledWith('p1', 'com.example.OrderService', 1, 'structural')
    expect(wrapper.text()).toContain('Direct related')
  })

  it('shows an accessible error state when the API fails', async () => {
    getImpactMock.mockRejectedValueOnce(new ApiError(404, 'Not Found', 'Node not found'))
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode() },
    })

    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('Node not found')
  })

  it('resets the result when the selected node changes', async () => {
    getImpactMock.mockResolvedValueOnce(fakeImpact())
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode() },
    })

    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()
    expect(wrapper.text()).toContain('HIGH')

    await wrapper.setProps({ node: fakeNode({ id: 'com.example.Other', name: 'Other' }) })
    await nextTick()

    // Previous result cleared; back to the pre-analysis hint.
    expect(wrapper.text()).not.toContain('Will break')
  })

  it('invalidates a shown result when the depth selector changes (no stale numbers)', async () => {
    getImpactMock.mockResolvedValueOnce(fakeImpact())
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode() },
    })

    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()
    expect(wrapper.text()).toContain('Will break')

    // Changing depth after a result must clear it so the displayed numbers never
    // disagree with the selector; the user re-runs Analyze.
    await wrapper.get('#impact-depth').setValue('3')
    await nextTick()

    expect(wrapper.text()).not.toContain('Will break')
    expect(wrapper.text()).toContain('Choose a depth and run analysis')
  })

  it('invalidates a shown result when the profile selector changes', async () => {
    getImpactMock.mockResolvedValueOnce(fakeImpact())
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode() },
    })

    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()
    expect(wrapper.text()).toContain('Will break')

    await wrapper.get('#impact-profile').setValue('structural')
    await nextTick()

    expect(wrapper.text()).not.toContain('Will break')
    expect(wrapper.text()).toContain('Choose a depth and run analysis')
  })

  it('explains the empty result for node types the dependency traversal cannot target', async () => {
    getImpactMock.mockResolvedValueOnce(
      fakeImpact({
        riskLevel: 'LOW',
        directDependents: 0,
        totalDependents: 0,
        willBreak: [],
        likelyAffected: [],
        mayNeedTesting: [],
        target: { id: 'poly.pkg', type: 'Package', name: 'pkg', fullName: 'poly.pkg', filePath: '' },
      }),
    )
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode({ id: 'poly.pkg', name: 'pkg', type: 'Package' }) },
    })

    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()

    const text = wrapper.text()
    expect(text).toContain('not targets of those relationships')
    expect(text).not.toContain('Try a deeper depth')
  })

  it('frames an empty result on a supported type as a likely entrypoint', async () => {
    getImpactMock.mockResolvedValueOnce(
      fakeImpact({
        riskLevel: 'LOW',
        directDependents: 0,
        totalDependents: 0,
        willBreak: [],
        likelyAffected: [],
        mayNeedTesting: [],
      }),
    )
    const wrapper = mount(ImpactAnalysisPanel, {
      props: { projectId: 'p1', node: fakeNode() },
    })

    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()

    expect(wrapper.text()).toContain('entrypoint')
  })
})
