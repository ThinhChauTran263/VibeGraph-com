<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAccountStore } from '@/stores/account'
const { t } = useI18n({ useScope: 'global' })
const account = useAccountStore(),
  displayName = ref(''),
  oldPassword = ref(''),
  newPassword = ref(''),
  confirmPassword = ref(''),
  message = ref(''),
  busy = ref(false)
onMounted(async () => {
  try {
    await account.fetchProfile()
    displayName.value = account.profile?.displayName ?? ''
  } catch (e) {
    message.value =
      e instanceof Error ? e.message : t('admin.settings.messages.profileLoadFailed')
  }
})
async function saveProfile() {
  busy.value = true
  try {
    await account.updateDisplayName(displayName.value.trim())
    message.value = t('admin.settings.messages.profileUpdated')
  } catch (e) {
    message.value =
      e instanceof Error ? e.message : t('admin.settings.messages.profileUpdateFailed')
  } finally {
    busy.value = false
  }
}
async function changePassword() {
  if (newPassword.value !== confirmPassword.value) {
    message.value = t('admin.settings.messages.passwordMismatch')
    return
  }
  busy.value = true
  try {
    await account.changePassword(oldPassword.value, newPassword.value, confirmPassword.value)
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    message.value = t('admin.settings.messages.passwordChanged')
  } catch (e) {
    message.value =
      e instanceof Error ? e.message : t('admin.settings.messages.passwordChangeFailed')
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="settings">
    <header>
      <span>{{ t('admin.settings.eyebrow') }}</span>
      <h1>{{ t('admin.settings.title') }}</h1>
      <p>{{ t('admin.settings.description') }}</p>
    </header>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <div class="grid">
      <section>
        <h2>{{ t('admin.settings.profile.title') }}</h2>
        <form @submit.prevent="saveProfile">
          <label for="admin-email">{{ t('admin.settings.profile.email') }}</label
          ><input id="admin-email" :value="account.profile?.email ?? ''" disabled /><label
            for="admin-name"
            >{{ t('admin.settings.profile.displayName') }}</label
          ><input id="admin-name" v-model="displayName" required /><button
            type="submit"
            :disabled="busy || !displayName.trim()"
          >
            {{ t('admin.settings.profile.save') }}
          </button>
        </form>
      </section>
      <section>
        <h2>{{ t('admin.settings.password.title') }}</h2>
        <form @submit.prevent="changePassword">
          <label for="admin-old-password">{{ t('admin.settings.password.current') }}</label
          ><input
            id="admin-old-password"
            v-model="oldPassword"
            type="password"
            autocomplete="current-password"
            required
          /><label for="admin-new-password">{{ t('admin.settings.password.new') }}</label
          ><input
            id="admin-new-password"
            v-model="newPassword"
            type="password"
            autocomplete="new-password"
            required
          /><label for="admin-confirm-password">{{
            t('admin.settings.password.confirm')
          }}</label
          ><input
            id="admin-confirm-password"
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            required
          /><button type="submit" :disabled="busy">
            {{ t('admin.settings.password.submit') }}
          </button>
        </form>
      </section>
    </div>
  </section>
</template>
<style scoped>
.settings {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-5);
}
header span {
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}
h1,
h2 {
  font-family: var(--vg-font-display);
  color: var(--vg-text);
}
h1 {
  margin: 0.25rem 0;
  font-size: clamp(1.625rem, 2.2vw, 1.875rem);
}
h2 {
  font-size: var(--vg-text-lg);
}
p {
  color: var(--vg-text-muted);
}
.notice {
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-4);
}
section {
  padding: var(--vg-space-4);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
}
form {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
}
label {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}
input,
select {
  min-height: 44px;
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg);
  color: var(--vg-text);
  font: inherit;
}
button {
  align-self: flex-start;
  min-height: 38px;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--vg-blue);
  border-radius: 6px;
  background: var(--vg-blue);
  color: white;
  font: 600 var(--vg-text-sm) var(--vg-font-body);
  cursor: pointer;
}
button:disabled,
input:disabled,
select:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.retention {
  grid-column: 1/-1;
  opacity: 0.7;
}
.retention > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-3);
}
.retention span {
  color: var(--vg-warning);
  font-size: var(--vg-text-xs);
  font-weight: 800;
}
@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .retention {
    grid-column: auto;
  }
}
</style>
