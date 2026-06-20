import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import DiagramPanel from '../DiagramPanel.vue'
import { ApiError, type DiagramResponse, type UmlUseCaseResponse } from '@/lib/api'

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
      umlUseCase: vi.fn<(projectId: string, mode?: string) => Promise<UmlUseCaseResponse>>(),
      classDiagram: vi.fn<(projectId: string, pkg?: string) => Promise<DiagramResponse>>(),
    },
  }
})

const { diagramApi } = await import('@/lib/api')
const umlUseCaseMock = diagramApi.umlUseCase as ReturnType<typeof vi.fn>
const classDiagramMock = diagramApi.classDiagram as ReturnType<typeof vi.fn>

function umlUseCaseResponse(overrides: Partial<UmlUseCaseResponse> = {}): UmlUseCaseResponse {
  return {
    diagramType: 'usecase',
    style: 'uml',
    mode: 'flat',
    systemName: 'Orders Service',
    actors: [{ id: 'A_Admin', name: 'Admin', source: 'path:/admin', confidence: 0.9 }],
    useCases: [
      {
        id: 'UC_ManageProduct',
        name: 'Manage products',
        domain: 'Product',
        level: 'summary',
        source: 'group',
        sourceEndpoint: null,
        confidence: 0.8,
      },
    ],
    relations: [
      { from: 'A_Admin', to: 'UC_ManageProduct', type: 'association', label: null, confidence: 0.8 },
    ],
    warnings: ['Role for POST /api/products inferred from HTTP method.'],
    mermaidSyntax: 'flowchart TB\n  subgraph Orders\n    uc([Manage products])\n  end',
    plantUmlSyntax: '@startuml\nleft to right direction\nrectangle "Orders Service" {\n}\n@enduml',
    ...overrides,
  }
}

function classResponse(mermaidSyntax = 'classDiagram\n  class OrderService'): DiagramResponse {
  return { diagramType: 'class', mermaidSyntax }
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
  umlUseCaseMock.mockReset()
  classDiagramMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('DiagramPanel', () => {
  it('loads and renders the UML use case diagram by default (no API Map tab)', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()

    expect(umlUseCaseMock).toHaveBeenCalledWith('p1', 'detailed')
    // API Map has been removed; only UML and Class tabs remain.
    expect(wrapper.find('[data-test="diagram-tab-api-map"]').exists()).toBe(false)
    expect(wrapper.get('[data-test="diagram-tab-uml"]').text()).toBe('UML Use Case')
    expect(wrapper.get('[data-test="diagram-tab-class"]').text()).toBe('Class')
    expect(wrapper.html()).toContain('<svg')
    expect(wrapper.html()).toContain('Manage products')
  })

  it('renders UML warnings without exposing PlantUML source', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()

    expect(wrapper.get('[data-test="diagram-warnings"]').text()).toContain('inferred from HTTP method')
    expect(wrapper.find('[data-test="diagram-plantuml-source"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="diagram-copy-plantuml"]').exists()).toBe(false)
    expect(wrapper.html()).toContain('Manage products')
  })

  it('renders a single canonical UML diagram with no detail-level toggle', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()

    // The canonical model is mode-independent, so the Flat/Grouped toggle is gone.
    expect(wrapper.find('[data-test="diagram-uml-mode-flat"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="diagram-uml-mode-grouped"]').exists()).toBe(false)
    expect(umlUseCaseMock).toHaveBeenCalledTimes(1)
    expect(umlUseCaseMock).toHaveBeenCalledWith('p1', 'detailed')
  })

  it('zooms the rendered diagram without reloading it', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()
    expect(wrapper.get('[data-test="diagram-zoom-reset"]').text()).toBe('100%')

    await wrapper.get('[data-test="diagram-zoom-in"]').trigger('click')
    await flushAsync()
    expect(wrapper.get('[data-test="diagram-zoom-reset"]').text()).toBe('110%')
    expect(wrapper.get('[data-test="diagram-stage"]').attributes('style')).toContain('scale(2.42)')

    await wrapper.get('[data-test="diagram-zoom-out"]').trigger('click')
    await flushAsync()
    expect(wrapper.get('[data-test="diagram-zoom-reset"]').text()).toBe('100%')
    expect(umlUseCaseMock).toHaveBeenCalledTimes(1)
  })

  it('opens and closes the fullscreen diagram viewer', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' }, attachTo: document.body })
    await flushAsync()
    await wrapper.get('[data-test="diagram-fullscreen-open"]').trigger('click')
    await flushAsync()

    expect(document.body.querySelector('[data-test="diagram-fullscreen"]')).not.toBeNull()
    expect(document.body.textContent).toContain('Diagram viewer')

    const close = document.body.querySelector('[data-test="diagram-fullscreen-close"]') as HTMLButtonElement
    close.click()
    await flushAsync()
    expect(document.body.querySelector('[data-test="diagram-fullscreen"]')).toBeNull()

    wrapper.unmount()
  })

  it('hides inference warnings in formal export mode but keeps them in interactive mode', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()

    // Interactive mode: warning is visible to developers.
    expect(wrapper.find('[data-test="diagram-warnings"]').exists()).toBe(true)

    // Toggle formal export mode: warning disappears for clean SRS screenshots/exports.
    await wrapper.get('[data-test="diagram-export-mode"]').trigger('click')
    await flushAsync()
    expect(wrapper.find('[data-test="diagram-warnings"]').exists()).toBe(false)
    expect(wrapper.get('.diagram-panel').attributes('data-export-mode')).toBe('true')

    // Toggling back restores the developer warning.
    await wrapper.get('[data-test="diagram-export-mode"]').trigger('click')
    await flushAsync()
    expect(wrapper.find('[data-test="diagram-warnings"]').exists()).toBe(true)
  })

  it('strips developer controls from the formal export surface', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()

    expect(wrapper.find('[data-test="diagram-export-exit"]').exists()).toBe(false)

    await wrapper.get('[data-test="diagram-export-mode"]').trigger('click')
    await flushAsync()

    expect(wrapper.get('.diagram-panel').classes()).toContain('diagram-panel--export')
    expect(wrapper.find('[data-test="diagram-export-exit"]').exists()).toBe(true)

    await wrapper.get('[data-test="diagram-export-exit"]').trigger('click')
    await flushAsync()
    expect(wrapper.get('.diagram-panel').classes()).not.toContain('diagram-panel--export')
    expect(wrapper.find('[data-test="diagram-export-exit"]').exists()).toBe(false)
  })

  it('switches to class diagram and renders the class response', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())
    classDiagramMock.mockResolvedValueOnce(classResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()
    await wrapper.get('[data-test="diagram-tab-class"]').trigger('click')
    await flushAsync()

    expect(classDiagramMock).toHaveBeenCalledWith('p1', undefined)
    expect(wrapper.text()).toContain('Class')
    expect(wrapper.html()).toContain('OrderService')
  })

  it('passes the package filter to the class endpoint', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())
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
    expect(wrapper.html()).toContain('InvoiceService')
  })

  it('re-renders on refresh even when the source is unchanged (no blank screen)', async () => {
    // Class tab uses the Mermaid render path; refreshing identical content must not blank out.
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())
    classDiagramMock.mockResolvedValue(classResponse())

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()
    await wrapper.get('[data-test="diagram-tab-class"]').trigger('click')
    await flushAsync()
    expect(wrapper.get('[data-test="diagram-stage"]').html()).toContain('<svg')

    await wrapper.get('[data-test="diagram-refresh"]').trigger('click')
    await flushAsync()

    const stage = wrapper.find('[data-test="diagram-stage"]')
    expect(stage.exists()).toBe(true)
    expect(stage.html()).toContain('<svg')
    expect(stage.html()).toContain('OrderService')
  })

  it('shows a friendly PROJECT_NOT_ANALYZED error', async () => {
    umlUseCaseMock.mockRejectedValueOnce(new ApiError(409, 'Conflict', 'PROJECT_NOT_ANALYZED'))

    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })
    await flushAsync()

    expect(wrapper.get('[role="alert"]').text()).toContain('Analyze this project')
  })

  it('shows loading, error, and empty states', async () => {
    const slow = deferred<UmlUseCaseResponse>()
    umlUseCaseMock.mockReturnValueOnce(slow.promise)
    const wrapper = mount(DiagramPanel, { props: { projectId: 'p1' } })

    await nextTick()
    expect(wrapper.get('[role="status"]').text()).toContain('Loading diagram')

    slow.resolve(umlUseCaseResponse({ mermaidSyntax: '' }))
    await flushAsync()
    expect(wrapper.text()).toContain('No diagram content')

    umlUseCaseMock.mockRejectedValueOnce(new Error('network down'))
    await wrapper.get('[data-test="diagram-refresh"]').trigger('click')
    await flushAsync()
    expect(wrapper.get('[role="alert"]').text()).toContain('network down')
  })

  it('cleans up stale responses when the project changes', async () => {
    const oldRequest = deferred<UmlUseCaseResponse>()
    umlUseCaseMock.mockReturnValueOnce(oldRequest.promise)
    umlUseCaseMock.mockResolvedValueOnce(
      umlUseCaseResponse({ systemName: 'Fresh Project', mermaidSyntax: 'flowchart TB\n  fresh' }),
    )

    const wrapper = mount(DiagramPanel, { props: { projectId: 'old-project' } })
    await nextTick()
    await wrapper.setProps({ projectId: 'new-project' })
    await flushAsync()

    oldRequest.resolve(umlUseCaseResponse({ systemName: 'Old Project' }))
    await flushAsync()

    expect(umlUseCaseMock).toHaveBeenLastCalledWith('new-project', 'detailed')
    expect(wrapper.html()).toContain('Fresh Project')
    expect(wrapper.html()).not.toContain('Old Project')
  })
})
