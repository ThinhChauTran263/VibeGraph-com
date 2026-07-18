import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Project } from '@/lib/api'

/**
 * Project store - manages current project state.
 */
export const useProjectStore = defineStore('project', () => {
  const currentProjectId = ref<string | null>(null)
  const projectName = ref('')
  const isConnected = ref(false)
  const lastUpdated = ref<Date | null>(null)
  const projects = ref<Project[]>([])
  const projectsLoaded = ref(false)

  // TODO: Implement project management actions

  return {
    currentProjectId,
    projectName,
    isConnected,
    lastUpdated,
    projects,
    projectsLoaded,
  }
})
