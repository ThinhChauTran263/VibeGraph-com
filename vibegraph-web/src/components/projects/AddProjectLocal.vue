<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Project } from '@/lib/api'
import { useLocalImport } from '@/composables/useLocalImport'
import DirectoryBrowserModal from '@/components/projects/DirectoryBrowserModal.vue'
import Spinner from '@/components/ui/Spinner.vue'

const emit = defineEmits<{
  imported: [project: Project]
}>()

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
  <section class="local-import" aria-labelledby="local-import-heading">
    <header class="local-import__header">
      <h2 id="local-import-heading">Add project from a local folder</h2>
      <p class="local-import__hint">
        Analyze a folder that already exists on the machine running VibeGraph. The graph then
        updates in realtime as you edit those files — no zip needed.
      </p>
    </header>

    <form class="local-import__form" @submit.prevent="onSubmit">
      <label class="local-import__field">
        <span class="local-import__label">Project folder path</span>
        <div class="local-import__path-row">
          <input
            v-model="path"
            class="local-import__text-input"
            type="text"
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
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: 1.5rem;
  border: 1px solid #2a2a2a;
  border-radius: 8px;
  background: #111;
  color: #e5e7eb;
  max-width: 36rem;
}

.local-import__header,
.local-import__form,
.local-import__field {
  display: flex;
  flex-direction: column;
}

.local-import__header {
  gap: 0.25rem;
}

.local-import__form {
  gap: 1rem;
}

.local-import__field {
  gap: 0.4rem;
}

.local-import__header h2 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
}

.local-import__hint,
.local-import__error {
  margin: 0;
  font-size: 0.875rem;
}

.local-import__hint {
  color: #9ca3af;
}

.local-import__label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #d1d5db;
}

.local-import__path-row {
  display: flex;
  gap: 0.5rem;
}

.local-import__text-input {
  flex: 1;
  font: inherit;
  color: inherit;
  padding: 0.5rem 0.75rem;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  background: #1f1f1f;
}

.local-import__text-input:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 2px;
  border-color: #2563eb;
}

.local-import__text-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.local-import__actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.local-import__btn {
  font: inherit;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  border: 1px solid #2a2a2a;
  background: transparent;
  color: inherit;
  cursor: pointer;
  transition: background-color 150ms ease, border-color 150ms ease, color 150ms ease;
}

.local-import__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.local-import__btn--primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #ffffff;
}

.local-import__btn--primary:not(:disabled):hover {
  background: #1d4ed8;
  border-color: #1d4ed8;
}

.local-import__btn--ghost:not(:disabled):hover {
  border-color: #4b5563;
  color: #f3f4f6;
}

.local-import__submitting {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.local-import__progress {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.local-import__progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 0.8125rem;
  color: #cbd5e1;
}

.local-import__progress-value {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #e5e7eb;
}

.local-import__progress-track {
  position: relative;
  height: 8px;
  border-radius: 999px;
  background: #1f1f1f;
  border: 1px solid #2a2a2a;
  overflow: hidden;
}

.local-import__progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #60a5fa);
  transition: width 300ms cubic-bezier(0.16, 1, 0.3, 1);
}

@media (prefers-reduced-motion: reduce) {
  .local-import__progress-fill {
    transition: none;
  }
}

.local-import__error {
  padding: 0.5rem 0.75rem;
  border: 1px solid #7f1d1d;
  border-radius: 6px;
  background: rgba(127, 29, 29, 0.2);
  color: #f87171;
}
</style>
