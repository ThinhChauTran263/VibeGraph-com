import { computed, readonly, ref } from 'vue'
import { ApiError, diagramApi, type DiagramResponse, type UseCaseResponse } from '@/lib/api'

export type DiagramKind = 'usecase' | 'class'
export type DiagramStatus = 'idle' | 'loading' | 'success' | 'error'
export type LoadedDiagram = (UseCaseResponse | DiagramResponse) & { kind: DiagramKind }

function mapDiagramError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.message.includes('PROJECT_NOT_ANALYZED')) {
      return 'Analyze this project before opening generated diagrams.'
    }
    if (err.message.includes('PROJECT_NOT_FOUND')) {
      return 'Project not found. Select an existing project and try again.'
    }
    return err.message || `Request failed (${err.status}).`
  }
  if (err instanceof Error && err.message) return err.message
  return 'Failed to load diagram.'
}

export function useDiagrams() {
  const status = ref<DiagramStatus>('idle')
  const diagram = ref<LoadedDiagram | null>(null)
  const errorMessage = ref<string | null>(null)
  const isLoading = computed(() => status.value === 'loading')
  let requestSeq = 0

  async function runLatest<T extends UseCaseResponse | DiagramResponse>(
    projectId: string,
    kind: DiagramKind,
    loader: (trimmedProjectId: string) => Promise<T>,
  ): Promise<LoadedDiagram | null> {
    const trimmedProjectId = projectId?.trim() ?? ''
    if (!trimmedProjectId) {
      status.value = 'error'
      diagram.value = null
      errorMessage.value = 'A project is required to load diagrams.'
      return null
    }

    status.value = 'loading'
    errorMessage.value = null
    const seq = ++requestSeq

    try {
      const data = await loader(trimmedProjectId)
      if (seq !== requestSeq) return null
      const loaded = { ...data, kind }
      diagram.value = loaded
      status.value = 'success'
      return loaded
    } catch (err) {
      if (seq !== requestSeq) return null
      diagram.value = null
      errorMessage.value = mapDiagramError(err)
      status.value = 'error'
      return null
    }
  }

  function loadUseCaseDiagram(projectId: string): Promise<LoadedDiagram | null> {
    return runLatest(projectId, 'usecase', (trimmedProjectId) => diagramApi.useCase(trimmedProjectId))
  }

  function loadClassDiagram(projectId: string, packageFilter?: string): Promise<LoadedDiagram | null> {
    const trimmedPackage = packageFilter?.trim() || undefined
    return runLatest(projectId, 'class', (trimmedProjectId) =>
      diagramApi.classDiagram(trimmedProjectId, trimmedPackage),
    )
  }

  function reset(): void {
    requestSeq++
    status.value = 'idle'
    diagram.value = null
    errorMessage.value = null
  }

  return {
    status: readonly(status),
    diagram: readonly(diagram),
    errorMessage: readonly(errorMessage),
    isLoading,
    loadUseCaseDiagram,
    loadClassDiagram,
    reset,
  }
}
