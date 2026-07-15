<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAdminStore } from '@/stores/admin'
const admin = useAdminStore(),
  loading = ref(true),
  error = ref('')
async function load() {
  loading.value = true
  try {
    await admin.fetchSecurityEvents(50)
    error.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load security events.'
  } finally {
    loading.value = false
  }
}
onMounted(load)
const unavailable = [
  { title: 'Request monitor', text: 'Live request telemetry endpoint is unavailable.' },
  { title: 'Exact IP block / watchlist', text: 'IP policy management endpoint is unavailable.' },
  { title: 'Audit log', text: 'Audit log query endpoint is unavailable.' },
]
</script>
<template>
  <main class="security">
    <header>
      <div>
        <span>Operations</span>
        <h1>Security</h1>
        <p>Real security events and contract-aware defensive controls.</p>
      </div>
      <button type="button" :disabled="loading" @click="load">
        {{ loading ? 'Loading...' : 'Refresh events' }}
      </button>
    </header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <section class="events">
      <div>
        <h2>Rate-limit & security events</h2>
        <span>Live endpoint</span>
      </div>
      <p v-if="!loading && !admin.securityEvents.length" class="empty">
        No security events recorded.
      </p>
      <div v-else class="table">
        <div class="row head">
          <span>Type</span><span>Severity</span><span>Source</span><span>Description</span
          ><span>Created</span>
        </div>
        <div v-for="event in admin.securityEvents" :key="event.id" class="row">
          <strong>{{ event.eventType }}</strong
          ><span>{{ event.severity }}</span
          ><span>{{ event.source || '-' }}</span
          ><span>{{ event.description }}</span
          ><time>{{ event.createdAt ? new Date(event.createdAt).toLocaleString() : '-' }}</time>
        </div>
      </div>
    </section>
    <section class="unavailable" aria-label="Unavailable security capabilities">
      <article v-for="surface in unavailable" :key="surface.title" aria-disabled="true">
        <div>
          <h2>{{ surface.title }}</h2>
          <span>Contract unavailable</span>
        </div>
        <p>
          {{ surface.text }} This surface is intentionally disabled and contains no simulated data.
        </p>
        <button type="button" disabled>Unavailable</button>
      </article>
    </section>
  </main>
</template>
<style scoped>
.security {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-5);
}
header,
header > div,
.events > div,
.unavailable article > div {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--vg-space-4);
}
header > div {
  display: block;
}
header span {
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.1em;
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
p {
  color: var(--vg-text-muted);
}
button {
  min-height: 38px;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--vg-border);
  border-radius: 6px;
  background: var(--vg-surface);
  color: var(--vg-text);
  font: 600 var(--vg-text-sm) var(--vg-font-body);
  cursor: pointer;
}
button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.error {
  color: var(--vg-danger);
}
.events,
.unavailable article {
  padding: var(--vg-space-4);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
}
.events h2,
.unavailable h2 {
  margin: 0;
}
.events > div > span {
  color: var(--vg-green-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
}
.table {
  margin-top: var(--vg-space-4);
  overflow: auto;
}
.row {
  min-width: 900px;
  display: grid;
  grid-template-columns: 150px 100px 130px minmax(250px, 1fr) 180px;
  gap: var(--vg-space-3);
  padding: var(--vg-space-3);
  border-top: 1px solid var(--vg-border);
  color: var(--vg-text-muted);
}
.row.head {
  background: var(--vg-bg);
  color: var(--vg-text);
  font-weight: 700;
}
.row strong {
  color: var(--vg-text);
}
.empty {
  padding: var(--vg-space-5) 0;
  text-align: left;
}
.unavailable {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--vg-space-4);
}
.unavailable article {
  opacity: 0.68;
}
.unavailable article span {
  color: var(--vg-warning);
  font-size: var(--vg-text-xs);
  font-weight: 800;
}
.unavailable article button {
  width: auto;
  justify-self: start;
  text-align: left;
}
@media (max-width: 900px) {
  .unavailable {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  header {
    flex-direction: column;
  }
}
</style>
