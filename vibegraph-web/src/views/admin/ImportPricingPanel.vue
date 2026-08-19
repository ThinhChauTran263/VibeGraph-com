<script setup lang="ts">
/**
 * ImportPricingPanel - dedicated editor for tiered import billing.
 *
 * Each import method (archive upload, GitHub, CLI push) owns its own
 * small/medium/large/xlarge tier set: a max-.java-files bound and a fixed
 * credit cost per tier. Saving replaces the whole set of one method
 * atomically (PUT /api/admin/import-pricing/{operationCode}).
 */
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminStore } from '@/stores/admin'

const { t } = useI18n({ useScope: 'global' })
const adminStore = useAdminStore()

interface TierDraft {
  tierCode: string
  /** null = unlimited top tier */
  maxFiles: number | null
  credits: number
}

interface OperationDraft {
  operationCode: string
  tiers: TierDraft[]
  saving: boolean
  message: string
  error: boolean
}

/** Fixed presentation order; mirrors AdminImportPricingManagementService. */
const OPERATION_ORDER = ['IMPORT_ARCHIVE', 'IMPORT_GITHUB', 'CLI_PUSH']

const drafts = ref<OperationDraft[]>([])
const loading = ref(true)
const errorMsg = ref('')

onMounted(load)

async function load(): Promise<void> {
  try {
    await adminStore.fetchImportPricing()
    drafts.value = OPERATION_ORDER.map((code) => {
      const entry = adminStore.importPricing.find((p) => p.operationCode === code)
      return {
        operationCode: code,
        saving: false,
        message: '',
        error: false,
        tiers: (entry?.tiers ?? []).map((tier) => ({ ...tier })),
      }
    })
    errorMsg.value = ''
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : t('admin.importPricing.errors.load')
  } finally {
    loading.value = false
  }
}

function isLastTier(draft: OperationDraft, index: number): boolean {
  return index === draft.tiers.length - 1
}

/** Mirrors the backend validation so users get instant feedback. */
function validate(draft: OperationDraft): string | null {
  let previous: number | null = null
  for (let i = 0; i < draft.tiers.length; i++) {
    const tier = draft.tiers[i]
    if (!tier) continue
    if (Number(tier.credits) < 0) {
      return t('admin.importPricing.errors.negativeCredits')
    }
    if (tier.maxFiles === null) {
      if (!isLastTier(draft, i)) {
        return t('admin.importPricing.errors.unlimitedMustBeLast')
      }
      continue
    }
    const bound = Math.round(Number(tier.maxFiles) || 0)
    if (bound <= 0) {
      return t('admin.importPricing.errors.invalidBound')
    }
    if (previous !== null && bound <= previous) {
      return t('admin.importPricing.errors.boundsAscending')
    }
    previous = bound
  }
  return null
}

async function save(draft: OperationDraft): Promise<void> {
  const problem = validate(draft)
  if (problem) {
    draft.message = problem
    draft.error = true
    return
  }
  draft.saving = true
  draft.message = ''
  draft.error = false
  try {
    await adminStore.saveImportPricing(
      draft.operationCode,
      draft.tiers.map((tier) => ({ ...tier, credits: Math.round(Number(tier.credits) || 0) })),
    )
    draft.message = t('admin.importPricing.saved')
  } catch (e: unknown) {
    draft.error = true
    draft.message = e instanceof Error ? e.message : t('admin.importPricing.errors.save')
  } finally {
    draft.saving = false
  }
}
</script>

<template>
  <section class="import-pricing" aria-labelledby="import-pricing-heading">
    <header class="import-pricing__header">
      <h2 id="import-pricing-heading">{{ t('admin.importPricing.title') }}</h2>
      <p class="import-pricing__hint">{{ t('admin.importPricing.hint') }}</p>
    </header>

    <p v-if="loading" class="import-pricing__status">{{ t('admin.importPricing.loading') }}</p>
    <p v-else-if="errorMsg" class="import-pricing__status import-pricing__status--error" role="alert">
      {{ errorMsg }}
    </p>

    <div v-else class="import-pricing__grid">
      <article
        v-for="draft in drafts"
        :key="draft.operationCode"
        class="import-pricing__method"
      >
        <h3 class="import-pricing__method-title">
          {{ t(`admin.importPricing.operations.${draft.operationCode}`) }}
        </h3>

        <table class="import-pricing__table">
          <thead>
            <tr>
              <th scope="col">{{ t('admin.importPricing.columns.tier') }}</th>
              <th scope="col">{{ t('admin.importPricing.columns.maxFiles') }}</th>
              <th scope="col">{{ t('admin.importPricing.columns.credits') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(tier, index) in draft.tiers" :key="tier.tierCode">
              <td data-label="tier">
                {{ t(`admin.importPricing.tiers.${tier.tierCode}`) }}
              </td>
              <td data-label="maxFiles">
                <template v-if="tier.maxFiles === null">
                  <span class="import-pricing__unlimited" :title="t('admin.importPricing.unlimited')">
                    ∞
                  </span>
                </template>
                <input
                  v-else
                  v-model.number="tier.maxFiles"
                  class="import-pricing__input"
                  type="number"
                  min="1"
                  :disabled="draft.saving"
                  :aria-label="`${t(`admin.importPricing.tiers.${tier.tierCode}`)} ${t('admin.importPricing.columns.maxFiles')}`"
                />
              </td>
              <td data-label="credits">
                <input
                  v-model.number="tier.credits"
                  class="import-pricing__input"
                  type="number"
                  min="0"
                  :disabled="draft.saving"
                  :aria-label="`${t(`admin.importPricing.tiers.${tier.tierCode}`)} ${t('admin.importPricing.columns.credits')}`"
                />
              </td>
            </tr>
          </tbody>
        </table>

        <div class="import-pricing__footer">
          <button
            class="import-pricing__save"
            type="button"
            :disabled="draft.saving"
            @click="save(draft)"
          >
            {{ draft.saving ? t('admin.importPricing.saving') : t('admin.importPricing.save') }}
          </button>
          <p
            v-if="draft.message"
            class="import-pricing__message"
            :class="{ 'import-pricing__message--error': draft.error, 'import-pricing__message--ok': !draft.error }"
            role="status"
          >
            {{ draft.message }}
          </p>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.import-pricing {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.import-pricing__header h2 {
  margin: 0 0 0.25rem;
  font-size: var(--vg-text-lg);
  font-weight: 600;
}

.import-pricing__hint {
  margin: 0;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.import-pricing__status {
  margin: 0;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.import-pricing__status--error {
  color: #fca5a5;
}

.import-pricing__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
}

.import-pricing__method {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-lg);
  background: var(--vg-grad-surface);
}

.import-pricing__method-title {
  margin: 0;
  font-size: var(--vg-text-base);
  font-weight: 600;
}

.import-pricing__table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--vg-text-sm);
}

.import-pricing__table th {
  text-align: left;
  font-weight: 500;
  color: var(--vg-text-muted);
  padding: 0.25rem 0.4rem;
}

.import-pricing__table td {
  padding: 0.3rem 0.4rem;
}

.import-pricing__input {
  width: 100%;
  max-width: 8rem;
  font: inherit;
  color: var(--vg-text);
  padding: 0.35rem 0.55rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius-sm);
  background: rgba(7, 11, 22, 0.55);
}

.import-pricing__input:focus {
  outline: none;
  border-color: var(--vg-cyan);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.16);
}

.import-pricing__input:disabled {
  opacity: 0.55;
}

.import-pricing__unlimited {
  font-size: var(--vg-text-lg);
  color: var(--vg-text-muted);
}

.import-pricing__footer {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.import-pricing__save {
  font: inherit;
  font-weight: 600;
  padding: 0.45rem 1rem;
  border-radius: var(--vg-radius-pill);
  border: 1px solid transparent;
  background: linear-gradient(135deg, #22d3ee, #0891b2);
  color: #04212b;
  cursor: pointer;
}

.import-pricing__save:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.import-pricing__message {
  margin: 0;
  font-size: var(--vg-text-sm);
}

.import-pricing__message--ok {
  color: var(--vg-green-bright);
}

.import-pricing__message--error {
  color: #fca5a5;
}
</style>
