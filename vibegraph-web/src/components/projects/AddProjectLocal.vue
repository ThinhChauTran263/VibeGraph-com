<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Project } from '@/lib/api'
import { useLocalImport } from '@/composables/useLocalImport'
import DirectoryBrowserModal from '@/components/projects/DirectoryBrowserModal.vue'
import Spinner from '@/components/ui/Spinner.vue'

const emit = defineEmits<{
  imported: [project: Project]
}>()

// When embedded inside the unified ImportProjectPanel, the form drops its own
// card chrome + header (the panel provides them) and renders just the fields.
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const path = ref('')
const name = ref('')
const browserOpen = ref(false)
const { status, errorMessage, progress, isImporting, importLocal, reset } = useLocalImport()

const canSubmit = computed(() => path.value.trim().length > 0 && !isImporting.value)
const progressPct = computed(() => Math.round(progress.value))
const progressLabel = computed(() =>
  progressPct.value >= 98 ? 'Finalizing graph…' : `Analyzing folder… ${progressPct.value}%`,
)
// Button caption mirrors progress so the percentage is visible on the button too.
const submitLabel = computed(() =>
  progressPct.value >= 98 ? 'Finalizing…' : `Importing… ${progressPct.value}%`,
)

function onBrowseSelect(selected: string): void {
  path.value = selected
  browserOpen.value = false
}

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) return
  const project = await importLocal(path.value, name.value)
  if (project) {
    emit('imported', project)
  }
}

function clearForm(): void {
  path.value = ''
  name.value = ''
  reset()
}
</script>

<template>
  <section class="local-import" :class="{ 'local-import--embedded': embedded }" aria-labelledby="local-import-heading">
    <header v-if="!embedded" class="local-import__header">
      <span class="local-import__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
        </svg>
      </span>
      <div class="local-import__heading-group">
        <h2 id="local-import-heading">Add project from a local folder</h2>
        <p class="local-import__hint">
          Analyze a folder that already exists on the machine running VibeGraph. The graph then
          updates in realtime as you edit those files — no zip needed.
        </p>
      </div>
    </header>

    <form class="local-import__form" @submit.prevent="onSubmit">
      <label class="local-import__field">
        <span class="local-import__label">Project folder path</span>
        <div class="local-import__path-row">
          <input
            v-model="path"
            class="local-import__text-input"
            type="text"
            name="projectPath"
            placeholder="D:\Users\me\IdeaProjects\my-app"
            :disabled="isImporting"
            autocomplete="off"
            spellcheck="false"
          />
          <button
            type="button"
            class="local-import__btn local-import__btn--ghost"
            data-test="local-browse"
            :disabled="isImporting"
            @click="browserOpen = true"
          >
            Browse…
          </button>
        </div>
      </label>

      <label class="local-import__field">
        <span class="local-import__label">Display name (optional)</span>
        <input
          v-model="name"
          class="local-import__text-input"
          type="text"
          name="displayName"
          placeholder="my-app"
          :disabled="isImporting"
          autocomplete="off"
        />
      </label>

      <div class="local-import__actions">
        <button type="submit" class="local-import__btn local-import__btn--primary" :disabled="!canSubmit">
          <span v-if="isImporting" class="local-import__submitting">
            <Spinner size="sm" aria-hidden="true" />
            <span>{{ submitLabel }}</span>
          </span>
          <span v-else>Import folder</span>
        </button>
        <button type="button" class="local-import__btn local-import__btn--ghost" :disabled="isImporting" @click="clearForm">
          Reset
        </button>
      </div>

      <div
        v-if="isImporting"
        class="local-import__progress"
        role="progressbar"
        :aria-valuenow="progressPct"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-label="progressLabel"
      >
        <div class="local-import__progress-head">
          <span class="local-import__progress-label">{{ progressLabel }}</span>
          <span class="local-import__progress-value">{{ progressPct }}%</span>
        </div>
        <div class="local-import__progress-track">
          <div class="local-import__progress-fill" :style="{ width: `${progressPct}%` }"></div>
        </div>
      </div>

      <p v-if="status === 'error' && errorMessage" class="local-import__error" role="alert">{{ errorMessage }}</p>
    </form>

    <DirectoryBrowserModal :open="browserOpen" @select="onBrowseSelect" @close="browserOpen = false" />
  </section>
</template>

<style scoped>
.local-import {
  --accent: var(--vg-blue-bright);
  --accent-soft: rgba(96, 165, 250, 0.16);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 1.35rem;
  padding: 1.6rem;
  overflow: hidden;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-lg);
  background: var(--vg-grad-surface);
  color: var(--vg-text);
  box-shadow: var(--vg-shadow);
  transition: border-color var(--vg-dur) var(--vg-ease-out),
    transform var(--vg-dur) var(--vg-ease-out), box-shadow var(--vg-dur) var(--vg-ease-out);
}

/* Accent shine along the top edge — gives each card a distinct identity. */
.local-import::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  opacity: 0.7;
}

/* Soft accent glow that lifts on hover. */
.local-import::after {
  content: '';
  position: absolute;
  top: -40%;
  right: -20%;
  width: 60%;
  height: 80%;
  background: radial-gradient(circle, var(--accent-soft), transparent 70%);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--vg-dur) var(--vg-ease-out);
}

.local-import:hover {
  border-color: var(--vg-border-strong);
  transform: translateY(-3px);
  box-shadow: var(--vg-shadow-lg);
}
.local-import:hover::after {
  opacity: 1;
}

/* Embedded inside the unified panel: drop the card so only the fields show. */
.local-import--embedded {
  gap: 1.1rem;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: none;
  box-shadow: none;
}
.local-import--embedded::before,
.local-import--embedded::after {
  display: none;
}
.local-import--embedded:hover {
  transform: none;
  box-shadow: none;
}

.local-import__header {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
}

.local-import__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: var(--vg-radius);
  border: 1px solid var(--vg-border-strong);
  background: var(--accent-soft);
  color: var(--accent);
}

.local-import__heading-group {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.local-import__form,
.local-import__field {
  display: flex;
  flex-direction: column;
}

.local-import__form {
  gap: 1.1rem;
}

.local-import__field {
  gap: 0.45rem;
}

.local-import__header h2 {
  margin: 0;
  font-size: var(--vg-text-lg);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.local-import__hint,
.local-import__error {
  margin: 0;
  font-size: var(--vg-text-sm);
}

.local-import__hint {
  color: var(--vg-text-muted);
  line-height: 1.5;
}

.local-import__label {
  font-size: var(--vg-text-sm);
  font-weight: 500;
  color: var(--vg-text-muted);
}

.local-import__path-row {
  display: flex;
  gap: 0.5rem;
}

.local-import__text-input {
  flex: 1;
  min-width: 0;
  font: inherit;
  color: var(--vg-text);
  padding: 0.6rem 0.85rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius-sm);
  background: rgba(7, 11, 22, 0.55);
  transition: border-color var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur-fast) var(--vg-ease-out), background-color var(--vg-dur-fast);
}

.local-import__text-input::placeholder {
  color: var(--vg-text-dim);
}

.local-import__text-input:hover:not(:disabled) {
  border-color: var(--accent);
}

.local-import__text-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
  background: rgba(7, 11, 22, 0.75);
}

.local-import__text-input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.local-import__actions {
  display: flex;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.local-import__btn {
  font: inherit;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1.15rem;
  border-radius: var(--vg-radius-pill);
  border: 1px solid var(--vg-border-strong);
  background: rgba(148, 163, 184, 0.06);
  color: var(--vg-text);
  cursor: pointer;
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out),
    border-color var(--vg-dur-fast) var(--vg-ease-out), transform var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur) var(--vg-ease-out);
}

.local-import__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.local-import__btn--primary {
  background: var(--vg-grad-blue);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 8px 24px -10px rgba(59, 130, 246, 0.7);
}

.local-import__btn--primary:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow: 0 0 0 1px rgba(96, 165, 250, 0.5), 0 18px 40px -14px rgba(59, 130, 246, 0.8);
}
.local-import__btn--primary:not(:disabled):active {
  transform: translateY(0);
}

.local-import__btn--ghost:not(:disabled):hover {
  border-color: var(--accent);
  background: rgba(148, 163, 184, 0.12);
}

.local-import__submitting {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.local-import__progress {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.local-import__progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.local-import__progress-value {
  font-family: var(--vg-font-display);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--vg-text);
}

.local-import__progress-track {
  position: relative;
  height: 8px;
  border-radius: var(--vg-radius-pill);
  background: rgba(7, 11, 22, 0.6);
  border: 1px solid var(--vg-border);
  overflow: hidden;
}

.local-import__progress-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--vg-grad-brand);
  transition: width var(--vg-dur) var(--vg-ease-out);
}

@media (prefers-reduced-motion: reduce) {
  .local-import,
  .local-import__btn,
  .local-import__text-input,
  .local-import__progress-fill {
    transition: none;
  }
}

.local-import__error {
  padding: 0.6rem 0.85rem;
  border: 1px solid rgba(239, 68, 68, 0.45);
  border-radius: var(--vg-radius-sm);
  background: rgba(127, 29, 29, 0.2);
  color: #fca5a5;
}
</style>
