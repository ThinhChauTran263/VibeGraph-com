import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type DiagramResponse, type UseCaseResponse } from '@/lib/api'
import { useDiagrams } from '../useDiagrams'

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

function useCaseResponse(overrides: Partial<UseCaseResponse> = {}): UseCaseResponse {
  return {
    projectId: 'p1',
    mermaid: 'flowchart LR\n  user[HTTP Client] --> route[GET /api/orders]',
    ...overrides,
  }
}

function classResponse(overrides: Partial<DiagramResponse> = {}): DiagramResponse {
  return {
    projectId: 'p1',
    diagramType: 'class',
    mermaid: 'classDiagram\n  class OrderService',
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
  useCaseMock.mockReset()
  classDiagramMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('useDiagrams', () => {
  it('loads a use case diagram successfully', async () => {
    useCaseMock.mockResolvedValueOnce(useCaseResponse())
    const composable = useDiagrams()

    const result = await composable.loadUseCaseDiagram(' p1 ')

    expect(useCaseMock).toHaveBeenCalledWith('p1')
    expect(result?.mermaid).toContain('flowchart LR')
    expect(composable.status.value).toBe('success')
    expect(composable.diagram.value?.mermaid).toContain('HTTP Client')
  })

  it('loads a class diagram and forwards the trimmed package filter', async () => {
    classDiagramMock.mockResolvedValueOnce(classResponse())
    const composable = useDiagrams()

    await composable.loadClassDiagram('p1', ' com.example.service ')

    expect(classDiagramMock).toHaveBeenCalledWith('p1', 'com.example.service')
    expect(composable.diagram.value?.mermaid).toContain('classDiagram')
  })

  it('maps PROJECT_NOT_ANALYZED to a friendly error', async () => {
    useCaseMock.mockRejectedValueOnce(new ApiError(409, 'Conflict', 'PROJECT_NOT_ANALYZED'))
    const composable = useDiagrams()

    const result = await composable.loadUseCaseDiagram('p1')

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
    const slow = deferred<UseCaseResponse>()
    useCaseMock.mockReturnValueOnce(slow.promise)
    const composable = useDiagrams()

    const pending = composable.loadUseCaseDiagram('p1')
    expect(composable.status.value).toBe('loading')
    composable.reset()

    slow.resolve(useCaseResponse({ mermaid: 'flowchart LR\n  stale' }))
    const result = await pending

    expect(result).toBeNull()
    expect(composable.status.value).toBe('idle')
    expect(composable.diagram.value).toBeNull()
  })
})
