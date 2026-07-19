<script setup lang="ts">
/**
 * AddProjectArchive - file-picker form for uploading a project archive.
 *
 * Backend contract (Sprint 2): `POST /api/projects/import-archive`
 * multipart fields `name` + `file`. See
 * `.kiro/specs/project-folder-upload/design.md`.
 *
 * The component is intentionally self-contained: it owns its form state and
 * delegates the network call to `useArchiveImport`. The parent emits hook is
 * `imported` so the host view can navigate or refresh its project list.
 */

import { computed, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ARCHIVE_ACCEPT_ATTRIBUTE,
  ARCHIVE_MAX_SIZE_BYTES,
  formatFileSize,
  validateArchiveFile,
} from '@/lib/archiveUpload'
import type { Project } from '@/lib/api'
import { useArchiveImport } from '@/composables/useArchiveImport'
import Spinner from '@/components/ui/Spinner.vue'

const { t } = useI18n({ useScope: 'global' })
const props = withDefaults(
  defineProps<{
    /**
     * When true, submit uses the async import endpoint (`?async=true`) and
     * tracks progress over WebSocket. Default false keeps the synchronous
     * baseline behavior unchanged.
     */
    async?: boolean
    /** Embedded inside the unified panel: drop card chrome + header. */
    embedded?: boolean
  }>(),
  { async: false, embedded: false },
)

const emit = defineEmits<{
  imported: [project: Project]
}>()

const projectName = ref('')
const selectedFile = ref<File | null>(null)
const fileError = ref<string | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const {
  status,
  progress,
  isBusy,
  isAnalyzing,
  errorMessage,
  importedProject,
  uploadArchive,
  uploadArchiveAsync,
  reset,
} = useArchiveImport()

const maxSizeLabel = computed(() => formatFileSize(ARCHIVE_MAX_SIZE_BYTES))

const canSubmit = computed(
  () =>
    !isBusy.value &&
    projectName.value.trim().length > 0 &&
    selectedFile.value !== null &&
    fileError.value === null,
)

const submitLabel = computed(() => {
  if (isAnalyzing.value) {
    return progress.value >= 98 ? t('user.import.finalizing') : `${t('user.import.importing')} ${progress.value}%`
  }
  if (status.value === 'uploading') return t('user.import.uploading')
  return t('user.import.uploadArchive')
})

// Progress-bar caption. While the file is still uploading the byte progress
// isn't known, so show "Uploading…"; once the server is analyzing show a
// determinate percentage, and "Finalizing graph…" as it nears completion —
// matching the GitHub/local import wording.
const progressLabel = computed(() => {
  if (!isAnalyzing.value) return t('user.import.uploading')
  return progress.value >= 98 ? t('user.import.finalizing') : `${t('user.import.analyzing')} ${progress.value}%`
})

function onFileChange(event: Event): void {
  const target = event.target as HTMLInputElement
  const file = target.files && target.files.length > 0 ? target.files[0] : null

  // A new selection always supersedes a previous success/error state.
  reset()

  if (!file) {
    selectedFile.value = null
    fileError.value = null
    return
  }

  const validation = validateArchiveFile(file)
  if (validation) {
    selectedFile.value = null
    fileError.value = validation.message
    // Clear the input so the user can re-pick the same file after the warning.
    target.value = ''
    return
  }

  selectedFile.value = file
  fileError.value = null
}

async function onSubmit(): Promise<void> {
  if (!canSubmit.value || !selectedFile.value) return
  const upload = props.async ? uploadArchiveAsync : uploadArchive
  const project = await upload(projectName.value, selectedFile.value)
  if (project) {
    emit('imported', project)
  }
}

function clearForm(): void {
  projectName.value = ''
  selectedFile.value = null
  fileError.value = null
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
  reset()
}

// Ensure any in-flight WebSocket / poll / watchdog timers are torn down when
// the component is destroyed (e.g. navigation after a successful import).
onBeforeUnmount(() => {
  reset()
})
</script>

<template>
  <section
    class="archive-import"
    :class="{ 'archive-import--embedded': embedded }"
    aria-labelledby="archive-import-heading"
  >
    <header v-if="!embedded" class="archive-import__header">
      <span class="archive-import__icon" aria-hidden="true">
        <svg
          viewBox="0 0 24 24"
          width="20"
          height="20"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M3 7l9-4 9 4v10l-9 4-9-4z" />
          <path d="M3 7l9 4 9-4" />
          <path d="M12 11v10" />
        </svg>
      </span>
      <div class="archive-import__heading-group">
        <h2 id="archive-import-heading">{{ t('user.import.archiveTitle') }}</h2>
        <p class="archive-import__hint">{{ t('user.import.archiveHint', { max: maxSizeLabel }) }}</p>
      </div>
    </header>

    <form class="archive-import__form" @submit.prevent="onSubmit">
      <label class="archive-import__field">
        <span class="archive-import__label">{{ t('user.import.projectName') }}</span>
        <input
          v-model="projectName"
          class="archive-import__text-input"
          type="text"
          name="projectName"
          placeholder="my-java-service"
          :disabled="isBusy"
          aria-required="true"
          autocomplete="off"
          spellcheck="false"
        />
      </label>

      <div class="archive-import__field">
        <span class="archive-import__label">{{ t('user.import.archiveFile') }}</span>
        <input
          ref="fileInputRef"
          class="archive-import__file-input"
          type="file"
          name="archiveFile"
          :accept="ARCHIVE_ACCEPT_ATTRIBUTE"
          :disabled="isBusy"
          aria-describedby="archive-import-file-help"
          @change="onFileChange"
        />
        <p id="archive-import-file-help" class="archive-import__file-meta">
          <span v-if="selectedFile">
            {{ t('user.import.selectedFile') }} <strong>{{ selectedFile.name }}</strong> ({{
              formatFileSize(selectedFile.size)
            }})
          </span>
          <span v-else>{{ t('user.import.noFile') }}</span>
        </p>
        <p v-if="fileError" class="archive-import__error" role="alert">{{ fileError }}</p>
      </div>

      <div
        v-if="isBusy"
        class="archive-import__progress"
        role="progressbar"
        :aria-valuenow="progress"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-label="progressLabel"
      >
        <div class="archive-import__progress-head">
          <span class="archive-import__progress-label">{{ progressLabel }}</span>
          <span class="archive-import__progress-value">{{ progress }}%</span>
        </div>
        <div class="archive-import__progress-track">
          <div class="archive-import__progress-fill" :style="{ width: progress + '%' }"></div>
        </div>
      </div>

      <div class="archive-import__actions">
        <button
          type="submit"
          class="archive-import__btn archive-import__btn--primary"
          :disabled="!canSubmit"
        >
          <span v-if="isBusy" class="archive-import__submit-spinner">
            <Spinner size="sm" aria-hidden="true" />
            <span>{{ submitLabel }}</span>
          </span>
          <span v-else>{{ submitLabel }}</span>
        </button>
        <button
          type="button"
          class="archive-import__btn archive-import__btn--ghost"
          :disabled="isBusy"
          @click="clearForm"
        >
          {{ t('user.import.reset') }}
        </button>
      </div>

      <p
        v-if="status === 'error' && errorMessage"
        class="archive-import__error archive-import__error--server"
        role="alert"
      >
        {{ errorMessage }}
      </p>

      <p
        v-if="status === 'success' && importedProject"
        class="archive-import__success"
        role="status"
      >
        {{ t('user.import.successFor') }} <strong>{{ importedProject.name }}</strong> (status: {{ importedProject.status }}).
      </p>
    </form>
  </section>
</template>

<style scoped>
.archive-import {
  --accent: var(--vg-cyan);
  --accent-soft: rgba(34, 211, 238, 0.16);
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

.archive-import::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  opacity: 0.7;
}

.archive-import::after {
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

.archive-import:hover {
  border-color: var(--vg-border-strong);
  transform: translateY(-3px);
  box-shadow: var(--vg-shadow-lg);
}
.archive-import:hover::after {
  opacity: 1;
}

/* Embedded inside the unified panel: drop the card so only the fields show. */
.archive-import--embedded {
  gap: 1.1rem;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: none;
  box-shadow: none;
}
.archive-import--embedded::before,
.archive-import--embedded::after {
  display: none;
}
.archive-import--embedded:hover {
  transform: none;
  box-shadow: none;
}

.archive-import__header {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
}

.archive-import__icon {
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

.archive-import__heading-group {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.archive-import__header h2 {
  margin: 0;
  font-size: var(--vg-text-lg);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.archive-import__hint {
  margin: 0;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
  line-height: 1.5;
}

.archive-import__form {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.archive-import__field {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.archive-import__label {
  font-size: var(--vg-text-sm);
  font-weight: 500;
  color: var(--vg-text-muted);
}

.archive-import__text-input {
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

.archive-import__text-input::placeholder {
  color: var(--vg-text-dim);
}

.archive-import__text-input:hover:not(:disabled) {
  border-color: var(--accent);
}

.archive-import__text-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
  background: rgba(7, 11, 22, 0.75);
}

.archive-import__text-input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.archive-import__file-input {
  font: inherit;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
  padding: 0.55rem 0.7rem;
  border: 1px dashed var(--vg-border-strong);
  border-radius: var(--vg-radius-sm);
  background: rgba(7, 11, 22, 0.4);
  cursor: pointer;
  transition:
    border-color var(--vg-dur-fast) var(--vg-ease-out),
    background-color var(--vg-dur-fast);
}

.archive-import__file-input:hover:not(:disabled) {
  border-color: var(--accent);
  background: rgba(7, 11, 22, 0.6);
}

.archive-import__file-input::file-selector-button {
  font: inherit;
  font-weight: 600;
  margin-right: 0.75rem;
  padding: 0.35rem 0.85rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius-pill);
  background: var(--accent-soft);
  color: var(--accent);
  cursor: pointer;
  transition: background-color var(--vg-dur-fast) var(--vg-ease-out);
}

.archive-import__file-input::file-selector-button:hover {
  background: rgba(34, 211, 238, 0.28);
}

.archive-import__file-input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.archive-import__file-meta {
  margin: 0;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.archive-import__file-meta strong {
  color: var(--vg-text);
}

.archive-import__error {
  margin: 0;
  font-size: var(--vg-text-sm);
  color: #fca5a5;
}

.archive-import__error--server {
  padding: 0.6rem 0.85rem;
  border: 1px solid rgba(239, 68, 68, 0.45);
  border-radius: var(--vg-radius-sm);
  background: rgba(127, 29, 29, 0.2);
}

.archive-import__success {
  margin: 0;
  padding: 0.6rem 0.85rem;
  border: 1px solid rgba(34, 197, 94, 0.4);
  border-radius: var(--vg-radius-sm);
  background: rgba(20, 83, 45, 0.2);
  color: var(--vg-green-bright);
  font-size: var(--vg-text-sm);
}

.archive-import__progress {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.archive-import__progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: var(--vg-text-sm);
  color: var(--vg-text-muted);
}

.archive-import__progress-value {
  font-family: var(--vg-font-display);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--vg-text);
}

.archive-import__progress-track {
  position: relative;
  height: 8px;
  border-radius: var(--vg-radius-pill);
  background: rgba(7, 11, 22, 0.6);
  border: 1px solid var(--vg-border);
  overflow: hidden;
}

.archive-import__progress-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--vg-grad-brand);
  transition: width var(--vg-dur) var(--vg-ease-out);
}

@media (prefers-reduced-motion: reduce) {
  .archive-import,
  .archive-import__btn,
  .archive-import__text-input,
  .archive-import__progress-fill {
    transition: none;
  }
}

.archive-import__actions {
  display: flex;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.archive-import__btn {
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

.archive-import__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.archive-import__btn--primary {
  background: linear-gradient(135deg, #22d3ee, #0891b2);
  border-color: transparent;
  color: #04212b;
  box-shadow: 0 8px 24px -10px rgba(34, 211, 238, 0.7);
}

.archive-import__btn--primary:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow:
    0 0 0 1px rgba(34, 211, 238, 0.5),
    0 18px 40px -14px rgba(34, 211, 238, 0.8);
}
.archive-import__btn--primary:not(:disabled):active {
  transform: translateY(0);
}

.archive-import__btn--ghost:not(:disabled):hover {
  border-color: var(--accent);
  background: rgba(148, 163, 184, 0.12);
}

.archive-import__submit-spinner {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}
</style>
