<script setup lang="ts">
import ThemedSelect from '@/components/ui/ThemedSelect.vue'

type AuthorizationMode = 'KEY' | 'EXISTING' | 'NEW'
type SelectOption = { value: string; label: string }

defineProps<{
  apiKeyOptions: SelectOption[]
  projectOptions: SelectOption[]
  apiKeysAvailable: boolean
  projectsAvailable: boolean
  canApprove: boolean
  approving: boolean
  error: string
}>()

const emit = defineEmits<{ submit: [] }>()
const mode = defineModel<AuthorizationMode>('mode', { required: true })
const selectedApiKeyId = defineModel<string>('selectedApiKeyId', { required: true })
const selectedProjectId = defineModel<string>('selectedProjectId', { required: true })
const projectName = defineModel<string>('projectName', { required: true })
</script>

<template>
  <form class="cli-auth__form" @submit.prevent="emit('submit')">
    <div class="cli-auth__modes" role="group" aria-label="Authorization mode">
      <button
        type="button"
        :aria-pressed="mode === 'KEY'"
        :class="{ active: mode === 'KEY' }"
        :disabled="!apiKeysAvailable"
        @click="mode = 'KEY'"
      >
        Use existing key
      </button>
      <button
        type="button"
        :aria-pressed="mode === 'EXISTING'"
        :class="{ active: mode === 'EXISTING' }"
        @click="mode = 'EXISTING'"
      >
        Create key for project
      </button>
      <button
        type="button"
        :aria-pressed="mode === 'NEW'"
        :class="{ active: mode === 'NEW' }"
        @click="mode = 'NEW'"
      >
        New project
      </button>
    </div>

    <div v-if="mode === 'KEY'" class="cli-auth__field">
      <label for="cli-api-key">API key and project</label>
      <ThemedSelect
        v-model="selectedApiKeyId"
        class="cli-auth__select cli-auth__select--key"
        input-id="cli-api-key"
        name="apiKeyId"
        :options="apiKeyOptions"
        aria-label="API key and project"
        :disabled="!apiKeysAvailable"
      />
      <small v-if="!apiKeysAvailable" class="cli-auth__hint">
        Create or replace a project key in your VibeGraph account, then refresh this page.
      </small>
    </div>
    <div v-else-if="mode === 'EXISTING'" class="cli-auth__field">
      <label for="cli-project">Project</label>
      <ThemedSelect
        v-model="selectedProjectId"
        class="cli-auth__select"
        input-id="cli-project"
        name="projectId"
        :options="projectOptions"
        aria-label="Project"
        :disabled="!projectsAvailable"
      />
    </div>
    <div v-else class="cli-auth__field">
      <label for="cli-project-name">Project name</label>
      <input
        id="cli-project-name"
        v-model="projectName"
        maxlength="120"
        autocomplete="off"
        placeholder="My repository"
      />
    </div>

    <div v-if="error" class="cli-auth__error" role="alert">{{ error }}</div>
    <button class="cli-auth__approve" type="submit" :disabled="!canApprove">
      {{ approving ? 'Connecting...' : 'Authorize CLI' }}
    </button>
    <p class="cli-auth__fineprint">VibeGraph never displays the API key in this browser window.</p>
  </form>
</template>

<style scoped src="./cli-authorization-form.css"></style>
