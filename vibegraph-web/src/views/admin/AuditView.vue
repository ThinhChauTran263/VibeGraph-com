<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminStore } from '@/stores/admin'
import type { AdminAuditLog } from '@/types/api'
import ThemedSelect from '@/components/ui/ThemedSelect.vue'
import { useSilentRefresh } from '@/composables/useSilentRefresh'

const { locale, t } = useI18n({ useScope: 'global' })
const admin = useAdminStore()
const loading = ref(true)
const savingRetention = ref(false)
const error = ref('')
const message = ref('')
const action = ref('')
const outcome = ref('')
const fromDate = ref('')
const toDate = ref('')
const retentionDays = ref(90)
const detailPanelRef = ref<HTMLElement | null>(null)
let isActive = true

const pageNumber = computed(
  () => admin.auditPagination.pageNumber ?? admin.auditPagination.page ?? 0,
)
const pageSize = computed(() => admin.auditPagination.pageSize ?? admin.auditPagination.size ?? 50)
const liveStatusLabel = computed(() => {
  if (admin.auditLiveStatus === 'connected') return t('admin.audit.status.liveConnected')
  if (admin.auditLiveStatus === 'polling') return t('admin.audit.status.polling')
  if (admin.auditLiveStatus === 'reconnecting') return t('admin.audit.status.reconnecting')
  return t('admin.audit.status.livePaused')
})
const outcomeOptions = computed(() => [
  { value: '', label: t('admin.audit.filters.allOutcomes') },
  { value: 'SUCCESS', label: t('admin.audit.filters.success') },
  { value: 'FAILURE', label: t('admin.audit.filters.failure') },
])

onMounted(async () => {
  await load()
  if (isActive) admin.startAuditStream()
})
onBeforeUnmount(() => {
  isActive = false
  admin.stopAuditStream()
})

useSilentRefresh(async () => {
  try {
    await load(pageNumber.value)
    if (admin.auditLogDetail) {
      await admin.fetchAuditLogDetail(admin.auditLogDetail.id)
    }
  } catch {
    // Silent failure
  }
})

async function load(page = 0): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([
      admin.fetchAuditLogs({
        action: action.value.trim() || undefined,
        outcome: outcome.value || undefined,
        from: fromDate.value ? new Date(`${fromDate.value}T00:00:00`).toISOString() : undefined,
        to: toDate.value ? new Date(`${toDate.value}T23:59:59`).toISOString() : undefined,
        page,
        size: pageSize.value,
      }),
      admin.fetchAuditRetention(),
    ])
    retentionDays.value = admin.auditRetention?.retentionDays ?? 90
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('admin.audit.errors.loadData')
  } finally {
    loading.value = false
  }
}

async function openDetail(id: string): Promise<void> {
  try {
    await admin.fetchAuditLogDetail(id)
    await nextTick()
    detailPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    detailPanelRef.value?.focus({ preventScroll: true })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('admin.audit.errors.loadDetail')
  }
}
async function saveRetention(): Promise<void> {
  savingRetention.value = true
  error.value = ''
  message.value = ''
  try {
    await admin.updateAuditRetention(retentionDays.value)
    message.value = t('admin.audit.messages.retentionUpdated', { days: retentionDays.value })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('admin.audit.errors.updateRetention')
  } finally {
    savingRetention.value = false
  }
}

function clearFilters(): void {
  action.value = ''
  outcome.value = ''
  fromDate.value = ''
  toDate.value = ''
  void load(0)
}

function retryLiveUpdates(): void {
  admin.stopAuditStream()
  admin.startAuditStream()
}

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString(locale.value) : '-'
}

function shortId(value: string | null | undefined): string {
  return value ? value.slice(0, 8) : ''
}

function principalLabel(
  id: string | null | undefined,
  displayName: string | null | undefined,
): string {
  if (!id) return t('admin.audit.labels.system')
  return `${shortId(id)}/${displayName?.trim() || id}`
}

function actorLabel(log: AdminAuditLog): string {
  return principalLabel(log.actorUserId, log.actorDisplayName)
}

function targetUserLabel(log: AdminAuditLog): string {
  return log.targetUserId ? principalLabel(log.targetUserId, log.targetUserDisplayName) : '-'
}

function targetIdentifierLabel(log: AdminAuditLog): string {
  if (
    log.targetUserId &&
    (!log.targetId || log.targetId === log.targetUserId || log.targetType === 'USER')
  ) {
    return targetUserLabel(log)
  }
  return log.targetId || log.targetUserId || ''
}
</script>

<template>
  <section class="audit-page">
    <header class="page-header">
      <div>
        <span class="eyebrow">{{ t('admin.audit.eyebrow') }}</span>
        <h1>{{ t('admin.audit.title') }}</h1>
        <p>{{ t('admin.audit.description') }}</p>
      </div>
      <div class="header-actions">
        <span class="live-status" :class="admin.auditLiveStatus" role="status">
          {{ liveStatusLabel }}
        </span>
        <button type="button" class="secondary" :disabled="loading" @click="load(pageNumber)">
          {{ loading ? t('admin.audit.actions.loading') : t('admin.audit.actions.refresh') }}
        </button>
      </div>
    </header>

    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="message" class="notice success" role="status">{{ message }}</p>
    <section v-if="admin.auditLiveStatus === 'polling'" class="notice info" role="status">
      <div>
        <strong>{{ t('admin.audit.polling.title') }}</strong>
        <p>{{ t('admin.audit.polling.description') }}</p>
      </div>
    </section>
    <section v-else-if="admin.auditLiveStatus === 'paused'" class="notice warning" role="status">
      <div>
        <strong>{{ t('admin.audit.liveWarning.title') }}</strong>
        <p>{{ t('admin.audit.liveWarning.description') }}</p>
      </div>
      <button type="button" class="secondary" @click="retryLiveUpdates">
        {{ t('admin.audit.actions.retryLiveUpdates') }}
      </button>
    </section>

    <section class="panel retention-panel">
      <div>
        <h2>{{ t('admin.audit.retention.title') }}</h2>
        <p>
          {{ t('admin.audit.retention.description') }}
          <span v-if="admin.auditRetention?.updatedAt">
            {{
              t('admin.audit.retention.lastUpdated', {
                date: formatDate(admin.auditRetention.updatedAt),
              })
            }}
          </span>
        </p>
      </div>
      <form @submit.prevent="saveRetention">
        <label for="audit-retention-days">{{ t('admin.audit.retention.days') }}</label>
        <input
          id="audit-retention-days"
          v-model.number="retentionDays"
          type="number"
          min="1"
          max="3650"
          required
        />
        <button type="submit" :disabled="savingRetention">
          {{
            savingRetention
              ? t('admin.audit.actions.saving')
              : t('admin.audit.actions.saveRetention')
          }}
        </button>
      </form>
    </section>

    <section class="panel">
      <form class="filters" @submit.prevent="load(0)">
        <label>
          <span>{{ t('admin.audit.filters.action') }}</span>
          <input
            v-model="action"
            :placeholder="t('admin.audit.filters.actionPlaceholder')"
            maxlength="120"
          />
        </label>
        <label>
          <span>{{ t('admin.audit.filters.outcome') }}</span>
          <ThemedSelect
            v-model="outcome"
            input-id="audit-outcome"
            name="auditOutcome"
            :options="outcomeOptions"
            :aria-label="t('admin.audit.filters.outcome')"
          />
        </label>
        <label
          ><span>{{ t('admin.audit.filters.from') }}</span
          ><input v-model="fromDate" type="date"
        /></label>
        <label
          ><span>{{ t('admin.audit.filters.to') }}</span
          ><input v-model="toDate" type="date"
        /></label>
        <button type="submit" :disabled="loading">{{ t('admin.audit.actions.apply') }}</button>
        <button type="button" class="secondary" :disabled="loading" @click="clearFilters">
          {{ t('admin.audit.actions.reset') }}
        </button>
      </form>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t('admin.audit.table.action') }}</th>
              <th>{{ t('admin.audit.table.outcome') }}</th>
              <th>{{ t('admin.audit.table.actor') }}</th>
              <th>{{ t('admin.audit.table.target') }}</th>
              <th>{{ t('admin.audit.table.ip') }}</th>
              <th>{{ t('admin.audit.table.created') }}</th>
              <th>{{ t('admin.audit.table.detail') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && !admin.auditLogs.length">
              <td colspan="7" class="empty">{{ t('admin.audit.table.empty') }}</td>
            </tr>
            <tr v-for="log in admin.auditLogs" :key="log.id">
              <td :data-label="t('admin.audit.table.action')">
                <strong>{{ log.action }}</strong>
              </td>
              <td :data-label="t('admin.audit.table.outcome')">
                <span class="outcome" :class="log.outcome.toLowerCase()">{{ log.outcome }}</span>
              </td>
              <td :data-label="t('admin.audit.table.actor')" class="principal-cell">
                {{ actorLabel(log) }}
              </td>
              <td :data-label="t('admin.audit.table.target')">
                {{ log.targetType || '-' }}<small>{{ targetIdentifierLabel(log) }}</small>
              </td>
              <td :data-label="t('admin.audit.table.ip')" class="mono">
                {{ log.ipAddress || '-' }}
              </td>
              <td :data-label="t('admin.audit.table.created')">
                <time>{{ formatDate(log.createdAt) }}</time>
              </td>
              <td :data-label="t('admin.audit.table.detail')">
                <button type="button" class="detail" @click="openDetail(log.id)">
                  {{ t('admin.audit.actions.inspect') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="pagination">
        <span>{{
          t('admin.audit.pagination.entries', {
            count: admin.auditPagination.totalElements,
          })
        }}</span>
        <div>
          <button
            type="button"
            class="secondary"
            :disabled="loading || pageNumber <= 0"
            @click="load(pageNumber - 1)"
          >
            {{ t('admin.audit.actions.previous') }}
          </button>
          <span>{{
            t('admin.audit.pagination.page', {
              current: pageNumber + 1,
              total: Math.max(admin.auditPagination.totalPages, 1),
            })
          }}</span>
          <button
            type="button"
            class="secondary"
            :disabled="loading || pageNumber + 1 >= admin.auditPagination.totalPages"
            @click="load(pageNumber + 1)"
          >
            {{ t('admin.audit.actions.next') }}
          </button>
        </div>
      </footer>
    </section>

    <section
      v-if="admin.auditLogDetail"
      ref="detailPanelRef"
      class="panel detail-panel"
      aria-live="polite"
      tabindex="-1"
    >
      <div class="detail-heading">
        <div>
          <span class="eyebrow">{{ t('admin.audit.detail.eyebrow') }}</span>
          <h2>{{ admin.auditLogDetail.action }}</h2>
        </div>
        <button type="button" class="secondary" @click="admin.auditLogDetail = null">
          {{ t('admin.audit.actions.closeDetail') }}
        </button>
      </div>
      <dl>
        <div>
          <dt>{{ t('admin.audit.detail.outcome') }}</dt>
          <dd>{{ admin.auditLogDetail.outcome }}</dd>
        </div>
        <div>
          <dt>{{ t('admin.audit.detail.actor') }}</dt>
          <dd>{{ actorLabel(admin.auditLogDetail) }}</dd>
        </div>
        <div>
          <dt>{{ t('admin.audit.detail.targetUser') }}</dt>
          <dd>{{ targetUserLabel(admin.auditLogDetail) }}</dd>
        </div>
        <div>
          <dt>{{ t('admin.audit.detail.target') }}</dt>
          <dd>
            {{ admin.auditLogDetail.targetType || '-' }} /
            {{ targetIdentifierLabel(admin.auditLogDetail) || '-' }}
          </dd>
        </div>
        <div>
          <dt>{{ t('admin.audit.detail.ipAddress') }}</dt>
          <dd>{{ admin.auditLogDetail.ipAddress || '-' }}</dd>
        </div>
        <div>
          <dt>{{ t('admin.audit.detail.created') }}</dt>
          <dd>{{ formatDate(admin.auditLogDetail.createdAt) }}</dd>
        </div>
      </dl>
      <div class="details-copy">
        <strong>{{ t('admin.audit.detail.redactedDetails') }}</strong>
        <pre>{{ admin.auditLogDetail.details || t('admin.audit.detail.noAdditionalDetails') }}</pre>
      </div>
    </section>
  </section>
</template>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}
/* Inset the header's right edge by the panel padding (+1px for the panel
   border) so Refresh lines up exactly with Save retention / Reset / the
   DETAIL column instead of sticking out. */
.page-header {
  padding-right: calc(var(--vg-space-4) + 1px);
}
.page-header,
.retention-panel,
.detail-heading,
.pagination,
.pagination > div,
.notice.warning,
.notice.info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-4);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--vg-space-2);
}
.live-status {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 800;
}
.live-status::before {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 999px;
  background: var(--vg-text-muted);
  content: '';
}
.live-status.connected {
  color: var(--vg-green-bright);
}
.live-status.connected::before {
  background: var(--vg-green-bright);
}
.live-status.reconnecting::before {
  background: #f59e0b;
}
.live-status.polling {
  color: var(--vg-blue-bright);
}
.live-status.polling::before {
  background: var(--vg-blue-bright);
}
.eyebrow {
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}
h1,
h2 {
  margin: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  letter-spacing: 0;
}
h1 {
  margin-top: var(--vg-space-1);
  font-size: clamp(1.625rem, 2.2vw, 1.875rem);
}
h2 {
  font-size: var(--vg-text-lg);
}
p {
  margin: var(--vg-space-1) 0 0;
  color: var(--vg-text-muted);
}
.panel,
.notice {
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
  padding: var(--vg-space-4);
}
.notice.error {
  border-color: rgba(239, 68, 68, 0.32);
  color: var(--vg-danger);
}
.notice.success {
  border-color: rgba(34, 197, 94, 0.3);
  color: var(--vg-green-bright);
}
.notice.warning {
  border-color: rgba(245, 158, 11, 0.38);
  background: rgba(245, 158, 11, 0.08);
}
.notice.info {
  border-color: rgba(59, 130, 246, 0.32);
  background: rgba(59, 130, 246, 0.08);
}
.notice.warning strong,
.notice.info strong {
  color: var(--vg-text);
}
.retention-panel form {
  display: grid;
  grid-template-columns: auto 7rem auto;
  align-items: center;
  gap: var(--vg-space-2);
}
.filters {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) repeat(3, minmax(0, 1fr)) auto auto;
  gap: var(--vg-space-3);
  align-items: end;
}
.filters label {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
input,
select,
button {
  min-height: 2.75rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  font: inherit;
}
input,
select {
  min-width: 0;
  padding: 0 var(--vg-space-3);
  background: var(--vg-bg);
  color: var(--vg-text);
}
input:focus,
select:focus,
button:focus-visible {
  outline: 2px solid var(--vg-blue-bright);
  outline-offset: 2px;
}
button {
  padding: 0 var(--vg-space-3);
  background: var(--vg-blue);
  border-color: var(--vg-blue);
  color: white;
  cursor: pointer;
  font-weight: 800;
}
button.secondary,
button.detail {
  background: var(--vg-surface-2);
  border-color: var(--vg-border);
  color: var(--vg-text);
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.table-wrap {
  margin-top: var(--vg-space-4);
  overflow-x: auto;
}
table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
}
th:nth-child(1) {
  width: 9%;
}
th:nth-child(2) {
  width: 10%;
}
th:nth-child(3) {
  width: 21%;
}
th:nth-child(4) {
  width: 21%;
}
th:nth-child(5) {
  width: 10%;
}
th:nth-child(6) {
  width: 17%;
}
th:nth-child(7) {
  width: 12%;
}
th,
td {
  padding: var(--vg-space-3);
  border-bottom: 1px solid var(--vg-border);
  color: var(--vg-text);
  text-align: left;
  vertical-align: top;
  overflow-wrap: anywhere;
}
/* Center the DETAIL column so the header and the Inspect button share the
   same center axis instead of hanging off the right edge at different widths. */
th:last-child,
td:last-child:not(.empty) {
  text-align: center;
}
button.detail {
  padding: 0 var(--vg-space-2);
  font-size: var(--vg-text-sm);
  white-space: nowrap;
}
th {
  background: var(--vg-surface-2);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
td small {
  display: block;
  margin-top: 0.2rem;
  color: var(--vg-text-dim);
}
.mono {
  font-family: var(--vg-font-mono, monospace);
  font-size: var(--vg-text-xs);
}
.principal-cell {
  font-family: var(--vg-font-mono, monospace);
  font-size: var(--vg-text-xs);
  overflow-wrap: anywhere;
}
.outcome {
  display: inline-flex;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.1);
  font-size: var(--vg-text-xs);
  font-weight: 800;
}
.outcome.success {
  color: var(--vg-green-bright);
  background: rgba(34, 197, 94, 0.1);
}
.outcome.failure {
  color: var(--vg-danger);
  background: rgba(239, 68, 68, 0.1);
}
.empty {
  color: var(--vg-text-muted);
  text-align: center;
}
.pagination {
  margin-top: var(--vg-space-4);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}
.detail-panel dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--vg-space-3);
}
.detail-panel dl div,
.details-copy {
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg);
}
dt {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  text-transform: uppercase;
}
dd {
  margin: var(--vg-space-1) 0 0;
  color: var(--vg-text);
  overflow-wrap: anywhere;
}
.details-copy {
  margin-top: var(--vg-space-3);
  color: var(--vg-text-muted);
}
pre {
  margin: var(--vg-space-2) 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--vg-text);
  font-family: var(--vg-font-mono, monospace);
}
@media (max-width: 1024px) {
  .filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 900px) {
  .table-wrap {
    overflow-x: visible;
  }
  table {
    min-width: 0;
  }
  thead {
    display: none;
  }
  tbody {
    display: grid;
    gap: var(--vg-space-3);
  }
  tr {
    display: grid;
    gap: var(--vg-space-2);
    padding: var(--vg-space-3);
    border: 1px solid var(--vg-border);
    border-radius: var(--vg-radius-sm);
    background: var(--vg-bg);
  }
  td {
    display: grid;
    grid-template-columns: minmax(6rem, 0.42fr) minmax(0, 1fr);
    gap: var(--vg-space-3);
    padding: 0;
    border-bottom: 0;
    align-items: start;
  }
  td::before {
    content: attr(data-label);
    color: var(--vg-text-muted);
    font-size: var(--vg-text-xs);
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }
  td.empty {
    display: block;
    padding: var(--vg-space-4);
    text-align: center;
  }
  td.empty::before {
    content: '';
    display: none;
  }
  /* Stacked cards keep left-aligned labels like the other columns. */
  td:last-child:not(.empty) {
    text-align: left;
  }
  td small {
    margin-top: var(--vg-space-1);
  }
  td button.detail {
    width: 100%;
  }
}
@media (max-width: 720px) {
  .page-header,
  .retention-panel,
  .detail-heading,
  .pagination,
  .notice.warning,
  .notice.info {
    align-items: stretch;
    flex-direction: column;
  }
  .header-actions {
    justify-content: space-between;
  }
  .retention-panel form,
  .filters,
  .detail-panel dl {
    grid-template-columns: 1fr;
    width: 100%;
  }
  .pagination > div {
    width: 100%;
  }
  .pagination > div button {
    flex: 1;
  }
}
@media (max-width: 420px) {
  td {
    grid-template-columns: 1fr;
    gap: var(--vg-space-1);
  }
}
@media (prefers-reduced-motion: reduce) {
  * {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
