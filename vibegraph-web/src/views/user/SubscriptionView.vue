<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAccountStore } from '@/stores/account'
import { useSilentRefresh } from '@/composables/useSilentRefresh'
import ErrorAlert from '@/components/ui/ErrorAlert.vue'
import { displayPlanName } from '@/lib/planDisplay'

const accountStore = useAccountStore()
const { t } = useI18n({ useScope: 'global' })
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
const storageRemainingMb = computed(
  () =>
    usage.value?.remainingMb ??
    Math.max(storageLimitMb.value - (usage.value?.sourceStorageUsed ?? 0), 0),
)
const remainingCredits = computed(() => {
  if (typeof usage.value?.creditsRemaining === 'number') return usage.value.creditsRemaining
  if (
    typeof usage.value?.creditsLimit === 'number' &&
    typeof usage.value?.creditsUsed === 'number'
  ) {
    return Math.max(usage.value.creditsLimit - usage.value.creditsUsed, 0)
  }
  return null
})
const planLabel = computed(() =>
  displayPlanName(
    t,
    usage.value?.planCode,
    usage.value?.planName,
    t('user.subscription.unavailableValue'),
  ),
)

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
      loadError.value = error instanceof Error ? error.message : t('user.subscription.loadFallback')
    }
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  void loadUsage()
})

// Kept alive by UserLayout: plan/credit changes appear on re-activation even
// though loadUsage() short-circuits once cached data exists.
useSilentRefresh(() => accountStore.fetchUsage().catch(() => undefined))
</script>

<template>
  <div class="subscription-view">
    <header class="page-header">
      <h1>{{ t('user.subscription.title') }}</h1>
      <p>{{ t('user.subscription.description') }}</p>
    </header>

    <ErrorAlert
      v-if="loadError && !usage"
      role="alert"
      :title="t('user.subscription.unavailable')"
      :message="loadError"
    >
      <button
        data-test="retry-subscription"
        type="button"
        class="retry-button"
        :disabled="isLoading"
        @click="loadUsage"
      >
        {{ t('user.subscription.retry') }}
      </button>
    </ErrorAlert>
    <div v-else-if="isLoading && !usage" class="loading">{{ t('user.subscription.loading') }}</div>

    <section v-if="usage || (!isLoading && !loadError)" class="current-plan">
      <span class="current-plan__label">{{ t('user.subscription.currentPlan') }}</span>
      <strong>{{ planLabel }}</strong>
      <dl v-if="usage">
        <div>
          <dt>{{ t('user.subscription.planCode') }}</dt>
          <dd>{{ planLabel }}</dd>
        </div>
        <div>
          <dt>{{ t('user.subscription.sourceStorage') }}</dt>
          <dd>{{ storageLimitMb }} MB</dd>
        </div>
        <div>
          <dt>{{ t('user.subscription.remainingStorage') }}</dt>
          <dd>{{ storageRemainingMb }} MB</dd>
        </div>
        <div>
          <dt>{{ t('user.subscription.remainingCredits') }}</dt>
          <dd>
            {{
              remainingCredits === null
                ? t('user.subscription.unavailableValue')
                : `${remainingCredits} ${t('user.subscription.credits')}`
            }}
          </dd>
        </div>
      </dl>
      <p v-else>{{ t('user.subscription.detailsUnavailable') }}</p>
    </section>

    <section v-if="!isLoading && !loadError" class="empty-state">
      {{ t('user.subscription.catalogUnavailable') }}
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
