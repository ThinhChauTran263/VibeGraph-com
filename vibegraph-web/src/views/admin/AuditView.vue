<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAdminStore } from '@/stores/admin'

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

const pageNumber = computed(() => admin.auditPagination.pageNumber ?? admin.auditPagination.page ?? 0)
const pageSize = computed(() => admin.auditPagination.pageSize ?? admin.auditPagination.size ?? 50)

onMounted(load)

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
    error.value = cause instanceof Error ? cause.message : 'Failed to load audit data.'
  } finally {
    loading.value = false
  }
}

async function openDetail(id: string): Promise<void> {
  try {
    await admin.fetchAuditLogDetail(id)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Failed to load audit detail.'
  }
}
async function saveRetention(): Promise<void> {
  savingRetention.value = true
  error.value = ''
  message.value = ''
  try {
    await admin.updateAuditRetention(retentionDays.value)
    message.value = `Retention updated to ${retentionDays.value} days.`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Failed to update retention.'
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

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString() : '-'
}
</script>

<template>
  <main class="audit-page">
    <header class="page-header">
      <div>
        <span class="eyebrow">Traceability</span>
        <h1>Audit logs</h1>
        <p>Inspect administrative actions, outcomes, targets, and retention policy.</p>
      </div>
      <button type="button" class="secondary" :disabled="loading" @click="load(pageNumber)">
        {{ loading ? 'Loading...' : 'Refresh' }}
      </button>
    </header>

    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="message" class="notice success" role="status">{{ message }}</p>

    <section class="panel retention-panel">
      <div>
        <h2>Retention policy</h2>
        <p>
          Keep audit history for 1 to 3,650 days.
          <span v-if="admin.auditRetention?.updatedAt">
            Last updated {{ formatDate(admin.auditRetention.updatedAt) }}.
          </span>
        </p>
      </div>
      <form @submit.prevent="saveRetention">
        <label for="audit-retention-days">Days</label>
        <input
          id="audit-retention-days"
          v-model.number="retentionDays"
          type="number"
          min="1"
          max="3650"
          required
        />
        <button type="submit" :disabled="savingRetention">
          {{ savingRetention ? 'Saving...' : 'Save retention' }}
        </button>
      </form>
    </section>

    <section class="panel">
      <form class="filters" @submit.prevent="load(0)">
        <label>
          <span>Action</span>
          <input v-model="action" placeholder="USER_BLOCKED" maxlength="120" />
        </label>
        <label>
          <span>Outcome</span>
          <select v-model="outcome">
            <option value="">All outcomes</option>
            <option value="SUCCESS">Success</option>
            <option value="FAILURE">Failure</option>
          </select>
        </label>
        <label><span>From</span><input v-model="fromDate" type="date" /></label>
        <label><span>To</span><input v-model="toDate" type="date" /></label>
        <button type="submit" :disabled="loading">Apply</button>
        <button type="button" class="secondary" :disabled="loading" @click="clearFilters">
          Reset
        </button>
      </form>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Action</th>
              <th>Outcome</th>
              <th>Actor</th>
              <th>Target</th>
              <th>IP</th>
              <th>Created</th>
              <th>Detail</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && !admin.auditLogs.length">
              <td colspan="7" class="empty">No audit entries match these filters.</td>
            </tr>
            <tr v-for="log in admin.auditLogs" :key="log.id">
              <td data-label="Action"><strong>{{ log.action }}</strong></td>
              <td data-label="Outcome"><span class="outcome" :class="log.outcome.toLowerCase()">{{ log.outcome }}</span></td>
              <td data-label="Actor" class="mono">{{ log.actorUserId || 'System' }}</td>
              <td data-label="Target">{{ log.targetType || '-' }}<small>{{ log.targetId || log.targetUserId || '' }}</small></td>
              <td data-label="IP" class="mono">{{ log.ipAddress || '-' }}</td>
              <td data-label="Created"><time>{{ formatDate(log.createdAt) }}</time></td>
              <td data-label="Detail"><button type="button" class="detail" @click="openDetail(log.id)">Inspect</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="pagination">
        <span>{{ admin.auditPagination.totalElements }} entries</span>
        <div>
          <button type="button" class="secondary" :disabled="loading || pageNumber <= 0" @click="load(pageNumber - 1)">Previous</button>
          <span>Page {{ pageNumber + 1 }} / {{ Math.max(admin.auditPagination.totalPages, 1) }}</span>
          <button type="button" class="secondary" :disabled="loading || pageNumber + 1 >= admin.auditPagination.totalPages" @click="load(pageNumber + 1)">Next</button>
        </div>
      </footer>
    </section>

    <section v-if="admin.auditLogDetail" class="panel detail-panel" aria-live="polite">
      <div class="detail-heading">
        <div><span class="eyebrow">Selected event</span><h2>{{ admin.auditLogDetail.action }}</h2></div>
        <button type="button" class="secondary" @click="admin.auditLogDetail = null">Close detail</button>
      </div>
      <dl>
        <div><dt>Outcome</dt><dd>{{ admin.auditLogDetail.outcome }}</dd></div>
        <div><dt>Actor</dt><dd>{{ admin.auditLogDetail.actorUserId || 'System' }}</dd></div>
        <div><dt>Target user</dt><dd>{{ admin.auditLogDetail.targetUserId || '-' }}</dd></div>
        <div><dt>Target</dt><dd>{{ admin.auditLogDetail.targetType || '-' }} / {{ admin.auditLogDetail.targetId || '-' }}</dd></div>
        <div><dt>IP address</dt><dd>{{ admin.auditLogDetail.ipAddress || '-' }}</dd></div>
        <div><dt>Created</dt><dd>{{ formatDate(admin.auditLogDetail.createdAt) }}</dd></div>
      </dl>
      <div class="details-copy"><strong>Redacted details</strong><pre>{{ admin.auditLogDetail.details || 'No additional details.' }}</pre></div>
    </section>
  </main>
</template>

<style scoped>
.audit-page { display: flex; flex-direction: column; gap: var(--vg-space-4); }
.page-header, .retention-panel, .detail-heading, .pagination, .pagination > div { display: flex; align-items: center; justify-content: space-between; gap: var(--vg-space-4); }
.eyebrow { color: var(--vg-blue-bright); font-size: var(--vg-text-xs); font-weight: 800; letter-spacing: .09em; text-transform: uppercase; }
h1, h2 { margin: 0; color: var(--vg-text); font-family: var(--vg-font-display); letter-spacing: 0; }
h1 { margin-top: var(--vg-space-1); font-size: clamp(1.625rem, 2.2vw, 1.875rem); }
h2 { font-size: var(--vg-text-lg); }
p { margin: var(--vg-space-1) 0 0; color: var(--vg-text-muted); }
.panel, .notice { border: 1px solid var(--vg-border); border-radius: var(--vg-radius); background: var(--vg-surface); padding: var(--vg-space-4); }
.notice.error { border-color: rgba(239,68,68,.32); color: var(--vg-danger); }
.notice.success { border-color: rgba(34,197,94,.3); color: var(--vg-green-bright); }
.retention-panel form { display: grid; grid-template-columns: auto 7rem auto; align-items: center; gap: var(--vg-space-2); }
.filters { display: grid; grid-template-columns: minmax(12rem,1.5fr) repeat(3,minmax(9rem,1fr)) auto auto; gap: var(--vg-space-3); align-items: end; }
.filters label { display: flex; flex-direction: column; gap: var(--vg-space-2); color: var(--vg-text-muted); font-size: var(--vg-text-xs); font-weight: 800; text-transform: uppercase; letter-spacing: .04em; }
input, select, button { min-height: 2.75rem; border: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm); font: inherit; }
input, select { min-width: 0; padding: 0 var(--vg-space-3); background: var(--vg-bg); color: var(--vg-text); }
input:focus, select:focus, button:focus-visible { outline: 2px solid var(--vg-blue-bright); outline-offset: 2px; }
button { padding: 0 var(--vg-space-3); background: var(--vg-blue); border-color: var(--vg-blue); color: white; cursor: pointer; font-weight: 800; }
button.secondary, button.detail { background: var(--vg-surface-2); border-color: var(--vg-border); color: var(--vg-text); }
button:disabled { opacity: .5; cursor: not-allowed; }
.table-wrap { margin-top: var(--vg-space-4); overflow-x: auto; }
table { width: 100%; min-width: 74rem; border-collapse: collapse; }
th, td { padding: var(--vg-space-3); border-bottom: 1px solid var(--vg-border); color: var(--vg-text); text-align: left; vertical-align: top; }
th { background: var(--vg-surface-2); color: var(--vg-text-muted); font-size: var(--vg-text-xs); text-transform: uppercase; letter-spacing: .04em; }
td small { display: block; margin-top: .2rem; color: var(--vg-text-dim); }
.mono { font-family: var(--vg-font-mono, monospace); font-size: var(--vg-text-xs); }
.outcome { display: inline-flex; padding: .2rem .5rem; border-radius: 999px; background: rgba(148,163,184,.1); font-size: var(--vg-text-xs); font-weight: 800; }
.outcome.success { color: var(--vg-green-bright); background: rgba(34,197,94,.1); }
.outcome.failure { color: var(--vg-danger); background: rgba(239,68,68,.1); }
.empty { color: var(--vg-text-muted); text-align: center; }
.pagination { margin-top: var(--vg-space-4); color: var(--vg-text-muted); font-size: var(--vg-text-sm); }
.detail-panel dl { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: var(--vg-space-3); }
.detail-panel dl div, .details-copy { padding: var(--vg-space-3); border: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm); background: var(--vg-bg); }
dt { color: var(--vg-text-muted); font-size: var(--vg-text-xs); font-weight: 800; text-transform: uppercase; }
dd { margin: var(--vg-space-1) 0 0; color: var(--vg-text); overflow-wrap: anywhere; }
.details-copy { margin-top: var(--vg-space-3); color: var(--vg-text-muted); }
pre { margin: var(--vg-space-2) 0 0; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--vg-text); font-family: var(--vg-font-mono, monospace); }
@media (max-width: 1024px) { .filters { grid-template-columns: repeat(2,minmax(0,1fr)); } }
@media (max-width: 720px) { .page-header, .retention-panel, .detail-heading, .pagination { align-items: stretch; flex-direction: column; } .retention-panel form, .filters, .detail-panel dl { grid-template-columns: 1fr; width: 100%; } .pagination > div { width: 100%; } .pagination > div button { flex: 1; } }
@media (prefers-reduced-motion: reduce) { * { scroll-behavior: auto !important; transition-duration: .01ms !important; } }
</style>