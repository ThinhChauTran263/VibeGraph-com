<script setup lang="ts">
/**
 * HomeView - landing page for project management.
 *
 * Shows the three import flows plus a list of already-imported projects so a
 * project is always reachable — even if an import's progress poll timed out
 * (the analysis still finishes on the backend and the project shows up here).
 */

import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AddProjectArchive from '@/components/projects/AddProjectArchive.vue'
import AddProjectLocal from '@/components/projects/AddProjectLocal.vue'
import GitHubImportForm from '@/components/projects/GitHubImportForm.vue'
import { useProjectStore } from '@/stores/project'
import { projectApi, type Project } from '@/lib/api'

const router = useRouter()
const projectStore = useProjectStore()

const projects = ref<Project[]>([])
const loadingProjects = ref(false)
const projectsError = ref<string | null>(null)
const deletingId = ref<string | null>(null)
const clearingAll = ref(false)

const hasProjects = computed(() => projects.value.length > 0)

// While the user sits on this page, silently re-fetch the list so a project still ANALYZING on the
// backend (e.g. an import whose in-page progress poll dropped) appears — and flips to ANALYZED —
// without anyone pressing Refresh/F5. The list call is cheap and the tab is gated on visibility.
const AUTO_REFRESH_INTERVAL_MS = 5_000
let refreshTimer: ReturnType<typeof setInterval> | null = null

async function loadProjects(): Promise<void> {
  loadingProjects.value = true
  projectsError.value = null
  try {
    projects.value = await projectApi.list()
  } catch {
    projectsError.value = 'Could not load projects. Is the backend running?'
  } finally {
    loadingProjects.value = false
  }
}

// Background refresh used by the timer: same fetch, but it must not flip the visible "Loading…"
// state or clobber an error the user is reading, so it updates the list quietly and stays silent
// on failure (the next manual Refresh surfaces any real problem).
async function refreshProjectsQuietly(): Promise<void> {
  if (loadingProjects.value) return
  try {
    projects.value = await projectApi.list()
  } catch {
    // Transient failure — leave the current list and error state untouched.
  }
}

function startAutoRefresh(): void {
  if (refreshTimer !== null) return
  refreshTimer = setInterval(() => {
    // Don't poll a backgrounded tab; it resumes on the next tick once visible again.
    if (typeof document !== 'undefined' && document.hidden) return
    void refreshProjectsQuietly()
  }, AUTO_REFRESH_INTERVAL_MS)
}

function stopAutoRefresh(): void {
  if (refreshTimer !== null) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function openProject(project: Project): void {
  projectStore.currentProjectId = project.id
  projectStore.projectName = project.name
  router.push({ name: 'graph', params: { projectId: project.id } })
}

async function removeProject(project: Project): Promise<void> {
  if (deletingId.value) return
  deletingId.value = project.id
  try {
    await projectApi.remove(project.id)
    projects.value = projects.value.filter((p) => p.id !== project.id)
  } catch {
    projectsError.value = `Could not delete "${project.name}".`
  } finally {
    deletingId.value = null
  }
}

async function clearAll(): Promise<void> {
  if (clearingAll.value || !hasProjects.value) return
  if (!window.confirm(`Delete all ${projects.value.length} projects? This cannot be undone.`)) return
  clearingAll.value = true
  projectsError.value = null
  // Snapshot the ids up front; each delete reassigns projects.value, so iterating a copy of the
  // ids avoids mutating the list we're walking.
  const ids = projects.value.map((p) => p.id)
  for (const id of ids) {
    try {
      await projectApi.remove(id)
      projects.value = projects.value.filter((p) => p.id !== id)
    } catch {
      projectsError.value = 'Some projects could not be deleted. Try Refresh, then clear again.'
    }
  }
  clearingAll.value = false
}

function onImported(project: Project): void {
  projectStore.currentProjectId = project.id
  projectStore.projectName = project.name
  router.push({ name: 'graph', params: { projectId: project.id } })
}

onMounted(() => {
  void loadProjects()
  startAutoRefresh()
})
// Refresh when navigating back from the graph view (e.g. after a delete or a new import), and
// resume the background poll. Stop the poll while the view is inactive or torn down so it never
// runs against a hidden/destroyed page.
onActivated(() => {
  void loadProjects()
  startAutoRefresh()
})
onDeactivated(stopAutoRefresh)
onUnmounted(stopAutoRefresh)
</script>

<template>
  <main class="home">
    <header class="home__header">
      <h1>VibeGraph Projects</h1>
      <p class="home__subtitle">Import a Java project archive to start exploring its graph.</p>
    </header>

    <section class="home__import-grid" aria-label="Project import options">
      <AddProjectLocal @imported="onImported" />
      <AddProjectArchive :async="true" @imported="onImported" />
      <GitHubImportForm @imported="onImported" />
    </section>

    <section class="home__projects" aria-label="Imported projects">
      <div class="home__projects-head">
        <h2>Your projects</h2>
        <div class="home__projects-actions">
          <button
            class="home__refresh"
            type="button"
            :disabled="loadingProjects"
            data-test="projects-refresh"
            @click="loadProjects"
          >
            {{ loadingProjects ? 'Loading…' : 'Refresh' }}
          </button>
          <button
            v-if="hasProjects"
            class="home__clear-all"
            type="button"
            :disabled="clearingAll"
            data-test="projects-clear-all"
            @click="clearAll"
          >
            {{ clearingAll ? 'Clearing…' : 'Clear all' }}
          </button>
        </div>
      </div>

      <p v-if="projectsError" class="home__projects-error" role="alert">{{ projectsError }}</p>
      <p v-else-if="!loadingProjects && !hasProjects" class="home__projects-empty">
        No projects yet. Import one above — it will appear here when analysis finishes.
      </p>

      <ul v-else class="home__projects-list">
        <li v-for="project in projects" :key="project.id" class="home__project-row">
          <button
            class="home__project-open"
            type="button"
            :data-test="`open-project-${project.id}`"
            @click="openProject(project)"
          >
            <span class="home__project-name">{{ project.name }}</span>
            <span class="home__project-meta">{{ project.status }}</span>
          </button>
          <button
            class="home__project-delete"
            type="button"
            :disabled="deletingId === project.id"
            :aria-label="`Delete ${project.name}`"
            :data-test="`delete-project-${project.id}`"
            @click="removeProject(project)"
          >
            {{ deletingId === project.id ? '…' : '✕' }}
          </button>
        </li>
      </ul>
    </section>
  </main>
</template>

<style scoped>
.home {
  min-height: 100vh;
  padding: 2.5rem 2rem;
  background: #0b0b0b;
  color: #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.home__header {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.home__header h1 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.home__subtitle {
  margin: 0;
  color: #9ca3af;
}

.home__import-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 28rem), 1fr));
  gap: 1.5rem;
  align-items: start;
}

.home__projects {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.home__projects-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.home__projects-head h2 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
}

.home__projects-actions {
  display: flex;
  gap: 0.5rem;
}

.home__refresh {
  font: inherit;
  padding: 0.35rem 0.85rem;
  border-radius: 6px;
  border: 1px solid #2a2a2a;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.home__refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.home__clear-all {
  font: inherit;
  padding: 0.35rem 0.85rem;
  border-radius: 6px;
  border: 1px solid #7f1d1d;
  background: transparent;
  color: #f87171;
  cursor: pointer;
}

.home__clear-all:hover:not(:disabled) {
  background: rgba(127, 29, 29, 0.2);
}

.home__clear-all:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.home__projects-error {
  margin: 0;
  color: #f87171;
  font-size: 0.875rem;
}

.home__projects-empty {
  margin: 0;
  color: #9ca3af;
  font-size: 0.875rem;
}

.home__projects-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.home__project-row {
  display: flex;
  align-items: stretch;
  gap: 0.5rem;
}

.home__project-open {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  font: inherit;
  text-align: left;
  padding: 0.65rem 0.9rem;
  border-radius: 6px;
  border: 1px solid #2a2a2a;
  background: #141414;
  color: inherit;
  cursor: pointer;
  transition: border-color 150ms ease, background-color 150ms ease;
}

.home__project-open:hover {
  border-color: #2563eb;
  background: #18181b;
}

.home__project-name {
  font-weight: 500;
}

.home__project-meta {
  font-size: 0.75rem;
  color: #9ca3af;
}

.home__project-delete {
  font: inherit;
  width: 2.5rem;
  border-radius: 6px;
  border: 1px solid #2a2a2a;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  transition: border-color 150ms ease, color 150ms ease;
}

.home__project-delete:hover:not(:disabled) {
  border-color: #7f1d1d;
  color: #f87171;
}

.home__project-delete:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
