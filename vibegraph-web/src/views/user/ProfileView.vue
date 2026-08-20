<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAccountStore } from '@/stores/account'
import { useSilentRefresh } from '@/composables/useSilentRefresh'
import ErrorAlert from '@/components/ui/ErrorAlert.vue'

const accountStore = useAccountStore()
const { t } = useI18n({ useScope: 'global' })
const displayNameInput = ref('')
const isProfileLoading = ref(!accountStore.profile)
const profileLoadError = ref('')

function syncDisplayName(): void {
  displayNameInput.value = accountStore.profile?.displayName ?? ''
}

async function loadProfile(): Promise<void> {
  if (accountStore.profile) {
    isProfileLoading.value = false
    profileLoadError.value = ''
    syncDisplayName()
    return
  }

  isProfileLoading.value = true
  profileLoadError.value = ''
  try {
    await accountStore.fetchProfile()
    syncDisplayName()
  } catch (error) {
    if (!accountStore.profile) {
      profileLoadError.value =
        error instanceof Error ? error.message : t('user.profile.loadFallback')
    }
  } finally {
    isProfileLoading.value = false
  }
}
const currentPassword = ref('')
const newPassword = ref('')
const confirmNewPassword = ref('')
const profileMessage = ref('')
const passwordMessage = ref('')
const isSubmitting = ref(false)
const isChangingPassword = ref(false)
const profileMessageIsError = ref(false)
const passwordMessageIsError = ref(false)
const MIN_PASSWORD_LENGTH = 8
const roleLabel = computed(() => {
  const role = accountStore.profile?.role?.trim().toUpperCase()
  if (!role) return t('user.profile.roleNames.unknown')
  const roleKey = `user.profile.roleNames.${role.toLowerCase()}`
  return t(roleKey)
})

onMounted(() => {
  void loadProfile()
})

// Kept alive by UserLayout: profile edits made elsewhere show on re-activation.
useSilentRefresh(() => accountStore.fetchProfile().catch(() => undefined))

const updateProfile = async () => {
  const displayName = displayNameInput.value.trim()
  profileMessage.value = ''
  profileMessageIsError.value = false
  if (!displayName) {
    profileMessage.value = t('user.profile.displayNameRequired')
    profileMessageIsError.value = true
    return
  }

  isSubmitting.value = true
  try {
    await accountStore.updateDisplayName(displayName)
    profileMessage.value = t('user.profile.displayNameUpdated')
  } catch (error) {
    profileMessage.value =
      error instanceof Error ? error.message : t('user.profile.profileFallback')
    profileMessageIsError.value = true
  } finally {
    isSubmitting.value = false
  }
}

async function submitPasswordChange(): Promise<void> {
  passwordMessage.value = ''
  passwordMessageIsError.value = false
  if (!currentPassword.value || !newPassword.value || !confirmNewPassword.value) {
    passwordMessage.value = t('user.profile.passwordFieldsRequired')
    passwordMessageIsError.value = true
    return
  }
  if (newPassword.value.length < MIN_PASSWORD_LENGTH) {
    passwordMessage.value = t('user.profile.passwordMinimum', { count: MIN_PASSWORD_LENGTH })
    passwordMessageIsError.value = true
    return
  }
  if (newPassword.value !== confirmNewPassword.value) {
    passwordMessage.value = t('user.profile.passwordMismatch')
    passwordMessageIsError.value = true
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
    passwordMessage.value = t('user.profile.passwordChanged')
  } catch (e) {
    passwordMessage.value = e instanceof Error ? e.message : t('user.profile.passwordFallback')
    passwordMessageIsError.value = true
  } finally {
    isChangingPassword.value = false
  }
}
</script>

<template>
  <div class="settings-view">
    <header class="page-header">
      <h1>{{ t('user.profile.title') }}</h1>
      <p>{{ t('user.profile.description') }}</p>
    </header>

    <ErrorAlert
      v-if="profileLoadError"
      role="alert"
      :title="t('user.profile.unavailable')"
      :message="profileLoadError"
    >
      <button
        data-test="retry-profile"
        type="button"
        class="retry-button"
        :disabled="isProfileLoading"
        @click="loadProfile"
      >
        {{ t('user.profile.retry') }}
      </button>
    </ErrorAlert>
    <div v-if="isProfileLoading" class="loading">{{ t('user.profile.loading') }}</div>
    <div v-else-if="accountStore.profile" class="settings-grid">
      <section class="settings-card">
        <h3>{{ t('user.profile.account') }}</h3>
        <div class="info-group">
          <span class="info-label">{{ t('user.profile.email') }}</span>
          <div class="info-value">{{ accountStore.profile.email }}</div>
        </div>

        <div class="info-group">
          <span class="info-label">{{ t('user.profile.role') }}</span>
          <div class="info-value role-badge">{{ roleLabel }}</div>
        </div>

        <form class="update-form" @submit.prevent="updateProfile">
          <div class="field">
            <label for="displayName">{{ t('user.profile.displayName') }}</label>
            <div class="input-row">
              <input
                id="displayName"
                v-model="displayNameInput"
                type="text"
                class="form-input"
                required
                :disabled="isSubmitting"
                :aria-invalid="profileMessageIsError"
                :aria-describedby="profileMessage ? 'profile-message' : undefined"
              />
              <button type="submit" class="btn-primary" :disabled="isSubmitting">
                {{ isSubmitting ? t('user.profile.updating') : t('user.profile.update') }}
              </button>
            </div>
          </div>
          <p
            v-if="profileMessage"
            id="profile-message"
            data-test="profile-message"
            class="form-note"
            :role="profileMessageIsError ? 'alert' : 'status'"
          >
            {{ profileMessage }}
          </p>
        </form>
      </section>

      <section class="settings-card">
        <h3>{{ t('user.profile.password') }}</h3>
        <form class="password-form" @submit.prevent="submitPasswordChange">
          <label class="field" for="current-password">
            <span>{{ t('user.profile.currentPassword') }}</span>
            <input
              id="current-password"
              v-model="currentPassword"
              type="password"
              class="form-input"
              autocomplete="current-password"
              required
              :disabled="isChangingPassword"
              :aria-invalid="passwordMessageIsError"
              :aria-describedby="passwordMessage ? 'password-message' : undefined"
            />
          </label>
          <label class="field" for="new-password">
            <span>{{ t('user.profile.newPassword') }}</span>
            <input
              id="new-password"
              v-model="newPassword"
              type="password"
              class="form-input"
              autocomplete="new-password"
              minlength="8"
              required
              :disabled="isChangingPassword"
              :aria-invalid="passwordMessageIsError"
              :aria-describedby="passwordMessage ? 'password-message' : undefined"
            />
          </label>
          <label class="field" for="confirm-new-password">
            <span>{{ t('user.profile.confirmPassword') }}</span>
            <input
              id="confirm-new-password"
              v-model="confirmNewPassword"
              type="password"
              class="form-input"
              autocomplete="new-password"
              minlength="8"
              required
              :disabled="isChangingPassword"
              :aria-invalid="passwordMessageIsError"
              :aria-describedby="passwordMessage ? 'password-message' : undefined"
            />
          </label>
          <p
            v-if="passwordMessage"
            id="password-message"
            data-test="password-message"
            class="form-note"
            :role="passwordMessageIsError ? 'alert' : 'status'"
          >
            {{ passwordMessage }}
          </p>
          <button type="submit" class="btn-primary" :disabled="isChangingPassword">
            {{ isChangingPassword ? t('user.profile.changing') : t('user.profile.changePassword') }}
          </button>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.settings-view {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-6);
}

.page-header h1 {
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

.info-label,
.field span,
.field > label {
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
  text-transform: none;
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

.retry-button {
  min-height: 38px;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--vg-danger);
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-danger);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
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
