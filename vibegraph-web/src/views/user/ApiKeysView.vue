<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAccountStore } from '@/stores/account'
import AppIcon from '@/components/ui/AppIcon.vue'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import type { ApiKeyCreated } from '@/types/api'
import { accountApi } from '@/lib/api'
import { refreshFeatureAvailability, useFeatureAvailability } from '@/lib/featureAvailability'
import { useSilentRefresh } from '@/composables/useSilentRefresh'
const { t } = useI18n({ useScope: 'global' })
const account = useAccountStore(),
  open = ref(false),
  name = ref(''),
  projectId = ref(''),
  creating = ref(false),
  secret = ref<ApiKeyCreated | null>(null),
  disableId = ref<string | null>(null),
  disabling = ref(false),
  enablingId = ref<string | null>(null),
  deleteId = ref<string | null>(null),
  deleting = ref(false),
  projectsLoaded = ref(account.projectsLoaded),
  projectsLoadError = ref(''),
  message = ref('')
const capability = useFeatureAvailability('api_keys.create.global')
const existingProjectIds = computed(
  () => new Set(account.apiKeys.filter((key) => !key.deletedAt).map((key) => key.project?.id)),
)
const selectedProjectKey = computed(() =>
  account.apiKeys.find((key) => !key.deletedAt && key.project?.id === projectId.value),
)
const selectedProjectReason = computed(() => {
  const key = selectedProjectKey.value
  if (!key) return null
  if (key.locked) return t('user.apiKeys.duplicateLocked')
  return t('user.apiKeys.duplicateExisting')
})
const createDisabled = computed(
  () =>
    !capability.value.enabled ||
    !projectsLoaded.value ||
    Boolean(projectsLoadError.value) ||
    account.projects.length === 0,
)
const reason = computed(() => {
  if (!capability.value.enabled) return capability.value.reason
  if (projectsLoadError.value) return projectsLoadError.value
  if (!projectsLoaded.value) return t('user.apiKeys.checkingRepositories')
  if (!account.projects.length) return t('user.apiKeys.importFirst')
  return null
})
async function loadProjects(): Promise<void> {
  if (account.projectsLoaded) {
    projectsLoaded.value = true
    projectsLoadError.value = ''
    return
  }
  projectsLoaded.value = false
  projectsLoadError.value = ''
  try {
    await account.fetchProjects()
    projectsLoaded.value = true
  } catch (error) {
    projectsLoadError.value = error instanceof Error ? error.message : t('user.apiKeys.loadFallback')
  }
}
async function loadApiKeys(): Promise<void> {
  await account.fetchApiKeys({ force: true })
}
const canSubmit = computed(
  () =>
    name.value.trim().length > 0 &&
    projectId.value.length > 0 &&
    !selectedProjectReason.value &&
    !creating.value &&
    !createDisabled.value,
)
onMounted(() => {
  void Promise.allSettled([loadApiKeys(), loadProjects(), refreshFeatureAvailability()])
})

// Kept alive by UserLayout: keys created/revoked elsewhere appear on
// re-activation without a reload flash.
useSilentRefresh(() =>
  Promise.allSettled([loadApiKeys(), account.fetchProjects({ force: true })]),
)
async function create() {
  if (!canSubmit.value) return
  creating.value = true
  secret.value = null
  message.value = ''
  try {
    secret.value = await account.createApiKey(name.value.trim(), projectId.value)
    open.value = false
    name.value = ''
    projectId.value = ''
  } catch (e) {
    message.value = e instanceof Error ? e.message : t('user.apiKeys.createFallback')
  } finally {
    creating.value = false
  }
}
async function disable() {
  if (!disableId.value || disabling.value) return
  disabling.value = true
  message.value = ''
  try {
    await account.disableApiKey(disableId.value)
    disableId.value = null
    message.value = t('user.apiKeys.disabledMessage')
  } catch (error) {
    message.value = error instanceof Error ? error.message : t('user.apiKeys.disableFallback')
  } finally {
    disabling.value = false
  }
}
async function enable(id: string) {
  if (enablingId.value) return
  enablingId.value = id
  message.value = ''
  try {
    if (typeof account.enableApiKey === 'function') {
      await account.enableApiKey(id)
    } else {
      await accountApi.enableApiKey(id)
      await account.fetchApiKeys()
    }
    message.value = t('user.apiKeys.enabledMessage')
  } catch (error) {
    message.value = error instanceof Error ? error.message : t('user.apiKeys.enableFallback')
  } finally {
    enablingId.value = null
  }
}
async function remove() {
  if (!deleteId.value || deleting.value) return
  deleting.value = true
  message.value = ''
  try {
    await account.deleteApiKey(deleteId.value)
    deleteId.value = null
    message.value = t('user.apiKeys.deletedMessage')
  } catch (error) {
    message.value = error instanceof Error ? error.message : t('user.apiKeys.deleteFallback')
  } finally {
    deleting.value = false
  }
}
async function copy() {
  if (!secret.value) return
  try {
    await navigator.clipboard.writeText(secret.value.secretKey)
    message.value = t('user.apiKeys.secretCopied')
  } catch {
    message.value = t('user.apiKeys.secretCopyFailed')
  }
}
</script>
<template>
  <section class="keys" aria-labelledby="api-keys-title">
    <header>
      <div>
        <span class="eyebrow">{{ t('user.apiKeys.eyebrow') }}</span>
        <h1 id="api-keys-title">{{ t('user.apiKeys.title') }}</h1>
        <p>{{ t('user.apiKeys.description') }}</p>
      </div>
      <button
        data-test="create-api-key"
        class="primary"
        type="button"
        :disabled="createDisabled"
        :aria-describedby="createDisabled ? 'key-disabled' : undefined"
        @click="open = true"
      >
        <AppIcon name="plus" />{{ t('user.apiKeys.create') }}
      </button>
    </header>
    <p v-if="reason" id="key-disabled" class="disabled-note">{{ reason }}</p>
    <section v-if="secret" class="secret" aria-labelledby="secret-title">
      <div>
        <h2 id="secret-title">{{ t('user.apiKeys.secretTitle') }}</h2>
        <p>{{ t('user.apiKeys.secretDescription') }}</p>
      </div>
      <code>{{ secret.secretKey }}</code
      ><button type="button" @click="copy">{{ t('user.apiKeys.secretCopy') }}</button>
    </section>
    <p v-if="message" role="status" class="note">{{ message }}</p>
    <section class="list">
      <h2>{{ t('user.apiKeys.yourKeys') }}</h2>
      <div v-if="!account.apiKeys.length" class="empty">{{ t('user.apiKeys.empty') }}</div>
      <article v-for="key in account.apiKeys" :key="key.id">
        <div class="key-main">
          <strong>{{ key.name }}</strong
          ><code>{{ key.keyPrefix }}</code>
        </div>
        <span>
          {{ key.project ? t('user.apiKeys.repository', { name: key.project.name }) : t('user.apiKeys.noRepository') }}
        </span
        ><span class="key-state" :class="{ off: key.disabled }">
          {{ key.disabled ? t('user.apiKeys.disabled') : t('user.apiKeys.active') }}
          <strong v-if="key.locked" class="locked-badge">{{ t('user.apiKeys.adminLocked') }}</strong>
          <small v-if="key.disabledBy">{{ t('user.apiKeys.disabledBy', { actor: key.disabledBy }) }}</small>
        </span
        ><time>{{ new Date(key.createdAt).toLocaleDateString() }}</time
        ><div class="key-actions">
          <button
            v-if="key.disabled && key.disabledBy !== 'ADMIN' && !key.locked"
            type="button"
            :data-test="`enable-key-${key.id}`"
            :disabled="enablingId === key.id"
            :aria-label="t('user.apiKeys.enableAria', { name: key.name })"
            @click="enable(key.id)"
          >
            {{ enablingId === key.id ? t('user.apiKeys.enabling') : t('user.apiKeys.enable') }}
          </button>
          <button
            v-if="!key.disabled"
            type="button"
            :data-test="`disable-key-${key.id}`"
            :aria-label="t('user.apiKeys.disableAria', { name: key.name })"
            @click="disableId = key.id"
          >
            {{ t('user.apiKeys.disable') }}
          </button>
          <button
            type="button"
            :data-test="`delete-key-${key.id}`"
            :disabled="key.canDelete === false || key.locked"
            :title="key.canDelete === false || key.locked ? t('user.apiKeys.lockedDelete') : undefined"
            :aria-label="t('user.apiKeys.deleteAria', { name: key.name })"
            @click="deleteId = key.id"
          >
            {{ t('user.apiKeys.delete') }}
          </button>
        </div>
      </article>
    </section>
    <div
      v-if="open"
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-key-title"
      @keydown.esc="open = false"
    >
      <form @submit.prevent="create">
        <div class="modal__head">
          <h2 id="create-key-title">{{ t('user.apiKeys.createTitle') }}</h2>
          <button type="button" :aria-label="t('user.apiKeys.closeDialog')" @click="open = false">
            <AppIcon name="close" />
          </button>
        </div>
        <label for="key-name">{{ t('user.apiKeys.keyName') }}</label
        ><input id="key-name" v-model="name" required :placeholder="t('user.apiKeys.keyPlaceholder')" /><label
          for="key-project"
          >{{ t('user.apiKeys.repositoryLabel') }}</label
        ><select id="key-project" v-model="projectId" required>
          <option value="" disabled>{{ t('user.apiKeys.selectRepository') }}</option>
          <option
            v-for="project in account.projects"
            :key="project.id"
            :value="project.id"
          >
            {{ project.name }}{{ existingProjectIds.has(project.id) ? ` - ${t('user.apiKeys.existingKey')}` : '' }}
          </option>
        </select>
        <p v-if="!account.projects.length">{{ t('user.apiKeys.importFirst') }}</p>
        <p v-if="selectedProjectReason" data-test="duplicate-project-reason" class="form-warning">
          {{ selectedProjectReason }}
        </p>
        <button class="primary" type="submit" :disabled="!canSubmit">
          {{ creating ? t('user.apiKeys.creating') : t('user.apiKeys.create') }}
        </button>
      </form>
    </div>
    <AdminConfirmDialog
      :open="Boolean(disableId)"
      :title="t('user.apiKeys.disableTitle')"
      :message="t('user.apiKeys.disableMessage')"
      :confirm-label="t('user.apiKeys.disableConfirm')"
      tone="danger"
      :busy="disabling"
      @cancel="disableId = null"
      @confirm="disable"
    />
    <AdminConfirmDialog
      :open="Boolean(deleteId)"
      :title="t('user.apiKeys.deleteTitle')"
      :message="t('user.apiKeys.deleteMessage')"
      :confirm-label="t('user.apiKeys.deleteConfirm')"
      tone="danger"
      :busy="deleting"
      @cancel="deleteId = null"
      @confirm="remove"
    />
  </section>
</template>
<style scoped>
.keys {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-5);
}
header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--vg-space-4);
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
.eyebrow {
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}
p {
  color: var(--vg-text-muted);
}
button,
input,
select {
  font: inherit;
}
.primary {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.5rem;
  min-height: 38px;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--vg-blue);
  border-radius: 6px;
  background: var(--vg-blue);
  color: white;
  font-size: var(--vg-text-sm);
  font-weight: 600;
  cursor: pointer;
}
.primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.disabled-note,
.note {
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
  color: var(--vg-warning);
}
.secret {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--vg-space-3);
  padding: var(--vg-space-5);
  border: 1px solid rgba(34, 197, 94, 0.35);
  border-radius: var(--vg-radius);
  background: rgba(34, 197, 94, 0.08);
}
.secret code {
  grid-column: 1/-1;
  padding: var(--vg-space-3);
  overflow-wrap: anywhere;
  background: var(--vg-bg);
}
.list {
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
  overflow: hidden;
}
.list > h2,
.empty {
  padding: var(--vg-space-4);
}
.list article {
  display: grid;
  grid-template-columns:
    minmax(12rem, 1.2fr) minmax(12rem, 1.2fr) minmax(8.5rem, 0.6fr)
    minmax(7rem, 0.6fr) 12.5rem;
  align-items: center;
  gap: var(--vg-space-3);
  min-height: 72px;
  padding: var(--vg-space-3) var(--vg-space-4);
  border-top: 1px solid var(--vg-border);
  color: var(--vg-text-muted);
}
.list article > * {
  margin-top: 0;
  line-height: 1.35;
}
.key-main {
  display: grid;
  grid-template-rows: 20px 18px;
  align-items: start;
  gap: 2px;
  margin-top: 0;
}
.key-main strong,
.key-main code {
  line-height: inherit;
}
.list article button {
  align-self: center;
  margin-top: 0;
}
.list strong {
  color: var(--vg-text);
}
.list article button,
.secret button {
  min-height: 38px;
  padding: 0.45rem 0.7rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-text);
  cursor: pointer;
}
.list article button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.key-state,
.key-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.key-actions {
  align-items: center;
  justify-content: flex-end;
  justify-self: end;
  gap: 0.5rem;
  width: 12.5rem;
}
.key-actions button {
  min-width: 86px;
  text-align: center;
}
.key-state {
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  min-height: 44px;
}
.key-state small {
  color: var(--vg-text-muted);
}
.locked-badge {
  padding: 0.15rem 0.4rem;
  border: 1px solid rgba(239, 68, 68, 0.35);
  border-radius: 999px;
  color: var(--vg-danger);
  font-size: var(--vg-text-xs);
}
.form-warning {
  margin: 0;
  color: var(--vg-warning);
}
.off {
  color: var(--vg-danger);
}
.modal {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: var(--vg-space-4);
  background: rgba(0, 0, 0, 0.56);
}
.modal form {
  width: min(32rem, 100%);
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
  padding: var(--vg-space-5);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-lg);
  background: var(--vg-surface);
  box-shadow: var(--vg-shadow);
}
.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.modal__head h2 {
  margin: 0;
}
.modal__head button {
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--vg-text);
  cursor: pointer;
}
.modal label {
  color: var(--vg-text);
  font-weight: 700;
}
.modal input,
.modal select {
  min-height: 44px;
  padding: 0.65rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg);
  color: var(--vg-text);
}
@media (max-width: 760px) {
  header {
    flex-direction: column;
  }
  .list article {
    grid-template-columns: 1fr 1fr;
  }
  .key-main {
    grid-column: 1/-1;
  }
  .key-actions {
    width: auto;
    justify-self: start;
    justify-content: flex-start;
  }
}
</style>
