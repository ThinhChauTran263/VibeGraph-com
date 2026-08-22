<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import LanguageSelector from '@/components/ui/LanguageSelector.vue'
import { useAuthStore } from '@/stores/auth'

const SIDEBAR_KEY = 'vg_admin_sidebar_collapsed'

const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n({ useScope: 'global' })
const isCollapsed = ref(localStorage.getItem(SIDEBAR_KEY) === 'true')
const isMobileOpen = ref(false)

const navItems = computed(() => [
  { label: t('admin.layout.nav.overview'), to: '/admin', icon: 'overview' },
  { label: t('admin.layout.nav.users'), to: '/admin/users', icon: 'users' },
  {
    label: t('admin.layout.nav.feedbackReports'),
    to: '/admin/reports',
    icon: 'reports',
  },
  {
    label: t('admin.layout.nav.plansCredits'),
    to: '/admin/plans-credits',
    icon: 'wallet',
  },
  { label: t('admin.layout.nav.security'), to: '/admin/security', icon: 'shield' },
  { label: t('admin.layout.nav.audit'), to: '/admin/audit', icon: 'audit' },
  { label: t('admin.layout.nav.system'), to: '/admin/system', icon: 'system' },
  {
    label: t('admin.layout.nav.announcements'),
    to: '/admin/announcements',
    icon: 'announcement',
  },
  { label: t('admin.layout.nav.settings'), to: '/admin/settings', icon: 'settings' },
])

const sidebarClass = computed(() => ({
  'is-collapsed': isCollapsed.value,
  'is-mobile-open': isMobileOpen.value,
}))

function toggleSidebar(): void {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem(SIDEBAR_KEY, String(isCollapsed.value))
}

function closeMobileNav(): void {
  isMobileOpen.value = false
}

function signOut(): void {
  void auth.logout()
  void router.replace({ name: 'login' })
}
</script>

<template>
  <div class="admin-layout" :class="{ 'sidebar-collapsed': isCollapsed }">
    <button
      class="mobile-menu"
      type="button"
      :aria-label="t('admin.layout.openNavigation')"
      @click="isMobileOpen = true"
    >
      <AppIcon name="menu" :size="22" />
    </button>

    <aside
      class="admin-sidebar"
      :class="sidebarClass"
      :aria-label="t('admin.layout.navigationLabel')"
    >
      <div class="sidebar-brand">
        <BrandMark
          :size="30"
          :show-wordmark="!isCollapsed"
          glyph-to="/admin"
          :glyph-aria-label="t('admin.layout.overviewLinkLabel')"
        />
        <button
          class="sidebar-toggle"
          type="button"
          :aria-label="
            isCollapsed ? t('admin.layout.expandSidebar') : t('admin.layout.collapseSidebar')
          "
          @click="toggleSidebar"
          :aria-expanded="!isCollapsed"
        >
          <AppIcon
            :name="isCollapsed ? 'menu' : 'chevron'"
            :size="isCollapsed ? 20 : 18"
            :class="{ 'is-expanded': !isCollapsed }"
          />
        </button>
      </div>

      <nav class="nav-links">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          class="nav-link"
          :to="item.to"
          :title="isCollapsed ? item.label : undefined"
          :aria-label="item.label"
          @click="closeMobileNav"
        >
          <span class="nav-icon" aria-hidden="true"><AppIcon :name="item.icon" :size="19" /></span>
          <span class="nav-label">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-footer">
        <div class="admin-account" :title="auth.userEmail || t('admin.layout.adminAccount')">
          <span class="nav-icon account-icon" aria-hidden="true"
            ><AppIcon name="account" :size="19"
          /></span>
          <span class="account-copy">
            <strong>{{ auth.userDisplayName || t('admin.layout.adminRole') }}</strong>
            <small>{{ auth.userEmail || t('admin.layout.signedIn') }}</small>
          </span>
        </div>
        <button
          class="sign-out"
          type="button"
          :aria-label="t('admin.layout.signOut')"
          @click="signOut"
        >
          <span class="nav-icon" aria-hidden="true"><AppIcon name="logout" :size="19" /></span>
          <span class="nav-label">{{ t('admin.layout.signOut') }}</span>
        </button>
      </div>
    </aside>

    <button
      v-if="isMobileOpen"
      class="mobile-scrim"
      type="button"
      :aria-label="t('admin.layout.closeNavigation')"
      @click="closeMobileNav"
    ></button>

    <main class="main-content">
      <header class="admin-header">
        <div class="admin-header__inner">
          <p class="eyebrow">{{ t('admin.layout.console') }}</p>
          <span class="admin-header__divider" aria-hidden="true"></span>
          <h1>{{ t('admin.layout.operations') }}</h1>
          <LanguageSelector class="admin-language" />
        </div>
      </header>
      <div class="content">
        <RouterView v-slot="{ Component }">
          <KeepAlive :max="12">
            <component :is="Component" />
          </KeepAlive>
        </RouterView>
      </div>
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  --sidebar-width: 264px;
  --sidebar-collapsed-width: 66px;
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--vg-bg);
  color: var(--vg-text);
}

.admin-sidebar {
  width: var(--sidebar-width);
  background: var(--vg-surface);
  border-right: 1px solid var(--vg-border);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
  transition: width var(--vg-dur-fast) var(--vg-ease-out);
  z-index: 30;
}

.admin-sidebar.is-collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-2);
  min-height: 68px;
  padding: var(--vg-space-4);
  border-bottom: 1px solid var(--vg-border);
  user-select: none;
}

.sidebar-toggle,
.mobile-menu,
.sign-out {
  border: 1px solid var(--vg-border);
  background: var(--vg-surface-2);
  color: var(--vg-text);
  cursor: pointer;
}

.sidebar-toggle {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--vg-radius-sm);
  flex: 0 0 auto;
}

.sidebar-toggle svg {
  transition: transform var(--vg-dur-fast) var(--vg-ease-out);
}

.sidebar-toggle svg.is-expanded {
  transform: rotate(180deg);
}

.nav-links {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-1);
  padding: var(--vg-space-3);
  overflow-y: auto;
}

.nav-link,
.sign-out,
.admin-account {
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
  min-height: 40px;
  border-radius: 6px;
  border: 1px solid transparent;
  text-decoration: none;
  color: var(--vg-text-muted);
  font-family: var(--vg-font-body);
  font-size: var(--vg-text-sm);
  font-weight: 500;
  padding: 0 var(--vg-space-3);
  transition: all var(--vg-dur-fast) var(--vg-ease-out);
}

.nav-link:hover,
.sign-out:hover {
  color: var(--vg-text);
  background: var(--vg-surface-3);
}

.nav-link.router-link-exact-active {
  color: var(--vg-text);
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

.nav-icon {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 20px;
  background: transparent;
  color: currentColor;
}

.nav-label,
.account-copy {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.admin-sidebar.is-collapsed .nav-label,
.admin-sidebar.is-collapsed .account-copy {
  display: none;
}

.admin-sidebar.is-collapsed .sidebar-brand {
  justify-content: center;
  min-height: 64px;
  padding: var(--vg-space-2);
}

.admin-sidebar.is-collapsed .sidebar-toggle {
  flex: 0 0 40px;
}

.sidebar-footer {
  margin-top: auto;
  padding: var(--vg-space-3);
  border-top: 1px solid var(--vg-border);
}

.admin-account {
  color: var(--vg-text);
  background: var(--vg-bg-elev);
  border: 1px solid var(--vg-border);
  margin-bottom: var(--vg-space-2);
}

.account-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.account-copy small {
  color: var(--vg-text-dim);
  font-weight: 500;
}

.sign-out {
  width: 100%;
  color: var(--vg-danger);
  border-color: color-mix(in srgb, var(--vg-danger) 35%, transparent);
  background: color-mix(in srgb, var(--vg-danger) 8%, transparent);
}

.sign-out:hover {
  color: var(--vg-danger);
  border-color: var(--vg-danger);
  background: color-mix(in srgb, var(--vg-danger) 14%, transparent);
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
  overflow: hidden;
  background: var(--vg-bg-elev);
}

.admin-header {
  position: sticky;
  top: 0;
  z-index: 25;
  display: flex;
  align-items: center;
  min-height: 68px;
  background: rgba(15, 23, 42, 0.92);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid var(--vg-border);
}

.admin-header__inner {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 68px;
  margin: 0;
  padding: 0 var(--vg-space-3);
  gap: var(--vg-space-3);
  user-select: none;
}

.eyebrow {
  flex: 0 0 auto;
  margin: 0;
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.admin-language {
  margin-left: auto;
}

.admin-header__divider {
  width: 1px;
  height: 22px;
  background: var(--vg-border);
}

.admin-header h1 {
  margin: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  font-size: 1.28rem;
  line-height: 1;
  letter-spacing: 0;
}

.content {
  flex: 1;
  width: 100%;
  margin: 0;
  padding: var(--vg-space-3) var(--vg-space-3) 0;
  overflow: auto;
  min-height: 0;
}

.mobile-menu,
.mobile-scrim {
  display: none;
}

@media (max-width: 900px) {
  .admin-layout {
    display: block;
    height: 100vh;
  }

  .mobile-menu {
    display: inline-flex;
    position: fixed;
    top: var(--vg-space-3);
    left: var(--vg-space-3);
    z-index: 45;
    width: 40px;
    height: 40px;
    border-radius: var(--vg-radius-sm);
    align-items: center;
    justify-content: center;
    padding: 0;
  }

  .admin-sidebar,
  .admin-sidebar.is-collapsed {
    position: fixed;
    left: 0;
    width: min(84vw, 300px);
    transform: translateX(-105%);
    visibility: hidden;
    pointer-events: none;
    transition: transform var(--vg-dur-fast) var(--vg-ease-out);
  }

  .admin-sidebar.is-mobile-open {
    transform: translateX(0);
    visibility: visible;
    pointer-events: auto;
  }

  .admin-sidebar.is-collapsed .nav-label,
  .admin-sidebar.is-collapsed .account-copy {
    display: initial;
  }

  .admin-sidebar.is-collapsed .sidebar-brand {
    flex-direction: row;
    justify-content: space-between;
    min-height: 68px;
    padding: var(--vg-space-4);
  }

  .mobile-scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 20;
    background: rgba(0, 0, 0, 0.45);
    border: 0;
  }

  .admin-header {
    min-height: 64px;
  }

  .admin-header__inner {
    min-height: 64px;
    padding-left: 64px;
    padding-right: var(--vg-space-3);
    min-width: 0;
  }

  .sidebar-collapsed .admin-header__inner {
    padding-left: 64px;
  }

  .admin-header h1 {
    font-size: var(--vg-text-lg);
  }

  .content {
    padding: var(--vg-space-4);
  }
}

@media (max-width: 420px) {
  .admin-header__inner {
    gap: var(--vg-space-2);
  }

  .admin-header__divider,
  .admin-header h1 {
    display: none;
  }

  .eyebrow {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
