<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import BrandMark from '@/components/ui/BrandMark.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import AnnouncementBanner from '@/components/notifications/AnnouncementBanner.vue'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
const router = useRouter(),
  route = useRoute(),
  auth = useAuthStore(),
  account = useAccountStore()
const collapsed = ref(false),
  mobile = ref(false),
  isMobileViewport = ref(false),
  menuButton = ref<HTMLButtonElement | null>(null),
  sidebar = ref<HTMLElement | null>(null),
  mobileCloseButton = ref<HTMLButtonElement | null>(null)
const nav = [
  ['Overview', '/dashboard', 'overview'],
  ['Repositories', '/projects', 'repository'],
  ['API Keys', '/api-keys', 'key'],
  ['Usage', '/usage', 'usage'],
  ['Subscription', '/subscription', 'subscription'],
  ['Reports', '/reports', 'reports'],
  ['Settings', '/settings', 'settings'],
] as const
const displayName = computed(
    () =>
      account.profile?.displayName ||
      account.sessionState?.displayName ||
      auth.userDisplayName ||
      'Account',
  ),
  email = computed(
    () => account.profile?.email || account.sessionState?.email || auth.userEmail || 'Signed in',
  ),
  plan = computed(() => account.usage?.planName || 'Plan unavailable'),
  credits = computed(() => {
    const usage = account.usage as (typeof account.usage & { creditsRemaining?: number }) | null
    if (typeof usage?.creditsRemaining === 'number') return usage.creditsRemaining.toLocaleString()
    return typeof usage?.creditsLimit === 'number' && typeof usage.creditsUsed === 'number'
      ? Math.max(usage.creditsLimit - usage.creditsUsed, 0).toLocaleString()
      : 'Unavailable'
  })
const restricted = computed(() => account.accountRestricted)
const restrictionTitle = computed(() =>
  account.sessionState?.accountStatus?.toUpperCase() === 'DEACTIVATED'
    ? 'Account deactivated'
    : 'Account access restricted',
)
const restrictionReason = computed(
  () => account.restrictionReason || 'This account cannot use product features right now.',
)
const reportsRouteActive = computed(() => route.name === 'reports')
let accountPoll: ReturnType<typeof setInterval> | undefined
let mobileMedia: MediaQueryList | undefined
function syncMobileViewport(): void {
  isMobileViewport.value = mobileMedia?.matches ?? false
}
onMounted(() => {
  mobileMedia = window.matchMedia('(max-width: 900px)')
  syncMobileViewport()
  mobileMedia.addEventListener('change', syncMobileViewport)
  accountPoll = setInterval(refreshAccountState, 10000)
  window.addEventListener('focus', refreshAccountState)
  void refreshAccountState()
})
onBeforeUnmount(() => {
  if (accountPoll) clearInterval(accountPoll)
  mobileMedia?.removeEventListener('change', syncMobileViewport)
  window.removeEventListener('focus', refreshAccountState)
})
async function openMobileNavigation(): Promise<void> {
  mobile.value = true
  await nextTick()
  mobileCloseButton.value?.focus()
}
async function closeMobileNavigation(): Promise<void> {
  if (!mobile.value) return
  mobile.value = false
  await nextTick()
  menuButton.value?.focus()
}
function handleSidebarKeydown(event: KeyboardEvent): void {
  if (!mobile.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    void closeMobileNavigation()
    return
  }
  if (event.key !== 'Tab' || !sidebar.value) return

  const focusable = Array.from(
    sidebar.value.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  )
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (!first || !last) return
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
async function refreshAccountState() {
  try {
    await account.fetchSessionState()
    if (!account.accountRestricted) {
      await Promise.allSettled([account.fetchProfile(), account.fetchUsage()])
    }
  } catch {
    // Global HTTP handling owns authentication failures; keep the last safe account state.
  }
}
async function signOut() {
  await auth.logout()
  await router.push({ name: 'login' })
}
</script>
<template>
  <div class="layout" :class="{ collapsed }">
    <button
      ref="menuButton"
      class="mobile"
      type="button"
      aria-label="Open navigation"
      aria-controls="user-sidebar"
      :aria-expanded="mobile"
      @click="openMobileNavigation"
    >
      <AppIcon name="menu" /></button
    ><button
      v-if="mobile"
      class="scrim"
      type="button"
      aria-label="Close navigation"
      @click="closeMobileNavigation"
    ></button>
    <aside
      id="user-sidebar"
      ref="sidebar"
      :class="{ open: mobile }"
      :inert="isMobileViewport && !mobile ? true : undefined"
      aria-label="User navigation"
      @keydown="handleSidebarKeydown"
    >
      <header>
        <button
          ref="mobileCloseButton"
          class="sidebar__mobile-close"
          type="button"
          aria-label="Close navigation"
          @click="closeMobileNavigation"
        >
          <AppIcon name="close" />
        </button>
        <RouterLink to="/dashboard" aria-label="VibeGraph overview"
          ><BrandMark :size="30" :show-wordmark="!collapsed" /></RouterLink
        ><button
          class="sidebar__toggle"
          type="button"
          aria-controls="user-sidebar"
          :aria-expanded="!collapsed"
          :aria-label="collapsed ? 'Expand sidebar' : 'Collapse sidebar'"
          @click="collapsed = !collapsed"
        >
          <AppIcon :name="collapsed ? 'menu' : 'chevron'" />
        </button>
      </header>
      <nav>
        <template v-for="[label, to, icon] in nav" :key="to">
          <RouterLink
            v-if="!restricted || to === '/reports'"
            :to="to"
            :aria-label="label"
            :title="collapsed ? label : undefined"
            @click="closeMobileNavigation"
            ><AppIcon :name="icon" /><span>{{ label }}</span></RouterLink
          >
          <span
            v-else
            class="nav-disabled"
            aria-disabled="true"
            :aria-label="`${label} unavailable`"
            :title="restrictionReason"
          >
            <AppIcon :name="icon" /><span>{{ label }}</span>
          </span>
        </template>
      </nav>
      <section
        class="account"
        :title="collapsed ? `${displayName} · ${plan} · ${credits} credits` : email"
        :aria-label="`${displayName}, ${plan}, ${credits} credits remaining`"
      >
        <AppIcon name="account" />
        <div>
          <strong>{{ displayName }}</strong>
          <small>{{ email }}</small>
          <div class="account__summary">
            <b>{{ plan }}</b
            ><span>{{ credits }} credits</span>
          </div>
        </div>
      </section>
      <button data-test="user-sign-out" class="signout" type="button" @click="signOut">
        <AppIcon name="logout" /><span>Sign Out</span>
      </button>
    </aside>
    <main :inert="mobile ? true : undefined">
      <AnnouncementBanner v-if="!restricted" />
      <section v-if="restricted" class="restriction-banner" role="alert">
        <div>
          <span>Account status</span>
          <strong>{{ restrictionTitle }}</strong>
          <p>{{ restrictionReason }}</p>
        </div>
        <RouterLink v-if="!reportsRouteActive" to="/reports">Contact support</RouterLink>
      </section>
      <RouterView v-if="!restricted || reportsRouteActive" />
      <section v-else class="restricted-state" aria-labelledby="restricted-title">
        <AppIcon name="shield" :size="30" />
        <h1 id="restricted-title">Product controls are unavailable</h1>
        <p>{{ restrictionReason }}</p>
        <RouterLink to="/reports">Open a support report</RouterLink>
      </section>
    </main>
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
.nav-disabled,
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
.nav-disabled {
  opacity: 0.45;
  cursor: not-allowed;
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
.restriction-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-4);
  margin-bottom: var(--vg-space-4);
  padding: var(--vg-space-4);
  border: 1px solid color-mix(in srgb, var(--vg-danger) 45%, var(--vg-border));
  border-left: 4px solid var(--vg-danger);
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--vg-danger) 9%, var(--vg-surface));
}
.restriction-banner span {
  display: block;
  color: var(--vg-danger);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.restriction-banner strong {
  display: block;
  margin-top: 0.2rem;
  color: var(--vg-text);
  font: 700 var(--vg-text-lg) var(--vg-font-display);
}
.restriction-banner p,
.restricted-state p {
  margin: 0.35rem 0 0;
  color: var(--vg-text-muted);
}
.restriction-banner a,
.restricted-state a {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--vg-blue);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-blue);
  color: white;
  font-weight: 700;
  text-decoration: none;
}
.restricted-state {
  min-height: 24rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: clamp(1.5rem, 5vw, 4rem);
  border: 1px dashed var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
  color: var(--vg-danger);
}
.restricted-state h1 {
  margin: var(--vg-space-3) 0 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}
.restricted-state a {
  margin-top: var(--vg-space-4);
}
.sidebar__mobile-close {
  display: none;
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
  .mobile,
  .sidebar__mobile-close {
    width: 44px;
    height: 44px;
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
  .sidebar__mobile-close {
    display: grid;
    place-items: center;
    flex: 0 0 auto;
  }
  .collapsed header a {
    display: flex;
  }
  .collapsed nav a,
  .collapsed .nav-disabled,
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
  .restriction-banner {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
