import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * Project store - manages current project state.
 */
export const useProjectStore = defineStore('project', () => {
  const currentProjectId = ref<string | null>(null)
  const projectName = ref('')
  const isConnected = ref(false)
  const lastUpdated = ref<Date | null>(null)

  // TODO: Implement project management actions

  return {
    currentProjectId,
    projectName,
    isConnected,
    lastUpdated,
  }
})
