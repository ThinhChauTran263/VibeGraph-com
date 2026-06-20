import { computed, readonly, ref } from 'vue'
import {
  ApiError,
  diagramApi,
  type DiagramResponse,
  type UmlUseCaseMode,
  type UmlUseCaseResponse,
} from '@/lib/api'

export type DiagramKind = 'uml' | 'class'
export type DiagramStatus = 'idle' | 'loading' | 'success' | 'error'
export type LoadedDiagram =
  | (UmlUseCaseResponse & { kind: 'uml' })
  | (DiagramResponse & { kind: 'class' })

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

  async function runLatest<T extends LoadedDiagram>(
    projectId: string,
    kind: T['kind'],
    loader: (trimmedProjectId: string) => Promise<Omit<T, 'kind'>>,
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
      const loaded = { ...data, kind } as T
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

  function loadUmlUseCaseDiagram(
    projectId: string,
    mode: UmlUseCaseMode = 'detailed',
  ): Promise<LoadedDiagram | null> {
    return runLatest<UmlUseCaseResponse & { kind: 'uml' }>(projectId, 'uml', (trimmedProjectId) =>
      diagramApi.umlUseCase(trimmedProjectId, mode),
    )
  }

  function loadClassDiagram(projectId: string, packageFilter?: string): Promise<LoadedDiagram | null> {
    const trimmedPackage = packageFilter?.trim() || undefined
    return runLatest<DiagramResponse & { kind: 'class' }>(projectId, 'class', (trimmedProjectId) =>
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
    loadUmlUseCaseDiagram,
    loadClassDiagram,
    reset,
  }
}
