<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
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
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar__brand-group">
        <RouterLink to="/dashboard" class="sidebar__logo-link">
          <BrandMark :size="22" />
        </RouterLink>
        <span class="sidebar__badge">Admin</span>
      </div>
      <nav class="nav-links">
        <RouterLink to="/admin">Dashboard</RouterLink>
        <RouterLink to="/admin/users">Users</RouterLink>
        <RouterLink to="/admin/reports">Reports</RouterLink>
      </nav>
    </aside>
    <main class="main-content">
      <header class="admin-header">
        <div class="header-title">Admin Console</div>
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
      <div class="content">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--vg-bg);
}

.sidebar {
  width: 260px;
  background: var(--vg-surface);
  border-right: 1px solid var(--vg-border);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

.sidebar__brand-group {
  display: flex;
  align-items: center;
  gap: var(--vg-space-2);
  padding: var(--vg-space-4) var(--vg-space-5);
  border-bottom: 1px solid var(--vg-border);
}

.sidebar__logo-link {
  text-decoration: none;
  display: inline-flex;
}

.sidebar__badge {
  font-family: var(--vg-font-body);
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--vg-orange);
  background: rgba(245, 158, 11, 0.12);
  padding: var(--vg-space-0-5) var(--vg-space-1-5);
  border-radius: var(--vg-radius-sm);
  letter-spacing: 0.05em;
}

.nav-links {
  display: flex;
  flex-direction: column;
  padding: var(--vg-space-3) 0;
  gap: var(--vg-space-1);
}

.nav-links a {
  text-decoration: none;
  color: var(--vg-text-muted);
  font-family: var(--vg-font-body);
  font-size: var(--vg-text-sm);
  font-weight: 500;
  padding: var(--vg-space-3) var(--vg-space-5);
  border-left: 3px solid transparent;
  transition:
    color var(--vg-dur-fast) var(--vg-ease-out),
    background-color var(--vg-dur-fast) var(--vg-ease-out),
    border-color var(--vg-dur-fast) var(--vg-ease-out);
}

.nav-links a:hover {
  color: var(--vg-text);
  background: var(--vg-surface-3);
  border-left-color: var(--vg-border-strong);
}

.nav-links a.router-link-exact-active {
  color: var(--vg-blue-bright);
  background: rgba(59, 130, 246, 0.08);
  border-left-color: var(--vg-blue);
  font-weight: 600;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--vg-bg-elev);
  min-width: 0;
}

.admin-header {
  background: var(--vg-surface);
  padding: var(--vg-space-3) var(--vg-space-6);
  border-bottom: 1px solid var(--vg-border);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  font-family: var(--vg-font-body);
  font-weight: 600;
  font-size: var(--vg-text-base);
  color: var(--vg-text);
  letter-spacing: -0.01em;
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

.content {
  flex: 1;
  padding: var(--vg-space-6);
  overflow-y: auto;
  width: 100%;
  max-width: var(--vg-maxw);
  margin: 0 auto;
}
</style>
