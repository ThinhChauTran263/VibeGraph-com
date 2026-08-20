import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { nextTick } from 'vue'
import AddProjectArchive from '../AddProjectArchive.vue'
import type { Project } from '@/lib/api'
import i18n from '@/language'

/**
 * The component delegates the network call through `useArchiveImport`, which
 * itself imports `importApi.uploadArchive`. Mocking at the api layer keeps
 * the component test free from real fetches and from re-implementing the
 * composable internals.
 */
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    importApi: {
      uploadArchive: vi.fn<(name: string, file: File) => Promise<Project>>(),
      uploadArchiveAsync: vi.fn<(name: string, file: File) => Promise<Project>>(),
    },
    projectApi: {
      ...actual.projectApi,
      get: vi.fn<(projectId: string) => Promise<Project>>(),
    },
  }
})

/**
 * Async mode makes the real `useArchiveImport` open a WebSocket. Mock the
 * transport so the component test never touches SockJS. A hoisted controller
 * lets each test drive connect + status events.
 */
const wsController = vi.hoisted(() => {
  return {
    captured: null as null | { topic: string; cb: (e: unknown) => void },
    connectImpl: async () => {},
  }
})

vi.mock('@/composables/useWebSocket', () => {
  return {
    useWebSocket: () => ({
      status: { value: 'disconnected' },
      error: { value: null },
      connect: vi.fn<() => Promise<void>>(() => wsController.connectImpl()),
      disconnect: vi.fn<() => Promise<void>>(async () => {}),
      subscribe: vi.fn<(topic: string, cb: (e: unknown) => void) => { unsubscribe: () => void }>(
        (topic, cb) => {
          wsController.captured = { topic, cb }
          return { unsubscribe: vi.fn<() => void>() }
        },
      ),
    }),
  }
})

const { importApi, projectApi } = await import('@/lib/api')
const uploadArchiveMock = importApi.uploadArchive as ReturnType<typeof vi.fn>
const uploadArchiveAsyncMock = importApi.uploadArchiveAsync as ReturnType<typeof vi.fn>
const projectGetMock = projectApi.get as ReturnType<typeof vi.fn>

function mountArchive(options: Parameters<typeof mount>[1] = {}) {
  return mount(AddProjectArchive, {
    ...options,
    global: {
      ...options.global,
      plugins: [...(options.global?.plugins ?? []), i18n, createTestingPinia({ createSpy: vi.fn })],
    },
  })
}

function makeFile(name: string, size: number): File {
  const bytes = size > 0 ? new Uint8Array(Math.min(size, 64)) : new Uint8Array(0)
  const file = new File([bytes], name, { type: 'application/octet-stream' })
  if (file.size !== size) {
    Object.defineProperty(file, 'size', { value: size, configurable: true })
  }
  return file
}

function fakeProject(overrides: Partial<Project> = {}): Project {
  return {
    id: 'p-1',
    name: 'demo',
    totalFiles: 0,
    totalNodes: 0,
    totalEdges: 0,
    status: 'ANALYZING',
    ...overrides,
  }
}

beforeEach(() => {
  uploadArchiveMock.mockReset()
  uploadArchiveAsyncMock.mockReset()
  projectGetMock.mockReset()
  // Default poll: report ANALYZING so the WebSocket channel drives outcomes.
  projectGetMock.mockResolvedValue(fakeProject({ status: 'ANALYZING', progress: 0 }))
  wsController.captured = null
  wsController.connectImpl = async () => {}
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('AddProjectArchive', () => {
  it('disables the submit button when neither name nor file are provided', () => {
    const wrapper = mountArchive()
    const submit = wrapper.get('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()
  })

  it('disables the submit button when only the project name is filled', async () => {
    const wrapper = mountArchive()
    await wrapper.get('input[type="text"]').setValue('demo')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('shows a validation error and clears the file when the extension is invalid', async () => {
    const wrapper = mountArchive()
    await wrapper.get('input[type="text"]').setValue('demo')

    const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
    const invalidFile = makeFile('readme.txt', 1024)
    Object.defineProperty(fileInput, 'files', {
      value: [invalidFile],
      configurable: true,
    })
    await wrapper.get('input[type="file"]').trigger('change')

    expect(wrapper.text()).toContain('Unsupported archive type')
    // No file should be considered selected, and the submit button stays disabled.
    expect(wrapper.text()).toContain('No file selected.')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('shows the selected file name and enables submit when name and valid file are present', async () => {
    const wrapper = mountArchive()
    await wrapper.get('input[type="text"]').setValue('demo')

    const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
    const validFile = makeFile('demo.zip', 4096)
    Object.defineProperty(fileInput, 'files', {
      value: [validFile],
      configurable: true,
    })
    await wrapper.get('input[type="file"]').trigger('change')

    expect(wrapper.text()).toContain('demo.zip')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('emits "imported" with the project on a successful upload', async () => {
    const project = fakeProject({ id: 'imp-1', name: 'imp-demo', status: 'ANALYZED' })
    uploadArchiveMock.mockResolvedValueOnce(project)

    const wrapper = mountArchive()
    await wrapper.get('input[type="text"]').setValue('imp-demo')

    const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', {
      value: [makeFile('imp.zip', 1024)],
      configurable: true,
    })
    await wrapper.get('input[type="file"]').trigger('change')

    await wrapper.get('form').trigger('submit.prevent')
    // Allow the awaited mocked promise + state update + DOM patch.
    await nextTick()
    await nextTick()

    expect(uploadArchiveMock).toHaveBeenCalledWith('imp-demo', expect.any(File))
    // Default (sync) mode must NOT touch the async endpoint.
    expect(uploadArchiveAsyncMock).not.toHaveBeenCalled()
    const emitted = wrapper.emitted('imported')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]?.[0]).toEqual(project)
    expect(wrapper.text()).toContain('Imported')
  })
})

describe('AddProjectArchive - async mode', () => {
  async function selectValidFileAndSubmit(asyncMode: boolean) {
    const wrapper = mountArchive({ props: { async: asyncMode } })
    await wrapper.get('input[type="text"]').setValue('async-demo')
    const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', {
      value: [makeFile('async.zip', 1024)],
      configurable: true,
    })
    await wrapper.get('input[type="file"]').trigger('change')
    await wrapper.get('form').trigger('submit.prevent')
    return wrapper
  }

  it('uses the async endpoint when async prop is true', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'a1', status: 'ANALYZING', progress: 0 }),
    )

    const wrapper = await selectValidFileAndSubmit(true)
    await nextTick()
    await nextTick()

    expect(uploadArchiveAsyncMock).toHaveBeenCalledWith('async-demo', expect.any(File))
    expect(uploadArchiveMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('shows a progress bar while analyzing', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'a2', status: 'ANALYZING', progress: 0 }),
    )

    const wrapper = await selectValidFileAndSubmit(true)
    await nextTick()
    await nextTick()

    // Analyzing UI is visible while we wait for status events.
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Analyzing')
    wrapper.unmount()
  })

  it('emits imported and shows success when an ANALYZED event arrives', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'a3', name: 'svc', status: 'ANALYZING', progress: 0 }),
    )

    const wrapper = await selectValidFileAndSubmit(true)
    await nextTick()
    await nextTick()

    // Drive the ANALYZED status event through the mocked WS subscription.
    wsController.captured?.cb({
      projectId: 'a3',
      status: 'ANALYZED',
      progress: 100,
      message: null,
      timestamp: '2026-06-01T00:00:00Z',
    })
    await nextTick()
    await nextTick()

    const emitted = wrapper.emitted('imported')
    expect(emitted).toBeTruthy()
    const project = emitted![0]![0] as Project
    expect(project.id).toBe('a3')
    expect(wrapper.text()).toContain('Imported')
    wrapper.unmount()
  })

  it('shows an error and does not emit imported on a FAILED event', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'a4', status: 'ANALYZING', progress: 0 }),
    )

    const wrapper = await selectValidFileAndSubmit(true)
    await nextTick()
    await nextTick()

    wsController.captured?.cb({
      projectId: 'a4',
      status: 'FAILED',
      progress: 10,
      message: 'No parseable .java files.',
      timestamp: '2026-06-01T00:00:00Z',
    })
    await nextTick()
    await nextTick()

    expect(wrapper.emitted('imported')).toBeFalsy()
    expect(wrapper.text()).toContain('No parseable .java files.')
    wrapper.unmount()
  })

  it('still succeeds via polling when the WebSocket cannot connect', async () => {
    uploadArchiveAsyncMock.mockResolvedValueOnce(
      fakeProject({ id: 'a5', status: 'ANALYZING', progress: 0 }),
    )
    wsController.connectImpl = async () => {
      throw new Error('WebSocket connection failed.')
    }
    // Poll reports ANALYZED so the fallback drives success despite WS failure.
    projectGetMock.mockResolvedValue(fakeProject({ id: 'a5', status: 'ANALYZED', progress: 100 }))

    const wrapper = await selectValidFileAndSubmit(true)
    await nextTick()
    await nextTick()
    await nextTick()

    const emitted = wrapper.emitted('imported')
    expect(emitted).toBeTruthy()
    const project = emitted![0]![0] as Project
    expect(project.id).toBe('a5')
    expect(wrapper.text()).toContain('Imported')
    wrapper.unmount()
  })
})
