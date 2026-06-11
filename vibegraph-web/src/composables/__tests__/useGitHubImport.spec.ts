import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type Project } from '@/lib/api'
import { useGitHubImport, validateGitHubRepoUrl } from '../useGitHubImport'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    importApi: {
      ...actual.importApi,
      importGithub: vi.fn<(url: string) => Promise<Project>>(),
    },
  }
})

const { importApi } = await import('@/lib/api')
const importGithubMock = importApi.importGithub as ReturnType<typeof vi.fn>

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

beforeEach(() => {
  importGithubMock.mockReset()
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
    const project = fakeProject({ id: 'gh-2', name: 'spring-petclinic' })
    importGithubMock.mockResolvedValueOnce(project)
    const composable = useGitHubImport()

    const result = await composable.importGithub('  https://github.com/spring-projects/spring-petclinic  ')

    expect(importGithubMock).toHaveBeenCalledWith('https://github.com/spring-projects/spring-petclinic')
    expect(result).toEqual(project)
    expect(composable.status.value).toBe('success')
    expect(composable.importedProject.value).toEqual(project)
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
