<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useAccountStore } from '@/stores/account'
import type { Report, ReportMessage, FeedbackCategory, ReportRealtimeEvent } from '@/types/api'
import StatusChip from '@/components/ui/StatusChip.vue'
import { useReportRealtime } from '@/composables/useReportRealtime'

const accountStore = useAccountStore()

const selectedReport = ref<Report | null>(null)
const newCategory = ref<FeedbackCategory>('BUG')
const newTitle = ref('')
const newMessage = ref('')
const replyMessage = ref('')
const isSubmitting = ref(false)
const isSending = ref(false)
const errorMsg = ref('')
const selectedReportId = computed(() => selectedReport.value?.id ?? null)

const CATEGORIES: { value: FeedbackCategory; label: string }[] = [
  { value: 'BUG', label: 'Bug Report' },
  { value: 'PROJECT', label: 'Project Issue' },
  { value: 'QUOTA', label: 'Quota / Billing' },
  { value: 'FEATURE', label: 'Feature Request' },
  { value: 'OTHER', label: 'Other' },
]

onMounted(async () => {
  await accountStore.fetchReports()
})

const reportRealtime = useReportRealtime(selectedReportId, {
  onEvent: (event) => {
    handleRealtimeEvent(event)
  },
})
const reportRealtimeStatus = reportRealtime.status

const submitReport = async () => {
  if (!newTitle.value.trim() || !newMessage.value.trim()) return
  errorMsg.value = ''
  isSubmitting.value = true
  try {
    await accountStore.createReport(newCategory.value, newTitle.value, newMessage.value)
    newTitle.value = ''
    newMessage.value = ''
    newCategory.value = 'BUG'
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to submit report'
  } finally {
    isSubmitting.value = false
  }
}

const selectReport = async (report: Report) => {
  try {
    const full = await accountStore.fetchReportDetail(report.id)
    selectedReport.value = full
  } catch {
    selectedReport.value = report
  }
}

const backToList = async () => {
  selectedReport.value = null
  await accountStore.fetchReports()
}

const sendReply = async () => {
  if (!replyMessage.value.trim() || !selectedReport.value) return
  isSending.value = true
  try {
    const msg: ReportMessage = await accountStore.addMessage(
      selectedReport.value.id,
      replyMessage.value,
    )
    selectedReport.value.messages.push(msg)
    replyMessage.value = ''
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to send reply'
  } finally {
    isSending.value = false
  }
}

const handleRealtimeEvent = (event: ReportRealtimeEvent) => {
  const currentReport = selectedReport.value
  if (!currentReport || currentReport.id !== event.reportId) return

  if (event.type === 'REPORT_MESSAGE_ADDED' && event.message) {
    currentReport.messages ||= []
    if (!currentReport.messages.some((msg) => msg.id === event.message?.id)) {
      currentReport.messages.push(normalizeMessage(event.message))
    }
    return
  }

  if (event.type === 'REPORT_CLOSED' && event.report) {
    currentReport.status = event.report.status
    currentReport.closedAt = event.report.closedAt
    currentReport.deletesAfter = event.report.deletesAfter
    const idx = accountStore.reports.findIndex((r) => r.id === event.reportId)
    const listReport = idx >= 0 ? accountStore.reports[idx] : null
    if (listReport) {
      listReport.status = event.report.status
      listReport.closedAt = event.report.closedAt
      listReport.deletesAfter = event.report.deletesAfter
    }
  }
}

const normalizeMessage = (message: ReportMessage): ReportMessage => ({
  ...message,
  isAdmin: message.senderRole === 'ADMIN',
  senderName: message.senderRole === 'ADMIN' ? 'Support Team' : 'You',
})
</script>

<template>
  <div class="reports-view">
    <div v-if="!selectedReport" class="list-container">
      <div class="header">
        <h2>My Reports</h2>
        <p class="subtitle">Submit feedback or report an issue</p>
      </div>

      <div class="card create-report">
        <h3>Submit Report</h3>
        <form @submit.prevent="submitReport" class="form-grid">
          <div class="form-group">
            <label for="report-category">Category</label>
            <select id="report-category" v-model="newCategory" class="form-input" required>
              <option v-for="cat in CATEGORIES" :key="cat.value" :value="cat.value">
                {{ cat.label }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label for="report-subject">Subject</label>
            <input
              id="report-subject"
              v-model="newTitle"
              type="text"
              class="form-input"
              maxlength="200"
              required
            />
          </div>
          <div class="form-group">
            <label for="report-message">Message</label>
            <textarea
              id="report-message"
              v-model="newMessage"
              class="form-input"
              rows="4"
              maxlength="5000"
              required
            ></textarea>
          </div>
          <div v-if="errorMsg" class="error-text">{{ errorMsg }}</div>
          <button
            type="submit"
            class="btn-primary"
            :disabled="isSubmitting || !newTitle || !newMessage"
          >
            {{ isSubmitting ? 'Submitting...' : 'Submit' }}
          </button>
        </form>
      </div>

      <div class="card reports-list">
        <h3>Previous Reports</h3>
        <div v-if="accountStore.reports.length === 0" class="empty-state">No reports found.</div>
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
              <tr v-for="r in accountStore.reports" :key="r.id">
                <td class="font-medium">{{ r.title }}</td>
                <td class="text-muted">{{ r.category }}</td>
                <td><StatusChip :status="r.status.toLowerCase()" :label="r.status" /></td>
                <td class="text-muted">
                  {{ new Date(r.closedAt ?? r.createdAt).toLocaleString() }}
                </td>
                <td><button class="btn-secondary btn-sm" @click="selectReport(r)">View</button></td>
              </tr>
            </tbody>
          </table>
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
            {{ reportRealtimeStatus === 'connected' ? 'Live' : 'Syncing' }}
          </span>
        </div>
      </div>

      <div class="thread">
        <article
          v-for="msg in selectedReport.messages"
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
                <span class="message-role">{{ msg.isAdmin ? 'VibeGraph support' : 'You' }}</span>
              </div>
              <time :datetime="msg.createdAt">{{ new Date(msg.createdAt).toLocaleString() }}</time>
            </header>
            <p class="message-content">{{ msg.body }}</p>
          </div>
        </article>
      </div>

      <div v-if="selectedReport.status === 'OPEN'" class="reply-box">
        <div class="reply-box__heading">
          <strong>Reply</strong>
          <span>Continue this support conversation</span>
        </div>
        <form @submit.prevent="sendReply" class="reply-form">
          <label class="sr-only" for="report-reply">Reply</label>
          <textarea
            id="report-reply"
            v-model="replyMessage"
            class="form-input reply-input"
            placeholder="Type a reply..."
            rows="2"
            maxlength="5000"
            required
          ></textarea>
          <div v-if="errorMsg" class="error-text">{{ errorMsg }}</div>
          <button type="submit" class="btn-primary" :disabled="isSending || !replyMessage">
            {{ isSending ? 'Sending...' : 'Send' }}
          </button>
        </form>
      </div>
      <div v-else class="closed-notice">This report is closed.</div>
    </div>
  </div>
</template>

<style scoped>
.reports-view {
  position: relative;
  width: 100%;
  height: 100%;
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
}
.header {
  margin-bottom: var(--vg-space-4);
}
.header h2 {
  margin: 0 0 var(--vg-space-1) 0;
  font: 700 clamp(1.625rem, 2.2vw, 1.875rem) var(--vg-font-display);
  color: var(--vg-text);
}
.subtitle {
  color: var(--vg-text-muted);
  margin: 0;
}
.card {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  padding: var(--vg-space-4);
  margin-bottom: var(--vg-space-4);
  box-shadow: var(--vg-shadow-sm);
}
.card h3 {
  margin: 0 0 var(--vg-space-4) 0;
  color: var(--vg-text);
}
.form-grid {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--vg-space-3);
}
.form-group {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-1);
}
.form-group label {
  font-weight: 500;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}
.form-input {
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  font-family: inherit;
  font-size: var(--vg-text-sm);
  transition: border-color var(--vg-dur-fast) var(--vg-ease-out);
}
.form-input:focus {
  outline: none;
  border-color: var(--vg-blue);
}
.btn-primary {
  width: auto;
  min-height: 38px;
  align-self: flex-start;
  background: var(--vg-grad-blue);
  color: white;
  border: none;
  padding: 0.4rem 0.75rem;
  text-align: left;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  font-weight: 500;
  transition: transform var(--vg-dur-fast) var(--vg-ease-out);
}
.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  opacity: 0.9;
}
.btn-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.btn-secondary {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--vg-surface-3);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  padding: 0.25rem 0.75rem;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  transition: background var(--vg-dur-fast) var(--vg-ease-out);
}
.btn-secondary:hover {
  background: rgba(148, 163, 184, 0.16);
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
  padding: var(--vg-space-2) var(--vg-space-3);
  text-align: center;
  vertical-align: middle;
  border-bottom: 1px solid var(--vg-border);
}
.table td:first-child,
.table th:first-child {
  text-align: left;
}
.table td:last-child,
.table th:last-child {
  width: 6rem;
}
.table th {
  background: var(--vg-surface-2);
  color: var(--vg-text-muted);
  font-weight: 600;
}
.table tbody tr {
  background: var(--vg-surface);
  transition: background var(--vg-dur-fast) var(--vg-ease-out);
}
.table tbody tr:hover {
  background: var(--vg-surface-3);
}
.font-medium {
  font-weight: 500;
  color: var(--vg-text);
}
.text-muted {
  color: var(--vg-text-muted);
}
.text-sm {
  font-size: var(--vg-text-sm);
}
.empty-state {
  padding: var(--vg-space-4) 0;
  text-align: left;
  color: var(--vg-text-muted);
}
.error-text {
  color: var(--vg-danger);
  font-size: var(--vg-text-sm);
}

/* Detail */
.detail-container {
  width: 100%;
  height: calc(100vh - 24px);
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
  transition:
    background-color var(--vg-dur-fast) var(--vg-ease-out),
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    color var(--vg-dur-fast) var(--vg-ease-out);
}
.btn-back:hover {
  border-color: rgba(96, 165, 250, 0.45);
  background: var(--vg-surface-3);
  color: var(--vg-blue-bright);
}
.btn-back:focus-visible {
  outline: 2px solid var(--vg-blue-bright);
  outline-offset: 2px;
}
.thread {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
  margin-bottom: var(--vg-space-3);
  overflow-y: auto;
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--vg-bg) 72%, transparent);
}
.message {
  width: min(100%, 46rem);
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: start;
  gap: var(--vg-space-2);
}
.message-user {
  align-self: flex-end;
  direction: rtl;
}
.message-user > * {
  direction: ltr;
}
.message-admin {
  align-self: flex-start;
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
.message-user .message-bubble {
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
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: var(--vg-space-2);
}
.message-meta strong {
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
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
  color: var(--vg-text);
  line-height: 1.55;
  text-wrap: pretty;
}
.reply-box {
  display: grid;
  grid-template-columns: minmax(9rem, 0.24fr) minmax(0, 1fr);
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
.reply-box__heading strong {
  color: var(--vg-text);
}
.reply-box__heading span {
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.reply-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: stretch;
  gap: var(--vg-space-2);
}
.reply-input {
  height: 44px;
  min-height: 44px;
  padding-block: 0.65rem;
  resize: none;
}
.reply-form .btn-primary {
  min-width: 76px;
  height: 44px;
  min-height: 44px;
  align-self: stretch;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.closed-notice {
  text-align: left;
  padding: var(--vg-space-3);
  background: var(--vg-surface-2);
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text-muted);
}

.sr-only {
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

@media (max-width: 700px) {
  .detail-container {
    width: 100%;
    height: calc(100vh - 100px);
    min-height: 30rem;
  }
  .detail-header__title {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--vg-space-2);
  }
  .message {
    width: 100%;
  }
  .message-user {
    direction: ltr;
  }
  .message-meta,
  .message-meta > div {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.2rem;
  }
  .reply-box,
  .reply-form {
    grid-template-columns: 1fr;
  }
  .detail-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
