<script setup lang="ts">
import { computed, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAccountStore } from '@/stores/account'
import AppIcon from '@/components/ui/AppIcon.vue'
import RepositorySelect from '@/components/ui/RepositorySelect.vue'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import type { ApiKey } from '@/types/api'
import { accountApi } from '@/lib/api'
import { refreshFeatureAvailability, useFeatureAvailability } from '@/lib/featureAvailability'
import { useSilentRefresh } from '@/composables/useSilentRefresh'
const { locale, t } = useI18n({ useScope: 'global' })
const account = useAccountStore(),
  open = ref(false),
  name = ref(''),
  projectId = ref(''),
  creating = ref(false),
  disableId = ref<string | null>(null),
  disabling = ref(false),
  enablingId = ref<string | null>(null),
  deleteId = ref<string | null>(null),
  deleting = ref(false),
  projectsLoaded = ref(account.projectsLoaded),
  projectsLoadError = ref(''),
  message = ref(''),
  revealingId = ref<string | null>(null),
  revealError = ref<{ keyId: string; message: string } | null>(null),
  copied = ref(false),
  ttlPercent = ref(100),
  secretDialog = ref<{ heading: string; name: string; secretKey: string } | null>(null)
let ttlTimer: ReturnType<typeof setInterval> | undefined
const SECRET_TTL_MS = 10_000
const ttlSeconds = computed(() =>
  Math.max(0, Math.ceil((ttlPercent.value / 100) * (SECRET_TTL_MS / 1000))),
)
const capability = useFeatureAvailability('api_keys.create.global')
const existingProjectIds = computed(
  () =>
    new Set(
      account.apiKeys.flatMap((key) => (!key.deletedAt && key.project ? [key.project.id] : [])),
    ),
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
    projectsLoadError.value =
      error instanceof Error ? error.message : t('user.apiKeys.loadFallback')
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
useSilentRefresh(() => Promise.allSettled([loadApiKeys(), account.fetchProjects({ force: true })]))
async function create() {
  if (!canSubmit.value) return
  creating.value = true
  message.value = ''
  try {
    const created = await account.createApiKey(name.value.trim(), projectId.value)
    open.value = false
    name.value = ''
    projectId.value = ''
    openSecretDialog(t('user.apiKeys.secretHeadingCreated'), created.name, created.secretKey)
  } catch (e) {
    message.value = e instanceof Error ? e.message : t('user.apiKeys.createFallback')
  } finally {
    creating.value = false
  }
}
function cancelCreate(): void {
  if (creating.value) return
  open.value = false
  name.value = ''
  projectId.value = ''
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
async function copySecret() {
  if (!secretDialog.value || copied.value) return
  try {
    await navigator.clipboard.writeText(secretDialog.value.secretKey)
    copied.value = true
    message.value = t('user.apiKeys.secretCopied')
    closeSecretDialog()
  } catch {
    message.value = t('user.apiKeys.secretCopyFailed')
  }
}
async function reveal(key: ApiKey) {
  if (!key.revealable || revealingId.value) return
  revealingId.value = key.id
  revealError.value = null
  message.value = ''
  try {
    const revealed = await accountApi.revealApiKey(key.id)
    if (!revealed.secretKey?.trim()) throw new Error(t('user.apiKeys.revealFallback'))
    openSecretDialog(t('user.apiKeys.secretHeadingReveal'), key.name, revealed.secretKey)
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : t('user.apiKeys.revealFallback')
    revealError.value = { keyId: key.id, message: errorMessage }
    message.value = errorMessage
  } finally {
    revealingId.value = null
  }
}
function openSecretDialog(heading: string, keyName: string, secretKey: string) {
  clearSecretTimers()
  copied.value = false
  ttlPercent.value = 100
  secretDialog.value = { heading, name: keyName, secretKey }
  const startedAt = Date.now()
  ttlTimer = setInterval(() => {
    const remaining = Math.max(0, 1 - (Date.now() - startedAt) / SECRET_TTL_MS)
    ttlPercent.value = remaining * 100
    if (remaining <= 0) closeSecretDialog()
  }, 100)
}
function closeSecretDialog() {
  clearSecretTimers()
  secretDialog.value = null
  copied.value = false
}
function clearSecretTimers() {
  if (ttlTimer) clearInterval(ttlTimer)
  ttlTimer = undefined
}
function formatCreatedAt(value: string | null | undefined): string {
  if (!value) return t('user.apiKeys.dateUnavailable')
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return t('user.apiKeys.dateUnavailable')
  return new Intl.DateTimeFormat(locale.value, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
  }).format(date)
}
onBeforeUnmount(clearSecretTimers)
// KeepAlive: do not leave a sensitive secret visible while the view is hidden.
onDeactivated(closeSecretDialog)
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
    <p v-if="message" role="status" class="note">{{ message }}</p>
    <section class="list" aria-labelledby="your-keys-title">
      <h2 id="your-keys-title">{{ t('user.apiKeys.yourKeys') }}</h2>
      <div v-if="!account.apiKeys.length" class="empty">{{ t('user.apiKeys.empty') }}</div>
      <template v-else>
        <div class="list__head" aria-hidden="true">
          <span>{{ t('user.apiKeys.colName') }}</span>
          <span>{{ t('user.apiKeys.colKey') }}</span>
          <span>{{ t('user.apiKeys.colRepository') }}</span>
          <span class="list__head-status">{{ t('user.apiKeys.colStatus') }}</span>
          <span class="list__head-created">{{ t('user.apiKeys.colCreated') }}</span>
          <span class="list__head-actions">{{ t('user.apiKeys.colActions') }}</span>
        </div>
        <article v-for="key in account.apiKeys" :key="key.id">
          <strong class="cell-name" :title="key.name">{{ key.name }}</strong>
          <code class="cell-key" :data-label="t('user.apiKeys.colKey')">{{ key.keyPrefix }}</code>
          <span
            class="cell-repo"
            :data-label="t('user.apiKeys.colRepository')"
            :title="key.project?.name"
          >
            {{ key.project ? key.project.name : t('user.apiKeys.noRepository') }}
          </span>
          <span class="cell-status">
            <span class="state-pill" :class="{ off: key.disabled }">
              {{ key.disabled ? t('user.apiKeys.disabled') : t('user.apiKeys.active') }}
            </span>
            <strong v-if="key.locked" class="locked-badge">{{
              t('user.apiKeys.adminLocked')
            }}</strong>
            <small v-if="key.disabledBy">{{
              t('user.apiKeys.disabledBy', { actor: key.disabledBy })
            }}</small>
          </span>
          <time
            class="cell-created"
            :data-label="t('user.apiKeys.colCreated')"
            :datetime="key.createdAt || undefined"
          >
            {{ formatCreatedAt(key.createdAt) }}
          </time>
          <div class="key-actions">
            <button
              v-if="key.revealable"
              type="button"
              class="icon-btn"
              :data-test="`reveal-key-${key.id}`"
              :disabled="Boolean(revealingId)"
              :aria-busy="revealingId === key.id"
              :title="t('user.apiKeys.reveal')"
              :aria-label="t('user.apiKeys.revealAria', { name: key.name })"
              @click="reveal(key)"
            >
              <span v-if="revealingId === key.id" class="action-spinner" aria-hidden="true" />
              <AppIcon v-else name="eye" :size="17" />
            </button>
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
              :title="
                key.canDelete === false || key.locked ? t('user.apiKeys.lockedDelete') : undefined
              "
              :aria-label="t('user.apiKeys.deleteAria', { name: key.name })"
              @click="deleteId = key.id"
            >
              {{ t('user.apiKeys.delete') }}
            </button>
            <small v-if="revealError?.keyId === key.id" class="key-action-error" role="alert">
              {{ revealError.message }}
            </small>
          </div>
        </article>
      </template>
    </section>
    <div
      v-if="open"
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-key-title"
      @keydown.esc="cancelCreate"
    >
      <form @submit.prevent="create">
        <div class="modal__head">
          <h2 id="create-key-title">{{ t('user.apiKeys.createTitle') }}</h2>
          <button
            type="button"
            :aria-label="t('user.apiKeys.closeDialog')"
            :disabled="creating"
            @click="cancelCreate"
          >
            <AppIcon name="close" />
          </button>
        </div>
        <div class="modal__field">
          <label for="key-name">{{ t('user.apiKeys.keyName') }}</label>
          <input
            id="key-name"
            v-model="name"
            required
            :placeholder="t('user.apiKeys.keyPlaceholder')"
          />
        </div>
        <div class="modal__field">
          <span id="key-project-label" class="modal__label">
            {{ t('user.apiKeys.repositoryLabel') }}
          </span>
          <RepositorySelect
            id="key-project"
            v-model="projectId"
            :projects="account.projects"
            :existing-project-ids="existingProjectIds"
            :label="t('user.apiKeys.repositoryLabel')"
            :placeholder="t('user.apiKeys.selectRepository')"
            :existing-label="t('user.apiKeys.existingKey')"
          />
        </div>
        <p v-if="!account.projects.length">{{ t('user.apiKeys.importFirst') }}</p>
        <p v-if="selectedProjectReason" data-test="duplicate-project-reason" class="form-warning">
          {{ selectedProjectReason }}
        </p>
        <div class="modal__actions">
          <button type="button" class="modal__cancel" :disabled="creating" @click="cancelCreate">
            {{ t('common.cancel') }}
          </button>
          <button class="primary modal__submit" type="submit" :disabled="!canSubmit">
            {{ creating ? t('user.apiKeys.creating') : t('user.apiKeys.create') }}
          </button>
        </div>
      </form>
    </div>
    <div
      v-if="secretDialog"
      class="modal secret-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="secret-title"
      aria-describedby="secret-hint"
      @keydown.esc="closeSecretDialog"
    >
      <div class="secret-card">
        <div class="secret-card__head">
          <span class="secret-card__badge"><AppIcon name="key" :size="16" /></span>
          <div class="secret-card__titles">
            <h2 id="secret-title">{{ secretDialog.heading }}</h2>
            <p>{{ secretDialog.name }}</p>
          </div>
          <button
            type="button"
            class="ghost-close"
            :aria-label="t('user.apiKeys.closeDialog')"
            @click="closeSecretDialog"
          >
            <AppIcon name="close" :size="14" />
          </button>
        </div>
        <div class="secret-card__row">
          <code>{{ secretDialog.secretKey }}</code>
          <button type="button" class="copy-btn" :class="{ done: copied }" @click="copySecret">
            <AppIcon :name="copied ? 'check' : 'copy'" :size="15" />
            <span>{{ copied ? t('user.apiKeys.copied') : t('user.apiKeys.copy') }}</span>
          </button>
        </div>
        <p id="secret-hint" class="secret-card__hint" role="status" aria-live="polite">
          {{
            copied
              ? t('user.apiKeys.secretCopiedClosing')
              : t('user.apiKeys.secretAutoClose', { seconds: ttlSeconds })
          }}
        </p>
        <div
          class="secret-card__ttl"
          role="progressbar"
          :aria-label="t('user.apiKeys.secretTimerAria')"
          aria-valuemin="0"
          :aria-valuemax="SECRET_TTL_MS / 1000"
          :aria-valuenow="ttlSeconds"
        >
          <span :style="{ width: ttlPercent + '%' }" />
        </div>
      </div>
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
.list {
  --api-key-columns: minmax(9rem, 1fr) minmax(8rem, 0.78fr) minmax(15rem, 1.35fr)
    minmax(7.5rem, 0.68fr) minmax(8rem, 0.68fr) 13rem;

  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
  overflow-x: auto;
}
.list > h2,
.empty {
  padding: var(--vg-space-4);
}
.list__head,
.list article {
  display: grid;
  grid-template-columns: var(--api-key-columns);
  align-items: center;
  gap: var(--vg-space-3);
  min-width: 73rem;
  padding: var(--vg-space-3) var(--vg-space-4);
}
.list__head {
  border-top: 1px solid var(--vg-border);
  border-bottom: 1px solid var(--vg-border);
  background: var(--vg-surface-2, rgba(148, 163, 184, 0.06));
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}
.list__head-status,
.list__head-created,
.list__head-actions {
  text-align: center;
}
.list article {
  min-height: 70px;
  border-top: 1px solid var(--vg-border);
  color: var(--vg-text-muted);
}
.list__head + article {
  border-top: 0;
}
.cell-name,
.cell-key,
.cell-repo {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cell-name {
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
  font-weight: 600;
}
.cell-key {
  font-size: var(--vg-text-xs);
  color: var(--vg-text-muted);
}
.cell-repo {
  font-size: var(--vg-text-sm);
}
.cell-created {
  font-size: var(--vg-text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--vg-text-dim);
  text-align: center;
}
.cell-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
  justify-content: center;
}
.state-pill {
  padding: 0.15rem 0.55rem;
  border: 1px solid rgba(34, 197, 94, 0.35);
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.1);
  color: #86efac;
  font-size: var(--vg-text-xs);
  font-weight: 700;
  white-space: nowrap;
}
.state-pill.off {
  border-color: rgba(239, 68, 68, 0.35);
  background: rgba(239, 68, 68, 0.1);
  color: var(--vg-danger);
}
.cell-status small {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.list article button,
.icon-btn {
  align-self: center;
}
.list strong {
  color: var(--vg-text);
}
.list article button {
  min-height: 44px;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-text);
  font-size: var(--vg-text-xs);
  font-weight: 600;
  cursor: pointer;
}
.list article button:hover:not(:disabled) {
  border-color: rgba(96, 165, 250, 0.45);
  background: var(--vg-surface-3, rgba(148, 163, 184, 0.12));
}
.list article button:focus-visible,
.ghost-close:focus-visible,
.copy-btn:focus-visible {
  outline: 2px solid var(--vg-blue-bright);
  outline-offset: 2px;
}
.list article button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.icon-btn {
  width: 44px;
  height: 44px;
  min-height: 44px;
  padding: 0 !important;
  display: inline-grid;
  place-items: center;
  color: var(--vg-blue-bright);
}
.key-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  justify-self: stretch;
  gap: 0.5rem;
}
.key-action-error {
  flex: 1 0 100%;
  color: var(--vg-danger);
  font-size: var(--vg-text-xs);
  line-height: 1.35;
  text-align: center;
}
.action-spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid color-mix(in srgb, currentColor 28%, transparent);
  border-top-color: currentColor;
  border-radius: var(--vg-radius-pill);
  animation: action-spin 0.7s linear infinite;
}
@keyframes action-spin {
  to {
    transform: rotate(360deg);
  }
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
  background: rgba(2, 6, 23, 0.72);
  backdrop-filter: blur(10px);
}
.modal form {
  width: min(38rem, 100%);
  max-height: calc(100dvh - 2rem);
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
  padding: clamp(1.25rem, 3vw, 1.75rem);
  overflow-y: auto;
  border: 1px solid color-mix(in srgb, var(--vg-blue-bright) 18%, var(--vg-border));
  border-radius: var(--vg-radius-lg);
  background:
    radial-gradient(
      circle at 0 0,
      color-mix(in srgb, var(--vg-blue-bright) 10%, transparent),
      transparent 20rem
    ),
    linear-gradient(160deg, var(--vg-surface), var(--vg-bg-elev));
  box-shadow: var(--vg-shadow-lg);
}
.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-4);
  padding-bottom: var(--vg-space-2);
}
.modal__head h2 {
  margin: 0;
  font-size: clamp(1.35rem, 3vw, 1.65rem);
  line-height: 1.2;
}
.modal__head button {
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--vg-surface-3) 48%, transparent);
  color: var(--vg-text-muted);
  cursor: pointer;
  transition:
    border-color 180ms ease,
    background 180ms ease,
    color 180ms ease;
}
.modal__head button:hover {
  border-color: var(--vg-border-strong);
  background: var(--vg-surface-3);
  color: var(--vg-text);
}
.modal__head button:focus-visible {
  outline: 2px solid var(--vg-blue-bright);
  outline-offset: 2px;
}
.modal__field {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
  min-width: 0;
}
.modal__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--vg-space-3);
  margin-top: var(--vg-space-1);
}
.modal__actions button {
  min-height: 44px;
  width: auto;
  justify-content: center;
  padding: 0.65rem 1rem;
  border-radius: var(--vg-radius-sm);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}
.modal__cancel {
  border: 1px solid var(--vg-border-strong);
  background: transparent;
  color: var(--vg-text-muted);
  cursor: pointer;
}
.modal__cancel:hover:not(:disabled) {
  border-color: var(--vg-blue-bright);
  background: color-mix(in srgb, var(--vg-blue-bright) 8%, transparent);
  color: var(--vg-text);
}
.modal__cancel:focus-visible,
.modal__submit:focus-visible {
  outline: 2px solid var(--vg-blue-bright);
  outline-offset: 2px;
}
.modal__cancel:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.modal__submit {
  min-width: 8.5rem;
}
.modal label,
.modal__label {
  color: var(--vg-text);
  font-weight: 700;
  font-size: var(--vg-text-sm);
}
.modal input {
  min-height: 52px;
  padding: 0.75rem 0.9rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius);
  background: color-mix(in srgb, var(--vg-bg) 88%, transparent);
  color: var(--vg-text);
  font: inherit;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}
.modal input::placeholder {
  color: var(--vg-text-dim);
}
.modal input:focus {
  border-color: var(--vg-blue-bright);
  outline: 0;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--vg-blue-bright) 14%, transparent);
}
/* The secret stays readable at desktop width but still collapses safely on phones. */
.secret-card {
  width: min(44rem, 100%);
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
  padding: var(--vg-space-5);
  border: 1px solid color-mix(in srgb, var(--vg-blue-bright) 22%, var(--vg-border));
  border-radius: var(--vg-radius-lg);
  background:
    radial-gradient(
      circle at 8% 0%,
      color-mix(in srgb, var(--vg-blue-bright) 12%, transparent),
      transparent 18rem
    ),
    var(--vg-surface);
  box-shadow: 0 28px 80px -36px color-mix(in srgb, var(--vg-blue-bright) 48%, transparent);
  overflow: hidden;
}
.secret-card__head {
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
}
.secret-card__badge {
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--vg-green-bright) 38%, transparent);
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--vg-green-bright) 12%, transparent);
  color: var(--vg-green-bright);
}
.secret-card__titles {
  min-width: 0;
  flex: 1;
}
.secret-card__titles h2 {
  margin: 0;
  font-size: clamp(1.25rem, 2vw, 1.5rem);
}
.secret-card__titles p {
  margin: 0.25rem 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-sm);
}
.ghost-close {
  width: 2.75rem;
  height: 2.75rem;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-text-muted);
  cursor: pointer;
}
.ghost-close:hover {
  color: var(--vg-text);
  background: var(--vg-surface-3, rgba(148, 163, 184, 0.12));
}
.secret-card__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--vg-space-3);
  min-height: 4rem;
  padding: 0.625rem 0.75rem 0.625rem var(--vg-space-4);
  border: 1px solid color-mix(in srgb, var(--vg-blue-bright) 20%, var(--vg-border));
  border-radius: var(--vg-radius);
  background: color-mix(in srgb, var(--vg-bg) 88%, transparent);
}
.secret-card__row code {
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  white-space: nowrap;
  font-size: var(--vg-text-base);
  line-height: 1.6;
  color: var(--vg-text);
}
.copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-height: 44px;
  padding: 0.55rem 0.9rem;
  border: 1px solid var(--vg-blue);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-blue);
  color: white;
  font-size: var(--vg-text-sm);
  font-weight: 700;
  cursor: pointer;
}
.copy-btn.done {
  border-color: rgba(34, 197, 94, 0.5);
  background: rgba(34, 197, 94, 0.15);
  color: #86efac;
}
.secret-card__hint {
  margin: 0;
  padding: 0.7rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--vg-blue-bright) 18%, transparent);
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--vg-blue-bright) 7%, transparent);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.5;
}
.secret-card__ttl {
  height: 0.625rem;
  padding: 2px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--vg-blue-bright) 18%, transparent);
  border-radius: var(--vg-radius-pill);
  background: color-mix(in srgb, var(--vg-text-muted) 12%, transparent);
}
.secret-card__ttl span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--vg-blue-bright), var(--vg-green-bright));
  box-shadow: 0 0 14px color-mix(in srgb, var(--vg-blue-bright) 46%, transparent);
  transition: width 100ms linear;
}
@media (prefers-reduced-motion: reduce) {
  .action-spinner {
    animation-duration: 1.5s;
  }
  .secret-card__ttl span {
    transition: none;
  }
}
@media (max-width: 560px) {
  .secret-card {
    padding: var(--vg-space-4);
  }
  .secret-card__row {
    grid-template-columns: 1fr;
    padding: var(--vg-space-3);
  }
  .copy-btn {
    width: 100%;
    justify-content: center;
  }
}
@media (max-width: 760px) {
  header {
    flex-direction: column;
  }
  .list__head {
    display: none;
  }
  .list article {
    grid-template-columns: 1fr auto;
    row-gap: 0.55rem;
    padding-block: var(--vg-space-3);
  }
  .cell-name {
    grid-column: 1;
    grid-row: 1;
  }
  .cell-status {
    grid-column: 2;
    grid-row: 1;
    justify-content: flex-end;
  }
  .cell-key {
    grid-column: 1 / -1;
    grid-row: 2;
  }
  .cell-repo {
    grid-column: 1;
    grid-row: 3;
  }
  .cell-created {
    grid-column: 2;
    grid-row: 3;
    text-align: right;
  }
  .cell-key::before,
  .cell-repo::before,
  .cell-created::before {
    content: attr(data-label) ': ';
    color: var(--vg-text-dim);
    font: 600 var(--vg-text-xs) var(--vg-font-body);
  }
  .key-actions {
    grid-column: 1 / -1;
    grid-row: 4;
    justify-self: start;
    justify-content: flex-start;
  }
}
</style>
