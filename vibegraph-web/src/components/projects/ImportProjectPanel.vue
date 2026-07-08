<script setup lang="ts">
/**
 * ImportProjectPanel - unified import surface.
 *
 * Combines the three import flows (local folder, archive upload, GitHub URL)
 * into a single card with a segmented control, so the user switches methods in
 * place instead of scanning three separate cards. Each underlying form is
 * rendered `embedded` (no card chrome / header) and the panel owns the title,
 * tabs, accent and per-method description.
 */
import { computed, ref } from 'vue'
import type { Project } from '@/lib/api'
import AddProjectArchive from '@/components/projects/AddProjectArchive.vue'
import AddProjectLocal from '@/components/projects/AddProjectLocal.vue'
import GitHubImportForm from '@/components/projects/GitHubImportForm.vue'

const emit = defineEmits<{
  imported: [project: Project]
}>()

type Method = 'local' | 'archive' | 'github'

interface MethodTab {
  id: Method
  label: string
  short: string
  description: string
  accent: string
  accentSoft: string
}

const tabs: MethodTab[] = [
  {
    id: 'local',
    label: 'Local folder',
    short: 'Local',
    description:
      'Analyze a folder already on the machine running VibeGraph — the graph updates in realtime as you edit, no zip needed.',
    accent: 'var(--vg-blue-bright)',
    accentSoft: 'rgba(96, 165, 250, 0.16)',
  },
  {
    id: 'archive',
    label: 'Archive',
    short: 'Archive',
    description: 'Upload a Java project archive (.zip, .tar, .tar.gz, .tgz). VibeGraph extracts and analyzes it for you.',
    accent: 'var(--vg-cyan)',
    accentSoft: 'rgba(34, 211, 238, 0.16)',
  },
  {
    id: 'github',
    label: 'GitHub',
    short: 'GitHub',
    description: 'Point VibeGraph at any public GitHub repository by its HTTPS URL and it clones, indexes and maps it.',
    accent: 'var(--vg-violet)',
    accentSoft: 'rgba(167, 139, 250, 0.16)',
  },
]

const active = ref<Method>('local')
const activeTab = computed<MethodTab>(() => tabs.find((t) => t.id === active.value) ?? tabs[0]!)

function onImported(project: Project): void {
  emit('imported', project)
}

// Each method gets a distinct icon so the segmented control reads at a glance.
function iconPath(id: Method): string {
  switch (id) {
    case 'local':
      return 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'
    case 'archive':
      return 'M3 7l9-4 9 4v10l-9 4-9-4z M3 7l9 4 9-4 M12 11v10'
    case 'github':
    default:
      return 'M9 19c-4.3 1.4-4.3-2.5-6-3m12 5v-3.5c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.2 4.2 0 0 0-.1-3.2s-1.1-.3-3.5 1.3a12 12 0 0 0-6.2 0C6.5 2.8 5.4 3.1 5.4 3.1a4.2 4.2 0 0 0-.1 3.2A4.6 4.6 0 0 0 4 9.5c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21'
  }
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
        <h2 id="import-panel-heading" class="import-panel__title">Import a project</h2>
        <span class="import-panel__badge">Java</span>
      </div>
      <p class="import-panel__desc">{{ activeTab.description }}</p>
    </header>

    <div class="import-panel__tabs" role="tablist" aria-label="Import method">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="import-panel__tab"
        :class="{ 'import-panel__tab--active': active === tab.id }"
        :style="active === tab.id ? { '--accent': tab.accent, '--accent-soft': tab.accentSoft } : {}"
        type="button"
        role="tab"
        :aria-selected="active === tab.id"
        :data-test="`import-tab-${tab.id}`"
        @click="active = tab.id"
      >
        <svg
          class="import-panel__tab-icon"
          viewBox="0 0 24 24"
          width="18"
          height="18"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path :d="iconPath(tab.id)" />
        </svg>
        <span class="import-panel__tab-label">{{ tab.label }}</span>
        <span class="import-panel__tab-label-short" aria-hidden="true">{{ tab.short }}</span>
      </button>
    </div>

    <div class="import-panel__body">
      <Transition name="import-fade" mode="out-in">
        <AddProjectLocal v-if="active === 'local'" key="local" embedded @imported="onImported" />
        <AddProjectArchive v-else-if="active === 'archive'" key="archive" :async="true" embedded @imported="onImported" />
        <GitHubImportForm v-else key="github" embedded @imported="onImported" />
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
  gap: var(--vg-space-6);
  overflow: hidden;
  padding: clamp(1.25rem, 1rem + 1.5vw, 2rem);
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
  transition: opacity var(--vg-dur) var(--vg-ease-out), background var(--vg-dur);
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
  justify-content: center;
  gap: 0.5rem;
  font: inherit;
  font-weight: 600;
  font-size: var(--vg-text-sm);
  padding: 0.6rem 0.85rem;
  border: 1px solid transparent;
  border-radius: calc(var(--vg-radius) - 4px);
  background: transparent;
  color: var(--vg-text-muted);
  cursor: pointer;
  white-space: nowrap;
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out),
    color var(--vg-dur-fast) var(--vg-ease-out), border-color var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur) var(--vg-ease-out);
}

.import-panel__tab:hover {
  color: var(--vg-text);
  background: rgba(148, 163, 184, 0.08);
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
  transition: opacity var(--vg-dur) var(--vg-ease-out), transform var(--vg-dur) var(--vg-ease-out);
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
