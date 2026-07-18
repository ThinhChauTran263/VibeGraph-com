<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAccountStore } from '@/stores/account'
import ErrorAlert from '@/components/ui/ErrorAlert.vue'

const accountStore = useAccountStore()
const isLoading = ref(!accountStore.usage)
const loadError = ref('')

interface SubscriptionUsage {
  limitMb?: number
  remainingMb?: number
  sourceStorageLimit?: number
  creditsRemaining?: number
  creditsLimit?: number
  creditsUsed?: number
}

const usage = computed(
  () => accountStore.usage as (typeof accountStore.usage & SubscriptionUsage) | null,
)
const storageLimitMb = computed(() => usage.value?.limitMb ?? usage.value?.sourceStorageLimit ?? 0)
const storageRemainingMb = computed(() =>
  usage.value?.remainingMb ?? Math.max(storageLimitMb.value - (usage.value?.sourceStorageUsed ?? 0), 0),
)
const remainingCredits = computed(() => {
  if (typeof usage.value?.creditsRemaining === 'number') return usage.value.creditsRemaining
  if (typeof usage.value?.creditsLimit === 'number' && typeof usage.value?.creditsUsed === 'number') {
    return Math.max(usage.value.creditsLimit - usage.value.creditsUsed, 0)
  }
  return null
})

async function loadUsage(): Promise<void> {
  if (accountStore.usage) {
    isLoading.value = false
    loadError.value = ''
    return
  }

  isLoading.value = true
  loadError.value = ''
  try {
    await accountStore.fetchUsage()
  } catch (error) {
    if (!accountStore.usage) {
      loadError.value = error instanceof Error ? error.message : 'Failed to load subscription data.'
    }
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  void loadUsage()
})
</script>

<template>
  <div class="subscription-view">
    <header class="page-header">
      <h1>Subscription</h1>
      <p>Review your current plan and account quota from the account usage API.</p>
    </header>

    <ErrorAlert
      v-if="loadError && !usage"
      role="alert"
      title="Subscription unavailable"
      :message="loadError"
    >
      <button
        data-test="retry-subscription"
        type="button"
        class="retry-button"
        :disabled="isLoading"
        @click="loadUsage"
      >
        Retry subscription
      </button>
    </ErrorAlert>
    <div v-else-if="isLoading && !usage" class="loading">Loading subscription data...</div>

    <section v-if="usage || (!isLoading && !loadError)" class="current-plan">
      <span class="current-plan__label">Current plan</span>
      <strong>{{ usage?.planName ?? 'Unavailable' }}</strong>
      <dl v-if="usage">
        <div>
          <dt>Plan code</dt>
          <dd>{{ usage.planCode }}</dd>
        </div>
        <div>
          <dt>Source storage quota</dt>
          <dd>{{ storageLimitMb }} MB</dd>
        </div>
        <div>
          <dt>Remaining storage</dt>
          <dd>{{ storageRemainingMb }} MB</dd>
        </div>
        <div>
          <dt>Remaining credits</dt>
          <dd>{{ remainingCredits === null ? 'Unavailable' : `${remainingCredits} credits` }}</dd>
        </div>
      </dl>
      <p v-else>Plan details are unavailable until the account usage API responds.</p>
    </section>

    <section v-if="!isLoading && !loadError" class="empty-state">
      A public plan catalog is not available from the current user API. Upgrade options, including
      Enterprise contact sales, remain unavailable until the backend exposes that contract.
    </section>
  </div>
</template>

<style scoped>
.subscription-view {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-6);
}

.page-header h1 {
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

.loading {
  color: var(--vg-text-dim);
}

.retry-button {
  min-height: 38px;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--vg-danger);
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-danger);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}
</style>
