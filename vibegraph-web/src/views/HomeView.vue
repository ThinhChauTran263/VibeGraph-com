<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAccountStore } from '@/stores/account'
import { useSilentRefresh } from '@/composables/useSilentRefresh'
import AppIcon from '@/components/ui/AppIcon.vue'
import { displayPlanName } from '@/lib/planDisplay'

const router = useRouter()
const account = useAccountStore()
const { t } = useI18n({ useScope: 'global' })
const name = computed(
  () => account.profile?.displayName || account.profile?.email?.split('@')[0] || t('user.layout.account'),
)
const remainingCredits = computed(() => {
  const usage = account.usage as (typeof account.usage & { creditsRemaining?: number }) | null
  if (typeof usage?.creditsRemaining === 'number') return usage.creditsRemaining.toLocaleString()
  const limit = usage?.creditsLimit
  const used = usage?.creditsUsed
  return typeof limit === 'number' && typeof used === 'number'
    ? Math.max(limit - used, 0).toLocaleString()
    : t('user.overview.unavailable')
})
const planLabel = computed(() =>
  displayPlanName(
    t,
    account.usage?.planCode,
    account.usage?.planName,
    t('user.overview.unavailable'),
  ),
)

onMounted(() => {
  const tasks: Promise<unknown>[] = []
  if (!account.profile) tasks.push(account.fetchProfile())
  if (!account.projectsLoaded) tasks.push(account.fetchProjects())
  if (!account.usage) tasks.push(account.fetchUsage())
  void Promise.allSettled(tasks)
})

// Kept alive by UserLayout: project/credit counters reconcile in the
// background on re-activation (profile/usage are already polled by the layout).
useSilentRefresh(() => account.fetchProjects({ force: true }).catch(() => undefined))
</script>

<template>
  <section class="overview" aria-labelledby="overview-title">
    <header class="overview__header">
      <span class="eyebrow">{{ t('user.overview.eyebrow') }}</span>
      <h1 id="overview-title">{{ t('user.overview.welcome', { name }) }}</h1>
      <p>{{ t('user.overview.description') }}</p>
    </header>

    <section class="summary" :aria-label="t('user.overview.summaryLabel')">
      <article>
        <AppIcon name="repository" :size="24" /><span>{{ t('user.overview.repositories') }}</span
        ><strong>{{ account.projects.length }}</strong
        ><small>{{ t('user.overview.importedProjects') }}</small>
      </article>
      <article>
        <AppIcon name="usage" :size="24" /><span>{{ t('user.overview.credits') }}</span
        ><strong>{{ remainingCredits }}</strong
        ><small v-if="typeof account.usage?.creditsUsed === 'number'">{{
          t('user.overview.usedThisMonth', { count: account.usage.creditsUsed.toLocaleString() })
        }}</small
        ><small v-else>{{ t('user.overview.usageUnavailable') }}</small>
      </article>
      <article>
        <AppIcon name="subscription" :size="24" /><span>{{ t('user.overview.plan') }}</span
        ><strong>{{ planLabel }}</strong
        ><small>{{ t('user.overview.currentPlan') }}</small>
      </article>
    </section>

    <section class="quick" aria-labelledby="quick-heading">
      <div>
        <span class="eyebrow">{{ t('user.overview.nextStep') }}</span>
        <h2 id="quick-heading">{{ t('user.overview.quickActions') }}</h2>
      </div>
      <div class="quick__actions">
        <button
          data-test="quick-repositories"
          type="button"
          @click="router.push({ name: 'projects', query: { import: 'new' } })"
        >
          <span class="quick__icon" aria-hidden="true"
            ><AppIcon name="repository" :size="22"
          /></span>
          <span>{{ t('user.overview.newRepository') }}</span>
        </button>
        <button data-test="quick-api-keys" type="button" @click="router.push({ name: 'api-keys' })">
          <span class="quick__icon" aria-hidden="true"><AppIcon name="key" :size="22" /></span>
          <span>{{ t('user.overview.createApiKey') }}</span>
        </button>
        <button data-test="quick-reports" type="button" @click="router.push({ name: 'reports' })">
          <span class="quick__icon" aria-hidden="true"><AppIcon name="reports" :size="22" /></span>
          <span>{{ t('user.overview.openReports') }}</span>
        </button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-5);
  color: var(--vg-text);
}
.overview__header {
  max-width: 48rem;
}
.eyebrow {
  color: var(--vg-blue-bright);
  font: 700 var(--vg-text-xs) var(--vg-font-display);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.overview h1 {
  margin: 0.25rem 0;
  font: 700 clamp(1.75rem, 2.4vw, 2rem) var(--vg-font-display);
  letter-spacing: -0.025em;
  text-wrap: balance;
}
.overview p,
.summary small {
  color: var(--vg-text-muted);
}
.summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--vg-space-3);
}
.summary article {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  align-items: center;
  column-gap: var(--vg-space-2);
  row-gap: 0.2rem;
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-grad-surface);
  box-shadow: var(--vg-shadow-sm);
}
.summary article:first-child {
  border-color: rgba(96, 165, 250, 0.35);
}
.summary svg {
  color: var(--vg-blue-bright);
}
.summary span {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}
.summary strong,
.summary small {
  grid-column: 2;
}
.summary strong {
  font: 700 var(--vg-text-xl) var(--vg-font-display);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.summary small {
  font-size: var(--vg-text-xs);
  line-height: 1.35;
  text-wrap: pretty;
}
.quick {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--vg-space-2);
  padding-top: var(--vg-space-4);
  border-top: 1px solid var(--vg-border);
}
.quick h2 {
  margin: 0.2rem 0;
  font: 700 var(--vg-text-xl) var(--vg-font-display);
}
.quick__actions {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--vg-space-3);
}
.quick button {
  min-height: 100px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--vg-space-3);
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
  color: var(--vg-text);
  font: 700 var(--vg-text-base) var(--vg-font-body);
  text-align: left;
  cursor: pointer;
}
.quick__icon {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  flex: 0 0 36px;
  border: 1px solid rgba(96, 165, 250, 0.25);
  border-radius: 8px;
  background: rgba(59, 130, 246, 0.1);
  color: var(--vg-blue-bright);
}
.quick button > span:last-child {
  align-self: center;
}
.quick button:hover {
  border-color: var(--vg-blue-bright);
  background: var(--vg-surface-3);
}
@media (max-width: 760px) {
  .summary {
    grid-template-columns: 1fr;
  }
  .quick__actions {
    grid-template-columns: 1fr;
  }
  .quick button {
    min-height: 72px;
  }
}
</style>
