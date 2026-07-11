<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="logo">VibeGraph Admin</div>
      <nav class="nav-links">
        <RouterLink to="/admin">Dashboard</RouterLink>
        <RouterLink to="/admin/users">Users</RouterLink>
        <RouterLink to="/admin/reports">Reports</RouterLink>
      </nav>
    </aside>
    <main class="main-content">
      <header class="admin-header">
        <div class="header-title">Admin Console</div>
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

.logo {
  font-family: var(--vg-font-display);
  font-weight: 700;
  font-size: var(--vg-text-lg);
  padding: var(--vg-space-5) var(--vg-space-5);
  border-bottom: 1px solid var(--vg-border);
  background: var(--vg-grad-brand);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.02em;
  user-select: none;
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
  padding: var(--vg-space-4) var(--vg-space-6);
  border-bottom: 1px solid var(--vg-border);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.header-title {
  font-family: var(--vg-font-body);
  font-weight: 600;
  font-size: var(--vg-text-base);
  color: var(--vg-text);
  letter-spacing: -0.01em;
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
