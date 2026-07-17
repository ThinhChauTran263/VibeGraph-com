<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import type { AdminReport, ReportMessage, ReportRealtimeEvent } from '@/types/api'
import StatusChip from '@/components/ui/StatusChip.vue'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import { useReportRealtime } from '@/composables/useReportRealtime'

const adminStore = useAdminStore()
const selectedReport = ref<AdminReport | null>(null)
const selectedMessages = ref<ReportMessage[]>([])
const replyMessage = ref('')
const isSending = ref(false)
const isClosing = ref(false)
const errorMsg = ref('')
const closeDialogOpen = ref(false)
const selectedReportId = computed(() => selectedReport.value?.id ?? null)

onMounted(async () => {
  try {
    await adminStore.fetchReports()
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to load reports'
  }
})

const reportRealtime = useReportRealtime(selectedReportId, {
  onEvent: (event) => {
    handleRealtimeEvent(event)
  },
})
const reportRealtimeStatus = reportRealtime.status
const reportRealtimeLabel = computed(() => {
  if (reportRealtimeStatus.value === 'connected') return 'Live'
  if (reportRealtimeStatus.value === 'error') return 'Realtime unavailable'
  if (reportRealtimeStatus.value === 'connecting') return 'Syncing'
  return 'Offline'
})

const selectReport = async (report: AdminReport) => {
  errorMsg.value = ''
  try {
    const detail = await adminStore.fetchReportDetail(report.id)
    selectedReport.value = detail.report
    selectedMessages.value = detail.messages
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to load report'
    selectedReport.value = report
    selectedMessages.value = []
  }
}

const backToList = async () => {
  selectedReport.value = null
  selectedMessages.value = []
  replyMessage.value = ''
  await adminStore.fetchReports()
}

const sendReply = async () => {
  if (!replyMessage.value.trim() || !selectedReport.value) return
  isSending.value = true
  errorMsg.value = ''
  try {
    await adminStore.replyToReport(selectedReport.value.id, replyMessage.value)
    // Reload thread to reflect the new message
    const detail = await adminStore.fetchReportDetail(selectedReport.value.id)
    selectedMessages.value = detail.messages
    replyMessage.value = ''
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to send reply'
  } finally {
    isSending.value = false
  }
}

const closeReport = async () => {
  if (!selectedReport.value) return
  closeDialogOpen.value = true
}

const confirmCloseReport = async () => {
  if (!selectedReport.value) return
  isClosing.value = true
  errorMsg.value = ''
  try {
    await adminStore.closeReport(selectedReport.value.id)
    const detail = await adminStore.fetchReportDetail(selectedReport.value.id)
    selectedReport.value = detail.report
    selectedMessages.value = detail.messages
    closeDialogOpen.value = false
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to close report'
  } finally {
    isClosing.value = false
  }
}

const handleRealtimeEvent = (event: ReportRealtimeEvent) => {
  const currentReport = selectedReport.value
  if (!currentReport || currentReport.id !== event.reportId) return

  if (event.type === 'REPORT_MESSAGE_ADDED' && event.message) {
    if (!selectedMessages.value.some((msg) => msg.id === event.message?.id)) {
      selectedMessages.value.push(normalizeMessage(event.message))
    }
    return
  }

  if (event.type === 'REPORT_CLOSED' && event.report) {
    currentReport.status = event.report.status
    currentReport.closedAt = event.report.closedAt
    currentReport.deleteAfter = event.report.deletesAfter ?? currentReport.deleteAfter
    const idx = adminStore.reports.findIndex((r) => r.id === event.reportId)
    const listReport = idx >= 0 ? adminStore.reports[idx] : null
    if (listReport) {
      listReport.status = event.report.status
    }
  }
}

const normalizeMessage = (message: ReportMessage): ReportMessage => ({
  ...message,
  isAdmin: message.senderRole === 'ADMIN',
  senderName: message.senderRole === 'ADMIN' ? 'Admin' : 'User',
})

const formatDateTime = (value: string | null | undefined): string => {
  if (!value) return 'Just now'
  const timestamp = Date.parse(value)
  return Number.isNaN(timestamp) ? 'Just now' : new Date(timestamp).toLocaleString()
}
</script>

<template>
  <div class="admin-reports-view">
    <div v-if="!selectedReport" class="list-container">
      <div class="header">
        <h2>Admin Reports Management</h2>
        <p class="subtitle">View and respond to user feedback</p>
      </div>

      <div class="card reports-list">
        <div v-if="adminStore.reports.length === 0" class="empty-state">No active reports.</div>
        <div v-else class="table-responsive">
          <table class="table">
            <thead>
              <tr>
                <th>Subject</th>
                <th>Category</th>
                <th>Status</th>
                <th>Last Updated</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in adminStore.reports" :key="r.id">
                <td class="font-medium">{{ r.title }}</td>
                <td class="text-muted">{{ r.category }}</td>
                <td><StatusChip :status="r.status.toLowerCase()" :label="r.status" /></td>
                <td class="text-muted">
                  {{ formatDateTime(r.closedAt ?? r.createdAt) }}
                </td>
                <td><button class="btn-secondary btn-sm" @click="selectReport(r)">View</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination-info" v-if="adminStore.reportsPagination.totalElements > 0">
          Showing {{ adminStore.reports.length }} of
          {{ adminStore.reportsPagination.totalElements }} reports
        </div>
      </div>
    </div>

    <!-- Detail View -->
    <div v-else class="detail-container">
      <div class="detail-header">
        <button class="btn-back" type="button" @click="backToList">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M15 18l-6-6 6-6" />
            <path d="M9 12h10" />
          </svg>
          <span>Back to reports</span>
        </button>
        <div class="detail-header__title">
          <h2>{{ selectedReport.title }}</h2>
          <StatusChip
            :status="selectedReport.status.toLowerCase()"
            :label="selectedReport.status"
          />
          <span class="realtime-pill" :data-status="reportRealtimeStatus">
            {{ reportRealtimeLabel }}
          </span>
        </div>
      </div>

      <div class="thread">
        <div v-if="selectedMessages.length === 0" class="empty-state">
          No messages in this thread.
        </div>
        <article
          v-for="msg in selectedMessages"
          :key="msg.id"
          :class="['message', msg.isAdmin ? 'message-admin' : 'message-user']"
        >
          <div class="message-avatar" aria-hidden="true">
            {{ msg.isAdmin ? 'VG' : msg.senderName.slice(0, 1).toUpperCase() }}
          </div>
          <div class="message-bubble">
            <header class="message-meta">
              <div>
                <strong>{{ msg.senderName }}</strong>
                <span class="message-role">{{ msg.isAdmin ? 'VibeGraph support' : 'User' }}</span>
              </div>
              <time :datetime="msg.createdAt || undefined">{{ formatDateTime(msg.createdAt) }}</time>
            </header>
            <p class="message-content">{{ msg.body }}</p>
          </div>
        </article>
      </div>

      <div v-if="selectedReport.status !== 'CLOSED'" class="reply-box">
        <div class="reply-box__heading">
          <strong>Admin reply</strong>
          <span>Respond to this support request</span>
        </div>
        <form @submit.prevent="sendReply" class="reply-form">
          <label class="sr-only" for="admin-report-reply">Admin reply</label>
          <textarea
            id="admin-report-reply"
            v-model="replyMessage"
            class="reply-input"
            placeholder="Type a reply..."
            rows="1"
            required
          ></textarea>
          <button type="submit" class="btn-primary" :disabled="isSending || !replyMessage">
            {{ isSending ? 'Sending...' : 'Send Reply' }}
          </button>
          <button type="button" class="btn-danger" @click="closeReport" :disabled="isClosing">
            {{ isClosing ? 'Closing...' : 'Close Report' }}
          </button>
          <div v-if="errorMsg" class="error-text reply-error">{{ errorMsg }}</div>
        </form>
      </div>
      <div v-else class="closed-notice">
        This report is closed. <br />
        <small v-if="selectedReport.deleteAfter" class="text-danger">
          Deletes after {{ new Date(selectedReport.deleteAfter).toLocaleDateString() }}.
        </small>
      </div>
    </div>

    <AdminConfirmDialog
      :open="closeDialogOpen"
      title="Close report"
      message="Close this report thread? Both admin and user will see it as resolved, and cleanup becomes eligible after the retention date."
      confirm-label="Close report"
      :busy="isClosing"
      @cancel="closeDialogOpen = false"
      @confirm="confirmCloseReport"
    />
  </div>
</template>

<style scoped>
.admin-reports-view {
  width: 100%;
  height: 100%;
  margin: 0;
  position: relative;
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
}
.header {
  margin-bottom: var(--vg-space-6);
}
.header h2 {
  margin: 0 0 var(--vg-space-2) 0;
  font-family: var(--vg-font-display);
  color: var(--vg-text);
}
.subtitle {
  color: var(--vg-text-muted);
  margin: 0;
}
.card {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  padding: var(--vg-space-6);
  margin-bottom: var(--vg-space-6);
}
.table-responsive {
  overflow-x: auto;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th,
.table td {
  padding: var(--vg-space-3) var(--vg-space-4);
  text-align: left;
  border-bottom: 1px solid var(--vg-border);
  color: var(--vg-text);
}
.table th {
  background: var(--vg-surface-2);
  color: var(--vg-text-muted);
  font-weight: 600;
  font-size: var(--vg-text-sm);
}
.table tbody tr {
  background: var(--vg-surface);
  transition: background var(--vg-dur-fast);
}
.table tbody tr:hover {
  background: var(--vg-surface-3);
}
.font-medium {
  font-weight: 500;
}
.text-muted {
  color: var(--vg-text-muted);
}
.text-sm {
  font-size: var(--vg-text-sm);
}
.text-danger {
  color: var(--vg-danger);
}
.empty-state {
  padding: var(--vg-space-8);
  text-align: center;
  color: var(--vg-text-muted);
}
.pagination-info {
  margin-top: var(--vg-space-4);
  text-align: right;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.detail-container {
  width: 100%;
  height: calc(100vh - 92px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 32rem;
}
.detail-header {
  margin-bottom: var(--vg-space-3);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--vg-space-3);
}
.detail-header__title {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
}
.detail-header h2 {
  margin: 0;
  color: var(--vg-text);
  font: 700 var(--vg-text-xl) var(--vg-font-display);
}
.realtime-pill {
  min-width: 4.25rem;
  min-height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(34, 197, 94, 0.28);
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.1);
  color: #86efac;
  font-size: var(--vg-text-xs);
  font-weight: 700;
}
.realtime-pill:not([data-status='connected']) {
  border-color: rgba(148, 163, 184, 0.24);
  background: rgba(148, 163, 184, 0.1);
  color: var(--vg-text-dim);
}
.btn-back {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.65rem;
  border: 1px solid var(--vg-border);
  border-radius: 6px;
  background: var(--vg-surface);
  color: var(--vg-text-muted);
  cursor: pointer;
  font: 600 var(--vg-text-sm) var(--vg-font-body);
}
.btn-back:hover {
  border-color: rgba(96, 165, 250, 0.45);
  background: var(--vg-surface-3);
  color: var(--vg-blue-bright);
}
.thread {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
  margin-bottom: var(--vg-space-3);
  overflow-y: auto;
  padding: var(--vg-space-3);
  background: color-mix(in srgb, var(--vg-bg) 72%, transparent);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
}
.message {
  width: min(100%, 46rem);
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: start;
  gap: var(--vg-space-2);
}
.message-user {
  align-self: flex-start;
}
.message-admin {
  align-self: flex-end;
  direction: rtl;
}
.message-admin > * {
  direction: ltr;
}
.message-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 1px solid var(--vg-border);
  border-radius: 50%;
  background: var(--vg-surface-2);
  color: var(--vg-text-muted);
  font-size: 0.68rem;
  font-weight: 800;
}
.message-admin .message-avatar {
  border-color: rgba(34, 211, 238, 0.34);
  color: var(--vg-cyan);
}
.message-bubble {
  min-width: 0;
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
}
.message-admin .message-bubble {
  border-color: rgba(96, 165, 250, 0.32);
  background: rgba(59, 130, 246, 0.1);
}
.message-meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--vg-space-3);
  margin-bottom: var(--vg-space-2);
}
.message-meta > div {
  display: flex;
  align-items: baseline;
  gap: var(--vg-space-2);
}
.message-role,
.message-meta time {
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.message-meta time {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}
.message-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.55;
}
.error-text {
  color: var(--vg-danger);
  font-size: var(--vg-text-sm);
}

.reply-box {
  display: grid;
  grid-template-columns: minmax(9rem, 0.22fr) minmax(0, 1fr);
  align-items: center;
  gap: var(--vg-space-4);
  padding: var(--vg-space-4);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
  box-shadow: var(--vg-shadow-sm);
}
.reply-box__heading {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.reply-box__heading span {
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.reply-form {
  position: relative;
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(18rem, 1fr) auto auto;
  align-items: stretch;
  gap: var(--vg-space-2);
}
.reply-form .sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.reply-error {
  grid-column: 1 / -1;
}
.reply-input {
  height: 44px;
  min-height: 44px;
  resize: none;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--vg-border);
  background: var(--vg-bg);
  color: var(--vg-text);
  border-radius: var(--vg-radius-sm);
  font-family: inherit;
  font-size: var(--vg-text-sm);
}
.reply-input:focus {
  outline: none;
  border-color: var(--vg-blue);
}
.btn-primary {
  min-width: 92px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--vg-grad-blue);
  color: white;
  border: none;
  padding: 0.5rem 1.25rem;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  font-weight: 500;
}
.btn-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.btn-secondary {
  background: var(--vg-surface-3);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  padding: 0.25rem 0.75rem;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
}
.btn-secondary:hover {
  background: rgba(148, 163, 184, 0.16);
}
.btn-danger {
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(239, 68, 68, 0.15);
  color: var(--vg-danger);
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 0.5rem 0.85rem;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  font-weight: 500;
}
.btn-danger:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.25);
}
.btn-danger:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.closed-notice {
  text-align: center;
  padding: var(--vg-space-4);
  background: var(--vg-surface-2);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text-muted);
}

@media (max-width: 700px) {
  .detail-container {
    height: calc(100vh - 120px);
  }
  .detail-header__title,
  .message-meta,
  .message-meta > div {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.2rem;
  }
  .message {
    width: 100%;
  }
  .message-admin {
    direction: ltr;
  }
  .reply-box,
  .reply-form {
    grid-template-columns: 1fr;
  }
  .reply-box {
    position: sticky;
    bottom: 0;
    z-index: 5;
    margin-inline: calc(var(--vg-space-2) * -1);
    border-radius: var(--vg-radius-sm) var(--vg-radius-sm) 0 0;
    box-shadow: 0 -8px 24px rgba(2, 6, 23, 0.18);
  }
}
</style>
