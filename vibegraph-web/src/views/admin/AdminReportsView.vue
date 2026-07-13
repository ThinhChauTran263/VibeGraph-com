<script setup lang="ts">
import { ref } from 'vue'
import type { Report } from '@/types/api'
import StatusChip from '@/components/ui/StatusChip.vue'

const reports = ref<Report[]>([
  {
    id: 'rep-1',
    subject: 'Cannot upload archive',
    status: 'open',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    messages: [
      {
        id: 'msg-1',
        senderId: 'usr-1',
        senderName: 'Alice',
        content: 'I keep getting a 400 error when uploading my zip file.',
        createdAt: new Date().toISOString(),
        isAdmin: false
      }
    ]
  }
])

const selectedReport = ref<Report | null>(null)
const replyMessage = ref('')

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
    senderId: 'admin-1',
    senderName: 'Admin',
    content: replyMessage.value,
    createdAt: new Date().toISOString(),
    isAdmin: true
  })
  selectedReport.value.updatedAt = new Date().toISOString()
  replyMessage.value = ''
}

const closeReport = () => {
  if (selectedReport.value && confirm('Close this report?')) {
    selectedReport.value.status = 'closed'
  }
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
        <div v-if="reports.length === 0" class="empty-state">No active reports.</div>
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
        <button class="btn-back" @click="backToList">← Back to list</button>
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
          <textarea v-model="replyMessage" class="form-input reply-input" placeholder="Admin reply..." rows="3" required></textarea>
          <div class="action-buttons">
            <button type="submit" class="btn-primary" :disabled="!replyMessage">Send Reply</button>
            <button type="button" class="btn-danger" @click="closeReport">Close Report</button>
          </div>
        </form>
      </div>
      <div v-else class="closed-notice">
        This report is closed. <br/>
        <small class="text-danger">Deletes after 30 days from closure.</small>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-reports-view { 
  max-width: 900px; 
  margin: 0 auto;
  height: 100%; 
  position: relative; 
}
.header { margin-bottom: var(--vg-space-6); }
.header h2 { margin: 0 0 var(--vg-space-2) 0; font-family: var(--vg-font-display); color: var(--vg-text); }
.subtitle { color: var(--vg-text-muted); margin: 0; }
.card { background: var(--vg-surface); border: 1px solid var(--vg-border); border-radius: var(--vg-radius); padding: var(--vg-space-6); margin-bottom: var(--vg-space-6); }
.table-responsive { overflow-x: auto; }
.table { width: 100%; border-collapse: collapse; }
.table th, .table td { padding: var(--vg-space-3) var(--vg-space-4); text-align: left; border-bottom: 1px solid var(--vg-border); color: var(--vg-text); }
.table th { background: var(--vg-surface-2); color: var(--vg-text-muted); font-weight: 600; font-size: var(--vg-text-sm); }
.table tbody tr { background: var(--vg-surface); transition: background var(--vg-dur-fast); }
.table tbody tr:hover { background: var(--vg-surface-3); }
.font-medium { font-weight: 500; }
.text-muted { color: var(--vg-text-muted); }
.text-sm { font-size: var(--vg-text-sm); }
.text-danger { color: var(--vg-danger); }
.empty-state { padding: var(--vg-space-8); text-align: center; color: var(--vg-text-muted); }

.detail-container { display: flex; flex-direction: column; height: 100%; min-height: 400px; }
.detail-header { margin-bottom: var(--vg-space-6); display: flex; align-items: center; gap: var(--vg-space-4); flex-wrap: wrap; }
.detail-header h2 { margin: 0; color: var(--vg-text); font-family: var(--vg-font-display); }
.btn-back { background: transparent; border: none; color: var(--vg-blue-bright); cursor: pointer; font-weight: 500; padding: 0; }
.thread { flex: 1; display: flex; flex-direction: column; gap: var(--vg-space-4); margin-bottom: var(--vg-space-8); overflow-y: auto; padding-bottom: 120px; }
.message { padding: var(--vg-space-4); border-radius: var(--vg-radius-sm); max-width: 80%; }
.message-user { background: var(--vg-surface-2); align-self: flex-start; border: 1px solid var(--vg-border); border-bottom-left-radius: 0; color: var(--vg-text); }
.message-admin { background: rgba(59, 130, 246, 0.15); align-self: flex-end; border-bottom-right-radius: 0; color: var(--vg-text); }
.message-meta { display: flex; justify-content: space-between; gap: var(--vg-space-4); margin-bottom: var(--vg-space-2); }
.message-content { white-space: pre-wrap; }

.reply-box { background: var(--vg-bg-elev); border-top: 1px solid var(--vg-border); padding: var(--vg-space-4) 0; position: sticky; bottom: 0; }
.reply-form { display: flex; flex-direction: column; gap: var(--vg-space-4); }
.reply-input { resize: vertical; padding: 0.75rem; border: 1px solid var(--vg-border); background: var(--vg-bg); color: var(--vg-text); border-radius: var(--vg-radius-sm); font-family: inherit; font-size: var(--vg-text-base); }
.reply-input:focus { outline: none; border-color: var(--vg-blue); }
.action-buttons { display: flex; gap: var(--vg-space-4); }
.btn-primary { background: var(--vg-grad-blue); color: white; border: none; padding: 0.5rem 1.25rem; border-radius: var(--vg-radius-sm); cursor: pointer; font-weight: 500; }
.btn-primary:disabled { opacity: 0.65; cursor: not-allowed; }
.btn-secondary { background: var(--vg-surface-3); color: var(--vg-text); border: 1px solid var(--vg-border); padding: 0.25rem 0.75rem; border-radius: var(--vg-radius-sm); cursor: pointer; }
.btn-secondary:hover { background: rgba(148, 163, 184, 0.16); }
.btn-danger { background: rgba(239, 68, 68, 0.15); color: var(--vg-danger); border: 1px solid rgba(239, 68, 68, 0.3); padding: 0.5rem 1.25rem; border-radius: var(--vg-radius-sm); cursor: pointer; font-weight: 500; }
.btn-danger:hover { background: rgba(239, 68, 68, 0.25); }
.closed-notice { text-align: center; padding: var(--vg-space-4); background: var(--vg-surface-2); border-radius: var(--vg-radius-sm); color: var(--vg-text-muted); }
</style>
