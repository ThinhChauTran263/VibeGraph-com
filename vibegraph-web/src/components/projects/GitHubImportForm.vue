<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Project } from '@/lib/api'
import { useGitHubImport } from '@/composables/useGitHubImport'
import Spinner from '@/components/ui/Spinner.vue'

const emit = defineEmits<{
  imported: [project: Project]
}>()

const repoUrl = ref('')
const { status, errorMessage, importedProject, isImporting, importGithub, reset } = useGitHubImport()

const canSubmit = computed(() => repoUrl.value.trim().length > 0 && !isImporting.value)

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
  <section class="github-import" aria-labelledby="github-import-heading">
    <header class="github-import__header">
      <h2 id="github-import-heading">Add project from GitHub</h2>
      <p class="github-import__hint">
        Import a public repository using its HTTPS URL. Example:
        https://github.com/spring-projects/spring-petclinic
      </p>
    </header>

    <form class="github-import__form" @submit.prevent="onSubmit">
      <label class="github-import__field">
        <span class="github-import__label">GitHub repository URL</span>
        <input
          v-model="repoUrl"
          class="github-import__text-input"
          type="url"
          placeholder="https://github.com/owner/repo"
          :disabled="isImporting"
          aria-required="true"
          autocomplete="off"
          spellcheck="false"
          @input="reset"
        />
      </label>

      <div class="github-import__actions">
        <button type="submit" class="github-import__btn github-import__btn--primary" :disabled="!canSubmit">
          <span v-if="isImporting" class="github-import__submit-spinner">
            <Spinner size="sm" aria-hidden="true" />
            <span>Importing...</span>
          </span>
          <span v-else>Import GitHub repo</span>
        </button>
        <button type="button" class="github-import__btn github-import__btn--ghost" :disabled="isImporting" @click="clearForm">
          Reset
        </button>
      </div>

      <p v-if="status === 'error' && errorMessage" class="github-import__error" role="alert">
        {{ errorMessage }}
      </p>

      <p v-if="status === 'success' && importedProject" class="github-import__success" role="status">
        Import completed for <strong>{{ importedProject.name }}</strong> (status:
        {{ importedProject.status }}).
      </p>
    </form>
  </section>
</template>

<style scoped>
.github-import {
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

.github-import__header,
.github-import__form,
.github-import__field {
  display: flex;
  flex-direction: column;
}

.github-import__header {
  gap: 0.25rem;
}

.github-import__form {
  gap: 1rem;
}

.github-import__field {
  gap: 0.4rem;
}

.github-import__header h2 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
}

.github-import__hint,
.github-import__error,
.github-import__success {
  margin: 0;
  font-size: 0.875rem;
}

.github-import__hint {
  color: #9ca3af;
}

.github-import__label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #d1d5db;
}

.github-import__text-input {
  font: inherit;
  color: inherit;
  padding: 0.5rem 0.75rem;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  background: #1f1f1f;
}

.github-import__text-input:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 2px;
  border-color: #2563eb;
}

.github-import__text-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.github-import__actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.github-import__btn {
  font: inherit;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  border: 1px solid #2a2a2a;
  background: transparent;
  color: inherit;
  cursor: pointer;
  transition: background-color 150ms ease, border-color 150ms ease, color 150ms ease;
}

.github-import__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.github-import__btn--primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #ffffff;
}

.github-import__btn--primary:not(:disabled):hover {
  background: #1d4ed8;
  border-color: #1d4ed8;
}

.github-import__btn--ghost:not(:disabled):hover {
  border-color: #4b5563;
  color: #f3f4f6;
}

.github-import__btn:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 2px;
}

.github-import__submit-spinner {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.github-import__error {
  padding: 0.5rem 0.75rem;
  border: 1px solid #7f1d1d;
  border-radius: 6px;
  background: rgba(127, 29, 29, 0.2);
  color: #f87171;
}

.github-import__success {
  padding: 0.5rem 0.75rem;
  border: 1px solid #14532d;
  border-radius: 6px;
  background: rgba(20, 83, 45, 0.2);
  color: #4ade80;
}
</style>
