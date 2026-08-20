<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { projectApi, type TrashedProject } from '@/lib/api'
import { toAccountProject, useAccountStore } from '@/stores/account'
import { useProjectStore } from '@/stores/project'
import { useSilentRefresh } from '@/composables/useSilentRefresh'

const { t, locale } = useI18n({ useScope: 'global' })
const projectStore = useProjectStore(),
  accountStore = useAccountStore()
const items = ref<TrashedProject[]>([]),
  loading = ref(true),
  errorMsg = ref(''),
  busyId = ref(''),
  purgeTarget = ref<TrashedProject | null>(null),
  purging = ref(false)

async function loadTrash() {
  loading.value = true
  try {
    items.value = await projectApi.trash()
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : t('user.trash.loadFallback')
  } finally {
    loading.value = false
  }
}

async function restore(entry: TrashedProject) {
  busyId.value = entry.id
  try {
    await projectApi.restore(entry.id)
    items.value = items.value.filter((item) => item.id !== entry.id)
    // The restored project must reappear in the listings, and both stores cache them.
    invalidateProjectCaches()
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : t('user.trash.restoreFallback')
  } finally {
    busyId.value = ''
  }
}

async function confirmPurge() {
  const entry = purgeTarget.value
  if (!entry) return
  purging.value = true
  try {
    await projectApi.purge(entry.id)
    items.value = items.value.filter((item) => item.id !== entry.id)
    // Purging frees storage, so the quota shown elsewhere is now stale.
    invalidateProjectCaches()
    purgeTarget.value = null
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : t('user.trash.purgeFallback')
  } finally {
    purging.value = false
  }
}

/** Drops the cached project lists so the next visit re-reads them from the server. */
async function invalidateProjectCaches() {
  projectStore.projectsLoaded = false
  try {
    const projects = await projectApi.list()
    projectStore.projects = projects
    projectStore.projectsLoaded = true
    accountStore.setProjects(projects.map(toAccountProject))
  } catch {
    // A stale list is harmless: projectsLoaded is false, so the next visit refetches.
  }
}

function countdown(entry: TrashedProject): string {
  return entry.daysRemaining <= 0
    ? t('user.trash.purgeToday')
    : t('user.trash.daysRemaining', { count: entry.daysRemaining }, entry.daysRemaining)
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString(locale.value)
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let size = bytes / 1024,
    unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${size.toFixed(size < 10 ? 1 : 0)} ${units[unit]}`
}

onMounted(() => {
  void loadTrash()
})

// Kept alive by UserLayout: purges/restores made elsewhere refresh the list on
// re-activation without a reload flash.
useSilentRefresh(async () => {
  try {
    items.value = await projectApi.trash()
    errorMsg.value = ''
  } catch {
    // Keep the cached list; the visible error UI owns failure states.
  }
})
</script>

<template>
  <section class="trash" aria-labelledby="trash-title">
    <header class="page-header">
      <div>
        <span class="eyebrow">{{ t('user.trash.workspace') }}</span>
        <h1 id="trash-title">{{ t('user.trash.title') }}</h1>
        <p>{{ t('user.trash.description') }}</p>
      </div>
    </header>

    <p v-if="errorMsg" class="notice error" role="alert">{{ errorMsg }}</p>

    <p v-if="loading" class="notice" role="status">{{ t('common.loading') }}</p>

    <ul v-else-if="items.length" class="trash-list" :aria-label="t('user.trash.title')">
      <li v-for="entry in items" :key="entry.id" class="trash-card" :data-test="`trash-${entry.id}`">
        <div class="trash-card__identity">
          <h2>{{ entry.name }}</h2>
          <code>{{ entry.id.slice(0, 8) }}</code>
        </div>
        <dl class="trash-card__meta">
          <div>
            <dt>{{ t('user.trash.deletedAt') }}</dt>
            <dd>{{ formatDate(entry.deletedAt) }}</dd>
          </div>
          <div>
            <dt>{{ t('user.trash.size') }}</dt>
            <dd>{{ formatSize(entry.sizeBytes) }}</dd>
          </div>
          <div>
            <dt>{{ t('user.trash.purgeIn') }}</dt>
            <dd :class="{ 'is-imminent': entry.daysRemaining <= 0 }">{{ countdown(entry) }}</dd>
          </div>
        </dl>
        <div class="trash-card__actions">
          <button
            class="restore"
            type="button"
            :disabled="busyId === entry.id"
            :aria-label="t('user.trash.restoreAria', { name: entry.name })"
            @click="restore(entry)"
          >
            <AppIcon name="restore" :size="17" />{{ t('user.trash.restore') }}
          </button>
          <button
            class="purge"
            type="button"
            :aria-label="t('user.trash.purgeAria', { name: entry.name })"
            @click="purgeTarget = entry"
          >
            <AppIcon name="trash" :size="17" />{{ t('user.trash.purge') }}
          </button>
        </div>
      </li>
    </ul>

    <section v-else-if="!errorMsg" class="empty">
      <AppIcon name="trash" :size="30" />
      <h2>{{ t('user.trash.emptyTitle') }}</h2>
      <p>{{ t('user.trash.emptyDescription') }}</p>
    </section>

    <AdminConfirmDialog
      :open="Boolean(purgeTarget)"
      :title="t('user.trash.purgeTitle')"
      :message="t('user.trash.purgeMessage', { name: purgeTarget?.name ?? t('user.trash.title') })"
      :confirm-label="t('user.trash.purgeConfirm')"
      tone="danger"
      :busy="purging"
      @cancel="!purging && (purgeTarget = null)"
      @confirm="confirmPurge"
    />
  </section>
</template>

<style scoped>
.trash {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--vg-space-4);
}
.eyebrow {
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
  margin: 0;
  font-size: var(--vg-text-lg);
}
p {
  color: var(--vg-text-muted);
}
.notice {
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
  color: var(--vg-text-muted);
}
.error {
  color: var(--vg-danger);
}
.trash-list {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}
.trash-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--vg-space-3);
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
  box-shadow: var(--vg-shadow-sm);
}
.trash-card__identity {
  min-width: 0;
  grid-column: 1;
}
.trash-card__identity code {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
}
.trash-card__meta {
  grid-column: 1;
  display: flex;
  flex-wrap: wrap;
  gap: var(--vg-space-3);
  margin: 0;
}
.trash-card__meta dt {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
.trash-card__meta dd {
  margin: 0;
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
}
.trash-card__meta .is-imminent {
  color: var(--vg-danger);
  font-weight: 600;
}
.trash-card__actions {
  grid-column: 2;
  grid-row: 1 / span 2;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.restore,
.purge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 38px;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  font: inherit;
  font-size: var(--vg-text-sm);
  font-weight: 600;
  cursor: pointer;
}
.restore {
  border: 1px solid var(--vg-blue);
  background: var(--vg-blue);
  color: white;
}
.restore:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.purge {
  border: 1px solid var(--vg-border);
  background: transparent;
  color: var(--vg-danger);
}
.purge:hover {
  border-color: var(--vg-danger);
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--vg-space-3);
  padding: var(--vg-space-5) var(--vg-space-3);
  border: 1px dashed var(--vg-border);
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text-muted);
  text-align: center;
}
@media (max-width: 640px) {
  .trash-card {
    grid-template-columns: minmax(0, 1fr);
  }
  .trash-card__actions {
    grid-column: 1;
    grid-row: auto;
  }
}
</style>
