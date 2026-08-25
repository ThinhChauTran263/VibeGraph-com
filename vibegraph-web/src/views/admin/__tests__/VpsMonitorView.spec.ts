import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import VpsMonitorView from '../VpsMonitorView.vue'
import { adminApi } from '@/lib/api'

const snapshot = {
  capturedAt: '2026-08-25T10:00:00Z',
  status: 'HEALTHY',
  host: {
    cpuPercent: 23.1,
    vcpuCount: 4,
    currentGHz: 2.4,
    avgCpuPercent: 16.8,
    peakCpuPercent: 31.4,
    status: 'MEASURED',
  },
  memory: {
    totalBytes: 7.8 * 1024 ** 3,
    usedBytes: 2.7 * 1024 ** 3,
    availableBytes: 5.1 * 1024 ** 3,
    usedPercent: 34.6,
    breakdown: [
      { key: 'neo4j', label: 'Neo4j RAM', usedBytes: 1.59 * 1024 ** 3, percentOfTotal: 20.4 },
      { key: 'backend', label: 'Backend RAM', usedBytes: 672 * 1024 ** 2, percentOfTotal: 8.4 },
      { key: 'services', label: 'PostgreSQL + services RAM', usedBytes: 166 * 1024 ** 2, percentOfTotal: 2.1 },
      { key: 'other', label: 'Linux cache / other RAM', usedBytes: 272 * 1024 ** 2, percentOfTotal: 3.5 },
    ],
    status: 'HEALTHY',
  },
  disk: {
    totalBytes: 47.4 * 1024 ** 3,
    usedBytes: 23.3 * 1024 ** 3,
    freeBytes: 24.1 * 1024 ** 3,
    usedPercent: 49.2,
    breakdown: [
      {
        key: 'tracked-projects',
          label: 'Neo4j data + logs',
          usedBytes: 8.6 * 1024 ** 3,
          percentOfTotal: 18.1,
          source: 'Docker volumes',
        status: 'MEASURED',
      },
      {
        key: 'filesystem-other',
          label: 'Uploads + source archives',
          usedBytes: 5.2 * 1024 ** 3,
          percentOfTotal: 11,
          source: 'Uploads volume',
        status: 'ESTIMATED',
      },
    ],
    status: 'MEASURED',
  },
  network: {
    inBytesPerSecond: 8.2 * 1024 ** 2,
    outBytesPerSecond: 2.7 * 1024 ** 2,
    droppedPackets: 0,
    status: 'MEASURED',
  },
  diskIo: {
    readBytesPerSecond: 18 * 1024 ** 2,
    writeBytesPerSecond: 4 * 1024 ** 2,
    utilizationPercent: 24,
    status: 'MEASURED',
  },
  containers: [
    {
      name: 'Backend',
      status: 'running',
      healthy: true,
      memoryUsedBytes: 672 * 1024 ** 2,
      cpuPercent: 0.4,
      restartCount: 0,
    },
  ],
  latestOperation: {
    id: 'op-1',
    projectName: 'ASM_Final_Java6',
    type: 'ANALYZE' as const,
    operation: 'Analyze completed',
    status: 'SUCCESS',
    durationMs: 42_800,
    nodes: 17_439,
    edges: 68_902,
    ramBeforeBytes: 3.1 * 1024 ** 3,
    ramPeakBytes: 4.01 * 1024 ** 3,
    ramAfterCooldownBytes: 3.17 * 1024 ** 3,
    ramIncreaseBytes: 910 * 1024 ** 2,
    cooldownComplete: true,
    cpuAvgPercent: 38,
    cpuPeakPercent: 76,
    storageAddedBytes: 278 * 1024 ** 2,
    confidence: 'HIGH',
    traceId: 'trace-1',
    projectId: 'project-1',
    startedAt: '2026-08-25T10:00:00Z',
    completedAt: '2026-08-25T10:00:42Z',
    cpuCoreSeconds: 61.4,
    diskReadBytes: 0,
    diskWriteBytes: 0,
    concurrentHeavyOperations: 0,
    backendVersion: '77d119d',
    measurementType: 'OBSERVED',
    stopReason: null,
  },
  capacity: {
    safeHeadroomPercent: 28,
    evidenceSamples: 48,
    confidence: 'HIGH',
    mcpSafe: { nodes: 75_000, edges: 200_000, measurementType: 'VALIDATED', confidence: 'HIGH' },
    graphApi: { nodes: 10_000, edges: 30_000, measurementType: 'VALIDATED', confidence: 'HIGH' },
    analyzeObservedSafe: {
      nodes: 62_000,
      edges: 184_000,
      measurementType: 'OBSERVED',
      confidence: 'HIGH',
    },
    heavyConcurrency: '1 Analyze or 1 full MCP',
  },
  history: [],
  incidents: [],
}

describe('VpsMonitorView', () => {
  beforeEach(() => {
    vi.spyOn(adminApi, 'getInfrastructureSnapshot').mockResolvedValue(snapshot)
  })

  afterEach(() => vi.restoreAllMocks())

  it('renders the approved host cards and operation evidence labels', async () => {
    const wrapper = mount(VpsMonitorView)
    await flushPromises()

    expect(wrapper.text()).toContain('CPU LOAD')
    expect(wrapper.text()).toContain('2.7 GB')
    expect(wrapper.text()).toContain('in 8.2 MB/s')
    expect(wrapper.text()).toContain('out 2.7 MB/s')
    expect(wrapper.text()).toContain('3.10 GB · Peak 4.01 GB · After cooldown 3.17 GB')
    expect(wrapper.text()).toContain('+910 MB peak')
    expect(wrapper.text()).toContain('Confidence HIGH')
    expect(wrapper.text()).toContain('Verified VPS capacity')
    expect(wrapper.text()).toContain('1 Analyze or 1 full MCP')
    expect(wrapper.text()).toContain('Docker services')
    expect(wrapper.text()).toContain('Neo4j data + logs')
    expect(wrapper.text()).toContain('Uploads + source archives')
    expect(wrapper.text()).not.toContain('VIEW EVIDENCE')
    expect(wrapper.text()).not.toContain('VALIDATE LIMIT')
    expect(wrapper.find('.capacity-ring').attributes('style')).toContain('28%')
    wrapper.unmount()
  })

  it('shows a useful error state when the infrastructure API is unavailable', async () => {
    vi.mocked(adminApi.getInfrastructureSnapshot).mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(VpsMonitorView)
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('offline')
    expect(wrapper.text()).toContain('No recorded operations yet')
    wrapper.unmount()
  })

  it('renders a safe project fallback when telemetry omits the project name', async () => {
    vi.mocked(adminApi.getInfrastructureSnapshot).mockResolvedValueOnce({
      ...snapshot,
      latestOperation: { ...snapshot.latestOperation, projectName: null },
      history: [{ ...snapshot.latestOperation, projectName: null }],
    })
    const wrapper = mount(VpsMonitorView)
    await flushPromises()

    expect(wrapper.text()).toContain('project-1 · Analyze completed')
    expect(wrapper.text()).not.toContain('null ·')
    wrapper.unmount()
  })

  it('does not fabricate unavailable host metrics', async () => {
    vi.mocked(adminApi.getInfrastructureSnapshot).mockResolvedValueOnce({
      ...snapshot,
      network: {
        inBytesPerSecond: 0,
        outBytesPerSecond: 0,
        droppedPackets: 0,
        status: 'UNAVAILABLE',
      },
      diskIo: {
        readBytesPerSecond: 0,
        writeBytesPerSecond: 0,
        utilizationPercent: null,
        status: 'UNAVAILABLE',
      },
      containers: [],
      capacity: {
        ...snapshot.capacity,
        status: 'LEARNING',
        safeHeadroomPercent: null,
      },
    })

    const wrapper = mount(VpsMonitorView)
    await flushPromises()

    expect(wrapper.text()).toContain('Network source unavailable')
    expect(wrapper.text()).toContain('Disk I/O')
    expect(wrapper.text()).toContain('UNAVAILABLE')
    expect(wrapper.text()).toContain('Container metrics unavailable')
    expect(wrapper.text()).not.toContain('0 / 0 HEALTHY')
    expect(wrapper.find('.chart-series.net').attributes('points')).toBe('')
    expect(wrapper.find('.chart-series.disk').attributes('points')).toBe('')
    wrapper.unmount()
  })

  it('does not render warming-up rates as measured zeros', async () => {
    vi.mocked(adminApi.getInfrastructureSnapshot).mockResolvedValueOnce({
      ...snapshot,
      network: { ...snapshot.network, status: 'WARMING_UP' },
      diskIo: { ...snapshot.diskIo, utilizationPercent: null, status: 'WARMING_UP' },
    })

    const wrapper = mount(VpsMonitorView)
    await flushPromises()

    expect(wrapper.text()).toContain('Network source unavailable')
    expect(wrapper.text()).toContain('Disk I/O')
    expect(wrapper.text()).toContain('UNAVAILABLE')
    expect(wrapper.find('.chart-series.net').attributes('points')).toBe('')
    expect(wrapper.find('.chart-series.disk').attributes('points')).toBe('')
    wrapper.unmount()
  })

  it('distinguishes configured ceilings from observed capacity evidence', async () => {
    vi.mocked(adminApi.getInfrastructureSnapshot).mockResolvedValueOnce({
      ...snapshot,
      capacity: {
        ...snapshot.capacity,
        mcpSafe: {
          ...snapshot.capacity.mcpSafe!,
          measurementType: 'CONFIGURED',
        },
        graphApi: {
          ...snapshot.capacity.graphApi!,
          measurementType: 'CONFIGURED',
        },
      },
    })

    const wrapper = mount(VpsMonitorView)
    await flushPromises()

    expect(wrapper.text()).toContain('MCP safe limitconfigured ceiling')
    expect(wrapper.text()).toContain('Graph APIconfigured ceiling')
    expect(wrapper.text()).toContain('Analyze observed safe')
    expect(wrapper.text()).toContain('Verified VPS capacity')
    wrapper.unmount()
  })

  it('does not present an unreported Analyze storage delta as measured zero', async () => {
    vi.mocked(adminApi.getInfrastructureSnapshot).mockResolvedValueOnce({
      ...snapshot,
      latestOperation: {
        ...snapshot.latestOperation,
        storageAddedBytes: 0,
      },
    })

    const wrapper = mount(VpsMonitorView)
    await flushPromises()

    expect(wrapper.text()).toContain('Storage addednot measured')
    expect(wrapper.text()).toContain('source did not report a delta')
    expect(wrapper.text()).not.toContain('Storage delta+0 B')
    wrapper.unmount()
  })

  it('adds labels that turn history rows into readable mobile cards', async () => {
    vi.mocked(adminApi.getInfrastructureSnapshot).mockResolvedValueOnce({
      ...snapshot,
      history: [snapshot.latestOperation],
    })

    const wrapper = mount(VpsMonitorView)
    await flushPromises()

    expect(wrapper.get('td[data-label="Project / operation"]').text()).toContain('ASM_Final_Java6')
    expect(wrapper.get('td[data-label="RAM before → peak → after"]').text()).toContain(
      '3.10 GB → 4.01 GB → 3.17 GB',
    )
    expect(wrapper.get('td[data-label="Evidence"]').text()).toContain('HIGH · trace-1')
    wrapper.unmount()
  })
})
