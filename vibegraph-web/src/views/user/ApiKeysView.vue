<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAccountStore } from '@/stores/account'
import StatusChip from '@/components/ui/StatusChip.vue'
import type { ApiKey } from '@/types/api'

const accountStore = useAccountStore()
const newKeyName = ref('')
const isCreating = ref(false)
const recentlyCreatedKey = ref<ApiKey | null>(null)

onMounted(async () => {
  await accountStore.fetchApiKeys()
})

const handleCreate = async () => {
  if (!newKeyName.value.trim()) return
  
  isCreating.value = true
  try {
    const key = await accountStore.createApiKey(newKeyName.value)
    recentlyCreatedKey.value = key
    newKeyName.value = ''
  } finally {
    isCreating.value = false
  }
}

const handleDisable = async (id: string) => {
  if (confirm('Are you sure you want to disable this API key? This action cannot be undone.')) {
    await accountStore.disableApiKey(id)
  }
}

const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text)
  alert('Copied to clipboard!')
}
</script>

<template>
  <div class="api-keys-view">
    <div class="header">
      <h2>API Keys</h2>
      <p class="subtitle">Manage your API keys for accessing VibeGraph programmatically.</p>
    </div>

    <!-- Create new key form -->
    <div class="create-section card">
      <h3>Create New Key</h3>
      <form @submit.prevent="handleCreate" class="create-form">
        <div class="input-group">
          <input 
            type="text" 
            v-model="newKeyName" 
            placeholder="Key Name (e.g. Production Env)"
            class="form-input"
            required
          />
          <button type="submit" class="btn-primary" :disabled="isCreating || !newKeyName">
            {{ isCreating ? 'Creating...' : 'Create Key' }}
          </button>
        </div>
      </form>
      
      <!-- Show secret only once after creation -->
      <div v-if="recentlyCreatedKey" class="secret-alert">
        <div class="secret-alert-header">
          <strong>Key Created Successfully!</strong>
          <span>Please copy this secret key now. You will not be able to see it again.</span>
        </div>
        <div class="secret-box">
          <code>{{ recentlyCreatedKey.secret }}</code>
          <button @click="copyToClipboard(recentlyCreatedKey.secret!)" class="btn-secondary">Copy</button>
        </div>
      </div>
    </div>

    <!-- List of existing keys -->
    <div class="list-section card">
      <h3>Your API Keys</h3>
      
      <div v-if="accountStore.apiKeys.length === 0" class="empty-state">
        You haven't created any API keys yet.
      </div>
      
      <div v-else class="table-responsive">
        <table class="keys-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Token Prefix</th>
              <th>Status</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="apiKey in accountStore.apiKeys" :key="apiKey.id">
              <td class="font-medium">{{ apiKey.name }}</td>
              <td class="font-mono">vg-****</td>
              <td>
                <StatusChip 
                  :status="apiKey.disabled ? 'disabled' : 'active'" 
                  :label="apiKey.disabled ? 'Disabled' : 'Active'" 
                />
              </td>
              <td class="text-muted">{{ new Date(apiKey.createdAt).toLocaleDateString() }}</td>
              <td>
                <button 
                  v-if="!apiKey.disabled" 
                  @click="handleDisable(apiKey.id)" 
                  class="btn-danger btn-disable"
                >
                  Disable
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.api-keys-view {
  max-width: 900px;
  margin: 0 auto;
}
.header {
  margin-bottom: var(--vg-space-8);
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
  box-shadow: var(--vg-shadow-sm);
}
.card h3 {
  margin: 0 0 var(--vg-space-4) 0;
  font-size: var(--vg-text-lg);
  color: var(--vg-text);
}
.create-form {
  margin-bottom: var(--vg-space-4);
}
.input-group {
  display: flex;
  gap: var(--vg-space-4);
}
.form-input {
  flex: 1;
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  font-size: var(--vg-text-base);
  font-family: inherit;
  transition: border-color var(--vg-dur-fast) var(--vg-ease-out);
}
.form-input:focus {
  outline: none;
  border-color: var(--vg-blue);
}
.btn-primary {
  background: var(--vg-grad-blue);
  color: white;
  border: none;
  padding: 0.5rem 1.25rem;
  border-radius: var(--vg-radius-sm);
  font-weight: 500;
  cursor: pointer;
  transition: transform var(--vg-dur-fast) var(--vg-ease-out), opacity var(--vg-dur-fast) var(--vg-ease-out);
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
.btn-danger {
  background: rgba(239, 68, 68, 0.15);
  color: var(--vg-danger);
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 0.375rem 0.75rem;
  border-radius: var(--vg-radius-sm);
  font-size: var(--vg-text-sm);
  cursor: pointer;
  transition: background var(--vg-dur-fast) var(--vg-ease-out);
}
.btn-danger:hover {
  background: rgba(239, 68, 68, 0.25);
}

.secret-alert {
  background-color: rgba(34, 197, 94, 0.15);
  border: 1px solid rgba(34, 197, 94, 0.3);
  border-radius: var(--vg-radius-sm);
  padding: var(--vg-space-4);
  margin-top: var(--vg-space-4);
}
.secret-alert-header {
  display: flex;
  flex-direction: column;
  color: var(--vg-green-bright);
  margin-bottom: var(--vg-space-3);
}
.secret-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--vg-bg-elev);
  border: 1px solid var(--vg-border);
  padding: var(--vg-space-3);
  border-radius: var(--vg-radius-sm);
}
.secret-box code {
  font-size: var(--vg-text-lg);
  font-weight: 600;
  color: var(--vg-text);
}

.table-responsive {
  overflow-x: auto;
}
.keys-table {
  width: 100%;
  border-collapse: collapse;
}
.keys-table th,
.keys-table td {
  padding: var(--vg-space-4);
  text-align: left;
  border-bottom: 1px solid var(--vg-border);
}
.keys-table th {
  background-color: var(--vg-surface-2);
  font-weight: 600;
  color: var(--vg-text-muted);
}
.keys-table tbody tr {
  background-color: var(--vg-surface);
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out);
}
.keys-table tbody tr:hover {
  background-color: var(--vg-surface-3);
}
.keys-table tbody tr:last-child td {
  border-bottom: none;
}
.font-medium {
  font-weight: 500;
  color: var(--vg-text);
}
.font-mono {
  font-family: var(--vg-font-display);
  color: var(--vg-text-dim);
}
.text-muted {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}
.empty-state {
  padding: var(--vg-space-8);
  text-align: center;
  color: var(--vg-text-muted);
}
</style>
