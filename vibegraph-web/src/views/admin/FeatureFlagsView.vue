<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAdminStore } from '@/stores/admin'
import {
  featureAvailabilityContract,
  refreshFeatureAvailability,
} from '@/lib/featureAvailability'
import type { AdminFeatureFlag, AdminFeatureFlagRequest } from '@/types/api'

const adminStore = useAdminStore()
const loading = ref(true)
const errorMsg = ref('')
const capabilityError = ref('')
const runtimeContractConnected = computed(() => featureAvailabilityContract.value === true)

const form = ref<AdminFeatureFlagRequest>({
  key: '',
  scope: 'GLOBAL',
  displayName: '',
  enabled: true,
  description: '',
})

type TemplateFlag = AdminFeatureFlagRequest & {
  group: string
  note: string
}

const templates: TemplateFlag[] = [
  {
    group: 'Import methods',
    key: 'import.local',
    scope: 'GLOBAL',
    displayName: 'Local import',
    enabled: true,
    description: 'Allow importing projects from server-local paths.',
    note: 'Use when /projects import has issues.',
  },
  {
    group: 'Import methods',
    key: 'import.archive',
    scope: 'GLOBAL',
    displayName: 'Archive import',
    enabled: true,
    description: 'Allow ZIP/TAR archive uploads.',
    note: 'Disable if archive parsing or storage is degraded.',
  },
  {
    group: 'Import methods',
    key: 'import.github',
    scope: 'GLOBAL',
    displayName: 'GitHub import',
    enabled: true,
    description: 'Allow imports from GitHub repositories.',
    note: 'Disable without impacting local/archive import.',
  },
  {
    group: 'CLI push',
    key: 'cli.push',
    scope: 'GLOBAL',
    displayName: 'CLI push',
    enabled: true,
    description: 'Allow vibegraph-cli patch pushes.',
    note: 'Useful when patch writes or credit preflight need maintenance.',
  },
  {
    group: 'Project analysis',
    key: 'project.analyze',
    scope: 'GLOBAL',
    displayName: 'Project analyze',
    enabled: true,
    description: 'Allow users to analyze imported projects.',
    note: 'Disable during analyzer incidents without blocking repository browsing.',
  },
  {
    group: 'Gen use case',
    key: 'usecase.generate',
    scope: 'GLOBAL',
    displayName: 'Use case generation',
    enabled: true,
    description: 'Allow generated use-case views.',
    note: 'Disable generation without disabling graph exploration.',
  },
  {
    group: 'API key creation',
    key: 'api_keys.create.global',
    scope: 'GLOBAL',
    displayName: 'New API keys',
    enabled: true,
    description: 'Allow users to create API keys globally.',
    note: 'User-level disable still overrides this.',
  },
  {
    group: 'Registration',
    key: 'registration',
    scope: 'GLOBAL',
    displayName: 'Registration',
    enabled: true,
    description: 'Allow new account registration.',
    note: 'Turn off during abuse spikes or private beta.',
  },
  {
    group: 'MCP global and child tools',
    key: 'mcp.enabled',
    scope: 'GLOBAL',
    displayName: 'All MCP tools',
    enabled: true,
    description: 'Allow MCP tool execution.',
    note: 'Master switch for MCP incidents.',
  },
  ...[
    'get_project_architecture',
    'get_class_context',
    'get_impact_analysis',
    'get_layer_pattern',
    'get_source_file',
    'get_method_source',
    'search_source',
    'find_references',
    'trace_endpoint',
    'get_method_cpg_context',
    'find_related_tests',
    'suggest_test_plan',
    'plan_code_change',
    'explain_failure_path',
    'get_project_conventions',
  ].map((toolName) => ({
    group: 'MCP global and child tools',
    key: `mcp.tool.${toolName}`,
    scope: 'MCP_TOOL' as const,
    displayName: toolName.replace(/_/g, ' ').replace(/(^|\s)\S/g, (letter: string) => letter.toUpperCase()),
    enabled: true,
    description: `Allow ${toolName.replace(/_/g, ' ')} MCP calls.`,
    note: 'Child control; the MCP global switch overrides this state.',
  })),
]

const COLLAPSE_KEY = 'vg_admin_system_collapsed_groups'
const collapsedGroups = ref<Record<string, boolean>>(readCollapsedGroups())

function readCollapsedGroups(): Record<string, boolean> {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(COLLAPSE_KEY) ?? '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.fromEntries(
      Object.entries(parsed).filter((entry): entry is [string, boolean] => typeof entry[1] === 'boolean'),
    )
  } catch {
    localStorage.removeItem(COLLAPSE_KEY)
    return {}
  }
}
function toggleGroup(group: string): void {
  collapsedGroups.value[group] = !collapsedGroups.value[group]
  localStorage.setItem(COLLAPSE_KEY, JSON.stringify(collapsedGroups.value))
}

const groups = computed(() => [...new Set(templates.map((item) => item.group))])
const groupColumns = computed(() => [
  groups.value.filter((_, index) => index % 2 === 0),
  groups.value.filter((_, index) => index % 2 === 1),
])
const extraFlags = computed(() =>
  adminStore.featureFlags.filter(
    (flag) => flag.key !== 'auth.registration' && !templates.some((template) => template.key === flag.key),
  ),
)

onMounted(loadFlags)

async function loadFlags(): Promise<void> {
  const [flagsResult, capabilityResult] = await Promise.allSettled([
    adminStore.fetchFeatureFlags(),
    refreshFeatureAvailability(),
  ])
  errorMsg.value =
    flagsResult.status === 'rejected'
      ? flagsResult.reason instanceof Error
        ? flagsResult.reason.message
        : 'Failed to load feature flags.'
      : ''
  capabilityError.value =
    capabilityResult.status === 'rejected'
      ? 'Runtime capability state could not be verified. Controls remain configuration-only.'
      : ''
  loading.value = false
}

async function refreshRuntimeCapabilityState(): Promise<void> {
  try {
    await refreshFeatureAvailability()
    capabilityError.value = ''
  } catch {
    capabilityError.value =
      'Runtime capability state could not be verified. Controls remain configuration-only.'
  }
}

async function submitFlag(): Promise<void> {
  try {
    await adminStore.upsertFeatureFlag({
      ...form.value,
      key: form.value.key.trim(),
      displayName: form.value.displayName.trim(),
    })
    await refreshRuntimeCapabilityState()
    form.value = { key: '', scope: 'GLOBAL', displayName: '', enabled: true, description: '' }
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to save feature flag.'
  }
}

async function toggleFlag(flagKey: string, checked: boolean): Promise<void> {
  const flag = adminStore.featureFlags.find((item) => item.key === flagKey)
  if (!flag) return
  try {
    await adminStore.setFeatureFlagEnabled(flag, checked)
    await refreshRuntimeCapabilityState()
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to update feature flag.'
  }
}

async function toggleTemplate(template: TemplateFlag, checked: boolean): Promise<void> {
  const existing = adminStore.featureFlags.find((item) => item.key === template.key)
  try {
    await adminStore.upsertFeatureFlag({
      key: template.key,
      scope: template.scope,
      displayName: existing?.displayName ?? template.displayName,
      enabled: checked,
      description: existing?.description ?? template.description,
    })
    await refreshRuntimeCapabilityState()
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to update system control.'
  }
}

function groupId(group: string): string {
  return `system-group-${group.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`
}

function currentFlag(template: TemplateFlag): AdminFeatureFlag | null {
  return adminStore.featureFlags.find((flag) => flag.key === template.key) ?? null
}

function mcpGlobalEnabled(): boolean {
  const global = templates.find((item) => item.key === 'mcp.enabled')
  return global ? currentFlag(global)?.enabled ?? global.enabled : true
}

function currentEnabled(template: TemplateFlag): boolean {
  const ownEnabled = currentFlag(template)?.enabled ?? template.enabled
  return template.scope === 'MCP_TOOL' ? ownEnabled && mcpGlobalEnabled() : ownEnabled
}
</script>

<template>
  <div class="admin-page">
    <div class="page-title">
      <div>
        <h2>System</h2>
        <p>
          Operational switches for imports, CLI, MCP tools, registration, and global API key
          creation.
        </p>
      </div>
      <span
        class="api-state"
        :class="{ unavailable: errorMsg, connected: runtimeContractConnected }"
      >{{
        errorMsg
          ? 'Flag API error'
          : runtimeContractConnected
            ? 'Runtime connected'
            : 'Configuration only'
      }}</span>
    </div>

    <div v-if="errorMsg" class="notice error" role="alert">{{ errorMsg }}</div>
    <div v-if="loading" class="notice" role="status">Loading feature flags...</div>
    <div
      v-else-if="!runtimeContractConnected"
      class="notice warning"
      role="status"
    >
      <strong>Configuration-only controls</strong>
      <p>
        Flag values are stored by the admin API but are not yet propagated to user-facing runtime
        capability state. Do not treat these switches as active protection.
      </p>
      <small v-if="capabilityError">{{ capabilityError }}</small>
    </div>
    <div v-else class="notice success" role="status">
      <strong>Runtime capability propagation connected</strong>
      <p>User-facing controls consume the real session capability contract.</p>
    </div>

    <section class="panel">
      <div class="panel-heading">
        <h3>Create or update system control</h3>
        <p>Use this for new feature gates that are not listed in the default groups below.</p>
      </div>
      <form class="flag-form" @submit.prevent="submitFlag">
        <label class="field">
          <span>Key</span>
          <input
            id="system-flag-key"
            v-model="form.key"
            name="systemFlagKey"
            required
            pattern="[a-z0-9_.:-]{2,120}"
            placeholder="cli.push"
          />
        </label>
        <label class="field">
          <span>Scope</span>
          <select id="system-flag-scope" v-model="form.scope" name="systemFlagScope">
            <option value="GLOBAL">Global</option>
            <option value="MCP_TOOL">MCP tool</option>
          </select>
        </label>
        <label class="field">
          <span>Display name</span>
          <input
            id="system-flag-display-name"
            v-model="form.displayName"
            name="systemFlagDisplayName"
            required
            maxlength="160"
            placeholder="CLI push"
          />
        </label>
        <label class="field">
          <span>Description</span>
          <input
            id="system-flag-description"
            v-model="form.description"
            name="systemFlagDescription"
            maxlength="500"
            placeholder="Optional operator note"
          />
        </label>
        <label class="mini-switch" :class="{ active: form.enabled }" for="system-flag-enabled">
          <input
            id="system-flag-enabled"
            v-model="form.enabled"
            name="systemFlagEnabled"
            type="checkbox"
          />
          <span class="toggle-track" aria-hidden="true"><span></span></span>
          <strong>{{ form.enabled ? 'Enabled' : 'Disabled' }}</strong>
        </label>
        <button type="submit">Save flag</button>
      </form>
    </section>

    <section class="system-grid">
      <div v-for="(column, columnIndex) in groupColumns" :key="columnIndex" class="system-column">
      <article v-for="group in column" :key="group" class="panel control-panel">
        <button
          class="group-toggle"
          type="button"
          :aria-expanded="!collapsedGroups[group]"
          :aria-controls="groupId(group)"
          @click="toggleGroup(group)"
        >
          <h3>{{ group }}</h3>
          <span aria-hidden="true">{{ collapsedGroups[group] ? '›' : '⌄' }}</span>
        </button>
        <div
          v-if="!collapsedGroups[group]"
          :id="groupId(group)"
          class="toggle-list"
        >
          <label
            v-for="template in templates.filter((item) => item.group === group)"
            :key="template.key"
            class="toggle-row"
            :class="{ disabled: !currentEnabled(template) }"
          >
            <span class="control-copy">
              <strong>{{ currentFlag(template)?.displayName ?? template.displayName }}</strong>
              <small>{{ template.key }}</small>
              <em>{{ currentFlag(template)?.description ?? template.note }}</em>
            </span>
            <span class="switch-wrap">
              <input
                type="checkbox"
                :checked="currentFlag(template)?.enabled ?? template.enabled"
                :disabled="template.scope === 'MCP_TOOL' && !mcpGlobalEnabled()"
                :aria-label="`Toggle ${template.displayName}`"
                @change="toggleTemplate(template, ($event.target as HTMLInputElement).checked)"
              />
              <span class="switch" aria-hidden="true"><span></span></span>
            </span>
          </label>
        </div>
      </article>
      </div>
    </section>

    <section v-if="extraFlags.length" class="panel">
      <h3>Other configured controls</h3>
      <div class="toggle-list">
        <label
          v-for="flag in extraFlags"
          :key="flag.key"
          class="toggle-row"
          :class="{ disabled: !flag.enabled }"
        >
          <span class="control-copy">
            <strong>{{ flag.displayName }}</strong>
            <small>{{ flag.key }} / {{ flag.scope }}</small>
            <em>{{ flag.description || 'No description provided.' }}</em>
          </span>
          <span class="switch-wrap">
            <input
              type="checkbox"
              :checked="flag.enabled"
              :aria-label="`Toggle ${flag.displayName}`"
              @change="toggleFlag(flag.key, ($event.target as HTMLInputElement).checked)"
            />
            <span class="switch" aria-hidden="true"><span></span></span>
          </span>
        </label>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}

.page-title,
.system-grid,
.flag-form {
  display: grid;
  gap: var(--vg-space-4);
}

.page-title {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
}

h2,
h3 {
  margin: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  letter-spacing: 0;
}

p,
.empty-state,
small {
  color: var(--vg-text-muted);
}

.panel-heading p {
  margin: var(--vg-space-1) 0 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
}

.api-state,
button {
  font-size: var(--vg-text-sm);
  font-weight: 700;
}

.api-state {
  color: #d97706;
}

.api-state.connected {
  color: var(--vg-green-bright);
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
  padding: var(--vg-space-4);
}

.notice p {
  margin: var(--vg-space-1) 0 0;
  color: var(--vg-text-muted);
}

.notice.warning {
  border-color: rgba(245, 158, 11, 0.38);
  background: rgba(245, 158, 11, 0.08);
}

.notice.warning strong {
  color: var(--vg-text);
}

.notice.warning small {
  display: block;
  margin-top: var(--vg-space-2);
  color: #d97706;
}

.notice.success {
  border-color: rgba(34, 197, 94, 0.3);
}

.notice.success strong {
  color: var(--vg-green-bright);
}

.system-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-auto-rows: max-content;
  align-items: start;
}

.system-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
}

.control-panel {
  width: 100%;
  align-self: stretch;
}

.flag-form {
  grid-template-columns:
    minmax(9rem, 1fr) 10rem minmax(10rem, 1fr) minmax(12rem, 1.2fr)
    7.25rem 7.5rem;
  align-items: end;
}

.field,
.toggle-list,
.control-copy {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
}

.field {
  min-width: 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.mini-switch {
  position: relative;
  min-height: 2.5rem;
  display: inline-grid;
  grid-template-columns: 1.9rem minmax(0, auto);
  align-items: center;
  justify-self: start;
  gap: var(--vg-space-2);
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--vg-radius-sm);
  background: transparent;
  color: var(--vg-text);
  cursor: pointer;
  white-space: nowrap;
}

.mini-switch input,
.switch-wrap input {
  position: absolute;
  inset: 0;
  width: 100%;
  min-height: 0;
  height: 100%;
  margin: 0;
  opacity: 0;
  pointer-events: none;
}

.mini-switch strong {
  display: block;
  font-size: var(--vg-text-xs);
  line-height: 1;
}

.mini-switch.active {
  border-color: transparent;
  background: transparent;
}

.toggle-track,
.switch {
  width: 1.78rem;
  height: 1rem;
  display: inline-flex;
  align-items: center;
  padding: 0.12rem;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.28);
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out);
}

.toggle-track span,
.switch span {
  width: 0.76rem;
  height: 0.76rem;
  border-radius: 999px;
  background: white;
  transform: translateX(0);
  transition: transform var(--vg-dur-fast) var(--vg-ease-out);
}

.mini-switch.active .toggle-track,
.switch-wrap input:checked + .switch {
  background: var(--vg-blue);
}

.mini-switch.active .toggle-track span,
.switch-wrap input:checked + .switch span {
  transform: translateX(0.78rem);
}

input:not([type='checkbox']),
select {
  min-height: 2.75rem;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg);
  color: var(--vg-text);
  padding: var(--vg-space-3);
  font: inherit;
  letter-spacing: 0;
  text-transform: none;
}

select {
  appearance: none;
  padding-right: 2.6rem;
  background-image:
    linear-gradient(45deg, transparent 50%, var(--vg-text-muted) 50%),
    linear-gradient(135deg, var(--vg-text-muted) 50%, transparent 50%);
  background-position:
    calc(100% - 1.1rem) 50%,
    calc(100% - 0.78rem) 50%;
  background-repeat: no-repeat;
  background-size:
    0.38rem 0.38rem,
    0.38rem 0.38rem;
}

input:focus,
select:focus {
  outline: none;
  border-color: var(--vg-blue);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.16);
}

button {
  align-self: end;
  min-height: 2.375rem;
  text-align: left;
  border: 1px solid var(--vg-blue);
  border-radius: 6px;
  background: var(--vg-blue);
  color: white;
  padding: 0.45rem 0.75rem;
  font-weight: 600;
  cursor: pointer;
}

.toggle-list {
  margin-top: var(--vg-space-4);
}

.group-toggle {
  width: 100%;
  min-height: 2.75rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--vg-space-2) 0;
  border: 0;
  background: transparent;
  color: var(--vg-text);
  cursor: pointer;
}

.group-toggle:focus-visible,
.switch-wrap:focus-within,
.mini-switch:focus-within {
  outline: 2px solid var(--vg-blue-bright);
  outline-offset: 3px;
}

.group-toggle span {
  font-size: var(--vg-text-xl);
}

.toggle-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 3rem;
  align-items: center;
  gap: var(--vg-space-4);
  padding: var(--vg-space-3);
  background: var(--vg-bg);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text);
}

.toggle-row.disabled {
  background: rgba(2, 6, 23, 0.24);
}

.control-copy {
  min-width: 0;
}

.control-copy strong {
  color: var(--vg-text);
}

.control-copy em {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-style: normal;
  line-height: 1.35;
}

.switch-wrap {
  position: relative;
  display: inline-flex;
  justify-content: flex-end;
}

@media (max-width: 1180px) {
  .flag-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .page-title,
  .system-grid,
  .flag-form {
    grid-template-columns: 1fr;
  }

  .system-column {
    display: contents;
  }
}
</style>
