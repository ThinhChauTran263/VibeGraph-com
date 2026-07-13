<script setup lang="ts">
import { onMounted } from 'vue'
import { useAccountStore } from '@/stores/account'
import StatusChip from '@/components/ui/StatusChip.vue'

const accountStore = useAccountStore()

onMounted(async () => {
  await accountStore.fetchProjects()
})
</script>

<template>
  <div class="projects-view">
    <div class="header">
      <h2>My Projects</h2>
    </div>
    
    <div class="projects-list">
      <div v-if="accountStore.projects.length === 0" class="empty-state">
        No projects found.
      </div>
      <div v-else class="table-responsive">
        <table class="projects-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
              <th>Last Analyzed</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="project in accountStore.projects" :key="project.id">
              <td class="font-medium">{{ project.name }}</td>
              <td>
                <StatusChip :status="project.status" :label="project.status" />
              </td>
              <td class="text-muted">{{ project.lastAnalyzedAt ? new Date(project.lastAnalyzedAt).toLocaleString() : 'Never' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.header {
  margin-bottom: 1.5rem;
}
.header h2 {
  margin: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}
.projects-list {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  box-shadow: var(--vg-shadow-sm);
  overflow: hidden;
}
.empty-state {
  padding: 3rem;
  text-align: center;
  color: var(--vg-text-dim);
}
.table-responsive {
  overflow-x: auto;
}
.projects-table {
  width: 100%;
  border-collapse: collapse;
}
.projects-table th,
.projects-table td {
  padding: 1rem 1.5rem;
  text-align: left;
  border-bottom: 1px solid var(--vg-border);
}
.projects-table th {
  background-color: var(--vg-surface-2);
  font-weight: 600;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm, 0.875rem);
}
.projects-table td {
  color: var(--vg-text);
}
.projects-table tbody tr {
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out);
}
.projects-table tbody tr:hover {
  background-color: var(--vg-surface-3);
}
.projects-table tbody tr:last-child td {
  border-bottom: none;
}
.font-medium {
  font-weight: 500;
}
.text-muted {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm, 0.875rem);
}
</style>

