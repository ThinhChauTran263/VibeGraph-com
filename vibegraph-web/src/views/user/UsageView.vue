<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAccountStore } from '@/stores/account'
import QuotaMeter from '@/components/ui/QuotaMeter.vue'

const accountStore = useAccountStore()

interface UsageDisplay {
  usedMb?: number
  limitMb?: number
  remainingMb?: number
  sourceStorageUsed?: number
  sourceStorageLimit?: number
  creditsUsed?: number
  creditsLimit?: number
  creditsRemaining?: number
}

const usage = computed(() => accountStore.usage as (typeof accountStore.usage & UsageDisplay) | null)
const usedMb = computed(() => usage.value?.usedMb ?? usage.value?.sourceStorageUsed ?? 0)
const limitMb = computed(() => usage.value?.limitMb ?? usage.value?.sourceStorageLimit ?? 0)
const remainingMb = computed(() =>
  usage.value?.remainingMb ?? Math.max(limitMb.value - usedMb.value, 0),
)
const creditBalanceLabel = computed(() => {
  if (typeof usage.value?.creditsRemaining === 'number') {
    return `${usage.value.creditsRemaining} credits`
  }
  if (
    typeof usage.value?.creditsUsed !== 'number' ||
    typeof usage.value?.creditsLimit !== 'number'
  ) {
    return 'Unavailable'
  }
  return `${Math.max(usage.value.creditsLimit - usage.value.creditsUsed, 0)} credits`
})

onMounted(async () => {
  await Promise.all([
    accountStore.usage ? Promise.resolve() : accountStore.fetchUsage(),
    accountStore.fetchCreditLedger(10),
  ])
})

function formatOperation(operationCode: string): string {
  return operationCode
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function formatDate(value: string | null): string {
  if (!value) return '-'
  return new Intl.DateTimeFormat(undefined, {
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
      <h1>Usage</h1>
      <p>Current plan, source storage quota, and credit availability from account APIs.</p>
    </header>

    <div v-if="usage" class="usage-grid">
      <section class="usage-card">
        <span class="usage-card__label">Current plan</span>
        <strong>{{ usage.planName }}</strong>
        <span class="usage-card__meta">{{ usage.planCode }}</span>
      </section>

      <section class="usage-card">
        <span class="usage-card__label">Credit balance</span>
        <strong>{{ creditBalanceLabel }}</strong>
        <span class="usage-card__meta">
          {{ usage.creditsUsed ?? 0 }} /
          {{ usage.creditsLimit ?? 0 }} credits used this cycle
        </span>
      </section>

      <section class="usage-card usage-card--wide">
        <div class="section-heading">
          <h3>Source storage quota</h3>
          <span>{{ remainingMb }} MB remaining</span>
        </div>
        <QuotaMeter
          :used="usedMb"
          :total="limitMb"
          unit="MB"
        />
      </section>

      <section class="usage-card usage-card--wide">
        <div class="section-heading">
          <h3>Recent credit ledger</h3>
        </div>
        <div v-if="accountStore.creditLedger.length" class="ledger-list">
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
                {{ entry.creditsDelta > 0 ? '+' : '' }}{{ entry.creditsDelta }} credits
              </span>
              <time>{{ formatDate(entry.createdAt) }}</time>
            </div>
          </article>
        </div>
        <div v-else class="empty-state">No credit activity yet.</div>
      </section>
    </div>
    <div v-else class="loading">Loading usage data...</div>
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
.usage-card__meta,
.section-heading span {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

.usage-card strong {
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
  min-height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-4);
  padding: var(--vg-space-3) var(--vg-space-4);
  border-bottom: 1px solid var(--vg-border);
}

.ledger-row:last-child {
  border-bottom: 0;
}

.ledger-row strong,
.ledger-row span,
.ledger-row time {
  overflow-wrap: anywhere;
}

.ledger-row strong {
  display: block;
  color: var(--vg-text);
}

.ledger-row span,
.ledger-row time {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

.ledger-row__meta {
  min-width: 160px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--vg-space-1);
}

.credit-delta {
  min-width: 96px;
  font-weight: 700;
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
