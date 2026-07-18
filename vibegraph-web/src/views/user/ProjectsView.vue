<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ImportProjectPanel from '@/components/projects/ImportProjectPanel.vue'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { projectApi, type Project } from '@/lib/api'
import { useProjectStore } from '@/stores/project'
import { refreshFeatureAvailability, useFeatureAvailability } from '@/lib/featureAvailability'

const route = useRoute(),
  router = useRouter(),
  projectStore = useProjectStore()
const projects = ref<Project[]>([]),
  errorMsg = ref(''),
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
    ? 'Repository import is blocked until the account capability contract reports an enabled method.'
    : null,
)
watch(
  () => route.query.import,
  (importQuery) => {
    showImport.value = importQuery === 'new'
  },
)

async function loadProjects() {
  try {
    projects.value = await projectApi.list()
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to load repositories.'
  }
}
function open(project: Project) {
  projectStore.currentProjectId = project.id
  projectStore.projectName = project.name
  void router.push({ name: 'graph', params: { projectId: project.id } })
}
function imported(project: Project) {
  showImport.value = false
  open(project)
}
async function confirmDelete() {
  if (!deleteTarget.value) return
  const projectId = deleteTarget.value.id
  deleting.value = true
  try {
    await projectApi.remove(projectId)
    projects.value = projects.value.filter((item) => item.id !== projectId)
    deleteTarget.value = null
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to delete repository.'
  } finally {
    deleting.value = false
  }
}
function relative(value?: string) {
  if (!value) return 'Not analyzed yet'
  const delta = Date.now() - new Date(value).getTime(),
    days = Math.floor(delta / 86400000)
  if (days > 0) return `${days}d ago`
  const hours = Math.floor(delta / 3600000)
  return hours > 0 ? `${hours}h ago` : 'Just now'
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
        <span class="eyebrow">Workspace</span>
        <h1 id="repositories-title">Repositories</h1>
        <p>Imported Java projects, ready for graph exploration.</p>
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
          showImport ? 'Close' : 'New Repository'
        }}
      </button>
    </header>
    <p v-if="importReason" id="import-disabled" class="disabled-note">{{ importReason }}</p>
    <p v-if="errorMsg" class="notice error" role="alert">{{ errorMsg }}</p>
    <section v-if="projects.length" class="repo-grid" aria-label="Imported repositories">
      <article v-for="project in projects" :key="project.id" class="repo-card">
        <div class="repo-card__top">
          <div class="repo-card__identity">
            <h2>{{ project.name }}</h2>
            <code>{{ project.id.slice(0, 8) }}</code>
          </div>
          <span class="status">
            <i :class="`is-${(project.status ?? 'ready').toLowerCase()}`"></i>
            {{ project.status ?? 'Ready' }}
          </span>
        </div>
        <dl>
          <div>
            <dt>Files</dt>
            <dd>{{ project.totalFiles }}</dd>
          </div>
          <div>
            <dt>Nodes</dt>
            <dd>{{ project.totalNodes }}</dd>
          </div>
          <div class="repo-card__updated">
            <dt>Updated</dt>
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
            <AppIcon name="graph" :size="17" />Explore Graph
          </button>
          <button
            class="icon-button danger"
            type="button"
            :aria-label="`Delete ${project.name}`"
            @click="deleteTarget = project"
          >
            <AppIcon name="trash" :size="17" />
          </button>
        </div>
      </article>
    </section>
    <section v-else-if="!errorMsg" class="empty">
      <AppIcon name="repository" :size="30" />
      <h2>No repositories yet</h2>
      <p>Import your first Java project to build its graph.</p>
      <button type="button" :disabled="importDisabled" @click="showImport = true">
        New Repository
      </button>
    </section>
    <section v-if="showImport && !importDisabled" class="import-section">
      <ImportProjectPanel
        :disabled-methods="{
          local: local.enabled ? null : local.reason,
          archive: archive.enabled ? null : archive.reason,
          github: github.enabled ? null : github.reason,
        }"
        @imported="imported"
      />
    </section>
    <AdminConfirmDialog
      :open="Boolean(deleteTarget)"
      title="Delete repository"
      :message="`Delete ${deleteTarget?.name ?? 'this repository'}? This cannot be undone.`"
      confirm-label="Delete repository"
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
.import-section {
  max-width: 62rem;
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
@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
  }
  .primary {
    align-self: stretch;
  }
}
</style>
