import { afterEach, describe, expect, it, vi } from 'vitest'
import { adminApi } from '@/lib/api'
import { useInfrastructureMonitor } from '../useInfrastructureMonitor'
import { emptyInfrastructureSnapshot, type InfrastructureSnapshot } from '@/types/infrastructure'

function validSnapshot(): InfrastructureSnapshot {
  return {
    ...emptyInfrastructureSnapshot(),
    capturedAt: '2026-08-25T10:00:00.000Z',
    host: {
      cpuPercent: 20,
      vcpuCount: 4,
      currentGHz: 2.4,
      avgCpuPercent: 18,
      peakCpuPercent: 32,
      status: 'MEASURED',
    },
    memory: {
      totalBytes: 8_000,
      usedBytes: 3_000,
      availableBytes: 5_000,
      usedPercent: 37.5,
      breakdown: [],
      status: 'MEASURED',
    },
    disk: {
      totalBytes: 50_000,
      usedBytes: 20_000,
      freeBytes: 30_000,
      usedPercent: 40,
      breakdown: [],
      status: 'MEASURED',
    },
    network: {
      inBytesPerSecond: 100,
      outBytesPerSecond: 50,
      droppedPackets: 0,
      status: 'MEASURED',
    },
    diskIo: {
      readBytesPerSecond: 75,
      writeBytesPerSecond: 25,
      utilizationPercent: 12,
      status: 'MEASURED',
    },
  }
}

describe('useInfrastructureMonitor', () => {
  afterEach(() => vi.restoreAllMocks())

  it('accepts a complete finite snapshot', async () => {
    const value = validSnapshot()
    vi.spyOn(adminApi, 'getInfrastructureSnapshot').mockResolvedValue(value)
    const monitor = useInfrastructureMonitor()

    await monitor.refresh()

    expect(monitor.snapshot.value.capturedAt).toBe(value.capturedAt)
    expect(monitor.samples.value).toHaveLength(1)
  })

  it.each([
    ['invalid timestamp', { capturedAt: 'not-a-date' }],
    ['non-finite CPU', { host: { ...validSnapshot().host, cpuPercent: Number.NaN } }],
    ['malformed memory breakdown', { memory: { ...validSnapshot().memory, breakdown: [{}] } }],
    ['missing history array', { history: undefined }],
    [
      'non-finite network rate',
      { network: { ...validSnapshot().network, outBytesPerSecond: Infinity } },
    ],
  ])('rejects %s without mutating the current snapshot', async (_label, override) => {
    const malformed = { ...validSnapshot(), ...override } as InfrastructureSnapshot
    vi.spyOn(adminApi, 'getInfrastructureSnapshot').mockResolvedValue(malformed)
    const monitor = useInfrastructureMonitor()

    await expect(monitor.refresh()).rejects.toThrow('invalid snapshot')

    expect(monitor.snapshot.value.capturedAt).toBe(new Date(0).toISOString())
    expect(monitor.samples.value).toHaveLength(0)
  })
})
