import { computed, readonly, ref } from 'vue'
import { adminApi, api } from '@/lib/api'
import { emptyInfrastructureSnapshot, type InfrastructureSnapshot } from '@/types/infrastructure'
import { createInfrastructureMockSnapshot } from '@/mocks/infrastructureSnapshot'

export type InfrastructureLiveStatus = 'connected' | 'reconnecting' | 'polling' | 'paused'

export interface InfrastructureSample {
  capturedAt: number
  cpuPercent: number
  memoryPercent: number
  diskPercent: number | null
  networkBytesPerSecond: number | null
}

const POLL_INTERVAL_MS = 5_000
const MAX_SAMPLES = 60
const DEV_MOCK_ENABLED = import.meta.env.DEV && import.meta.env.VITE_INFRASTRUCTURE_MOCK === 'true'

export function useInfrastructureMonitor() {
  const snapshot = ref<InfrastructureSnapshot>(emptyInfrastructureSnapshot())
  const samples = ref<InfrastructureSample[]>([])
  const status = ref<InfrastructureLiveStatus>('paused')
  const loading = ref(false)
  const error = ref<string | null>(null)
  let source: EventSource | null = null
  let pollTimer: ReturnType<typeof setInterval> | null = null
  let started = false

  const hasSnapshot = computed(() => snapshot.value.capturedAt !== new Date(0).toISOString())

  async function refresh(): Promise<void> {
    loading.value = !hasSnapshot.value
    try {
      if (DEV_MOCK_ENABLED) {
        applySnapshot(createInfrastructureMockSnapshot())
        error.value = null
        return
      }
      const value: unknown = await adminApi.getInfrastructureSnapshot()
      if (!isInfrastructureSnapshot(value)) {
        throw new Error('Infrastructure API returned an invalid snapshot.')
      }
      applySnapshot(value)
      error.value = null
    } catch (cause) {
      error.value =
        cause instanceof Error ? cause.message : 'Infrastructure metrics are unavailable.'
      throw cause
    } finally {
      loading.value = false
    }
  }

  function start(): void {
    if (started) return
    started = true
    void refresh().catch(() => undefined)
    if (DEV_MOCK_ENABLED) {
      status.value = 'connected'
      return
    }
    openStream()
  }

  function stop(): void {
    started = false
    source?.close()
    source = null
    stopPolling()
    status.value = 'paused'
  }

  function openStream(): void {
    if (typeof EventSource === 'undefined') {
      startPolling()
      return
    }
    source?.close()
    status.value = 'reconnecting'
    const stream = new EventSource(`${api.baseUrl}/api/admin/infrastructure/stream`, {
      withCredentials: true,
    })
    source = stream
    stream.onopen = () => {
      if (source !== stream) return
      stopPolling()
      status.value = 'connected'
    }
    stream.onerror = () => {
      if (source !== stream) return
      status.value = 'reconnecting'
      startPolling()
    }
    stream.addEventListener('infrastructure-snapshot', handleSnapshotEvent)
  }

  function handleSnapshotEvent(event: Event): void {
    if (!('data' in event) || typeof event.data !== 'string') return
    try {
      const value: unknown = JSON.parse(event.data)
      if (isInfrastructureSnapshot(value)) applySnapshot(value)
    } catch {
      error.value = 'A live infrastructure update could not be read.'
    }
  }

  function applySnapshot(value: InfrastructureSnapshot): void {
    snapshot.value = value
    const capturedAt = Date.parse(value.capturedAt)
    const diskAvailable =
      !['UNAVAILABLE', 'UNKNOWN', 'WARMING_UP'].includes(value.diskIo.status ?? '') &&
      value.diskIo.utilizationPercent !== null
    const networkAvailable = !['UNAVAILABLE', 'UNKNOWN', 'WARMING_UP'].includes(
      value.network.status ?? '',
    )
    const current = {
      capturedAt: Number.isFinite(capturedAt) ? capturedAt : Date.now(),
      cpuPercent: value.host.cpuPercent,
      memoryPercent: value.memory.usedPercent,
      diskPercent: diskAvailable ? value.diskIo.utilizationPercent : null,
      networkBytesPerSecond: networkAvailable
        ? value.network.inBytesPerSecond + value.network.outBytesPerSecond
        : null,
    }
    if (samples.value.length === 0) {
      // Only the local mock needs a populated preview; production charts must start from
      // measured samples and never invent a 24-second history on the first snapshot.
      samples.value = DEV_MOCK_ENABLED ? seedSamples(current) : [current]
      return
    }
    samples.value = [...samples.value, current].slice(-MAX_SAMPLES)
  }

  function startPolling(): void {
    status.value = 'polling'
    if (pollTimer) return
    pollTimer = setInterval(() => {
      void refresh().catch(() => undefined)
    }, POLL_INTERVAL_MS)
  }

  function stopPolling(): void {
    if (pollTimer) clearInterval(pollTimer)
    pollTimer = null
  }

  function isInfrastructureSnapshot(value: unknown): value is InfrastructureSnapshot {
    if (!isRecord(value)) return false
    const candidate = value as Record<string, unknown>
    return (
      isTimestamp(candidate.capturedAt) &&
      isHost(candidate.host) &&
      isMemory(candidate.memory) &&
      isDisk(candidate.disk) &&
      isNetwork(candidate.network) &&
      isDiskIo(candidate.diskIo) &&
      Array.isArray(candidate.containers) &&
      candidate.containers.every(isContainer) &&
      (candidate.latestOperation === null || isOperation(candidate.latestOperation)) &&
      isCapacity(candidate.capacity) &&
      Array.isArray(candidate.history) &&
      candidate.history.every(isOperation) &&
      Array.isArray(candidate.incidents) &&
      candidate.incidents.every(isIncident)
    )
  }

  return {
    snapshot: readonly(snapshot),
    samples: readonly(samples),
    status: readonly(status),
    loading: readonly(loading),
    error: readonly(error),
    hasSnapshot,
    start,
    stop,
    refresh,
  }
}

function seedSamples(current: InfrastructureSample): InfrastructureSample[] {
  const cpu = [31, 28, 34, 30, 27, 32, 29, 36, 31, 33, 26, 28, 24, 27, 25, 29, 23, 26, 24, 28, 25, 27, 24, current.cpuPercent]
  const memory = [55, 54, 50, 48, 46, 43, 40, 42, 39, 45, 43, 47, 48, 44, 40, 41, 46, 48, 49, 47, 49, 50, 45, current.memoryPercent]
  const disk = [40, 37, 36, 38, 41, 39, 34, 35, 37, 42, 39, 43, 40, 36, 32, 29, 34, 39, 41, 37, 38, 40, 36, current.diskPercent]
  const network = [1.05, 1.1, 1.2, 1.15, 1.12, 1.04, 1.08, 1.1, 1.07, 1, 0.95, 0.92, 0.96, 0.98, 0.9, 0.87, 0.85, 0.84, 0.86, 0.92, 0.94, 0.9, 0.86, 0.88]
  const samples = Array.from({ length: 24 }, (_, index) => {
    return {
      capturedAt: current.capturedAt - (23 - index) * 1000,
      cpuPercent: cpu[index] ?? current.cpuPercent,
      memoryPercent: memory[index] ?? current.memoryPercent,
      diskPercent: current.diskPercent === null ? null : (disk[index] ?? current.diskPercent),
      networkBytesPerSecond:
        current.networkBytesPerSecond === null
          ? null
          : current.networkBytesPerSecond * (network[index] ?? 1),
    }
  })
  samples[samples.length - 1] = current
  return samples
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

function isNullableFiniteNumber(value: unknown): value is number | null {
  return value === null || isFiniteNumber(value)
}

function isOptionalFiniteNumber(value: unknown): boolean {
  return value === undefined || value === null || isFiniteNumber(value)
}

function isOptionalString(value: unknown): boolean {
  return value === undefined || value === null || typeof value === 'string'
}

function isTimestamp(value: unknown): value is string {
  return typeof value === 'string' && Number.isFinite(Date.parse(value))
}

function isBreakdown(value: unknown): boolean {
  return (
    isRecord(value) &&
    typeof value.key === 'string' &&
    typeof value.label === 'string' &&
    isFiniteNumber(value.usedBytes) &&
    isFiniteNumber(value.percentOfTotal) &&
    isOptionalString(value.source) &&
    isOptionalString(value.status)
  )
}

function isHost(value: unknown): boolean {
  return (
    isRecord(value) &&
    isFiniteNumber(value.cpuPercent) &&
    isFiniteNumber(value.vcpuCount) &&
    isNullableFiniteNumber(value.currentGHz) &&
    isFiniteNumber(value.avgCpuPercent) &&
    isFiniteNumber(value.peakCpuPercent) &&
    isOptionalString(value.status)
  )
}

function isMemory(value: unknown): boolean {
  return (
    isRecord(value) &&
    isFiniteNumber(value.totalBytes) &&
    isFiniteNumber(value.usedBytes) &&
    isFiniteNumber(value.availableBytes) &&
    isFiniteNumber(value.usedPercent) &&
    Array.isArray(value.breakdown) &&
    value.breakdown.every(isBreakdown) &&
    isOptionalString(value.status)
  )
}

function isDisk(value: unknown): boolean {
  return (
    isRecord(value) &&
    isFiniteNumber(value.totalBytes) &&
    isFiniteNumber(value.usedBytes) &&
    isFiniteNumber(value.freeBytes) &&
    isFiniteNumber(value.usedPercent) &&
    Array.isArray(value.breakdown) &&
    value.breakdown.every(isBreakdown) &&
    isOptionalString(value.status)
  )
}

function isNetwork(value: unknown): boolean {
  return (
    isRecord(value) &&
    isFiniteNumber(value.inBytesPerSecond) &&
    isFiniteNumber(value.outBytesPerSecond) &&
    isFiniteNumber(value.droppedPackets) &&
    isOptionalString(value.status)
  )
}

function isDiskIo(value: unknown): boolean {
  return (
    isRecord(value) &&
    isFiniteNumber(value.readBytesPerSecond) &&
    isFiniteNumber(value.writeBytesPerSecond) &&
    isNullableFiniteNumber(value.utilizationPercent) &&
    isOptionalString(value.status)
  )
}

function isContainer(value: unknown): boolean {
  return (
    isRecord(value) &&
    typeof value.name === 'string' &&
    typeof value.healthy === 'boolean' &&
    isFiniteNumber(value.cpuPercent) &&
    isOptionalFiniteNumber(value.memoryUsedBytes) &&
    isOptionalFiniteNumber(value.memoryLimitBytes) &&
    isOptionalFiniteNumber(value.restartCount) &&
    isOptionalFiniteNumber(value.uptimeSeconds) &&
    isOptionalString(value.status) &&
    isOptionalString(value.healthStatus) &&
    (value.healthKnown === undefined ||
      value.healthKnown === null ||
      typeof value.healthKnown === 'boolean')
  )
}

function isOperation(value: unknown): boolean {
  if (!isRecord(value)) return false
  const numericFields = [
    'durationMs',
    'nodes',
    'edges',
    'ramBeforeBytes',
    'ramPeakBytes',
    'ramIncreaseBytes',
    'ramAfterCooldownBytes',
    'cpuAvgPercent',
    'cpuPeakPercent',
    'cpuCoreSeconds',
    'storageAddedBytes',
    'diskReadBytes',
    'diskWriteBytes',
    'concurrentHeavyOperations',
  ]
  return (
    typeof value.id === 'string' &&
    typeof value.projectName === 'string' &&
    isFiniteNumber(value.durationMs) &&
    numericFields.every((field) => isOptionalFiniteNumber(value[field])) &&
    isOptionalString(value.type) &&
    isOptionalString(value.operation) &&
    isOptionalString(value.status) &&
    isOptionalString(value.measurementType) &&
    isOptionalString(value.confidence) &&
    isOptionalString(value.traceId)
  )
}

function isCapacityBoundary(value: unknown): boolean {
  return (
    isRecord(value) &&
    isFiniteNumber(value.nodes) &&
    isFiniteNumber(value.edges) &&
    typeof value.measurementType === 'string' &&
    typeof value.confidence === 'string' &&
    isOptionalString(value.evidenceId)
  )
}

function isCapacity(value: unknown): boolean {
  return (
    isRecord(value) &&
    isFiniteNumber(value.evidenceSamples) &&
    typeof value.confidence === 'string' &&
    isNullableFiniteNumber(value.safeHeadroomPercent) &&
    (value.mcpSafe === null || isCapacityBoundary(value.mcpSafe)) &&
    (value.graphApi === null || isCapacityBoundary(value.graphApi)) &&
    (value.analyzeObservedSafe === null || isCapacityBoundary(value.analyzeObservedSafe)) &&
    isOptionalString(value.heavyConcurrency) &&
    isOptionalString(value.status)
  )
}

function isIncident(value: unknown): boolean {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    isTimestamp(value.occurredAt) &&
    typeof value.reason === 'string' &&
    typeof value.status === 'string' &&
    isOptionalString(value.projectName) &&
    isOptionalString(value.operationType) &&
    isOptionalString(value.evidenceId)
  )
}
