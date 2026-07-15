<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import type { AdminUserResponse } from '@/types/api'
import StatusChip from '@/components/ui/StatusChip.vue'
import UserDetailDrawer from './UserDetailDrawer.vue'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import AdminReasonDialog from '@/components/admin/AdminReasonDialog.vue'

const adminStore = useAdminStore()

// Detail panel
const drawerOpen = ref(false)
const selectedUser = ref<AdminUserResponse | null>(null)

// Search / filter
const searchQuery = ref('')
const statusFilter = ref('')
const planFilter = ref('')

// Create user modal
const showCreateModal = ref(false)
const createForm = ref({
  email: '',
  displayName: '',
  role: 'USER',
  planCode: 'FREE',
  temporaryPassword: '',
})
const createRoleOptions = [
  { value: 'USER', label: 'User', description: 'Workspace, reports, API keys' },
  { value: 'ADMIN', label: 'Admin', description: 'Admin console access' },
] as const
const createPlanOptions = [
  { value: 'FREE', label: 'Free', description: '100 MB + 100 credits' },
  { value: 'PRO', label: 'Pro', description: '500 MB + 500 credits' },
  { value: 'PRO_PLUS', label: 'Pro Plus', description: '1024 MB + 1000 credits' },
  { value: 'MAX', label: 'Max', description: '2048 MB + 2000 credits' },
  { value: 'ENTERPRISE', label: 'Enterprise', description: 'Custom contract' },
] as const
const isCreating = ref(false)
const createError = ref('')
const tableActionError = ref('')
const reasonDialogUser = ref<AdminUserResponse | null>(null)
const unblockDialogUser = ref<AdminUserResponse | null>(null)
const isUserActioning = ref(false)

onMounted(async () => {
  await adminStore.fetchUsers()
})

const applyFilters = async () => {
  await adminStore.fetchUsers({
    search: searchQuery.value || undefined,
    status: statusFilter.value || undefined,
    plan: planFilter.value || undefined,
  })
}

const openDrawer = (user: AdminUserResponse) => {
  selectedUser.value = user
  drawerOpen.value = true
}

const closeDrawer = () => {
  drawerOpen.value = false
  selectedUser.value = null
}

const syncSelectedUser = () => {
  if (!selectedUser.value) return
  const fresh = adminStore.users.find((u) => u.id === selectedUser.value?.id)
  if (fresh) selectedUser.value = fresh
}

/** After the detail panel performs an action, keep the open detail in sync immediately. */
const onUserUpdated = async () => {
  syncSelectedUser()
  await adminStore.fetchUsers({
    search: searchQuery.value || undefined,
    status: statusFilter.value || undefined,
    plan: planFilter.value || undefined,
    page: adminStore.usersPagination.pageNumber,
    size: adminStore.usersPagination.pageSize,
  })
  syncSelectedUser()
}

const handleBlock = async (user: AdminUserResponse) => {
  reasonDialogUser.value = user
}

const submitBlockReason = async (payload: { safeReason: string; reason: string }) => {
  if (!reasonDialogUser.value) return
  isUserActioning.value = true
  tableActionError.value = ''
  try {
    await adminStore.blockUser(reasonDialogUser.value.id, payload.reason, payload.safeReason)
    reasonDialogUser.value = null
  } catch (e: unknown) {
    tableActionError.value = e instanceof Error ? e.message : 'Failed to block user'
  } finally {
    isUserActioning.value = false
  }
}

const handleUnblock = async (user: AdminUserResponse) => {
  unblockDialogUser.value = user
}

const confirmUnblock = async () => {
  if (!unblockDialogUser.value) return
  isUserActioning.value = true
  tableActionError.value = ''
  try {
    await adminStore.unblockUser(unblockDialogUser.value.id)
    unblockDialogUser.value = null
  } catch (e: unknown) {
    tableActionError.value = e instanceof Error ? e.message : 'Failed to unblock user'
  } finally {
    isUserActioning.value = false
  }
}

const submitCreateUser = async () => {
  createError.value = ''
  isCreating.value = true
  try {
    await adminStore.createUser({ ...createForm.value })
    showCreateModal.value = false
    createForm.value = {
      email: '',
      displayName: '',
      role: 'USER',
      planCode: 'FREE',
      temporaryPassword: '',
    }
  } catch (e: unknown) {
    createError.value = e instanceof Error ? e.message : 'Failed to create user'
  } finally {
    isCreating.value = false
  }
}

/** Derived status label for display */
function userStatus(u: AdminUserResponse): string {
  if (u.blocked) return 'blocked'
  if (u.deactivated) return 'deactivated'
  return 'active'
}
</script>

<template>
  <div class="users-view">
    <div class="header">
      <div class="header-top">
        <div>
          <h2>Users Management</h2>
          <p class="subtitle">Manage user accounts, roles, and status</p>
        </div>
        <button class="btn-create" aria-label="Create User" @click="showCreateModal = true">
          + Create
        </button>
      </div>

      <div class="filter-bar">
        <input
          id="adminUserSearch"
          name="userSearch"
          v-model="searchQuery"
          type="text"
          class="filter-input"
          aria-label="Search users"
          placeholder="Search by email or name…"
          @keyup.enter="applyFilters"
        />
        <select
          id="adminUserStatusFilter"
          name="statusFilter"
          v-model="statusFilter"
          class="filter-select"
          aria-label="Filter users by status"
          @change="applyFilters"
        >
          <option value="">All Statuses</option>
          <option value="active">Active</option>
          <option value="blocked">Blocked</option>
          <option value="deactivated">Deactivated</option>
        </select>
        <select
          id="adminUserPlanFilter"
          name="planFilter"
          v-model="planFilter"
          class="filter-select"
          aria-label="Filter users by plan"
          @change="applyFilters"
        >
          <option value="">All Plans</option>
          <option value="FREE">Free</option>
          <option value="PRO">Pro</option>
          <option value="PRO_PLUS">Pro+</option>
          <option value="MAX">Max</option>
          <option value="ENTERPRISE">Enterprise</option>
        </select>
        <button class="btn-filter" @click="applyFilters">Search</button>
      </div>
      <div v-if="tableActionError" class="inline-error">{{ tableActionError }}</div>
    </div>

    <div class="card">
      <div class="table-responsive">
        <table class="users-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Plan</th>
              <th>Status</th>
              <th>Reason</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in adminStore.users" :key="user.id">
              <td class="font-medium" data-label="Name">{{ user.displayName }}</td>
              <td data-label="Email">{{ user.email }}</td>
              <td class="capitalize" data-label="Role">{{ user.role }}</td>
              <td class="text-muted" data-label="Plan">{{ user.planCode }}</td>
              <td data-label="Status">
                <StatusChip :status="userStatus(user)" :label="userStatus(user)" />
              </td>
              <td class="text-muted" data-label="Reason">
                {{ user.blockedReasonSafe ?? user.deactivationReasonSafe ?? '-' }}
              </td>
              <td class="actions-cell" data-label="Actions">
                <div class="row-actions">
                  <button class="btn-detail btn-sm" @click="openDrawer(user)">Detail</button>
                  <button v-if="!user.blocked" class="btn-danger btn-sm" @click="handleBlock(user)">
                    Block
                  </button>
                  <button v-else class="btn-secondary btn-sm" @click="handleUnblock(user)">
                    Unblock
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="table-footer" v-if="adminStore.usersPagination.totalElements > 0">
        {{ adminStore.users.length }} / {{ adminStore.usersPagination.totalElements }} users
      </div>

      <!-- User Detail Panel: rendered inside the users list card -->
      <UserDetailDrawer
        :isOpen="drawerOpen"
        :user="selectedUser"
        @close="closeDrawer"
        @updated="onUserUpdated"
      />
    </div>

    <AdminReasonDialog
      :open="Boolean(reasonDialogUser)"
      title="Block user"
      :description="`Block ${reasonDialogUser?.email ?? 'this user'} and pause project, patch, analyze, and API key actions.`"
      confirm-label="Block user"
      :busy="isUserActioning"
      @cancel="reasonDialogUser = null"
      @submit="submitBlockReason"
    />

    <AdminConfirmDialog
      :open="Boolean(unblockDialogUser)"
      title="Unblock user"
      :message="`Restore access for ${unblockDialogUser?.email ?? 'this user'}?`"
      confirm-label="Unblock"
      :busy="isUserActioning"
      @cancel="unblockDialogUser = null"
      @confirm="confirmUnblock"
    />

    <!-- Create User Modal -->
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div
        class="modal create-user-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="createUserTitle"
      >
        <div class="modal-header">
          <div>
            <p class="modal-kicker">Admin action</p>
            <h3 id="createUserTitle">Create user</h3>
            <p class="modal-subtitle">Create a manual account with a temporary password.</p>
          </div>
          <button
            class="close-btn"
            aria-label="Close create user modal"
            @click="showCreateModal = false"
          >
            &times;
          </button>
        </div>
        <form @submit.prevent="submitCreateUser" class="modal-form">
          <div class="create-grid">
            <div class="form-group">
              <label for="createUserEmail">Email</label>
              <input
                id="createUserEmail"
                v-model="createForm.email"
                type="email"
                class="form-input"
                name="email"
                autocomplete="email"
                placeholder="user@company.com"
                required
                maxlength="254"
              />
            </div>
            <div class="form-group">
              <label for="createUserDisplayName">Display name</label>
              <input
                id="createUserDisplayName"
                v-model="createForm.displayName"
                type="text"
                class="form-input"
                name="displayName"
                autocomplete="name"
                placeholder="Jane Nguyen"
                required
                maxlength="120"
              />
            </div>
          </div>

          <fieldset class="form-group option-fieldset">
            <legend>Role</legend>
            <div class="role-options" role="group" aria-label="Create user role">
              <button
                v-for="role in createRoleOptions"
                :key="role.value"
                type="button"
                class="role-option"
                :class="{ selected: createForm.role === role.value }"
                :aria-pressed="createForm.role === role.value"
                @click="createForm.role = role.value"
              >
                <span class="option-title">{{ role.label }}</span>
                <span class="option-description">{{ role.description }}</span>
              </button>
            </div>
          </fieldset>

          <fieldset class="form-group option-fieldset">
            <legend>Plan</legend>
            <div class="plan-options" role="group" aria-label="Create user plan">
              <button
                v-for="plan in createPlanOptions"
                :key="plan.value"
                type="button"
                class="plan-option"
                :class="{ selected: createForm.planCode === plan.value }"
                :aria-pressed="createForm.planCode === plan.value"
                @click="createForm.planCode = plan.value"
              >
                <span class="option-title">{{ plan.label }}</span>
                <span class="option-description">{{ plan.description }}</span>
              </button>
            </div>
          </fieldset>

          <div class="form-group">
            <label for="createUserPassword">Temporary password</label>
            <input
              id="createUserPassword"
              v-model="createForm.temporaryPassword"
              type="password"
              class="form-input"
              name="temporaryPassword"
              autocomplete="new-password"
              placeholder="Minimum 8 characters"
              required
              minlength="8"
              maxlength="100"
            />
          </div>
          <div v-if="createError" class="error-text" role="alert">{{ createError }}</div>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="showCreateModal = false">
              Cancel
            </button>
            <button type="submit" class="btn-primary" :disabled="isCreating">
              {{ isCreating ? 'Creating...' : 'Create' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
:host,
.users-view {
  --admin-users-action-width: 8rem;
  --admin-users-action-height: 3rem;
}

.header {
  margin-bottom: var(--vg-space-6);
  padding-right: 4rem;
}
.header-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--vg-space-4);
  margin-bottom: var(--vg-space-4);
  flex-wrap: wrap;
}
.header h2 {
  margin: 0 0 var(--vg-space-2) 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}
.subtitle {
  color: var(--vg-text-dim);
  margin: 0;
  font-family: var(--vg-font-body);
}
.btn-create {
  background: var(--vg-grad-blue);
  color: white;
  border: none;
  width: var(--admin-users-action-width);
  min-width: var(--admin-users-action-width);
  min-height: var(--admin-users-action-height);
  padding: 0.5rem 0.75rem;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  font-weight: 800;
  white-space: nowrap;
}
.btn-create:hover {
  opacity: 0.9;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(18rem, 42rem) 11rem 10rem var(--admin-users-action-width);
  gap: var(--vg-space-3);
  justify-content: end;
  align-items: stretch;
}
.filter-input {
  min-width: 0;
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  font-family: inherit;
}
.filter-select {
  min-width: 0;
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  font-family: inherit;
}
.filter-input:focus,
.filter-select:focus {
  outline: none;
  border-color: var(--vg-blue);
}
.btn-filter {
  background: var(--vg-surface-3);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  width: var(--admin-users-action-width);
  min-width: var(--admin-users-action-width);
  min-height: var(--admin-users-action-height);
  padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  font-weight: 800;
}
.inline-error {
  margin-top: var(--vg-space-3);
  padding: var(--vg-space-3);
  border: 1px solid rgba(239, 68, 68, 0.32);
  border-radius: var(--vg-radius-sm);
  background: rgba(239, 68, 68, 0.08);
  color: var(--vg-danger);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}

.card {
  position: relative;
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  overflow: hidden;
}
.table-responsive {
  overflow-x: auto;
  max-height: 600px;
}
.users-table {
  width: 100%;
  border-collapse: collapse;
}
.users-table th {
  position: sticky;
  top: 0;
  background-color: var(--vg-surface-2);
  font-weight: 600;
  color: var(--vg-text-muted);
  padding: var(--vg-space-4);
  text-align: left;
  border-bottom: 1px solid var(--vg-border);
  z-index: 10;
  font-size: var(--vg-text-xs);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  font-family: var(--vg-font-body);
}
.users-table td {
  padding: var(--vg-space-4);
  text-align: left;
  border-bottom: 1px solid var(--vg-border);
  color: var(--vg-text);
  font-family: var(--vg-font-body);
}
.users-table tbody tr {
  background: var(--vg-surface);
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out);
}
.users-table tbody tr:hover {
  background-color: var(--vg-surface-3);
}
.users-table tbody tr:last-child td {
  border-bottom: none;
}
.font-medium {
  font-weight: 500;
  color: var(--vg-text);
}
.capitalize {
  text-transform: capitalize;
}
.text-muted {
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.actions-cell {
  min-width: 10rem;
  white-space: nowrap;
}
.row-actions {
  display: flex;
  align-items: center;
  gap: var(--vg-space-2);
  flex-wrap: nowrap;
}
.btn-sm {
  padding: 0.25rem 0.625rem;
  font-size: var(--vg-text-xs);
}
.btn-detail {
  background: var(--vg-surface-3);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
}
.btn-detail:hover {
  background: rgba(148, 163, 184, 0.16);
}
.btn-danger {
  background: rgba(239, 68, 68, 0.15);
  color: var(--vg-danger);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  font-weight: 500;
  transition:
    background-color var(--vg-dur-fast) var(--vg-ease-out),
    border-color var(--vg-dur-fast) var(--vg-ease-out);
}
.btn-danger:hover {
  background: rgba(239, 68, 68, 0.25);
  border-color: rgba(239, 68, 68, 0.5);
}
.btn-secondary {
  background: var(--vg-surface-3);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
}
.btn-secondary:hover {
  background: rgba(148, 163, 184, 0.16);
}
.table-footer {
  padding: var(--vg-space-3) var(--vg-space-4);
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
  text-align: right;
}

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}
.modal {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  width: 100%;
  max-width: 480px;
  box-shadow: var(--vg-shadow);
  overflow: hidden;
}
.create-user-modal {
  max-width: 720px;
  border-color: rgba(148, 163, 184, 0.22);
}
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--vg-space-4);
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--vg-border);
  background: linear-gradient(180deg, rgba(148, 163, 184, 0.08), rgba(148, 163, 184, 0.02));
}
.modal-header h3 {
  margin: 0;
  color: var(--vg-text);
  font-size: 1.25rem;
  line-height: 1.2;
}
.modal-kicker {
  margin: 0 0 0.25rem;
  color: var(--vg-blue);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.modal-subtitle {
  margin: 0.35rem 0 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}
.close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--vg-surface-2);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  width: 2.25rem;
  height: 2.25rem;
  cursor: pointer;
  color: var(--vg-text-muted);
  font-size: 1.35rem;
  line-height: 1;
  flex: 0 0 auto;
}
.close-btn:hover {
  color: var(--vg-text);
}
.modal-form {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.create-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--vg-space-3);
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.form-group label,
.option-fieldset legend {
  font-size: var(--vg-text-sm);
  font-weight: 700;
  color: var(--vg-text-muted);
}
.option-fieldset {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}
.form-input {
  min-height: 2.75rem;
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
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14);
}
select.form-input {
  appearance: none;
  padding-right: 2.6rem;
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
.role-options,
.plan-options {
  display: grid;
  gap: var(--vg-space-2);
}
.role-options {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.plan-options {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}
.role-option,
.plan-option {
  min-width: 0;
  min-height: 4.5rem;
  padding: 0.75rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg-elev);
  color: var(--vg-text);
  cursor: pointer;
  text-align: left;
  font: inherit;
  transition:
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    background-color var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur-fast) var(--vg-ease-out);
}
.role-option:hover,
.plan-option:hover {
  border-color: rgba(59, 130, 246, 0.48);
  background: rgba(59, 130, 246, 0.08);
}
.role-option.selected,
.plan-option.selected {
  border-color: var(--vg-blue);
  background: rgba(59, 130, 246, 0.14);
  box-shadow: inset 0 0 0 1px rgba(59, 130, 246, 0.28);
}
.option-title,
.option-description {
  display: block;
  overflow-wrap: anywhere;
}
.option-title {
  color: var(--vg-text);
  font-weight: 800;
  line-height: 1.2;
}
.option-description {
  margin-top: 0.35rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  line-height: 1.35;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 0.75rem;
  padding-top: 0.25rem;
}
.modal-actions .btn-secondary,
.modal-actions .btn-primary {
  min-width: 7rem;
  min-height: 2.75rem;
  padding: 0.5rem 1rem;
  font-weight: 800;
}
.btn-primary {
  background: var(--vg-grad-blue);
  color: white;
  border: none;
  padding: 0.5rem 1.25rem;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  font-weight: 500;
}
.btn-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.error-text {
  color: var(--vg-danger);
  font-size: var(--vg-text-sm);
}

@media (max-width: 768px) {
  .header {
    padding-right: 0;
  }
  .header-top {
    flex-direction: column;
  }
  .filter-bar {
    grid-template-columns: 1fr;
    justify-content: stretch;
  }
  .filter-input,
  .filter-select {
    width: 100%;
  }

  .table-responsive {
    max-height: none;
    overflow-x: visible;
  }

  .users-table,
  .users-table tbody,
  .users-table tr,
  .users-table td {
    display: block;
  }

  .users-table thead {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }

  .users-table tbody {
    padding: var(--vg-space-3);
  }

  .users-table tbody tr {
    border: 1px solid var(--vg-border);
    border-radius: var(--vg-radius-sm);
    margin-bottom: var(--vg-space-3);
    overflow: hidden;
  }

  .users-table tbody tr:last-child {
    margin-bottom: 0;
  }

  .users-table td {
    display: grid;
    grid-template-columns: minmax(5.5rem, 34%) minmax(0, 1fr);
    gap: var(--vg-space-3);
    align-items: start;
    padding: var(--vg-space-3);
    border-bottom: 1px solid var(--vg-border);
    word-break: break-word;
  }

  .users-table td::before {
    content: attr(data-label);
    color: var(--vg-text-muted);
    font-size: var(--vg-text-xs);
    font-weight: 700;
    letter-spacing: 0.04em;
    text-transform: uppercase;
  }

  .users-table td.actions-cell {
    min-width: 0;
    white-space: normal;
  }

  .row-actions {
    justify-content: flex-end;
    flex-wrap: wrap;
  }

  .modal-overlay {
    align-items: flex-start;
    overflow-y: auto;
    padding: var(--vg-space-4);
  }

  .create-user-modal {
    max-width: 100%;
    margin: var(--vg-space-4) 0;
  }

  .modal-header,
  .modal-form {
    padding: var(--vg-space-4);
  }

  .create-grid {
    grid-template-columns: 1fr;
  }

  .role-options,
  .plan-options {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .plan-option:last-child {
    grid-column: 1 / -1;
  }

  .modal-actions {
    justify-content: stretch;
  }

  .modal-actions .btn-secondary,
  .modal-actions .btn-primary {
    flex: 1;
    min-width: 0;
  }

  .btn-sm {
    min-width: 76px;
    min-height: 36px;
  }

  .table-footer {
    text-align: left;
  }
}
</style>
