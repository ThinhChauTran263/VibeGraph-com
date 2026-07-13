<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useAdminStore } from '@/stores/admin'

const adminStore = useAdminStore()
let pollInterval: ReturnType<typeof setInterval>

onMounted(async () => {
  await adminStore.fetchOverview()
  // Poll every 30s
  pollInterval = setInterval(() => {
    adminStore.fetchOverview()
  }, 30000)
})

onUnmounted(() => {
  clearInterval(pollInterval)
})
</script>

<template>
  <div class="dashboard-view">
    <div class="header">
      <h2>Admin Dashboard</h2>
      <p class="subtitle">Platform overview and metrics</p>
    </div>

    <div v-if="adminStore.overview" class="metrics-grid">
      <div class="metric-card">
        <div class="metric-title">Total Users</div>
        <div class="metric-value">{{ adminStore.overview.totalUsers }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-title">Online Users</div>
        <div class="metric-value text-success">{{ adminStore.overview.onlineUsers }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-title">Total Projects</div>
        <div class="metric-value">{{ adminStore.overview.totalProjects }}</div>
      </div>
    </div>
    <div v-else class="loading">
      Loading dashboard...
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
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--vg-space-6);
}
.metric-card {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-bottom: 3px solid var(--vg-blue);
  border-radius: var(--vg-radius);
  padding: var(--vg-space-6);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: var(--vg-space-2);
  backdrop-filter: blur(12px);
  transition: transform var(--vg-dur-fast) var(--vg-ease-out),
              box-shadow var(--vg-dur-fast) var(--vg-ease-out),
              border-color var(--vg-dur-fast) var(--vg-ease-out);
}
.metric-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--vg-shadow);
  border-color: var(--vg-border-strong);
}
.metric-title {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-family: var(--vg-font-body);
}
.metric-value {
  font-size: 3rem;
  font-weight: 700;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}
.text-success {
  color: var(--vg-green-bright);
}
.loading {
  color: var(--vg-text-muted);
  padding: var(--vg-space-6);
  text-align: center;
  font-family: var(--vg-font-body);
}
</style>
