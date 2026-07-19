import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { nextTick, ref } from 'vue'
import AdminReportsView from '../AdminReportsView.vue'
import { useAdminStore } from '@/stores/admin'
import type { AdminReport, ReportRealtimeEvent } from '@/types/api'
import i18n, { setLocale } from '@/language'

const realtime = vi.hoisted(() => ({
  emit: null as ((event: ReportRealtimeEvent) => void) | null,
  status: null as unknown as ReturnType<typeof ref<string>>,
  active: null as unknown as ReturnType<typeof ref<boolean>>,
}))

vi.mock('@/composables/useReportRealtime', () => ({
  useReportRealtime: (
    _reportId: unknown,
    options: { onEvent?: (event: ReportRealtimeEvent) => void } = {},
  ) => {
    realtime.emit = options.onEvent ?? null
    realtime.status = ref('connected')
    realtime.active = ref(true)
    return {
      status: realtime.status,
      active: realtime.active,
      error: ref<string | null>(null),
      lastError: ref<string | null>(null),
      stop: vi.fn(),
    }
  },
}))

const openReport = (overrides: Partial<AdminReport> = {}): AdminReport => ({
  id: 'report-1',
  userId: 'user-1',
  status: 'OPEN',
  category: 'BUG',
  title: 'Admin report',
  createdAt: '2026-07-17T10:00:00Z',
  closedAt: null,
  deleteAfter: null,
  messages: [],
  ...overrides,
})
function mountView(reports: AdminReport[] = []) {
  return mount(AdminReportsView, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: {
            admin: {
              reports,
              reportsPagination: {
                totalElements: reports.length,
                totalPages: 1,
                pageNumber: 0,
                pageSize: 20,
              },
            },
          },
        }),
        i18n,
      ],
    },
  })
}

afterEach(() => {
  document.body.innerHTML = ''
  realtime.emit = null
})

describe('Admin ReportsView', () => {
  beforeEach(() => setLocale('en-US'))

  it('renders report detail and ignores another users realtime report event', async () => {
    const report = openReport()
    const wrapper = mountView([report])
    await flushPromises()
    const store = useAdminStore()
    vi.mocked(store.fetchReportDetail).mockResolvedValue({
      report,
      messages: [
        {
          id: 'message-1',
          senderRole: 'USER',
          body: 'User message',
          createdAt: '2026-07-17T10:00:00Z',
          isAdmin: false,
          senderName: 'User',
        },
      ],
    })

    await wrapper.get('.reports-list button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('User message')

    realtime.emit?.({
      type: 'REPORT_MESSAGE_ADDED',
      reportId: 'report-owned-by-someone-else',
      message: {
        id: 'leak',
        senderRole: 'USER',
        body: 'Other user private content',
        createdAt: '2026-07-17T10:01:00Z',
        isAdmin: false,
        senderName: 'User',
      },
      timestamp: '2026-07-17T10:01:00Z',
    })
    await nextTick()
    expect(wrapper.text()).not.toContain('Other user private content')

    realtime.emit?.({
      type: 'REPORT_MESSAGE_ADDED',
      reportId: report.id,
      message: {
        id: 'message-2',
        senderRole: 'ADMIN',
        body: 'Admin realtime reply',
        createdAt: '2026-07-17T10:02:00Z',
        isAdmin: true,
        senderName: 'Admin',
      },
      timestamp: '2026-07-17T10:02:00Z',
    })
    await nextTick()
    expect(wrapper.text()).toContain('Admin realtime reply')
    wrapper.unmount()
  })

  it('replies through the backend and refreshes the thread', async () => {
    const report = openReport()
    const wrapper = mountView([report])
    await flushPromises()
    const store = useAdminStore()
    vi.mocked(store.fetchReportDetail)
      .mockResolvedValueOnce({ report, messages: [] })
      .mockResolvedValueOnce({
        report,
        messages: [
          {
            id: 'admin-message',
            senderRole: 'ADMIN',
            body: 'Reply from support',
            createdAt: '2026-07-17T10:05:00Z',
            isAdmin: true,
            senderName: 'Admin',
          },
        ],
      })

    await wrapper.get('.reports-list button').trigger('click')
    await flushPromises()
    await wrapper.get('#admin-report-reply').setValue('Reply from support')
    await wrapper.get('.reply-form').trigger('submit')
    await flushPromises()

    expect(store.replyToReport).toHaveBeenCalledWith(report.id, 'Reply from support')
    expect(wrapper.text()).toContain('Reply from support')
    wrapper.unmount()
  })

  it('does not label report realtime as Live until the topic subscription is active', async () => {
    const report = openReport()
    const wrapper = mountView([report])
    await flushPromises()
    const store = useAdminStore()
    vi.mocked(store.fetchReportDetail).mockResolvedValue({ report, messages: [] })
    realtime.active.value = false

    await wrapper.get('.reports-list button').trigger('click')
    await flushPromises()

    expect(wrapper.get('.realtime-pill').text()).toBe('Syncing')
    realtime.active.value = true
    await nextTick()
    expect(wrapper.get('.realtime-pill').text()).toBe('Live')
    wrapper.unmount()
  })

  it('reloads a closed report so deleteAfter is visible', async () => {
    const report = openReport()
    const closed = openReport({
      status: 'CLOSED',
      closedAt: '2026-07-17T11:00:00Z',
      deleteAfter: '2026-08-16T11:00:00Z',
    })
    const wrapper = mountView([report])
    await flushPromises()
    const store = useAdminStore()
    vi.mocked(store.fetchReportDetail)
      .mockResolvedValueOnce({ report, messages: [] })
      .mockResolvedValueOnce({ report: closed, messages: [] })

    await wrapper.get('.reports-list button').trigger('click')
    await flushPromises()
    await wrapper.get('.btn-danger').trigger('click')
    const confirm = Array.from(document.body.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Close report'),
    )
    confirm?.click()
    await flushPromises()

    expect(store.closeReport).toHaveBeenCalledWith(report.id)
    expect(wrapper.text()).toContain('Deletes after')
    wrapper.unmount()
  })
})
