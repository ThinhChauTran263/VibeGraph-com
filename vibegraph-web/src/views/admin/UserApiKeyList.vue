<script setup lang="ts">
/**
 * F-M6 split (UserDetailDrawer, step 2): the API-key metadata table, extracted with its
 * scoped styles so the drawer file shrinks without visual drift. Actions bubble to the
 * parent, which owns the confirm dialog and the store calls.
 */
import { useI18n } from 'vue-i18n'
import type { ApiKey } from '@/types/api'
import StatusChip from '@/components/ui/StatusChip.vue'
import * as fmt from './user-detail-format'

defineProps<{ keys: ApiKey[] }>()

const emit = defineEmits<{
  (e: 'disable', keyId: string): void
  (e: 'lock', keyId: string): void
  (e: 'unlock', keyId: string): void
}>()

const { locale, t } = useI18n({ useScope: 'global' })

const formatDate = (value: string | null | undefined): string =>
  fmt.formatDate(locale.value, t, value)
const apiKeyStatus = fmt.apiKeyStatus
const apiKeyStatusLabel = (key: ApiKey): string => fmt.apiKeyStatusLabel(t, key)
const projectLabel = (key: ApiKey): string => fmt.projectLabel(t, key)
const lockedMeta = (key: ApiKey): string => fmt.lockedMeta(t, locale.value, key)
</script>

<template>
  <!-- User's API Keys -->
  <div class="section api-keys-section">
    <div class="section-title-row api-keys-title-row">
      <div>
        <h4>{{ t('admin.userDetail.apiKeys.title') }}</h4>
        <p class="section-caption">
          {{ t('admin.userDetail.apiKeys.description') }}
        </p>
      </div>
    </div>

    <!-- Existing keys list -->
    <div v-if="keys.length === 0" class="empty-state">
      {{ t('admin.userDetail.apiKeys.empty') }}
    </div>
    <div v-else class="table-shell">
      <table class="keys-table">
        <thead>
          <tr>
            <th>{{ t('admin.userDetail.apiKeys.table.name') }}</th>
            <th>{{ t('admin.userDetail.apiKeys.table.apiKey') }}</th>
            <th class="key-status-heading">
              {{ t('admin.userDetail.apiKeys.table.status') }}
            </th>
            <th class="key-actions-heading"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="k in keys" :key="k.id" class="key-row">
            <td>
              <strong class="key-name">{{ k.name }}</strong>
              <span class="key-meta">
                {{ t('admin.userDetail.apiKeys.repository', { name: projectLabel(k) }) }}
              </span>
              <span v-if="k.project?.sourceType || k.project?.status" class="key-meta">
                {{ k.project?.sourceType ?? t('admin.userDetail.apiKeys.sourceUnknown') }}
                /
                {{ k.project?.status ?? t('admin.userDetail.apiKeys.statusUnknown') }}
              </span>
            </td>
            <td>
              <span class="mono key-value">{{ k.keyPrefix }}********</span>
              <span class="key-meta">
                {{ t('admin.userDetail.apiKeys.prefix', { prefix: k.keyPrefix }) }}
              </span>
              <span class="key-meta">{{ t('admin.userDetail.apiKeys.secretHelp') }}</span>
              <span class="key-meta">
                {{ t('admin.userDetail.apiKeys.created', { date: formatDate(k.createdAt) }) }}
              </span>
              <span class="key-meta">
                {{
                  t('admin.userDetail.apiKeys.lastUsed', {
                    date: formatDate(k.lastUsedAt),
                  })
                }}
              </span>
              <span v-if="k.expiresAt" class="key-meta">
                {{ t('admin.userDetail.apiKeys.expires', { date: formatDate(k.expiresAt) }) }}
              </span>
            </td>
            <td class="key-status-cell">
              <StatusChip :status="apiKeyStatus(k)" :label="apiKeyStatusLabel(k)" />
              <span v-if="k.disabledReason" class="key-meta key-reason">
                {{ k.disabledReason }}
              </span>
              <span v-if="k.locked" class="key-meta key-reason">
                {{ lockedMeta(k) }}
              </span>
              <span v-if="k.disabledBy" class="key-meta">
                {{ t('admin.userDetail.apiKeys.disabledBy', { actor: k.disabledBy }) }}
              </span>
            </td>
            <td class="key-action-cell">
              <div class="key-actions">
                <button
                  v-if="!k.disabled && !k.deletedAt"
                  type="button"
                  class="key-action key-action--disable"
                  @click="emit('disable', k.id)"
                >
                  <svg class="key-action-icon" viewBox="0 0 20 20" aria-hidden="true">
                    <circle cx="10" cy="10" r="6.75" />
                    <path d="m5.25 5.25 9.5 9.5" />
                  </svg>
                  <span>{{ t('admin.userDetail.actions.disable') }}</span>
                </button>
                <button
                  v-if="!k.locked && !k.deletedAt"
                  type="button"
                  class="key-action key-action--lock"
                  @click="emit('lock', k.id)"
                >
                  <svg class="key-action-icon" viewBox="0 0 20 20" aria-hidden="true">
                    <rect x="4.75" y="8.5" width="10.5" height="7.75" rx="2" />
                    <path d="M7.25 8.5V6.75a2.75 2.75 0 0 1 5.5 0V8.5" />
                  </svg>
                  <span>{{ t('admin.userDetail.actions.lock') }}</span>
                </button>
                <button
                  v-if="k.locked && !k.deletedAt"
                  type="button"
                  class="key-action key-action--unlock"
                  @click="emit('unlock', k.id)"
                >
                  <svg class="key-action-icon" viewBox="0 0 20 20" aria-hidden="true">
                    <rect x="4.75" y="8.5" width="10.5" height="7.75" rx="2" />
                    <path d="M7.25 8.5V6.75a2.75 2.75 0 0 1 5.05-1.5" />
                  </svg>
                  <span>{{ t('admin.userDetail.actions.unlock') }}</span>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
/* Moved verbatim from UserDetailDrawer.vue (F-M6 split) so the table keeps its look. */
.section-title-row {
  --detail-action-width: 8rem;
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--detail-action-width);
  align-items: flex-start;
  column-gap: var(--vg-space-3);
  row-gap: var(--vg-space-1);
  padding-inline: var(--vg-space-4);
}

.section-title-row > div {
  display: contents;
}

.section-title-row h4 {
  grid-column: 1;
  grid-row: 1;
  margin-bottom: var(--vg-space-1);
}

.section-caption {
  grid-column: 1 / -1;
  grid-row: 2;
  margin: 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.45;
}

/* API Keys table */
.keys-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--vg-text-sm);
  margin-top: 0.5rem;
}
.keys-table th,
.keys-table td {
  padding: 0.5rem 0.5rem;
  text-align: left;
  border-bottom: 1px solid var(--vg-border);
  color: var(--vg-text);
}
.keys-table th {
  color: var(--vg-text-muted);
  font-weight: 600;
  background: var(--vg-surface-2);
}
.mono {
  font-family: monospace;
  color: var(--vg-text-dim);
  font-size: var(--vg-text-xs);
}
.empty-state {
  text-align: center;
  padding: 1rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

.table-shell {
  overflow-x: auto;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: var(--vg-radius-sm);
}

.keys-table {
  margin-top: 0;
  min-width: 31rem;
}

.keys-table th,
.keys-table td {
  padding: var(--vg-space-3);
  vertical-align: top;
}

.keys-table th {
  position: sticky;
  top: 0;
  background: rgba(20, 30, 52, 0.98);
  color: var(--vg-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.keys-table tr:last-child td {
  border-bottom: 0;
}

.key-name,
.key-meta {
  display: block;
  min-width: 0;
}

.key-name {
  margin-bottom: 0.35rem;
  color: var(--vg-text);
  font-weight: 800;
  overflow-wrap: anywhere;
}

.key-value {
  display: block;
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-sm);
  font-weight: 800;
  overflow-wrap: anywhere;
}

.key-meta {
  margin-top: 0.24rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.key-reason {
  max-width: 18rem;
  color: var(--vg-amber);
}

.key-actions {
  display: grid;
  justify-items: stretch;
  gap: 0.55rem;
}

.key-action {
  min-width: 8.25rem;
  min-height: 2.75rem;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.625rem;
  padding: 0.55rem 0.85rem;
  border: 1px solid transparent;
  border-radius: var(--vg-radius-pill);
  color: var(--vg-text);
  font: inherit;
  font-size: var(--vg-text-sm);
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition:
    background-color var(--vg-dur-fast) var(--vg-ease-out),
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur-fast) var(--vg-ease-out),
    color var(--vg-dur-fast) var(--vg-ease-out);
}

.key-action-icon {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.key-action--disable {
  border-color: color-mix(in srgb, var(--vg-danger) 38%, transparent);
  background: color-mix(in srgb, var(--vg-danger) 14%, transparent);
  color: color-mix(in srgb, var(--vg-danger) 58%, white);
}

.key-action--disable:hover {
  border-color: color-mix(in srgb, var(--vg-danger) 64%, transparent);
  background: color-mix(in srgb, var(--vg-danger) 22%, transparent);
  color: color-mix(in srgb, var(--vg-danger) 34%, white);
  box-shadow: 0 8px 22px -16px color-mix(in srgb, var(--vg-danger) 90%, transparent);
}

.key-action--lock,
.key-action--unlock {
  border-color: color-mix(in srgb, var(--vg-blue-bright) 34%, transparent);
  background: color-mix(in srgb, var(--vg-blue-bright) 12%, transparent);
  color: color-mix(in srgb, var(--vg-blue-bright) 62%, white);
}

.key-action--lock:hover,
.key-action--unlock:hover {
  border-color: color-mix(in srgb, var(--vg-blue-bright) 58%, transparent);
  background: color-mix(in srgb, var(--vg-blue-bright) 20%, transparent);
  color: color-mix(in srgb, var(--vg-blue-bright) 38%, white);
  box-shadow: 0 8px 22px -16px color-mix(in srgb, var(--vg-blue-bright) 90%, transparent);
}

.key-action:focus-visible {
  outline: 2px solid var(--vg-blue-bright);
  outline-offset: 2px;
}

.key-action:active {
  box-shadow: inset 0 1px 4px rgba(2, 6, 23, 0.35);
}

.empty-state {
  border: 1px dashed rgba(148, 163, 184, 0.22);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.24);
}

.keys-table td:last-child,
.keys-table th:last-child {
  width: 9.5rem;
  white-space: nowrap;
}

.keys-table .key-action-cell {
  white-space: normal;
}

.keys-table .key-status-heading,
.keys-table .key-status-cell {
  width: 9.5rem;
}

.keys-table .key-status-heading {
  text-align: center;
}

.keys-table .key-status-cell {
  vertical-align: middle;
}

.key-status-cell :deep(.status-chip) {
  display: flex;
  width: fit-content;
  margin-inline: auto;
}

.key-actions-heading,
.key-action-cell {
  padding-inline: var(--vg-space-4);
}

.key-action-cell {
  vertical-align: middle;
}

@media (prefers-reduced-motion: reduce) {
  .key-action {
    transition: none;
  }
}
</style>
