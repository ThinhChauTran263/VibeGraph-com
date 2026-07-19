<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppIcon from '@/components/ui/AppIcon.vue'
import Spinner from '@/components/ui/Spinner.vue'
import { importApi, type CliRepositorySetup, type Project } from '@/lib/api'
import { useAccountStore } from '@/stores/account'

const { t } = useI18n({ useScope: 'global' })
const account = useAccountStore()
const emit = defineEmits<{
  imported: [project: Project]
}>()

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const name = ref('')
const setup = ref<CliRepositorySetup | null>(null)
const loading = ref(false)
const error = ref('')
const copied = ref<string | null>(null)

const canSubmit = computed(() => !loading.value && !setup.value)
const commandBlock = computed(() => setup.value?.commands.join('\n') ?? '')

async function createRepository(): Promise<void> {
  if (!canSubmit.value) return
  loading.value = true
  error.value = ''
  copied.value = null
  try {
    setup.value = await importApi.createCliRepository(name.value)
    await Promise.allSettled([
      account.fetchProjects({ force: true }),
      account.fetchApiKeys({ force: true }),
    ])
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('user.import.cli.error')
  } finally {
    loading.value = false
  }
}

async function copyText(value: string, label: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(value)
    copied.value = label
  } catch {
    copied.value = null
    error.value = t('user.import.cli.copyFailed')
  }
}

function reset(): void {
  name.value = ''
  setup.value = null
  error.value = ''
  copied.value = null
}
</script>

<template>
  <section class="cli-import" :class="{ 'cli-import--embedded': embedded }" aria-labelledby="cli-import-heading">
    <header v-if="!embedded" class="cli-import__header">
      <span class="cli-import__icon" aria-hidden="true"><AppIcon name="repository" :size="20" /></span>
      <div>
        <h2 id="cli-import-heading">{{ t('user.import.cli.title') }}</h2>
        <p>{{ t('user.import.cli.hint') }}</p>
      </div>
    </header>

    <form v-if="!setup" class="cli-import__form" @submit.prevent="createRepository">
      <label class="cli-import__field">
        <span>{{ t('user.import.projectName') }}</span>
        <input
          v-model="name"
          type="text"
          name="repositoryName"
          :placeholder="t('user.import.cli.namePlaceholder')"
          :disabled="loading"
          autocomplete="off"
        />
      </label>

      <div class="cli-import__actions">
        <button type="submit" class="cli-import__btn cli-import__btn--primary" :disabled="!canSubmit">
          <Spinner v-if="loading" size="sm" aria-hidden="true" />
          <AppIcon v-else name="plus" :size="17" />
          <span>{{ loading ? t('user.import.cli.creating') : t('user.import.cli.create') }}</span>
        </button>
        <button type="button" class="cli-import__btn" :disabled="loading" @click="reset">
          {{ t('user.import.reset') }}
        </button>
      </div>
    </form>

    <section v-else class="cli-import__result" aria-live="polite">
      <div class="cli-import__success">
        <span class="cli-import__success-icon" aria-hidden="true"><AppIcon name="key" :size="18" /></span>
        <div>
          <h3>{{ t('user.import.cli.readyTitle', { name: setup.project.name }) }}</h3>
          <p>{{ t('user.import.cli.secretNotice') }}</p>
        </div>
      </div>

      <div class="cli-import__secret">
        <span>{{ t('user.import.cli.apiKey') }}</span>
        <code>{{ setup.apiKey.secretKey }}</code>
        <button type="button" class="cli-import__icon-btn" @click="copyText(setup.apiKey.secretKey, 'secret')">
          {{ copied === 'secret' ? t('user.import.cli.copied') : t('user.import.cli.copy') }}
        </button>
      </div>

      <div class="cli-import__commands">
        <div class="cli-import__commands-head">
          <span>{{ t('user.import.cli.commands') }}</span>
          <button type="button" class="cli-import__icon-btn" @click="copyText(commandBlock, 'commands')">
            {{ copied === 'commands' ? t('user.import.cli.copied') : t('user.import.cli.copyAll') }}
          </button>
        </div>
        <pre><code>{{ commandBlock }}</code></pre>
      </div>

      <div class="cli-import__actions">
        <button type="button" class="cli-import__btn cli-import__btn--primary" @click="emit('imported', setup.project)">
          <AppIcon name="graph" :size="17" />
          <span>{{ t('user.import.cli.openRepository') }}</span>
        </button>
        <button type="button" class="cli-import__btn" @click="reset">
          {{ t('user.import.cli.createAnother') }}
        </button>
      </div>
    </section>

    <p v-if="error" class="cli-import__error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.cli-import {
  --accent: var(--vg-blue-bright);
  --accent-soft: rgba(96, 165, 250, 0.16);
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
  padding: var(--vg-space-4);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-lg);
  background: var(--vg-grad-surface);
  color: var(--vg-text);
  box-shadow: var(--vg-shadow);
}

.cli-import--embedded {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: none;
  box-shadow: none;
}

.cli-import__header,
.cli-import__success {
  display: flex;
  align-items: flex-start;
  gap: var(--vg-space-3);
}

.cli-import__icon,
.cli-import__success-icon {
  display: inline-grid;
  flex: 0 0 auto;
  width: 2.35rem;
  height: 2.35rem;
  place-items: center;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius-sm);
  background: var(--accent-soft);
  color: var(--accent);
}

h2,
h3,
p {
  margin: 0;
}

h2,
h3 {
  font-family: var(--vg-font-display);
  color: var(--vg-text);
}

h2 {
  font-size: var(--vg-text-lg);
}

h3 {
  font-size: var(--vg-text-base);
}

p {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  line-height: 1.5;
}

.cli-import__form,
.cli-import__field,
.cli-import__result {
  display: flex;
  flex-direction: column;
}

.cli-import__form,
.cli-import__result {
  gap: var(--vg-space-3);
}

.cli-import__field {
  gap: var(--vg-space-2);
}

.cli-import__field span,
.cli-import__secret span,
.cli-import__commands-head span {
  color: var(--vg-text-muted);
  font-size: var(--vg-text-sm);
  font-weight: 600;
}

input {
  min-height: 40px;
  width: 100%;
  min-width: 0;
  padding: 0.55rem 0.75rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius-sm);
  background: rgba(7, 11, 22, 0.55);
  color: var(--vg-text);
  font: inherit;
}

input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.cli-import__actions,
.cli-import__commands-head {
  display: flex;
  align-items: center;
  gap: var(--vg-space-2);
  flex-wrap: wrap;
}

.cli-import__btn,
.cli-import__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 38px;
  border: 1px solid var(--vg-border-strong);
  border-radius: 6px;
  background: rgba(148, 163, 184, 0.08);
  color: var(--vg-text);
  font: inherit;
  font-size: var(--vg-text-sm);
  font-weight: 700;
  cursor: pointer;
}

.cli-import__btn {
  padding: 0.5rem 0.75rem;
}

.cli-import__icon-btn {
  min-height: 32px;
  padding: 0.35rem 0.65rem;
}

.cli-import__btn--primary {
  border-color: var(--vg-blue);
  background: var(--vg-blue);
  color: #fff;
}

.cli-import__btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.cli-import__secret,
.cli-import__commands {
  display: grid;
  gap: var(--vg-space-2);
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: rgba(7, 11, 22, 0.42);
}

.cli-import__secret {
  grid-template-columns: minmax(6rem, max-content) minmax(0, 1fr) auto;
  align-items: center;
}

code {
  overflow: hidden;
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

pre {
  margin: 0;
  overflow-x: auto;
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: rgba(3, 7, 18, 0.75);
}

pre code {
  display: block;
  line-height: 1.6;
  white-space: pre;
}

.cli-import__error {
  padding: var(--vg-space-3);
  border: 1px solid rgba(239, 68, 68, 0.45);
  border-radius: var(--vg-radius-sm);
  background: rgba(127, 29, 29, 0.2);
  color: #fca5a5;
}

@media (max-width: 42rem) {
  .cli-import__secret {
    grid-template-columns: 1fr;
  }

  .cli-import__icon-btn {
    justify-self: start;
  }
}
</style>
