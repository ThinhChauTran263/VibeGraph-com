import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import GitHubImportForm from '../GitHubImportForm.vue'
import type { Project } from '@/lib/api'

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

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

beforeEach(() => {
  importGithubMock.mockReset()
})

describe('GitHubImportForm', () => {
  it('keeps submit disabled until a URL is entered', () => {
    const wrapper = mount(GitHubImportForm)

    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('submits a GitHub URL and emits the imported project', async () => {
    const project = fakeProject({ id: 'gh-2', name: 'spring-petclinic' })
    importGithubMock.mockResolvedValueOnce(project)
    const wrapper = mount(GitHubImportForm)

    await wrapper.get('input[type="url"]').setValue('https://github.com/spring-projects/spring-petclinic')
    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    await nextTick()

    expect(importGithubMock).toHaveBeenCalledWith('https://github.com/spring-projects/spring-petclinic')
    const emitted = wrapper.emitted('imported')
    expect(emitted).toBeTruthy()
    expect(emitted![0]![0]).toEqual(project)
    expect(wrapper.text()).toContain('Import started')
  })

  it('shows validation errors without calling the API', async () => {
    const wrapper = mount(GitHubImportForm)

    await wrapper.get('input[type="url"]').setValue('https://example.com/owner/repo')
    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()

    expect(importGithubMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('URL must match')
  })

  it('disables controls and shows importing state while the request is pending', async () => {
    const pending = deferred<Project>()
    importGithubMock.mockReturnValueOnce(pending.promise)
    const wrapper = mount(GitHubImportForm)

    await wrapper.get('input[type="url"]').setValue('https://github.com/owner/repo')
    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()

    expect(wrapper.get('input[type="url"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[type="button"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Importing...')

    pending.resolve(fakeProject())
    await flushPromises()
  })

  it('reset clears the current URL and status message', async () => {
    const wrapper = mount(GitHubImportForm)

    await wrapper.get('input[type="url"]').setValue('https://example.com/owner/repo')
    await wrapper.get('form').trigger('submit.prevent')
    await nextTick()
    expect(wrapper.text()).toContain('URL must match')

    await wrapper.get('button[type="button"]').trigger('click')
    await nextTick()

    expect((wrapper.get('input[type="url"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.text()).not.toContain('URL must match')
  })
})
