import { computed, ref } from 'vue'
import { ApiError, fetchFullGraph, importApi, projectApi, type Project } from '@/lib/api'

const GITHUB_REPO_URL_PATTERN = /^https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+(?:\.git)?\/?$/
const GENERIC_GITHUB_IMPORT_ERROR = 'Import failed. Verify the repository is public and try again.'
const SAFE_ERROR_PATTERNS = [
  /required/i,
  /must match/i,
  /public/i,
  /private/i,
  /not found/i,
  /too large/i,
  /rate limit/i,
  /still analyzing/i,
]
const GITHUB_IMPORT_POLL_INTERVAL_MS = 1_000
const GITHUB_IMPORT_MAX_POLLS = 60

export type GitHubImportStatus = 'idle' | 'importing' | 'success' | 'error'

function getGitHubImportError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return GENERIC_GITHUB_IMPORT_ERROR
  }

  const message = error.message.trim()
  if (!message) {
    return GENERIC_GITHUB_IMPORT_ERROR
  }

  return SAFE_ERROR_PATTERNS.some((pattern) => pattern.test(message)) ? message : GENERIC_GITHUB_IMPORT_ERROR
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

async function waitForGraphData(project: Project): Promise<void> {
  for (let attempt = 0; attempt < GITHUB_IMPORT_MAX_POLLS; attempt += 1) {
    const graph = await fetchFullGraph(project.id)
    if (graph.nodes.length > 0 || graph.edges.length > 0) {
      return
    }

    await delay(GITHUB_IMPORT_POLL_INTERVAL_MS)
  }

  throw new ApiError(408, 'Import Timeout', 'Import is still analyzing. Try opening the project again in a moment.')
}

async function waitForGitHubAnalysis(project: Project): Promise<Project> {
  if (project.status !== 'ANALYZING') {
    return project
  }

  for (let attempt = 0; attempt < GITHUB_IMPORT_MAX_POLLS; attempt += 1) {
    await delay(GITHUB_IMPORT_POLL_INTERVAL_MS)
    const latestProject = await projectApi.get(project.id)

    if (latestProject.status === 'ANALYZED') {
      await waitForGraphData(latestProject)
      return latestProject
    }

    if (latestProject.status === 'FAILED') {
      throw new ApiError(400, 'Import Failed', 'Import failed. Verify the repository is public and try again.')
    }
  }

  throw new ApiError(408, 'Import Timeout', 'Import is still analyzing. Try opening the project again in a moment.')
}

export function validateGitHubRepoUrl(url: string): string | null {
  const trimmed = url.trim()
  if (!trimmed) {
    return 'GitHub repository URL is required.'
  }

  if (!GITHUB_REPO_URL_PATTERN.test(trimmed)) {
    return 'URL must match https://github.com/{owner}/{repo}.'
  }

  return null
}

export function useGitHubImport() {
  const status = ref<GitHubImportStatus>('idle')
  const errorMessage = ref<string | null>(null)
  const importedProject = ref<Project | null>(null)

  const isImporting = computed(() => status.value === 'importing')

  async function importGithub(url: string): Promise<Project | null> {
    const trimmedUrl = url.trim()
    const validationError = validateGitHubRepoUrl(trimmedUrl)
    if (validationError) {
      status.value = 'error'
      errorMessage.value = validationError
      importedProject.value = null
      return null
    }

    status.value = 'importing'
    errorMessage.value = null
    importedProject.value = null

    try {
      const project = await importApi.importGithub(trimmedUrl)
      const analyzedProject = await waitForGitHubAnalysis(project)
      status.value = 'success'
      importedProject.value = analyzedProject
      return analyzedProject
    } catch (error: unknown) {
      status.value = 'error'
      errorMessage.value = getGitHubImportError(error)
      importedProject.value = null
      return null
    }
  }

  function reset(): void {
    status.value = 'idle'
    errorMessage.value = null
    importedProject.value = null
  }

  return {
    status,
    errorMessage,
    importedProject,
    isImporting,
    importGithub,
    reset,
  }
}
