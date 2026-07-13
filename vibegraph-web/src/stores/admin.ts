import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AdminOverview, UserProfile } from '../types/api'

export const useAdminStore = defineStore('admin', () => {
  const overview = ref<AdminOverview | null>(null)
  const users = ref<UserProfile[]>([])

  async function fetchOverview() {
    overview.value = {
      totalUsers: 150,
      onlineUsers: 5,
      totalProjects: 320
    }
  }

  async function fetchUsers() {
    users.value = [
      { id: 'usr-1', email: 'alice@example.com', displayName: 'Alice', role: 'user', status: 'active' },
      { id: 'usr-2', email: 'bob@example.com', displayName: 'Bob', role: 'admin', status: 'blocked', safeReason: 'Spam' }
    ]
  }

  return {
    overview,
    users,
    fetchOverview,
    fetchUsers
  }
})
