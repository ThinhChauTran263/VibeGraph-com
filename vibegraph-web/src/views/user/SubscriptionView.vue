<script setup lang="ts">
import { onMounted } from 'vue'
import { useAccountStore } from '@/stores/account'

const accountStore = useAccountStore()

onMounted(async () => {
  if (!accountStore.usage) {
    await accountStore.fetchUsage()
  }
})
</script>

<template>
  <div class="subscription-view">
    <header class="page-header">
      <h2>Subscription</h2>
      <p>Review your current plan and account quota from the account usage API.</p>
    </header>

    <section class="current-plan">
      <span class="current-plan__label">Current plan</span>
      <strong>{{ accountStore.usage?.planName ?? 'Unavailable' }}</strong>
      <dl v-if="accountStore.usage">
        <div>
          <dt>Plan code</dt>
          <dd>{{ accountStore.usage.planCode }}</dd>
        </div>
        <div>
          <dt>Source storage quota</dt>
          <dd>{{ Math.round(accountStore.usage.limitBytes / 1024 / 1024) }} MB</dd>
        </div>
        <div>
          <dt>Remaining storage</dt>
          <dd>{{ Math.round(accountStore.usage.remainingBytes / 1024 / 1024) }} MB</dd>
        </div>
      </dl>
      <p v-else>Plan details are unavailable until the account usage API responds.</p>
    </section>

    <section class="empty-state">
      Plan catalog management is available in the admin console. The user app does not expose a
      public plan catalog or billing-management API yet.
    </section>
  </div>
</template>

<style scoped>
.subscription-view {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-6);
}

.page-header h2 {
  margin: 0 0 var(--vg-space-1);
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}

.page-header p,
.current-plan p,
.empty-state,
dt {
  margin: 0;
  color: var(--vg-text-muted);
}

.current-plan,
.empty-state {
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
  box-shadow: var(--vg-shadow-sm);
}

.current-plan {
  padding: var(--vg-space-5);
}

.current-plan__label {
  display: block;
  margin-bottom: var(--vg-space-1);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

.current-plan strong {
  display: block;
  margin-bottom: var(--vg-space-4);
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-xl);
}

.current-plan dl {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--vg-space-3);
  margin: 0;
}

.current-plan dl div {
  min-width: 0;
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg-elev);
}

dd {
  margin: var(--vg-space-1) 0 0;
  color: var(--vg-text);
  font-weight: 700;
}

.empty-state {
  padding: var(--vg-space-5);
  text-align: center;
}
</style>
