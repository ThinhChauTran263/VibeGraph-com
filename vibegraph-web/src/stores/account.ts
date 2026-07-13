import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserProfile, UserUsage, Project, ApiKey } from '../types/api'
import { useAuthStore } from './auth'
import { accountApi } from '../lib/api'

export const useAccountStore = defineStore('account', () => {
  const profile = ref<UserProfile | null>(null)
  const usage = ref<UserUsage | null>(null)
  const projects = ref<Project[]>([])
  const apiKeys = ref<ApiKey[]>([])

  async function fetchProfile() {
    const authStore = useAuthStore()
    if (authStore.user) {
      profile.value = {
        id: authStore.user.id,
        email: authStore.user.email,
        displayName: authStore.user.displayName,
        role: authStore.user.role,
        status: 'active'
      }
    } else {
      profile.value = {
        id: 'usr-1',
        email: 'user@example.com',
        displayName: 'Test User',
        role: 'USER',
        status: 'active'
      }
    }
  }

  async function updateDisplayName(newName: string) {
    const updated = await accountApi.updateProfile(newName)
    if (profile.value) {
      profile.value.displayName = updated.displayName
    }
    const authStore = useAuthStore()
    if (authStore.user) {
      authStore.user.displayName = updated.displayName
      localStorage.setItem('vg_user', JSON.stringify(authStore.user))
    }
  }

  async function fetchUsage() {
    usage.value = {
      planId: 'free',
      planName: 'Free Tier',
      sourceStorageUsed: 100,
      sourceStorageLimit: 500,
      creditsUsed: 50,
      creditsLimit: 100,
      apiKeyLimit: 3,
      apiKeysDisabled: false
    }
  }

  async function fetchProjects() {
    projects.value = []
  }

  async function fetchApiKeys() {
    // Initial fetch mock
    apiKeys.value = []
  }

  async function createApiKey(name: string) {
    const newKey: ApiKey = {
      id: `key-${Date.now()}`,
      name,
      secret: `vg-${Math.random().toString(36).substring(2, 15)}`,
      createdAt: new Date().toISOString(),
      disabled: false
    }
    apiKeys.value.push(newKey)
    return newKey
  }

  async function disableApiKey(id: string) {
    const key = apiKeys.value.find(k => k.id === id)
    if (key) {
      key.disabled = true
    }
  }

  return {
    profile,
    usage,
    projects,
    apiKeys,
    fetchProfile,
    updateDisplayName,
    fetchUsage,
    fetchProjects,
    fetchApiKeys,
    createApiKey,
    disableApiKey
  }
})
