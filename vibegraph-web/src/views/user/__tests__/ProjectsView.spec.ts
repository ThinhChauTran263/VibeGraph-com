import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createWebHistory } from 'vue-router'
import ProjectsView from '../ProjectsView.vue'
import { useProjectStore } from '@/stores/project'
import i18n from '@/language'

const apiMocks = vi.hoisted(() => ({
  list: vi.fn(),
  remove: vi.fn(),
  restore: vi.fn<(id: string) => Promise<void>>(),
}))
const featureMocks = vi.hoisted(() => ({
  enabled: true,
  reason: null as string | null,
}))

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    projectApi: {
      ...actual.projectApi,
      list: apiMocks.list,
      remove: apiMocks.remove,
      restore: apiMocks.restore,
    },
  }
})

vi.mock('@/lib/featureAvailability', async () => {
  const { computed, ref } = await import('vue')
  return {
    featureAvailabilityContract: ref(true),
    refreshFeatureAvailability: vi.fn().mockResolvedValue(undefined),
    useFeatureAvailability: (key: string) => computed(() => ({
      key,
      enabled: featureMocks.enabled,
      reason: featureMocks.reason,
    })),
  }
})

const ImportProjectPanelStub = {
  props: ['disabledMethods'],
  emits: ['imported'],
  template: `
    <section data-test="import-panel">
      Import panel
      <button
        data-test="complete-cli-import"
        @click="$emit('imported', {
          id: 'cli-new',
          name: 'New CLI Repo',
          totalFiles: 0,
          totalNodes: 0,
          totalEdges: 0,
          status: 'CREATED'
        })"
      >Complete CLI import</button>
    </section>
  `,
}
const ConfirmDialogStub = {
  props: ['open', 'busy'],
  emits: ['confirm', 'cancel'],
  template: `
    <div v-if="open">
      <button data-test="confirm-delete" @click="$emit('confirm')">Confirm</button>
      <button data-test="cancel-delete" @click="$emit('cancel')">Cancel</button>
    </div>
  `,
}

function makeProject(id: string, name: string) {
  return {
    id,
    name,
    createdAt: '2026-07-17T10:00:00Z',
    lastAnalyzedAt: '2026-07-17T11:00:00Z',
    totalFiles: 24,
    totalNodes: 180,
    totalEdges: 320,
    status: 'ANALYZED',
  }
}

async function mountView(path = '/projects', pinia = createTestingPinia({ createSpy: vi.fn })) {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/projects', name: 'projects', component: ProjectsView },
      { path: '/projects/:projectId/graph', name: 'graph', component: { template: '<div />' } },
      { path: '/trash', name: 'trash', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ProjectsView, {
    global: {
      plugins: [router, pinia, i18n],
      stubs: {
        ImportProjectPanel: ImportProjectPanelStub,
        AdminConfirmDialog: ConfirmDialogStub,
      },
    },
  })
  await flushPromises()
  return { wrapper, router, pinia }
}

describe('ProjectsView', () => {
  beforeEach(() => {
    apiMocks.list.mockReset()
    apiMocks.remove.mockReset()
    apiMocks.restore.mockReset()
    apiMocks.restore.mockResolvedValue(undefined)
    featureMocks.enabled = true
    featureMocks.reason = null
    apiMocks.list.mockResolvedValue([
      makeProject('project-1', 'VibeGraph Web'),
      makeProject('project-2', 'VibeGraph CLI'),
    ])
  })

  it('lists imported repositories before revealing the import form', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('section[aria-label="Imported repositories"]').text()).toContain(
      'VibeGraph Web',
    )
    expect(wrapper.find('[data-test="import-panel"]').exists()).toBe(false)

    await wrapper.get('button[data-test="new-repository"]').trigger('click')
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.get('[data-test="import-panel"]').text()).toContain('Import panel')
  })

  it('reconciles the repository list with the server when returning to the page', async () => {
    const { wrapper, pinia } = await mountView()
    const projectStore = useProjectStore()

    apiMocks.list.mockClear()
    wrapper.unmount()
    const cached = await mountView('/projects', pinia)

    // Cache renders instantly, but the page always re-fetches so background
    // imports / CLI pushes appear without a full page reload.
    expect(projectStore.projectsLoaded).toBe(true)
    expect(apiMocks.list).toHaveBeenCalledTimes(1)
    expect(cached.wrapper.text()).toContain('VibeGraph Web')
  })

  it('shows a background-imported project on the next visit without a reload', async () => {
    const { wrapper, pinia } = await mountView()
    wrapper.unmount()
    apiMocks.list.mockResolvedValueOnce([
      makeProject('project-1', 'VibeGraph Web'),
      makeProject('project-2', 'VibeGraph CLI'),
      { ...makeProject('project-3', 'Pushed Via CLI'), status: 'ANALYZING' },
    ])

    const cached = await mountView('/projects', pinia)

    expect(cached.wrapper.text()).toContain('Pushed Via CLI')
  })

  it('reacts to the quick-action import query while already mounted', async () => {
    const { wrapper, router } = await mountView()

    await router.push({ name: 'projects', query: { import: 'new' } })
    await flushPromises()
    expect(wrapper.get('[role="dialog"] [data-test="import-panel"]').text()).toContain(
      'Import panel',
    )

    await router.push({ name: 'projects' })
    await flushPromises()
    expect(wrapper.find('[data-test="import-panel"]').exists()).toBe(false)
  })

  it('opens an imported repository in the graph view', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('button[data-test="open-project-project-1"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('graph')
    expect(router.currentRoute.value.params.projectId).toBe('project-1')
  })

  it('returns a newly created CLI repository to the repository list instead of an empty graph', async () => {
    const { wrapper, router } = await mountView('/projects?import=new')

    await wrapper.get('[data-test="complete-cli-import"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('projects')
    expect(router.currentRoute.value.query).toEqual({})
    expect(wrapper.text()).toContain('New CLI Repo')
  })

  it('shows the current empty and error states', async () => {
    apiMocks.list.mockResolvedValueOnce([])
    const empty = await mountView()
    expect(empty.wrapper.text()).toContain('No repositories yet')

    apiMocks.list.mockRejectedValueOnce(new Error('Repository service unavailable'))
    const failed = await mountView()
    expect(failed.wrapper.get('[role="alert"]').text()).toContain('Repository service unavailable')
  })

  it('removes the intended repository even if cancel is emitted while deletion is pending', async () => {
    let resolveDelete: (() => void) | undefined
    apiMocks.remove.mockImplementationOnce(
      () => new Promise<void>((resolve) => {
        resolveDelete = resolve
      }),
    )
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Delete VibeGraph Web"]').trigger('click')
    await wrapper.get('[data-test="confirm-delete"]').trigger('click')
    await wrapper.get('[data-test="cancel-delete"]').trigger('click')
    resolveDelete?.()
    await flushPromises()

    // The card is gone from the grid; the name survives only in the undo bar.
    expect(wrapper.findAll('.repo-card').map((card) => card.text())).toHaveLength(1)
    expect(wrapper.get('.repo-card').text()).toContain('VibeGraph CLI')
    expect(wrapper.get('[data-test="undo-delete"]').text()).toContain('VibeGraph Web')
  })

  it('undoes a delete and puts the repository back in the list', async () => {
    apiMocks.remove.mockResolvedValue(undefined)
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Delete VibeGraph Web"]').trigger('click')
    await wrapper.get('[data-test="confirm-delete"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-test="undo-delete"] .undo-bar__undo').trigger('click')
    await flushPromises()

    expect(apiMocks.restore).toHaveBeenCalledWith('project-1')
    expect(wrapper.find('[data-test="undo-delete"]').exists()).toBe(false)
    expect(wrapper.findAll('.repo-card').map((card) => card.text()).join(' ')).toContain(
      'VibeGraph Web',
    )
  })

  it('keeps the undo bar and surfaces the reason when the restore fails', async () => {
    apiMocks.remove.mockResolvedValue(undefined)
    apiMocks.restore.mockRejectedValueOnce(new Error('Trashed project not found'))
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Delete VibeGraph Web"]').trigger('click')
    await wrapper.get('[data-test="confirm-delete"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-test="undo-delete"] .undo-bar__undo').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Trashed project not found')
    expect(wrapper.find('[data-test="undo-delete"]').exists()).toBe(true)
  })

  it('shows the live analyzing state instead of stats while a project analyzes', async () => {
    apiMocks.list.mockResolvedValue([
      { ...makeProject('project-1', 'Analyzing Repo'), status: 'ANALYZING', totalFiles: 0, totalNodes: 0 },
      makeProject('project-2', 'Done Repo'),
    ])
    const { wrapper } = await mountView()
    const cards = wrapper.findAll('.repo-card')
    const analyzing = cards[0]!
    const done = cards[1]!

    // Analyzing card: brand loader + live progress, no stats and no status pill.
    expect(analyzing.find('.repo-card__spinner').exists()).toBe(true)
    expect(analyzing.find('.repo-card__live').exists()).toBe(true)
    expect(analyzing.find('dl').exists()).toBe(false)
    expect(analyzing.find('.status').exists()).toBe(false)

    // Analyzed card: status pill + stats, no loader.
    expect(done.find('.repo-card__spinner').exists()).toBe(false)
    expect(done.find('.repo-card__live').exists()).toBe(false)
    expect(done.find('dl').exists()).toBe(true)
    expect(done.get('.status').text()).toContain('ANALYZED')
  })

  it('fails closed when import capabilities are unavailable', async () => {
    featureMocks.enabled = false
    featureMocks.reason = 'Capability contract unavailable.'
    const { wrapper, router } = await mountView('/projects?import=new')

    expect(wrapper.get('button[data-test="new-repository"]').attributes()).toHaveProperty('disabled')
    expect(wrapper.text()).toContain('Project import is blocked')
    expect(wrapper.find('[data-test="import-panel"]').exists()).toBe(false)
    await router.push({ name: 'projects', query: { import: 'new' } })
    await flushPromises()
    expect(wrapper.find('[data-test="import-panel"]').exists()).toBe(false)
  })
})
