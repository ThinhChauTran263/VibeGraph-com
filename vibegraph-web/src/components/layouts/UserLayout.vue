<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import BrandMark from '@/components/ui/BrandMark.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import AnnouncementBanner from '@/components/notifications/AnnouncementBanner.vue'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
const KEY = 'vg_user_sidebar_collapsed',
  router = useRouter(),
  auth = useAuthStore(),
  account = useAccountStore()
const collapsed = ref(localStorage.getItem(KEY) === 'true'),
  mobile = ref(false)
const nav = [
  ['Overview', '/dashboard', 'overview'],
  ['Repositories', '/projects', 'repository'],
  ['API Keys', '/api-keys', 'key'],
  ['Usage', '/usage', 'usage'],
  ['Subscription', '/subscription', 'subscription'],
  ['Reports', '/reports', 'reports'],
  ['Notification', '/notifications', 'notification'],
  ['Tutorial', '/tutorial', 'tutorial'],
  ['Settings', '/settings', 'settings'],
] as const
const email = computed(() => account.profile?.email || auth.userEmail || 'Signed in'),
  plan = computed(() => account.usage?.planName || 'Plan unavailable'),
  credits = computed(() => {
    const u = account.usage
    return typeof u?.creditsLimit === 'number' && typeof u.creditsUsed === 'number'
      ? Math.max(u.creditsLimit - u.creditsUsed, 0).toLocaleString()
      : 'Unavailable'
  })
watch(collapsed, (v) => localStorage.setItem(KEY, String(v)))
onMounted(() => {
  void Promise.allSettled([account.fetchProfile(), account.fetchUsage()])
})
function signOut() {
  auth.logout()
  void router.push({ name: 'login' })
}
</script>
<template>
  <div class="layout" :class="{ collapsed }">
    <button class="mobile" type="button" aria-label="Open navigation" @click="mobile = true">
      <AppIcon name="menu" /></button
    ><button
      v-if="mobile"
      class="scrim"
      type="button"
      aria-label="Close navigation"
      @click="mobile = false"
    ></button>
    <aside :class="{ open: mobile }" aria-label="User navigation">
      <header>
        <RouterLink to="/dashboard" aria-label="VibeGraph overview"
          ><BrandMark :size="30" :show-wordmark="!collapsed" /></RouterLink
        ><button
          type="button"
          :aria-label="collapsed ? 'Expand sidebar' : 'Collapse sidebar'"
          @click="collapsed = !collapsed"
        >
          <AppIcon :name="collapsed ? 'menu' : 'chevron'" />
        </button>
      </header>
      <nav>
        <RouterLink
          v-for="[label, to, icon] in nav"
          :key="to"
          :to="to"
          :title="collapsed ? label : undefined"
          @click="mobile = false"
          ><AppIcon :name="icon" /><span>{{ label }}</span></RouterLink
        >
      </nav>
      <section class="account" :title="email">
        <AppIcon name="wallet" />
        <div>
          <div class="account__summary">
            <b>{{ plan }}</b
            ><span>{{ credits }} credits</span>
          </div>
          <small>{{ email }}</small>
        </div>
      </section>
      <button class="signout" type="button" @click="signOut">
        <AppIcon name="logout" /><span>Sign Out</span>
      </button>
    </aside>
    <main><AnnouncementBanner /><RouterView /></main>
  </div>
</template>
<style scoped>
.layout {
  --side: 264px;
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--side) minmax(0, 1fr);
  background: var(--vg-bg);
}
.layout.collapsed {
  --side: 66px;
}
aside {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
  padding: var(--vg-space-3);
  overflow: hidden;
  border-right: 1px solid var(--vg-border);
  background: var(--vg-surface);
  z-index: 30;
}
header {
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.35rem;
}
header a {
  display: flex;
  min-width: 0;
}
header button,
.mobile {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid var(--vg-border);
  border-radius: 6px;
  background: var(--vg-bg-elev);
  color: var(--vg-text);
  cursor: pointer;
}
nav {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  overflow-y: auto;
}
nav a,
.signout,
.account {
  min-height: 40px;
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
  padding: 0.45rem 0.6rem;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--vg-text-muted);
  text-align: left;
  text-decoration: none;
  font-size: var(--vg-text-sm);
  font-weight: 600;
}
nav a:hover,
nav a.router-link-active {
  color: var(--vg-text);
  border-color: rgba(96, 165, 250, 0.28);
  background: rgba(59, 130, 246, 0.1);
}
nav svg,
.account > svg,
.signout svg {
  flex: 0 0 auto;
  color: var(--vg-blue-bright);
}
.account {
  margin-top: auto;
  border-color: var(--vg-border);
  background: var(--vg-bg-elev);
}
.account > div {
  min-width: 0;
  flex: 1;
}
.account__summary {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--vg-space-2);
}
.account b {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 600;
}
.account span {
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-sm);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.account small {
  display: block;
  margin-top: 0.15rem;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.account span,
.account small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.signout {
  width: 100%;
  background: transparent;
  cursor: pointer;
  font: inherit;
  color: var(--vg-danger);
}
.collapsed aside {
  align-items: center;
  padding-inline: 0.75rem;
}
.collapsed header,
.collapsed nav,
.collapsed .signout {
  width: 100%;
}
.collapsed header {
  justify-content: center;
}
.collapsed header a {
  display: none;
}
.collapsed nav a,
.collapsed .signout {
  justify-content: center;
  padding-inline: 0;
}
.collapsed .account {
  width: 40px;
  min-height: 40px;
  justify-content: center;
  padding: 0;
  border-color: var(--vg-border);
  background: var(--vg-bg-elev);
}
.collapsed nav span,
.collapsed .signout span,
.collapsed .account div {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
.layout > main {
  min-width: 0;
  width: 100%;
  margin: 0;
  padding: var(--vg-space-3);
}
.mobile,
.scrim {
  display: none;
}
@media (max-width: 900px) {
  .layout,
  .layout.collapsed {
    display: block;
  }
  .mobile {
    display: grid;
    position: fixed;
    top: var(--vg-space-3);
    left: var(--vg-space-3);
    z-index: 25;
  }
  .scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 20;
    border: 0;
    background: rgba(0, 0, 0, 0.52);
  }
  aside {
    position: fixed;
    width: min(88vw, 300px);
    transform: translateX(-105%);
    transition: transform var(--vg-dur-fast) var(--vg-ease-out);
  }
  aside.open {
    transform: none;
  }
  .collapsed header a {
    display: flex;
  }
  .collapsed nav a,
  .collapsed .signout,
  .collapsed .account {
    justify-content: flex-start;
    padding: 0.55rem 0.7rem;
  }
  .collapsed nav span,
  .collapsed .signout span,
  .collapsed .account div {
    position: static;
    width: auto;
    height: auto;
    clip: auto;
  }
  .layout > main {
    padding: calc(var(--vg-space-8) + 2rem) var(--vg-space-4) var(--vg-space-4);
  }
}
</style>
