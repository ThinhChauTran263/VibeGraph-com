<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { browseApi, type DirectoryListing } from '@/lib/api'
import Spinner from '@/components/ui/Spinner.vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  select: [path: string]
  close: []
}>()

const listing = ref<DirectoryListing | null>(null)
const isLoading = ref(false)
const errorMessage = ref<string | null>(null)
// Editable address bar: reflects the current folder and accepts a pasted/typed path (Enter to go).
const pathInput = ref('')

// Remembered "home" folder (where the user's projects live) so the browser opens there each
// time instead of starting at the drive list. Persisted per-machine in localStorage.
const DEFAULT_PATH_KEY = 'vibegraph:browse-default-path'
const defaultPath = ref<string>(readDefaultPath())

function readDefaultPath(): string {
  try {
    return localStorage.getItem(DEFAULT_PATH_KEY) ?? ''
  } catch {
    return ''
  }
}

// parent === null means "top" (Up disabled); '' means "go to the drive list".
const canGoUp = computed(() => listing.value != null && listing.value.parent !== null)
const canSelect = computed(() => !!listing.value?.path && !isLoading.value)
const isAtDefault = computed(() => !!listing.value?.path && listing.value.path === defaultPath.value)

async function load(path?: string): Promise<boolean> {
  isLoading.value = true
  errorMessage.value = null
  try {
    listing.value = await browseApi.browse(path)
    return true
  } catch (err) {
    errorMessage.value = err instanceof Error && err.message ? err.message : 'Failed to list directory.'
    return false
  } finally {
    isLoading.value = false
  }
}

/** Open at the saved default folder; fall back to the drive list if it is gone/invalid. */
async function openInitial(): Promise<void> {
  if (defaultPath.value) {
    const ok = await load(defaultPath.value)
    if (ok) return
  }
  await load()
}

// Load the starting view each time the modal opens.
watch(
  () => props.open,
  (open) => {
    if (open) void openInitial()
  },
  { immediate: true },
)

function openDir(path: string): void {
  void load(path)
}

// Keep the address bar in sync with the folder actually shown ('' = This PC).
watch(
  () => listing.value?.path,
  (p) => {
    pathInput.value = p ?? ''
  },
)

function goUp(): void {
  const parent = listing.value?.parent
  if (parent !== null && parent !== undefined) void load(parent)
}

/** Navigate to the path typed/pasted in the address bar (blank → drive list / base). */
function goToInput(): void {
  void load(pathInput.value.trim() || undefined)
}

function selectCurrent(): void {
  if (listing.value?.path) emit('select', listing.value.path)
}

/** Remember the current folder as the default opening location. */
function setDefault(): void {
  const p = listing.value?.path
  if (!p) return
  defaultPath.value = p
  try {
    localStorage.setItem(DEFAULT_PATH_KEY, p)
  } catch {
    // localStorage unavailable (private mode) — keep the in-session default only.
  }
}

/** Clear the saved default and jump back to the drive list (This PC). */
function resetDefault(): void {
  defaultPath.value = ''
  try {
    localStorage.removeItem(DEFAULT_PATH_KEY)
  } catch {
    // ignore
  }
  void load()
}
</script>

<template>
  <div
    v-if="open"
    class="dir-modal"
    role="dialog"
    aria-modal="true"
    aria-label="Choose a project folder"
    data-test="dir-browser"
    @keydown.esc="emit('close')"
  >
    <div class="dir-modal__scrim" @click="emit('close')"></div>
    <div class="dir-modal__panel">
      <header class="dir-modal__header">
        <h2 class="dir-modal__title">Choose a project folder</h2>
        <button class="dir-modal__icon-btn" type="button" aria-label="Close" @click="emit('close')">✕</button>
      </header>

      <div class="dir-modal__path-bar">
        <button
          class="dir-modal__up"
          type="button"
          :disabled="!canGoUp || isLoading"
          @click="goUp"
        >
          ↑ Up
        </button>
        <input
          v-model="pathInput"
          class="dir-modal__path-input"
          type="text"
          spellcheck="false"
          autocomplete="off"
          placeholder="This PC — paste a path and press Enter"
          :aria-label="'Current folder path'"
          @keydown.enter.prevent="goToInput"
        />
        <button
          class="dir-modal__go"
          type="button"
          data-test="dir-go"
          :disabled="isLoading"
          @click="goToInput"
        >
          Go
        </button>
      </div>

      <p v-if="errorMessage" class="dir-modal__error" role="alert">{{ errorMessage }}</p>

      <div class="dir-modal__list" data-test="dir-list">
        <div v-if="isLoading" class="dir-modal__loading"><Spinner size="sm" /><span>Loading…</span></div>
        <p v-else-if="listing && listing.entries.length === 0" class="dir-modal__empty">
          No sub-folders here.
        </p>
        <ul v-else class="dir-modal__entries">
          <li v-for="entry in listing?.entries ?? []" :key="entry.path">
            <button class="dir-modal__entry" type="button" @click="openDir(entry.path)">
              <span class="dir-modal__entry-icon" aria-hidden="true">📁</span>
              <span class="dir-modal__entry-name">{{ entry.name }}</span>
              <span v-if="entry.containsJava" class="dir-modal__badge">Java</span>
            </button>
          </li>
        </ul>
      </div>

      <footer class="dir-modal__footer">
        <div class="dir-modal__footer-left">
          <button
            class="dir-modal__btn dir-modal__btn--ghost"
            type="button"
            data-test="dir-set-default"
            :disabled="!canSelect || isAtDefault"
            :title="defaultPath ? `Current default: ${defaultPath}` : 'No default set'"
            @click="setDefault"
          >
            {{ isAtDefault ? '★ Default folder' : 'Set as default' }}
          </button>
          <button
            class="dir-modal__btn dir-modal__btn--ghost"
            type="button"
            data-test="dir-reset-default"
            :disabled="!defaultPath || isLoading"
            @click="resetDefault"
          >
            Reset
          </button>
        </div>
        <div class="dir-modal__footer-right">
          <button class="dir-modal__btn dir-modal__btn--ghost" type="button" @click="emit('close')">Cancel</button>
          <button
            class="dir-modal__btn dir-modal__btn--primary"
            type="button"
            data-test="dir-select"
            :disabled="!canSelect"
            @click="selectCurrent"
          >
            Use this folder
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.dir-modal {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dir-modal__scrim {
  position: absolute;
  inset: 0;
  background: rgba(2, 6, 23, 0.6);
}

.dir-modal__panel {
  position: relative;
  width: min(40rem, 92vw);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.25rem;
  border: 1px solid #2a2a2a;
  border-radius: 0.875rem;
  background: #111;
  color: #e5e7eb;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.5);
}

.dir-modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dir-modal__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
}

.dir-modal__icon-btn {
  border: none;
  background: transparent;
  color: #9ca3af;
  font-size: 1rem;
  cursor: pointer;
}

.dir-modal__icon-btn:hover {
  color: #f3f4f6;
}

.dir-modal__path-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.dir-modal__up {
  flex-shrink: 0;
  border: 1px solid #2a2a2a;
  border-radius: 0.5rem;
  background: rgba(30, 41, 59, 0.86);
  color: #e5e7eb;
  padding: 0.3rem 0.6rem;
  cursor: pointer;
}

.dir-modal__up:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.dir-modal__path-input {
  flex: 1;
  min-width: 0;
  font: inherit;
  font-size: 0.8125rem;
  color: #e5e7eb;
  background: #1f1f1f;
  border: 1px solid #2a2a2a;
  border-radius: 0.5rem;
  padding: 0.35rem 0.55rem;
}

.dir-modal__path-input:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 1px;
  border-color: #2563eb;
}

.dir-modal__go {
  flex-shrink: 0;
  border: 1px solid #2a2a2a;
  border-radius: 0.5rem;
  background: rgba(30, 41, 59, 0.86);
  color: #e5e7eb;
  padding: 0.3rem 0.7rem;
  cursor: pointer;
}

.dir-modal__go:hover:not(:disabled) {
  border-color: rgba(147, 197, 253, 0.7);
}

.dir-modal__go:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.dir-modal__list {
  flex: 1;
  overflow: auto;
  border: 1px solid #2a2a2a;
  border-radius: 0.5rem;
  min-height: 8rem;
}

.dir-modal__loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  color: #9ca3af;
}

.dir-modal__empty {
  margin: 0;
  padding: 1rem;
  color: #9ca3af;
  font-size: 0.875rem;
}

.dir-modal__entries {
  list-style: none;
  margin: 0;
  padding: 0.25rem;
}

.dir-modal__entry {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  color: inherit;
  padding: 0.45rem 0.55rem;
  border-radius: 0.5rem;
  cursor: pointer;
}

.dir-modal__entry:hover {
  background: rgba(37, 99, 235, 0.18);
}

.dir-modal__entry-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dir-modal__badge {
  font-size: 0.7rem;
  font-weight: 600;
  color: #bfdbfe;
  background: rgba(37, 99, 235, 0.35);
  border-radius: 999px;
  padding: 0.05rem 0.45rem;
}

.dir-modal__error {
  margin: 0;
  font-size: 0.85rem;
  color: #f87171;
}

.dir-modal__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.dir-modal__footer-left,
.dir-modal__footer-right {
  display: flex;
  gap: 0.5rem;
}

.dir-modal__btn {
  font: inherit;
  padding: 0.45rem 0.9rem;
  border-radius: 0.5rem;
  border: 1px solid #2a2a2a;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.dir-modal__btn--primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.dir-modal__btn--primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.dir-modal__btn--ghost:hover {
  border-color: #4b5563;
}
</style>
