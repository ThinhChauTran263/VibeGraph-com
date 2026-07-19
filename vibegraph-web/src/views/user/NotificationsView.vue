<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, accountApi } from '@/lib/api'
import type { UserNotification } from '@/types/api'

const route = useRoute()
const router = useRouter()
const { t } = useI18n({ useScope: 'global' })
const items = ref<UserNotification[]>([])
const selected = ref<UserNotification | null>(null)
const available = ref(true)
const loading = ref(true)
const busyId = ref<string | null>(null)
const errorMsg = ref('')
let selectionVersion = 0

onMounted(loadNotifications)

async function loadNotifications(): Promise<void> {
  try {
    items.value = (await accountApi.listNotifications()).sort(
      (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt),
    )
    const requestedId = typeof route.query.id === 'string' ? route.query.id : null
    const initial = items.value.find((item) => item.id === requestedId) ?? items.value[0] ?? null
    if (initial) await selectNotification(initial)
  } catch (error) {
    if (error instanceof ApiError && [404, 405, 501].includes(error.status)) {
      available.value = false
    } else {
      errorMsg.value = error instanceof Error ? error.message : t('user.notifications.loadFallback')
    }
  } finally {
    loading.value = false
  }
}

async function selectNotification(item: UserNotification): Promise<void> {
  const currentSelectionVersion = ++selectionVersion
  selected.value = item
  await router.replace({ name: 'notifications', query: { id: item.id } })
  if (item.read) return
  busyId.value = item.id
  try {
    const updated = await accountApi.markNotificationRead(item.id)
    replaceNotification(updated)
    if (currentSelectionVersion === selectionVersion) selected.value = updated
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : t('user.notifications.readFallback')
  } finally {
    if (busyId.value === item.id) busyId.value = null
  }
}

async function dismissSelected(): Promise<void> {
  if (!selected.value || busyId.value) return
  const id = selected.value.id
  busyId.value = id
  try {
    await accountApi.dismissNotification(id)
    items.value = items.value.filter((item) => item.id !== id)
    selected.value = items.value[0] ?? null
    await router.replace({
      name: 'notifications',
      query: selected.value ? { id: selected.value.id } : {},
    })
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : t('user.notifications.dismissFallback')
  } finally {
    busyId.value = null
  }
}

function replaceNotification(updated: UserNotification): void {
  items.value = items.value.map((item) => (item.id === updated.id ? updated : item))
}

function creatorLabel(item: UserNotification): string {
  return item.creatorDisplayName || item.creatorName || item.creatorEmail || t('user.notifications.creatorFallback')
}
</script>

<template>
  <section class="notifications" aria-labelledby="notifications-title">
    <header>
      <span>{{ t('user.notifications.inbox') }}</span>
      <h1 id="notifications-title">{{ t('user.notifications.title') }}</h1>
      <p>{{ t('user.notifications.description') }}</p>
    </header>
    <p v-if="errorMsg" class="notice error" role="alert">{{ errorMsg }}</p>
    <section v-if="loading" class="empty">{{ t('user.notifications.loading') }}</section>
    <section v-else-if="!available" class="empty">
      <h2>{{ t('user.notifications.unavailableTitle') }}</h2>
      <p>{{ t('user.notifications.unavailableDescription') }}</p>
    </section>
    <section v-else-if="!items.length" class="empty">
      <h2>{{ t('user.notifications.emptyTitle') }}</h2>
      <p>{{ t('user.notifications.emptyDescription') }}</p>
    </section>
    <div v-else class="grid">
      <ol :aria-label="t('user.notifications.listLabel')">
        <li v-for="item in items" :key="item.id">
          <button
            type="button"
            :class="{ active: selected?.id === item.id, unread: !item.read }"
            :aria-current="selected?.id === item.id ? 'true' : undefined"
            :disabled="busyId === item.id"
            @click="selectNotification(item)"
          >
            <span class="list-heading">
              <i :class="`severity-${item.severity.toLowerCase()}`">{{ item.severity }}</i>
              <strong>{{ item.title }}</strong>
            </span>
            <span>{{ creatorLabel(item) }} - {{ new Date(item.createdAt).toLocaleString() }}</span>
          </button>
        </li>
      </ol>
      <article v-if="selected" class="detail">
        <div class="detail-meta">
          <span>{{ creatorLabel(selected) }}</span>
          <time :datetime="selected.createdAt">{{ new Date(selected.createdAt).toLocaleString() }}</time>
        </div>
        <span class="detail-type">{{ selected.type.replace(/_/g, ' ') }}</span>
        <h2>{{ selected.title }}</h2>
        <p>{{ selected.body }}</p>
        <button
          v-if="selected.dismissible"
          type="button"
          class="dismiss"
          :disabled="busyId === selected.id"
          @click="dismissSelected"
        >
          {{ busyId === selected.id ? t('user.notifications.updating') : t('user.notifications.dismiss') }}
        </button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.notifications {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-5);
}
header > span,
.detail-type {
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
  margin: 0.45rem 0 var(--vg-space-3);
  font-size: var(--vg-text-lg);
}
p,
.detail-meta {
  color: var(--vg-text-muted);
}
.notice,
.empty {
  padding: var(--vg-space-4);
  border: 1px dashed var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
}
.error {
  color: var(--vg-danger);
}
.grid {
  display: grid;
  grid-template-columns: minmax(17rem, 0.8fr) minmax(0, 1.2fr);
  gap: var(--vg-space-4);
}
ol {
  max-height: calc(100vh - 13rem);
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
}
li + li {
  border-top: 1px solid var(--vg-border);
}
li button {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: var(--vg-space-4);
  border: 0;
  border-left: 3px solid transparent;
  background: var(--vg-surface);
  color: var(--vg-text);
  text-align: left;
  cursor: pointer;
}
li button.unread {
  border-left-color: var(--vg-blue-bright);
  background: color-mix(in srgb, var(--vg-blue) 7%, var(--vg-surface));
}
li button.active,
li button:hover {
  background: var(--vg-surface-3);
}
li button:disabled {
  cursor: wait;
}
.list-heading {
  display: flex;
  align-items: center;
  gap: var(--vg-space-2);
}
.list-heading i {
  flex: 0 0 auto;
  padding: 0.18rem 0.4rem;
  border-radius: var(--vg-radius-pill);
  background: rgba(96, 165, 250, 0.12);
  color: var(--vg-blue-bright);
  font-size: 0.65rem;
  font-style: normal;
  font-weight: 800;
}
.list-heading .severity-warning {
  color: var(--vg-warning);
}
.list-heading .severity-critical {
  color: var(--vg-danger);
}
li button > span:last-child {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
}
.detail {
  align-self: start;
  padding: var(--vg-space-5);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
}
.detail-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--vg-space-3);
  margin-bottom: var(--vg-space-4);
  font-size: var(--vg-text-sm);
}
.detail p {
  white-space: pre-wrap;
  line-height: 1.7;
}
.dismiss {
  min-height: 38px;
  margin-top: var(--vg-space-4);
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-text);
  font: inherit;
  cursor: pointer;
}
@media (max-width: 720px) {
  .grid {
    grid-template-columns: 1fr;
  }
  ol {
    max-height: 40vh;
  }
  .detail-meta {
    flex-direction: column;
  }
}
</style>
