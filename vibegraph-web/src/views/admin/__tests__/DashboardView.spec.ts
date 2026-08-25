import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import DashboardView from '../DashboardView.vue'
import { buildPeriodSeries } from '../dashboard-chart-utils'
import type { AdminOverview } from '@/types/api'
import type { InfrastructureSnapshot } from '@/types/infrastructure'
import { useAdminStore } from '@/stores/admin'
import { adminApi } from '@/lib/api'
import i18n, { setLocale } from '@/language'

// render function instead of an inline template: async-component mounting in this
// suite hit the runtime-compiler path inconsistently; a render fn needs no compiler.
// vi.hoisted because vi.mock factories are hoisted above module-level consts;
// h() is imported inside the hoisted scope via the global Vue test environment.
const { vChartMock } = vi.hoisted(() => ({
  vChartMock: {
    name: 'VChart',
    props: ['option', 'autoresize', 'updateOptions'],
    template: '<div class="echart-mock" data-test="echart"></div>',
  },
}))

vi.mock('vue-echarts', () => ({ default: vChartMock }))

// DashboardView loads VChart through this async module (F-M6 split); mock it the
// same way so the test never pulls real echarts into jsdom. __esModule lets Vue's
// defineAsyncComponent interop take .default without probing vitest's mock
// namespace for internal flags (__isTeleport etc. throw when undefined).
vi.mock('../dashboard-echarts', () => ({ __esModule: true, default: vChartMock }))

// The dashboard opens a STOMP channel for live online-user snapshots. Mock the
// transport so the view test never touches SockJS; a hoisted controller lets
// each test flip the connection status and inspect the subscription. The status
// ref is created inside the (async) mock factory so the view's computed sees a
// genuinely reactive value.
const wsController = vi.hoisted(() => ({
  status: null as unknown as { value: 'disconnected' | 'connecting' | 'connected' | 'error' },
  captured: null as null | { topic: string; cb: (payload: unknown) => void },
}))

vi.mock('@/composables/useWebSocket', async () => {
  const { ref } = await import('vue')
  const status = ref<'disconnected' | 'connecting' | 'connected' | 'error'>('disconnected')
  wsController.status = status
  return {
    useWebSocket: () => ({
      status,
      error: { value: null },
      connect: () => Promise.resolve(),
      disconnect: () => Promise.resolve(),
      subscribe: (topic: string, cb: (payload: unknown) => void) => {
        wsController.captured = { topic, cb }
        return { active: { value: true }, unsubscribe: () => {} }
      },
    }),
  }
})

describe('Admin DashboardView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setLocale('en-US')
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 17, 13, 5, 30))
    setVisibility('visible')
    wsController.status.value = 'disconnected'
    wsController.captured = null
    vi.spyOn(adminApi, 'getInfrastructureSnapshot').mockResolvedValue(createInfrastructureSnapshot({
      capturedAt: '2026-07-17T13:05:30Z',
      status: 'HEALTHY',
      host: {
        cpuPercent: 27.9,
        vcpuCount: 4,
        currentGHz: 2.4,
        avgCpuPercent: 25,
        peakCpuPercent: 32,
        status: 'MEASURED',
      },
      memory: {
        usedBytes: 2_899_102_924,
        totalBytes: 8_375_377_920,
        availableBytes: 5_476_274_996,
        usedPercent: 34.6,
        breakdown: [],
        status: 'MEASURED',
      },
      disk: {
        usedBytes: 25_018_754_867,
        totalBytes: 50_895_503_360,
        freeBytes: 25_876_748_493,
        usedPercent: 49.2,
        breakdown: [],
        status: 'MEASURED',
      },
      network: {
        inBytesPerSecond: 7_707_853,
        outBytesPerSecond: 2_516_582,
        droppedPackets: 0,
        status: 'MEASURED',
      },
      containers: [
        {
          name: 'backend',
          status: 'running',
          healthy: true,
          memoryUsedBytes: 704_643_072,
          cpuPercent: 0.4,
          restartCount: 0,
          source: 'test',
        },
        {
          name: 'neo4j',
          status: 'running',
          healthy: true,
          memoryUsedBytes: 1_703_624_704,
          cpuPercent: 1.35,
          restartCount: 0,
          source: 'test',
        },
      ],
    }))
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
          i18n,
        ],
      },
    })

    await settleCharts()
    expect(wrapper.text()).toContain('1,500')
    expect(wrapper.text()).toContain('42')
    expect(wrapper.text()).toContain('350')
    expect(wrapper.text()).toContain('Platform Analytics')
    expect(wrapper.text()).toContain('Total Users')
    expect(wrapper.text()).toContain('Online Users')
    expect(wrapper.text()).toContain('Infrastructure')
    expect(wrapper.text()).not.toContain('Server Disk')
    const infrastructureCard = wrapper.get('[data-test="infrastructure-card"]')
    expect(infrastructureCard.text()).toContain('Live')
    expect(infrastructureCard.text()).toContain('OK')
    expect(infrastructureCard.text()).toContain('VPS is healthy')
    expect(infrastructureCard.text()).toContain('4 vCPU')
    expect(infrastructureCard.text()).toContain('7.8 GB RAM')
    expect(infrastructureCard.text()).toContain('CPU')
    expect(infrastructureCard.text()).toContain('27.9%')
    expect(infrastructureCard.text()).toContain('RAM')
    expect(infrastructureCard.text()).toContain('34.6%')
    expect(infrastructureCard.text()).toContain('SSD')
    expect(infrastructureCard.text()).toContain('49.2%')
    expect(infrastructureCard.text()).toContain('NET')
    expect(infrastructureCard.text()).toContain('2.4 MB/s')
    expect(infrastructureCard.text()).toContain('34.6% used')
    expect(infrastructureCard.text()).toContain('5.1 GB available')
    expect(infrastructureCard.text()).toContain('23.3 GB used')
    expect(infrastructureCard.text()).toContain('24.1 GB free')
    expect(infrastructureCard.text()).toContain('in 7.4 MB/s')
    expect(infrastructureCard.text()).toContain('0 drops')
    expect(infrastructureCard.findAll('.infrastructure-mini')).toHaveLength(4)
    expect(infrastructureCard.findAll('.infrastructure-mini-sparkline')).toHaveLength(3)
    expect(infrastructureCard.findAll('.infrastructure-disk-bar')).toHaveLength(1)
    expect(infrastructureCard.find('.infrastructure-health-ring').exists()).toBe(true)
    expect(infrastructureCard.text()).not.toContain('services healthy')
    expect(wrapper.find('[data-test="infrastructure-card-link"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Top Storage Projects')
    expect(wrapper.text()).toContain('Plan Distribution')
    expect(wrapper.text()).toContain('Security / Abuse Alerts')
    expect(wrapper.findAll('[data-test="echart"]')).toHaveLength(5)
    expect(wrapper.find('[data-test="plan-distribution-panel"]').classes()).toContain('panel--wide')
    expect(wrapper.find('[data-test="security-alerts-panel"]').classes()).toContain('panel--wide')
    wrapper.unmount()
  })

  it('keeps storage rankings compact while preserving overflow items for scrolling', async () => {
    const projects = Array.from({ length: 7 }, (_, index) => ({
      id: `project-${index + 1}`,
      name: `project-${index + 1}`,
      ownerEmail: `owner-${index + 1}@example.com`,
      usedBytes: (index + 1) * 1024,
    }))
    const users = Array.from({ length: 6 }, (_, index) => ({
      id: `user-${index + 1}`,
      name: `User ${index + 1}`,
      ownerEmail: `user-${index + 1}@example.com`,
      usedBytes: (index + 1) * 1024,
    }))
    const wrapper = mountDashboard(createOverview({ topStorageProjects: projects, topStorageUsers: users }))

    await settleCharts()

    const projectChart = wrapper
      .findAllComponents({ name: 'VChart' })
      .find((chart) => chart.classes().includes('storage-project-chart'))
    if (!projectChart) throw new Error('Storage project chart was not rendered')
    const projectOption = projectChart.props('option') as { yAxis: { data: string[] } }

    expect(projectOption.yAxis.data).toHaveLength(5)
    expect(wrapper.get('.storage-users-list').classes()).toContain('storage-users-list')
    expect(wrapper.findAll('.storage-users-list .compact-row')).toHaveLength(6)
    expect(wrapper.get('.storage-projects-panel .panel-header').text()).toContain('Count · 5')
    wrapper.unmount()
  })

  it('keeps the infrastructure card usable when the live snapshot is unavailable', async () => {
    vi.mocked(adminApi.getInfrastructureSnapshot).mockRejectedValueOnce(
      new Error('Infrastructure unavailable'),
    )
    const wrapper = mountDashboard(createOverview())

    await settleCharts()

    const card = wrapper.get('[data-test="infrastructure-card"]')
    expect(card.text()).toContain('Infrastructure')
    expect(card.text()).toContain('Live metrics unavailable')
    expect(wrapper.find('[data-test="infrastructure-card-link"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('does not present zeroes as live infrastructure measurements', async () => {
    vi.mocked(adminApi.getInfrastructureSnapshot).mockResolvedValueOnce(
      createInfrastructureSnapshot(),
    )
    const wrapper = mountDashboard(createOverview())

    await settleCharts()

    const card = wrapper.get('[data-test="infrastructure-card"]')
    expect(card.text()).toContain('—')
    expect(card.text()).not.toContain('0.0%')
    expect(card.text()).not.toContain('0 B')
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
    await settleCharts()
    const secondAdmin = mountDashboard(createOverview())

    await settleCharts()
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
          i18n,
        ],
      },
    })

    await settleCharts()
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
          i18n,
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

  it('skips the 30s overview poll while the realtime channel is connected', async () => {
    wsController.status.value = 'connected'
    const wrapper = mount(DashboardView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: { admin: { overview: createOverview() } },
          }),
          i18n,
        ],
      },
    })
    const adminStore = useAdminStore()

    await flushPromises()
    expect(wsController.captured?.topic).toBe('/topic/admin/online-users')
    expect(adminStore.fetchOverview).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(30_000)
    expect(adminStore.fetchOverview).toHaveBeenCalledTimes(1)

    // After the five-minute background window the full overview refreshes again.
    await vi.advanceTimersByTimeAsync(4 * 60_000 + 30_000)
    expect(adminStore.fetchOverview).toHaveBeenCalledTimes(2)

    // Once the channel drops, the 30s polling fallback resumes.
    wsController.status.value = 'disconnected'
    await vi.advanceTimersByTimeAsync(30_000)
    expect(adminStore.fetchOverview).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('shows live copy while the realtime channel is connected', async () => {
    wsController.status.value = 'connected'
    const wrapper = mount(DashboardView, {
      global: {
        plugins: [
          createTestingPinia({
            createSpy: vi.fn,
            initialState: { admin: { overview: createOverview() } },
          }),
          i18n,
        ],
      },
    })

    await settleCharts()
    expect(wrapper.text()).toContain('Live updates connected')
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

function createInfrastructureSnapshot(
  overrides: Partial<InfrastructureSnapshot> = {},
): InfrastructureSnapshot {
  return {
    capturedAt: '2026-07-17T13:05:30Z',
    status: 'HEALTHY',
    host: {
      cpuPercent: 0,
      vcpuCount: 4,
      currentGHz: null,
      avgCpuPercent: 0,
      peakCpuPercent: 0,
      status: 'UNKNOWN',
    },
      memory: {
      usedBytes: 0,
      totalBytes: 8_375_377_920,
      availableBytes: 8_375_377_920,
      usedPercent: 0,
      breakdown: [],
      status: 'UNKNOWN',
    },
    disk: {
      usedBytes: 0,
      totalBytes: 50_895_503_360,
      freeBytes: 50_895_503_360,
      usedPercent: 0,
      breakdown: [],
      status: 'UNKNOWN',
    },
    network: { inBytesPerSecond: 0, outBytesPerSecond: 0, droppedPackets: 0, status: 'UNKNOWN' },
    diskIo: { readBytesPerSecond: 0, writeBytesPerSecond: 0, utilizationPercent: 0, status: 'UNKNOWN' },
    containers: [],
    latestOperation: null,
    capacity: {
      status: 'LEARNING',
      evidenceSamples: 0,
      confidence: 'UNKNOWN',
      safeHeadroomPercent: 0,
      mcpSafe: null,
      graphApi: null,
      analyzeObservedSafe: null,
      heavyConcurrency: null,
    },
    history: [],
    incidents: [],
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
        i18n,
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

/**
 * VChart is a defineAsyncComponent (F-M6 split moved echarts into its own async
 * chunk), so chart rendering settles one dynamic-import hop later than a plain
 * flushPromises under fake timers.
 */
async function settleCharts(): Promise<void> {
  // Multiple rounds: when several dashboards mount under fake timers, each async
  // chunk resolution may need its own flush hop.
  for (let round = 0; round < 3; round++) {
    await flushPromises()
    await vi.advanceTimersByTimeAsync(0)
  }
  await flushPromises()
}
