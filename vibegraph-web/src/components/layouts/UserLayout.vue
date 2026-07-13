<script setup lang="ts">
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import BrandMark from '@/components/ui/BrandMark.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

function handleLogout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="user-layout">
    <header class="header">
      <div class="header__brand-group">
        <RouterLink to="/dashboard" class="header__logo-link">
          <BrandMark :size="24" />
        </RouterLink>
        <span class="header__separator">|</span>
        <span class="header__title">User Panel</span>
      </div>

      <nav class="nav-links">
        <RouterLink to="/dashboard">Home</RouterLink>
        <RouterLink to="/profile">Profile</RouterLink>
        <RouterLink to="/projects">Projects</RouterLink>
        <RouterLink to="/api-keys">API Keys</RouterLink>
        <RouterLink to="/usage">Usage</RouterLink>
        <RouterLink to="/reports">Reports</RouterLink>
      </nav>

      <div class="header__user-actions" v-if="auth.isAuthenticated">
        <span class="header__email" :title="auth.userEmail">
          {{ auth.userDisplayName || auth.userEmail }}
        </span>
        <button
          class="header__logout-btn"
          type="button"
          @click="handleLogout"
          aria-label="Sign out"
        >
          Logout
        </button>
      </div>
    </header>
    <main class="main-content" :class="{ 'main-content--full-width': route.name === 'graph' }">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.user-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--vg-bg);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--vg-space-3) var(--vg-space-6);
  background: var(--vg-surface);
  border-bottom: 1px solid var(--vg-border);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  position: sticky;
  top: 0;
  z-index: 100;
  gap: var(--vg-space-4);
}

.header__brand-group {
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
}

.header__logo-link {
  text-decoration: none;
  display: inline-flex;
}

.header__separator {
  color: var(--vg-border);
  font-weight: 300;
}

.header__title {
  font-family: var(--vg-font-body);
  font-weight: 500;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.nav-links {
  display: flex;
  gap: var(--vg-space-1);
}

.nav-links a {
  text-decoration: none;
  color: var(--vg-text-muted);
  font-family: var(--vg-font-body);
  font-size: var(--vg-text-sm);
  font-weight: 500;
  padding: var(--vg-space-2) var(--vg-space-4);
  border-radius: var(--vg-radius-pill);
  transition:
    color var(--vg-dur-fast) var(--vg-ease-out),
    background-color var(--vg-dur-fast) var(--vg-ease-out);
  position: relative;
}

.nav-links a:hover {
  color: var(--vg-text);
  background: rgba(148, 163, 184, 0.08);
}

.nav-links a.router-link-active {
  color: var(--vg-blue-bright);
  background: rgba(59, 130, 246, 0.12);
  font-weight: 600;
}

.header__user-actions {
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
}

.header__email {
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header__logout-btn {
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

.header__logout-btn:hover {
  color: var(--vg-danger);
  border-color: var(--vg-danger);
  background: rgba(239, 68, 68, 0.06);
}

.main-content {
  flex: 1;
  padding: var(--vg-space-6);
  background: transparent;
  width: 100%;
  max-width: var(--vg-maxw);
  margin: 0 auto;
}

.main-content--full-width {
  max-width: none;
  padding: 0;
  margin: 0;
}
</style>

