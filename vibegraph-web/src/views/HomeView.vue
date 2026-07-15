<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAccountStore } from '@/stores/account'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()
const account = useAccountStore()
const name = computed(
  () => account.profile?.displayName || account.profile?.email?.split('@')[0] || 'there',
)
const remainingCredits = computed(() => {
  const limit = account.usage?.creditsLimit
  const used = account.usage?.creditsUsed
  return typeof limit === 'number' && typeof used === 'number'
    ? Math.max(limit - used, 0).toLocaleString()
    : 'Unavailable'
})

onMounted(() => {
  void Promise.allSettled([account.fetchProfile(), account.fetchProjects(), account.fetchUsage()])
})
</script>

<template>
  <main class="overview">
    <header class="overview__header">
      <span class="eyebrow">Overview</span>
      <h1>Welcome back, {{ name }}</h1>
      <p>A focused summary of your VibeGraph workspace.</p>
    </header>

    <section class="summary" aria-label="Workspace summary">
      <article>
        <AppIcon name="repository" :size="24" /><span>Repositories</span
        ><strong>{{ account.projects.length }}</strong
        ><small>Imported projects</small>
      </article>
      <article>
        <AppIcon name="usage" :size="24" /><span>Credits</span
        ><strong>{{ remainingCredits }}</strong
        ><small v-if="typeof account.usage?.creditsUsed === 'number'"
          >{{ account.usage.creditsUsed.toLocaleString() }} used this month</small
        ><small v-else>Usage details unavailable</small>
      </article>
      <article>
        <AppIcon name="subscription" :size="24" /><span>Plan</span
        ><strong>{{ account.usage?.planName || account.usage?.planCode || 'Unavailable' }}</strong
        ><small>Your current workspace plan</small>
      </article>
    </section>

    <section class="quick" aria-labelledby="quick-heading">
      <div>
        <span class="eyebrow">Next step</span>
        <h2 id="quick-heading">Quick actions</h2>
      </div>
      <div class="quick__actions">
        <button type="button" @click="router.push({ name: 'projects', query: { import: 'new' } })">
          <span class="quick__icon" aria-hidden="true"
            ><AppIcon name="repository" :size="22"
          /></span>
          <span>New repository</span>
        </button>
        <button type="button" @click="router.push({ name: 'api-keys' })">
          <span class="quick__icon" aria-hidden="true"><AppIcon name="key" :size="22" /></span>
          <span>Create API key</span>
        </button>
        <button type="button" @click="router.push({ name: 'reports' })">
          <span class="quick__icon" aria-hidden="true"><AppIcon name="reports" :size="22" /></span>
          <span>Open reports</span>
        </button>
      </div>
    </section>
  </main>
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
