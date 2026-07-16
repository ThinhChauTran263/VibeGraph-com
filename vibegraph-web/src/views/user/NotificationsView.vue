<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError, api } from '@/lib/api'
export interface UserAnnouncement {
  id: string
  creatorName: string
  title: string
  body: string
  createdAt: string
}
const items = ref<UserAnnouncement[]>([]),
  selected = ref<UserAnnouncement | null>(null),
  available = ref(true),
  error = ref('')
onMounted(async () => {
  try {
    items.value = (await api.get<UserAnnouncement[]>('/api/account/announcements')).sort(
      (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt),
    )
    const requested = new URLSearchParams(location.search).get('id')
    selected.value = items.value.find((item) => item.id === requested) ?? null
  } catch (e) {
    if (e instanceof ApiError && [404, 405, 501].includes(e.status)) {
      available.value = false
      return
    }
    error.value = e instanceof Error ? e.message : 'Notifications could not be loaded.'
  }
})
</script>
<template>
  <main class="notifications">
    <header>
      <span>Inbox</span>
      <h1>Notification</h1>
      <p>Product announcements and operational updates.</p>
    </header>
    <p v-if="error" role="alert">{{ error }}</p>
    <section v-if="!available" class="empty">
      <h2>Notifications are not connected yet</h2>
      <p>The announcement endpoint is unavailable. No placeholder messages are shown.</p>
    </section>
    <section v-else-if="!items.length" class="empty">
      <h2>All quiet</h2>
      <p>There are no notifications for your account.</p>
    </section>
    <div v-else class="grid">
      <ol>
        <li v-for="item in items" :key="item.id">
          <button
            type="button"
            :class="{ active: selected?.id === item.id }"
            @click="selected = item"
          >
            <strong>{{ item.title }}</strong
            ><span>{{ item.creatorName }} · {{ new Date(item.createdAt).toLocaleString() }}</span>
          </button>
        </li>
      </ol>
      <article v-if="selected">
        <span
          >{{ selected.creatorName }} · {{ new Date(selected.createdAt).toLocaleString() }}</span
        >
        <h2>{{ selected.title }}</h2>
        <p>{{ selected.body }}</p>
      </article>
      <article v-else class="empty">Select a notification to read it.</article>
    </div>
  </main>
</template>
<style scoped>
.notifications {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-5);
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
p,
article span {
  color: var(--vg-text-muted);
}
.empty {
  padding: var(--vg-space-4);
  border: 1px dashed var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
}
.grid {
  display: grid;
  grid-template-columns: minmax(15rem, 0.75fr) 1.25fr;
  gap: var(--vg-space-4);
}
ol {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  overflow: hidden;
}
li + li {
  border-top: 1px solid var(--vg-border);
}
li button {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: var(--vg-space-4);
  border: 0;
  background: var(--vg-surface);
  color: var(--vg-text);
  text-align: left;
  cursor: pointer;
}
li button.active,
li button:hover {
  background: var(--vg-surface-3);
}
li span {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
}
article {
  padding: var(--vg-space-5);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
}
article p {
  white-space: pre-wrap;
  line-height: 1.65;
}
@media (max-width: 720px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
