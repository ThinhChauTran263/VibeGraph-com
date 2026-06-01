<script setup lang="ts">
/**
 * HomeView - landing page for project management.
 *
 * Sprint 2 scope: surface the Add Project archive upload flow. The full
 * project list and selection UI come later; for now we route the user
 * straight to the graph view of a successful import so the upload feedback
 * loop is end-to-end.
 */

import { useRouter } from 'vue-router'
import AddProjectArchive from '@/components/projects/AddProjectArchive.vue'
import { useProjectStore } from '@/stores/project'
import type { Project } from '@/lib/api'

const router = useRouter()
const projectStore = useProjectStore()

function onImported(project: Project): void {
  projectStore.currentProjectId = project.id
  projectStore.projectName = project.name
  router.push({ name: 'graph', params: { projectId: project.id } })
}
</script>

<template>
  <main class="home">
    <header class="home__header">
      <h1>VibeGraph Projects</h1>
      <p class="home__subtitle">Import a Java project archive to start exploring its graph.</p>
    </header>

    <AddProjectArchive @imported="onImported" />
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
</style>
