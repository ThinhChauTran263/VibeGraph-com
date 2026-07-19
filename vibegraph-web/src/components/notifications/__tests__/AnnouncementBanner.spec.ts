import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AnnouncementBanner from '../AnnouncementBanner.vue'
import type { UserNotification } from '@/types/api'
import i18n from '@/language'

const mocks = vi.hoisted(() => ({
  listNotifications: vi.fn(),
  dismissNotification: vi.fn(),
  markNotificationRead: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/lib/api', () => ({
  accountApi: {
    listNotifications: mocks.listNotifications,
    dismissNotification: mocks.dismissNotification,
    markNotificationRead: mocks.markNotificationRead,
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

const announcement: UserNotification = {
  id: 'notification-1',
  announcementId: 'announcement-1',
  title: 'Scheduled maintenance',
  body: 'Realtime updates may pause briefly.',
  creatorName: 'admin',
  creatorDisplayName: 'VibeGraph Admin',
  creatorEmail: null,
  createdAt: '2026-07-18T08:00:00Z',
  severity: 'WARNING',
  type: 'MAINTENANCE',
  dismissible: true,
  read: false,
  readAt: null,
  dismissedAt: null,
}

describe('AnnouncementBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders a retryable error when the initial announcement request fails', async () => {
    mocks.listNotifications
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValueOnce([announcement])

    const wrapper = mount(AnnouncementBanner, { global: { plugins: [i18n] } })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'Announcements are temporarily unavailable.',
    )
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(mocks.listNotifications).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Scheduled maintenance')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})
