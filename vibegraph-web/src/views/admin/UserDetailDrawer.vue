<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AdminUserResponse, ApiKey, ApiKeyCreated } from '@/types/api'
import { useAdminStore } from '@/stores/admin'
import StatusChip from '@/components/ui/StatusChip.vue'

const props = defineProps<{
  isOpen: boolean
  user: AdminUserResponse | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated'): void
}>()

const adminStore = useAdminStore()

// ── Quota ────────────────────────────────────────────────────────────────────
const storageOverrideMb = ref<number | ''>('')
const quotaError = ref('')
const isSavingQuota = ref(false)

// ── Actions ──────────────────────────────────────────────────────────────────
const actionError = ref('')
const isActioning = ref(false)

// ── Plan ─────────────────────────────────────────────────────────────────────
const selectedPlan = ref('FREE')
const isSavingPlan = ref(false)

// ── API Key creation toggle ───────────────────────────────────────────────────
const isTogglingApiKeyCreation = ref(false)

// ── User's API keys ───────────────────────────────────────────────────────────
const userApiKeys = ref<ApiKey[]>([])
const newKeyName = ref('')
const isCreatingKey = ref(false)
const createdKeySecret = ref<ApiKeyCreated | null>(null)

// Reset form when user changes
watch(
  () => props.user,
  async (u) => {
    if (!u) return
    quotaError.value = ''
    actionError.value = ''
    createdKeySecret.value = null
    selectedPlan.value = u.planCode

    // Quota override in MB (backend stores bytes; convert for display)
    const overrideBytes = u.storageQuotaOverrideBytes
    storageOverrideMb.value =
      overrideBytes != null ? Math.round(overrideBytes / (1024 * 1024)) : ''

    // Load user API keys
    try {
      userApiKeys.value = await adminStore.listApiKeysForUser(u.id)
    } catch {
      userApiKeys.value = []
    }
    requestAnimationFrame(() => {
      document.querySelector('.drawer-body')?.scrollTo({ top: 0 })
    })
  },
  { immediate: false },
)

// ── Helpers ───────────────────────────────────────────────────────────────────

function usedMb(u: AdminUserResponse): number {
  return Math.round(u.usedBytes / (1024 * 1024))
}

function quotaMb(u: AdminUserResponse): number {
  return Math.round(u.quotaBytes / (1024 * 1024))
}

function storagePercent(u: AdminUserResponse): number {
  if (!u.quotaBytes) return 0
  return Math.min(100, Math.round((u.usedBytes / u.quotaBytes) * 100))
}

function userInitials(u: AdminUserResponse): string {
  const source = u.displayName || u.email
  return source
    .split(/[\s@._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('') || 'US'
}

function userStatus(u: AdminUserResponse): string {
  if (u.blocked) return 'blocked'
  if (u.deactivated) return 'deactivated'
  return 'active'
}

// ── Quota ─────────────────────────────────────────────────────────────────────

const handleQuotaUpdate = async () => {
  if (!props.user) return
  const overrideMb = storageOverrideMb.value === '' ? null : Number(storageOverrideMb.value)
  const usedMbVal = usedMb(props.user)
  if (overrideMb !== null && overrideMb < usedMbVal) {
    quotaError.value = `Cannot set quota lower than currently used (${usedMbVal} MB)`
    return
  }
  quotaError.value = ''
  isSavingQuota.value = true
  try {
    await adminStore.updateQuota(props.user.id, overrideMb, null)
    emit('updated')
  } catch (e: unknown) {
    quotaError.value = e instanceof Error ? e.message : 'Failed to update quota'
  } finally {
    isSavingQuota.value = false
  }
}

// ── Plan ──────────────────────────────────────────────────────────────────────

const handlePlanUpdate = async () => {
  if (!props.user) return
  isSavingPlan.value = true
  actionError.value = ''
  try {
    await adminStore.updatePlan(props.user.id, selectedPlan.value)
    emit('updated')
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : 'Failed to update plan'
  } finally {
    isSavingPlan.value = false
  }
}

// ── Block / Deactivate / Unblock ──────────────────────────────────────────────

const handleBlock = async () => {
  if (!props.user) return
  const safeReason = prompt('Public reason shown to the user (max 240 chars):')
  if (!safeReason) return
  const reason = prompt('Internal reason (admin-only, max 500 chars):') ?? safeReason
  isActioning.value = true
  actionError.value = ''
  try {
    await adminStore.blockUser(props.user.id, reason, safeReason)
    emit('updated')
    emit('close')
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : 'Failed to block user'
  } finally {
    isActioning.value = false
  }
}

const handleUnblock = async () => {
  if (!props.user || !confirm('Unblock this user?')) return
  isActioning.value = true
  actionError.value = ''
  try {
    await adminStore.unblockUser(props.user.id)
    emit('updated')
    emit('close')
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : 'Failed to unblock user'
  } finally {
    isActioning.value = false
  }
}

const handleDeactivate = async () => {
  if (!props.user) return
  const safeReason = prompt('Public reason shown to the user (max 240 chars):')
  if (!safeReason) return
  const reason = prompt('Internal reason (admin-only, max 500 chars):') ?? safeReason
  if (!confirm('Deactivate this account? This cannot be undone easily.')) return
  isActioning.value = true
  actionError.value = ''
  try {
    await adminStore.deactivateUser(props.user.id, reason, safeReason)
    emit('updated')
    emit('close')
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : 'Failed to deactivate user'
  } finally {
    isActioning.value = false
  }
}

// ── API Key creation toggle ───────────────────────────────────────────────────

const handleToggleApiKeyCreation = async () => {
  if (!props.user) return
  const newVal = !props.user.apiKeyCreationDisabled
  isTogglingApiKeyCreation.value = true
  actionError.value = ''
  try {
    await adminStore.updateApiKeyCreation(props.user.id, newVal)
    emit('updated')
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : 'Failed to toggle API key creation'
  } finally {
    isTogglingApiKeyCreation.value = false
  }
}

// ── Admin create / disable API key ────────────────────────────────────────────

const handleCreateApiKey = async () => {
  if (!props.user || !newKeyName.value.trim()) return
  isCreatingKey.value = true
  actionError.value = ''
  try {
    const created = await adminStore.createApiKeyForUser(props.user.id, newKeyName.value)
    createdKeySecret.value = created
    newKeyName.value = ''
    userApiKeys.value = await adminStore.listApiKeysForUser(props.user.id)
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : 'Failed to create API key'
  } finally {
    isCreatingKey.value = false
  }
}

const handleDisableApiKey = async (keyId: string) => {
  if (!confirm('Disable this API key?')) return
  try {
    await adminStore.disableApiKey(keyId)
    userApiKeys.value = await adminStore.listApiKeysForUser(props.user!.id)
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : 'Failed to disable API key'
  }
}

const copySecret = (secret: string) => {
  navigator.clipboard.writeText(secret)
}
</script>

<template>
  <div v-if="isOpen && user" class="drawer-overlay" @click.self="emit('close')">
    <div class="drawer">
      <div class="drawer-header">
        <div>
          <span class="header-kicker">Admin user detail</span>
          <h3>{{ user.displayName }}</h3>
          <p>{{ user.email }}</p>
        </div>
        <button class="close-btn" type="button" aria-label="Close user detail" @click="emit('close')">
          Close
        </button>
      </div>

      <div class="drawer-body">
        <!-- User Info -->
        <div class="section user-summary">
          <span class="user-avatar" aria-hidden="true">{{ userInitials(user) }}</span>
          <div class="summary-copy">
            <h4>{{ user.displayName }}</h4>
            <p class="text-muted">{{ user.email }}</p>
            <div class="tags">
              <StatusChip :status="userStatus(user)" :label="userStatus(user)" />
              <span class="role-badge">{{ user.role }}</span>
              <span class="plan-badge">{{ user.planCode }}</span>
            </div>
          </div>
          <div class="summary-metrics">
            <div>
              <span>Used</span>
              <strong>{{ usedMb(user) }} MB</strong>
            </div>
            <div>
              <span>Quota</span>
              <strong>{{ quotaMb(user) }} MB</strong>
            </div>
            <div>
              <span>Storage</span>
              <strong>{{ storagePercent(user) }}%</strong>
            </div>
          </div>
          <div v-if="user.blockedReasonSafe" class="reason-note">
            Blocked: {{ user.blockedReasonSafe }}
          </div>
          <div v-if="user.deactivationReasonSafe" class="reason-note">
            Deactivated: {{ user.deactivationReasonSafe }}
          </div>
        </div>

        <hr />

        <!-- Actions -->
        <div class="section action-section">
          <h4>Actions</h4>
          <div v-if="actionError" class="error-text">{{ actionError }}</div>
          <div class="action-buttons">
            <button
              v-if="!user.blocked"
              class="btn-outline-danger"
              @click="handleBlock"
              :disabled="isActioning"
            >
              Block User
            </button>
            <button
              v-else
              class="btn-outline-secondary"
              @click="handleUnblock"
              :disabled="isActioning"
            >
              Unblock User
            </button>
            <button
              class="btn-danger"
              @click="handleDeactivate"
              :disabled="isActioning || user.deactivated"
            >
              Deactivate Account
            </button>
          </div>
        </div>

        <hr />

        <!-- Plan -->
        <div class="section plan-section">
          <h4>Plan</h4>
          <div class="input-group">
            <select id="adminUserPlan" name="planCode" v-model="selectedPlan" class="form-input">
              <option value="FREE">Free</option>
              <option value="PRO">Pro</option>
              <option value="PRO_PLUS">Pro+</option>
              <option value="MAX">Max</option>
              <option value="ENTERPRISE">Enterprise</option>
            </select>
            <button class="btn-primary" @click="handlePlanUpdate" :disabled="isSavingPlan">
              {{ isSavingPlan ? 'Saving...' : 'Update Plan' }}
            </button>
          </div>
        </div>

        <hr />

        <!-- Storage Quota -->
        <div class="section storage-section">
          <h4>Storage Quota</h4>
          <p class="quota-summary">
            <span><strong>{{ usedMb(user) }} MB</strong> used</span>
            <span><strong>{{ quotaMb(user) }} MB</strong> quota</span>
          </p>
          <div class="quota-meter" aria-label="Storage quota usage">
            <div :style="{ width: `${storagePercent(user)}%` }"></div>
          </div>

          <form @submit.prevent="handleQuotaUpdate" class="quota-form">
            <label for="quotaLimit">Override Limit (MB) — leave blank to use plan default</label>
            <div class="input-group">
              <input
                id="quotaLimit"
                name="quotaLimit"
                type="number"
                v-model="storageOverrideMb"
                class="form-input"
                min="0"
                placeholder="Plan default"
              />
              <button type="submit" class="btn-primary" :disabled="isSavingQuota">
                {{ isSavingQuota ? 'Saving...' : 'Save' }}
              </button>
            </div>
            <div v-if="quotaError" class="error-text">{{ quotaError }}</div>
          </form>
        </div>

        <hr />

        <!-- API Key Creation Toggle -->
        <div class="section api-toggle-section">
          <h4>API Key Creation</h4>
          <div class="toggle-row">
            <span class="text-sm">
              {{ user.apiKeyCreationDisabled ? 'API key creation is disabled for this user' : 'API key creation is enabled' }}
            </span>
            <button
              class="btn-outline-secondary btn-sm"
              @click="handleToggleApiKeyCreation"
              :disabled="isTogglingApiKeyCreation"
            >
              {{ user.apiKeyCreationDisabled ? 'Enable' : 'Disable' }}
            </button>
          </div>
        </div>

        <hr />

        <!-- User's API Keys -->
        <div class="section api-keys-section">
          <h4>API Keys</h4>

          <!-- Create key for user -->
          <div class="input-group key-create-form">
            <input
              id="adminApiKeyName"
              name="apiKeyName"
              v-model="newKeyName"
              type="text"
              class="form-input"
              placeholder="New key name…"
              maxlength="120"
            />
            <button class="btn-primary btn-sm" @click="handleCreateApiKey" :disabled="isCreatingKey || !newKeyName">
              {{ isCreatingKey ? '...' : 'Create' }}
            </button>
          </div>

          <!-- Secret revealed after creation -->
          <div v-if="createdKeySecret" class="secret-alert">
            <div class="secret-alert-title">⚠ Save this secret — shown only once!</div>
            <div class="secret-box">
              <code>{{ createdKeySecret.secretKey }}</code>
              <button class="btn-copy" @click="copySecret(createdKeySecret.secretKey)">Copy</button>
            </div>
          </div>

          <!-- Existing keys list -->
          <div v-if="userApiKeys.length === 0" class="empty-state">No API keys.</div>
          <div v-else class="table-shell">
            <table class="keys-table">
              <thead>
                <tr><th>Name</th><th>Prefix</th><th>Status</th><th></th></tr>
              </thead>
              <tbody>
                <tr v-for="k in userApiKeys" :key="k.id">
                  <td>{{ k.name }}</td>
                  <td class="mono">{{ k.keyPrefix }}</td>
                  <td>
                    <StatusChip
                      :status="k.disabled ? 'disabled' : 'active'"
                      :label="k.disabled ? 'Disabled' : 'Active'"
                    />
                  </td>
                  <td>
                    <button
                      v-if="!k.disabled"
                      class="btn-danger btn-sm"
                      @click="handleDisableApiKey(k.id)"
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
    </div>
  </div>
</template>

<style scoped>
.drawer-overlay {
  position: absolute; top: 0; left: 0; right: 0; bottom: 0;
  background-color: rgba(0, 0, 0, 0.55);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
  border-radius: inherit;
  overflow: hidden;
}
.drawer {
  width: 480px;
  max-width: 100%;
  background-color: var(--vg-surface);
  height: 100%;
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.3);
  animation: slideIn 0.25s ease-out forwards;
}
@keyframes slideIn {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}
.drawer-header {
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--vg-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--vg-surface-2);
}
.drawer-header h3 {
  margin: 0;
  font-size: 1.125rem;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}
.close-btn {
  background: none; border: none; font-size: 1.5rem;
  cursor: pointer; color: var(--vg-text-muted); line-height: 1;
}
.close-btn:hover { color: var(--vg-text); }
.drawer-body {
  padding: 1.25rem 1.5rem;
  overflow-y: auto;
  flex: 1;
}
.section { margin-bottom: 0.25rem; }
.section h4 { margin: 0 0 0.75rem 0; font-size: 0.9375rem; color: var(--vg-text); }
.text-muted { color: var(--vg-text-muted); margin: 0 0 0.75rem 0; font-size: var(--vg-text-sm); }
.text-sm { font-size: var(--vg-text-sm); margin: 0 0 0.75rem 0; color: var(--vg-text); }
.tags { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 0.5rem; }
.role-badge, .plan-badge {
  background-color: var(--vg-surface-3);
  color: var(--vg-text-muted);
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  border: 1px solid var(--vg-border);
}
.reason-note { font-size: var(--vg-text-xs); color: var(--vg-danger); margin-top: 0.5rem; }
hr { border: 0; border-top: 1px solid var(--vg-border); margin: 1rem 0; }
.action-buttons { display: flex; flex-direction: column; gap: 0.625rem; }
.btn-outline-danger {
  background: transparent; color: var(--vg-danger);
  border: 1px solid var(--vg-danger); padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm); font-weight: 500; cursor: pointer;
}
.btn-outline-danger:hover:not(:disabled) { background: rgba(239,68,68,0.1); }
.btn-outline-danger:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-outline-secondary {
  background: transparent; color: var(--vg-text-muted);
  border: 1px solid var(--vg-border); padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm); font-weight: 500; cursor: pointer;
}
.btn-outline-secondary:hover:not(:disabled) { background: var(--vg-surface-3); }
.btn-outline-secondary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-danger {
  background: rgba(239,68,68,0.15); color: var(--vg-danger);
  border: 1px solid rgba(239,68,68,0.3); padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm); font-weight: 500; cursor: pointer;
}
.btn-danger:hover:not(:disabled) { background: rgba(239,68,68,0.25); }
.btn-danger:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary {
  background: var(--vg-grad-blue); color: white; border: none;
  padding: 0.5rem 1rem; border-radius: var(--vg-radius-sm);
  font-weight: 500; cursor: pointer;
}
.btn-primary:disabled { opacity: 0.65; cursor: not-allowed; }
.btn-sm { padding: 0.25rem 0.625rem; font-size: var(--vg-text-xs); }

.quota-form { display: flex; flex-direction: column; gap: 0.5rem; }
.quota-form label { font-size: var(--vg-text-sm); font-weight: 500; color: var(--vg-text-muted); }
.input-group { display: flex; gap: 0.5rem; }
.form-input {
  flex: 1;
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev); color: var(--vg-text);
  border: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm);
  font-family: inherit; font-size: var(--vg-text-base);
}
.form-input:focus { outline: none; border-color: var(--vg-blue); }
.error-text { color: var(--vg-danger); font-size: var(--vg-text-sm); margin-top: 0.25rem; }

.toggle-row { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }

/* API Keys table */
.keys-table { width: 100%; border-collapse: collapse; font-size: var(--vg-text-sm); margin-top: 0.5rem; }
.keys-table th, .keys-table td { padding: 0.5rem 0.5rem; text-align: left; border-bottom: 1px solid var(--vg-border); color: var(--vg-text); }
.keys-table th { color: var(--vg-text-muted); font-weight: 600; background: var(--vg-surface-2); }
.mono { font-family: monospace; color: var(--vg-text-dim); font-size: var(--vg-text-xs); }
.empty-state { text-align: center; padding: 1rem; color: var(--vg-text-muted); font-size: var(--vg-text-sm); }

/* Secret alert */
.secret-alert { background: rgba(34,197,94,0.12); border: 1px solid rgba(34,197,94,0.3); border-radius: var(--vg-radius-sm); padding: 0.75rem; margin: 0.75rem 0; }
.secret-alert-title { color: var(--vg-green-bright); font-weight: 600; font-size: var(--vg-text-sm); margin-bottom: 0.5rem; }
.secret-box { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; background: var(--vg-bg-elev); border: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm); padding: 0.5rem; }
.secret-box code { font-family: monospace; font-size: var(--vg-text-sm); color: var(--vg-text); word-break: break-all; }
.btn-copy { background: var(--vg-surface-3); border: 1px solid var(--vg-border); color: var(--vg-text); padding: 0.2rem 0.5rem; border-radius: var(--vg-radius-sm); cursor: pointer; font-size: var(--vg-text-xs); white-space: nowrap; }

@media (max-width: 480px) {
  .drawer { width: 100%; }
  .input-group { flex-direction: column; }
  .action-buttons { flex-direction: column; }
}

/* Professional admin drawer polish */
.drawer-overlay {
  background:
    linear-gradient(90deg, rgba(2, 6, 23, 0.72), rgba(2, 6, 23, 0.36)),
    rgba(2, 6, 23, 0.58);
  backdrop-filter: blur(3px);
}

.drawer {
  width: min(42rem, calc(100% - 2rem));
  background:
    linear-gradient(180deg, rgba(20, 30, 52, 0.98), rgba(11, 17, 32, 0.99)),
    var(--vg-surface);
  border-left: 1px solid rgba(148, 163, 184, 0.22);
  box-shadow: -1.5rem 0 3rem rgba(2, 6, 23, 0.48);
}

.drawer-header {
  position: sticky;
  top: 0;
  z-index: 2;
  min-height: 5rem;
  padding: var(--vg-space-5) var(--vg-space-6);
  background: rgba(15, 23, 42, 0.94);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.drawer-header::before {
  content: 'Admin control';
  display: block;
  color: var(--vg-blue-bright);
  font-family: var(--vg-font-body);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.drawer-header h3 {
  margin-top: var(--vg-space-1);
  font-size: 1.45rem;
  line-height: 1.1;
}

.close-btn {
  width: 2.25rem;
  height: 2.25rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-text-muted);
}

.close-btn:hover {
  border-color: var(--vg-border);
  background: var(--vg-surface-3);
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
  padding: var(--vg-space-5) var(--vg-space-6) var(--vg-space-8);
}

.drawer-body > hr {
  display: none;
}

.section {
  margin: 0;
  padding: var(--vg-space-4);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: var(--vg-radius);
  background: rgba(15, 23, 42, 0.68);
}

.section:first-child {
  position: relative;
  display: grid;
  grid-template-columns: 3.25rem minmax(0, 1fr);
  column-gap: var(--vg-space-4);
  align-items: center;
  background:
    linear-gradient(135deg, rgba(59, 130, 246, 0.16), rgba(34, 197, 94, 0.08)),
    rgba(15, 23, 42, 0.82);
}

.user-avatar {
  width: 3.25rem;
  height: 3.25rem;
  grid-row: 1 / span 3;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--vg-radius);
  background:
    radial-gradient(circle at 35% 30%, rgba(96, 165, 250, 0.95), transparent 32%),
    linear-gradient(135deg, rgba(59, 130, 246, 0.9), rgba(34, 197, 94, 0.75));
  box-shadow: 0 0 0 1px rgba(96, 165, 250, 0.25);
  color: white;
  font-family: var(--vg-font-display);
  font-weight: 900;
}

.section:first-child h4,
.section:first-child .text-muted,
.section:first-child .tags,
.section:first-child .reason-note {
  grid-column: 2;
}

.section h4 {
  margin-bottom: var(--vg-space-2);
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-lg);
  letter-spacing: 0;
}

.section:not(:first-child) h4 {
  font-size: var(--vg-text-base);
}

.text-muted {
  margin-bottom: var(--vg-space-3);
  color: var(--vg-text-muted);
}

.tags {
  gap: var(--vg-space-2);
  margin-bottom: 0;
}

.role-badge,
.plan-badge {
  min-width: 4.75rem;
  text-align: center;
  background: rgba(96, 165, 250, 0.13);
  color: #c7d2fe;
  border-color: rgba(96, 165, 250, 0.22);
  font-weight: 800;
}

.reason-note {
  margin-top: var(--vg-space-3);
  padding: var(--vg-space-3);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: var(--vg-radius-sm);
  background: rgba(239, 68, 68, 0.08);
}

.action-buttons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-3);
}

.btn-outline-danger,
.btn-danger,
.btn-outline-secondary,
.btn-primary,
.btn-copy {
  min-height: 2.75rem;
  border-radius: var(--vg-radius-sm);
  font-weight: 800;
  letter-spacing: 0;
  transition:
    background-color var(--vg-dur-fast) var(--vg-ease-out),
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    transform var(--vg-dur-fast) var(--vg-ease-out);
}

.btn-outline-danger {
  background: rgba(239, 68, 68, 0.04);
  border-color: rgba(239, 68, 68, 0.48);
}

.btn-danger {
  background: rgba(239, 68, 68, 0.14);
  border-color: rgba(239, 68, 68, 0.28);
}

.btn-primary {
  min-width: 8.25rem;
  background: linear-gradient(135deg, var(--vg-blue), var(--vg-blue-deep));
  box-shadow: 0 10px 30px -18px rgba(59, 130, 246, 0.9);
}

.btn-outline-secondary {
  background: rgba(148, 163, 184, 0.06);
}

.btn-outline-danger:hover:not(:disabled),
.btn-danger:hover:not(:disabled),
.btn-outline-secondary:hover:not(:disabled),
.btn-primary:hover:not(:disabled),
.btn-copy:hover:not(:disabled) {
  transform: translateY(-1px);
}

.input-group,
.toggle-row {
  align-items: stretch;
  gap: var(--vg-space-3);
}

.form-input {
  min-height: 2.75rem;
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(2, 6, 23, 0.42);
}

.form-input:focus {
  border-color: var(--vg-blue-bright);
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.16);
}

.quota-form {
  margin-top: var(--vg-space-3);
}

.toggle-row {
  padding: var(--vg-space-3);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.28);
}

.key-create-form {
  display: flex;
  gap: var(--vg-space-3);
  margin-bottom: var(--vg-space-3);
}

.secret-alert {
  border-color: rgba(34, 197, 94, 0.32);
  background: rgba(34, 197, 94, 0.1);
}

.table-shell {
  overflow-x: auto;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: var(--vg-radius-sm);
}

.keys-table {
  margin-top: 0;
  min-width: 31rem;
}

.keys-table th,
.keys-table td {
  padding: var(--vg-space-3);
}

.keys-table th {
  position: sticky;
  top: 0;
  background: rgba(20, 30, 52, 0.98);
  color: var(--vg-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.keys-table tr:last-child td {
  border-bottom: 0;
}

.empty-state {
  border: 1px dashed rgba(148, 163, 184, 0.22);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.24);
}

.quota-summary {
  display: flex;
  justify-content: space-between;
  gap: var(--vg-space-3);
  margin: 0 0 var(--vg-space-3);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

.quota-summary strong {
  color: var(--vg-text);
}

.quota-meter {
  height: 0.65rem;
  margin-bottom: var(--vg-space-4);
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.12);
}

.quota-meter div {
  height: 100%;
  min-width: 0;
  border-radius: inherit;
  background: linear-gradient(90deg, rgba(96, 165, 250, 0.95), rgba(34, 197, 94, 0.88));
}

/* Full-width in-card detail panel */
.drawer-overlay {
  inset: 0;
  display: block;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.98), rgba(2, 6, 23, 0.98)),
    var(--vg-surface);
  backdrop-filter: none;
}

.drawer {
  width: 100%;
  max-width: none;
  height: 100%;
  border-left: 0;
  box-shadow: none;
  animation: none;
  background:
    radial-gradient(circle at 12% 0%, rgba(59, 130, 246, 0.16), transparent 28rem),
    radial-gradient(circle at 88% 12%, rgba(34, 197, 94, 0.1), transparent 24rem),
    var(--vg-surface);
}

.drawer-header {
  min-height: auto;
  padding: var(--vg-space-5) var(--vg-space-6);
  background: rgba(15, 23, 42, 0.92);
}

.drawer-header::before {
  display: none;
}

.drawer-header > div {
  min-width: 0;
}

.header-kicker {
  display: block;
  margin-bottom: var(--vg-space-1);
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.drawer-header h3 {
  margin: 0;
  font-size: clamp(1.25rem, 2vw, 1.65rem);
  overflow-wrap: anywhere;
}

.drawer-header p {
  margin: var(--vg-space-1) 0 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  overflow-wrap: anywhere;
}

.close-btn {
  width: auto;
  min-width: 5.25rem;
  height: 2.5rem;
  padding: 0 var(--vg-space-4);
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: var(--vg-radius-sm);
  background: rgba(148, 163, 184, 0.08);
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
  font-weight: 800;
}

.drawer-body {
  display: grid;
  grid-template-columns: minmax(18rem, 0.9fr) minmax(20rem, 1.1fr);
  align-content: start;
  gap: var(--vg-space-4);
  width: min(100%, 72rem);
  margin: 0 auto;
  padding: var(--vg-space-5) var(--vg-space-6) var(--vg-space-8);
}

.section {
  min-width: 0;
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.7);
  box-shadow: 0 18px 50px -36px rgba(2, 6, 23, 0.85);
}

.user-summary,
.api-keys-section {
  grid-column: 1 / -1;
}

.user-summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(17rem, 0.8fr);
  gap: var(--vg-space-4);
  align-items: center;
  background:
    linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(34, 197, 94, 0.08)),
    rgba(15, 23, 42, 0.84);
}

.section:first-child h4,
.section:first-child .text-muted,
.section:first-child .tags,
.section:first-child .reason-note {
  grid-column: auto;
}

.summary-copy {
  min-width: 0;
}

.summary-copy h4 {
  margin-bottom: var(--vg-space-1);
  overflow-wrap: anywhere;
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--vg-space-3);
}

.summary-metrics div {
  min-width: 0;
  padding: var(--vg-space-3);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.28);
}

.summary-metrics span {
  display: block;
  margin-bottom: var(--vg-space-1);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 700;
  text-transform: uppercase;
}

.summary-metrics strong {
  display: block;
  color: var(--vg-text);
  font-size: var(--vg-text-lg);
  overflow-wrap: anywhere;
}

.reason-note {
  grid-column: 1 / -1;
}

.input-group {
  display: flex;
}

select.form-input {
  appearance: none;
  padding-right: 2.6rem;
  background-color: rgba(2, 6, 23, 0.42);
  background-image:
    linear-gradient(45deg, transparent 50%, var(--vg-text-muted) 50%),
    linear-gradient(135deg, var(--vg-text-muted) 50%, transparent 50%);
  background-position:
    calc(100% - 1.1rem) 50%,
    calc(100% - 0.78rem) 50%;
  background-repeat: no-repeat;
  background-size: 0.38rem 0.38rem, 0.38rem 0.38rem;
}

.key-create-form {
  margin-bottom: var(--vg-space-3);
}

.toggle-row .text-sm {
  margin: 0;
}

.keys-table td:last-child,
.keys-table th:last-child {
  width: 1%;
  text-align: right;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .drawer {
    width: 100%;
  }

  .drawer-header,
  .drawer-body {
    padding-inline: var(--vg-space-4);
  }

  .drawer-header {
    align-items: flex-start;
    gap: var(--vg-space-3);
  }

  .drawer-body {
    grid-template-columns: 1fr;
  }

  .section:first-child,
  .user-summary {
    grid-template-columns: 1fr;
  }

  .section:first-child::before {
    display: none;
  }

  .section:first-child h4,
  .section:first-child .text-muted,
  .section:first-child .tags,
  .section:first-child .reason-note {
    grid-column: 1;
  }

  .action-buttons,
  .input-group,
  .key-create-form,
  .toggle-row {
    grid-template-columns: 1fr;
    flex-direction: column;
  }
}
</style>
