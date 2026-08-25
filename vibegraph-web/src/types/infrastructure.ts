export type InfrastructureMeasurement = 'MEASURED' | 'OBSERVED' | 'ESTIMATED' | 'VALIDATED'
export type InfrastructureHealth = 'HEALTHY' | 'WATCH' | 'CRITICAL' | 'UNKNOWN'
export type InfrastructureOperationType = 'ANALYZE' | 'MCP' | 'API' | 'IMPORT' | 'OTHER'

export interface InfrastructureBreakdownItem {
  key: string
  label: string
  usedBytes: number
  percentOfTotal: number
  source?: string
  status?: string
}

export interface InfrastructureHostSnapshot {
  cpuPercent: number
  vcpuCount: number
  currentGHz: number | null
  avgCpuPercent: number
  peakCpuPercent: number
  status?: string
}

export interface InfrastructureMemorySnapshot {
  totalBytes: number
  usedBytes: number
  availableBytes: number
  usedPercent: number
  breakdown: InfrastructureBreakdownItem[]
  status?: string
}

export interface InfrastructureDiskSnapshot {
  totalBytes: number
  usedBytes: number
  freeBytes: number
  usedPercent: number
  breakdown: InfrastructureBreakdownItem[]
  status?: string
}

export interface InfrastructureNetworkSnapshot {
  inBytesPerSecond: number
  outBytesPerSecond: number
  droppedPackets: number
  status?: string
}

export interface InfrastructureDiskIoSnapshot {
  readBytesPerSecond: number
  writeBytesPerSecond: number
  utilizationPercent: number | null
  status?: string
}

export interface InfrastructureContainerSnapshot {
  name: string
  status?: string
  healthy: boolean
  healthKnown?: boolean | null
  healthStatus?: string | null
  memoryUsedBytes?: number
  memoryBytes?: number
  memoryLimitBytes?: number | null
  cpuPercent: number
  restartCount?: number | null
  uptimeSeconds?: number | null
  source?: string
}

export interface InfrastructureOperationSnapshot {
  id: string
  traceId?: string | null
  projectId?: string | null
  projectName: string
  type?: InfrastructureOperationType
  operation?: string
  status?: string
  startedAt?: string | null
  completedAt?: string | null
  durationMs: number
  nodes?: number
  edges?: number
  ramBeforeBytes?: number
  ramPeakBytes?: number
  ramIncreaseBytes?: number
  ramAfterCooldownBytes?: number
  cooldownComplete?: boolean
  cpuAvgPercent?: number
  cpuPeakPercent?: number
  cpuCoreSeconds?: number | null
  storageAddedBytes?: number | null
  diskReadBytes?: number
  diskWriteBytes?: number
  concurrentHeavyOperations?: number
  backendVersion?: string | null
  measurementType?: InfrastructureMeasurement | string
  confidence?: string
  stopReason?: string | null
  operationType?: InfrastructureOperationType
  operationLabel?: string | null
  nodeCount?: number
  edgeCount?: number
  ramDeltaBytes?: number
  ramAfterBytes?: number
  cpuAveragePercent?: number
  evidenceId?: string | null
}

export interface InfrastructureCapacityBoundary {
  nodes: number
  edges: number
  measurementType: InfrastructureMeasurement | string
  confidence: string
  evidenceId?: string | null
}

export interface InfrastructureCapacitySnapshot {
  status?: string
  evidenceSamples: number
  confidence: string
  safeHeadroomPercent: number | null
  mcpSafe: InfrastructureCapacityBoundary | null
  graphApi: InfrastructureCapacityBoundary | null
  analyzeObservedSafe: InfrastructureCapacityBoundary | null
  heavyConcurrency: string | null
  evidenceSampleCount?: number
  mcpSafeNodes?: number | null
  mcpSafeEdges?: number | null
  graphApiNodes?: number | null
  graphApiEdges?: number | null
  analyzeObservedNodes?: number | null
  analyzeObservedEdges?: number | null
  heavyConcurrencyLabel?: string | null
}

export interface InfrastructureIncidentSnapshot {
  id: string
  occurredAt: string
  projectName: string | null
  operationType: InfrastructureOperationType | null
  reason: string
  evidenceId: string | null
  status: string
}

export interface InfrastructureSnapshot {
  capturedAt: string
  status?: string
  health?: InfrastructureHealth
  host: InfrastructureHostSnapshot
  memory: InfrastructureMemorySnapshot
  disk: InfrastructureDiskSnapshot
  network: InfrastructureNetworkSnapshot
  diskIo: InfrastructureDiskIoSnapshot
  containers: InfrastructureContainerSnapshot[]
  latestOperation: InfrastructureOperationSnapshot | null
  capacity: InfrastructureCapacitySnapshot
  history: InfrastructureOperationSnapshot[]
  incidents: InfrastructureIncidentSnapshot[]
}

export const emptyInfrastructureSnapshot = (): InfrastructureSnapshot => ({
  capturedAt: new Date(0).toISOString(),
  status: 'UNKNOWN',
  host: {
    cpuPercent: 0,
    vcpuCount: 0,
    currentGHz: null,
    avgCpuPercent: 0,
    peakCpuPercent: 0,
    status: 'UNKNOWN',
  },
  memory: {
    totalBytes: 0,
    usedBytes: 0,
    availableBytes: 0,
    usedPercent: 0,
    breakdown: [],
    status: 'UNKNOWN',
  },
  disk: {
    totalBytes: 0,
    usedBytes: 0,
    freeBytes: 0,
    usedPercent: 0,
    breakdown: [],
    status: 'UNKNOWN',
  },
  network: { inBytesPerSecond: 0, outBytesPerSecond: 0, droppedPackets: 0, status: 'UNKNOWN' },
  diskIo: {
    readBytesPerSecond: 0,
    writeBytesPerSecond: 0,
    utilizationPercent: 0,
    status: 'UNKNOWN',
  },
  containers: [],
  latestOperation: null,
  capacity: {
    status: 'LEARNING',
    evidenceSamples: 0,
    confidence: 'UNKNOWN',
    safeHeadroomPercent: null,
    mcpSafe: null,
    graphApi: null,
    analyzeObservedSafe: null,
    heavyConcurrency: null,
  },
  history: [],
  incidents: [],
})
