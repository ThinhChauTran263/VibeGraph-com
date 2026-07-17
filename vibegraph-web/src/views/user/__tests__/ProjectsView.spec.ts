import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createWebHistory } from 'vue-router'
import ProjectsView from '../ProjectsView.vue'

const apiMocks = vi.hoisted(() => ({
  list: vi.fn(),
  remove: vi.fn(),
}))

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    projectApi: { ...actual.projectApi, list: apiMocks.list, remove: apiMocks.remove },
  }
})

vi.mock('@/lib/featureAvailability', async () => {
  const { computed, ref } = await import('vue')
  return {
    featureAvailabilityContract: ref(true),
    refreshFeatureAvailability: vi.fn().mockResolvedValue(undefined),
    useFeatureAvailability: (key: string) => computed(() => ({ key, enabled: true, reason: null })),
  }
})

const ImportProjectPanelStub = {
  props: ['disabledMethods'],
  emits: ['imported'],
  template: '<section data-test="import-panel">Import panel</section>',
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

async function mountView(path = '/projects') {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/projects', name: 'projects', component: ProjectsView },
      { path: '/projects/:projectId/graph', name: 'graph', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ProjectsView, {
    global: {
      plugins: [router, createTestingPinia({ createSpy: vi.fn })],
      stubs: {
        ImportProjectPanel: ImportProjectPanelStub,
        AdminConfirmDialog: true,
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('ProjectsView', () => {
  beforeEach(() => {
    apiMocks.list.mockReset()
    apiMocks.remove.mockReset()
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
    expect(wrapper.get('[data-test="import-panel"]').text()).toContain('Import panel')
    expect(
      wrapper.get('section[aria-label="Imported repositories"]').element.compareDocumentPosition(
        wrapper.get('[data-test="import-panel"]').element,
      ) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
  })

  it('reacts to the quick-action import query while already mounted', async () => {
    const { wrapper, router } = await mountView()

    await router.push({ name: 'projects', query: { import: 'new' } })
    await flushPromises()
    expect(wrapper.get('[data-test="import-panel"]').text()).toContain('Import panel')

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

  it('shows the current empty and error states', async () => {
    apiMocks.list.mockResolvedValueOnce([])
    const empty = await mountView()
    expect(empty.wrapper.text()).toContain('No repositories yet')

    apiMocks.list.mockRejectedValueOnce(new Error('Repository service unavailable'))
    const failed = await mountView()
    expect(failed.wrapper.get('[role="alert"]').text()).toContain('Repository service unavailable')
  })
})
