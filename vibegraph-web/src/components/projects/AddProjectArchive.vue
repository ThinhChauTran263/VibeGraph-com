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
import {
  ARCHIVE_ACCEPT_ATTRIBUTE,
  ARCHIVE_MAX_SIZE_BYTES,
  formatFileSize,
  validateArchiveFile,
} from '@/lib/archiveUpload'
import type { Project } from '@/lib/api'
import { useArchiveImport } from '@/composables/useArchiveImport'
import Spinner from '@/components/ui/Spinner.vue'

const props = withDefaults(
  defineProps<{
    /**
     * When true, submit uses the async import endpoint (`?async=true`) and
     * tracks progress over WebSocket. Default false keeps the synchronous
     * baseline behavior unchanged.
     */
    async?: boolean
  }>(),
  { async: false },
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
  if (isAnalyzing.value) return `Analyzing... ${progress.value}%`
  if (status.value === 'uploading') return 'Uploading...'
  return 'Upload archive'
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
  <section class="archive-import" aria-labelledby="archive-import-heading">
    <header class="archive-import__header">
      <h2 id="archive-import-heading">Add project from archive</h2>
      <p class="archive-import__hint">
        Upload a Java project archive. Supported formats: .zip, .tar, .tar.gz, .tgz. Max
        {{ maxSizeLabel }}.
      </p>
    </header>

    <form class="archive-import__form" @submit.prevent="onSubmit">
      <label class="archive-import__field">
        <span class="archive-import__label">Project name</span>
        <input
          v-model="projectName"
          class="archive-import__text-input"
          type="text"
          placeholder="my-java-service"
          :disabled="isBusy"
          aria-required="true"
          autocomplete="off"
          spellcheck="false"
        />
      </label>

      <div class="archive-import__field">
        <span class="archive-import__label">Archive file</span>
        <input
          ref="fileInputRef"
          class="archive-import__file-input"
          type="file"
          :accept="ARCHIVE_ACCEPT_ATTRIBUTE"
          :disabled="isBusy"
          aria-describedby="archive-import-file-help"
          @change="onFileChange"
        />
        <p id="archive-import-file-help" class="archive-import__file-meta">
          <span v-if="selectedFile">
            Selected: <strong>{{ selectedFile.name }}</strong> ({{ formatFileSize(selectedFile.size) }})
          </span>
          <span v-else>No file selected.</span>
        </p>
        <p v-if="fileError" class="archive-import__error" role="alert">{{ fileError }}</p>
      </div>

      <div
        v-if="isAnalyzing"
        class="archive-import__progress"
        role="status"
        aria-live="polite"
      >
        <div class="archive-import__progress-track">
          <div
            class="archive-import__progress-bar"
            role="progressbar"
            :aria-valuenow="progress"
            aria-valuemin="0"
            aria-valuemax="100"
            :style="{ width: progress + '%' }"
          />
        </div>
        <span class="archive-import__progress-label">Analyzing... {{ progress }}%</span>
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
          Reset
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
        Imported <strong>{{ importedProject.name }}</strong> (status:
        {{ importedProject.status }}).
      </p>
    </form>
  </section>
</template>

<style scoped>
.archive-import {
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

.archive-import__header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.archive-import__header h2 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
}

.archive-import__hint {
  margin: 0;
  font-size: 0.875rem;
  color: #9ca3af;
}

.archive-import__form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.archive-import__field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.archive-import__label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #d1d5db;
}

.archive-import__text-input {
  font: inherit;
  color: inherit;
  padding: 0.5rem 0.75rem;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  background: #1f1f1f;
}

.archive-import__text-input:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 2px;
  border-color: #2563eb;
}

.archive-import__text-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.archive-import__file-input {
  font: inherit;
  color: inherit;
  padding: 0.5rem 0.5rem 0.5rem 0;
}

.archive-import__file-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.archive-import__file-meta {
  margin: 0;
  font-size: 0.8125rem;
  color: #9ca3af;
}

.archive-import__error {
  margin: 0;
  font-size: 0.875rem;
  color: #f87171;
}

.archive-import__error--server {
  padding: 0.5rem 0.75rem;
  border: 1px solid #7f1d1d;
  border-radius: 6px;
  background: rgba(127, 29, 29, 0.2);
}

.archive-import__success {
  margin: 0;
  padding: 0.5rem 0.75rem;
  border: 1px solid #14532d;
  border-radius: 6px;
  background: rgba(20, 83, 45, 0.2);
  color: #4ade80;
  font-size: 0.875rem;
}

.archive-import__progress {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.archive-import__progress-track {
  width: 100%;
  height: 6px;
  border-radius: 999px;
  background: #1f2937;
  overflow: hidden;
}

.archive-import__progress-bar {
  height: 100%;
  background: #2563eb;
  border-radius: 999px;
  transition: width 200ms ease;
}

.archive-import__progress-label {
  font-size: 0.8125rem;
  color: #9ca3af;
}

.archive-import__actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.archive-import__btn {
  font: inherit;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  border: 1px solid #2a2a2a;
  background: transparent;
  color: inherit;
  cursor: pointer;
  transition: background-color 150ms ease, border-color 150ms ease, color 150ms ease;
}

.archive-import__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.archive-import__btn--primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #ffffff;
}

.archive-import__btn--primary:not(:disabled):hover {
  background: #1d4ed8;
  border-color: #1d4ed8;
}

.archive-import__btn--ghost:not(:disabled):hover {
  border-color: #4b5563;
  color: #f3f4f6;
}

.archive-import__btn:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 2px;
}

.archive-import__submit-spinner {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}
</style>
