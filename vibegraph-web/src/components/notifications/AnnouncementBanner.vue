<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'
import { accountApi } from '@/lib/api'
import type { UserNotification } from '@/types/api'

const router = useRouter()
const { t } = useI18n({ useScope: 'global' })
const notification = ref<UserNotification | null>(null)
const busy = ref(false)
const errorMsg = ref('')

/** Severity drives the accent colour and the icon, so meaning is never colour-only. */
const severity = computed(() => (notification.value?.severity ?? 'INFO').toLowerCase())
const severityIcon = computed(() =>
  severity.value === 'critical' ? 'critical' : severity.value === 'warning' ? 'warning' : 'info',
)
const typeLabel = computed(() => notification.value?.type.replace(/_/g, ' ') ?? '')

onMounted(loadActiveNotification)

async function loadActiveNotification(): Promise<void> {
  busy.value = true
  errorMsg.value = ''
  try {
    const items = await accountApi.listNotifications(20)
    notification.value =
      items
        .filter((item) => !item.read && !item.dismissedAt)
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))[0] ?? null
  } catch {
    errorMsg.value = t('user.notifications.announcementUnavailable')
  } finally {
    busy.value = false
  }
}

async function close(): Promise<void> {
  if (!notification.value || busy.value) return
  busy.value = true
  errorMsg.value = ''
  try {
    await accountApi.dismissNotification(notification.value.id)
    notification.value = null
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : t('user.notifications.dismissFallback')
  } finally {
    busy.value = false
  }
}

async function read(): Promise<void> {
  if (!notification.value || busy.value) return
  const id = notification.value.id
  busy.value = true
  errorMsg.value = ''
  try {
    await accountApi.markNotificationRead(id)
    notification.value.read = true
    await router.push({ name: 'notifications', query: { id } })
    notification.value = null
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : t('user.notifications.readFallback')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <aside
    v-if="errorMsg && !notification"
    class="banner is-error"
    role="alert"
    aria-live="assertive"
  >
    <span class="glyph"><AppIcon name="warning" :size="18" /></span>
    <div class="body">
      <p class="headline">{{ t('user.notifications.unavailableTitle') }}</p>
      <p class="detail">{{ errorMsg }}</p>
    </div>
    <div class="actions">
      <button type="button" class="btn btn-ghost" :disabled="busy" @click="loadActiveNotification">
        {{ busy ? t('user.notifications.loading') : t('common.retry') }}
      </button>
    </div>
  </aside>

  <aside v-else-if="notification" class="banner" :class="`is-${severity}`" aria-live="polite">
    <span class="glyph"><AppIcon :name="severityIcon" :size="18" /></span>

    <div class="body">
      <p class="headline">
        <span class="kind">{{ typeLabel }}</span>
        <span class="title">{{ notification.title }}</span>
      </p>
      <p class="detail">{{ notification.body }}</p>
      <p v-if="errorMsg" class="failure" role="alert">{{ errorMsg }}</p>
    </div>

    <div class="actions">
      <button type="button" class="btn btn-primary" :disabled="busy" @click="read">
        {{ t('common.view') }}
      </button>
      <button
        v-if="notification.dismissible"
        type="button"
        class="btn btn-icon"
        :disabled="busy"
        :aria-label="t('common.close')"
        :title="t('common.close')"
        @click="close"
      >
        <AppIcon name="close" :size="16" />
      </button>
    </div>
  </aside>
</template>

<style scoped>
.banner {
  --accent: var(--vg-blue-bright);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--vg-space-3);
  margin-bottom: var(--vg-space-4);
  padding: var(--vg-space-3) var(--vg-space-4);
  border: 1px solid var(--vg-border);
  border-left: 3px solid var(--accent);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
  box-shadow: var(--vg-shadow-sm);
  animation: banner-in var(--vg-dur) var(--vg-ease-out);
}
.is-warning {
  --accent: var(--vg-warning);
}
.is-critical,
.is-error {
  --accent: var(--vg-danger);
}

/* Icon carries the severity so meaning is not conveyed by colour alone. */
.glyph {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
}

.body {
  min-width: 0;
  padding-top: 0.15rem;
}
.headline {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--vg-space-2);
  margin: 0;
}
.kind {
  flex: 0 0 auto;
  color: var(--accent);
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}
.title {
  min-width: 0;
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-sm);
  font-weight: 600;
  color: var(--vg-text);
}
.detail {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  max-width: 78ch;
  margin: 0.2rem 0 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.5;
}
.failure {
  margin: var(--vg-space-2) 0 0;
  color: var(--vg-danger);
  font-size: var(--vg-text-xs);
}

.actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--vg-space-2);
}
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--vg-space-2);
  min-height: 34px;
  padding: 0 var(--vg-space-3);
  border: 1px solid transparent;
  border-radius: var(--vg-radius-sm);
  font: inherit;
  font-size: var(--vg-text-sm);
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color var(--vg-dur-fast) var(--vg-ease-out),
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    color var(--vg-dur-fast) var(--vg-ease-out);
}
.btn-primary {
  background: color-mix(in srgb, var(--accent) 18%, transparent);
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  color: var(--vg-text);
}
.btn-primary:hover:not(:disabled) {
  background: color-mix(in srgb, var(--accent) 28%, transparent);
}
.btn-ghost {
  border-color: var(--vg-border-strong);
  background: transparent;
  color: var(--vg-text);
}
.btn-ghost:hover:not(:disabled) {
  background: var(--vg-surface-3);
}
/* Icon-only control keeps a 40px hit area while looking compact. */
.btn-icon {
  width: 34px;
  min-width: 34px;
  padding: 0;
  background: transparent;
  color: var(--vg-text-muted);
}
.btn-icon:hover:not(:disabled) {
  background: var(--vg-surface-3);
  color: var(--vg-text);
}
.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@keyframes banner-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 640px) {
  .banner {
    grid-template-columns: auto minmax(0, 1fr);
  }
  .actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}
</style>
