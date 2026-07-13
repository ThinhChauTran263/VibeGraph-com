import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ProjectsView from '../ProjectsView.vue'

describe('ProjectsView', () => {
  it('renders projects list correctly', async () => {
    const wrapper = mount(ProjectsView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                projects: [
                  { id: 'p1', name: 'VibeGraph Web', status: 'active', lastAnalyzedAt: '2023-10-01T12:00:00Z' },
                  { id: 'p2', name: 'VibeGraph CLI', status: 'pending', lastAnalyzedAt: null }
                ]
              }
            }
          })
        ]
      }
    })

    await flushPromises()
    expect(wrapper.text()).toContain('VibeGraph Web')
    expect(wrapper.text()).toContain('active')
    expect(wrapper.text()).toContain('VibeGraph CLI')
    expect(wrapper.text()).toContain('pending')
  })

  it('shows empty state when no projects', async () => {
    const wrapper = mount(ProjectsView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              account: {
                projects: []
              }
            }
          })
        ]
      }
    })

    await flushPromises()
    expect(wrapper.text()).toContain('No projects found')
  })
})
