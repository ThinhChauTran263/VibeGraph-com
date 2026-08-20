<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAccountStore } from '@/stores/account'
import { useSilentRefresh } from '@/composables/useSilentRefresh'
import ErrorAlert from '@/components/ui/ErrorAlert.vue'
import QuotaMeter from '@/components/ui/QuotaMeter.vue'
import { displayPlanName } from '@/lib/planDisplay'

const accountStore = useAccountStore()
const { t, te, locale } = useI18n({ useScope: 'global' })
const isUsageLoading = ref(!accountStore.usage)
const isLedgerLoading = ref(false)
const usageError = ref('')
const ledgerError = ref('')

interface UsageDisplay {
  usedMb?: number
  limitMb?: number
  usedBytes?: number
  limitBytes?: number
  sourceStorageUsed?: number
  sourceStorageLimit?: number
  creditsUsed?: number
  creditsLimit?: number
  creditsRemaining?: number
}

const usage = computed(
  () => accountStore.usage as (typeof accountStore.usage & UsageDisplay) | null,
)
const usedMb = computed(() => usage.value?.usedMb ?? usage.value?.sourceStorageUsed ?? 0)
const limitMb = computed(() => usage.value?.limitMb ?? usage.value?.sourceStorageLimit ?? 0)
const BYTES_PER_MB = 1024 * 1024
// Prefer the exact byte counters; fall back to the rounded MB fields for older backends.
const usedBytes = computed(() => usage.value?.usedBytes ?? usedMb.value * BYTES_PER_MB)
const limitBytes = computed(() => usage.value?.limitBytes ?? limitMb.value * BYTES_PER_MB)
const creditBalanceLabel = computed(() => {
  if (typeof usage.value?.creditsRemaining === 'number') {
    return `${usage.value.creditsRemaining} ${t('user.usage.credits')}`
  }
  if (
    typeof usage.value?.creditsUsed !== 'number' ||
    typeof usage.value?.creditsLimit !== 'number'
  ) {
    return t('user.subscription.unavailableValue')
  }
  return `${Math.max(usage.value.creditsLimit - usage.value.creditsUsed, 0)} ${t('user.usage.credits')}`
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
    isUsageLoading.value = false
    usageError.value = ''
    return
  }

  isUsageLoading.value = true
  usageError.value = ''
  try {
    await accountStore.fetchUsage()
  } catch (error) {
    if (!accountStore.usage) {
      usageError.value = error instanceof Error ? error.message : t('user.usage.loadFallback')
    }
  } finally {
    isUsageLoading.value = false
  }
}

async function loadLedger(): Promise<void> {
  isLedgerLoading.value = true
  ledgerError.value = ''
  try {
    await accountStore.fetchCreditLedger(10)
  } catch (error) {
    ledgerError.value = error instanceof Error ? error.message : t('user.usage.ledgerFallback')
  } finally {
    isLedgerLoading.value = false
  }
}

onMounted(() => {
  void loadUsage()
  void loadLedger()
})

// Kept alive by UserLayout: re-activation silently reconciles with the server so
// new ledger rows / credit changes appear without any reload flash.
useSilentRefresh(async () => {
  const [usageResult, ledgerResult] = await Promise.allSettled([
    accountStore.fetchUsage(),
    accountStore.fetchCreditLedger(10),
  ])
  if (usageResult.status === 'fulfilled') {
    usageError.value = ''
    isUsageLoading.value = false
  }
  if (ledgerResult.status === 'fulfilled') {
    ledgerError.value = ''
    isLedgerLoading.value = false
  }
})

function formatOperation(operationCode: string): string {
  const key = `user.usage.operations.${operationCode}`
  if (te(key)) return t(key)
  return operationCode
}

function formatDate(value: string | null): string {
  if (!value) return '-'
  return new Intl.DateTimeFormat(locale.value, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="usage-view">
    <header class="page-header">
      <h1>{{ t('user.usage.title') }}</h1>
      <p>{{ t('user.usage.description') }}</p>
    </header>

    <ErrorAlert
      v-if="usageError"
      role="alert"
      :title="t('user.usage.unavailable')"
      :message="usageError"
    >
      <button
        data-test="retry-usage"
        type="button"
        class="retry-button"
        :disabled="isUsageLoading"
        @click="loadUsage"
      >
        {{ t('user.usage.retry') }}
      </button>
    </ErrorAlert>
    <div v-if="isUsageLoading" class="loading">{{ t('user.usage.loading') }}</div>
    <div v-else-if="usage" class="usage-grid">
      <section class="usage-card">
        <span class="usage-card__label">{{ t('user.usage.currentPlan') }}</span>
        <strong>{{ planLabel }}</strong>
        <span class="usage-card__meta">{{ t('user.usage.activePlan') }}</span>
      </section>

      <section class="usage-card">
        <span class="usage-card__label">{{ t('user.usage.creditBalance') }}</span>
        <strong>{{ creditBalanceLabel }}</strong>
        <span class="usage-card__meta">
          {{
            t('user.usage.usedCycle', {
              used: usage.creditsUsed ?? 0,
              limit: usage.creditsLimit ?? 0,
            })
          }}
        </span>
      </section>

      <section class="usage-card usage-card--wide">
        <div class="section-heading">
          <h3>{{ t('user.usage.sourceStorage') }}</h3>
        </div>
        <QuotaMeter :used-bytes="usedBytes" :total-bytes="limitBytes" />
      </section>

      <section class="usage-card usage-card--wide">
        <div class="section-heading">
          <h3>{{ t('user.usage.ledger') }}</h3>
        </div>
        <ErrorAlert
          v-if="ledgerError"
          role="alert"
          :title="t('user.usage.activityUnavailable')"
          :message="ledgerError"
        >
          <button
            data-test="retry-ledger"
            type="button"
            class="retry-button"
            :disabled="isLedgerLoading"
            @click="loadLedger"
          >
            {{ t('user.usage.retryLedger') }}
          </button>
        </ErrorAlert>
        <div v-else-if="isLedgerLoading" class="loading">{{ t('user.usage.loadingActivity') }}</div>
        <div v-else-if="accountStore.creditLedger.length" class="ledger-list">
          <article v-for="entry in accountStore.creditLedger" :key="entry.id" class="ledger-row">
            <div>
              <strong>{{ formatOperation(entry.operationCode) }}</strong>
              <span
                >{{ entry.source
                }}<template v-if="entry.projectId"> - {{ entry.projectId }}</template></span
              >
            </div>
            <div class="ledger-row__meta">
              <span
                :class="[
                  'credit-delta',
                  entry.creditsDelta < 0 ? 'credit-delta--debit' : 'credit-delta--credit',
                ]"
              >
                {{ entry.creditsDelta > 0 ? '+' : '' }}{{ entry.creditsDelta }}
                {{ t('user.usage.credits') }}
              </span>
              <time>{{ formatDate(entry.createdAt) }}</time>
            </div>
          </article>
        </div>
        <div v-else class="empty-state">{{ t('user.usage.emptyActivity') }}</div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.usage-view {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-6);
}

.page-header h1 {
  margin: 0 0 var(--vg-space-1);
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}

.page-header p {
  margin: 0;
  color: var(--vg-text-muted);
}

.usage-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--vg-space-4);
}

.usage-card {
  min-width: 0;
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  box-shadow: var(--vg-shadow-sm);
  padding: var(--vg-space-5);
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
}

.usage-card--wide {
  grid-column: 1 / -1;
}

.usage-card__label,
.usage-card__meta {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

/* Direct children only: the stat cards (plan, balance) get the big mono figure,
   while ledger rows style their own <strong> small (see .ledger-row). */
.usage-card > strong {
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-xl);
  overflow-wrap: anywhere;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--vg-space-3);
}

.section-heading h3 {
  margin: 0;
  color: var(--vg-text);
  font-size: var(--vg-text-lg);
}

.empty-state {
  padding: var(--vg-space-6);
  border: 1px dashed var(--vg-border);
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text-muted);
  text-align: center;
}

.ledger-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  overflow: hidden;
}

.ledger-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-3);
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid var(--vg-border);
}

.ledger-row:last-child {
  border-bottom: 0;
}

.ledger-row > div {
  min-width: 0;
}

.ledger-row strong,
.ledger-row span,
.ledger-row time {
  overflow-wrap: anywhere;
}

/* Compact list typography: operation name reads as a list title, not a stat. */
.ledger-row strong {
  display: block;
  color: var(--vg-text);
  font-family: var(--vg-font-body);
  font-size: var(--vg-text-sm);
  font-weight: 600;
}

.ledger-row span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}

.ledger-row time {
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}

.ledger-row__meta {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.125rem;
}

.credit-delta {
  font-size: var(--vg-text-sm);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.credit-delta--debit {
  color: var(--vg-danger, #b42318);
}

.credit-delta--credit {
  color: var(--vg-success, #027a48);
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

@media (max-width: 700px) {
  .usage-grid {
    grid-template-columns: 1fr;
  }

  .section-heading {
    flex-direction: column;
    align-items: flex-start;
  }

  .ledger-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .ledger-row__meta {
    min-width: 0;
    align-items: flex-start;
  }
}
</style>
