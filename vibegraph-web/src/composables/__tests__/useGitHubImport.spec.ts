import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type Project } from '@/lib/api'
import type { GraphData } from '@/types/graph'
import { useGitHubImport, validateGitHubRepoUrl } from '../useGitHubImport'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    fetchFullGraph: vi.fn<typeof actual.fetchFullGraph>(),
    importApi: {
      ...actual.importApi,
      importGithub: vi.fn<(url: string) => Promise<Project>>(),
    },
    projectApi: {
      ...actual.projectApi,
      get: vi.fn<(id: string) => Promise<Project>>(),
    },
  }
})

const { fetchFullGraph, importApi, projectApi } = await import('@/lib/api')
const fetchFullGraphMock = fetchFullGraph as ReturnType<typeof vi.fn>
const importGithubMock = importApi.importGithub as ReturnType<typeof vi.fn>
const getProjectMock = projectApi.get as ReturnType<typeof vi.fn>

function fakeProject(overrides: Partial<Project> = {}): Project {
  return {
    id: 'gh-1',
    name: 'repo',
    totalFiles: 0,
    totalNodes: 0,
    totalEdges: 0,
    status: 'ANALYZING',
    ...overrides,
  }
}

function fakeGraph(overrides: Partial<GraphData> = {}): GraphData {
  return {
    nodes: [
      {
        id: 'node-1',
        type: 'Class',
        name: 'Sample',
        fullName: 'com.example.Sample',
        filePath: 'src/Sample.java',
        lineNumber: 1,
        properties: {},
      },
    ],
    edges: [],
    nodeStats: { Class: 1 } as GraphData['nodeStats'],
    edgeStats: {} as GraphData['edgeStats'],
    ...overrides,
  }
}

beforeEach(() => {
  fetchFullGraphMock.mockReset()
  importGithubMock.mockReset()
  getProjectMock.mockReset()
})

describe('validateGitHubRepoUrl', () => {
  it('accepts public GitHub HTTPS repository URLs', () => {
    expect(validateGitHubRepoUrl('https://github.com/spring-projects/spring-petclinic')).toBeNull()
    expect(validateGitHubRepoUrl('https://github.com/owner/repo.git')).toBeNull()
  })

  it('rejects blank or non-GitHub URLs', () => {
    expect(validateGitHubRepoUrl('   ')).toMatch(/required/i)
    expect(validateGitHubRepoUrl('https://example.com/owner/repo')).toMatch(/github/i)
  })
})

describe('useGitHubImport', () => {
  it('validates URL before calling the API', async () => {
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://example.com/not/github')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toMatch(/github/i)
    expect(importGithubMock).not.toHaveBeenCalled()
  })

  it('imports a valid repo and exposes the returned project', async () => {
    const project = fakeProject({ id: 'gh-2', name: 'spring-petclinic', status: 'ANALYZED' })
    importGithubMock.mockResolvedValueOnce(project)
    const composable = useGitHubImport()

    const result = await composable.importGithub('  https://github.com/spring-projects/spring-petclinic  ')

    expect(importGithubMock).toHaveBeenCalledWith('https://github.com/spring-projects/spring-petclinic')
    expect(result).toEqual(project)
    expect(composable.status.value).toBe('success')
    expect(composable.importedProject.value).toEqual(project)
  })

  it('waits for an async import to finish before exposing success', async () => {
    vi.useFakeTimers()
    const analyzingProject = fakeProject({ id: 'gh-3', name: 'lab7', status: 'ANALYZING', progress: 20 })
    const analyzedProject = fakeProject({
      id: 'gh-3',
      name: 'lab7',
      status: 'ANALYZED',
      progress: 100,
      totalFiles: 24,
      totalNodes: 133,
      totalEdges: 434,
    })
    importGithubMock.mockResolvedValueOnce(analyzingProject)
    getProjectMock.mockResolvedValueOnce(analyzedProject)
    fetchFullGraphMock.mockResolvedValueOnce(fakeGraph())
    const composable = useGitHubImport()

    try {
      const resultPromise = composable.importGithub('https://github.com/owner/lab7')
      await vi.advanceTimersByTimeAsync(1_000)
      const result = await resultPromise

      expect(getProjectMock).toHaveBeenCalledWith('gh-3')
      expect(fetchFullGraphMock).toHaveBeenCalledWith('gh-3')
      expect(result).toEqual(analyzedProject)
      expect(composable.status.value).toBe('success')
      expect(composable.importedProject.value).toEqual(analyzedProject)
    } finally {
      vi.useRealTimers()
    }
  })

  it('maps safe API errors to user-visible error state', async () => {
    importGithubMock.mockRejectedValueOnce(new ApiError(400, 'Bad Request', 'Repository is private.'))
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/private-repo')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe('Repository is private.')
  })

  it('uses a generic message for unexpected API errors', async () => {
    importGithubMock.mockRejectedValueOnce(new ApiError(500, 'Internal Server Error', 'Stack trace: /srv/app/importer'))
    const composable = useGitHubImport()

    const result = await composable.importGithub('https://github.com/owner/repo')

    expect(result).toBeNull()
    expect(composable.status.value).toBe('error')
    expect(composable.errorMessage.value).toBe('Import failed. Verify the repository is public and try again.')
  })
})
