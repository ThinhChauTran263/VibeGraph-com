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
import ImportProjectPanel from '@/components/projects/ImportProjectPanel.vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import { useProjectStore } from '@/stores/project'
import { projectApi, type Project } from '@/lib/api'
import { PROJECTS_AUTO_REFRESH_INTERVAL_MS } from '@/lib/runtimeConfig'

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
const AUTO_REFRESH_INTERVAL_MS = PROJECTS_AUTO_REFRESH_INTERVAL_MS
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
  <div class="dash">
    <main class="home">
      <header class="home__header">
        <span class="home__eyebrow">Dashboard</span>
        <h1>Your projects</h1>
        <p class="home__subtitle">
          Import a Java project from a local folder, an archive, or GitHub to start exploring its
          graph.
        </p>
      </header>

    <section class="home__import" aria-label="Project import options">
      <ImportProjectPanel @imported="onImported" />
    </section>

    <section class="home__projects" aria-label="Imported projects">
      <div class="home__projects-head">
        <h2>Imported projects</h2>
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
  </div>
</template>

<style scoped>
.dash {
  min-height: 100vh;
  background: radial-gradient(100% 60% at 100% 0%, rgba(34, 197, 94, 0.06), transparent 55%),
    radial-gradient(80% 50% at 0% 0%, rgba(59, 130, 246, 0.08), transparent 55%), var(--vg-bg);
  color: var(--vg-text);
}

.dash-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: var(--vg-space-4);
  padding: 0.85rem clamp(1rem, 0.5rem + 2vw, 2.5rem);
  background: rgba(7, 11, 22, 0.72);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--vg-border);
}

.dash-nav__brand {
  display: inline-flex;
  border-radius: var(--vg-radius-sm);
}

.dash-nav__back {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 0.9rem;
  border-radius: var(--vg-radius-pill);
  border: 1px solid var(--vg-border-strong);
  background: rgba(148, 163, 184, 0.06);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  font-weight: 500;
  transition: color var(--vg-dur-fast), border-color var(--vg-dur-fast),
    background-color var(--vg-dur-fast);
}
.dash-nav__back:hover {
  color: var(--vg-text);
  border-color: var(--vg-blue-bright);
  background: rgba(148, 163, 184, 0.12);
}

.home {
  max-width: var(--vg-maxw);
  margin: 0 auto;
  padding: clamp(2rem, 1.5rem + 2vw, 3rem) clamp(1rem, 0.5rem + 2vw, 2.5rem) 4rem;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-12);
}

.home__header {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.home__eyebrow {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-sm);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--vg-blue-bright);
}

.home__header h1 {
  margin: 0;
  font-size: var(--vg-text-2xl);
  font-weight: 700;
}

.home__subtitle {
  margin: 0;
  max-width: 42rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-lg);
}

.home__import {
  max-width: 52rem;
  width: 100%;
  margin-inline: auto;
}

.home__projects {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}

.home__projects-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.home__projects-head h2 {
  margin: 0;
  font-size: var(--vg-text-xl);
  font-weight: 600;
}

.home__projects-actions {
  display: flex;
  gap: 0.5rem;
}

.home__refresh {
  font: inherit;
  padding: 0.4rem 0.95rem;
  border-radius: var(--vg-radius-pill);
  border: 1px solid var(--vg-border-strong);
  background: rgba(148, 163, 184, 0.06);
  color: inherit;
  cursor: pointer;
  transition: border-color var(--vg-dur-fast), background-color var(--vg-dur-fast);
}

.home__refresh:hover:not(:disabled) {
  border-color: var(--vg-blue-bright);
  background: rgba(148, 163, 184, 0.12);
}

.home__refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.home__clear-all {
  font: inherit;
  padding: 0.4rem 0.95rem;
  border-radius: var(--vg-radius-pill);
  border: 1px solid rgba(239, 68, 68, 0.5);
  background: transparent;
  color: #f87171;
  cursor: pointer;
  transition: background-color var(--vg-dur-fast);
}

.home__clear-all:hover:not(:disabled) {
  background: rgba(127, 29, 29, 0.25);
}

.home__clear-all:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.home__projects-error {
  margin: 0;
  color: #f87171;
  font-size: var(--vg-text-sm);
}

.home__projects-empty {
  margin: 0;
  padding: var(--vg-space-8);
  text-align: center;
  border: 1px dashed var(--vg-border-strong);
  border-radius: var(--vg-radius-lg);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
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
  padding: 0.8rem 1.1rem;
  border-radius: var(--vg-radius);
  border: 1px solid var(--vg-border);
  background: var(--vg-grad-surface);
  color: inherit;
  cursor: pointer;
  transition: border-color var(--vg-dur-fast) ease, transform var(--vg-dur-fast) ease,
    box-shadow var(--vg-dur) ease;
}

.home__project-open:hover {
  border-color: var(--vg-blue-bright);
  box-shadow: var(--vg-shadow);
}

.home__project-name {
  font-weight: 600;
}

.home__project-meta {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  padding: 0.2rem 0.6rem;
  border-radius: var(--vg-radius-pill);
  border: 1px solid var(--vg-border-strong);
  color: var(--vg-green-bright);
}

.home__project-delete {
  font: inherit;
  width: 2.75rem;
  border-radius: var(--vg-radius);
  border: 1px solid var(--vg-border);
  background: transparent;
  color: var(--vg-text-dim);
  cursor: pointer;
  transition: border-color var(--vg-dur-fast) ease, color var(--vg-dur-fast) ease;
}

.home__project-delete:hover:not(:disabled) {
  border-color: rgba(239, 68, 68, 0.6);
  color: #f87171;
}

.home__project-delete:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
