import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type DiagramResponse, type UmlUseCaseResponse } from '@/lib/api'
import { useDiagrams } from '../useDiagrams'

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
    relations: [{ from: 'A_Admin', to: 'UC_ManageProduct', type: 'association', label: null, confidence: 0.8 }],
    warnings: ['Role for POST /api/products inferred from HTTP method.'],
    mermaidSyntax: 'flowchart LR\n  subgraph Orders\n    uc([Manage products])\n  end',
    plantUmlSyntax: '@startuml\nleft to right direction\n@enduml',
    ...overrides,
  }
}

function classResponse(overrides: Partial<DiagramResponse> = {}): DiagramResponse {
  return {
    diagramType: 'class',
    mermaidSyntax: 'classDiagram\n  class OrderService',
    ...overrides,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

beforeEach(() => {
  umlUseCaseMock.mockReset()
  classDiagramMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('useDiagrams', () => {
  it('loads the UML use case diagram and forwards the mode', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse({ mode: 'grouped' }))
    const composable = useDiagrams()

    const result = await composable.loadUmlUseCaseDiagram(' p1 ', 'grouped')

    expect(umlUseCaseMock).toHaveBeenCalledWith('p1', 'grouped')
    expect(result?.kind).toBe('uml')
    const loaded = composable.diagram.value
    if (loaded?.kind !== 'uml') throw new Error('expected a UML diagram to be loaded')
    expect(loaded.plantUmlSyntax).toContain('@startuml')
    expect(loaded.warnings.length).toBeGreaterThan(0)
    expect(composable.status.value).toBe('success')
  })

  it('defaults the UML use case mode to the canonical detailed view', async () => {
    umlUseCaseMock.mockResolvedValueOnce(umlUseCaseResponse())
    const composable = useDiagrams()

    await composable.loadUmlUseCaseDiagram('p1')

    expect(umlUseCaseMock).toHaveBeenCalledWith('p1', 'detailed')
  })

  it('loads a class diagram and forwards the trimmed package filter', async () => {
    classDiagramMock.mockResolvedValueOnce(classResponse())
    const composable = useDiagrams()

    await composable.loadClassDiagram('p1', ' com.example.service ')

    expect(classDiagramMock).toHaveBeenCalledWith('p1', 'com.example.service')
    expect(composable.diagram.value?.mermaidSyntax).toContain('classDiagram')
  })

  it('maps PROJECT_NOT_ANALYZED to a friendly error', async () => {
    umlUseCaseMock.mockRejectedValueOnce(new ApiError(409, 'Conflict', 'PROJECT_NOT_ANALYZED'))
    const composable = useDiagrams()

    const result = await composable.loadUmlUseCaseDiagram('p1')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toContain('Analyze this project')
  })

  it('rejects a blank projectId without calling the API', async () => {
    const composable = useDiagrams()

    const result = await composable.loadClassDiagram('   ')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toContain('project')
    expect(classDiagramMock).not.toHaveBeenCalled()
  })

  it('ignores stale responses after reset', async () => {
    const slow = deferred<UmlUseCaseResponse>()
    umlUseCaseMock.mockReturnValueOnce(slow.promise)
    const composable = useDiagrams()

    const pending = composable.loadUmlUseCaseDiagram('p1')
    expect(composable.status.value).toBe('loading')
    composable.reset()

    slow.resolve(umlUseCaseResponse())
    const result = await pending

    expect(result).toBeNull()
    expect(composable.status.value).toBe('idle')
    expect(composable.diagram.value).toBeNull()
  })
})
