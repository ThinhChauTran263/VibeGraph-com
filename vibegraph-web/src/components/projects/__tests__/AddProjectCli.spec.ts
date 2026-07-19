import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import AddProjectCli from '../AddProjectCli.vue'
import type { CliRepositorySetup, Project } from '@/lib/api'
import { useAccountStore } from '@/stores/account'
import i18n from '@/language'

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    importApi: {
      ...actual.importApi,
      createCliRepository: vi.fn<(name?: string) => Promise<CliRepositorySetup>>(),
    },
  }
})

const { importApi } = await import('@/lib/api')
const createCliRepositoryMock = importApi.createCliRepository as ReturnType<typeof vi.fn>

function fakeProject(overrides: Partial<Project> = {}): Project {
  return {
    id: 'cli-1',
    name: 'CLI Repo',
    totalFiles: 0,
    totalNodes: 0,
    totalEdges: 0,
    status: 'CREATED',
    ...overrides,
  }
}

function setupResponse(): CliRepositorySetup {
  return {
    project: fakeProject(),
    apiKey: {
      id: 'key-1',
      keyPrefix: 'vbg_abcd1234',
      name: 'CLI Repo CLI',
      project: { id: 'cli-1', name: 'CLI Repo', sourceType: 'LOCAL', status: 'ANALYZING' },
      secretKey: 'vbg_fullsecret',
      createdAt: '2026-07-20T00:00:00Z',
      expiresAt: null,
    },
    commands: ['vibegraph login vbg_fullsecret', 'vibegraph push', 'vibegraph watch'],
  }
}

function mountForm() {
  const pinia = createTestingPinia({ createSpy: vi.fn })
  return {
    wrapper: mount(AddProjectCli, { global: { plugins: [pinia, i18n] } }),
    account: useAccountStore(pinia),
  }
}

beforeEach(() => {
  createCliRepositoryMock.mockReset()
})

describe('AddProjectCli', () => {
  it('creates a CLI repository and keeps the one-time secret visible', async () => {
    createCliRepositoryMock.mockResolvedValueOnce(setupResponse())
    const { wrapper, account } = mountForm()

    await wrapper.get('input[name="repositoryName"]').setValue('CLI Repo')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()

    expect(createCliRepositoryMock).toHaveBeenCalledWith('CLI Repo')
    expect(wrapper.text()).toContain('vbg_fullsecret')
    expect(wrapper.text()).toContain('vibegraph push')
    expect(wrapper.emitted('imported')).toBeFalsy()
    expect(account.fetchProjects).toHaveBeenCalledWith({ force: true })
    expect(account.fetchApiKeys).toHaveBeenCalledWith({ force: true })
  })

  it('emits the project only when the user opens the repository', async () => {
    const response = setupResponse()
    createCliRepositoryMock.mockResolvedValueOnce(response)
    const { wrapper } = mountForm()

    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('Open repository'))!.trigger('click')

    expect(wrapper.emitted('imported')?.[0]?.[0]).toEqual(response.project)
  })
})
