<script setup lang="ts">
import { computed, nextTick, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAccountStore } from '@/stores/account'
import type { Report, ReportMessage, FeedbackCategory, ReportRealtimeEvent } from '@/types/api'
import StatusChip from '@/components/ui/StatusChip.vue'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import { useReportRealtime } from '@/composables/useReportRealtime'
import { useSilentRefresh } from '@/composables/useSilentRefresh'
import ThemedSelect from '@/components/ui/ThemedSelect.vue'

const accountStore = useAccountStore()
const { t } = useI18n({ useScope: 'global' })

const selectedReport = ref<Report | null>(null)
let reportSelectionVersion = 0
const newCategory = ref<FeedbackCategory>('BUG')
const newTitle = ref('')
const newMessage = ref('')
const replyMessage = ref('')
const isSubmitting = ref(false)
const isSending = ref(false)
const isClosing = ref(false)
const closeDialogOpen = ref(false)
const errorMsg = ref('')
const threadRef = ref<HTMLElement | null>(null)
const selectedReportId = computed(() => selectedReport.value?.id ?? null)

const categories = computed<{ value: FeedbackCategory; label: string }[]>(() => [
  { value: 'BUG', label: t('user.reports.bug') },
  { value: 'PROJECT', label: t('user.reports.project') },
  { value: 'QUOTA', label: t('user.reports.quota') },
  { value: 'FEATURE', label: t('user.reports.feature') },
  { value: 'OTHER', label: t('user.reports.other') },
])

onMounted(async () => {
  try {
    await accountStore.fetchReports()
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : t('user.reports.loadFallback')
  }
})

// Kept alive by UserLayout: report status changes reflect on re-activation
// without a reload flash.
useSilentRefresh(() =>
  accountStore.fetchReports().then(
    () => {
      errorMsg.value = ''
    },
    () => undefined,
  ),
)

const reportRealtime = useReportRealtime(selectedReportId, {
  onEvent: (event) => {
    handleRealtimeEvent(event)
  },
})
const reportRealtimeStatus = reportRealtime.status
const reportRealtimeActive = reportRealtime.active
const reportRealtimeLabel = computed(() => {
  if (reportRealtimeStatus.value === 'connected' && reportRealtimeActive.value)
    return t('user.reports.live')
  if (reportRealtimeStatus.value === 'error') return t('user.reports.realtimeUnavailable')
  if (reportRealtimeStatus.value === 'connecting' || reportRealtimeStatus.value === 'connected') {
    return t('user.reports.syncing')
  }
  return t('user.reports.offline')
})

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
    errorMsg.value = e instanceof Error ? e.message : t('user.reports.submitFallback')
  } finally {
    isSubmitting.value = false
  }
}

const selectReport = async (report: Report) => {
  const selectionVersion = ++reportSelectionVersion
  try {
    const full = await accountStore.fetchReportDetail(report.id)
    if (selectionVersion === reportSelectionVersion) {
      selectedReport.value = full
      await scrollThreadToBottom()
    }
  } catch {
    if (selectionVersion === reportSelectionVersion) {
      selectedReport.value = report
      await scrollThreadToBottom()
    }
  }
}

const backToList = async () => {
  reportSelectionVersion += 1
  selectedReport.value = null
  await accountStore.fetchReports()
}

const sendReply = async () => {
  const report = selectedReport.value
  const body = replyMessage.value.trim()
  if (!body || !report) return
  const reportId = report.id
  isSending.value = true
  try {
    const msg: ReportMessage = await accountStore.addMessage(reportId, body)
    if (selectedReport.value?.id === reportId) {
      if (!selectedReport.value.messages.some((item) => item.id === msg.id)) {
        selectedReport.value.messages.push(msg)
      }
      replyMessage.value = ''
      await scrollThreadToBottom('smooth')
    }
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : t('user.reports.replyFallback')
  } finally {
    isSending.value = false
  }
}

const closeReport = () => {
  if (selectedReport.value?.status === 'OPEN') closeDialogOpen.value = true
}

const confirmCloseReport = async () => {
  if (!selectedReport.value) return
  isClosing.value = true
  errorMsg.value = ''
  try {
    const closed = await accountStore.closeReport(selectedReport.value.id)
    selectedReport.value = { ...selectedReport.value, ...closed }
    closeDialogOpen.value = false
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : t('user.reports.closeFallback')
  } finally {
    isClosing.value = false
  }
}

const handleRealtimeEvent = (event: ReportRealtimeEvent) => {
  const currentReport = selectedReport.value
  if (!currentReport || currentReport.id !== event.reportId) return

  if (event.type === 'REPORT_MESSAGE_ADDED' && event.message) {
    currentReport.messages ||= []
    if (!currentReport.messages.some((msg) => msg.id === event.message?.id)) {
      currentReport.messages.push(normalizeMessage(event.message))
      void scrollThreadToBottom('smooth')
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
  senderName:
    message.senderRole === 'ADMIN' ? t('user.reports.supportTeam') : t('user.reports.you'),
})

const scrollThreadToBottom = async (behavior: ScrollBehavior = 'auto'): Promise<void> => {
  await nextTick()
  const thread = threadRef.value
  if (!thread) return
  if (typeof thread.scrollTo === 'function') {
    thread.scrollTo({ top: thread.scrollHeight, behavior })
  } else {
    thread.scrollTop = thread.scrollHeight
  }
}

const formatDateTime = (value: string | null | undefined): string => {
  if (!value) return t('user.reports.justNow')
  const timestamp = Date.parse(value)
  return Number.isNaN(timestamp) ? t('user.reports.justNow') : new Date(timestamp).toLocaleString()
}
</script>

<template>
  <div class="reports-view">
    <div v-if="!selectedReport" class="list-container">
      <div class="header">
        <h2>{{ t('user.reports.title') }}</h2>
        <p class="subtitle">{{ t('user.reports.subtitle') }}</p>
      </div>

      <div class="card create-report">
        <h3>{{ t('user.reports.submitTitle') }}</h3>
        <form @submit.prevent="submitReport" class="form-grid">
          <div class="form-group">
            <label for="report-category">{{ t('user.reports.category') }}</label>
            <ThemedSelect
              v-model="newCategory"
              class="form-select"
              input-id="report-category"
              name="reportCategory"
              :options="categories"
              :aria-label="t('user.reports.category')"
            />
          </div>
          <div class="form-group">
            <label for="report-subject">{{ t('user.reports.subject') }}</label>
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
            <label for="report-message">{{ t('user.reports.message') }}</label>
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
            {{ isSubmitting ? t('user.reports.submitting') : t('user.reports.submit') }}
          </button>
        </form>
      </div>

      <div class="card reports-list">
        <h3>{{ t('user.reports.previous') }}</h3>
        <div v-if="accountStore.reports.length === 0" class="empty-state">
          {{ t('user.reports.empty') }}
        </div>
        <div v-else class="table-responsive">
          <table class="table">
            <thead>
              <tr>
                <th>{{ t('user.reports.subject') }}</th>
                <th>{{ t('user.reports.category') }}</th>
                <th>{{ t('user.reports.status') }}</th>
                <th>{{ t('user.reports.lastUpdated') }}</th>
                <th>{{ t('user.reports.action') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in accountStore.reports" :key="r.id">
                <td class="font-medium">{{ r.title }}</td>
                <td class="text-muted">{{ r.category }}</td>
                <td><StatusChip :status="r.status.toLowerCase()" :label="r.status" /></td>
                <td class="text-muted">
                  {{ formatDateTime(r.closedAt ?? r.createdAt) }}
                </td>
                <td>
                  <button class="btn-secondary btn-sm" @click="selectReport(r)">
                    {{ t('user.reports.view') }}
                  </button>
                </td>
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
          <span>{{ t('user.reports.back') }}</span>
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

      <div ref="threadRef" class="thread" aria-live="polite">
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
                <span class="message-role">{{
                  msg.isAdmin ? t('user.reports.support') : t('user.reports.you')
                }}</span>
              </div>
              <time :datetime="msg.createdAt || undefined">{{
                formatDateTime(msg.createdAt)
              }}</time>
            </header>
            <p class="message-content">{{ msg.body }}</p>
          </div>
        </article>
      </div>

      <div v-if="selectedReport.status === 'OPEN'" class="reply-box">
        <div class="reply-box__heading">
          <strong>{{ t('user.reports.reply') }}</strong>
          <span>{{ t('user.reports.continue') }}</span>
        </div>
        <form @submit.prevent="sendReply" class="reply-form">
          <label class="sr-only" for="report-reply">{{ t('user.reports.reply') }}</label>
          <textarea
            id="report-reply"
            v-model="replyMessage"
            class="form-input reply-input"
            :placeholder="t('user.reports.replyPlaceholder')"
            rows="2"
            maxlength="5000"
            required
          ></textarea>
          <div v-if="errorMsg" class="error-text">{{ errorMsg }}</div>
          <button type="submit" class="btn-primary" :disabled="isSending || !replyMessage">
            {{ isSending ? t('user.reports.sending') : t('user.reports.send') }}
          </button>
          <button type="button" class="btn-danger" :disabled="isClosing" @click="closeReport">
            {{ t('user.reports.close') }}
          </button>
        </form>
      </div>
      <div v-else class="closed-notice">
        {{ t('user.reports.closed') }}
        <small v-if="selectedReport.deletesAfter">
          {{
            t('user.reports.scheduledDeletion', {
              date: new Date(selectedReport.deletesAfter).toLocaleDateString(),
            })
          }}
        </small>
      </div>
    </div>

    <AdminConfirmDialog
      :open="closeDialogOpen"
      :title="t('user.reports.close')"
      :message="t('user.reports.closeMessage')"
      :confirm-label="t('user.reports.close')"
      :busy="isClosing"
      @cancel="closeDialogOpen = false"
      @confirm="confirmCloseReport"
    />
  </div>
</template>

<style scoped>
.reports-view {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
}
.list-container {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
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
.btn-danger {
  min-height: 44px;
  padding: 0.4rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--vg-danger) 45%, var(--vg-border));
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--vg-danger) 10%, transparent);
  color: var(--vg-danger);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.btn-danger:disabled {
  opacity: 0.55;
  cursor: wait;
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
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 0;
  overflow: hidden;
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
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
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
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: stretch;
  gap: var(--vg-space-2);
}
.reply-form .error-text {
  grid-column: 1 / -1;
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
.closed-notice small {
  display: block;
  margin-top: 0.35rem;
  color: var(--vg-text-dim);
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
    height: 100%;
    min-height: 0;
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
  .reply-box {
    position: sticky;
    bottom: 0;
    z-index: 5;
    margin-inline: calc(var(--vg-space-2) * -1);
    border-radius: var(--vg-radius-sm) var(--vg-radius-sm) 0 0;
    box-shadow: 0 -8px 24px rgba(2, 6, 23, 0.18);
  }
  .detail-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
