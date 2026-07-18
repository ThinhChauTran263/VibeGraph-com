<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AdminUserResponse, ApiKey } from '@/types/api'
import { useAdminStore } from '@/stores/admin'
import StatusChip from '@/components/ui/StatusChip.vue'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import AdminReasonDialog from '@/components/admin/AdminReasonDialog.vue'

const props = defineProps<{ isOpen: boolean; user: AdminUserResponse | null }>()
const emit = defineEmits<{ close: []; updated: [] }>()
const admin = useAdminStore()
const keys = ref<ApiKey[]>([])
const loadingKeys = ref(false)
const actionError = ref('')
const pending = ref<{ type: 'disable' | 'lock' | 'unlock'; id: string } | null>(null)
const reasonMode = ref<'block' | 'deactivate' | null>(null)
const actionBusy = ref(false)

function status(user: AdminUserResponse): string {
  if (user.blocked) return 'blocked'
  if (user.deactivated) return 'deactivated'
  return 'active'
}

async function loadKeys(userId: string): Promise<void> {
  loadingKeys.value = true
  try {
    keys.value = await admin.listApiKeysForUser(userId)
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : 'Could not load API key metadata.'
    keys.value = []
  } finally {
    loadingKeys.value = false
  }
}

watch(
  () => [props.isOpen, props.user?.id] as const,
  ([open, userId]) => {
    if (open && userId) void loadKeys(userId)
  },
  { immediate: true },
)

function requestKeyAction(type: 'disable' | 'lock' | 'unlock', id: string): void {
  pending.value = { type, id }
}

async function confirmKeyAction(): Promise<void> {
  if (!pending.value || !props.user) return
  actionBusy.value = true
  actionError.value = ''
  try {
    if (pending.value.type === 'disable') await admin.disableApiKey(pending.value.id)
    else if (pending.value.type === 'lock') await admin.lockApiKey(pending.value.id)
    else await admin.unlockApiKey(pending.value.id)
    await loadKeys(props.user.id)
    pending.value = null
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : 'Could not update this API key.'
  } finally {
    actionBusy.value = false
  }
}

async function submitReason(payload: { reason: string; safeReason: string }): Promise<void> {
  if (!props.user || !reasonMode.value) return
  actionBusy.value = true
  try {
    if (reasonMode.value === 'block') await admin.blockUser(props.user.id, payload.reason, payload.safeReason)
    else await admin.deactivateUser(props.user.id, payload.reason, payload.safeReason)
    reasonMode.value = null
    emit('updated')
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : 'Could not update the user.'
  } finally {
    actionBusy.value = false
  }
}

async function unblock(): Promise<void> {
  if (!props.user) return
  actionBusy.value = true
  try {
    await admin.unblockUser(props.user.id)
    emit('updated')
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : 'Could not unblock the user.'
  } finally {
    actionBusy.value = false
  }
}
</script>

<template>
  <section v-if="isOpen && user" class="drawer-overlay" aria-label="Selected user detail">
    <div class="drawer" role="dialog" aria-modal="true" aria-labelledby="user-detail-title">
      <header class="drawer-header">
        <div>
          <span class="eyebrow">Admin user detail</span>
          <h2 id="user-detail-title">{{ user.displayName }}</h2>
          <p>{{ user.email }}</p>
        </div>
        <button class="close-btn" type="button" aria-label="Close user detail" @click="emit('close')">Close</button>
      </header>
      <div class="drawer-body">
        <section class="section">
          <h3>Account</h3>
          <div class="tags">
            <StatusChip :status="status(user)" :label="status(user)" />
            <span class="tag">{{ user.role }}</span><span class="tag">{{ user.planCode }}</span>
          </div>
          <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
          <div class="actions">
            <button v-if="!user.blocked" type="button" class="danger" @click="reasonMode = 'block'">Block user</button>
            <button v-else type="button" @click="unblock">Unblock user</button>
            <button v-if="!user.deactivated" type="button" @click="reasonMode = 'deactivate'">Deactivate user</button>
          </div>
        </section>

        <section class="section api-keys-section" aria-labelledby="api-keys-title">
          <div class="section-title"><div><h3 id="api-keys-title">API key metadata</h3><p>Admins can inspect, disable, or lock keys. Users create their own keys.</p></div></div>
          <p v-if="loadingKeys">Loading API key metadata...</p>
          <p v-else-if="!keys.length">No API keys.</p>
          <div v-else class="key-list">
            <article v-for="key in keys" :key="key.id" class="key-row">
              <div><strong>{{ key.name }}</strong><code>{{ key.keyPrefix }}</code></div>
              <span>{{ key.project?.name ?? 'Unbound project' }}</span>
              <span :class="{ off: key.disabled }">{{ key.disabled ? 'Disabled' : 'Active' }}</span>
              <span v-if="key.disabledBy">Disabled by {{ key.disabledBy }}</span>
              <span v-if="key.disabledReason">{{ key.disabledReason }}</span>
              <strong v-if="key.locked" class="locked">
                Admin locked{{ key.lockedBy ? ` by ${key.lockedBy}` : '' }}
              </strong>
              <div class="actions">
                <button v-if="!key.disabled" type="button" @click="requestKeyAction('disable', key.id)">Disable</button>
                <button v-if="!key.locked" type="button" @click="requestKeyAction('lock', key.id)">Lock</button>
                <button v-else type="button" @click="requestKeyAction('unlock', key.id)">Unlock</button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </div>
    <AdminReasonDialog
      :open="Boolean(reasonMode)"
      :title="reasonMode === 'block' ? 'Block user' : 'Deactivate user'"
      :description="reasonMode === 'block' ? 'Block this account and pause API access.' : 'Deactivate this account and disable sign-in.'"
      :confirm-label="reasonMode === 'block' ? 'Block user' : 'Deactivate user'"
      :busy="actionBusy"
      @cancel="reasonMode = null"
      @submit="submitReason"
    />
    <AdminConfirmDialog
      :open="Boolean(pending)"
      :title="pending?.type === 'lock' ? 'Lock API key' : pending?.type === 'unlock' ? 'Unlock API key' : 'Disable API key'"
      :message="pending?.type === 'lock' ? 'Lock this key so the user cannot delete or replace it?' : pending?.type === 'unlock' ? 'Unlock this key? It remains active and the user may delete it before creating a replacement.' : 'Disable this key immediately?'"
      :confirm-label="pending?.type === 'lock' ? 'Lock key' : pending?.type === 'unlock' ? 'Unlock key' : 'Disable key'"
      tone="danger"
      :busy="actionBusy"
      @cancel="pending = null"
      @confirm="confirmKeyAction"
    />
  </section>
</template>

<style scoped>
.drawer-overlay { position: fixed; inset: 0; z-index: 1000; display: flex; justify-content: flex-end; background: rgba(2, 6, 23, .65); }
.drawer { width: min(46rem, 100%); height: 100%; overflow-y: auto; background: var(--vg-surface); color: var(--vg-text); }
.drawer-header { display: flex; justify-content: space-between; gap: 1rem; padding: 1.5rem; border-bottom: 1px solid var(--vg-border); }
.drawer-header h2 { margin: .25rem 0; }
.drawer-header p, .section p { color: var(--vg-text-muted); }
.eyebrow { color: var(--vg-blue-bright); font-size: var(--vg-text-xs); font-weight: 800; text-transform: uppercase; }
.close-btn, button { min-height: 2.5rem; padding: .45rem .75rem; border: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm); background: transparent; color: inherit; cursor: pointer; }
.drawer-body { display: grid; gap: 1rem; padding: 1.5rem; }
.section { padding: 1rem; border: 1px solid var(--vg-border); border-radius: var(--vg-radius); background: var(--vg-bg); }
.section h3 { margin-top: 0; }
.tags, .actions { display: flex; flex-wrap: wrap; gap: .5rem; }
.tag { padding: .25rem .5rem; border: 1px solid var(--vg-border); border-radius: 999px; }
.actions { margin-top: 1rem; }
.danger { border-color: var(--vg-danger); color: var(--vg-danger); }
.error { color: var(--vg-danger) !important; }
.key-list { display: grid; gap: .75rem; }
.key-row { display: grid; grid-template-columns: 1.2fr 1fr .6fr; gap: .5rem; padding: .75rem; border: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm); }
.key-row code, .key-row span { overflow-wrap: anywhere; color: var(--vg-text-muted); }
.key-row .actions { grid-column: 1 / -1; margin-top: .25rem; }
.off, .locked { color: var(--vg-danger) !important; }
@media (max-width: 600px) { .key-row { grid-template-columns: 1fr 1fr; } }
</style>
