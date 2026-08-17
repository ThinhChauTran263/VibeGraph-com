<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Project } from '@/lib/api'
import { useGitHubImport } from '@/composables/useGitHubImport'
import Spinner from '@/components/ui/Spinner.vue'

const { t } = useI18n({ useScope: 'global' })
const emit = defineEmits<{
  imported: [project: Project]
}>()

// Embedded inside the unified ImportProjectPanel: drop card chrome + header.
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const repoUrl = ref('')
const { status, errorMessage, importedProject, progress, isImporting, importGithub, reset } =
  useGitHubImport()

const canSubmit = computed(() => repoUrl.value.trim().length > 0 && !isImporting.value)
const progressPct = computed(() => Math.round(progress.value))
const progressLabel = computed(() =>
  progressPct.value >= 98
    ? t('user.import.finalizing')
    : `${t('user.import.analyzing')} ${progressPct.value}%`,
)
// Button caption mirrors progress so the percentage is visible on the button too.
const submitLabel = computed(() =>
  progressPct.value >= 98
    ? t('user.import.finalizing')
    : `${t('user.import.importing')} ${progressPct.value}%`,
)

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) return
  const project = await importGithub(repoUrl.value)
  if (project) {
    emit('imported', project)
  }
}

function clearForm(): void {
  repoUrl.value = ''
  reset()
}
</script>

<template>
  <section
    class="github-import"
    :class="{ 'github-import--embedded': embedded }"
    aria-labelledby="github-import-heading"
  >
    <header v-if="!embedded" class="github-import__header">
      <span class="github-import__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path
            d="M12 2C6.48 2 2 6.48 2 12c0 4.42 2.87 8.17 6.84 9.5.5.09.66-.22.66-.48v-1.7c-2.78.6-3.37-1.34-3.37-1.34-.45-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.6.07-.6 1 .07 1.53 1.03 1.53 1.03.89 1.53 2.34 1.09 2.91.83.09-.65.35-1.09.63-1.34-2.22-.25-4.55-1.11-4.55-4.94 0-1.09.39-1.98 1.03-2.68-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.75 1.02a9.56 9.56 0 0 1 5 0c1.91-1.29 2.75-1.02 2.75-1.02.55 1.38.2 2.4.1 2.65.64.7 1.03 1.59 1.03 2.68 0 3.84-2.34 4.69-4.57 4.94.36.31.68.92.68 1.85v2.74c0 .27.16.58.67.48A10.02 10.02 0 0 0 22 12c0-5.52-4.48-10-10-10z"
          />
        </svg>
      </span>
      <div class="github-import__heading-group">
        <h2 id="github-import-heading">{{ t('user.import.githubTitle') }}</h2>
        <p class="github-import__hint">
          {{ t('user.import.githubHint') }}
          https://github.com/spring-projects/spring-petclinic
        </p>
      </div>
    </header>

    <form class="github-import__form" @submit.prevent="onSubmit">
      <label class="github-import__field">
        <span class="github-import__label">{{ t('user.import.githubUrl') }}</span>
        <input
          v-model="repoUrl"
          class="github-import__text-input"
          type="url"
          name="repoUrl"
          :placeholder="t('user.import.githubPlaceholder')"
          :disabled="isImporting"
          aria-required="true"
          autocomplete="off"
          spellcheck="false"
          @input="reset"
        />
      </label>

      <div class="github-import__actions">
        <button
          type="submit"
          class="github-import__btn github-import__btn--primary"
          :disabled="!canSubmit"
        >
          <span v-if="isImporting" class="github-import__submit-spinner">
            <Spinner size="sm" aria-hidden="true" />
            <span>{{ submitLabel }}</span>
          </span>
          <span v-else>{{ t('user.import.importGithub') }}</span>
        </button>
        <button
          type="button"
          class="github-import__btn github-import__btn--ghost"
          :disabled="isImporting"
          @click="clearForm"
        >
          {{ t('user.import.reset') }}
        </button>
      </div>

      <div
        v-if="isImporting"
        class="github-import__progress"
        role="progressbar"
        :aria-valuenow="progressPct"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-label="progressLabel"
      >
        <div class="github-import__progress-head">
          <span class="github-import__progress-label">{{ progressLabel }}</span>
          <span class="github-import__progress-value">{{ progressPct }}%</span>
        </div>
        <div class="github-import__progress-track">
          <div class="github-import__progress-fill" :style="{ width: `${progressPct}%` }"></div>
        </div>
      </div>

      <p v-if="status === 'error' && errorMessage" class="github-import__error" role="alert">
        {{ errorMessage }}
      </p>

      <p
        v-if="status === 'success' && importedProject"
        class="github-import__success"
        role="status"
      >
        {{ t('user.import.success', { name: importedProject.name, status: importedProject.status }) }}
      </p>
    </form>
  </section>
</template>

<style scoped>
.github-import {
  --accent: var(--vg-violet);
  --accent-soft: rgba(167, 139, 250, 0.16);
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
  transition:
    border-color var(--vg-dur) var(--vg-ease-out),
    transform var(--vg-dur) var(--vg-ease-out),
    box-shadow var(--vg-dur) var(--vg-ease-out);
}

.github-import::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  opacity: 0.7;
}

.github-import::after {
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

.github-import:hover {
  border-color: var(--vg-border-strong);
  transform: translateY(-3px);
  box-shadow: var(--vg-shadow-lg);
}
.github-import:hover::after {
  opacity: 1;
}

/* Embedded inside the unified panel: drop the card so only the fields show. */
.github-import--embedded {
  gap: 1.1rem;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: none;
  box-shadow: none;
}
.github-import--embedded::before,
.github-import--embedded::after {
  display: none;
}
.github-import--embedded:hover {
  transform: none;
  box-shadow: none;
}

.github-import__header {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
}

.github-import__icon {
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

.github-import__heading-group {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.github-import__form,
.github-import__field {
  display: flex;
  flex-direction: column;
}

.github-import__form {
  gap: 1.1rem;
}

.github-import__field {
  gap: 0.45rem;
}

.github-import__header h2 {
  margin: 0;
  font-size: var(--vg-text-lg);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.github-import__hint,
.github-import__error,
.github-import__success {
  margin: 0;
  font-size: var(--vg-text-sm);
}

.github-import__hint {
  color: var(--vg-text-muted);
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.github-import__label {
  font-size: var(--vg-text-sm);
  font-weight: 500;
  color: var(--vg-text-muted);
}

.github-import__text-input {
  font: inherit;
  color: var(--vg-text);
  padding: 0.6rem 0.85rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius-sm);
  background: rgba(7, 11, 22, 0.55);
  transition:
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur-fast) var(--vg-ease-out),
    background-color var(--vg-dur-fast);
}

.github-import__text-input::placeholder {
  color: var(--vg-text-dim);
}

.github-import__text-input:hover:not(:disabled) {
  border-color: var(--accent);
}

.github-import__text-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
  background: rgba(7, 11, 22, 0.75);
}

.github-import__text-input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.github-import__actions {
  display: flex;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.github-import__btn {
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
  transition:
    background-color var(--vg-dur-fast) var(--vg-ease-out),
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    transform var(--vg-dur-fast) var(--vg-ease-out),
    box-shadow var(--vg-dur) var(--vg-ease-out);
}

.github-import__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.github-import__btn--primary {
  background: linear-gradient(135deg, #a78bfa, #7c3aed);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 8px 24px -10px rgba(124, 58, 237, 0.7);
}

.github-import__btn--primary:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow:
    0 0 0 1px rgba(167, 139, 250, 0.5),
    0 18px 40px -14px rgba(124, 58, 237, 0.8);
}
.github-import__btn--primary:not(:disabled):active {
  transform: translateY(0);
}

.github-import__btn--ghost:not(:disabled):hover {
  border-color: var(--accent);
  background: rgba(148, 163, 184, 0.12);
}

.github-import__submit-spinner {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.github-import__progress {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.github-import__progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.github-import__progress-value {
  font-family: var(--vg-font-display);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--vg-text);
}

.github-import__progress-track {
  position: relative;
  height: 8px;
  border-radius: var(--vg-radius-pill);
  background: rgba(7, 11, 22, 0.6);
  border: 1px solid var(--vg-border);
  overflow: hidden;
}

.github-import__progress-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--vg-grad-brand);
  transition: width var(--vg-dur) var(--vg-ease-out);
}

@media (prefers-reduced-motion: reduce) {
  .github-import,
  .github-import__btn,
  .github-import__text-input,
  .github-import__progress-fill {
    transition: none;
  }
}

.github-import__error {
  padding: 0.6rem 0.85rem;
  border: 1px solid rgba(239, 68, 68, 0.45);
  border-radius: var(--vg-radius-sm);
  background: rgba(127, 29, 29, 0.2);
  color: #fca5a5;
}

.github-import__success {
  padding: 0.6rem 0.85rem;
  border: 1px solid rgba(34, 197, 94, 0.4);
  border-radius: var(--vg-radius-sm);
  background: rgba(20, 83, 45, 0.2);
  color: var(--vg-green-bright);
}
</style>
