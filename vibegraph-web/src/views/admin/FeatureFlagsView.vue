<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminStore } from '@/stores/admin'
import { featureAvailabilityContract, refreshFeatureAvailability } from '@/lib/featureAvailability'
import type { AdminFeatureFlag, AdminFeatureFlagRequest } from '@/types/api'

const adminStore = useAdminStore()
const { t } = useI18n({ useScope: 'global' })
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
  groupLabelKey: string
  displayNameKey: string
  noteKey: string
}

const templates: TemplateFlag[] = [
  {
    group: 'Import methods',
    groupLabelKey: 'importMethods',
    key: 'import.local',
    scope: 'GLOBAL',
    displayName: 'Local import',
    displayNameKey: 'localImport',
    enabled: true,
    description: 'Allow importing projects from server-local paths.',
    noteKey: 'localImport',
  },
  {
    group: 'Import methods',
    groupLabelKey: 'importMethods',
    key: 'import.archive',
    scope: 'GLOBAL',
    displayName: 'Archive import',
    displayNameKey: 'archiveImport',
    enabled: true,
    description: 'Allow ZIP/TAR archive uploads.',
    noteKey: 'archiveImport',
  },
  {
    group: 'Import methods',
    groupLabelKey: 'importMethods',
    key: 'import.github',
    scope: 'GLOBAL',
    displayName: 'GitHub import',
    displayNameKey: 'githubImport',
    enabled: true,
    description: 'Allow imports from GitHub repositories.',
    noteKey: 'githubImport',
  },
  {
    group: 'CLI push',
    groupLabelKey: 'cliPush',
    key: 'cli.push',
    scope: 'GLOBAL',
    displayName: 'CLI push',
    displayNameKey: 'cliPush',
    enabled: true,
    description: 'Allow vibegraph-cli patch pushes.',
    noteKey: 'cliPush',
  },
  {
    group: 'Project analysis',
    groupLabelKey: 'projectAnalysis',
    key: 'project.analyze',
    scope: 'GLOBAL',
    displayName: 'Project analyze',
    displayNameKey: 'projectAnalyze',
    enabled: true,
    description: 'Allow users to analyze imported projects.',
    noteKey: 'projectAnalyze',
  },
  {
    group: 'Gen use case',
    groupLabelKey: 'useCaseGeneration',
    key: 'usecase.generate',
    scope: 'GLOBAL',
    displayName: 'Use case generation',
    displayNameKey: 'useCaseGeneration',
    enabled: true,
    description: 'Allow generated use-case views.',
    noteKey: 'useCaseGeneration',
  },
  {
    group: 'API key creation',
    groupLabelKey: 'apiKeyCreation',
    key: 'api_keys.create.global',
    scope: 'GLOBAL',
    displayName: 'New API keys',
    displayNameKey: 'newApiKeys',
    enabled: true,
    description: 'Allow users to create API keys globally.',
    noteKey: 'newApiKeys',
  },
  {
    group: 'Registration',
    groupLabelKey: 'registration',
    key: 'registration',
    scope: 'GLOBAL',
    displayName: 'Registration',
    displayNameKey: 'registration',
    enabled: true,
    description: 'Allow new account registration.',
    noteKey: 'registration',
  },
  {
    group: 'MCP global and child tools',
    groupLabelKey: 'mcpTools',
    key: 'mcp.enabled',
    scope: 'GLOBAL',
    displayName: 'All MCP tools',
    displayNameKey: 'allMcpTools',
    enabled: true,
    description: 'Allow MCP tool execution.',
    noteKey: 'allMcpTools',
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
    groupLabelKey: 'mcpTools',
    key: `mcp.tool.${toolName}`,
    scope: 'MCP_TOOL' as const,
    displayName: toolName
      .replace(/_/g, ' ')
      .replace(/(^|\s)\S/g, (letter: string) => letter.toUpperCase()),
    displayNameKey: '',
    enabled: true,
    description: `Allow ${toolName.replace(/_/g, ' ')} MCP calls.`,
    noteKey: 'mcpChildTool',
  })),
]

const COLLAPSE_KEY = 'vg_admin_system_collapsed_groups'
const collapsedGroups = ref<Record<string, boolean>>(readCollapsedGroups())

function readCollapsedGroups(): Record<string, boolean> {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(COLLAPSE_KEY) ?? '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.fromEntries(
      Object.entries(parsed).filter(
        (entry): entry is [string, boolean] => typeof entry[1] === 'boolean',
      ),
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
    (flag) =>
      flag.key !== 'auth.registration' && !templates.some((template) => template.key === flag.key),
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
        : t('admin.system.errors.load')
      : ''
  capabilityError.value =
    capabilityResult.status === 'rejected' ? t('admin.system.errors.runtimeVerification') : ''
  loading.value = false
}

async function refreshRuntimeCapabilityState(): Promise<void> {
  try {
    await refreshFeatureAvailability()
    capabilityError.value = ''
  } catch {
    capabilityError.value = t('admin.system.errors.runtimeVerification')
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
    errorMsg.value = e instanceof Error ? e.message : t('admin.system.errors.save')
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
    errorMsg.value = e instanceof Error ? e.message : t('admin.system.errors.updateFlag')
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
    errorMsg.value = e instanceof Error ? e.message : t('admin.system.errors.updateControl')
  }
}

function groupId(group: string): string {
  return `system-group-${group.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`
}

function groupLabel(group: string): string {
  const groupLabelKey = templates.find((template) => template.group === group)?.groupLabelKey
  return groupLabelKey ? t(`admin.system.groups.${groupLabelKey}`) : group
}

function currentFlag(template: TemplateFlag): AdminFeatureFlag | null {
  return adminStore.featureFlags.find((flag) => flag.key === template.key) ?? null
}

function mcpGlobalEnabled(): boolean {
  const global = templates.find((item) => item.key === 'mcp.enabled')
  return global ? (currentFlag(global)?.enabled ?? global.enabled) : true
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
        <h2>{{ t('admin.system.title') }}</h2>
        <p>{{ t('admin.system.description') }}</p>
      </div>
      <span
        class="api-state"
        :class="{ unavailable: errorMsg, connected: runtimeContractConnected }"
        >{{
          errorMsg
            ? t('admin.system.apiState.error')
            : runtimeContractConnected
              ? t('admin.system.apiState.connected')
              : t('admin.system.apiState.configurationOnly')
        }}</span
      >
    </div>

    <div v-if="errorMsg" class="notice error" role="alert">{{ errorMsg }}</div>
    <div v-if="loading" class="notice" role="status">{{ t('admin.system.loading') }}</div>
    <div v-else-if="!runtimeContractConnected" class="notice warning" role="status">
      <strong>{{ t('admin.system.runtime.configurationOnlyTitle') }}</strong>
      <p>{{ t('admin.system.runtime.configurationOnlyDescription') }}</p>
      <small v-if="capabilityError">{{ capabilityError }}</small>
    </div>
    <div v-else class="notice success" role="status">
      <strong>{{ t('admin.system.runtime.connectedTitle') }}</strong>
      <p>{{ t('admin.system.runtime.connectedDescription') }}</p>
    </div>

    <section class="panel">
      <div class="panel-heading">
        <h3>{{ t('admin.system.form.title') }}</h3>
        <p>{{ t('admin.system.form.description') }}</p>
      </div>
      <form class="flag-form" @submit.prevent="submitFlag">
        <label class="field">
          <span>{{ t('admin.system.form.key') }}</span>
          <input
            id="system-flag-key"
            v-model="form.key"
            name="systemFlagKey"
            required
            pattern="[a-z0-9_.:-]{2,120}"
            :placeholder="t('admin.system.form.keyPlaceholder')"
          />
        </label>
        <label class="field">
          <span>{{ t('admin.system.form.scope') }}</span>
          <select id="system-flag-scope" v-model="form.scope" name="systemFlagScope">
            <option value="GLOBAL">{{ t('admin.system.scopes.global') }}</option>
            <option value="MCP_TOOL">{{ t('admin.system.scopes.mcpTool') }}</option>
          </select>
        </label>
        <label class="field">
          <span>{{ t('admin.system.form.displayName') }}</span>
          <input
            id="system-flag-display-name"
            v-model="form.displayName"
            name="systemFlagDisplayName"
            required
            maxlength="160"
            :placeholder="t('admin.system.form.displayNamePlaceholder')"
          />
        </label>
        <label class="field">
          <span>{{ t('admin.system.form.flagDescription') }}</span>
          <input
            id="system-flag-description"
            v-model="form.description"
            name="systemFlagDescription"
            maxlength="500"
            :placeholder="t('admin.system.form.descriptionPlaceholder')"
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
          <strong>{{
            form.enabled ? t('admin.system.states.enabled') : t('admin.system.states.disabled')
          }}</strong>
        </label>
        <button type="submit">{{ t('admin.system.actions.saveFlag') }}</button>
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
            <h3>{{ groupLabel(group) }}</h3>
            <span aria-hidden="true">{{ collapsedGroups[group] ? '›' : '⌄' }}</span>
          </button>
          <div v-if="!collapsedGroups[group]" :id="groupId(group)" class="toggle-list">
            <label
              v-for="template in templates.filter((item) => item.group === group)"
              :key="template.key"
              class="toggle-row"
              :class="{ disabled: !currentEnabled(template) }"
            >
              <span class="control-copy">
                <strong>{{
                  currentFlag(template)?.displayName ??
                  (template.displayNameKey
                    ? t(`admin.system.controls.${template.displayNameKey}.name`)
                    : template.displayName)
                }}</strong>
                <small>{{ template.key }}</small>
                <em>{{
                  currentFlag(template)?.description ??
                  t(`admin.system.controls.${template.noteKey}.note`)
                }}</em>
              </span>
              <span class="switch-wrap">
                <input
                  type="checkbox"
                  :checked="currentFlag(template)?.enabled ?? template.enabled"
                  :disabled="template.scope === 'MCP_TOOL' && !mcpGlobalEnabled()"
                  :aria-label="
                    t('admin.system.actions.toggle', {
                      name:
                        currentFlag(template)?.displayName ??
                        (template.displayNameKey
                          ? t(`admin.system.controls.${template.displayNameKey}.name`)
                          : template.displayName),
                    })
                  "
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
      <h3>{{ t('admin.system.otherControls.title') }}</h3>
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
            <em>{{ flag.description || t('admin.system.otherControls.noDescription') }}</em>
          </span>
          <span class="switch-wrap">
            <input
              type="checkbox"
              :checked="flag.enabled"
              :aria-label="t('admin.system.actions.toggle', { name: flag.displayName })"
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
