<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAccountStore } from '@/stores/account'

const accountStore = useAccountStore()
const displayNameInput = ref('')
const isSubmitting = ref(false)

onMounted(async () => {
  if (!accountStore.profile) {
    await accountStore.fetchProfile()
  }
  if (accountStore.profile) {
    displayNameInput.value = accountStore.profile.displayName
  }
})

const updateProfile = async () => {
  if (!displayNameInput.value.trim()) return
  
  isSubmitting.value = true
  try {
    await accountStore.updateDisplayName(displayNameInput.value)
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="profile-view">
    <h2>Account Profile</h2>
    
    <div v-if="accountStore.profile" class="profile-card">
      <div class="info-group">
        <label>Email</label>
        <div class="info-value">{{ accountStore.profile.email }}</div>
      </div>
      
      <div class="info-group">
        <label>Role</label>
        <div class="info-value role-badge">{{ accountStore.profile.role }}</div>
      </div>
      
      <form @submit.prevent="updateProfile" class="update-form">
        <div class="info-group">
          <label for="displayName">Display Name</label>
          <div class="input-row">
            <input 
              id="displayName" 
              type="text" 
              v-model="displayNameInput" 
              class="form-input"
              :disabled="isSubmitting"
            />
            <button type="submit" class="btn-primary" :disabled="isSubmitting">
              {{ isSubmitting ? 'Updating...' : 'Update' }}
            </button>
          </div>
        </div>
      </form>
    </div>
    <div v-else class="loading">
      Loading profile...
    </div>
  </div>
</template>

<style scoped>
.profile-view {
  max-width: 600px;
  margin: 0 auto;
}
.profile-card {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  box-shadow: var(--vg-shadow-sm);
  padding: 2rem;
  margin-top: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}
.info-group label {
  display: block;
  font-size: var(--vg-text-sm, 0.875rem);
  font-weight: 600;
  color: var(--vg-text-muted);
  margin-bottom: 0.5rem;
}
.info-value {
  font-size: 1rem;
  color: var(--vg-text);
}
.role-badge {
  display: inline-block;
  background: var(--vg-surface-2);
  border: 1px solid var(--vg-border);
  padding: 0.25rem 0.75rem;
  border-radius: var(--vg-radius-pill);
  font-size: var(--vg-text-sm, 0.875rem);
  text-transform: capitalize;
  color: var(--vg-blue-bright);
}
.input-row {
  display: flex;
  gap: 1rem;
}
.form-input {
  flex: 1;
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text);
  font-size: 1rem;
  font-family: inherit;
  transition: border-color var(--vg-dur-fast) var(--vg-ease-out),
              box-shadow var(--vg-dur-fast) var(--vg-ease-out);
}
.form-input:focus {
  outline: none;
  border-color: var(--vg-blue);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.25);
}
.btn-primary {
  background: var(--vg-grad-blue);
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm);
  font-weight: 500;
  cursor: pointer;
  transition: transform var(--vg-dur-fast) var(--vg-ease-out),
              box-shadow var(--vg-dur-fast) var(--vg-ease-out);
}
.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--vg-glow-blue);
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.loading {
  color: var(--vg-text-dim);
}
</style>

