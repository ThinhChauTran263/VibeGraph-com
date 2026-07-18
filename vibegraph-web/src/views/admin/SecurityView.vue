<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAdminStore } from '@/stores/admin'
import type {
  AdminIpBlock,
  AdminIpBlockRequest,
  AdminRequestAggregate,
  AdminSuspiciousNetwork,
} from '@/types/api'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'

const admin = useAdminStore()
const loading = ref(true)
const saving = ref(false)
const retryingPanel = ref('')
const mutationError = ref('')
const mutationSuccess = ref('')
const unavailablePanels = ref<string[]>([])
const editingId = ref<string | null>(null)
const pendingDelete = ref<AdminIpBlock | null>(null)
const form = ref({ ipAddress: '', safeReason: '', expiresAt: '', active: true })

const totalRequests = computed(() => admin.requestEvents.length)
const blockedRequests = computed(() => admin.requestEvents.filter((event) => event.status === 429 || event.status === 403).length)
const usersById = computed(() => new Map(admin.users.map((user) => [user.id, user])))
const expandedNetworks = ref<Set<string>>(new Set())
const topUserRows = computed(() => groupTopUsers(admin.topUsers))
const topIpRows = computed(() => admin.topIps)
const abuseState = computed(() => {
  if (admin.securityEvents.some((event) => event.severity.toUpperCase() === 'CRITICAL')) return 'Critical'
  if (blockedRequests.value > 0) return 'Defending'
  return 'Stable'
})
const liveStatusLabel = computed(() => {
  if (admin.securityLiveStatus === 'connected') return 'Live connected'
  if (admin.securityLiveStatus === 'reconnecting') return 'Reconnecting'
  return 'Live paused'
})

onMounted(async () => {
  await load()
  admin.startSecurityStream()
})
onBeforeUnmount(() => admin.stopSecurityStream())

async function load(): Promise<void> {
  loading.value = true
  mutationError.value = ''
  try {
    const [securityResult, usersResult] = await Promise.allSettled([
      admin.fetchSecurityData(100),
      admin.fetchUsers({ size: 100 }),
    ])
    unavailablePanels.value =
      securityResult.status === 'fulfilled'
        ? securityResult.value ?? []
        : ['security events', 'request events', 'top users', 'suspicious networks', 'IP blocks']
    if (usersResult.status === 'rejected' && !unavailablePanels.value.includes('user identities')) {
      unavailablePanels.value = [...unavailablePanels.value, 'user identities']
    }
  } catch (cause) {
    mutationError.value = cause instanceof Error ? cause.message : 'Failed to load security operations.'
  } finally {
    loading.value = false
  }
}

const panelRetryActions: Record<string, () => Promise<void>> = {
  'security events': () => admin.fetchSecurityEvents(),
  'request events': () => admin.fetchRequestEvents(),
  'top users': () => admin.fetchTopUsers(),
  'suspicious networks': () => admin.fetchTopIps(),
  'IP blocks': () => admin.fetchIpBlocks(),
  'user identities': () => admin.fetchUsers({ size: 100 }),
}

async function retryPanel(panel: string): Promise<void> {
  const retry = panelRetryActions[panel]
  if (!retry) return
  retryingPanel.value = panel
  try {
    await retry()
    unavailablePanels.value = unavailablePanels.value.filter((item) => item !== panel)
  } catch {
    // Keep the panel in the warning list so the operator can retry again.
  } finally {
    retryingPanel.value = ''
  }
}

function toPayload(): AdminIpBlockRequest {
  return {
    ipAddress: form.value.ipAddress.trim(),
    safeReason: form.value.safeReason.trim(),
    expiresAt: form.value.expiresAt ? new Date(form.value.expiresAt).toISOString() : null,
    active: form.value.active,
  }
}

async function saveBlock(): Promise<void> {
  saving.value = true
  mutationError.value = ''
  mutationSuccess.value = ''
  const wasEditing = Boolean(editingId.value)
  try {
    const result = editingId.value
      ? await admin.updateIpBlock(editingId.value, toPayload())
      : await admin.createIpBlock(toPayload())
    mutationSuccess.value = `IP block policy ${wasEditing ? 'updated' : 'created'} successfully.${
      result.refreshFailed ? ' The policy list could not be refreshed; retry that panel.' : ''
    }`
    if (result.refreshFailed && !unavailablePanels.value.includes('IP blocks')) {
      unavailablePanels.value = [...unavailablePanels.value, 'IP blocks']
    }
    resetForm()
  } catch (cause) {
    mutationError.value = cause instanceof Error ? cause.message : 'Failed to save IP block.'
  } finally {
    saving.value = false
  }
}

function editBlock(block: AdminIpBlock): void {
  editingId.value = block.id
  form.value = {
    ipAddress: block.ipAddress,
    safeReason: block.safeReason,
    expiresAt: block.expiresAt ? toLocalDateTime(block.expiresAt) : '',
    active: block.active,
  }
}

function resetForm(): void {
  editingId.value = null
  form.value = { ipAddress: '', safeReason: '', expiresAt: '', active: true }
}

async function deleteBlock(): Promise<void> {
  if (!pendingDelete.value) return
  saving.value = true
  mutationError.value = ''
  mutationSuccess.value = ''
  try {
    const deletedId = pendingDelete.value.id
    const result = await admin.deleteIpBlock(deletedId)
    mutationSuccess.value = `IP block policy removed successfully.${
      result.refreshFailed ? ' The policy list could not be refreshed; retry that panel.' : ''
    }`
    if (result.refreshFailed && !unavailablePanels.value.includes('IP blocks')) {
      unavailablePanels.value = [...unavailablePanels.value, 'IP blocks']
    }
    if (editingId.value === deletedId) resetForm()
    pendingDelete.value = null
  } catch (cause) {
    mutationError.value = cause instanceof Error ? cause.message : 'Failed to remove IP block.'
  } finally {
    saving.value = false
  }
}

function toLocalDateTime(value: string): string {
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString() : 'Never'
}

function maskRef(value: string | null): string {
  if (!value) return 'No API key'
  if (/^[A-Za-z0-9_-]{1,8}\*{4}$/.test(value)) return value
  const storedPrefix = value.includes(':') ? value.slice(value.lastIndexOf(':') + 1) : value
  return `${storedPrefix.slice(0, 8)}****`
}

function shortId(value: string | null): string | null {
  if (!value) return null
  if (value.length <= 12) return value
  return `${value.slice(0, 8)}...`
}

function principalLabel(
  displayName: string | null,
  email: string | null,
  apiKeyRef: string | null,
  userId: string | null = null,
): string {
  const knownUser = userId ? usersById.value.get(userId) : null
  const identity =
    displayName?.trim() ||
    knownUser?.displayName?.trim() ||
    email?.trim() ||
    knownUser?.email?.trim() ||
    shortId(userId) ||
    'System'
  return `${identity} / ${maskRef(apiKeyRef)}`
}

type TopUserRow = AdminRequestAggregate & {
  identity: string
  label: string
}

function groupTopUsers(rows: AdminRequestAggregate[]): TopUserRow[] {
  const grouped = new Map<string, TopUserRow>()
  for (const row of rows) {
    const identity = `${row.userId ?? 'anonymous'}:${row.apiKeyRef ?? 'no-key'}`
    const current = grouped.get(identity)
    if (current) {
      current.requestsPerMinute += row.requestsPerMinute
      if (row.minuteBucket > current.minuteBucket) current.minuteBucket = row.minuteBucket
      continue
    }
    grouped.set(identity, {
      ...row,
      identity,
      label: principalLabel(row.userDisplayName, row.userEmail, row.apiKeyRef, row.userId),
    })
  }
  return [...grouped.values()].sort(
    (left, right) => right.requestsPerMinute - left.requestsPerMinute,
  )
}

function networkRegionId(network: AdminSuspiciousNetwork): string {
  return `network-${network.ipAddress.replace(/[^a-zA-Z0-9_-]/g, '-')}`
}

function isNetworkExpanded(ipAddress: string): boolean {
  return expandedNetworks.value.has(ipAddress)
}

function toggleNetwork(ipAddress: string): void {
  const next = new Set(expandedNetworks.value)
  if (next.has(ipAddress)) next.delete(ipAddress)
  else next.add(ipAddress)
  expandedNetworks.value = next
}

function countLabel(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`
}
</script>

<template>
  <main class="security-page">
    <header class="page-header">
      <div><span class="eyebrow">Operations</span><h1>Security</h1><p>Monitor request pressure, abuse signals, and exact IP policy.</p></div>
      <div class="header-actions">
        <span class="live-status" :class="admin.securityLiveStatus" role="status">{{ liveStatusLabel }}</span>
        <button type="button" class="secondary" :disabled="loading" @click="load">{{ loading ? 'Loading...' : 'Refresh data' }}</button>
      </div>
    </header>
    <p v-if="mutationError" class="notice error" role="alert">{{ mutationError }}</p>
    <p v-if="mutationSuccess" class="notice success" role="status">{{ mutationSuccess }}</p>
    <section v-if="unavailablePanels.length" class="notice warning" role="status">
      <div>
        <strong>Some monitoring panels are unavailable</strong>
        <p>Policy writes remain independent. Retry only the stale panel.</p>
      </div>
      <div class="retry-actions">
        <button
          v-for="panel in unavailablePanels"
          :key="panel"
          type="button"
          class="secondary"
          :disabled="Boolean(retryingPanel)"
          @click="retryPanel(panel)"
        >
          {{ retryingPanel === panel ? 'Retrying...' : `Retry ${panel}` }}
        </button>
      </div>
    </section>

    <section class="metrics" aria-label="Security status">
      <article><span>Request sample</span><strong>{{ totalRequests }}</strong><small>latest events</small></article>
      <article><span>Blocked / limited</span><strong>{{ blockedRequests }}</strong><small>HTTP 403 or 429</small></article>
      <article><span>Active IP blocks</span><strong>{{ admin.ipBlocks.filter((block) => block.active).length }}</strong><small>enforced policies</small></article>
      <article><span>Abuse state</span><strong>{{ abuseState }}</strong><small>from live signals</small></article>
    </section>

    <section class="rank-grid">
      <article class="panel">
        <div class="panel-heading"><div><h2>Top users</h2><p>Aggregated requests in the last 60 minutes.</p></div><span>Requests</span></div>
        <ol class="rank-list"><li v-for="row in topUserRows" :key="row.identity"><span><strong>{{ row.label }}</strong></span><strong>{{ row.requestsPerMinute }}</strong></li><li v-if="!topUserRows.length" class="empty">No user aggregates.</li></ol>
      </article>
      <article class="panel network-panel">
        <div class="panel-heading"><div><h2>Suspicious Networks</h2><p>Aggregated sources with identity and API key concentration.</p></div><span>Risk</span></div>
        <ol class="network-list">
          <li v-for="row in topIpRows" :key="row.ipAddress" class="network-row">
            <button
              type="button"
              class="network-toggle"
              :aria-expanded="isNetworkExpanded(row.ipAddress)"
              :aria-controls="networkRegionId(row)"
              @click="toggleNetwork(row.ipAddress)"
            >
              <span class="network-summary">
                <code>{{ row.ipAddress }}</code>
                <small>
                  {{ countLabel(row.totalRequests, 'request') }} /
                  {{ countLabel(row.uniqueUsers, 'user') }} /
                  {{ countLabel(row.uniqueApiKeys, 'API key') }}
                </small>
                <span v-if="row.uniqueUsers > 1 || row.uniqueApiKeys > 1" class="risk-hint">Shared network</span>
              </span>
              <span aria-hidden="true">{{ isNetworkExpanded(row.ipAddress) ? '-' : '+' }}</span>
            </button>
            <ol
              v-if="isNetworkExpanded(row.ipAddress)"
              :id="networkRegionId(row)"
              class="network-breakdown"
            >
              <li v-for="breakdown in row.breakdown" :key="`${breakdown.userId ?? 'anonymous'}:${breakdown.apiKeyRef ?? 'no-key'}`">
                <strong>{{ principalLabel(breakdown.userDisplayName, breakdown.userEmail, breakdown.apiKeyRef, breakdown.userId) }}</strong>
                <strong>{{ breakdown.requests }}</strong>
              </li>
            </ol>
          </li>
          <li v-if="!topIpRows.length" class="empty">No suspicious network aggregates.</li>
        </ol>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading"><div><h2>Request events</h2><p>Recent HTTP outcomes from the abuse telemetry pipeline.</p></div><span>{{ admin.requestEvents.length }} events</span></div>
      <div class="table-wrap request-events"><table><thead><tr><th>Event</th><th>Method / route</th><th>Status</th><th>User / key</th><th>IP</th><th>Occurred</th></tr></thead><tbody><tr v-if="!loading && !admin.requestEvents.length"><td colspan="6" class="empty">No request events recorded.</td></tr><tr v-for="event in admin.requestEvents" :key="event.id"><td data-label="Event"><strong>{{ event.eventType }}</strong></td><td data-label="Route"><code>{{ event.method }} {{ event.route }}</code></td><td data-label="Status"><span class="status" :class="{ danger: event.status >= 400 }">{{ event.status }}</span></td><td data-label="User / key"><code>{{ principalLabel(event.userDisplayName, event.userEmail, event.apiKeyRef, event.userId) }}</code></td><td data-label="IP"><code>{{ event.ipAddress || '-' }}</code></td><td data-label="Occurred"><time>{{ formatDate(event.occurredAt) }}</time></td></tr></tbody></table></div>
    </section>

    <section class="policy-grid">
      <article class="panel editor-panel">
        <div class="panel-heading"><div><h2>{{ editingId ? 'Edit IP block' : 'Create IP block' }}</h2><p>Use a safe reason suitable for client-facing denial responses.</p></div><button type="button" class="secondary" :disabled="saving" @click="resetForm">Reset</button></div>
        <form @submit.prevent="saveBlock">
          <label><span>IP address or CIDR</span><input id="ip-block-address" v-model="form.ipAddress" name="ipAddress" required maxlength="120" placeholder="203.0.113.42" /></label>
          <label class="wide"><span>Safe reason</span><textarea id="ip-block-reason" v-model="form.safeReason" name="safeReason" required maxlength="240" rows="4" placeholder="Access temporarily restricted due to unusual request volume."></textarea></label>
          <label><span>Expires at</span><input id="ip-block-expires-at" v-model="form.expiresAt" name="expiresAt" type="datetime-local" /></label>
          <label class="switch-row"><input id="ip-block-active" v-model="form.active" name="active" type="checkbox" /><span>{{ form.active ? 'Policy active' : 'Policy paused' }}</span></label>
          <button type="submit" :disabled="saving">{{ saving ? 'Saving...' : editingId ? 'Update block' : 'Create block' }}</button>
        </form>
      </article>
      <article class="panel blocks-panel">
        <div class="panel-heading"><div><h2>IP block policies</h2><p>Exact policies enforced by the backend filter.</p></div><span>{{ admin.ipBlocks.length }} policies</span></div>
        <div class="block-list"><article v-for="block in admin.ipBlocks" :key="block.id" :class="{ paused: !block.active }"><div><code>{{ block.ipAddress }}</code><span>{{ block.active ? 'Active' : 'Paused' }}</span></div><p>{{ block.safeReason }}</p><small>Expires {{ formatDate(block.expiresAt) }}</small><footer><button type="button" class="secondary" @click="editBlock(block)">Edit</button><button type="button" class="danger-button" @click="pendingDelete = block">Remove</button></footer></article><p v-if="!admin.ipBlocks.length" class="empty">No IP block policies configured.</p></div>
      </article>
    </section>

    <AdminConfirmDialog :open="Boolean(pendingDelete)" title="Remove IP block" :message="`Remove policy for ${pendingDelete?.ipAddress ?? 'this address'}? Requests will no longer be blocked by this policy.`" confirm-label="Remove policy" tone="danger" :busy="saving" @cancel="pendingDelete = null" @confirm="deleteBlock" />
  </main>
</template>

<style scoped>
.security-page { display: flex; flex-direction: column; gap: var(--vg-space-4); }
.page-header, .panel-heading, .metrics, .rank-grid, .policy-grid { display: grid; gap: var(--vg-space-4); }
.page-header, .panel-heading { grid-template-columns: minmax(0,1fr) auto; align-items: start; }
.header-actions { display: flex; align-items: center; gap: var(--vg-space-2); }
.live-status { display: inline-flex; align-items: center; gap: .4rem; color: var(--vg-text-muted); font-size: var(--vg-text-xs); font-weight: 800; }
.live-status::before { width: .55rem; height: .55rem; border-radius: 999px; background: var(--vg-text-muted); content: ''; }
.live-status.connected { color: var(--vg-green-bright); }
.live-status.connected::before { background: var(--vg-green-bright); }
.live-status.reconnecting::before { background: #f59e0b; }
.eyebrow { color: var(--vg-blue-bright); font-size: var(--vg-text-xs); font-weight: 800; letter-spacing: .09em; text-transform: uppercase; }
h1, h2 { margin: 0; color: var(--vg-text); font-family: var(--vg-font-display); letter-spacing: 0; }
h1 { margin-top: var(--vg-space-1); font-size: clamp(1.625rem, 2.2vw, 1.875rem); }
h2 { font-size: var(--vg-text-lg); }
p { margin: var(--vg-space-1) 0 0; color: var(--vg-text-muted); }
.notice, .panel { padding: var(--vg-space-4); border: 1px solid var(--vg-border); border-radius: var(--vg-radius); background: var(--vg-surface); }
.notice.error { color: var(--vg-danger); border-color: rgba(239,68,68,.3); }
.notice.success { color: var(--vg-green-bright); border-color: rgba(34,197,94,.3); }
.notice.warning { display: flex; align-items: center; justify-content: space-between; gap: var(--vg-space-4); border-color: rgba(245,158,11,.38); background: rgba(245,158,11,.08); }
.notice.warning strong { color: var(--vg-text); }
.notice.warning p { margin-top: var(--vg-space-1); }
.retry-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--vg-space-2); }
button, input, textarea { min-height: 2.75rem; border: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm); font: inherit; }
button { padding: 0 var(--vg-space-3); background: var(--vg-blue); border-color: var(--vg-blue); color: white; cursor: pointer; font-weight: 800; }
button.secondary { background: var(--vg-surface-2); border-color: var(--vg-border); color: var(--vg-text); }
button.danger-button { background: rgba(239,68,68,.12); border-color: rgba(239,68,68,.35); color: var(--vg-danger); }
button:disabled { opacity: .5; cursor: not-allowed; }
input, textarea { width: 100%; padding: var(--vg-space-2) var(--vg-space-3); background: var(--vg-bg); color: var(--vg-text); }
input:focus-visible, textarea:focus-visible, button:focus-visible { outline: 2px solid var(--vg-blue-bright); outline-offset: 2px; }
.metrics { grid-template-columns: repeat(4,minmax(0,1fr)); }
.metrics article { padding: var(--vg-space-4); border-left: 3px solid var(--vg-blue); background: var(--vg-surface); border-top: 1px solid var(--vg-border); border-right: 1px solid var(--vg-border); border-bottom: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm); }
.metrics span, .metrics small, .panel-heading > span { display: block; color: var(--vg-text-muted); font-size: var(--vg-text-xs); font-weight: 800; text-transform: uppercase; letter-spacing: .04em; }
.metrics strong { display: block; margin: .4rem 0 .15rem; color: var(--vg-text); font-family: var(--vg-font-display); font-size: 1.6rem; }
.rank-grid, .policy-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
.panel-heading > span { color: var(--vg-green-bright); }
ol { display: grid; gap: var(--vg-space-2); margin: var(--vg-space-4) 0 0; padding-left: 1.4rem; }
li { display: flex; justify-content: space-between; gap: var(--vg-space-3); padding: var(--vg-space-2); border-bottom: 1px solid var(--vg-border); color: var(--vg-text); }
code { overflow-wrap: anywhere; color: var(--vg-text); font-family: var(--vg-font-mono,monospace); font-size: var(--vg-text-xs); }
ol li > span { min-width: 0; }
ol li small { display: block; margin-top: .25rem; color: var(--vg-text-muted); font-size: var(--vg-text-xs); line-height: 1.35; overflow-wrap: anywhere; }
.rank-list,
.network-list {
  --visible-rank-rows: 5;
  --rank-row-height: 4rem;
  max-height: calc(var(--visible-rank-rows) * var(--rank-row-height) + (var(--visible-rank-rows) - 1) * var(--vg-space-2));
  overflow-y: auto;
  padding-right: .25rem;
  scrollbar-gutter: stable;
}
.network-list {
  --rank-row-height: 5.45rem;
  padding-left: 0;
  list-style: none;
}
.network-row { display: block; padding: 0; }
.network-toggle { display: flex; width: 100%; min-height: 3.5rem; align-items: center; justify-content: space-between; gap: var(--vg-space-3); padding: var(--vg-space-2); border: 0; background: transparent; color: var(--vg-text); text-align: left; }
.network-toggle:hover { background: var(--vg-surface-2); }
.network-summary { min-width: 0; }
.risk-hint { display: inline-flex; margin-top: var(--vg-space-2); padding: .2rem .5rem; border: 1px solid rgba(245,158,11,.38); border-radius: 999px; background: rgba(245,158,11,.08); color: #f59e0b; font-size: var(--vg-text-xs); font-weight: 800; }
.network-breakdown { margin: 0; padding: 0 var(--vg-space-2) var(--vg-space-2) var(--vg-space-4); list-style: none; }
.network-breakdown li { padding-left: 0; padding-right: 0; }
.table-wrap { margin-top: var(--vg-space-4); overflow-x: auto; }
.request-events { max-height: 32rem; overflow-y: auto; scrollbar-gutter: stable; }
table { width: 100%; min-width: 70rem; border-collapse: collapse; }
th, td { padding: var(--vg-space-3); border-bottom: 1px solid var(--vg-border); text-align: left; vertical-align: top; color: var(--vg-text); }
th { background: var(--vg-surface-2); color: var(--vg-text-muted); font-size: var(--vg-text-xs); text-transform: uppercase; letter-spacing: .04em; }
.status { display: inline-flex; min-width: 2.4rem; justify-content: center; padding: .2rem .45rem; border-radius: 999px; background: rgba(34,197,94,.1); color: var(--vg-green-bright); font-size: var(--vg-text-xs); font-weight: 800; }
.status.danger { background: rgba(239,68,68,.1); color: var(--vg-danger); }
.editor-panel form { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: var(--vg-space-3); margin-top: var(--vg-space-4); }
.editor-panel label { display: flex; flex-direction: column; gap: var(--vg-space-2); color: var(--vg-text-muted); font-size: var(--vg-text-xs); font-weight: 800; text-transform: uppercase; }
.editor-panel .wide { grid-column: 1 / -1; }
.switch-row { flex-direction: row !important; align-items: center; justify-content: flex-start; padding-top: 1.8rem; text-transform: none !important; font-size: var(--vg-text-sm) !important; }
.switch-row input { width: 1.15rem; min-height: 1.15rem; accent-color: var(--vg-blue); }
.block-list { display: grid; gap: var(--vg-space-3); margin-top: var(--vg-space-4); }
.block-list article { padding: var(--vg-space-3); border: 1px solid rgba(239,68,68,.27); border-radius: var(--vg-radius-sm); background: rgba(239,68,68,.06); }
.block-list article.paused { border-color: var(--vg-border); background: var(--vg-bg); opacity: .75; }
.block-list article > div, .block-list footer { display: flex; align-items: center; justify-content: space-between; gap: var(--vg-space-2); }
.block-list article > div span { color: var(--vg-danger); font-size: var(--vg-text-xs); font-weight: 800; text-transform: uppercase; }
.block-list article.paused > div span { color: var(--vg-text-muted); }
.block-list p { color: var(--vg-text); }
.block-list small { color: var(--vg-text-dim); }
.block-list footer { justify-content: flex-end; margin-top: var(--vg-space-3); }
.empty { color: var(--vg-text-muted); text-align: center; }
@media (max-width: 1000px) { .metrics { grid-template-columns: repeat(2,minmax(0,1fr)); } .rank-grid, .policy-grid { grid-template-columns: 1fr; } }
@media (max-width: 620px) { .page-header, .panel-heading { grid-template-columns: 1fr; } .notice.warning { align-items: stretch; flex-direction: column; } .retry-actions { justify-content: stretch; } .retry-actions button { width: 100%; } .metrics { grid-template-columns: 1fr; } .editor-panel form { grid-template-columns: 1fr; } .editor-panel .wide { grid-column: auto; } .switch-row { padding-top: 0; } }
</style>
