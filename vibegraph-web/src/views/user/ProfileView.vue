<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAccountStore } from '@/stores/account'

const accountStore = useAccountStore()
const displayNameInput = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const confirmNewPassword = ref('')
const profileMessage = ref('')
const passwordMessage = ref('')
const isSubmitting = ref(false)
const isChangingPassword = ref(false)

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
  profileMessage.value = ''
  try {
    await accountStore.updateDisplayName(displayNameInput.value)
    profileMessage.value = 'Display name updated.'
  } finally {
    isSubmitting.value = false
  }
}

async function submitPasswordChange(): Promise<void> {
  passwordMessage.value = ''
  if (!currentPassword.value || !newPassword.value || !confirmNewPassword.value) {
    passwordMessage.value = 'Enter your current password, new password, and confirmation.'
    return
  }
  if (newPassword.value !== confirmNewPassword.value) {
    passwordMessage.value = 'New password and confirmation do not match.'
    return
  }
  isChangingPassword.value = true
  try {
    await accountStore.changePassword(
      currentPassword.value,
      newPassword.value,
      confirmNewPassword.value,
    )
    currentPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''
    passwordMessage.value = 'Password changed.'
  } catch (e) {
    passwordMessage.value = e instanceof Error ? e.message : 'Password change failed.'
  } finally {
    isChangingPassword.value = false
  }
}
</script>

<template>
  <div class="settings-view">
    <header class="page-header">
      <h2>Settings</h2>
      <p>Manage account identity and password settings.</p>
    </header>

    <div v-if="accountStore.profile" class="settings-grid">
      <section class="settings-card">
        <h3>Account</h3>
        <div class="info-group">
          <label>Email</label>
          <div class="info-value">{{ accountStore.profile.email }}</div>
        </div>

        <div class="info-group">
          <label>Role</label>
          <div class="info-value role-badge">{{ accountStore.profile.role }}</div>
        </div>

        <form class="update-form" @submit.prevent="updateProfile">
          <label class="field" for="displayName">
            <span>Display name</span>
            <div class="input-row">
              <input
                id="displayName"
                v-model="displayNameInput"
                type="text"
                class="form-input"
                :disabled="isSubmitting"
              />
              <button type="submit" class="btn-primary" :disabled="isSubmitting">
                {{ isSubmitting ? 'Updating...' : 'Update' }}
              </button>
            </div>
          </label>
          <p v-if="profileMessage" class="form-note" role="status">{{ profileMessage }}</p>
        </form>
      </section>

      <section class="settings-card">
        <h3>Password</h3>
        <form class="password-form" @submit.prevent="submitPasswordChange">
          <label class="field" for="current-password">
            <span>Current password</span>
            <input
              id="current-password"
              v-model="currentPassword"
              type="password"
              class="form-input"
              autocomplete="current-password"
            />
          </label>
          <label class="field" for="new-password">
            <span>New password</span>
            <input
              id="new-password"
              v-model="newPassword"
              type="password"
              class="form-input"
              autocomplete="new-password"
            />
          </label>
          <label class="field" for="confirm-new-password">
            <span>Confirm new password</span>
            <input
              id="confirm-new-password"
              v-model="confirmNewPassword"
              type="password"
              class="form-input"
              autocomplete="new-password"
            />
          </label>
          <p v-if="passwordMessage" class="form-note" role="status">{{ passwordMessage }}</p>
          <button type="submit" class="btn-primary" :disabled="isChangingPassword">
            {{ isChangingPassword ? 'Changing...' : 'Change password' }}
          </button>
        </form>
      </section>
    </div>
    <div v-else class="loading">Loading settings...</div>
  </div>
</template>

<style scoped>
.settings-view {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-6);
}

.page-header h2 {
  margin: 0 0 var(--vg-space-1);
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}

.page-header p {
  margin: 0;
  color: var(--vg-text-muted);
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-5);
}

.settings-card {
  min-width: 0;
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  box-shadow: var(--vg-shadow-sm);
  padding: var(--vg-space-5);
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}

.settings-card h3 {
  margin: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}

.info-group label,
.field span {
  display: block;
  font-size: var(--vg-text-sm);
  font-weight: 600;
  color: var(--vg-text-muted);
  margin-bottom: var(--vg-space-2);
}

.info-value {
  color: var(--vg-text);
  overflow-wrap: anywhere;
}

.role-badge {
  display: inline-block;
  background: var(--vg-surface-2);
  border: 1px solid var(--vg-border);
  padding: 0.25rem 0.75rem;
  border-radius: var(--vg-radius-sm);
  font-size: var(--vg-text-sm);
  text-transform: capitalize;
  color: var(--vg-blue-bright);
}

.update-form,
.password-form {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}

.input-row {
  display: flex;
  gap: var(--vg-space-3);
}

.form-input {
  width: 100%;
  min-width: 0;
  padding: 0.55rem 0.75rem;
  background: var(--vg-bg-elev);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text);
  font-size: var(--vg-text-base);
  font-family: inherit;
}

.form-input:focus {
  outline: none;
  border-color: var(--vg-blue);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.25);
}

.btn-primary {
  border: none;
  border-radius: var(--vg-radius-sm);
  background: var(--vg-grad-blue);
  color: white;
  padding: 0.55rem 1rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.form-note {
  margin: 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

.loading {
  color: var(--vg-text-dim);
}

@media (max-width: 780px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .input-row {
    flex-direction: column;
  }
}
</style>
