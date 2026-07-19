<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { accountApi } from '@/lib/api'
import type { UserNotification } from '@/types/api'

const router = useRouter()
const { t } = useI18n({ useScope: 'global' })
const notification = ref<UserNotification | null>(null)
const busy = ref(false)
const errorMsg = ref('')

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
    class="banner banner-error"
    role="alert"
    aria-live="assertive"
  >
    <div class="copy">
      <span>{{ t('nav.announcements') }}</span>
      <strong>{{ t('user.notifications.unavailableTitle') }}</strong>
      <p>{{ errorMsg }}</p>
    </div>
    <div class="actions">
      <button type="button" :disabled="busy" @click="loadActiveNotification">
        {{ busy ? t('user.notifications.loading') : t('common.retry') }}
      </button>
    </div>
  </aside>
  <aside
    v-else-if="notification"
    class="banner"
    :class="`severity-${notification.severity.toLowerCase()}`"
    aria-live="polite"
  >
    <div class="copy">
      <span>{{ notification.type.replace(/_/g, ' ') }}</span>
      <strong>{{ notification.title }}</strong>
      <p>{{ notification.body }}</p>
      <small v-if="errorMsg" role="alert">{{ errorMsg }}</small>
    </div>
    <div class="actions">
      <button type="button" :disabled="busy" @click="read">{{ t('common.view') }}</button>
      <button
        v-if="notification.dismissible"
        type="button"
        :disabled="busy"
        @click="close"
      >
        {{ t('common.close') }}
      </button>
    </div>
  </aside>
</template>

<style scoped>
.banner {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--vg-space-4);
  margin-bottom: var(--vg-space-3);
  padding: var(--vg-space-4);
  border: 1px solid rgba(96, 165, 250, 0.46);
  border-left: 4px solid var(--vg-blue-bright);
  border-radius: var(--vg-radius);
  background: linear-gradient(110deg, rgba(59, 130, 246, 0.17), rgba(59, 130, 246, 0.06));
  color: var(--vg-text);
}
.severity-warning {
  border-color: color-mix(in srgb, var(--vg-warning) 48%, var(--vg-border));
  border-left-color: var(--vg-warning);
  background: color-mix(in srgb, var(--vg-warning) 10%, var(--vg-surface));
}
.severity-critical {
  border-color: color-mix(in srgb, var(--vg-danger) 52%, var(--vg-border));
  border-left-color: var(--vg-danger);
  background: color-mix(in srgb, var(--vg-danger) 10%, var(--vg-surface));
}
.banner-error {
  border-color: color-mix(in srgb, var(--vg-danger) 52%, var(--vg-border));
  border-left-color: var(--vg-danger);
  background: color-mix(in srgb, var(--vg-danger) 9%, var(--vg-surface));
}
.copy {
  min-width: 0;
}
.copy > span {
  display: block;
  margin-bottom: 0.25rem;
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.copy strong {
  display: block;
  font: 700 var(--vg-text-base) var(--vg-font-display);
}
p {
  max-width: 72ch;
  margin: 0.35rem 0 0;
  color: var(--vg-text-muted);
  line-height: 1.5;
}
small {
  display: block;
  margin-top: var(--vg-space-2);
  color: var(--vg-danger);
}
.actions {
  display: flex;
  flex: 0 0 auto;
  gap: var(--vg-space-2);
}
button {
  min-height: 38px;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
  color: var(--vg-text);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
button:disabled {
  opacity: 0.55;
  cursor: wait;
}
@media (max-width: 620px) {
  .banner {
    align-items: stretch;
    flex-direction: column;
  }
  .actions button {
    flex: 1;
  }
}
</style>
