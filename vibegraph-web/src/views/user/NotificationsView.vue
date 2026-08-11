<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'
import { ApiError, accountApi } from '@/lib/api'
import type { UserNotification } from '@/types/api'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n({ useScope: 'global' })
const items = ref<UserNotification[]>([])
const selected = ref<UserNotification | null>(null)
const available = ref(true)
const loading = ref(true)
const busyId = ref<string | null>(null)
const errorMsg = ref('')
let selectionVersion = 0

const unreadCount = computed(() => items.value.filter((item) => !item.read).length)

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
  return (
    item.creatorDisplayName ||
    item.creatorName ||
    item.creatorEmail ||
    t('user.notifications.creatorFallback')
  )
}

function severityOf(item: UserNotification): string {
  return item.severity.toLowerCase()
}

function severityIcon(item: UserNotification): string {
  const level = severityOf(item)
  return level === 'critical' ? 'critical' : level === 'warning' ? 'warning' : 'info'
}

/** Compact relative time for the list; the full timestamp stays in `title`/`datetime`. */
function relativeTime(iso: string): string {
  const target = Date.parse(iso)
  if (Number.isNaN(target)) return ''
  const diffSeconds = Math.round((target - Date.now()) / 1000)
  const absolute = Math.abs(diffSeconds)
  if (absolute < 60) return t('user.notifications.relativeNow')

  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31_536_000],
    ['month', 2_592_000],
    ['day', 86_400],
    ['hour', 3600],
    ['minute', 60],
  ]
  const formatter = new Intl.RelativeTimeFormat(locale.value, { numeric: 'auto' })
  for (const [unit, seconds] of units) {
    if (absolute >= seconds) return formatter.format(Math.round(diffSeconds / seconds), unit)
  }
  return t('user.notifications.relativeNow')
}

function fullTime(iso: string): string {
  const parsed = new Date(iso)
  return Number.isNaN(parsed.getTime()) ? '' : parsed.toLocaleString(locale.value)
}
</script>

<template>
  <section class="notifications" aria-labelledby="notifications-title">
    <header class="page-head">
      <div>
        <span class="eyebrow">{{ t('user.notifications.inbox') }}</span>
        <h1 id="notifications-title">{{ t('user.notifications.title') }}</h1>
        <p class="lede">{{ t('user.notifications.description') }}</p>
      </div>
      <span v-if="unreadCount" class="unread-pill">
        {{ t('user.notifications.unreadCount', { count: unreadCount }) }}
      </span>
    </header>

    <p v-if="errorMsg" class="notice error" role="alert">{{ errorMsg }}</p>

    <section v-if="loading" class="state" aria-busy="true">
      <span class="state-icon"><AppIcon name="inbox" :size="22" /></span>
      <p>{{ t('user.notifications.loading') }}</p>
    </section>

    <section v-else-if="!available" class="state">
      <span class="state-icon"><AppIcon name="warning" :size="22" /></span>
      <h2>{{ t('user.notifications.unavailableTitle') }}</h2>
      <p>{{ t('user.notifications.unavailableDescription') }}</p>
    </section>

    <section v-else-if="!items.length" class="state">
      <span class="state-icon"><AppIcon name="check" :size="22" /></span>
      <h2>{{ t('user.notifications.emptyTitle') }}</h2>
      <p>{{ t('user.notifications.emptyDescription') }}</p>
    </section>

    <div v-else class="grid">
      <ol class="list" :aria-label="t('user.notifications.listLabel')">
        <li v-for="item in items" :key="item.id">
          <button
            type="button"
            class="row"
            :class="[`sev-${severityOf(item)}`, { active: selected?.id === item.id }]"
            :aria-current="selected?.id === item.id ? 'true' : undefined"
            :disabled="busyId === item.id"
            @click="selectNotification(item)"
          >
            <span class="row-glyph"><AppIcon :name="severityIcon(item)" :size="16" /></span>
            <span class="row-main">
              <span class="row-title">
                <span class="text">{{ item.title }}</span>
                <span v-if="!item.read" class="dot" :aria-label="t('user.notifications.newBadge')" />
              </span>
              <span class="row-meta">
                <span class="who">{{ creatorLabel(item) }}</span>
                <time :datetime="item.createdAt" :title="fullTime(item.createdAt)">
                  {{ relativeTime(item.createdAt) }}
                </time>
              </span>
            </span>
          </button>
        </li>
      </ol>

      <article
        v-if="selected"
        class="detail"
        :class="`sev-${severityOf(selected)}`"
        :aria-label="t('user.notifications.detailLabel')"
      >
        <div class="detail-head">
          <span class="detail-glyph"><AppIcon :name="severityIcon(selected)" :size="18" /></span>
          <div class="detail-heading">
            <span class="kind">{{ selected.type.replace(/_/g, ' ') }}</span>
            <h2>{{ selected.title }}</h2>
          </div>
        </div>

        <div class="detail-meta">
          <span class="who">{{ creatorLabel(selected) }}</span>
          <span class="sep" aria-hidden="true">·</span>
          <time :datetime="selected.createdAt" :title="fullTime(selected.createdAt)">
            {{ fullTime(selected.createdAt) }}
          </time>
        </div>

        <p class="detail-body">{{ selected.body }}</p>

        <footer v-if="selected.dismissible" class="detail-foot">
          <button
            type="button"
            class="dismiss"
            :disabled="busyId === selected.id"
            @click="dismissSelected"
          >
            {{
              busyId === selected.id
                ? t('user.notifications.updating')
                : t('user.notifications.dismiss')
            }}
          </button>
        </footer>
      </article>

      <article v-else class="detail detail-empty">
        <p>{{ t('user.notifications.selectPrompt') }}</p>
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

/* ---- Page header ---- */
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--vg-space-4);
}
.eyebrow,
.kind {
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
h1 {
  margin: 0.3rem 0 0;
  font-size: clamp(1.5rem, 2vw, 1.75rem);
  color: var(--vg-text);
}
.lede {
  max-width: 62ch;
  margin: 0.35rem 0 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}
.unread-pill {
  flex: 0 0 auto;
  padding: 0.25rem 0.65rem;
  border: 1px solid color-mix(in srgb, var(--vg-blue-bright) 40%, transparent);
  border-radius: var(--vg-radius-pill);
  background: color-mix(in srgb, var(--vg-blue-bright) 14%, transparent);
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* ---- Notices and empty/loading states ---- */
.notice {
  padding: var(--vg-space-3) var(--vg-space-4);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
  font-size: var(--vg-text-sm);
}
.error {
  border-color: color-mix(in srgb, var(--vg-danger) 45%, transparent);
  color: var(--vg-danger);
}
.state {
  display: grid;
  justify-items: center;
  gap: var(--vg-space-2);
  padding: var(--vg-space-12) var(--vg-space-4);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
  text-align: center;
}
.state-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-bottom: var(--vg-space-1);
  border-radius: var(--vg-radius-pill);
  background: var(--vg-surface-3);
  color: var(--vg-text-muted);
}
.state h2 {
  margin: 0;
  font-size: var(--vg-text-lg);
  color: var(--vg-text);
}
.state p {
  max-width: 46ch;
  margin: 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

/* ---- Two-pane layout ---- */
.grid {
  display: grid;
  grid-template-columns: minmax(16rem, 0.75fr) minmax(0, 1.25fr);
  align-items: start;
  gap: var(--vg-space-4);
}

/* ---- List ---- */
.list {
  max-height: calc(100vh - 14rem);
  margin: 0;
  padding: var(--vg-space-1);
  overflow-y: auto;
  list-style: none;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
}
.row {
  --sev: var(--vg-blue-bright);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--vg-space-3);
  width: 100%;
  padding: var(--vg-space-3);
  border: 0;
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-text);
  text-align: left;
  cursor: pointer;
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out);
}
.row:hover:not(:disabled) {
  background: var(--vg-surface-2);
}
.row.active {
  background: var(--vg-surface-3);
}
.row:disabled {
  cursor: progress;
}
.sev-warning {
  --sev: var(--vg-warning);
}
.sev-critical {
  --sev: var(--vg-danger);
}
.row-glyph {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--sev) 14%, transparent);
  color: var(--sev);
}
.row-main {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}
.row-title {
  display: flex;
  align-items: center;
  gap: var(--vg-space-2);
  min-width: 0;
}
.row-title .text {
  overflow: hidden;
  font-size: var(--vg-text-sm);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* Unread marker: a dot, so "new" is not signalled by colour alone. */
.dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: var(--vg-radius-pill);
  background: var(--vg-blue-bright);
}
.row-meta {
  display: flex;
  align-items: center;
  gap: var(--vg-space-2);
  min-width: 0;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.row-meta .who {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-meta time {
  flex: 0 0 auto;
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

/* ---- Detail ---- */
.detail {
  --sev: var(--vg-blue-bright);
  align-self: start;
  padding: var(--vg-space-5);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
}
.detail-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--vg-space-3);
}
.detail-glyph {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--sev) 14%, transparent);
  color: var(--sev);
}
.detail-heading .kind {
  color: var(--sev);
}
.detail-heading h2 {
  margin: 0.2rem 0 0;
  font-size: var(--vg-text-lg);
  color: var(--vg-text);
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--vg-space-2);
  margin-top: var(--vg-space-3);
  padding-bottom: var(--vg-space-4);
  border-bottom: 1px solid var(--vg-border);
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.detail-meta time {
  font-variant-numeric: tabular-nums;
}
.sep {
  color: var(--vg-border-strong);
}
.detail-body {
  margin: var(--vg-space-4) 0 0;
  max-width: 70ch;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.7;
  white-space: pre-wrap;
}
.detail-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--vg-space-5);
  padding-top: var(--vg-space-4);
  border-top: 1px solid var(--vg-border);
}
.dismiss {
  min-height: 36px;
  padding: 0 var(--vg-space-4);
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-text);
  font: inherit;
  font-size: var(--vg-text-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out);
}
.dismiss:hover:not(:disabled) {
  background: var(--vg-surface-3);
}
.dismiss:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.detail-empty {
  display: grid;
  place-items: center;
  min-height: 12rem;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-sm);
}

@media (max-width: 860px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .list {
    max-height: 22rem;
  }
  .page-head {
    flex-direction: column;
  }
}
</style>
