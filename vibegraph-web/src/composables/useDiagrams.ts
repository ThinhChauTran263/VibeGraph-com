import { computed, readonly, ref } from 'vue'
import {
  ApiError,
  diagramApi,
  type DiagramResponse,
  type UmlUseCaseMode,
  type UmlUseCaseResponse,
} from '@/lib/api'
import { graphVersion } from '@/lib/graphVersion'

export type DiagramKind = 'uml' | 'class'
export type DiagramStatus = 'idle' | 'loading' | 'success' | 'error'
export type LoadedDiagram =
  | (UmlUseCaseResponse & { kind: 'uml' })
  | (DiagramResponse & { kind: 'class' })

interface CacheEntry {
  data: LoadedDiagram
  /** The graphVersion this diagram was built from; stale once graphVersion moves past it. */
  version: number
}

/**
 * App-wide diagram cache keyed by `projectId::kind::variant` (variant = UML mode or
 * class package filter). Module-level so it survives component remounts and tab
 * switches: re-opening a diagram or flipping UML<->Class is instant and skips the
 * network, while a graph change (graphVersion bump) marks entries stale for refetch.
 */
const diagramCache = new Map<string, CacheEntry>()

/** Test seam: drop all cached diagrams so unit tests start from a clean slate. */
export function clearDiagramCache(): void {
  diagramCache.clear()
}

function cacheKey(projectId: string, kind: DiagramKind, variant: string): string {
  return `${projectId}::${kind}::${variant}`
}

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

export interface DiagramLoadOptions {
  /** Bypass the cache and force a fresh fetch (e.g. the explicit Refresh button). */
  force?: boolean
}

export function useDiagrams() {
  const status = ref<DiagramStatus>('idle')
  const diagram = ref<LoadedDiagram | null>(null)
  const errorMessage = ref<string | null>(null)
  const isLoading = computed(() => status.value === 'loading')
  // graphVersion the currently-shown diagram was built from; drives staleness checks.
  const currentVersion = ref(-1)
  /** True when the shown diagram predates the latest graph change (should revalidate). */
  const isStale = computed(() => status.value === 'success' && currentVersion.value < graphVersion.value)
  let requestSeq = 0

  async function runWithCache(
    projectId: string,
    kind: DiagramKind,
    variant: string,
    loader: (trimmedProjectId: string) => Promise<Omit<LoadedDiagram, 'kind'>>,
    force: boolean,
  ): Promise<LoadedDiagram | null> {
    const trimmedProjectId = projectId?.trim() ?? ''
    if (!trimmedProjectId) {
      status.value = 'error'
      diagram.value = null
      errorMessage.value = 'A project is required to load diagrams.'
      return null
    }

    const key = cacheKey(trimmedProjectId, kind, variant)

    // Fresh cache hit: serve instantly, no network. Stale entries fall through to a fetch.
    const cached = diagramCache.get(key)
    if (!force && cached && cached.version >= graphVersion.value) {
      diagram.value = cached.data
      currentVersion.value = cached.version
      status.value = 'success'
      errorMessage.value = null
      return cached.data
    }

    status.value = 'loading'
    errorMessage.value = null
    const seq = ++requestSeq
    const versionAtFetch = graphVersion.value

    try {
      const data = await loader(trimmedProjectId)
      if (seq !== requestSeq) return null
      const loaded = { ...data, kind } as LoadedDiagram
      diagramCache.set(key, { data: loaded, version: versionAtFetch })
      diagram.value = loaded
      currentVersion.value = versionAtFetch
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
    options: DiagramLoadOptions = {},
  ): Promise<LoadedDiagram | null> {
    return runWithCache(
      projectId,
      'uml',
      mode,
      (trimmedProjectId) => diagramApi.umlUseCase(trimmedProjectId, mode),
      options.force ?? false,
    )
  }

  function loadClassDiagram(
    projectId: string,
    packageFilter?: string,
    options: DiagramLoadOptions = {},
  ): Promise<LoadedDiagram | null> {
    const trimmedPackage = packageFilter?.trim() || undefined
    return runWithCache(
      projectId,
      'class',
      trimmedPackage ?? '',
      (trimmedProjectId) => diagramApi.classDiagram(trimmedProjectId, trimmedPackage),
      options.force ?? false,
    )
  }

  function reset(): void {
    requestSeq++
    status.value = 'idle'
    diagram.value = null
    errorMessage.value = null
    currentVersion.value = -1
  }

  return {
    status: readonly(status),
    diagram: readonly(diagram),
    errorMessage: readonly(errorMessage),
    isLoading,
    isStale,
    loadUmlUseCaseDiagram,
    loadClassDiagram,
    reset,
  }
}
