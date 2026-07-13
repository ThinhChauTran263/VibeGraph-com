<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="user-layout">
    <header class="header">
      <div class="logo">VibeGraph User</div>
      <nav class="nav-links">
        <RouterLink to="/profile">Profile</RouterLink>
        <RouterLink to="/projects">Projects</RouterLink>
        <RouterLink to="/api-keys">API Keys</RouterLink>
        <RouterLink to="/usage">Usage</RouterLink>
        <RouterLink to="/reports">Reports</RouterLink>
      </nav>
    </header>
    <main class="main-content">
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
  padding: var(--vg-space-4) var(--vg-space-6);
  background: var(--vg-surface);
  border-bottom: 1px solid var(--vg-border);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  position: sticky;
  top: 0;
  z-index: 100;
}

.logo {
  font-family: var(--vg-font-display);
  font-weight: 700;
  font-size: var(--vg-text-lg);
  background: var(--vg-grad-brand);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.02em;
  user-select: none;
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

.main-content {
  flex: 1;
  padding: var(--vg-space-6);
  background: transparent;
  width: 100%;
  max-width: var(--vg-maxw);
  margin: 0 auto;
}
</style>
