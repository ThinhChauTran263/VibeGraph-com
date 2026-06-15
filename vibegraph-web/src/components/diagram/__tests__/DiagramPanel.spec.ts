import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import DiagramPanel from '../DiagramPanel.vue'
import { ApiError, type DiagramResponse, type UseCaseResponse } from '@/lib/api'

vi.mock('mermaid', () => ({
  default: {
    initialize: vi.fn<(config?: unknown) => void>(),
    render: vi.fn<(id: string, source: string) => Promise<{ svg: string }>>(async (id, source) => ({
      svg: `<svg role="img" data-id="${id}"><text>${source}</text></svg>`,
    })),
  },
}))

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    diagramApi: {
      ...actual.diagramApi,
      useCase: vi.fn<(projectId: string) => Promise<UseCaseResponse>>(),
      classDiagram: vi.fn<(projectId: string, pkg?: string) => Promise<DiagramResponse>>(),
    },
  }
})

const { diagramApi } = await import('@/lib/api')
const useCaseMock = diagramApi.useCase as ReturnType<typeof vi.fn>
const classDiagramMock = diagramApi.classDiagram as ReturnType<typeof vi.fn>

function useCaseResponse(mermaid = 'flowchart LR\n  client[HTTP Client] --> route[GET /orders]'): UseCaseResponse {
  return { projectId: 'p1', mermaid }
}

function classResponse(mermaid = 'classDiagram\n  class OrderService'): DiagramResponse {
  return { projectId: 'p1', diagramType: 'class', mermaid }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

async function flushAsync(): Promise<void> {
  await nextTick()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

beforeEach(() => {
  useCaseMock.mockReset()
  classDiagramMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('DiagramPanel', () => {
  it('loads and renders the use case diagram by default', async () => {
    useCaseMock.mockResolvedValueOnce(useCaseResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()

    expect(useCaseMock).toHaveBeenCalledWith('p1')
    expect(wrapper.text()).toContain('Use Case')
    expect(wrapper.html()).toContain('<svg')
    expect(wrapper.text()).toContain('HTTP Client')
  })

  it('switches to class diagram and renders the class response', async () => {
    useCaseMock.mockResolvedValueOnce(useCaseResponse())
    classDiagramMock.mockResolvedValueOnce(classResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()
    await wrapper.get('[data-test="diagram-tab-class"]').trigger('click')
    await flushAsync()

    expect(classDiagramMock).toHaveBeenCalledWith('p1', undefined)
    expect(wrapper.text()).toContain('Class')
    expect(wrapper.text()).toContain('OrderService')
  })

  it('passes the package filter to the class endpoint', async () => {
    useCaseMock.mockResolvedValueOnce(useCaseResponse())
    classDiagramMock.mockResolvedValueOnce(classResponse())
    classDiagramMock.mockResolvedValueOnce(classResponse('classDiagram\n  class InvoiceService'))

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()
    await wrapper.get('[data-test="diagram-tab-class"]').trigger('click')
    await flushAsync()
    await wrapper.get('#diagram-package-filter').setValue('com.example.billing')
    await wrapper.get('[data-test="diagram-refresh"]').trigger('click')
    await flushAsync()

    expect(classDiagramMock).toHaveBeenLastCalledWith('p1', 'com.example.billing')
    expect(wrapper.text()).toContain('InvoiceService')
  })

  it('shows a friendly PROJECT_NOT_ANALYZED error', async () => {
    useCaseMock.mockRejectedValueOnce(new ApiError(409, 'Conflict', 'PROJECT_NOT_ANALYZED'))

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()

    expect(wrapper.get('[role="alert"]').text()).toContain('Analyze this project')
  })

  it('shows loading, error, and empty states', async () => {
    const slow = deferred<UseCaseResponse>()
    useCaseMock.mockReturnValueOnce(slow.promise)
    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })

    await nextTick()
    expect(wrapper.get('[role="status"]').text()).toContain('Loading diagram')

    slow.resolve(useCaseResponse(''))
    await flushAsync()
    expect(wrapper.text()).toContain('No diagram content')

    useCaseMock.mockRejectedValueOnce(new Error('network down'))
    await wrapper.get('[data-test="diagram-refresh"]').trigger('click')
    await flushAsync()
    expect(wrapper.get('[role="alert"]').text()).toContain('network down')
  })

  it('cleans up stale responses when the project changes', async () => {
    const oldRequest = deferred<UseCaseResponse>()
    useCaseMock.mockReturnValueOnce(oldRequest.promise)
    useCaseMock.mockResolvedValueOnce(useCaseResponse('flowchart LR\n  fresh[Fresh Project]'))

    const wrapper = mount(DiagramPanel, { props: { projectId: 'old-project' } })
    await nextTick()
    await wrapper.setProps({ projectId: 'new-project' })
    await flushAsync()

    oldRequest.resolve(useCaseResponse('flowchart LR\n  stale[Old Project]'))
    await flushAsync()

    expect(useCaseMock).toHaveBeenLastCalledWith('new-project')
    expect(wrapper.text()).toContain('Fresh Project')
    expect(wrapper.text()).not.toContain('Old Project')
  })
})
