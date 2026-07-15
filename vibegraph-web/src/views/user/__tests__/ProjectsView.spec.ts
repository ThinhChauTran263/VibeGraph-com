import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ProjectsView from '../ProjectsView.vue'

const ImportProjectPanelStub = {
  template: '<section data-test="import-panel">Import panel</section>',
}

describe('ProjectsView', () => {
  it('renders import entry point and repositories list correctly', async () => {
    const wrapper = mount(ProjectsView, {
      global: {
        stubs: { ImportProjectPanel: ImportProjectPanelStub },
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                projects: [
                  {
                    id: 'p1',
                    name: 'VibeGraph Web',
                    status: 'ANALYZED',
                    sourceType: 'GITHUB',
                    sizeBytes: 12 * 1024 * 1024,
                    updatedAt: '2023-10-01T12:00:00Z',
                    lastAnalyzedAt: '2023-10-01T12:00:00Z',
                  },
                  {
                    id: 'p2',
                    name: 'VibeGraph CLI',
                    status: 'ANALYZING',
                    sourceType: 'ARCHIVE',
                    sizeBytes: 0,
                    updatedAt: null,
                    lastAnalyzedAt: null,
                  },
                ],
              },
            },
          }),
        ],
      },
    })

    await flushPromises()
    expect(wrapper.find('[data-test="import-panel"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('VibeGraph Web')
    expect(wrapper.text()).toContain('ANALYZED')
    expect(wrapper.text()).toContain('VibeGraph CLI')
    expect(wrapper.text()).toContain('ANALYZING')
  })

  it('shows empty state when no repositories exist', async () => {
    const wrapper = mount(ProjectsView, {
      global: {
        stubs: { ImportProjectPanel: ImportProjectPanelStub },
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                projects: [],
              },
            },
          }),
        ],
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('No repositories found')
  })
})
