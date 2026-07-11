<script setup lang="ts">
import { onMounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import StatusChip from '@/components/ui/StatusChip.vue'

const adminStore = useAdminStore()

onMounted(async () => {
  await adminStore.fetchUsers()
})

const handleBlock = (userId: string) => {
  if (confirm('Are you sure you want to block this user?')) {
    // API mock placeholder
    const user = adminStore.users.find(u => u.id === userId)
    if (user) user.status = 'blocked'
  }
}
</script>

<template>
  <div class="users-view">
    <div class="header">
      <h2>Users Management</h2>
      <p class="subtitle">Manage user accounts, roles, and status</p>
    </div>

    <div class="card">
      <div class="table-responsive">
        <table class="users-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
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
              <td>
                <StatusChip :status="user.status" :label="user.status" />
              </td>
              <td class="text-muted">{{ user.safeReason || '-' }}</td>
              <td>
                <button 
                  v-if="user.status !== 'blocked'" 
                  class="btn-danger btn-sm"
                  @click="handleBlock(user.id)"
                >
                  Block
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.header {
  margin-bottom: var(--vg-space-6);
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
.card {
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
.btn-sm {
  padding: 0.25rem 0.625rem;
  font-size: var(--vg-text-xs);
}
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
</style>
