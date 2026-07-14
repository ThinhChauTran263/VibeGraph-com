<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import type { AdminUserResponse } from '@/types/api'
import StatusChip from '@/components/ui/StatusChip.vue'
import UserDetailDrawer from './UserDetailDrawer.vue'

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
const isCreating = ref(false)
const createError = ref('')

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
  })
  syncSelectedUser()
}

const handleBlock = async (user: AdminUserResponse) => {
  const safeReason = prompt('Public reason shown to the user (max 240 chars):')
  if (!safeReason) return
  const reason = prompt('Internal reason (admin-only, max 500 chars):') ?? safeReason
  try {
    await adminStore.blockUser(user.id, reason, safeReason)
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : 'Failed to block user')
  }
}

const handleUnblock = async (user: AdminUserResponse) => {
  if (!confirm(`Unblock ${user.email}?`)) return
  try {
    await adminStore.unblockUser(user.id)
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : 'Failed to unblock user')
  }
}

const submitCreateUser = async () => {
  createError.value = ''
  isCreating.value = true
  try {
    await adminStore.createUser({ ...createForm.value })
    showCreateModal.value = false
    createForm.value = { email: '', displayName: '', role: 'USER', planCode: 'FREE', temporaryPassword: '' }
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
        <button class="btn-create" @click="showCreateModal = true">+ Create User</button>
      </div>

      <div class="filter-bar">
        <input
          id="adminUserSearch"
          name="userSearch"
          v-model="searchQuery"
          type="text"
          class="filter-input"
          placeholder="Search by email or name…"
          @keyup.enter="applyFilters"
        />
        <select id="adminUserStatusFilter" name="statusFilter" v-model="statusFilter" class="filter-select" @change="applyFilters">
          <option value="">All Statuses</option>
          <option value="active">Active</option>
          <option value="blocked">Blocked</option>
          <option value="deactivated">Deactivated</option>
        </select>
        <select id="adminUserPlanFilter" name="planFilter" v-model="planFilter" class="filter-select" @change="applyFilters">
          <option value="">All Plans</option>
          <option value="FREE">Free</option>
          <option value="PRO">Pro</option>
          <option value="PRO_PLUS">Pro+</option>
          <option value="MAX">Max</option>
          <option value="ENTERPRISE">Enterprise</option>
        </select>
        <button class="btn-filter" @click="applyFilters">Search</button>
      </div>
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
              <td class="font-medium">{{ user.displayName }}</td>
              <td>{{ user.email }}</td>
              <td class="capitalize">{{ user.role }}</td>
              <td class="text-muted">{{ user.planCode }}</td>
              <td>
                <StatusChip :status="userStatus(user)" :label="userStatus(user)" />
              </td>
              <td class="text-muted">{{ user.blockedReasonSafe ?? user.deactivationReasonSafe ?? '-' }}</td>
              <td class="actions-cell">
                <button class="btn-detail btn-sm" @click="openDrawer(user)">Detail</button>
                <button
                  v-if="!user.blocked"
                  class="btn-danger btn-sm"
                  @click="handleBlock(user)"
                >
                  Block
                </button>
                <button
                  v-else
                  class="btn-secondary btn-sm"
                  @click="handleUnblock(user)"
                >
                  Unblock
                </button>
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

    <!-- Create User Modal -->
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>Create User</h3>
          <button class="close-btn" @click="showCreateModal = false">&times;</button>
        </div>
        <form @submit.prevent="submitCreateUser" class="modal-form">
          <div class="form-group">
            <label>Email</label>
            <input v-model="createForm.email" type="email" class="form-input" required maxlength="254" />
          </div>
          <div class="form-group">
            <label>Display Name</label>
            <input v-model="createForm.displayName" type="text" class="form-input" required maxlength="120" />
          </div>
          <div class="form-group">
            <label>Role</label>
            <select v-model="createForm.role" class="form-input">
              <option value="USER">User</option>
              <option value="ADMIN">Admin</option>
            </select>
          </div>
          <div class="form-group">
            <label>Plan</label>
            <select v-model="createForm.planCode" class="form-input">
              <option value="FREE">Free</option>
              <option value="PRO">Pro</option>
              <option value="PRO_PLUS">Pro+</option>
              <option value="MAX">Max</option>
              <option value="ENTERPRISE">Enterprise</option>
            </select>
          </div>
          <div class="form-group">
            <label>Temporary Password</label>
            <input v-model="createForm.temporaryPassword" type="password" class="form-input" required minlength="8" maxlength="100" />
          </div>
          <div v-if="createError" class="error-text">{{ createError }}</div>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="showCreateModal = false">Cancel</button>
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
.header {
  margin-bottom: var(--vg-space-6);
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
  padding: 0.5rem 1.25rem;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  font-weight: 500;
  white-space: nowrap;
}
.btn-create:hover { opacity: 0.9; }

.filter-bar {
  display: flex;
  gap: var(--vg-space-3);
  flex-wrap: wrap;
}
.filter-input {
  flex: 1;
  min-width: 180px;
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  font-family: inherit;
}
.filter-select {
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  font-family: inherit;
}
.filter-input:focus, .filter-select:focus { outline: none; border-color: var(--vg-blue); }
.btn-filter {
  background: var(--vg-surface-3);
  color: var(--vg-text);
  border: 1px solid var(--vg-border);
  padding: 0.5rem 1rem;
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
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
.actions-cell { display: flex; gap: var(--vg-space-2); flex-wrap: wrap; }
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
.btn-detail:hover { background: rgba(148,163,184,0.16); }
.btn-danger {
  background: rgba(239, 68, 68, 0.15);
  color: var(--vg-danger);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  font-weight: 500;
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out),
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
.btn-secondary:hover { background: rgba(148,163,184,0.16); }
.table-footer { padding: var(--vg-space-3) var(--vg-space-4); font-size: var(--vg-text-sm); color: var(--vg-text-muted); text-align: right; }

/* Modal */
.modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.6); z-index: 2000;
  display: flex; align-items: center; justify-content: center; padding: 1rem;
}
.modal {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  width: 100%; max-width: 480px;
  box-shadow: var(--vg-shadow);
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--vg-border);
}
.modal-header h3 { margin: 0; color: var(--vg-text); }
.close-btn { background: none; border: none; font-size: 1.5rem; cursor: pointer; color: var(--vg-text-muted); line-height: 1; }
.close-btn:hover { color: var(--vg-text); }
.modal-form { padding: 1.5rem; display: flex; flex-direction: column; gap: 1rem; }
.form-group { display: flex; flex-direction: column; gap: 0.5rem; }
.form-group label { font-size: var(--vg-text-sm); font-weight: 500; color: var(--vg-text-muted); }
.form-input {
  padding: 0.5rem 0.75rem;
  background: var(--vg-bg-elev); color: var(--vg-text);
  border: 1px solid var(--vg-border); border-radius: var(--vg-radius-sm);
  font-family: inherit; font-size: var(--vg-text-base);
}
.form-input:focus { outline: none; border-color: var(--vg-blue); }
.modal-actions { display: flex; justify-content: flex-end; gap: 0.75rem; }
.btn-primary { background: var(--vg-grad-blue); color: white; border: none; padding: 0.5rem 1.25rem; border-radius: var(--vg-radius-sm); cursor: pointer; font-weight: 500; }
.btn-primary:disabled { opacity: 0.65; cursor: not-allowed; }
.error-text { color: var(--vg-danger); font-size: var(--vg-text-sm); }

@media (max-width: 768px) {
  .header-top { flex-direction: column; }
  .filter-bar { flex-direction: column; }
  .filter-input, .filter-select { width: 100%; }
}
</style>
