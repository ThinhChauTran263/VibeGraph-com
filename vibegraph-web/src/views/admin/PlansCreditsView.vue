<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminStore } from '@/stores/admin'
import type {
  AdminPlan,
  AdminPlanRequest,
  AdminPricingRule,
  AdminPricingRuleRequest,
} from '@/types/api'
import AdminConfirmDialog from '@/components/admin/AdminConfirmDialog.vue'

const { locale, t } = useI18n({ useScope: 'global' })
const adminStore = useAdminStore()
const loading = ref(true)
const saving = ref(false)
const errorMsg = ref('')
const editingPlanCode = ref<string | null>(null)
const editingRuleCode = ref<string | null>(null)

type PendingConfirm = {
  title: string
  message: string
  confirmLabel: string
  tone?: 'default' | 'danger'
  action: () => Promise<void>
  fallback: string
}

const pendingConfirm = ref<PendingConfirm | null>(null)

const emptyPlan: AdminPlanRequest = {
  code: '',
  name: '',
  storageLimitMb: 0,
  apiKeyLimit: 0,
  monthlyCreditLimit: 0,
  contactSalesRequired: false,
  active: true,
  sortOrder: 0,
}

const emptyRule: AdminPricingRuleRequest = {
  operationCode: '',
  displayName: '',
  baseCredits: 0,
  perFileCredits: 0,
  perMbCredits: 0,
  per1kNodesCredits: 0,
  minimumCredits: 0,
  active: true,
}

const planForm = ref<AdminPlanRequest>({ ...emptyPlan })
const ruleForm = ref<AdminPricingRuleRequest>({ ...emptyRule })
const plans = computed(() => adminStore.plans)
const pricingRules = computed(() => adminStore.pricingRules)
const planStorageMb = computed({
  get: () => planForm.value.storageLimitMb,
  set: (value: number) => {
    planForm.value.storageLimitMb = Math.max(0, Math.round(Number(value) || 0))
  },
})

onMounted(loadCatalogs)

async function loadCatalogs(): Promise<void> {
  try {
    await Promise.all([adminStore.fetchPlans(), adminStore.fetchPricingRules()])
    errorMsg.value = ''
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : t('admin.plansCredits.errors.loadCatalogs')
  } finally {
    loading.value = false
  }
}

async function submitPlan(): Promise<void> {
  await runSave(async () => {
    await adminStore.savePlan(
      {
        ...planForm.value,
        code: planForm.value.code.trim().toUpperCase(),
        name: planForm.value.name.trim(),
      },
      editingPlanCode.value ?? undefined,
    )
    resetPlanForm()
  }, t('admin.plansCredits.errors.savePlan'))
}

async function submitRule(): Promise<void> {
  await runSave(async () => {
    await adminStore.savePricingRule(
      {
        ...ruleForm.value,
        operationCode: ruleForm.value.operationCode.trim().toUpperCase(),
        displayName: ruleForm.value.displayName.trim(),
      },
      editingRuleCode.value ?? undefined,
    )
    resetRuleForm()
  }, t('admin.plansCredits.errors.saveRule'))
}

async function removePlan(code: string): Promise<void> {
  pendingConfirm.value = {
    title: t('admin.plansCredits.confirm.deactivatePlan.title'),
    message: t('admin.plansCredits.confirm.deactivatePlan.message', { code }),
    confirmLabel: t('admin.plansCredits.actions.deactivate'),
    tone: 'danger',
    action: () => adminStore.deletePlan(code),
    fallback: t('admin.plansCredits.errors.deletePlan'),
  }
}

async function removeRule(operationCode: string): Promise<void> {
  pendingConfirm.value = {
    title: t('admin.plansCredits.confirm.disableRule.title'),
    message: t('admin.plansCredits.confirm.disableRule.message', { operationCode }),
    confirmLabel: t('admin.plansCredits.actions.disable'),
    tone: 'danger',
    action: () => adminStore.deletePricingRule(operationCode),
    fallback: t('admin.plansCredits.errors.deleteRule'),
  }
}

async function confirmPendingAction(): Promise<void> {
  const pending = pendingConfirm.value
  if (!pending) return
  pendingConfirm.value = null
  await runSave(pending.action, pending.fallback)
}

async function runSave(action: () => Promise<void>, fallback: string): Promise<void> {
  saving.value = true
  try {
    await action()
    errorMsg.value = ''
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : fallback
  } finally {
    saving.value = false
  }
}

function editPlan(plan: AdminPlan): void {
  editingPlanCode.value = plan.code
  planForm.value = {
    ...plan,
    active: true,
    sortOrder: plans.value.findIndex((item) => item.code === plan.code),
  }
}

function editRule(rule: AdminPricingRule): void {
  editingRuleCode.value = rule.operationCode
  ruleForm.value = { ...rule }
}

function resetPlanForm(): void {
  editingPlanCode.value = null
  planForm.value = { ...emptyPlan }
}

function resetRuleForm(): void {
  editingRuleCode.value = null
  ruleForm.value = { ...emptyRule }
}

function formatStorageMb(plan: AdminPlan): string {
  const storageMb = plan.storageLimitMb ?? Math.round((plan.storageLimitBytes ?? 0) / (1024 * 1024))
  return `${storageMb.toLocaleString(locale.value)} MB`
}
</script>

<template>
  <div class="admin-page">
    <div class="page-title">
      <div>
        <h2>{{ t('admin.plansCredits.title') }}</h2>
        <p>{{ t('admin.plansCredits.description') }}</p>
      </div>
      <span v-if="errorMsg" class="api-state unavailable">{{
        t('admin.plansCredits.apiError')
      }}</span>
    </div>

    <div v-if="loading" class="notice">{{ t('admin.plansCredits.loading') }}</div>
    <div v-if="errorMsg" class="notice error">{{ errorMsg }}</div>

    <section class="catalog-grid">
      <article class="panel">
        <div class="panel-header">
          <div>
            <h3>{{ t('admin.plansCredits.plans.title') }}</h3>
            <p>{{ t('admin.plansCredits.plans.help') }}</p>
          </div>
          <button class="ghost-button" type="button" :disabled="saving" @click="resetPlanForm">
            {{ t('admin.plansCredits.actions.resetForm') }}
          </button>
        </div>
        <form class="plan-editor" @submit.prevent="submitPlan">
          <label class="field" for="plan-code">
            <span>{{ t('admin.plansCredits.fields.code') }}</span>
            <input
              id="plan-code"
              v-model="planForm.code"
              name="planCode"
              :disabled="Boolean(editingPlanCode)"
              required
              pattern="[A-Z0-9_]{2,32}"
              placeholder="FREE"
            />
          </label>
          <label class="field" for="plan-name">
            <span>{{ t('admin.plansCredits.fields.name') }}</span>
            <input
              id="plan-name"
              v-model="planForm.name"
              name="planName"
              required
              maxlength="120"
              :placeholder="t('admin.plansCredits.placeholders.planName')"
            />
          </label>
          <label class="field" for="plan-storage-limit">
            <span>{{ t('admin.plansCredits.fields.storageMb') }}</span>
            <input
              id="plan-storage-limit"
              v-model.number="planStorageMb"
              name="planStorageLimitMb"
              required
              min="0"
              type="number"
              placeholder="100"
            />
          </label>
          <label class="field" for="plan-api-key-limit">
            <span>{{ t('admin.plansCredits.fields.apiKeys') }}</span>
            <input
              id="plan-api-key-limit"
              v-model.number="planForm.apiKeyLimit"
              name="planApiKeyLimit"
              required
              min="0"
              max="10000"
              type="number"
              placeholder="3"
            />
          </label>
          <label class="field" for="plan-monthly-credit-limit">
            <span>{{ t('admin.plansCredits.fields.monthlyCredits') }}</span>
            <input
              id="plan-monthly-credit-limit"
              v-model.number="planForm.monthlyCreditLimit"
              name="planMonthlyCreditLimit"
              required
              min="0"
              max="10000000"
              type="number"
              placeholder="100"
            />
          </label>
          <label
            class="compact-switch sales-switch"
            :class="{ active: planForm.contactSalesRequired }"
            for="plan-contact-sales-required"
          >
            <input
              id="plan-contact-sales-required"
              v-model="planForm.contactSalesRequired"
              name="planContactSalesRequired"
              type="checkbox"
            />
            <span class="toggle-track" aria-hidden="true"><span></span></span>
            <strong>{{ t('admin.plansCredits.status.contactSales') }}</strong>
          </label>
          <button class="submit-button" type="submit" :disabled="saving">
            {{
              editingPlanCode
                ? t('admin.plansCredits.actions.updatePlan')
                : t('admin.plansCredits.actions.createPlan')
            }}
          </button>
        </form>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t('admin.plansCredits.columns.code') }}</th>
                <th>{{ t('admin.plansCredits.columns.name') }}</th>
                <th>{{ t('admin.plansCredits.columns.storage') }}</th>
                <th>{{ t('admin.plansCredits.columns.apiKeys') }}</th>
                <th>{{ t('admin.plansCredits.columns.creditsPerMonth') }}</th>
                <th>{{ t('admin.plansCredits.columns.sales') }}</th>
                <th>{{ t('admin.plansCredits.columns.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="plans.length === 0">
                <td colspan="7" class="empty-cell">
                  {{ t('admin.plansCredits.empty.plans') }}
                </td>
              </tr>
              <tr v-for="plan in plans" :key="plan.code">
                <td class="strong" :data-label="t('admin.plansCredits.columns.code')">
                  {{ plan.code }}
                </td>
                <td :data-label="t('admin.plansCredits.columns.name')">{{ plan.name }}</td>
                <td :data-label="t('admin.plansCredits.columns.storage')">
                  {{ formatStorageMb(plan) }}
                </td>
                <td :data-label="t('admin.plansCredits.columns.apiKeys')">
                  {{ plan.apiKeyLimit }}
                </td>
                <td :data-label="t('admin.plansCredits.columns.creditsPerMonth')">
                  {{ plan.monthlyCreditLimit }}
                </td>
                <td :data-label="t('admin.plansCredits.columns.sales')">
                  <span class="status-pill" :class="{ warning: plan.contactSalesRequired }">
                    {{
                      plan.contactSalesRequired
                        ? t('admin.plansCredits.status.contactSales')
                        : t('admin.plansCredits.status.selfService')
                    }}
                  </span>
                </td>
                <td class="actions" :data-label="t('admin.plansCredits.columns.actions')">
                  <button type="button" @click="editPlan(plan)">
                    {{ t('admin.plansCredits.actions.edit') }}
                  </button>
                  <button type="button" @click="removePlan(plan.code)">
                    {{ t('admin.plansCredits.actions.delete') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>

      <article class="panel">
        <div class="panel-header">
          <div>
            <h3>{{ t('admin.plansCredits.rules.title') }}</h3>
            <p>{{ t('admin.plansCredits.rules.help') }}</p>
          </div>
          <button v-if="editingRuleCode" class="ghost-button" type="button" @click="resetRuleForm">
            {{ t('admin.plansCredits.actions.newRule') }}
          </button>
        </div>
        <form class="rule-editor" @submit.prevent="submitRule">
          <label class="field operation-field" for="pricing-operation-code">
            <span>{{ t('admin.plansCredits.fields.operationCode') }}</span>
            <input
              id="pricing-operation-code"
              v-model="ruleForm.operationCode"
              name="pricingOperationCode"
              :disabled="Boolean(editingRuleCode)"
              required
              pattern="[A-Z0-9_]{2,64}"
              placeholder="MCP_TOOL_CALL"
            />
          </label>
          <label class="field display-field" for="pricing-display-name">
            <span>{{ t('admin.plansCredits.fields.displayName') }}</span>
            <input
              id="pricing-display-name"
              v-model="ruleForm.displayName"
              name="pricingDisplayName"
              required
              maxlength="120"
              :placeholder="t('admin.plansCredits.placeholders.displayName')"
            />
          </label>
          <label class="field per-mb-field" for="pricing-per-mb-credits">
            <span>{{ t('admin.plansCredits.fields.perMb') }}</span>
            <input
              id="pricing-per-mb-credits"
              v-model.number="ruleForm.perMbCredits"
              name="pricingPerMbCredits"
              required
              min="0"
              step="0.0001"
              type="number"
              placeholder="1"
            />
          </label>
          <button
            class="ghost-button reset-rule-button"
            type="button"
            :disabled="saving"
            @click="resetRuleForm"
          >
            {{ t('admin.plansCredits.actions.reset') }}
          </button>
          <button class="submit-button rule-submit-button" type="submit" :disabled="saving">
            {{
              editingRuleCode
                ? t('admin.plansCredits.actions.updateRule')
                : t('admin.plansCredits.actions.createRule')
            }}
          </button>
          <label class="field per-1k-field" for="pricing-per-1k-nodes-credits">
            <span>{{ t('admin.plansCredits.fields.per1kNodes') }}</span>
            <input
              id="pricing-per-1k-nodes-credits"
              v-model.number="ruleForm.per1kNodesCredits"
              name="pricingPer1kNodesCredits"
              required
              min="0"
              step="0.0001"
              type="number"
              placeholder="0"
            />
          </label>
          <label class="field minimum-field" for="pricing-minimum-credits">
            <span>{{ t('admin.plansCredits.fields.minimum') }}</span>
            <input
              id="pricing-minimum-credits"
              v-model.number="ruleForm.minimumCredits"
              name="pricingMinimumCredits"
              required
              min="0"
              max="10000000"
              type="number"
              placeholder="1"
            />
          </label>
          <label class="field base-field" for="pricing-base-credits">
            <span>{{ t('admin.plansCredits.fields.base') }}</span>
            <input
              id="pricing-base-credits"
              v-model.number="ruleForm.baseCredits"
              name="pricingBaseCredits"
              required
              min="0"
              step="0.0001"
              type="number"
              placeholder="1"
            />
          </label>
          <label class="field per-file-field" for="pricing-per-file-credits">
            <span>{{ t('admin.plansCredits.fields.perFile') }}</span>
            <input
              id="pricing-per-file-credits"
              v-model.number="ruleForm.perFileCredits"
              name="pricingPerFileCredits"
              required
              min="0"
              step="0.0001"
              type="number"
              placeholder="0.1"
            />
          </label>
          <label
            class="compact-switch pricing-switch"
            :class="{ active: ruleForm.active }"
            for="pricing-active"
          >
            <input
              id="pricing-active"
              v-model="ruleForm.active"
              name="pricingActive"
              type="checkbox"
            />
            <span class="toggle-track" aria-hidden="true"><span></span></span>
            <strong>{{
              ruleForm.active
                ? t('admin.plansCredits.status.active')
                : t('admin.plansCredits.status.paused')
            }}</strong>
          </label>
        </form>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t('admin.plansCredits.columns.operation') }}</th>
                <th>{{ t('admin.plansCredits.columns.base') }}</th>
                <th>{{ t('admin.plansCredits.columns.perFile') }}</th>
                <th>{{ t('admin.plansCredits.columns.perMb') }}</th>
                <th>{{ t('admin.plansCredits.columns.per1kNodes') }}</th>
                <th>{{ t('admin.plansCredits.columns.minimum') }}</th>
                <th>{{ t('admin.plansCredits.columns.status') }}</th>
                <th>{{ t('admin.plansCredits.columns.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="pricingRules.length === 0">
                <td colspan="8" class="empty-cell">
                  {{ t('admin.plansCredits.empty.rules') }}
                </td>
              </tr>
              <tr v-for="rule in pricingRules" :key="rule.operationCode">
                <td :data-label="t('admin.plansCredits.columns.operation')">
                  <span class="strong">{{ rule.operationCode }}</span
                  ><small>{{ rule.displayName }}</small>
                </td>
                <td :data-label="t('admin.plansCredits.columns.base')">
                  {{ rule.baseCredits }}
                </td>
                <td :data-label="t('admin.plansCredits.columns.perFile')">
                  {{ rule.perFileCredits }}
                </td>
                <td :data-label="t('admin.plansCredits.columns.perMb')">
                  {{ rule.perMbCredits }}
                </td>
                <td :data-label="t('admin.plansCredits.columns.per1kNodes')">
                  {{ rule.per1kNodesCredits }}
                </td>
                <td :data-label="t('admin.plansCredits.columns.minimum')">
                  {{ rule.minimumCredits }}
                </td>
                <td :data-label="t('admin.plansCredits.columns.status')">
                  <span class="status-pill" :class="{ off: !rule.active }">{{
                    rule.active
                      ? t('admin.plansCredits.status.active')
                      : t('admin.plansCredits.status.disabled')
                  }}</span>
                </td>
                <td class="actions" :data-label="t('admin.plansCredits.columns.actions')">
                  <button type="button" @click="editRule(rule)">
                    {{ t('admin.plansCredits.actions.edit') }}
                  </button>
                  <button type="button" @click="removeRule(rule.operationCode)">
                    {{ t('admin.plansCredits.actions.disable') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>
    </section>

    <AdminConfirmDialog
      :open="Boolean(pendingConfirm)"
      :title="pendingConfirm?.title ?? ''"
      :message="pendingConfirm?.message ?? ''"
      :confirm-label="pendingConfirm?.confirmLabel ?? t('admin.plansCredits.actions.confirm')"
      :tone="pendingConfirm?.tone ?? 'default'"
      :busy="saving"
      @cancel="pendingConfirm = null"
      @confirm="confirmPendingAction"
    />
  </div>
</template>

<style scoped>
.admin-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}
.page-title,
.panel-header,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--vg-space-3);
}
h2,
h3 {
  margin: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  letter-spacing: 0;
}
.page-title p,
.panel-header p {
  margin: var(--vg-space-1) 0 0;
  color: var(--vg-text-muted);
}
.panel-header p {
  font-size: var(--vg-text-sm);
}
.api-state {
  color: var(--vg-green-bright);
  font-size: var(--vg-text-sm);
  font-weight: 700;
}
.api-state.unavailable,
.notice.error {
  color: var(--vg-danger);
}
.notice,
.panel {
  background: var(--vg-surface);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
}
.notice {
  padding: var(--vg-space-4);
  color: var(--vg-text-muted);
}
.catalog-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--vg-space-4);
}
.catalog-grid > .panel:last-child {
  min-height: calc(100vh - 8rem);
}
.panel {
  padding: var(--vg-space-4);
  min-width: 0;
}
.plan-editor,
.rule-editor {
  display: grid;
  gap: var(--vg-space-3);
  margin-top: var(--vg-space-4);
  align-items: end;
}
.plan-editor {
  grid-template-columns:
    minmax(7rem, 0.72fr) minmax(12rem, 1.25fr) repeat(3, minmax(8rem, 0.82fr))
    11.25rem 8rem;
}
.rule-editor {
  grid-template-columns: repeat(16, minmax(0, 1fr));
  grid-template-areas:
    'operation operation operation operation operation operation display display display display permb permb permb reset reset reset'
    'nodes nodes nodes minimum minimum minimum base base perfile perfile active active active submit submit submit';
}
.field {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.field--wide {
  grid-column: span 2;
}
input {
  min-width: 0;
  min-height: 2.75rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg);
  color: var(--vg-text);
  padding: var(--vg-space-2) var(--vg-space-3);
  font: inherit;
  letter-spacing: 0;
  text-transform: none;
}
input::placeholder {
  color: var(--vg-text-dim);
}
input:focus {
  outline: none;
  border-color: var(--vg-blue);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.16);
}
.compact-switch {
  position: relative;
  min-height: 2.5rem;
  display: inline-grid;
  grid-template-columns: 2rem minmax(0, auto);
  align-items: center;
  gap: var(--vg-space-2);
  padding: 0 var(--vg-space-2);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: rgba(15, 23, 42, 0.38);
  color: var(--vg-text);
  cursor: pointer;
  white-space: nowrap;
}
.compact-switch input {
  position: absolute;
  inset: 0;
  width: 100%;
  min-height: 0;
  height: 100%;
  margin: 0;
  opacity: 0;
  pointer-events: none;
}
.compact-switch strong {
  display: block;
  font-size: var(--vg-text-xs);
  line-height: 1;
}
.compact-switch.active {
  border-color: rgba(96, 165, 250, 0.46);
  background: rgba(59, 130, 246, 0.1);
}
.sales-switch {
  min-height: 2.75rem;
  justify-self: stretch;
  padding-inline: var(--vg-space-3);
}
.pricing-switch {
  grid-area: active;
  min-height: 2.75rem;
  align-self: end;
}
.toggle-track {
  width: 1.78rem;
  height: 1rem;
  display: inline-flex;
  align-items: center;
  padding: 0.12rem;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.28);
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out);
}
.toggle-track span {
  width: 0.76rem;
  height: 0.76rem;
  border-radius: 999px;
  background: white;
  transform: translateX(0);
  transition: transform var(--vg-dur-fast) var(--vg-ease-out);
}
.compact-switch.active .toggle-track {
  background: var(--vg-blue);
}
.compact-switch.active .toggle-track span {
  transform: translateX(0.78rem);
}
button {
  min-height: 2.5rem;
  border: 1px solid var(--vg-blue);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-blue);
  color: white;
  padding: var(--vg-space-2) var(--vg-space-3);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}
button:disabled {
  border-color: var(--vg-border);
  background: var(--vg-surface-2);
  color: var(--vg-text-dim);
  cursor: not-allowed;
}
.ghost-button {
  min-width: 8rem;
  background: var(--vg-surface-2);
  color: var(--vg-text);
  border-color: var(--vg-border);
}
.submit-button {
  width: 100%;
}
.operation-field {
  grid-area: operation;
}
.display-field {
  grid-area: display;
}
.per-mb-field {
  grid-area: permb;
}
.per-1k-field {
  grid-area: nodes;
}
.minimum-field {
  grid-area: minimum;
}
.base-field {
  grid-area: base;
}
.per-file-field {
  grid-area: perfile;
}
.reset-rule-button {
  grid-area: reset;
  width: 100%;
  min-height: 2.75rem;
  align-self: end;
}
.rule-submit-button {
  grid-area: submit;
  min-height: 2.75rem;
  align-self: end;
}
.actions {
  justify-content: center;
}
.actions button {
  background: var(--vg-surface-2);
  color: var(--vg-text);
  border-color: var(--vg-border);
}
.table-wrap {
  margin-top: var(--vg-space-4);
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  min-width: 980px;
}
th,
td {
  padding: var(--vg-space-3);
  border-bottom: 1px solid var(--vg-border);
  text-align: center;
  color: var(--vg-text);
  vertical-align: middle;
}
th:first-child,
td:first-child {
  text-align: left;
}
th {
  color: var(--vg-text-muted);
  background: var(--vg-surface-2);
  font-size: var(--vg-text-sm);
}
td small {
  display: block;
  color: var(--vg-text-muted);
  margin-top: 2px;
}
.strong {
  font-weight: 700;
}
.empty-cell {
  color: var(--vg-text-muted);
  text-align: center;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 5.75rem;
  min-height: 1.75rem;
  padding: 0 var(--vg-space-2);
  border: 1px solid rgba(34, 197, 94, 0.26);
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.08);
  color: var(--vg-green-bright);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  white-space: nowrap;
}
.status-pill.warning {
  border-color: rgba(245, 158, 11, 0.3);
  background: rgba(245, 158, 11, 0.1);
  color: var(--vg-warning, #f59e0b);
}
.status-pill.off {
  border-color: rgba(148, 163, 184, 0.22);
  background: rgba(148, 163, 184, 0.08);
  color: var(--vg-text-muted);
}

@media (max-width: 1280px) {
  .plan-editor {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .rule-editor {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    grid-template-areas:
      'operation operation operation'
      'display display permb'
      'nodes minimum reset'
      'base perfile submit'
      'active active active';
  }

  .compact-switch,
  .submit-button {
    grid-column: span 1;
  }
}

@media (max-width: 720px) {
  .page-title,
  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .field--wide {
    grid-column: auto;
  }

  .plan-editor,
  .rule-editor {
    grid-template-columns: 1fr;
  }

  .compact-switch,
  .submit-button {
    grid-column: auto;
  }

  .operation-field,
  .display-field,
  .per-mb-field,
  .per-1k-field,
  .minimum-field,
  .base-field,
  .per-file-field,
  .pricing-switch,
  .reset-rule-button,
  .rule-submit-button {
    grid-area: auto;
  }

  .table-wrap {
    overflow-x: visible;
  }

  table,
  tbody,
  tr,
  td {
    display: block;
    width: 100%;
  }

  table {
    min-width: 0;
  }

  thead {
    display: none;
  }

  tbody tr {
    border: 1px solid var(--vg-border);
    border-radius: var(--vg-radius-sm);
    margin-bottom: var(--vg-space-3);
    overflow: hidden;
  }

  tbody tr:last-child {
    margin-bottom: 0;
  }

  td {
    display: grid;
    grid-template-columns: minmax(7rem, 42%) minmax(0, 1fr);
    align-items: start;
    gap: var(--vg-space-3);
    padding: var(--vg-space-3);
    word-break: break-word;
  }

  td::before {
    content: attr(data-label);
    color: var(--vg-text-muted);
    font-size: var(--vg-text-xs);
    font-weight: 800;
    letter-spacing: 0.04em;
    text-transform: uppercase;
  }

  .actions {
    justify-content: flex-end;
    flex-wrap: wrap;
  }

  .actions button {
    min-width: 88px;
  }

  .empty-cell {
    display: block;
  }

  .empty-cell::before {
    content: none;
  }
}
</style>
