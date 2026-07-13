<script setup lang="ts">
import { ref } from 'vue'
import type { Report } from '@/types/api'
import StatusChip from '@/components/ui/StatusChip.vue'

const reports = ref<Report[]>([])
const selectedReport = ref<Report | null>(null)

const newSubject = ref('')
const newMessage = ref('')

const replyMessage = ref('')

const submitReport = () => {
  if (!newSubject.value.trim() || !newMessage.value.trim()) return

  const report: Report = {
    id: `rep-${Date.now()}`,
    subject: newSubject.value,
    status: 'open',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    messages: [
      {
        id: `msg-${Date.now()}`,
        senderId: 'me',
        senderName: 'You',
        content: newMessage.value,
        createdAt: new Date().toISOString(),
        isAdmin: false
      }
    ]
  }
  reports.value.push(report)
  newSubject.value = ''
  newMessage.value = ''
}

const selectReport = (report: Report) => {
  selectedReport.value = report
}

const backToList = () => {
  selectedReport.value = null
}

const sendReply = () => {
  if (!replyMessage.value.trim() || !selectedReport.value) return
  
  selectedReport.value.messages.push({
    id: `msg-${Date.now()}`,
    senderId: 'me',
    senderName: 'You',
    content: replyMessage.value,
    createdAt: new Date().toISOString(),
    isAdmin: false
  })
  replyMessage.value = ''
}
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
            <label>Subject</label>
            <input v-model="newSubject" type="text" class="form-input" required />
          </div>
          <div class="form-group">
            <label>Message</label>
            <textarea v-model="newMessage" class="form-input" rows="4" required></textarea>
          </div>
          <button type="submit" class="btn-primary" :disabled="!newSubject || !newMessage">Submit</button>
        </form>
      </div>

      <div class="card reports-list">
        <h3>Previous Reports</h3>
        <div v-if="reports.length === 0" class="empty-state">No reports found.</div>
        <div v-else class="table-responsive">
          <table class="table">
            <thead>
              <tr>
                <th>Subject</th>
                <th>Status</th>
                <th>Last Updated</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in reports" :key="r.id">
                <td class="font-medium">{{ r.subject }}</td>
                <td><StatusChip :status="r.status" :label="r.status" /></td>
                <td class="text-muted">{{ new Date(r.updatedAt).toLocaleString() }}</td>
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
        <button class="btn-back" @click="backToList">← Back to reports</button>
        <h2>{{ selectedReport.subject }}</h2>
        <StatusChip :status="selectedReport.status" :label="selectedReport.status" />
      </div>

      <div class="thread">
        <div v-for="msg in selectedReport.messages" :key="msg.id" :class="['message', msg.isAdmin ? 'message-admin' : 'message-user']">
          <div class="message-meta">
            <strong>{{ msg.senderName }}</strong>
            <span class="text-sm text-muted">{{ new Date(msg.createdAt).toLocaleString() }}</span>
          </div>
          <div class="message-content">{{ msg.content }}</div>
        </div>
      </div>

      <div v-if="selectedReport.status === 'open'" class="reply-box">
        <form @submit.prevent="sendReply" class="reply-form">
          <textarea v-model="replyMessage" class="form-input reply-input" placeholder="Type a reply..." rows="2" required></textarea>
          <button type="submit" class="btn-primary" :disabled="!replyMessage">Send</button>
        </form>
      </div>
      <div v-else class="closed-notice">
        This report is closed.
      </div>
    </div>
  </div>
</template>

<style scoped>
.reports-view {
  max-width: 900px;
  margin: 0 auto;
  position: relative;
  height: 100%;
}
.header { margin-bottom: var(--vg-space-6); }
.header h2 { margin: 0 0 var(--vg-space-2) 0; font-family: var(--vg-font-display); color: var(--vg-text); }
.subtitle { color: var(--vg-text-muted); margin: 0; }
.card { background: var(--vg-surface); border: 1px solid var(--vg-border); border-radius: var(--vg-radius); padding: var(--vg-space-6); margin-bottom: var(--vg-space-6); box-shadow: var(--vg-shadow-sm); }
.card h3 { margin: 0 0 var(--vg-space-4) 0; color: var(--vg-text); }
.form-grid { display: flex; flex-direction: column; gap: var(--vg-space-4); }
.form-group { display: flex; flex-direction: column; gap: var(--vg-space-2); }
.form-group label { font-weight: 500; font-size: var(--vg-text-sm); color: var(--vg-text-muted); }
.form-input { padding: 0.5rem 0.75rem; background: var(--vg-bg-elev); color: var(--vg-text); border: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm); font-family: inherit; font-size: var(--vg-text-base); transition: border-color var(--vg-dur-fast) var(--vg-ease-out); }
.form-input:focus { outline: none; border-color: var(--vg-blue); }
.btn-primary { background: var(--vg-grad-blue); color: white; border: none; padding: 0.5rem 1rem; border-radius: var(--vg-radius-sm); cursor: pointer; font-weight: 500; transition: transform var(--vg-dur-fast) var(--vg-ease-out); }
.btn-primary:hover:not(:disabled) { transform: translateY(-1px); opacity: 0.9; }
.btn-primary:disabled { opacity: 0.65; cursor: not-allowed; }
.btn-secondary { background: var(--vg-surface-3); color: var(--vg-text); border: 1px solid var(--vg-border); padding: 0.25rem 0.75rem; border-radius: var(--vg-radius-sm); cursor: pointer; transition: background var(--vg-dur-fast) var(--vg-ease-out); }
.btn-secondary:hover { background: rgba(148, 163, 184, 0.16); }
.table-responsive { overflow-x: auto; }
.table { width: 100%; border-collapse: collapse; }
.table th, .table td { padding: var(--vg-space-3) var(--vg-space-4); text-align: left; border-bottom: 1px solid var(--vg-border); }
.table th { background: var(--vg-surface-2); color: var(--vg-text-muted); font-weight: 600; }
.table tbody tr { background: var(--vg-surface); transition: background var(--vg-dur-fast) var(--vg-ease-out); }
.table tbody tr:hover { background: var(--vg-surface-3); }
.font-medium { font-weight: 500; color: var(--vg-text); }
.text-muted { color: var(--vg-text-muted); }
.text-sm { font-size: var(--vg-text-sm); }
.empty-state { padding: var(--vg-space-8); text-align: center; color: var(--vg-text-muted); }

/* Detail */
.detail-container { display: flex; flex-direction: column; height: 100%; min-height: 400px; }
.detail-header { margin-bottom: var(--vg-space-6); display: flex; align-items: center; gap: var(--vg-space-4); flex-wrap: wrap; }
.detail-header h2 { margin: 0; font-family: var(--vg-font-display); color: var(--vg-text); }
.btn-back { background: transparent; border: none; color: var(--vg-blue-bright); cursor: pointer; font-weight: 500; padding: 0; transition: color var(--vg-dur-fast) var(--vg-ease-out); }
.btn-back:hover { color: var(--vg-cyan); }
.thread { flex: 1; display: flex; flex-direction: column; gap: var(--vg-space-4); margin-bottom: var(--vg-space-8); overflow-y: auto; padding-bottom: 100px; }
.message { padding: var(--vg-space-4); border-radius: var(--vg-radius-sm); max-width: 80%; }
.message-user { background: rgba(59, 130, 246, 0.15); align-self: flex-end; border-bottom-right-radius: 0; }
.message-admin { background: var(--vg-surface-2); align-self: flex-start; border: 1px solid var(--vg-border); border-bottom-left-radius: 0; }
.message-meta { display: flex; justify-content: space-between; gap: var(--vg-space-4); margin-bottom: var(--vg-space-2); }
.message-content { white-space: pre-wrap; color: var(--vg-text); }

/* Reply Box - sticky bottom on mobile */
.reply-box { 
  background: var(--vg-bg-elev); 
  border-top: 1px solid var(--vg-border); 
  padding: var(--vg-space-4) 0; 
  position: sticky; 
  bottom: 0; 
}
.reply-form { display: flex; gap: var(--vg-space-4); align-items: flex-end; }
.reply-input { flex: 1; resize: none; }
.closed-notice { text-align: center; padding: var(--vg-space-4); background: var(--vg-surface-2); border-radius: var(--vg-radius-sm); color: var(--vg-text-muted); }
</style>
