<script setup lang="ts">
import { ref, watch } from 'vue'
import type { UserProfile } from '@/types/api'
import StatusChip from '@/components/ui/StatusChip.vue'

const props = defineProps<{
  isOpen: boolean
  user: UserProfile | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const usedStorage = 50 // Mocked usage
const storageOverride = ref<number | ''>('')
const error = ref('')

// Reset form when user changes
watch(() => props.user, () => {
  storageOverride.value = 100 // default mock limit
  error.value = ''
})

const handleQuotaUpdate = () => {
  if (typeof storageOverride.value === 'number' && storageOverride.value < usedStorage) {
    error.value = `Cannot set quota lower than currently used (${usedStorage}MB)`
    return
  }
  error.value = ''
  alert('Quota updated successfully')
}

const handleAction = (action: string) => {
  if (confirm(`Are you sure you want to ${action} this user?`)) {
    alert(`User ${action}d successfully.`)
  }
}
</script>

<template>
  <div v-if="isOpen && user" class="drawer-overlay" @click.self="emit('close')">
    <div class="drawer">
      <div class="drawer-header">
        <h3>User Details</h3>
        <button class="close-btn" @click="emit('close')">&times;</button>
      </div>
      
      <div class="drawer-body">
        <div class="user-info-section">
          <h4>{{ user.displayName }}</h4>
          <p class="text-muted">{{ user.email }}</p>
          <div class="tags">
            <StatusChip :status="user.status" :label="user.status" />
            <span class="role-badge">{{ user.role }}</span>
          </div>
        </div>

        <hr />

        <div class="actions-section">
          <h4>Actions</h4>
          <div class="action-buttons">
            <button 
              class="btn-outline-danger" 
              @click="handleAction('block')"
              :disabled="user.status === 'blocked'"
            >
              Block User
            </button>
            <button 
              class="btn-danger" 
              @click="handleAction('deactivate')"
            >
              Deactivate Account
            </button>
          </div>
        </div>

        <hr />

        <div class="quota-section">
          <h4>Storage Quota</h4>
          <p class="text-sm">Currently used: <strong>{{ usedStorage }}MB</strong></p>
          
          <form @submit.prevent="handleQuotaUpdate" class="quota-form">
            <label for="quotaLimit">Override Limit (MB)</label>
            <div class="input-group">
              <input 
                id="quotaLimit"
                type="number" 
                v-model="storageOverride" 
                class="form-input"
                min="0"
                required
              />
              <button type="submit" class="btn-primary">Save</button>
            </div>
            <div v-if="error" class="error-text">{{ error }}</div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}
.drawer {
  width: 400px;
  max-width: 100%;
  background-color: #fff;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 15px rgba(0, 0, 0, 0.1);
  animation: slideIn 0.3s ease-out forwards;
}
@keyframes slideIn {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}
.drawer-header {
  padding: 1.5rem;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.drawer-header h3 {
  margin: 0;
  font-size: 1.25rem;
}
.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #6c757d;
  line-height: 1;
}
.close-btn:hover {
  color: #212529;
}
.drawer-body {
  padding: 1.5rem;
  overflow-y: auto;
  flex: 1;
}
.user-info-section h4 {
  margin: 0 0 0.25rem 0;
  font-size: 1.125rem;
}
.text-muted {
  color: #6c757d;
  margin: 0 0 1rem 0;
}
.tags {
  display: flex;
  gap: 0.5rem;
}
.role-badge {
  background-color: #e9ecef;
  color: #495057;
  padding: 0.25rem 0.75rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}
hr {
  border: 0;
  border-top: 1px solid #e9ecef;
  margin: 1.5rem 0;
}
.actions-section h4, .quota-section h4 {
  margin: 0 0 1rem 0;
}
.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.btn-outline-danger {
  background: transparent;
  color: #dc3545;
  border: 1px solid #dc3545;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  font-weight: 500;
  cursor: pointer;
}
.btn-outline-danger:hover:not(:disabled) {
  background: #dc3545;
  color: white;
}
.btn-outline-danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-danger {
  background: #dc3545;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  font-weight: 500;
  cursor: pointer;
}
.btn-danger:hover {
  background: #bb2d3b;
}
.text-sm {
  font-size: 0.875rem;
  margin: 0 0 1rem 0;
}
.quota-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.quota-form label {
  font-size: 0.875rem;
  font-weight: 500;
}
.input-group {
  display: flex;
  gap: 0.5rem;
}
.form-input {
  flex: 1;
  padding: 0.5rem 0.75rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-family: inherit;
}
.btn-primary {
  background: #0d6efd;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  font-weight: 500;
  cursor: pointer;
}
.btn-primary:hover {
  background: #0b5ed7;
}
.error-text {
  color: #dc3545;
  font-size: 0.875rem;
  margin-top: 0.25rem;
}
</style>
