<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import BrandMark from '@/components/ui/BrandMark.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import LanguageSelector from '@/components/ui/LanguageSelector.vue'
import AnnouncementBanner from '@/components/notifications/AnnouncementBanner.vue'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { displayPlanName } from '@/lib/planDisplay'
const router = useRouter(),
  route = useRoute(),
  auth = useAuthStore(),
  account = useAccountStore()
const { t } = useI18n({ useScope: 'global' })
const collapsed = ref(false),
  mobile = ref(false),
  isMobileViewport = ref(false),
  accountStateReady = ref(false),
  accountStateError = ref(false),
  menuButton = ref<HTMLButtonElement | null>(null),
  sidebar = ref<HTMLElement | null>(null),
  mobileCloseButton = ref<HTMLButtonElement | null>(null)
const nav = [
  ['nav.overview', '/dashboard', 'overview'],
  ['nav.repositories', '/projects', 'repository'],
  ['nav.apiKeys', '/api-keys', 'key'],
  ['nav.usage', '/usage', 'usage'],
  ['nav.subscription', '/subscription', 'subscription'],
  ['nav.reports', '/reports', 'reports'],
  ['nav.settings', '/settings', 'settings'],
] as const
const displayName = computed(
    () =>
      account.profile?.displayName ||
      account.sessionState?.displayName ||
      auth.userDisplayName ||
      t('user.layout.account'),
  ),
  email = computed(
    () =>
      account.profile?.email ||
      account.sessionState?.email ||
      auth.userEmail ||
      t('user.layout.signedIn'),
  ),
  plan = computed(() =>
    displayPlanName(
      t,
      account.usage?.planCode,
      account.usage?.planName,
      t('user.layout.planUnavailable'),
    ),
  ),
  credits = computed(() => {
    const usage = account.usage as (typeof account.usage & { creditsRemaining?: number }) | null
    if (typeof usage?.creditsRemaining === 'number') return usage.creditsRemaining.toLocaleString()
    return typeof usage?.creditsLimit === 'number' && typeof usage.creditsUsed === 'number'
      ? Math.max(usage.creditsLimit - usage.creditsUsed, 0).toLocaleString()
      : t('user.layout.unavailable')
  })
const restricted = computed(() => account.accountRestricted)
const restrictionTitle = computed(() =>
  account.sessionState?.accountStatus?.toUpperCase() === 'DEACTIVATED'
    ? t('user.layout.accountDeactivated')
    : t('user.layout.accountRestricted'),
)
const restrictionReason = computed(
  () => account.restrictionReason || t('user.layout.restrictionFallback'),
)
const reportsRouteActive = computed(() => route.name === 'reports')
const graphRouteActive = computed(() => route.name === 'graph')
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
let accountRefreshPromise: Promise<void> | null = null
async function refreshAccountState(): Promise<void> {
  if (accountRefreshPromise) return accountRefreshPromise
  accountRefreshPromise = (async () => {
    try {
      if (!accountStateReady.value) accountStateError.value = false
      await account.fetchSessionState()
      accountStateReady.value = true
      accountStateError.value = false
      if (!account.accountRestricted) {
        await Promise.allSettled([account.fetchProfile(), account.fetchUsage()])
      }
    } catch {
      // Global HTTP handling owns authentication failures; keep the last safe account state.
      if (!accountStateReady.value) accountStateError.value = true
    } finally {
      accountRefreshPromise = null
    }
  })()
  return accountRefreshPromise
}
function signOut(): void {
  void auth.logout()
  void router.replace({ name: 'login' })
}
</script>
<template>
  <div class="layout" :class="{ collapsed, 'layout--graph': graphRouteActive }">
    <button
      ref="menuButton"
      class="mobile"
      type="button"
      :aria-label="t('user.layout.openNavigation')"
      aria-controls="user-sidebar"
      :aria-expanded="mobile"
      @click="openMobileNavigation"
    >
      <AppIcon name="menu" /></button
    ><button
      v-if="mobile"
      class="scrim"
      type="button"
      :aria-label="t('user.layout.closeNavigation')"
      @click="closeMobileNavigation"
    ></button>
    <aside
      id="user-sidebar"
      ref="sidebar"
      :class="{ open: mobile }"
      :inert="isMobileViewport && !mobile ? true : undefined"
      :aria-label="t('user.layout.navigationLabel')"
      @keydown="handleSidebarKeydown"
    >
      <header>
        <button
          ref="mobileCloseButton"
          class="sidebar__mobile-close"
          type="button"
          :aria-label="t('user.layout.closeNavigation')"
          @click="closeMobileNavigation"
        >
          <AppIcon name="close" />
        </button>
        <RouterLink to="/dashboard" :aria-label="t('user.layout.overviewLinkLabel')"
          ><BrandMark :size="30" :show-wordmark="!collapsed" /></RouterLink
        ><button
          class="sidebar__toggle"
          type="button"
          aria-controls="user-sidebar"
          :aria-expanded="!collapsed"
          :aria-label="
            collapsed ? t('user.layout.expandSidebar') : t('user.layout.collapseSidebar')
          "
          @click="collapsed = !collapsed"
        >
          <AppIcon :name="collapsed ? 'menu' : 'chevron'" />
        </button>
      </header>
      <nav>
        <template v-for="[labelKey, to, icon] in nav" :key="to">
          <RouterLink
            v-if="!restricted || to === '/reports'"
            :to="to"
            :aria-label="t(labelKey)"
            :title="collapsed ? t(labelKey) : undefined"
            @click="closeMobileNavigation"
            ><AppIcon :name="icon" /><span>{{ t(labelKey) }}</span></RouterLink
          >
          <span
            v-else
            class="nav-disabled"
            aria-disabled="true"
            :aria-label="t('user.layout.navUnavailable', { label: t(labelKey) })"
            :title="restrictionReason"
          >
            <AppIcon :name="icon" /><span>{{ t(labelKey) }}</span>
          </span>
        </template>
      </nav>
      <section
        class="account"
        :title="
          collapsed ? t('user.layout.accountCollapsedTitle', { displayName, plan, credits }) : email
        "
        :aria-label="t('user.layout.accountTitle', { displayName, plan, credits })"
      >
        <AppIcon name="account" />
        <div>
          <strong>{{ displayName }}</strong>
          <small>{{ email }}</small>
          <div class="account__summary">
            <b>{{ plan }}</b
            ><span>{{ credits }} {{ t('user.layout.credits') }}</span>
          </div>
        </div>
      </section>
      <div class="sidebar-actions">
        <button data-test="user-sign-out" class="signout" type="button" @click="signOut">
          <AppIcon name="logout" /><span>{{ t('auth.signOut') }}</span>
        </button>
        <LanguageSelector class="sidebar-language" />
      </div>
    </aside>
    <main :inert="mobile ? true : undefined">
      <AnnouncementBanner v-if="accountStateReady && !restricted" />
      <section
        v-if="!accountStateReady && !accountStateError"
        class="account-loading"
        role="status"
      >
        {{ t('user.layout.checkingAccess') }}
      </section>
      <section
        v-else-if="!accountStateReady && accountStateError"
        class="account-access-error"
        role="alert"
      >
        <AppIcon name="shield" :size="28" />
        <div>
          <h1>{{ t('user.layout.accessVerificationFailed') }}</h1>
          <p>{{ t('user.layout.accessVerificationRetryHint') }}</p>
          <button type="button" @click="refreshAccountState">
            {{ t('user.layout.retryAccessVerification') }}
          </button>
        </div>
      </section>
      <section v-else-if="restricted" class="restriction-banner" role="alert">
        <div>
          <span>{{ t('user.layout.accountStatus') }}</span>
          <strong>{{ restrictionTitle }}</strong>
          <p>{{ restrictionReason }}</p>
        </div>
        <RouterLink v-if="!reportsRouteActive" to="/reports">{{
          t('user.layout.contactSupport')
        }}</RouterLink>
      </section>
      <RouterView v-if="accountStateReady && (!restricted || reportsRouteActive)" />
      <section
        v-else-if="accountStateReady && restricted"
        class="restricted-state"
        aria-labelledby="restricted-title"
      >
        <AppIcon name="shield" :size="30" />
        <h1 id="restricted-title">{{ t('user.layout.productUnavailable') }}</h1>
        <p>{{ restrictionReason }}</p>
        <RouterLink to="/reports">{{ t('user.layout.openSupportReport') }}</RouterLink>
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
.sidebar-actions {
  display: flex;
  align-items: center;
  gap: var(--vg-space-2);
}
.sidebar-actions .signout {
  flex: 1 1 auto;
}
.sidebar-language {
  width: 42px;
  min-width: 42px;
  height: 38px;
  flex: 0 0 auto;
}
.collapsed aside {
  align-items: center;
  padding-inline: 0.75rem;
}
.collapsed header,
.collapsed nav,
.collapsed .sidebar-actions,
.collapsed .signout,
.collapsed .sidebar-language {
  width: 100%;
}
.collapsed header {
  justify-content: center;
}
.collapsed header a {
  display: none;
}
.collapsed nav a,
.collapsed .signout,
.collapsed .sidebar-language {
  justify-content: center;
  padding-inline: 0;
}
.collapsed .sidebar-actions {
  flex-direction: column;
  gap: var(--vg-space-2);
}
.collapsed .sidebar-language {
  order: 1;
}
.collapsed .signout {
  order: 2;
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
.layout--graph > main {
  height: 100vh;
  overflow: hidden;
  padding: 0;
}
.account-loading {
  min-height: 12rem;
  display: grid;
  place-items: center;
  color: var(--vg-text-muted);
}
.account-access-error {
  min-height: 16rem;
  display: flex;
  align-items: flex-start;
  gap: var(--vg-space-4);
  padding: clamp(1.25rem, 4vw, 2rem);
  border: 1px solid color-mix(in srgb, var(--vg-warning) 45%, var(--vg-border));
  border-left: 4px solid var(--vg-warning);
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--vg-warning) 8%, var(--vg-surface));
  color: var(--vg-text);
}
.account-access-error h1 {
  margin: 0;
  font: 700 var(--vg-text-xl) var(--vg-font-display);
}
.account-access-error p {
  max-width: 42rem;
  margin: var(--vg-space-2) 0 var(--vg-space-4);
  color: var(--vg-text-muted);
}
.account-access-error button {
  min-height: 40px;
  padding: 0.5rem 0.8rem;
  border: 1px solid var(--vg-blue);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-blue);
  color: white;
  font-weight: 700;
  cursor: pointer;
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
    padding: var(--vg-space-4);
  }
  .layout--graph > main {
    height: 100vh;
    padding: 0;
  }
  .restriction-banner {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
