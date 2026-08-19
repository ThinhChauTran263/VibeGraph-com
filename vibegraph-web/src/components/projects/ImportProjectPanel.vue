<script setup lang="ts">
/**
 * ImportProjectPanel - unified import surface.
 *
 * Combines the supported import flows (CLI push, archive upload, GitHub URL)
 * into a single card with a segmented control, so the user switches methods in
 * place instead of scanning three separate cards. Each underlying form is
 * rendered `embedded` (no card chrome / header) and the panel owns the title,
 * tabs, accent and per-method description.
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Project } from '@/lib/api'
import AddProjectArchive from '@/components/projects/AddProjectArchive.vue'
import AddProjectCli from '@/components/projects/AddProjectCli.vue'
import GitHubImportForm from '@/components/projects/GitHubImportForm.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

type Method = 'cli' | 'archive' | 'github'

const { t } = useI18n({ useScope: 'global' })
const props = withDefaults(
  defineProps<{
    disabledMethods?: Partial<Record<Method, string | null>>
  }>(),
  { disabledMethods: () => ({}) },
)

const emit = defineEmits<{
  imported: [project: Project]
  /** User dismissed the dialog; in-flight imports keep running in background. */
  backgrounded: []
}>()

interface MethodTab {
  id: Method
  label: string
  short: string
  description: string
  accent: string
  accentSoft: string
}

const tabs = computed<MethodTab[]>(() => [
  {
    id: 'cli',
    label: t('user.projects.cliPush'),
    short: t('user.projects.cliShort'),
    description: t('user.projects.cliDescription'),
    accent: 'var(--vg-blue-bright)',
    accentSoft: 'rgba(96, 165, 250, 0.16)',
  },
  {
    id: 'archive',
    label: t('user.projects.archive'),
    short: t('user.projects.archive'),
    description: t('user.projects.archiveDescription'),
    accent: 'var(--vg-cyan)',
    accentSoft: 'rgba(34, 211, 238, 0.16)',
  },
  {
    id: 'github',
    label: 'GitHub',
    short: 'GitHub',
    description: t('user.projects.githubDescription'),
    accent: 'var(--vg-violet)',
    accentSoft: 'rgba(167, 139, 250, 0.16)',
  },
])

const enabledTabs = computed(() => tabs.value.filter((tab) => !props.disabledMethods[tab.id]))
const hasEnabledMethod = computed(() => enabledTabs.value.length > 0)
const active = ref<Method>(enabledTabs.value[0]?.id ?? 'cli')
const activeTab = computed<MethodTab>(() => tabs.value.find((tab) => tab.id === active.value) ?? tabs.value[0]!)

watch(
  () => props.disabledMethods,
  () => {
    if (props.disabledMethods[active.value]) {
      active.value = enabledTabs.value[0]?.id ?? 'cli'
    }
  },
  { deep: true, immediate: true },
)

function onImported(project: Project): void {
  emit('imported', project)
}

function selectMethod(method: Method): void {
  if (!props.disabledMethods[method]) active.value = method
}

// F-L4: each method gets a distinct icon so the segmented control reads at a
// glance; the SVG paths themselves live in the shared AppIcon registry.
const ICON_NAMES: Record<Method, string> = {
  cli: 'terminal',
  archive: 'package',
  github: 'github',
}
</script>

<template>
  <section
    class="import-panel"
    :style="{ '--accent': activeTab.accent, '--accent-soft': activeTab.accentSoft }"
    aria-labelledby="import-panel-heading"
  >
    <header class="import-panel__head">
      <div class="import-panel__title-row">
        <h2 id="import-panel-heading" class="import-panel__title">{{ t('user.projects.importDialogTitle') }}</h2>
        <span class="import-panel__badge">Java</span>
      </div>
      <p class="import-panel__desc">
        {{ hasEnabledMethod ? activeTab.description : t('user.projects.noImportMethod') }}
      </p>
    </header>

    <div class="import-panel__tabs" role="tablist" :aria-label="t('user.projects.importMethod')">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="import-panel__tab"
        :class="{ 'import-panel__tab--active': active === tab.id }"
        :style="
          active === tab.id ? { '--accent': tab.accent, '--accent-soft': tab.accentSoft } : {}
        "
        type="button"
        role="tab"
        :disabled="Boolean(props.disabledMethods[tab.id])"
        :aria-selected="active === tab.id"
        :title="props.disabledMethods[tab.id] || undefined"
        :data-test="`import-tab-${tab.id}`"
        @click="selectMethod(tab.id)"
      >
        <AppIcon class="import-panel__tab-icon" :name="ICON_NAMES[tab.id]" :size="18" />
        <span class="import-panel__tab-label">{{ tab.label }}</span>
        <span class="import-panel__tab-label-short" aria-hidden="true">{{ tab.short }}</span>
      </button>
    </div>

    <p v-if="!hasEnabledMethod" class="import-panel__disabled" role="status">
      {{ t('user.projects.noImportMethodDescription') }}
    </p>

    <div v-else class="import-panel__body">
      <Transition name="import-fade" mode="out-in">
        <AddProjectCli v-if="active === 'cli'" key="cli" embedded @imported="onImported" />
        <AddProjectArchive
          v-else-if="active === 'archive'"
          key="archive"
          :async="true"
          embedded
          @imported="onImported"
          @backgrounded="emit('backgrounded')"
        />
        <GitHubImportForm
          v-else
          key="github"
          embedded
          @imported="onImported"
          @backgrounded="emit('backgrounded')"
        />
      </Transition>
    </div>
  </section>
</template>

<style scoped>
.import-panel {
  --accent: var(--vg-blue-bright);
  --accent-soft: rgba(96, 165, 250, 0.16);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
  overflow: hidden;
  padding: var(--vg-space-4);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-lg);
  background: var(--vg-grad-surface);
  box-shadow: var(--vg-shadow);
  transition: border-color var(--vg-dur) var(--vg-ease-out);
}

/* Accent shine reflecting the active method. */
.import-panel::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  opacity: 0.8;
  transition: background var(--vg-dur) var(--vg-ease-out);
}

.import-panel::after {
  content: '';
  position: absolute;
  top: -30%;
  right: -10%;
  width: 50%;
  height: 70%;
  background: radial-gradient(circle, var(--accent-soft), transparent 70%);
  opacity: 0.7;
  pointer-events: none;
  transition:
    opacity var(--vg-dur) var(--vg-ease-out),
    background var(--vg-dur);
}

.import-panel__head {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.import-panel__title-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.import-panel__title {
  margin: 0;
  font-size: var(--vg-text-xl);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.import-panel__badge {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  padding: 0.18rem 0.55rem;
  border-radius: var(--vg-radius-pill);
  border: 1px solid var(--vg-border-strong);
  color: var(--accent);
  background: var(--accent-soft);
}

.import-panel__desc {
  margin: 0;
  min-height: 2.6em;
  max-width: 46rem;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.5;
}

/* Segmented control. */
.import-panel__tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.35rem;
  padding: 0.35rem;
  border-radius: var(--vg-radius);
  border: 1px solid var(--vg-border);
  background: rgba(7, 11, 22, 0.5);
}

.import-panel__tab {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.5rem;
  min-height: 38px;
  font: inherit;
  font-weight: 600;
  font-size: var(--vg-text-sm);
  padding: 0.45rem 0.7rem;
  text-align: left;
  border: 1px solid transparent;
  border-radius: calc(var(--vg-radius) - 4px);
  background: transparent;
  color: var(--vg-text-muted);
  cursor: pointer;
  white-space: nowrap;
  transition:
    background-color var(--vg-dur-fast) var(--vg-ease-out),
    color var(--vg-dur-fast) var(--vg-ease-out),
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur) var(--vg-ease-out);
}

.import-panel__tab:hover:not(:disabled) {
  color: var(--vg-text);
  background: rgba(148, 163, 184, 0.08);
}

.import-panel__tab:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.import-panel__disabled {
  margin: 0;
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-bg);
  color: var(--vg-amber);
  font-size: var(--vg-text-sm);
}

.import-panel__tab--active {
  color: var(--accent);
  border-color: color-mix(in oklab, var(--accent) 45%, transparent);
  background: var(--accent-soft);
  box-shadow: 0 6px 18px -10px var(--accent-soft);
}

.import-panel__tab-icon {
  flex-shrink: 0;
}

.import-panel__tab-label-short {
  display: none;
}

.import-panel__body {
  position: relative;
}

/* Method switch transition. */
.import-fade-enter-active,
.import-fade-leave-active {
  transition:
    opacity var(--vg-dur) var(--vg-ease-out),
    transform var(--vg-dur) var(--vg-ease-out);
}
.import-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.import-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 30rem) {
  .import-panel__tab {
    gap: 0.35rem;
    padding: 0.55rem 0.4rem;
  }
  .import-panel__tab-label {
    display: none;
  }
  .import-panel__tab-label-short {
    display: inline;
  }
}

@media (prefers-reduced-motion: reduce) {
  .import-panel,
  .import-panel::before,
  .import-panel::after,
  .import-panel__tab,
  .import-fade-enter-active,
  .import-fade-leave-active {
    transition: none;
  }
  .import-fade-enter-from,
  .import-fade-leave-to {
    transform: none;
  }
}
</style>
