import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import DashboardView from '../DashboardView.vue'
import { buildPeriodSeries } from '../dashboard-chart-utils'
import type { AdminOverview } from '@/types/api'
import { useAdminStore } from '@/stores/admin'

vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    props: ['option', 'autoresize', 'updateOptions'],
    template: '<div class="echart-mock" data-test="echart"></div>',
  },
}))

describe('Admin DashboardView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 17, 13, 5, 30))
    setVisibility('visible')
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('renders the operations metrics and wide supporting sections from the overview API', async () => {
    const wrapper = mount(DashboardView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              admin: { overview: createOverview() },
            },
          }),
        ],
      },
    })

    await flushPromises()
    expect(wrapper.text()).toContain('1,500')
    expect(wrapper.text()).toContain('42')
    expect(wrapper.text()).toContain('350')
    expect(wrapper.text()).toContain('Platform Analytics')
    expect(wrapper.text()).toContain('Total Users')
    expect(wrapper.text()).toContain('Online Users')
    expect(wrapper.text()).toContain('Top Storage Projects')
    expect(wrapper.text()).toContain('Plan Distribution')
    expect(wrapper.text()).toContain('Security / Abuse Alerts')
    expect(wrapper.findAll('[data-test="echart"]')).toHaveLength(6)
    expect(wrapper.find('[data-test="plan-distribution-panel"]').classes()).toContain('panel--wide')
    expect(wrapper.find('[data-test="security-alerts-panel"]').classes()).toContain('panel--wide')
    wrapper.unmount()
  })

  it('renders all twelve months and the latest five years', () => {
    const points = [
      { label: '2025-01', value: 2, period: 'month' },
      { label: '2025-12', value: 5, period: 'month' },
      { label: '2022', value: 8, period: 'year' },
      { label: '2026', value: 13, period: 'year' },
    ]

    const months = buildPeriodSeries(points, 'month', new Date(2025, 11, 1))
    const years = buildPeriodSeries(points, 'year', new Date(2026, 6, 17))

    expect(months.map((point) => point.label)).toEqual([
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ])
    expect(months.map((point) => point.value)).toEqual([2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5])
    expect(years.map((point) => point.label)).toEqual(['2022', '2023', '2024', '2025', '2026'])
    expect(years.map((point) => point.value)).toEqual([8, 0, 0, 0, 13])
  })

  it('renders the same backend online history for separate admin clients', async () => {
    const firstAdmin = mountDashboard(createOverview())
    const secondAdmin = mountDashboard(createOverview())

    await flushPromises()
    const firstPoints = onlineSeriesPoints(firstAdmin)
    const secondPoints = onlineSeriesPoints(secondAdmin)

    expect(firstPoints).toEqual(secondPoints)
    expect(firstPoints.slice(-2).map((point) => point[1])).toEqual([3, 42])
    firstAdmin.unmount()
    secondAdmin.unmount()
  })

  it('leaves online buckets before the first observed sample empty', async () => {
    const wrapper = mount(DashboardView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: {
              admin: {
                overview: createOverview({
                  onlineUserHistory: [
                    { label: '2026-07-17T13:05:00Z', value: 42, period: 'minute' },
                  ],
                }),
              },
            },
          }),
        ],
      },
    })

    await flushPromises()
    const onlineChart = wrapper.findAllComponents({ name: 'VChart' })[1]
    if (!onlineChart) throw new Error('Online users chart was not rendered')
    const option = onlineChart.props('option') as {
      series: Array<{ data: Array<[number, number | null]> }>
    }
    const points = option.series[0]?.data ?? []

    expect(points).toHaveLength(10)
    expect(points.slice(0, -1).every((point) => point[1] === null)).toBe(true)
    expect(points[points.length - 1]?.[1]).toBe(42)
    wrapper.unmount()
  })

  it('polls every thirty seconds only while the browser tab is visible', async () => {
    const wrapper = mount(DashboardView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: { admin: { overview: createOverview() } },
          }),
        ],
      },
    })
    const adminStore = useAdminStore()

    await flushPromises()
    expect(adminStore.fetchOverview).toHaveBeenCalledTimes(1)

    setVisibility('hidden')
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(30_000)
    expect(adminStore.fetchOverview).toHaveBeenCalledTimes(1)

    setVisibility('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(adminStore.fetchOverview).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(30_000)
    expect(adminStore.fetchOverview).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })
})

function createOverview(overrides: Partial<AdminOverview> = {}): AdminOverview {
  return {
    totalUsers: 1500,
    onlineUsers: 42,
    totalProjects: 350,
    totalReports: 8,
    openReports: 3,
    blockedUsers: 2,
    timestamp: '2026-07-17T13:05:30Z',
    userGrowth: [
      { label: '2026-06', value: 120, period: 'month' },
      { label: '2026-07', value: 80, period: 'month' },
    ],
    creditConsumption: [
      { label: '2026-06', value: 900, period: 'month' },
      { label: '2026-07', value: 750, period: 'month' },
    ],
    storage: {
      usedBytes: 375_809_638_400,
      totalBytes: 1_099_511_627_776,
      sourceLabel: 'Primary storage',
    },
    planDistribution: [
      { label: 'FREE', value: 1100 },
      { label: 'PRO', value: 400 },
    ],
    topStorageProjects: [
      {
        id: 'project-1',
        name: 'vibegraph-core',
        ownerEmail: 'owner@example.com',
        usedBytes: 1_073_741_824,
      },
    ],
    securityAlerts: [
      {
        id: 'security-rate-limit-warning',
        type: 'RATE_LIMIT',
        severity: 'WARNING',
        summary: '4 event(s) in the last 24 hours',
        createdAt: '2026-07-17T13:00:00Z',
      },
    ],
    onlineUserHistory: [
      { label: '2026-07-17T13:04:00Z', value: 3, period: 'minute' },
      { label: '2026-07-17T13:05:00Z', value: 42, period: 'minute' },
    ],
    ...overrides,
  }
}

function mountDashboard(overview: AdminOverview) {
  return mount(DashboardView, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: { admin: { overview } },
        }),
      ],
    },
  })
}

function onlineSeriesPoints(
  wrapper: ReturnType<typeof mount>,
): Array<[number, number | null]> {
  const onlineChart = wrapper.findAllComponents({ name: 'VChart' })[1]
  if (!onlineChart) throw new Error('Online users chart was not rendered')
  const option = onlineChart.props('option') as {
    series: Array<{ data: Array<[number, number | null]> }>
  }
  return option.series[0]?.data ?? []
}

function setVisibility(value: DocumentVisibilityState): void {
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    value,
  })
}
