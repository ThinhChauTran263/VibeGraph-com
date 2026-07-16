<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, api } from '@/lib/api'
interface Announcement {
  id: string
  title: string
  body: string
  dismissible?: boolean
}
const router = useRouter(),
  announcement = ref<Announcement | null>(null)
onMounted(async () => {
  try {
    const items = await api.get<Announcement[]>('/api/account/announcements')
    announcement.value =
      items.find(
        (item) => localStorage.getItem(`vg_announcement_dismissed_${item.id}`) !== 'true',
      ) ?? null
  } catch (e) {
    if (!(e instanceof ApiError && [404, 405, 501].includes(e.status)))
      console.warn('Announcement banner unavailable')
  }
})
function close() {
  if (!announcement.value) return
  localStorage.setItem(`vg_announcement_dismissed_${announcement.value.id}`, 'true')
  announcement.value = null
}
function read() {
  if (!announcement.value) return
  void router.push({ name: 'notifications', query: { id: announcement.value.id } })
  close()
}
</script>
<template>
  <aside v-if="announcement" class="banner" aria-live="polite">
    <div>
      <strong>{{ announcement.title }}</strong>
      <p>{{ announcement.body }}</p>
    </div>
    <div class="actions">
      <button type="button" @click="read">Read</button
      ><button v-if="announcement.dismissible !== false" type="button" @click="close">Close</button>
    </div>
  </aside>
</template>
<style scoped>
.banner {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--vg-space-3);
  margin-bottom: var(--vg-space-3);
  padding: var(--vg-space-3);
  border: 1px solid rgba(96, 165, 250, 0.38);
  border-radius: var(--vg-radius);
  background: rgba(59, 130, 246, 0.1);
  color: var(--vg-text);
}
p {
  margin: 0.3rem 0 0;
  color: var(--vg-text-muted);
}
.actions {
  display: flex;
  gap: var(--vg-space-2);
}
button {
  min-height: 38px;
  padding: 0.45rem 0.7rem;
  text-align: left;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
  color: var(--vg-text);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
@media (max-width: 620px) {
  .banner {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
