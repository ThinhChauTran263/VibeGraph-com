<script setup lang="ts">
import { ref } from 'vue'
import type {
  InfrastructureOperationSnapshot,
  InfrastructureOperationType,
} from '@/types/infrastructure'
import {
  duration,
  operationStorage,
  percent,
  preciseBytes,
} from './infrastructure-formatters'

const props = defineProps<{
  history: readonly InfrastructureOperationSnapshot[]
  historyFilter: 'ALL' | InfrastructureOperationType
}>()

const emit = defineEmits<{
  'update:historyFilter': [value: 'ALL' | InfrastructureOperationType]
}>()

const root = ref<HTMLElement | null>(null)

defineExpose({
  scrollIntoView(options?: ScrollIntoViewOptions) {
    root.value?.scrollIntoView(options)
  },
})

function operationRam(operation: InfrastructureOperationSnapshot): string {
  return [
    preciseBytes(operation.ramBeforeBytes),
    preciseBytes(operation.ramPeakBytes),
    preciseBytes(operation.ramAfterCooldownBytes),
  ].join(' → ')
}

function operationProjectLabel(operation: InfrastructureOperationSnapshot): string {
  return operation.projectName ?? operation.projectId ?? 'Unknown project'
}
</script>

<template>
  <section ref="root" class="monitor-panel history-panel">
    <div class="panel-heading">
      <div>
        <h3>Project resource history</h3>
        <p>automatically recorded from real operations</p>
      </div>
      <div class="history-tabs">
        <button
          v-for="filter in ['ALL', 'ANALYZE', 'MCP', 'API']"
          :key="filter"
          type="button"
          :class="{ active: props.historyFilter === filter }"
          @click="emit('update:historyFilter', filter as 'ALL' | InfrastructureOperationType)"
        >
          {{ filter }}
        </button>
      </div>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Project / operation</th>
            <th>Graph size</th>
            <th>RAM before → peak → after</th>
            <th>CPU avg / peak</th>
            <th>Duration</th>
            <th>Storage</th>
            <th>Evidence</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="operation in props.history" :key="operation.id">
            <td data-label="Project / operation">
              {{ operationProjectLabel(operation) }} · {{ operation.operation ?? operation.type }}
            </td>
            <td data-label="Graph size">
              {{ (operation.nodes ?? operation.nodeCount ?? 0).toLocaleString() }} /
              {{ (operation.edges ?? operation.edgeCount ?? 0).toLocaleString() }}
            </td>
            <td data-label="RAM before → peak → after">{{ operationRam(operation) }}</td>
            <td data-label="CPU avg / peak">
              {{ percent(operation.cpuAvgPercent) }} / {{ percent(operation.cpuPeakPercent) }}
            </td>
            <td data-label="Duration">{{ duration(operation.durationMs) }}</td>
            <td data-label="Storage">
              {{ operationStorage(operation.storageAddedBytes, operation.type) }}
            </td>
            <td data-label="Evidence">
              {{ operation.confidence ?? '—' }} · {{ operation.traceId ?? '—' }}
            </td>
            <td
              data-label="Status"
              :class="operation.status === 'SUCCESS' ? 'success-text' : 'critical-text'"
            >
              {{ operation.status }}
            </td>
          </tr>
          <tr v-if="props.history.length === 0">
            <td colspan="8" class="empty-row">No operation evidence recorded yet.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
@import './vps-monitor-history.css';
</style>
