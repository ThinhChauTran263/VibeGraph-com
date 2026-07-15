<script setup lang="ts">
/**
 * HeaderBar - Top navigation bar.
 * Contains: brand mark, spacer, user email + logout button (when authenticated).
 */
import { useRouter } from 'vue-router'
import BrandMark from '@/components/ui/BrandMark.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <header class="header-bar">
    <RouterLink class="header-bar__brand" :to="{ name: 'home' }" aria-label="VibeGraph home">
      <BrandMark :size="26" />
    </RouterLink>

    <div class="header-bar__spacer" />

    <div v-if="auth.isAuthenticated" class="header-bar__user">
      <span class="header-bar__email" :title="auth.userEmail">
        {{ auth.userDisplayName || auth.userEmail }}
      </span>
      <button
        class="header-bar__logout"
        type="button"
        @click="handleLogout"
        aria-label="Sign out"
      >
        Logout
      </button>
    </div>
  </header>
</template>

<style scoped>
.header-bar {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0 1rem;
  border-bottom: 1px solid var(--vg-border);
}

.header-bar__brand {
  display: inline-flex;
  align-items: center;
}

.header-bar__spacer {
  flex: 1;
}

.header-bar__user {
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
}

.header-bar__email {
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-bar__logout {
  padding: 0.3rem 0.65rem;
  font-size: var(--vg-text-sm);
  font-weight: 500;
  font-family: var(--vg-font-body);
  color: var(--vg-text-muted);
  background: transparent;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  cursor: pointer;
  transition:
    color var(--vg-dur-fast),
    border-color var(--vg-dur-fast),
    background var(--vg-dur-fast);
}

.header-bar__logout:hover {
  color: var(--vg-danger);
  border-color: var(--vg-danger);
  background: rgba(239, 68, 68, 0.06);
}
</style>
