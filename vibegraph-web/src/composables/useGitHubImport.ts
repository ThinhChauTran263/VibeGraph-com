import { computed, ref } from 'vue'
import { ApiError, importApi, type Project } from '@/lib/api'

const GITHUB_REPO_URL_PATTERN = /^https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+(?:\.git)?\/?$/
const GENERIC_GITHUB_IMPORT_ERROR = 'Import failed. Verify the repository is public and try again.'
const SAFE_ERROR_PATTERNS = [/required/i, /must match/i, /public/i, /private/i, /not found/i, /too large/i, /rate limit/i]

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
      status.value = 'success'
      importedProject.value = project
      return project
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
