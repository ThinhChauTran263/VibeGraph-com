<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AdminUserResponse, ApiKey } from '@/types/api'
import { useAdminStore } from '@/stores/admin'
import StatusChip from '@/components/ui/StatusChip.vue'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import AdminReasonDialog from '@/components/admin/AdminReasonDialog.vue'

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
const creditQuotaOverride = ref<number | ''>('')
const creditAdjustment = ref<number | ''>('')
const creditReason = ref('')
const quotaError = ref('')
const isSavingQuota = ref(false)
const isAdjustingCredits = ref(false)

// ── Actions ──────────────────────────────────────────────────────────────────
const actionError = ref('')
const isActioning = ref(false)
const reasonDialogMode = ref<'block' | 'deactivate' | null>(null)
const confirmDialogMode = ref<'unblock' | 'disableApiKey' | 'lockApiKey' | 'unlockApiKey' | null>(
  null,
)
const pendingApiKeyId = ref<string | null>(null)

// ── Plan ─────────────────────────────────────────────────────────────────────
const selectedPlan = ref('FREE')
const isSavingPlan = ref(false)
const isPlanMenuOpen = ref(false)
const planOptions = computed(() =>
  adminStore.plans.map((plan) => ({ value: plan.code, label: plan.name })),
)

const selectedPlanLabel = computed(
  () => planOptions.value.find((plan) => plan.value === selectedPlan.value)?.label ?? selectedPlan.value,
)

const creditOverview = computed(() =>
  props.user ? adminStore.creditOverviews[props.user.id] ?? null : null,
)

// ── API Key creation toggle ───────────────────────────────────────────────────
const isTogglingApiKeyCreation = ref(false)

// ── User's API keys ───────────────────────────────────────────────────────────
const userApiKeys = ref<ApiKey[]>([])
const drawerBodyRef = ref<HTMLElement | null>(null)

// Reset form when user changes
watch(
  () => props.user,
  async (u, previousUser) => {
    if (!u) return
    const isNewUser = u.id !== previousUser?.id
    const previousScrollTop = drawerBodyRef.value?.scrollTop ?? 0

    if (isNewUser) {
      quotaError.value = ''
      actionError.value = ''
    }

    selectedPlan.value = u.planCode

    const legacyOverrideBytes = u.storageQuotaOverrideBytes
    storageOverrideMb.value =
      u.storageQuotaOverrideMb ??
      (legacyOverrideBytes != null ? Math.round(legacyOverrideBytes / (1024 * 1024)) : '')
    creditQuotaOverride.value = u.creditQuotaOverride ?? ''

    if (isNewUser) {
      // Load user API keys only when opening a different user. Updating the same
      // user should not reset the API key section scroll position.
      try {
        const [keys] = await Promise.all([
          adminStore.listApiKeysForUser(u.id),
          adminStore.fetchCreditOverview(u.id),
          adminStore.plans.length ? Promise.resolve() : adminStore.fetchPlans(),
        ])
        userApiKeys.value = keys
      } catch {
        userApiKeys.value = []
      }
    }

    requestAnimationFrame(() => {
      if (typeof drawerBodyRef.value?.scrollTo === 'function') {
        drawerBodyRef.value.scrollTo({ top: isNewUser ? 0 : previousScrollTop })
      }
    })
  },
  { immediate: false },
)

// ── Helpers ───────────────────────────────────────────────────────────────────

function usedMb(u: AdminUserResponse): number {
  return u.usedMb ?? Math.round((u.usedBytes ?? 0) / (1024 * 1024))
}

function quotaMb(u: AdminUserResponse): number {
  return u.quotaMb ?? Math.round((u.quotaBytes ?? 0) / (1024 * 1024))
}

function storagePercent(u: AdminUserResponse): number {
  const quota = quotaMb(u)
  if (!quota) return 0
  return Math.min(100, Math.round((usedMb(u) / quota) * 100))
}

function userInitials(u: AdminUserResponse): string {
  const source = u.displayName || u.email
  return (
    source
      .split(/[\s@._-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('') || 'US'
  )
}

function userStatus(u: AdminUserResponse): string {
  if (u.blocked) return 'blocked'
  if (u.deactivated) return 'deactivated'
  return 'active'
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function isExpired(key: ApiKey): boolean {
  if (!key.expiresAt) return false
  const expiresAt = new Date(key.expiresAt).getTime()
  return Number.isFinite(expiresAt) && expiresAt <= Date.now()
}

function apiKeyStatusLabel(key: ApiKey): string {
  if (key.deletedAt) return 'Deleted'
  if (key.locked) return 'Locked'
  if (key.disabled) return 'Disabled'
  if (isExpired(key)) return 'Expired'
  return 'Active'
}

function apiKeyStatus(key: ApiKey): string {
  if (key.deletedAt) return 'disabled'
  if (key.locked) return 'blocked'
  if (key.disabled) return 'disabled'
  if (isExpired(key)) return 'pending'
  return 'active'
}

function projectLabel(key: ApiKey): string {
  return key.project?.name ?? 'No repository binding'
}

function lockedMeta(key: ApiKey): string {
  const parts = []
  if (key.lockedBy) parts.push(`by ${key.lockedBy}`)
  if (key.lockedAt) parts.push(`on ${formatDate(key.lockedAt)}`)
  return parts.length ? `Locked ${parts.join(' ')}` : 'Locked by administrator'
}

// ── Quota ─────────────────────────────────────────────────────────────────────

const handleQuotaUpdate = async (target: 'storage' | 'credit' | 'both') => {
  if (!props.user) return
  const overrideMb =
    (target === 'storage' || target === 'both') && storageOverrideMb.value !== ''
      ? Number(storageOverrideMb.value)
      : null
  const usedMbVal = usedMb(props.user)
  if (overrideMb !== null && overrideMb < usedMbVal) {
    quotaError.value = `Cannot set quota lower than currently used (${usedMbVal} MB)`
    return
  }
  quotaError.value = ''
  isSavingQuota.value = true
  try {
    const creditOverride =
      (target === 'credit' || target === 'both') && creditQuotaOverride.value !== ''
        ? Number(creditQuotaOverride.value)
        : null
    await adminStore.updateQuota(props.user.id, overrideMb, creditOverride)
    await adminStore.fetchCreditOverview(props.user.id)
    emit('updated')
  } catch (e: unknown) {
    quotaError.value = e instanceof Error ? e.message : 'Failed to update quota'
  } finally {
    isSavingQuota.value = false
  }
}

const handleCreditAdjustment = async () => {
  if (!props.user || creditAdjustment.value === '' || !creditReason.value.trim()) return
  const delta = Number(creditAdjustment.value)
  if (!Number.isInteger(delta) || delta === 0) {
    quotaError.value = 'Credit adjustment must be a non-zero whole number.'
    return
  }
  isAdjustingCredits.value = true
  quotaError.value = ''
  try {
    await adminStore.adjustCredits(props.user.id, delta, creditReason.value.trim())
    creditAdjustment.value = ''
    creditReason.value = ''
    emit('updated')
  } catch (e: unknown) {
    quotaError.value = e instanceof Error ? e.message : 'Failed to adjust credits'
  } finally {
    isAdjustingCredits.value = false
  }
}

// ── Plan ──────────────────────────────────────────────────────────────────────

const handlePlanUpdate = async () => {
  if (!props.user) return
  isPlanMenuOpen.value = false
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

const selectPlan = (planCode: string) => {
  selectedPlan.value = planCode
  isPlanMenuOpen.value = false
}

const handlePlanFocusOut = (event: FocusEvent) => {
  const nextTarget = event.relatedTarget as Node | null
  if (!nextTarget || !(event.currentTarget as HTMLElement).contains(nextTarget)) {
    isPlanMenuOpen.value = false
  }
}

// ── Block / Deactivate / Unblock ──────────────────────────────────────────────

const handleBlock = async () => {
  if (!props.user) return
  reasonDialogMode.value = 'block'
}

const submitReasonAction = async (payload: { safeReason: string; reason: string }) => {
  if (!props.user || !reasonDialogMode.value) return
  const mode = reasonDialogMode.value
  isActioning.value = true
  actionError.value = ''
  try {
    if (mode === 'block') {
      await adminStore.blockUser(props.user.id, payload.reason, payload.safeReason)
    } else {
      await adminStore.deactivateUser(props.user.id, payload.reason, payload.safeReason)
    }
    reasonDialogMode.value = null
    emit('updated')
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : `Failed to ${mode} user`
  } finally {
    isActioning.value = false
  }
}

const handleUnblock = async () => {
  if (!props.user) return
  confirmDialogMode.value = 'unblock'
}

const confirmSimpleAction = async () => {
  if (!props.user || !confirmDialogMode.value) return
  const mode = confirmDialogMode.value
  isActioning.value = true
  actionError.value = ''
  try {
    if (mode === 'unblock') {
      await adminStore.unblockUser(props.user.id)
      emit('updated')
    } else if (pendingApiKeyId.value) {
      if (mode === 'disableApiKey') {
        await adminStore.disableApiKey(pendingApiKeyId.value)
      } else if (mode === 'lockApiKey') {
        await adminStore.lockApiKey(pendingApiKeyId.value)
      } else if (mode === 'unlockApiKey') {
        await adminStore.unlockApiKey(pendingApiKeyId.value)
      }
      userApiKeys.value = await adminStore.listApiKeysForUser(props.user.id)
    }
    confirmDialogMode.value = null
    pendingApiKeyId.value = null
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : 'Failed to apply action'
  } finally {
    isActioning.value = false
  }
}

const handleDeactivate = async () => {
  if (!props.user) return
  reasonDialogMode.value = 'deactivate'
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

// ── Admin disable API key ─────────────────────────────────────────────────────

const handleDisableApiKey = async (keyId: string) => {
  pendingApiKeyId.value = keyId
  confirmDialogMode.value = 'disableApiKey'
}

const handleLockApiKey = async (keyId: string) => {
  pendingApiKeyId.value = keyId
  confirmDialogMode.value = 'lockApiKey'
}

const handleUnlockApiKey = async (keyId: string) => {
  pendingApiKeyId.value = keyId
  confirmDialogMode.value = 'unlockApiKey'
}

const confirmDialogTitle = computed(() => {
  if (confirmDialogMode.value === 'disableApiKey') return 'Disable API key'
  if (confirmDialogMode.value === 'lockApiKey') return 'Lock API key'
  if (confirmDialogMode.value === 'unlockApiKey') return 'Unlock API key'
  return 'Unblock user'
})

const confirmDialogMessage = computed(() => {
  if (confirmDialogMode.value === 'disableApiKey') {
    return 'This key will stop working immediately. Existing key secrets cannot be recovered.'
  }
  if (confirmDialogMode.value === 'lockApiKey') {
    return 'Lock this key so the user cannot delete it or create a replacement for the same repository until an admin resolves it.'
  }
  if (confirmDialogMode.value === 'unlockApiKey') {
    return 'Resolve the administrator lock and allow the user to delete or replace this repository key again.'
  }
  return 'Restore product access for this user?'
})

const confirmDialogLabel = computed(() => {
  if (confirmDialogMode.value === 'disableApiKey') return 'Disable key'
  if (confirmDialogMode.value === 'lockApiKey') return 'Lock key'
  if (confirmDialogMode.value === 'unlockApiKey') return 'Unlock'
  return 'Unblock'
})

</script>

<template>
  <section v-if="isOpen && user" class="drawer-overlay" aria-label="Selected user detail">
    <div class="drawer">
      <div class="drawer-header">
        <div>
          <span class="header-kicker">Admin user detail</span>
          <h3>{{ user.displayName }}</h3>
          <p>{{ user.email }}</p>
          <div class="header-tags">
            <StatusChip :status="userStatus(user)" :label="userStatus(user)" />
            <span class="role-badge">{{ user.role }}</span>
            <span class="plan-badge">{{ user.planCode }}</span>
          </div>
        </div>
        <button
          class="close-btn"
          type="button"
          aria-label="Close user detail"
          @click="emit('close')"
        >
          Close
        </button>
      </div>

      <div ref="drawerBodyRef" class="drawer-body">
        <!-- User Info -->
        <div class="section user-summary">
          <div class="summary-copy">
            <h4>Account state</h4>
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
            <div class="summary-credit">
              <span>Credits remaining</span>
              <strong>{{ creditOverview?.creditBalance ?? '-' }}</strong>
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
              Block user
            </button>
            <button
              v-else
              class="btn-outline-secondary"
              @click="handleUnblock"
              :disabled="isActioning"
            >
              Unblock user
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
            <div class="plan-select" @focusout="handlePlanFocusOut">
              <button
                id="adminUserPlan"
                type="button"
                class="plan-select-button"
                role="combobox"
                aria-haspopup="listbox"
                :aria-expanded="isPlanMenuOpen"
                aria-controls="adminUserPlanList"
                @click="isPlanMenuOpen = !isPlanMenuOpen"
                @keydown.esc.prevent="isPlanMenuOpen = false"
                @keydown.down.prevent="isPlanMenuOpen = true"
                @keydown.enter.prevent="isPlanMenuOpen = !isPlanMenuOpen"
              >
                <span>{{ selectedPlanLabel }}</span>
                <span class="plan-select-chevron" aria-hidden="true"></span>
              </button>
              <div
                v-if="isPlanMenuOpen"
                id="adminUserPlanList"
                class="plan-select-menu"
                role="listbox"
                aria-label="Plan"
              >
                <button
                  v-for="plan in planOptions"
                  :key="plan.value"
                  type="button"
                  class="plan-select-option"
                  :class="{ selected: selectedPlan === plan.value }"
                  role="option"
                  :aria-selected="selectedPlan === plan.value"
                  @click="selectPlan(plan.value)"
                >
                  <span>{{ plan.label }}</span>
                  <span
                    v-if="selectedPlan === plan.value"
                    class="plan-select-check"
                    aria-hidden="true"
                    >Active</span
                  >
                </button>
              </div>
            </div>
            <button class="btn-primary" @click="handlePlanUpdate" :disabled="isSavingPlan">
              {{ isSavingPlan ? 'Saving...' : 'Update Plan' }}
            </button>
          </div>
        </div>

        <hr />

        <div id="user-quota-controls" class="quota-credit-grid" tabindex="-1">
          <div class="quota-side-stack">
            <div class="section storage-section">
              <div class="section-title-row">
                <div>
                  <h4>Storage Quota</h4>
                  <p class="section-caption">Source storage usage and custom limit for this account.</p>
                </div>
                <span class="state-pill storage-pill">{{ storagePercent(user) }}%</span>
              </div>

              <div class="quota-card">
                <p class="quota-summary">
                  <span><strong>{{ usedMb(user) }} MB</strong> used</span>
                  <span><strong>{{ quotaMb(user) }} MB</strong> quota</span>
                </p>
                <div class="quota-meter" aria-label="Storage quota usage">
                  <div :style="{ width: `${storagePercent(user)}%` }"></div>
                </div>

                <form class="storage-override-form" @submit.prevent="handleQuotaUpdate('storage')">
                  <label for="quotaLimit">
                    <span>Storage override (MB)</span>
                    <input id="quotaLimit" v-model="storageOverrideMb" name="quotaLimit" type="number" class="form-input" min="0" placeholder="Use plan default" />
                  </label>
                  <button type="submit" class="btn-outline-secondary" :disabled="isSavingQuota">
                    {{ isSavingQuota ? 'Saving...' : 'Save storage limit' }}
                  </button>
                </form>
                <div v-if="quotaError" class="error-text">{{ quotaError }}</div>
              </div>
            </div>

            <div class="section api-toggle-section">
              <div class="section-title-row">
                <div>
                  <h4>API Key Creation</h4>
                  <p class="section-caption">Manage whether this account can create additional API keys.</p>
                </div>
                <span class="state-pill" :class="{ disabled: user.apiKeyCreationDisabled }">
                  {{ user.apiKeyCreationDisabled ? 'Paused' : 'Allowed' }}
                </span>
              </div>
              <form class="api-key-policy-form" @submit.prevent="handleToggleApiKeyCreation">
                <div class="api-key-policy-copy">
                  <span>Current policy</span>
                  <strong>{{ user.apiKeyCreationDisabled ? 'New key creation is paused' : 'New key creation is allowed' }}</strong>
                  <small>{{ user.apiKeyCreationDisabled ? 'Existing keys remain visible but no new key can be issued.' : 'The user can issue a key subject to their plan limit.' }}</small>
                </div>
                <button type="submit" class="btn-outline-secondary" :disabled="isTogglingApiKeyCreation">
                  {{ isTogglingApiKeyCreation ? 'Saving...' : user.apiKeyCreationDisabled ? 'Allow creation' : 'Pause creation' }}
                </button>
              </form>
            </div>
          </div>

          <div class="section credit-section">
            <div class="section-title-row">
              <div>
                <h4>Credits</h4>
                <p class="section-caption">Period credit limit and one-time account adjustments.</p>
              </div>
              <span class="state-pill" :class="{ disabled: creditOverview?.creditBalance === 0 }">
                {{ creditOverview?.creditBalance ?? '-' }} remaining
              </span>
            </div>

            <div class="quota-card">
              <div class="credit-overview" aria-label="User credit balance">
                <div><span>Credit limit</span><strong>{{ creditOverview?.currentCreditsLimit ?? '-' }}</strong></div>
                <div><span>Credits used</span><strong>{{ creditOverview?.creditsUsed ?? '-' }}</strong></div>
                <div><span>Admin adjustment</span><strong>{{ creditOverview?.creditsAdjustment ?? '-' }}</strong></div>
                <div class="credits-remaining"><span>Credits remaining</span><strong>{{ creditOverview?.creditBalance ?? '-' }}</strong></div>
              </div>

              <form class="credit-limit-form" @submit.prevent="handleQuotaUpdate('credit')">
                <label for="creditQuotaLimit">
                  <span>Credit quota override</span>
                  <input id="creditQuotaLimit" v-model="creditQuotaOverride" name="creditQuotaLimit" type="number" class="form-input" min="0" placeholder="Use plan default" />
                </label>
                <button type="submit" class="btn-outline-secondary" :disabled="isSavingQuota">
                  {{ isSavingQuota ? 'Saving...' : 'Save credit limit' }}
                </button>
              </form>

              <form class="credit-form" @submit.prevent="handleCreditAdjustment">
                <label for="creditAdjustment"><span>Credit adjustment</span><input id="creditAdjustment" v-model="creditAdjustment" class="form-input" type="number" min="-1000000" max="1000000" placeholder="+100 or -25" required /></label>
                <label for="creditReason"><span>Internal reason</span><input id="creditReason" v-model="creditReason" class="form-input" maxlength="500" placeholder="Support correction or goodwill credit" required /></label>
                <button type="submit" class="btn-outline-secondary" :disabled="isAdjustingCredits || creditAdjustment === '' || !creditReason.trim()">{{ isAdjustingCredits ? 'Adjusting...' : 'Apply credit adjustment' }}</button>
              </form>
              <div v-if="quotaError" class="error-text">{{ quotaError }}</div>
            </div>
          </div>
        </div>

        <hr />

        <!-- User's API Keys -->
        <div class="section api-keys-section">
          <div class="section-title-row api-keys-title-row">
            <div>
              <h4>API Keys</h4>
              <p class="section-caption">
                API key metadata, repository binding, and administrator controls.
              </p>
            </div>
          </div>

          <!-- Existing keys list -->
          <div v-if="userApiKeys.length === 0" class="empty-state">No API keys.</div>
          <div v-else class="table-shell">
            <table class="keys-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>API Key</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="k in userApiKeys" :key="k.id" class="key-row">
                  <td>
                    <strong class="key-name">{{ k.name }}</strong>
                    <span class="key-meta">Repository: {{ projectLabel(k) }}</span>
                    <span v-if="k.project?.sourceType || k.project?.status" class="key-meta">
                      {{ k.project?.sourceType ?? 'Source unknown' }} / {{ k.project?.status ?? 'Status unknown' }}
                    </span>
                  </td>
                  <td>
                    <span class="mono key-value">{{ k.keyPrefix }}********</span>
                    <span class="key-meta">Prefix: {{ k.keyPrefix }}</span>
                    <span class="key-meta">Full secret is shown once to the user.</span>
                    <span class="key-meta">Created {{ formatDate(k.createdAt) }}</span>
                    <span class="key-meta">Last used {{ formatDate(k.lastUsedAt) }}</span>
                    <span v-if="k.expiresAt" class="key-meta">Expires {{ formatDate(k.expiresAt) }}</span>
                  </td>
                  <td>
                    <StatusChip
                      :status="apiKeyStatus(k)"
                      :label="apiKeyStatusLabel(k)"
                    />
                    <span v-if="k.disabledReason" class="key-meta key-reason">
                      {{ k.disabledReason }}
                    </span>
                    <span v-if="k.locked" class="key-meta key-reason">
                      {{ lockedMeta(k) }}
                    </span>
                    <span v-if="k.disabledBy" class="key-meta">
                      Disabled by {{ k.disabledBy }}
                    </span>
                  </td>
                  <td class="key-action-cell">
                    <div class="key-actions">
                      <button
                        v-if="!k.disabled && !k.deletedAt"
                        class="btn-danger btn-sm"
                        @click="handleDisableApiKey(k.id)"
                      >
                        Disable
                      </button>
                      <button
                        v-if="!k.locked && !k.deletedAt"
                        class="btn-outline-secondary btn-sm"
                        @click="handleLockApiKey(k.id)"
                      >
                        Lock
                      </button>
                      <button
                        v-if="k.locked && !k.deletedAt"
                        class="btn-outline-secondary btn-sm"
                        @click="handleUnlockApiKey(k.id)"
                      >
                        Unlock
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <AdminReasonDialog
      :open="Boolean(reasonDialogMode)"
      :title="reasonDialogMode === 'deactivate' ? 'Deactivate user' : 'Block user'"
      :description="
        reasonDialogMode === 'deactivate'
          ? 'Deactivate this account. This disables sign-in and API access without immediately removing account data.'
          : 'Block this account. Project analysis, imports, patches, and API keys will be paused.'
      "
      :confirm-label="reasonDialogMode === 'deactivate' ? 'Deactivate' : 'Block user'"
      :require-final-confirm="reasonDialogMode === 'deactivate'"
      :busy="isActioning"
      @cancel="reasonDialogMode = null"
      @submit="submitReasonAction"
    />

    <AdminConfirmDialog
      :open="Boolean(confirmDialogMode)"
      :title="confirmDialogTitle"
      :message="confirmDialogMessage"
      :confirm-label="confirmDialogLabel"
      :tone="confirmDialogMode === 'disableApiKey' || confirmDialogMode === 'lockApiKey' ? 'danger' : 'default'"
      :busy="isActioning"
      @cancel="
        confirmDialogMode = null,
        pendingApiKeyId = null
      "
      @confirm="confirmSimpleAction"
    />
  </section>
</template>

<style scoped>
.drawer-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
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
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
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
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: var(--vg-text-muted);
  line-height: 1;
}
.close-btn:hover {
  color: var(--vg-text);
}
.drawer-body {
  padding: 1.25rem 1.5rem;
  overflow-y: auto;
  flex: 1;
}
.section {
  margin-bottom: 0.25rem;
}
.section h4 {
  margin: 0 0 0.75rem 0;
  font-size: 0.9375rem;
  color: var(--vg-text);
}
.text-muted {
  color: var(--vg-text-muted);
  margin: 0 0 0.75rem 0;
  font-size: var(--vg-text-sm);
}
.text-sm {
  font-size: var(--vg-text-sm);
  margin: 0 0 0.75rem 0;
  color: var(--vg-text);
}
.tags {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 0.5rem;
}
.role-badge,
.plan-badge {
  background-color: var(--vg-surface-3);
  color: var(--vg-text-muted);
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  border: 1px solid var(--vg-border);
}
.reason-note {
  font-size: var(--vg-text-xs);
  color: var(--vg-danger);
  margin-top: 0.5rem;
}
hr {
  border: 0;
  border-top: 1px solid var(--vg-border);
  margin: 1rem 0;
}
.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}
.btn-outline-danger {
  background: transparent;
  color: var(--vg-danger);
  border: 1px solid var(--vg-danger);
  padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm);
  font-weight: 500;
  cursor: pointer;
}
.btn-outline-danger:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
}
.btn-outline-danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-outline-secondary {
  background: transparent;
  color: var(--vg-text-muted);
  border: 1px solid var(--vg-border);
  padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm);
  font-weight: 500;
  cursor: pointer;
}
.btn-outline-secondary:hover:not(:disabled) {
  background: var(--vg-surface-3);
}
.btn-outline-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-danger {
  background: rgba(239, 68, 68, 0.15);
  color: var(--vg-danger);
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm);
  font-weight: 500;
  cursor: pointer;
}
.btn-danger:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.25);
}
.btn-danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-primary {
  background: var(--vg-grad-blue);
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm);
  font-weight: 500;
  cursor: pointer;
}
.btn-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.btn-sm {
  padding: 0.25rem 0.625rem;
  font-size: var(--vg-text-xs);
}

.quota-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.quota-form label {
  font-size: var(--vg-text-sm);
  font-weight: 500;
  color: var(--vg-text-muted);
}
.input-group {
  display: flex;
  gap: 0.5rem;
}
.form-input {
  flex: 1;
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  font-family: inherit;
  font-size: var(--vg-text-base);
}
.form-input:focus {
  outline: none;
  border-color: var(--vg-blue);
}
.error-text {
  color: var(--vg-danger);
  font-size: var(--vg-text-sm);
  margin-top: 0.25rem;
}

.toggle-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

/* API Keys table */
.keys-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--vg-text-sm);
  margin-top: 0.5rem;
}
.keys-table th,
.keys-table td {
  padding: 0.5rem 0.5rem;
  text-align: left;
  border-bottom: 1px solid var(--vg-border);
  color: var(--vg-text);
}
.keys-table th {
  color: var(--vg-text-muted);
  font-weight: 600;
  background: var(--vg-surface-2);
}
.mono {
  font-family: monospace;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.empty-state {
  text-align: center;
  padding: 1rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

@media (max-width: 480px) {
  .drawer {
    width: 100%;
  }
  .input-group {
    flex-direction: column;
  }
  .action-buttons {
    flex-direction: column;
  }
}

/* Professional admin drawer polish */
.drawer-overlay {
  background:
    linear-gradient(90deg, rgba(2, 6, 23, 0.72), rgba(2, 6, 23, 0.36)), rgba(2, 6, 23, 0.58);
  backdrop-filter: blur(3px);
}

.drawer {
  width: min(42rem, calc(100% - 2rem));
  background:
    linear-gradient(180deg, rgba(20, 30, 52, 0.98), rgba(11, 17, 32, 0.99)), var(--vg-surface);
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
.btn-primary {
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
.btn-primary:hover:not(:disabled) {
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
  margin-top: auto;
  flex: 0 0 auto;
}

.storage-section .input-group {
  align-items: stretch;
}

.storage-section .form-input,
.storage-section .btn-primary {
  min-height: 3.25rem;
}

.toggle-row {
  padding: var(--vg-space-3);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.28);
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
  vertical-align: top;
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

.key-name,
.key-meta {
  display: block;
  min-width: 0;
}

.key-name {
  margin-bottom: 0.35rem;
  color: var(--vg-text);
  font-weight: 800;
  overflow-wrap: anywhere;
}

.key-value {
  display: block;
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-sm);
  font-weight: 800;
  overflow-wrap: anywhere;
}

.key-meta {
  margin-top: 0.24rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.key-reason {
  max-width: 18rem;
  color: var(--vg-amber);
}

.key-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--vg-space-2);
}

.key-actions .btn-sm {
  min-width: 5.25rem;
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

/* Full-width inline detail panel */
.drawer-overlay {
  position: relative;
  inset: auto;
  display: block;
  border-top: 1px solid var(--vg-border);
  background: var(--vg-surface);
  backdrop-filter: none;
}

.drawer {
  --detail-action-width: 8rem;
  --detail-action-height: 3rem;

  width: 100%;
  max-width: none;
  height: auto;
  border-left: 0;
  box-shadow: none;
  animation: none;
  background:
    radial-gradient(circle at 12% 0%, rgba(59, 130, 246, 0.16), transparent 28rem),
    radial-gradient(circle at 88% 12%, rgba(34, 197, 94, 0.1), transparent 24rem), var(--vg-surface);
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

.header-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--vg-space-2);
  margin-top: var(--vg-space-3);
}

.close-btn {
  width: var(--detail-action-width);
  min-width: var(--detail-action-width);
  height: var(--detail-action-height);
  min-height: var(--detail-action-height);
  margin-right: 2.5rem;
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
  grid-template-columns: repeat(2, minmax(20rem, 1fr));
  align-content: start;
  gap: var(--vg-space-4);
  width: 100%;
  max-width: none;
  margin: 0;
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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

.summary-metrics .summary-credit {
  border-color: rgba(34, 197, 94, 0.38);
  background: rgba(34, 197, 94, 0.08);
}

.summary-metrics .summary-credit strong {
  color: var(--vg-green-bright);
}

.reason-note {
  grid-column: 1 / -1;
}

.input-group {
  display: flex;
}

.plan-section .input-group {
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--detail-action-width);
  padding-inline: var(--vg-space-4);
}

.plan-section .input-group {
  grid-template-columns: minmax(0, 1fr) var(--detail-action-width);
}

.plan-select {
  position: relative;
  min-width: 0;
}

.plan-select-button {
  width: 100%;
  min-height: 3.25rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-3);
  padding: 0.65rem 0.9rem;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.48);
  color: var(--vg-text);
  font: inherit;
  font-size: var(--vg-text-base);
  font-weight: 700;
  letter-spacing: 0;
  cursor: pointer;
  transition:
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur-fast) var(--vg-ease-out),
    background-color var(--vg-dur-fast) var(--vg-ease-out);
}

.plan-select-button:hover,
.plan-select-button[aria-expanded='true'] {
  border-color: rgba(96, 165, 250, 0.72);
  background: rgba(15, 23, 42, 0.82);
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.14);
}

.plan-select-chevron {
  width: 0;
  height: 0;
  flex: 0 0 auto;
  border-left: 0.38rem solid transparent;
  border-right: 0.38rem solid transparent;
  border-top: 0.42rem solid var(--vg-text-muted);
  transition: transform var(--vg-dur-fast) var(--vg-ease-out);
}

.plan-select-button[aria-expanded='true'] .plan-select-chevron {
  transform: rotate(180deg);
}

.plan-select-menu {
  position: absolute;
  z-index: 80;
  top: calc(100% + var(--vg-space-2));
  left: 0;
  right: 0;
  max-height: 17rem;
  overflow-y: auto;
  padding: var(--vg-space-2);
  border: 1px solid rgba(96, 165, 250, 0.28);
  border-radius: var(--vg-radius-sm);
  background: #081120;
  box-shadow: 0 22px 60px -28px rgba(2, 6, 23, 0.96);
}

.plan-select-option {
  width: 100%;
  min-height: 2.75rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-3);
  padding: 0.55rem 0.7rem;
  border: 1px solid transparent;
  border-radius: calc(var(--vg-radius-sm) - 2px);
  background: transparent;
  color: var(--vg-text-muted);
  font: inherit;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}

.plan-select-option:hover,
.plan-select-option:focus-visible {
  outline: none;
  border-color: rgba(96, 165, 250, 0.24);
  background: rgba(59, 130, 246, 0.12);
  color: var(--vg-text);
}

.plan-select-option.selected {
  border-color: rgba(96, 165, 250, 0.36);
  background: rgba(37, 99, 235, 0.22);
  color: #dbeafe;
}

.plan-select-check {
  min-width: 4rem;
  padding: 0.2rem 0.45rem;
  border-radius: 999px;
  background: rgba(96, 165, 250, 0.14);
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 900;
  text-align: center;
  text-transform: uppercase;
}

.plan-section .btn-primary {
  width: var(--detail-action-width);
  min-width: var(--detail-action-width);
  min-height: var(--detail-action-height);
  padding-inline: var(--vg-space-3);
}

.plan-section .form-input {
  min-height: 3.25rem;
}

.plan-section .plan-select-button {
  min-height: 3rem;
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
  background-size:
    0.38rem 0.38rem,
    0.38rem 0.38rem;
}

.toggle-row .text-sm {
  margin: 0;
}

.user-summary {
  grid-template-columns: 1fr;
}

.user-avatar {
  display: none;
}

.summary-copy {
  align-self: stretch;
  display: flex;
  align-items: center;
  grid-column: 1 / -1;
}

.summary-metrics {
  grid-column: 1 / -1;
}

.user-summary .reason-note {
  grid-column: 1 / -1;
  width: 100%;
}

.api-toggle-section {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
}

.storage-section {
  gap: var(--vg-space-3);
}

.storage-section,
.api-toggle-section {
  min-height: 18rem;
}

.storage-section {
  display: flex;
  flex-direction: column;
}

.section-title-row {
  --detail-action-width: 8rem;
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--detail-action-width);
  align-items: flex-start;
  column-gap: var(--vg-space-3);
  row-gap: var(--vg-space-1);
  padding-inline: var(--vg-space-4);
}

.section-title-row > div {
  display: contents;
}

.section-title-row h4 {
  grid-column: 1;
  grid-row: 1;
  margin-bottom: var(--vg-space-1);
}

.section-caption {
  grid-column: 1 / -1;
  grid-row: 2;
  margin: 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.45;
}

.state-pill {
  grid-column: 2;
  grid-row: 1;
  justify-self: end;
  width: var(--detail-action-width);
  padding: 0.35rem 0.75rem;
  border: 1px solid rgba(34, 197, 94, 0.3);
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.1);
  color: var(--vg-green-bright);
  font-size: var(--vg-text-xs);
  font-weight: 900;
  text-align: center;
  text-transform: uppercase;
}

.state-pill.disabled {
  border-color: rgba(239, 68, 68, 0.32);
  background: rgba(239, 68, 68, 0.1);
  color: var(--vg-danger);
}

.storage-pill {
  border-color: rgba(96, 165, 250, 0.32);
  background: rgba(96, 165, 250, 0.1);
  color: var(--vg-blue-bright);
}

.quota-card,
.api-key-control {
  display: flex;
  flex: 1;
  min-height: 12rem;
  padding: var(--vg-space-4);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.3);
}

.quota-card {
  flex-direction: column;
  gap: var(--vg-space-3);
}

.quota-card .quota-summary,
.quota-card .quota-meter {
  margin-bottom: 0;
}

.quota-card .quota-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-3);
}

.quota-card .quota-summary span {
  min-width: 0;
  padding: var(--vg-space-2) var(--vg-space-3);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: var(--vg-radius-sm);
  background: rgba(15, 23, 42, 0.5);
  text-align: left;
}

.quota-card .quota-summary strong {
  display: inline-block;
  min-width: 4.75rem;
}

.quota-form .input-group {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 8rem;
}

.quota-form .btn-primary {
  width: 8rem;
  min-width: 8rem;
}

.api-key-control {
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  gap: var(--vg-space-3);
}

.api-key-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-1);
  justify-content: flex-start;
}

.api-key-copy strong {
  color: var(--vg-text);
  font-size: var(--vg-text-base);
}

.api-key-copy span {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.45;
}

.api-key-control .btn-sm {
  align-self: flex-end;
  margin-top: auto;
  width: 8rem;
  min-width: 8rem;
  min-height: 3.25rem;
}

.keys-table td:last-child,
.keys-table th:last-child {
  width: 1%;
  text-align: right;
  white-space: nowrap;
}

.keys-table .key-action-cell {
  white-space: normal;
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

  .close-btn {
    margin-right: 0;
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
  .toggle-row {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .summary-copy,
  .summary-metrics {
    grid-column: 1;
  }

  .section-title-row,
  .api-key-control {
    flex-direction: column;
    align-items: stretch;
  }

  .state-pill,
  .api-key-control .btn-sm {
    width: 100%;
  }
}

.drawer-body .user-summary {
  grid-template-columns: 1fr;
}

.drawer-body .user-summary .summary-copy,
.drawer-body .user-summary .summary-metrics,
.drawer-body .user-summary .reason-note {
  grid-column: 1 / -1;
}

.drawer-body .user-summary .reason-note {
  width: 100%;
}
</style>

<style scoped>
.quota-side-stack .api-toggle-section {
  padding: 0.55rem 0.95rem;
}

.quota-side-stack .api-toggle-section .section-title-row {
  margin-bottom: 0.35rem;
}

.quota-side-stack .api-toggle-section .section-caption {
  display: none;
}

.quota-side-stack .api-key-policy-form {
  grid-template-columns: minmax(0, 1fr) minmax(8rem, 8.5rem);
  gap: 0.55rem;
}

.quota-side-stack .api-key-policy-copy strong {
  line-height: 1.15;
}

.quota-side-stack .api-key-policy-form .btn-outline-secondary {
  min-height: 2.2rem;
  padding-block: 0.35rem;
}

.quota-side-stack .storage-section .quota-card {
  padding: 0.65rem;
  gap: 0.5rem;
}

.quota-side-stack .storage-section .quota-summary span {
  min-height: 3rem;
}

.quota-side-stack .storage-section .form-input,
.quota-side-stack .storage-section .btn-outline-secondary {
  min-height: 2.4rem;
}

.drawer-body #user-quota-controls .quota-side-stack {
  gap: 10px;
}
</style>

<style scoped>
.quota-side-stack .api-toggle-section {
  padding: 0.55rem 0.95rem;
}

.quota-side-stack .api-toggle-section .section-title-row {
  margin-bottom: 0.35rem;
}

.quota-side-stack .api-toggle-section .section-caption {
  display: none;
}

.quota-side-stack .api-key-policy-form {
  grid-template-columns: minmax(0, 1fr) minmax(8rem, 8.5rem);
  gap: 0.55rem;
}

.quota-side-stack .api-key-policy-copy strong {
  line-height: 1.15;
}

.quota-side-stack .api-key-policy-form .btn-outline-secondary {
  min-height: 2.2rem;
  padding-block: 0.35rem;
}

.quota-side-stack .storage-section .quota-card {
  padding: 0.65rem;
  gap: 0.5rem;
}

.quota-side-stack .storage-section .quota-summary span {
  min-height: 3rem;
}

.quota-side-stack .storage-section .form-input,
.quota-side-stack .storage-section .btn-outline-secondary {
  min-height: 2.4rem;
}
</style>

<style scoped>
.drawer-body #user-quota-controls.quota-credit-grid {
  align-items: stretch;
}

.drawer-body #user-quota-controls .credit-section {
  align-self: stretch;
  height: 100%;
}

.drawer-body #user-quota-controls .credit-section .quota-card {
  flex: 1 1 auto;
}

.drawer-body #user-quota-controls .storage-override-form,
.drawer-body #user-quota-controls .credit-limit-form {
  grid-template-columns: minmax(0, 1fr) 11.5rem;
  align-items: end;
  gap: 0.75rem;
}

.drawer-body #user-quota-controls .storage-override-form label,
.drawer-body #user-quota-controls .credit-limit-form label {
  min-width: 0;
  gap: 0.45rem;
}

.drawer-body #user-quota-controls .storage-override-form .form-input,
.drawer-body #user-quota-controls .credit-limit-form .form-input,
.drawer-body #user-quota-controls .storage-override-form .btn-outline-secondary,
.drawer-body #user-quota-controls .credit-limit-form .btn-outline-secondary {
  box-sizing: border-box;
  width: 100%;
  min-height: 2.4rem;
  height: 2.4rem;
}

.drawer-body #user-quota-controls .storage-override-form .btn-outline-secondary,
.drawer-body #user-quota-controls .credit-limit-form .btn-outline-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 11.5rem;
  max-width: 11.5rem;
  padding: 0 0.75rem;
  line-height: 1;
  white-space: nowrap;
}

.drawer-body #user-quota-controls .credit-overview div {
  min-height: 4.25rem;
  padding: 0.55rem 0.65rem;
}

.drawer-body #user-quota-controls .credit-overview span {
  font-size: 0.68rem;
  line-height: 1.15;
}

.drawer-body #user-quota-controls .credit-overview strong {
  margin-top: 0.2rem;
  font-size: var(--vg-text-base);
  line-height: 1.2;
}

.drawer-body #user-quota-controls .credit-limit-form {
  margin-top: 0.45rem;
}

@media (max-width: 760px) {
  .drawer-body #user-quota-controls .storage-override-form,
  .drawer-body #user-quota-controls .credit-limit-form {
    grid-template-columns: 1fr;
  }

  .drawer-body #user-quota-controls .storage-override-form .btn-outline-secondary,
  .drawer-body #user-quota-controls .credit-limit-form .btn-outline-secondary {
    min-width: 0;
    max-width: none;
  }
}
</style>

<style scoped>
.drawer-body #user-quota-controls .storage-override-form,
.drawer-body #user-quota-controls .credit-limit-form {
  grid-template-columns: minmax(0, 1fr) 11.5rem;
  align-items: end;
  gap: 0.75rem;
}

.drawer-body #user-quota-controls .storage-override-form label,
.drawer-body #user-quota-controls .credit-limit-form label {
  min-width: 0;
  gap: 0.45rem;
}

.drawer-body #user-quota-controls .storage-override-form .form-input,
.drawer-body #user-quota-controls .credit-limit-form .form-input,
.drawer-body #user-quota-controls .storage-override-form .btn-outline-secondary,
.drawer-body #user-quota-controls .credit-limit-form .btn-outline-secondary {
  box-sizing: border-box;
  width: 100%;
  min-height: 2.4rem;
  height: 2.4rem;
}

.drawer-body #user-quota-controls .storage-override-form .btn-outline-secondary,
.drawer-body #user-quota-controls .credit-limit-form .btn-outline-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 11.5rem;
  max-width: 11.5rem;
  padding: 0 0.75rem;
  line-height: 1;
  white-space: nowrap;
}

.drawer-body #user-quota-controls .credit-overview div {
  min-height: 4.25rem;
  padding: 0.55rem 0.65rem;
}

.drawer-body #user-quota-controls .credit-overview span {
  font-size: 0.68rem;
  line-height: 1.15;
}

.drawer-body #user-quota-controls .credit-overview strong {
  margin-top: 0.2rem;
  font-size: var(--vg-text-base);
  line-height: 1.2;
}

.drawer-body #user-quota-controls .credit-limit-form {
  margin-top: 0.45rem;
}

@media (max-width: 760px) {
  .drawer-body #user-quota-controls .storage-override-form,
  .drawer-body #user-quota-controls .credit-limit-form {
    grid-template-columns: 1fr;
  }

  .drawer-body #user-quota-controls .storage-override-form .btn-outline-secondary,
  .drawer-body #user-quota-controls .credit-limit-form .btn-outline-secondary {
    min-width: 0;
    max-width: none;
  }
}
</style>

<style scoped>
.quota-fields,
.credit-overview,
.credit-form {
  display: grid;
  gap: var(--vg-space-3);
}
.quota-fields {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.quota-fields label,
.credit-form label {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}
.credit-overview {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: var(--vg-space-4);
}
.credit-overview div {
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg);
}
.credit-overview span,
.credit-overview strong {
  display: block;
}
.credit-overview span {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  text-transform: uppercase;
}
.credit-overview strong {
  margin-top: var(--vg-space-1);
  color: var(--vg-text);
  font-size: var(--vg-text-lg);
}
.credits-remaining {
  border-color: rgba(34, 197, 94, 0.38) !important;
  background: rgba(34, 197, 94, 0.08) !important;
}
.credits-remaining strong {
  color: var(--vg-green-bright);
}
.credit-form {
  grid-template-columns: minmax(8rem, 0.5fr) minmax(14rem, 1fr) auto;
  align-items: end;
  margin-top: var(--vg-space-3);
}
@media (max-width: 760px) {
  .quota-fields,
  .credit-form {
    grid-template-columns: 1fr;
  }
  .credit-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

<style scoped>
.quota-credit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-4);
}
.quota-credit-grid .storage-section,
.quota-credit-grid .credit-section {
  min-height: 0;
}
.quota-credit-grid .quota-card {
  min-height: 0;
}
.storage-override-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: var(--vg-space-3);
}
.storage-override-form label,
.api-key-policy-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--vg-space-2);
}
.storage-override-form label > span,
.api-key-policy-copy > span {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}
.storage-override-form .btn-primary,
.credit-form .btn-outline-secondary,
.api-key-policy-form .btn-outline-secondary {
  min-width: var(--detail-action-width);
  min-height: 3.25rem;
}
.credit-section .credit-overview {
  margin-top: 0;
}
.credit-section .credit-form {
  margin-top: var(--vg-space-3);
}
.api-key-policy-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: var(--vg-space-3);
  flex: 1;
  min-height: 0;
  padding: var(--vg-space-4);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.3);
}
.api-key-policy-copy strong {
  color: var(--vg-text);
  font-size: var(--vg-text-base);
}
.api-key-policy-copy small {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.45;
}
@media (max-width: 960px) {
  .quota-credit-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 720px) {
  .storage-override-form,
  .api-key-policy-form {
    grid-template-columns: 1fr;
  }
  .storage-override-form .btn-primary,
  .credit-form .btn-outline-secondary,
  .api-key-policy-form .btn-outline-secondary {
    width: 100%;
  }
}
</style>

<style scoped>
.quota-credit-grid,
.api-toggle-section {
  grid-column: 1 / -1;
}

.quota-credit-grid {
  display: block;
}

.quota-management-section {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
}

.quota-management-title {
  padding-inline: 0;
}

.quota-management-card {
  display: grid;
  grid-template-columns: minmax(16rem, 0.85fr) minmax(28rem, 1.15fr);
  gap: var(--vg-space-4);
  padding: var(--vg-space-4);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.3);
}

.storage-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--vg-space-3);
}

.quota-management-card .quota-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-3);
  margin: 0;
}

.quota-management-card .quota-summary span {
  min-width: 0;
  padding: var(--vg-space-3);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: var(--vg-radius-sm);
  background: rgba(15, 23, 42, 0.58);
}

.quota-management-card .quota-summary strong {
  display: block;
  min-width: 0;
  margin-bottom: var(--vg-space-1);
  color: var(--vg-text);
  font-size: var(--vg-text-lg);
}

.quota-management-card .quota-meter {
  margin: 0;
}

.quota-management-card .credit-overview {
  align-self: stretch;
  grid-template-columns: repeat(4, minmax(7rem, 1fr));
  gap: var(--vg-space-3);
  margin: 0;
}

.quota-management-card .credit-overview div {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--vg-space-3);
  overflow: hidden;
}

.quota-management-card .credit-overview span {
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.quota-management-card .credit-overview strong {
  overflow-wrap: anywhere;
}

.quota-overrides-form,
.credit-form {
  grid-column: 1 / -1;
}

.quota-overrides-form {
  display: grid;
  grid-template-columns: minmax(13rem, 1fr) minmax(13rem, 1fr) minmax(12rem, auto);
  align-items: end;
  gap: var(--vg-space-3);
}

.quota-overrides-form label,
.credit-form label {
  min-width: 0;
}

.quota-overrides-form label {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}

.quota-overrides-form .btn-outline-secondary,
.credit-form .btn-outline-secondary {
  width: 100%;
  min-width: 12rem;
  min-height: 3.25rem;
  white-space: normal;
}

.api-toggle-section {
  min-height: auto;
}

.api-toggle-section .section-title-row {
  padding-inline: 0;
}

.api-key-policy-form {
  min-height: 0;
  padding: var(--vg-space-3);
  grid-template-columns: minmax(0, 1fr) minmax(10rem, auto);
  align-items: center;
}

.api-key-policy-copy {
  gap: var(--vg-space-1);
}

.api-key-policy-form .btn-outline-secondary {
  width: 100%;
  min-width: 10rem;
  min-height: 3rem;
}

@media (max-width: 1180px) {
  .quota-management-card {
    grid-template-columns: 1fr;
  }

  .quota-management-card .credit-overview {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .quota-overrides-form,
  .credit-form,
  .api-key-policy-form {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .quota-management-card .quota-summary,
  .quota-management-card .credit-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

<style scoped>
.drawer-body .quota-credit-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-4);
}

.drawer-body .quota-credit-grid .storage-section,
.drawer-body .quota-credit-grid .credit-section {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
}

.drawer-body .quota-credit-grid .section-title-row {
  padding-inline: 0;
}

.drawer-body .quota-credit-grid .section-caption {
  grid-column: 1 / -1;
}

.drawer-body .quota-credit-grid .quota-card {
  min-height: 0;
  flex: 1;
}

.drawer-body .quota-credit-grid .quota-summary {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.drawer-body .quota-credit-grid .quota-summary span {
  display: block;
  min-width: 0;
}

.drawer-body .quota-credit-grid .quota-summary strong {
  display: block;
  min-width: 0;
  margin-bottom: var(--vg-space-1);
}

.credit-limit-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(10rem, auto);
  align-items: end;
  gap: var(--vg-space-3);
}

.credit-limit-form label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--vg-space-2);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}

.credit-limit-form .btn-outline-secondary,
.storage-override-form .btn-outline-secondary {
  width: 100%;
  min-width: 10rem;
  min-height: 3.25rem;
  white-space: normal;
}

.drawer-body .quota-credit-grid .credit-overview {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 0;
}

.drawer-body .quota-credit-grid .credit-overview div {
  min-width: 0;
  overflow: hidden;
}

.drawer-body .quota-credit-grid .credit-overview span,
.drawer-body .quota-credit-grid .credit-overview strong {
  overflow-wrap: anywhere;
}

.drawer-body .quota-credit-grid .credit-form {
  grid-template-columns: minmax(8rem, 0.7fr) minmax(14rem, 1fr) minmax(12rem, auto);
}

@media (max-width: 1180px) {
  .drawer-body .quota-credit-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .credit-limit-form,
  .drawer-body .quota-credit-grid .credit-form,
  .storage-override-form {
    grid-template-columns: 1fr;
  }

  .drawer-body .quota-credit-grid .credit-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

<style scoped>
.drawer-body #user-quota-controls.quota-credit-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: stretch;
  gap: var(--vg-space-4);
}

.quota-side-stack {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}

.quota-side-stack .storage-section,
.quota-side-stack .api-toggle-section,
.drawer-body #user-quota-controls .credit-section {
  min-width: 0;
  min-height: 0;
}

.quota-side-stack .storage-section,
.quota-side-stack .api-toggle-section {
  flex: 0 0 auto;
}

.quota-side-stack .storage-section {
  display: flex;
  flex-direction: column;
  background: rgba(15, 23, 42, 0.7);
}

.quota-side-stack .storage-section h4,
.quota-side-stack .storage-section .section-caption {
  grid-column: auto;
}

.quota-side-stack .storage-section .section-title-row {
  width: 100%;
}

.drawer-body #user-quota-controls .section-title-row {
  padding-inline: 0;
  grid-template-columns: minmax(0, 1fr) minmax(8rem, 10rem);
  align-items: start;
}

.drawer-body #user-quota-controls .section-title-row h4,
.drawer-body #user-quota-controls .section-caption {
  text-align: left;
}

.drawer-body #user-quota-controls .section-caption {
  grid-column: 1 / -1;
}

.drawer-body #user-quota-controls .quota-card,
.quota-side-stack .api-key-policy-form {
  width: 100%;
  min-height: 0;
  box-sizing: border-box;
}

.quota-side-stack .storage-section .quota-card {
  flex: 0 0 auto;
}

.quota-side-stack .storage-section .quota-summary {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.quota-side-stack .storage-section .quota-summary span {
  padding: var(--vg-space-3);
}

.storage-override-form {
  grid-template-columns: minmax(0, 1fr) minmax(10rem, 12rem);
}

.storage-override-form .btn-outline-secondary,
.credit-limit-form .btn-outline-secondary,
.api-key-policy-form .btn-outline-secondary {
  min-width: 0;
  max-width: none;
}

.quota-side-stack .api-toggle-section .section-title-row {
  grid-template-columns: minmax(0, 1fr) minmax(8rem, 10rem);
}

.quota-side-stack .api-key-policy-form {
  grid-template-columns: minmax(0, 1fr) minmax(10rem, 12rem);
  align-items: center;
}

.drawer-body #user-quota-controls .credit-section {
  display: flex;
  flex-direction: column;
}

.drawer-body #user-quota-controls .credit-section .quota-card {
  flex: 1;
}

.drawer-body #user-quota-controls .credit-overview {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.drawer-body #user-quota-controls .credit-form {
  grid-template-columns: minmax(7rem, 8rem) minmax(0, 1fr) minmax(8.25rem, 9rem);
  align-items: end;
  width: 100%;
}

.drawer-body #user-quota-controls .credit-form .btn-outline-secondary {
  width: 100%;
  min-width: 0;
  max-width: 9rem;
  justify-self: stretch;
  overflow-wrap: anywhere;
}

.drawer-body #user-quota-controls .credit-form .form-input,
.drawer-body #user-quota-controls .credit-form label {
  min-width: 0;
}

@media (max-width: 1180px) {
  .drawer-body #user-quota-controls.quota-credit-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .storage-override-form,
  .credit-limit-form,
  .quota-side-stack .api-key-policy-form,
  .drawer-body #user-quota-controls .credit-form {
    grid-template-columns: 1fr;
  }

  .drawer-body #user-quota-controls .credit-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

<style scoped>
.drawer-body #user-quota-controls.quota-credit-grid {
  align-items: start;
}

.drawer-body #user-quota-controls .credit-section,
.drawer-body #user-quota-controls .credit-section .quota-card {
  align-self: start;
}

.drawer-body #user-quota-controls .credit-section .quota-card {
  flex: 0 0 auto;
}

.quota-side-stack {
  gap: var(--vg-space-3);
}

.quota-side-stack .api-toggle-section {
  padding: var(--vg-space-3);
}

.quota-side-stack .api-toggle-section .section-title-row {
  grid-template-columns: minmax(0, 1fr) minmax(7rem, 8.75rem);
  column-gap: var(--vg-space-3);
}

.quota-side-stack .api-toggle-section h4 {
  margin-bottom: 0;
}

.quota-side-stack .api-toggle-section .section-caption {
  line-height: 1.35;
}

.quota-side-stack .api-key-policy-form {
  grid-template-columns: minmax(0, 1fr) minmax(8.5rem, 9.5rem);
  gap: var(--vg-space-3);
  padding: 0.75rem 0.875rem;
}

.quota-side-stack .api-key-policy-copy {
  gap: 0.35rem;
}

.quota-side-stack .api-key-policy-copy small {
  line-height: 1.35;
  font-size: var(--vg-text-xs);
}

.quota-side-stack .api-key-policy-form .btn-outline-secondary {
  min-height: 2.5rem;
}

@media (max-width: 760px) {
  .drawer-body #user-quota-controls.quota-credit-grid {
    align-items: stretch;
  }

  .quota-side-stack .api-key-policy-form {
    grid-template-columns: 1fr;
  }
}
</style>

<style scoped>
.drawer-body #user-quota-controls .section {
  padding: 0.95rem;
}

.drawer-body #user-quota-controls .section-title-row {
  margin-bottom: 0.75rem;
}

.drawer-body #user-quota-controls .section-title-row h4 {
  font-size: 1.05rem;
}

.drawer-body #user-quota-controls .section-caption {
  font-size: var(--vg-text-sm);
  line-height: 1.3;
}

.quota-side-stack .storage-section .quota-card {
  padding: 0.8rem;
  gap: 0.65rem;
}

.quota-side-stack .storage-section .quota-summary {
  gap: 0.75rem;
}

.quota-side-stack .storage-section .quota-summary span {
  min-height: 3.25rem;
  padding: 0.6rem 0.75rem;
}

.quota-side-stack .storage-section .quota-meter {
  height: 0.48rem;
}

.quota-side-stack .storage-section .storage-override-form {
  grid-template-columns: minmax(0, 1fr) minmax(10.5rem, 11.5rem);
  gap: 0.75rem;
}

.quota-side-stack .storage-section .form-input,
.quota-side-stack .storage-section .btn-outline-secondary {
  min-height: 2.55rem;
}

.quota-side-stack .storage-section .btn-outline-secondary {
  white-space: nowrap;
}

.quota-side-stack .api-toggle-section {
  padding: 0.8rem 0.95rem;
}

.quota-side-stack .api-toggle-section .section-title-row {
  margin-bottom: 0.45rem;
}

.quota-side-stack .api-key-policy-form {
  grid-template-columns: minmax(0, 1fr) minmax(8rem, 8.75rem);
  align-items: center;
  gap: 0.65rem;
  padding: 0;
  border: 0;
  background: transparent;
}

.quota-side-stack .api-key-policy-copy {
  gap: 0.2rem;
}

.quota-side-stack .api-key-policy-copy > span {
  display: none;
}

.quota-side-stack .api-key-policy-copy strong {
  font-size: var(--vg-text-sm);
}

.quota-side-stack .api-key-policy-copy small {
  display: none;
  max-width: 25rem;
  font-size: var(--vg-text-xs);
  line-height: 1.25;
}

.quota-side-stack .api-key-policy-form .btn-outline-secondary {
  min-height: 2.4rem;
  padding-inline: 0.75rem;
}

@media (max-width: 760px) {
  .quota-side-stack .storage-section .storage-override-form,
  .quota-side-stack .api-key-policy-form {
    grid-template-columns: 1fr;
  }
}
</style>

<style scoped>
.quota-side-stack .api-toggle-section {
  padding: 0.55rem 0.95rem;
}

.quota-side-stack .api-toggle-section .section-title-row {
  margin-bottom: 0.35rem;
}

.quota-side-stack .api-toggle-section .section-caption {
  display: none;
}

.quota-side-stack .api-key-policy-form {
  grid-template-columns: minmax(0, 1fr) minmax(8rem, 8.5rem);
  gap: 0.55rem;
}

.quota-side-stack .api-key-policy-copy strong {
  line-height: 1.15;
}

.quota-side-stack .api-key-policy-form .btn-outline-secondary {
  min-height: 2.2rem;
  padding-block: 0.35rem;
}

.quota-side-stack .storage-section .quota-card {
  padding: 0.65rem;
  gap: 0.5rem;
}

.quota-side-stack .storage-section .quota-summary span {
  min-height: 3rem;
}

.quota-side-stack .storage-section .form-input,
.quota-side-stack .storage-section .btn-outline-secondary {
  min-height: 2.4rem;
}
</style>

<style scoped>
.drawer-body #user-quota-controls.quota-credit-grid {
  align-items: stretch;
}

.drawer-body #user-quota-controls .credit-section {
  align-self: stretch;
  height: 100%;
}

.drawer-body #user-quota-controls .credit-section .quota-card {
  flex: 1 1 auto;
}

.drawer-body #user-quota-controls .credit-section .section-title-row .state-pill {
  margin-right: 1rem;
}
</style>
