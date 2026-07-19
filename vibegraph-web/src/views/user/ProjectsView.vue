<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ImportProjectPanel from '@/components/projects/ImportProjectPanel.vue'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { projectApi, type Project } from '@/lib/api'
import { useAccountStore } from '@/stores/account'
import { useProjectStore } from '@/stores/project'
import { refreshFeatureAvailability, useFeatureAvailability } from '@/lib/featureAvailability'
import type { Project as AccountProject } from '@/types/api'

const route = useRoute(),
  router = useRouter(),
  projectStore = useProjectStore(),
  accountStore = useAccountStore()
const { t } = useI18n({ useScope: 'global' })
const errorMsg = ref(''),
  showImport = ref(route.query.import === 'new'),
  deleteTarget = ref<Project | null>(null),
  deleting = ref(false)
const local = useFeatureAvailability('import.local'),
  archive = useFeatureAvailability('import.archive'),
  github = useFeatureAvailability('import.github')
const importDisabled = computed(
  () => ![local.value, archive.value, github.value].some((feature) => feature.enabled),
)
const importReason = computed(() =>
  importDisabled.value
    ? t('user.projects.importBlocked')
    : null,
)
const projects = computed(() => projectStore.projects)
watch(
  () => route.query.import,
  (importQuery) => {
    showImport.value = importQuery === 'new'
  },
)

async function loadProjects() {
  if (projectStore.projectsLoaded) {
    syncAccountProjects(projectStore.projects)
    return
  }
  await refreshProjects()
}
async function refreshProjects() {
  try {
    projectStore.projects = await projectApi.list()
    projectStore.projectsLoaded = true
    syncAccountProjects(projectStore.projects)
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : t('user.projects.loadFallback')
  }
}
function open(project: Project) {
  projectStore.currentProjectId = project.id
  projectStore.projectName = project.name
  void router.push({ name: 'graph', params: { projectId: project.id } })
}
function imported(project: Project) {
  showImport.value = false
  projectStore.projects = [
    project,
    ...projectStore.projects.filter((item) => item.id !== project.id),
  ]
  projectStore.projectsLoaded = true
  syncAccountProjects(projectStore.projects)
  open(project)
}
async function confirmDelete() {
  if (!deleteTarget.value) return
  const projectId = deleteTarget.value.id
  deleting.value = true
  try {
    await projectApi.remove(projectId)
    projectStore.projects = projectStore.projects.filter((item) => item.id !== projectId)
    syncAccountProjects(projectStore.projects)
    deleteTarget.value = null
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : t('user.projects.deleteFallback')
  } finally {
    deleting.value = false
  }
}
function relative(value?: string) {
  if (!value) return t('user.projects.notAnalyzed')
  const delta = Date.now() - new Date(value).getTime(),
    days = Math.floor(delta / 86400000)
  if (days > 0) return t('user.projects.daysAgo', { count: days })
  const hours = Math.floor(delta / 3600000)
  return hours > 0 ? t('user.projects.hoursAgo', { count: hours }) : t('user.projects.justNow')
}
function syncAccountProjects(nextProjects: Project[]) {
  accountStore.setProjects(nextProjects.map(toAccountProject))
}
function toAccountProject(project: Project): AccountProject {
  return {
    id: project.id,
    name: project.name,
    sourceType: null,
    sizeBytes: 0,
    status: project.status ?? null,
    createdAt: project.createdAt ?? null,
    updatedAt: project.lastAnalyzedAt ?? project.createdAt ?? null,
    lastAnalyzedAt: project.lastAnalyzedAt ?? null,
  }
}
onMounted(() => {
  void loadProjects()
  void refreshFeatureAvailability().catch(() => undefined)
})
</script>

<template>
  <section class="repositories" aria-labelledby="repositories-title">
    <header class="page-header">
      <div>
        <span class="eyebrow">{{ t('user.projects.workspace') }}</span>
        <h1 id="repositories-title">{{ t('user.projects.title') }}</h1>
        <p>{{ t('user.projects.description') }}</p>
      </div>
      <button
        data-test="new-repository"
        class="primary"
        type="button"
        :disabled="importDisabled"
        :aria-describedby="importDisabled ? 'import-disabled' : undefined"
        @click="showImport = !showImport"
      >
        <AppIcon :name="showImport ? 'close' : 'plus'" />{{
          showImport ? t('user.projects.close') : t('user.projects.newRepository')
        }}
      </button>
    </header>
    <p v-if="importReason" id="import-disabled" class="disabled-note">{{ importReason }}</p>
    <p v-if="errorMsg" class="notice error" role="alert">{{ errorMsg }}</p>
    <section v-if="projects.length" class="repo-grid" :aria-label="t('user.projects.importedRepositories')">
      <article v-for="project in projects" :key="project.id" class="repo-card">
        <div class="repo-card__top">
          <div class="repo-card__identity">
            <h2>{{ project.name }}</h2>
            <code>{{ project.id.slice(0, 8) }}</code>
          </div>
          <span class="status">
            <i :class="`is-${(project.status ?? 'ready').toLowerCase()}`"></i>
            {{ project.status ?? t('user.projects.ready') }}
          </span>
        </div>
        <dl>
          <div>
            <dt>{{ t('user.projects.files') }}</dt>
            <dd>{{ project.totalFiles }}</dd>
          </div>
          <div>
            <dt>{{ t('user.projects.nodes') }}</dt>
            <dd>{{ project.totalNodes }}</dd>
          </div>
          <div class="repo-card__updated">
            <dt>{{ t('user.projects.updated') }}</dt>
            <dd>{{ relative(project.lastAnalyzedAt || project.createdAt) }}</dd>
          </div>
        </dl>
        <div class="repo-card__actions">
          <button
            class="explore"
            type="button"
            :data-test="`open-project-${project.id}`"
            @click="open(project)"
          >
            <AppIcon name="graph" :size="17" />{{ t('user.projects.exploreGraph') }}
          </button>
          <button
            class="icon-button danger"
            type="button"
            :aria-label="t('user.projects.deleteAria', { name: project.name })"
            @click="deleteTarget = project"
          >
            <AppIcon name="trash" :size="17" />
          </button>
        </div>
      </article>
    </section>
    <section v-else-if="!errorMsg" class="empty">
      <AppIcon name="repository" :size="30" />
      <h2>{{ t('user.projects.emptyTitle') }}</h2>
      <p>{{ t('user.projects.emptyDescription') }}</p>
      <button type="button" :disabled="importDisabled" @click="showImport = true">
        {{ t('user.projects.newRepository') }}
      </button>
    </section>
    <div
      v-if="showImport && !importDisabled"
      class="import-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="import-modal-title"
      @click.self="showImport = false"
      @keydown.esc="showImport = false"
    >
      <section class="import-modal__panel">
        <h2 id="import-modal-title" class="sr-only">{{ t('user.projects.importDialogTitle') }}</h2>
        <button
          class="icon-button import-modal__close"
          type="button"
          :aria-label="t('user.projects.closeImportDialog')"
          @click="showImport = false"
        >
          <AppIcon name="close" :size="18" />
        </button>
        <ImportProjectPanel
          :disabled-methods="{
            local: local.enabled ? null : local.reason,
            archive: archive.enabled ? null : archive.reason,
            github: github.enabled ? null : github.reason,
          }"
          @imported="imported"
        />
      </section>
    </div>
    <AdminConfirmDialog
      :open="Boolean(deleteTarget)"
      :title="t('user.projects.deleteTitle')"
      :message="t('user.projects.deleteMessage', { name: deleteTarget?.name ?? t('user.projects.title') })"
      :confirm-label="t('user.projects.deleteConfirm')"
      tone="danger"
      :busy="deleting"
      @cancel="!deleting && (deleteTarget = null)"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.repositories {
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
  font-size: var(--vg-text-lg);
}
p {
  color: var(--vg-text-muted);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
button {
  font: inherit;
}
.primary,
.explore,
.empty button {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.5rem;
  min-height: 38px;
  border: 1px solid var(--vg-blue);
  border-radius: 6px;
  background: var(--vg-blue);
  color: white;
  padding: 0.5rem 0.75rem;
  font-size: var(--vg-text-sm);
  font-weight: 600;
  cursor: pointer;
}
.primary:disabled,
.empty button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.disabled-note,
.notice {
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
  color: var(--vg-warning);
}
.error {
  color: var(--vg-danger);
}
.repo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 360px));
  align-items: start;
  justify-content: start;
  gap: var(--vg-space-3);
}
.repo-card {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
  min-height: 0;
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface);
  box-shadow: var(--vg-shadow-sm);
}
.repo-card:hover {
  border-color: rgba(96, 165, 250, 0.45);
}
.repo-card__top {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--vg-space-3);
}
.repo-card__identity {
  min-width: 0;
  display: grid;
  grid-template-rows: 20px 16px;
  align-items: center;
}
.status {
  display: inline-flex;
  align-items: center;
  align-self: start;
  flex: 0 0 auto;
  gap: 0.35rem;
  height: 20px;
  padding: 0 0.45rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-pill);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  line-height: 1;
  text-transform: capitalize;
}
.status i {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  background: var(--vg-warning);
}
.status i.is-analyzed,
.status i.is-ready {
  background: var(--vg-green-bright);
}
.status i.is-failed {
  background: var(--vg-danger);
}
.icon-button {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--vg-text-muted);
  cursor: pointer;
}
.danger:hover {
  color: var(--vg-danger);
  border-color: color-mix(in srgb, var(--vg-danger) 45%, transparent);
  background: color-mix(in srgb, var(--vg-danger) 8%, transparent);
}
.repo-card h2 {
  overflow: hidden;
  margin: 0 0 0.15rem;
  color: var(--vg-text);
  font-size: var(--vg-text-base);
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.repo-card code {
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
dl {
  display: grid;
  grid-template-columns: 52px 52px minmax(0, 1fr);
  align-items: start;
  gap: var(--vg-space-3);
  margin: 0;
  padding-block: var(--vg-space-2);
  border-block: 1px solid var(--vg-border);
}
dl > div {
  display: grid;
  grid-template-rows: 14px 18px;
  align-items: center;
}
dt {
  font-size: 0.68rem;
  line-height: 14px;
  color: var(--vg-text-dim);
}
dd {
  margin: 0;
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.repo-card__updated {
  min-width: 0;
  text-align: left;
}
.repo-card__updated dd {
  overflow: hidden;
  color: var(--vg-text-muted);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.repo-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-2);
}
.explore {
  min-height: 36px;
  margin: 0;
  padding: 0.4rem 0.65rem;
  background: transparent;
  color: var(--vg-blue-bright);
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: var(--vg-space-6);
  border: 1px dashed var(--vg-border);
  border-radius: var(--vg-radius-lg);
  color: var(--vg-text-muted);
  text-align: left;
}
.empty h2,
.empty p {
  margin-bottom: var(--vg-space-2);
}
.import-modal {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: grid;
  align-items: start;
  justify-items: center;
  overflow-y: auto;
  padding: clamp(1rem, 4vh, 2rem) var(--vg-space-4);
  background: rgba(3, 7, 18, 0.72);
  backdrop-filter: blur(10px);
}
.import-modal__panel {
  position: relative;
  width: min(62rem, 100%);
  display: flex;
  flex-direction: column;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-lg);
  background: color-mix(in srgb, var(--vg-bg) 92%, transparent);
  box-shadow: var(--vg-shadow);
}
.import-modal__panel :deep(.import-panel) {
  border-color: transparent;
  border-radius: var(--vg-radius-lg);
  box-shadow: none;
}
.import-modal__close {
  position: absolute;
  top: var(--vg-space-3);
  right: var(--vg-space-3);
  z-index: 2;
  flex: 0 0 auto;
  border-color: var(--vg-border);
  background: var(--vg-surface);
}
@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
  }
  .primary {
    align-self: stretch;
  }
  .import-modal {
    padding: var(--vg-space-3);
  }
}
</style>
