import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import TrashView from '../TrashView.vue'
import i18n from '@/language'

const apiMocks = vi.hoisted(() => ({
  list: vi.fn<() => Promise<unknown[]>>(),
  trash: vi.fn<() => Promise<unknown[]>>(),
  restore: vi.fn<(id: string) => Promise<void>>(),
  purge: vi.fn<(id: string) => Promise<void>>(),
}))

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api')
  return {
    ...actual,
    projectApi: { ...actual.projectApi, ...apiMocks },
  }
})

const ConfirmDialogStub = {
  props: ['open', 'busy'],
  emits: ['confirm', 'cancel'],
  template: `
    <div v-if="open">
      <button data-test="confirm-purge" @click="$emit('confirm')">Confirm</button>
      <button data-test="cancel-purge" @click="$emit('cancel')">Cancel</button>
    </div>
  `,
}

function trashed(id: string, name: string, daysRemaining: number) {
  return {
    id,
    name,
    sourceType: 'GITHUB',
    sizeBytes: 5_242_880,
    deletedAt: '2026-08-09T10:00:00Z',
    purgeAt: '2026-08-12T10:00:00Z',
    daysRemaining,
  }
}

async function mountView() {
  const wrapper = mount(TrashView, {
    global: {
      plugins: [createTestingPinia({ createSpy: vi.fn }), i18n],
      stubs: { AdminConfirmDialog: ConfirmDialogStub },
    },
  })
  await flushPromises()
  return wrapper
}

describe('TrashView', () => {
  beforeEach(() => {
    apiMocks.list.mockReset().mockResolvedValue([])
    apiMocks.trash.mockReset()
    apiMocks.restore.mockReset().mockResolvedValue(undefined)
    apiMocks.purge.mockReset().mockResolvedValue(undefined)
    apiMocks.trash.mockResolvedValue([
      trashed('project-1', 'acme/widgets', 2),
      trashed('project-2', 'acme/gadgets', 0),
    ])
  })

  it('lists trashed repositories with the retention countdown', async () => {
    const wrapper = await mountView()

    expect(wrapper.get('[data-test="trash-project-1"]').text()).toContain('acme/widgets')
    expect(wrapper.get('[data-test="trash-project-1"]').text()).toContain('in 2 days')
    // Zero days left must read as "next sweep", never as a negative countdown.
    expect(wrapper.get('[data-test="trash-project-2"]').text()).toContain('on the next sweep')
  })

  it('shows the empty state when nothing is in trash', async () => {
    apiMocks.trash.mockResolvedValueOnce([])
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Trash is empty')
  })

  it('surfaces a load failure', async () => {
    apiMocks.trash.mockRejectedValueOnce(new Error('Trash service unavailable'))
    const wrapper = await mountView()

    expect(wrapper.get('[role="alert"]').text()).toContain('Trash service unavailable')
  })

  it('restores a repository and drops it from the trash list', async () => {
    const wrapper = await mountView()

    await wrapper.get('button[aria-label="Restore acme/widgets"]').trigger('click')
    await flushPromises()

    expect(apiMocks.restore).toHaveBeenCalledWith('project-1')
    expect(wrapper.find('[data-test="trash-project-1"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="trash-project-2"]').exists()).toBe(true)
  })

  it('keeps the entry and reports the reason when a restore fails', async () => {
    apiMocks.restore.mockRejectedValueOnce(new Error('Trashed project not found'))
    const wrapper = await mountView()

    await wrapper.get('button[aria-label="Restore acme/widgets"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Trashed project not found')
    expect(wrapper.find('[data-test="trash-project-1"]').exists()).toBe(true)
  })

  it('permanently deletes only after the confirmation is accepted', async () => {
    const wrapper = await mountView()

    await wrapper.get('button[aria-label="Permanently delete acme/widgets"]').trigger('click')
    await wrapper.get('[data-test="cancel-purge"]').trigger('click')
    expect(apiMocks.purge).not.toHaveBeenCalled()

    await wrapper.get('button[aria-label="Permanently delete acme/widgets"]').trigger('click')
    await wrapper.get('[data-test="confirm-purge"]').trigger('click')
    await flushPromises()

    expect(apiMocks.purge).toHaveBeenCalledWith('project-1')
    expect(wrapper.find('[data-test="trash-project-1"]').exists()).toBe(false)
  })

  it('keeps the entry and reports the reason when a permanent delete fails', async () => {
    apiMocks.purge.mockRejectedValueOnce(new Error('Administrator-locked API key'))
    const wrapper = await mountView()

    await wrapper.get('button[aria-label="Permanently delete acme/widgets"]').trigger('click')
    await wrapper.get('[data-test="confirm-purge"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Administrator-locked API key')
    expect(wrapper.find('[data-test="trash-project-1"]').exists()).toBe(true)
  })
})
