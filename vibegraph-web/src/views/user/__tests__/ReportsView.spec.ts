import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { nextTick, ref } from 'vue'
import ReportsView from '../ReportsView.vue'
import { useAccountStore } from '@/stores/account'
import type { Report, ReportRealtimeEvent } from '@/types/api'

const realtime = vi.hoisted(() => ({
  emit: null as ((event: ReportRealtimeEvent) => void) | null,
}))

vi.mock('@/composables/useReportRealtime', () => ({
  useReportRealtime: (
    _reportId: unknown,
    options: { onEvent?: (event: ReportRealtimeEvent) => void } = {},
  ) => {
    realtime.emit = options.onEvent ?? null
    return {
      status: ref('connected'),
      error: ref<string | null>(null),
      lastError: ref<string | null>(null),
      stop: vi.fn(),
    }
  },
}))

const openReport = (overrides: Partial<Report> = {}): Report => ({
  id: 'report-1',
  status: 'OPEN',
  category: 'BUG',
  title: 'Realtime issue',
  createdAt: '2026-07-17T10:00:00Z',
  closedAt: null,
  deletesAfter: null,
  messages: [],
  ...overrides,
})
function mountView(reports: Report[] = []) {
  return mount(ReportsView, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: { account: { reports } },
        }),
      ],
    },
  })
}

afterEach(() => {
  document.body.innerHTML = ''
  realtime.emit = null
})

describe('User ReportsView', () => {
  it('submits a backend-backed report', async () => {
    const wrapper = mountView()
    await flushPromises()
    const store = useAccountStore()
    vi.mocked(store.createReport).mockResolvedValue(openReport())

    await wrapper.get('#report-subject').setValue('Test Subject')
    await wrapper.get('#report-message').setValue('Test Message')
    await wrapper.get('.create-report form').trigger('submit')
    await flushPromises()

    expect(store.createReport).toHaveBeenCalledWith('BUG', 'Test Subject', 'Test Message')
    wrapper.unmount()
  })

  it('renders a thread and ignores realtime events for another report', async () => {
    const report = openReport()
    const wrapper = mountView([report])
    await flushPromises()
    const store = useAccountStore()
    vi.mocked(store.fetchReportDetail).mockResolvedValue({
      ...report,
      messages: [
        {
          id: 'message-1',
          senderRole: 'USER',
          body: 'Initial message',
          createdAt: '2026-07-17T10:00:00Z',
          isAdmin: false,
          senderName: 'You',
        },
      ],
    })

    await wrapper.get('.reports-list button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Initial message')

    realtime.emit?.({
      type: 'REPORT_MESSAGE_ADDED',
      reportId: 'another-report',
      message: {
        id: 'leaked-message',
        senderRole: 'ADMIN',
        body: 'Must not be visible',
        createdAt: '2026-07-17T10:01:00Z',
        isAdmin: true,
        senderName: 'Support Team',
      },
      timestamp: '2026-07-17T10:01:00Z',
    })
    await nextTick()
    expect(wrapper.text()).not.toContain('Must not be visible')

    realtime.emit?.({
      type: 'REPORT_MESSAGE_ADDED',
      reportId: report.id,
      message: {
        id: 'message-2',
        senderRole: 'ADMIN',
        body: 'Visible support reply',
        createdAt: '2026-07-17T10:02:00Z',
        isAdmin: true,
        senderName: 'Support Team',
      },
      timestamp: '2026-07-17T10:02:00Z',
    })
    await nextTick()
    expect(wrapper.text()).toContain('Visible support reply')
    wrapper.unmount()
  })

  it('closes a report and shows its retention date', async () => {
    const report = openReport()
    const wrapper = mountView([report])
    await flushPromises()
    const store = useAccountStore()
    vi.mocked(store.fetchReportDetail).mockResolvedValue(report)
    vi.mocked(store.closeReport).mockResolvedValue(
      openReport({
        status: 'CLOSED',
        closedAt: '2026-07-17T11:00:00Z',
        deletesAfter: '2026-08-16T11:00:00Z',
      }),
    )

    await wrapper.get('.reports-list button').trigger('click')
    await flushPromises()
    await wrapper.get('.btn-danger').trigger('click')
    const confirm = Array.from(document.body.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Close report'),
    )
    expect(confirm).toBeTruthy()
    confirm?.click()
    await flushPromises()

    expect(store.closeReport).toHaveBeenCalledWith(report.id)
    expect(wrapper.text()).toContain('Scheduled for deletion after')
    wrapper.unmount()
  })
})
