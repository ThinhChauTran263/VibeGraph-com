<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useInfrastructureMonitor } from '@/composables/useInfrastructureMonitor'
import type {
  InfrastructureBreakdownItem,
  InfrastructureContainerSnapshot,
  InfrastructureOperationSnapshot,
  InfrastructureOperationType,
} from '@/types/infrastructure'
import {
  bytes,
  chartPoints,
  duration,
  isAvailable,
  metricRate,
  operationStorage,
  percent,
  preciseBytes,
  rate,
  type MonitorChartKey,
  type MonitorSeriesKey,
} from './infrastructure-formatters'
import VpsMonitorHistory from './VpsMonitorHistory.vue'

const monitor = useInfrastructureMonitor()
const selectedSeries = ref<MonitorSeriesKey>('all')
const historyFilter = ref<'ALL' | InfrastructureOperationType>('ALL')

const snapshot = monitor.snapshot
const host = computed(() => snapshot.value.host)
const memory = computed(() => snapshot.value.memory)
const memoryBreakdown = computed(() => groupBreakdownByLabel(memory.value.breakdown))
const dockerServices = computed(() => groupContainersByName(snapshot.value.containers))
const disk = computed(() => snapshot.value.disk)
const network = computed(() => snapshot.value.network)
const diskIo = computed(() => snapshot.value.diskIo)
const cpuDelta = computed(() => {
  const values = monitor.samples.value
  if (values.length < 2) return 0
  const current = values[values.length - 1]
  const previous = values[values.length - 2]
  if (!current || !previous) return 0
  return current.cpuPercent - previous.cpuPercent
})
const history = computed<InfrastructureOperationSnapshot[]>(() =>
  historyFilter.value === 'ALL'
    ? [...snapshot.value.history]
    : snapshot.value.history.filter((item) => item.type === historyFilter.value),
)
const latest = computed(() => snapshot.value.latestOperation)
const latestIncident = computed(() => snapshot.value.incidents[0] ?? null)
const dockerPage = ref(0)
const dockerPageSize = ref(5)
const dockerPageCount = computed(() =>
  Math.max(1, Math.ceil(dockerServices.value.length / dockerPageSize.value)),
)
const dockerPageItems = computed(() => {
  const start = dockerPage.value * dockerPageSize.value
  return dockerServices.value.slice(start, start + dockerPageSize.value)
})
watch(dockerPageCount, (pageCount) => {
  dockerPage.value = Math.min(dockerPage.value, pageCount - 1)
})
const capacityRingStyle = computed(() => {
  const value = snapshot.value.capacity.safeHeadroomPercent
  if (value === null || !Number.isFinite(value)) {
    return { background: 'conic-gradient(var(--vg-border-strong) 0 100%)' }
  }
  const bounded = Math.max(0, Math.min(100, value))
  return {
    background: `conic-gradient(var(--vg-green) 0 ${bounded}%, #1b3330 ${bounded}% 100%)`,
  }
})

function points(key: MonitorChartKey): string {
  return chartPoints(monitor.samples.value, key, diskIo.value.status, network.value.status)
}

function miniPoints(key: 'cpuPercent' | 'memoryPercent'): string {
  const values = monitor.samples.value.slice(-18)
  if (values.length < 2) return ''
  const width = 220
  const height = 28
  return values
    .map((sample, index) => {
      const x = (index / (values.length - 1)) * width
      const y = height - (Math.max(0, Math.min(100, sample[key])) / 100) * (height - 4) - 2
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

function seriesClass(name: string): string {
  return selectedSeries.value === 'all' || selectedSeries.value === name ? 'is-active' : 'is-muted'
}

function healthClass(value?: string): string {
  return `health-${(value ?? 'UNKNOWN').toLowerCase()}`
}

function operationProjectLabel(operation: InfrastructureOperationSnapshot): string {
  return operation.projectName ?? operation.projectId ?? 'Unknown project'
}

function capacityEvidenceLabel(
  boundary: { measurementType?: string } | null | undefined,
  recordedLabel: string,
): string {
  return boundary?.measurementType === 'CONFIGURED' ? 'configured ceiling' : recordedLabel
}

function handleVisibility(): void {
  if (document.visibilityState === 'visible') monitor.start()
  else monitor.stop()
}

function updateDockerPageSize(): void {
  const nextSize =
    window.innerWidth < 680
      ? 1
      : window.innerWidth < 1100
        ? 3
        : window.innerWidth < 1500
          ? 6
          : window.innerWidth < 1800
            ? 7
            : 8
  dockerPageSize.value = nextSize
  dockerPage.value = Math.min(dockerPage.value, dockerPageCount.value - 1)
}

function moveDockerPage(direction: -1 | 1): void {
  dockerPage.value = Math.max(0, Math.min(dockerPage.value + direction, dockerPageCount.value - 1))
}

function groupBreakdownByLabel(
  items: readonly InfrastructureBreakdownItem[],
): InfrastructureBreakdownItem[] {
  const grouped = new Map<string, InfrastructureBreakdownItem>()
  for (const item of items) {
    const groupKey = item.label.trim().toLowerCase()
    const current = grouped.get(groupKey)
    if (current) {
      current.usedBytes += item.usedBytes
      current.percentOfTotal += item.percentOfTotal
      continue
    }
    grouped.set(groupKey, { ...item, key: groupKey })
  }
  return [...grouped.values()]
}

function groupContainersByName(
  items: readonly InfrastructureContainerSnapshot[],
): InfrastructureContainerSnapshot[] {
  const grouped = new Map<string, InfrastructureContainerSnapshot>()
  for (const item of items) {
    const groupKey = item.name.trim().toLowerCase()
    const current = grouped.get(groupKey)
    if (current) {
      current.memoryUsedBytes = (current.memoryUsedBytes ?? 0) + (item.memoryUsedBytes ?? 0)
      current.cpuPercent += item.cpuPercent
      current.restartCount =
        current.restartCount == null || item.restartCount == null
          ? null
          : current.restartCount + item.restartCount
      current.healthKnown = Boolean(current.healthKnown && item.healthKnown)
      current.healthy = current.healthy && item.healthy
      if (!current.healthKnown) current.healthStatus = null
      continue
    }
    grouped.set(groupKey, { ...item })
  }
  return [...grouped.values()]
}

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibility)
  window.addEventListener('resize', updateDockerPageSize)
  updateDockerPageSize()
  monitor.start()
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibility)
  window.removeEventListener('resize', updateDockerPageSize)
  monitor.stop()
})
</script>

<template>
  <div class="admin-page vps-monitor-page">
    <header class="monitor-title">
      <div>
        <p>Live host health, project resource cost and verified capacity.</p>
      </div>
      <span class="monitor-live" :class="`live-${monitor.status.value}`" aria-live="polite">
        <i></i
        >{{
          monitor.status.value === 'connected' ? 'REALTIME' : monitor.status.value.toUpperCase()
        }}
      </span>
    </header>

    <div v-if="monitor.error.value" class="monitor-notice" role="alert">
      {{ monitor.error.value }}
    </div>
    <div
      v-if="monitor.loading.value && !monitor.hasSnapshot.value"
      class="monitor-notice"
      role="status"
    >
      Loading infrastructure metrics…
    </div>

    <section class="monitor-kpis" aria-label="Host summary metrics">
      <article class="monitor-kpi kpi-cpu">
        <div class="kpi-label">
            <span>CPU LOAD</span><b>{{ cpuDelta >= 0 ? '+' : '' }}{{ cpuDelta.toFixed(1) }}%</b>
        </div>
        <div class="kpi-value"><strong>{{ percent(host.cpuPercent) }}</strong><span>/ {{ host.vcpuCount || '—' }} vCPU</span></div>
        <svg class="kpi-spark" viewBox="0 0 220 28" preserveAspectRatio="none" aria-hidden="true">
          <polyline :points="miniPoints('cpuPercent')" />
        </svg>
        <small
          >{{
            host.currentGHz === null
              ? 'frequency unavailable'
              : `${host.currentGHz.toFixed(2)} GHz current`
          }}
          · avg {{ percent(host.avgCpuPercent) }} · peak {{ percent(host.peakCpuPercent) }}</small
        >
      </article>
      <article class="monitor-kpi kpi-ram">
        <div class="kpi-label">
          MEMORY <b>{{ bytes(memory.availableBytes) }} free</b>
        </div>
        <div class="kpi-value"><strong>{{ bytes(memory.usedBytes) }}</strong><span>/ {{ bytes(memory.totalBytes) }}</span></div>
        <svg class="kpi-spark" viewBox="0 0 220 28" preserveAspectRatio="none" aria-hidden="true">
          <polyline :points="miniPoints('memoryPercent')" />
        </svg>
        <small
          >{{ percent(memory.usedPercent) }} used ·
          {{ bytes(memory.availableBytes) }} available</small
        >
      </article>
      <article class="monitor-kpi kpi-disk">
        <div class="kpi-label">
          SSD CAPACITY <b>{{ disk.status === 'MEASURED' ? 'healthy' : disk.status }}</b>
        </div>
        <div class="kpi-value"><strong>{{ percent(disk.usedPercent) }}</strong><span>used</span></div>
        <div class="kpi-bar">
          <i :style="{ width: `${Math.min(100, Math.max(0, disk.usedPercent))}%` }"></i>
        </div>
        <small
          >{{ bytes(disk.usedBytes) }} used of {{ bytes(disk.totalBytes) }} ·
          <em class="disk-free">{{ bytes(disk.freeBytes) }} free</em></small
        >
      </article>
      <article class="monitor-kpi kpi-net">
        <div class="kpi-label">
          NETWORK <b>{{ network.status === 'MEASURED' ? 'stable' : network.status }}</b>
        </div>
        <div class="kpi-value"><strong>{{ metricRate(network.outBytesPerSecond, network.status) }}</strong><span>out</span></div>
        <small>{{
          isAvailable(network.status)
            ? `in ${rate(network.inBytesPerSecond)} · out ${rate(network.outBytesPerSecond)} · ${network.droppedPackets} drops`
            : 'Network source unavailable'
        }}</small>
      </article>
    </section>

    <section class="monitor-grid">
      <article class="monitor-panel performance-panel">
        <div class="panel-heading">
          <div>
            <h3>Performance</h3>
            <p>host activity · 1 second samples · last 60 seconds</p>
          </div>
          <div class="series-tabs" role="group" aria-label="Performance series">
            <button
              v-for="series in ['all', 'cpu', 'ram', 'net', 'disk']"
              :key="series"
              type="button"
              :class="{ active: selectedSeries === series }"
              @click="selectedSeries = series as MonitorSeriesKey"
            >
              {{ series.toUpperCase() }}
            </button>
          </div>
        </div>
        <div class="performance-chart">
          <svg
            viewBox="0 0 720 235"
            preserveAspectRatio="none"
            role="img"
            aria-label="Realtime CPU, RAM, disk I/O and network chart"
          >
            <line
              v-for="y in [26, 85, 144, 203]"
              :key="y"
              x1="0"
              :y1="y"
              x2="720"
              :y2="y"
              class="chart-gridline"
            />
            <text x="4" y="19">100%</text>
            <text x="4" y="78">75</text>
            <text x="4" y="137">50</text>
            <text x="4" y="196">25</text>
            <polyline
              :points="points('cpuPercent')"
              :class="['chart-series', 'cpu', seriesClass('cpu')]"
            />
            <polyline
              :points="points('memoryPercent')"
              :class="['chart-series', 'ram', seriesClass('ram')]"
            />
            <polyline
              :points="points('diskPercent')"
              :class="['chart-series', 'disk', seriesClass('disk')]"
            />
            <polyline
              :points="points('networkBytesPerSecond')"
              :class="['chart-series', 'net', seriesClass('net')]"
            />
          </svg>
          <div v-if="monitor.samples.value.length" class="chart-now">
            NOW · CPU {{ percent(host.cpuPercent) }} · RAM {{ bytes(memory.usedBytes) }} · Disk
            {{ metricRate(diskIo.readBytesPerSecond, diskIo.status) }} · Net
            {{
              isAvailable(network.status)
            ? `in ${rate(network.inBytesPerSecond)} · out ${rate(network.outBytesPerSecond)}`
                : 'UNAVAILABLE'
            }}
          </div>
        </div>
        <div class="chart-legend">
          <span><i class="cpu"></i><strong>CPU usage </strong><em>{{ percent(host.cpuPercent) }}</em></span>
          <span><i class="ram"></i><strong>RAM used </strong><em>{{ bytes(memory.usedBytes) }} / {{ bytes(memory.totalBytes) }}</em></span>
          <span><i class="disk"></i><strong>Disk I/O </strong><em>{{ metricRate(diskIo.readBytesPerSecond, diskIo.status) }}</em></span>
          <span><i class="net"></i><strong>Network </strong><em>{{
              isAvailable(network.status)
                ? `in ${rate(network.inBytesPerSecond)} · out ${rate(network.outBytesPerSecond)}`
                : 'UNAVAILABLE'
            }}</em></span>
        </div>
        <div class="chart-foot" aria-label="Chart time range">
          <span>60 seconds ago</span>
          <span>normalized utilization · hover for raw values</span>
          <span>now</span>
        </div>
      </article>

      <article class="monitor-panel ram-panel">
        <div class="panel-heading">
          <div>
            <h3>VPS RAM now</h3>
            <p>what is using the {{ bytes(memory.totalBytes) }} host memory</p>
          </div>
          <span :class="['health-chip', healthClass(memory.status)]">● {{ memory.status }}</span>
        </div>
        <div class="big-value">
          {{ bytes(memory.usedBytes) }} <small>used of {{ bytes(memory.totalBytes) }}</small>
        </div>
        <div class="stack-bar">
          <i
            v-for="(item, index) in memoryBreakdown"
            :key="item.key"
            :class="`breakdown-fill-${index % 4}`"
            :style="{ width: `${item.percentOfTotal ?? 0}%` }"
          ></i>
        </div>
        <div
          class="breakdown-list"
          role="region"
          aria-label="VPS RAM usage breakdown"
          tabindex="0"
        >
          <div v-for="(item, index) in memoryBreakdown" :key="item.key">
            <span><i :class="`breakdown-color-${index % 4}`"></i>{{ item.label }}</span
            ><b>{{ bytes(item.usedBytes) }}</b>
          </div>
        </div>
        <div class="available-row">
          <span>VPS RAM available</span><b>{{ bytes(memory.availableBytes) }}</b>
        </div>
      </article>
    </section>

    <section class="monitor-panel disk-panel">
      <div class="panel-heading">
        <div>
          <h3>VPS disk now</h3>
          <p>where the {{ bytes(disk.usedBytes) }} used space is going</p>
        </div>
        <span :class="['health-chip', healthClass(disk.status)]">● {{ disk.status }}</span>
      </div>
      <div class="disk-total">
        <strong>{{ bytes(disk.usedBytes) }}</strong
        ><span
          >used of {{ bytes(disk.totalBytes) }} · {{ percent(disk.usedPercent) }} ·
          <em class="disk-free">{{ bytes(disk.freeBytes) }} free</em></span
        >
      </div>
      <div class="disk-breakdown-bar">
        <i
          v-for="(item, index) in disk.breakdown"
          :key="item.key"
          :class="`disk-fill-${index % 7}`"
          :style="{ width: `${item.percentOfTotal ?? 0}%` }"
        ></i>
      </div>
        <div class="disk-list">
          <div v-for="(item, index) in disk.breakdown" :key="item.key">
            <span><i :class="`disk-color-${index % 7}`"></i>{{ item.label }}</span>
            <b>{{ bytes(item.usedBytes) }} · {{ percent(item.percentOfTotal) }}</b>
          </div>
        </div>
    </section>

    <section class="monitor-grid operation-grid">
      <article v-if="latest" class="monitor-panel operation-panel">
        <div class="operation-heading">
          <div>
            <div class="operation-title">
              <span class="operation-icon" aria-hidden="true">A</span>
              <div>
                <h3>
                  {{ operationProjectLabel(latest) }} · {{ latest.operation ?? latest.type }}
                </h3>
                <p class="operation-subtitle">
                  {{ (latest.nodes ?? latest.nodeCount ?? 0).toLocaleString() }} nodes ·
                  {{ (latest.edges ?? latest.edgeCount ?? 0).toLocaleString() }} edges · observed
                  production operation
                </p>
              </div>
            </div>
          </div>
          <span
            :class="[
              'health-chip',
              latest.status === 'SUCCESS' ? 'health-healthy' : 'health-critical',
            ]"
            >● {{ latest.status }}</span
          >
        </div>
        <div class="cost-grid">
          <div>
            <label>RAM during operation</label
            ><strong
              >{{ preciseBytes(latest.ramBeforeBytes) }} →
              {{ preciseBytes(latest.ramPeakBytes) }}</strong
            ><small
              ><em class="ram-peak">+{{ bytes(latest.ramIncreaseBytes) }} peak</em> · after cooldown
              {{ preciseBytes(latest.ramAfterCooldownBytes) }}</small
            >
          </div>
          <div>
            <label>CPU avg / peak</label
            ><strong
              >{{ percent(latest.cpuAvgPercent) }} / {{ percent(latest.cpuPeakPercent) }}</strong
            ><small>{{ latest.cpuCoreSeconds ?? '—' }} core-seconds</small>
          </div>
          <div>
            <label>Storage added</label
            ><strong>{{ operationStorage(latest.storageAddedBytes, latest.type) }}</strong
            ><small>{{
              latest.storageAddedBytes && latest.storageAddedBytes > 0
                ? 'source · cache · graph'
                : latest.type === 'API' || latest.type === 'MCP'
                  ? 'read-only operation'
                  : 'source did not report a delta'
            }}</small>
          </div>
          <div>
            <label>Operation time</label><strong>{{ duration(latest.durationMs) }}</strong
            ><small>{{ latest.status }}</small>
          </div>
        </div>
        <div class="operation-trace">
          <span>VPS RAM during this operation</span
          ><b
            >Peak impact <em>+{{ bytes(latest.ramIncreaseBytes) }}</em></b
          >
          <div class="trace-bar"><i></i><i></i><i></i></div>
          <small
            >Before {{ preciseBytes(latest.ramBeforeBytes) }} · Peak
            {{ preciseBytes(latest.ramPeakBytes) }} · After cooldown
            {{ preciseBytes(latest.ramAfterCooldownBytes) }}</small
          >
        </div>
        <div class="evidence">
          <span>Confidence {{ latest.confidence ?? 'UNKNOWN' }}</span
          ><span>Evidence {{ latest.traceId ?? '—' }}</span
          ><span>Concurrent heavy ops {{ latest.concurrentHeavyOperations ?? '—' }}</span
          ><span>Backend {{ latest.backendVersion ?? '—' }}</span>
        </div>
      </article>
      <article v-else class="monitor-panel empty-operation" role="status">
        <h3>No recorded operations yet</h3>
        <p>Operation evidence will appear after Analyze, MCP, import or Graph API traffic.</p>
      </article>
      <article class="monitor-panel capacity-panel">
        <div class="panel-heading">
          <div>
            <h3>Verified VPS capacity</h3>
            <p>learned from real user operations</p>
          </div>
          <span :class="['health-chip', healthClass(snapshot.capacity.status)]"
            >● {{ snapshot.capacity.status ?? 'LEARNING' }}</span
          >
        </div>
        <div class="capacity-score">
          <div class="capacity-ring" :style="capacityRingStyle">
            <b>{{
              snapshot.capacity.safeHeadroomPercent === null
                ? '—'
                : `${snapshot.capacity.safeHeadroomPercent}%`
            }}</b>
          </div>
          <div>
            <strong>Safe headroom</strong
            ><span
              >{{ snapshot.capacity.evidenceSamples }} evidence samples ·
              {{ snapshot.capacity.confidence.toLowerCase() }} confidence</span
            >
          </div>
        </div>
        <div class="capacity-row">
          <span
            >MCP safe limit<small>{{
              capacityEvidenceLabel(snapshot.capacity.mcpSafe, 'recorded evidence')
            }}</small></span
          ><b
            >{{
              snapshot.capacity.mcpSafe?.nodes
                ? `${(snapshot.capacity.mcpSafe?.nodes / 1000).toFixed(0)}k nodes`
                : '—'
            }}<br />{{
              snapshot.capacity.mcpSafe?.edges
                ? `${(snapshot.capacity.mcpSafe?.edges / 1000).toFixed(0)}k edges`
                : '—'
            }}</b
          >
        </div>
        <div class="capacity-row">
          <span
            >Graph API<small>{{
              capacityEvidenceLabel(snapshot.capacity.graphApi, 'recorded evidence')
            }}</small></span
          ><b
            >{{
              snapshot.capacity.graphApi?.nodes
                ? `${(snapshot.capacity.graphApi?.nodes / 1000).toFixed(0)}k nodes`
                : '—'
            }}<br />{{
              snapshot.capacity.graphApi?.edges
                ? `${(snapshot.capacity.graphApi?.edges / 1000).toFixed(0)}k edges`
                : '—'
            }}</b
          >
        </div>
        <div class="capacity-row">
          <span>Analyze observed safe<small>largest healthy run</small></span
          ><b
            >{{
              snapshot.capacity.analyzeObservedSafe?.nodes
                ? `${(snapshot.capacity.analyzeObservedSafe?.nodes / 1000).toFixed(0)}k nodes`
                : '—'
            }}<br />{{
              snapshot.capacity.analyzeObservedSafe?.edges
                ? `${(snapshot.capacity.analyzeObservedSafe?.edges / 1000).toFixed(0)}k edges`
                : '—'
            }}</b
          >
        </div>
        <div class="capacity-row">
          <span>Heavy concurrency<small>4 vCPU / 8 GB VPS</small></span
          ><b class="nowrap">{{ snapshot.capacity.heavyConcurrency ?? '—' }}</b>
        </div>
      </article>
      <article class="monitor-panel docker-panel">
        <div class="panel-heading">
          <div>
            <h3>Docker services</h3>
            <p>live container RAM and health</p>
          </div>
          <span
            :class="[
              'health-chip',
              dockerServices.length && dockerServices.every((item) => item.healthKnown)
                ? 'health-healthy'
                : 'health-unknown',
            ]"
            >{{
              dockerServices.length && dockerServices.every((item) => item.healthKnown)
                ? `● ${dockerServices.filter((item) => item.healthy).length} / ${dockerServices.length} HEALTHY`
                : dockerServices.length
                  ? '● RUNNING · HEALTH UNKNOWN'
                  : '● UNAVAILABLE'
            }}</span
          >
        </div>
        <div
          v-if="dockerServices.length"
          class="services-viewport"
          role="region"
          aria-label="Docker service metrics"
          tabindex="0"
        >
          <div v-if="dockerPageCount > 1" class="services-toolbar">
            <button
              type="button"
              class="services-arrow"
              aria-label="Previous Docker service row"
              :disabled="dockerPage === 0"
              @click="moveDockerPage(-1)"
            >
              ←
            </button>
            <span>{{ dockerPage + 1 }} / {{ dockerPageCount }}</span>
            <button
              type="button"
              class="services-arrow"
              aria-label="Next Docker service row"
              :disabled="dockerPage >= dockerPageCount - 1"
              @click="moveDockerPage(1)"
            >
              →
            </button>
          </div>
          <div
            class="services-grid"
            :style="{ gridTemplateColumns: `repeat(${dockerPageSize}, minmax(0, 1fr))` }"
          >
            <div
              v-for="(service, index) in dockerPageItems"
              :key="`${dockerPage}-${index}-${service.name}`"
              class="service-card"
            >
              <div>
                <strong>{{ service.name }}</strong
                ><i :class="{ healthy: service.healthy }"></i>
              </div>
              <b>{{ bytes(service.memoryUsedBytes) }} RAM</b
              ><small
                >CPU {{ percent(service.cpuPercent) }} ·
                {{
                  service.restartCount == null
                    ? 'restarts unavailable'
                    : `${service.restartCount} restarts`
                }}
                ·
                {{
                  service.healthKnown
                    ? (service.healthStatus ?? 'health reported')
                    : 'health unknown'
                }}</small
              >
            </div>
          </div>
        </div>
        <p v-else class="empty-service-state">
          Container metrics unavailable from the configured host source.
        </p>
      </article>
    </section>

    <VpsMonitorHistory
      :history="history"
      :history-filter="historyFilter"
      @update:history-filter="historyFilter = $event"
    />
    <aside v-if="latestIncident" class="monitor-incident" role="alert">
      <strong>Operation stopped</strong>
      <span>{{ latestIncident.reason }} · Evidence {{ latestIncident.evidenceId }}</span>
    </aside>
    <footer class="monitor-footer">
      <span>{{
        snapshot.incidents.length
          ? `${snapshot.incidents.length} active incident(s)`
          : 'No active incidents · alerts appear only after an operation is stopped'
      }}</span
      ><span
        >captured
        {{
          snapshot.capturedAt === new Date(0).toISOString()
            ? '—'
            : new Date(snapshot.capturedAt).toLocaleTimeString()
        }}</span
      >
    </footer>
  </div>
</template>

<style scoped>
@import './vps-monitor.css';
</style>
