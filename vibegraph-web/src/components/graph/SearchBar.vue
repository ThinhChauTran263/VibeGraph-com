<script setup lang="ts">
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import type { GraphNode } from '@/types/graph'
import { SEARCH_SUGGESTIONS_LIMIT } from '@/lib/runtimeConfig'

const props = defineProps<{
  nodes: GraphNode[]
  selectedNodeId?: string | null
}>()

const emit = defineEmits<{
  select: [node: GraphNode]
  clear: []
}>()

const inputId = useId()
const resultsId = useId()
const inputEl = ref<HTMLInputElement | null>(null)
const query = ref('')
const isOpen = ref(false)

// F-L3: the O(all nodes) filter below runs against the DEBOUNCED query, not on
// every keystroke — burst typing collapses into one scan after the input rests.
// Clearing is applied immediately so the dropdown never lags behind an empty box.
const SEARCH_DEBOUNCE_MS = 150
const debouncedQuery = ref('')
let debounceTimer: ReturnType<typeof setTimeout> | null = null

watch(query, (value) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (!value.trim()) {
    debouncedQuery.value = value
    return
  }
  debounceTimer = setTimeout(() => {
    debouncedQuery.value = value
  }, SEARCH_DEBOUNCE_MS)
})

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})

const results = computed(() => {
  const term = normalizeSearchText(debouncedQuery.value)
  if (!term) return []

  return props.nodes
    .map((node, index) => ({ node, index, rank: searchRank(node, term) }))
    .filter((candidate) => candidate.rank >= 0)
    .sort((a, b) => a.rank - b.rank || a.index - b.index)
    .slice(0, SEARCH_SUGGESTIONS_LIMIT)
    .map((candidate) => candidate.node)
})

function normalizeSearchText(value: unknown): string {
  return typeof value === 'string' ? value.normalize('NFKC').trim().toLocaleLowerCase() : ''
}

/** Prefer direct name matches before broader full-name/path substring matches. */
function searchRank(node: GraphNode, term: string): number {
  const name = normalizeSearchText(node.name)
  const fullName = normalizeSearchText(node.fullName)
  const filePath = normalizeSearchText(node.filePath)
  const fileName = filePath.split(/[\\/]/).pop() ?? ''
  const terms = term.endsWith('.java') ? [term, term.slice(0, -'.java'.length)] : [term]
  let bestRank = Number.POSITIVE_INFINITY

  for (const candidate of terms) {
    if (!candidate) continue
    if (name === candidate || fileName === candidate) bestRank = Math.min(bestRank, 0)
    else if (name.startsWith(candidate) || fileName.startsWith(candidate))
      bestRank = Math.min(bestRank, 1)
    else if (name.includes(candidate)) bestRank = Math.min(bestRank, 2)
    else if (fullName.includes(candidate)) bestRank = Math.min(bestRank, 3)
    else if (filePath.includes(candidate)) bestRank = Math.min(bestRank, 4)
  }

  return Number.isFinite(bestRank) ? bestRank : -1
}

const hasQuery = computed(() => query.value.trim().length > 0)
const hasResults = computed(() => results.value.length > 0)
const showResults = computed(() => isOpen.value && hasQuery.value)

function onInput(): void {
  isOpen.value = true
}

function focusInput(event: PointerEvent): void {
  // Mobile browsers otherwise scroll the graph stage to reveal the focused input
  // on the first tap, making the search bar appear to jump before typing starts.
  event.preventDefault()
  inputEl.value?.focus({ preventScroll: true })
}

function selectNode(node: GraphNode): void {
  query.value = node.name
  isOpen.value = false
  emit('select', node)
}

function clearSearch(): void {
  query.value = ''
  isOpen.value = false
  emit('clear')
}
</script>

<template>
  <div
    class="search-bar"
    role="search"
    @pointerdown.stop
    @mousedown.stop
    @click.stop
  >
    <label class="search-bar__label" :for="inputId">Search graph nodes</label>
    <div class="search-bar__control">
      <input
        :id="inputId"
        ref="inputEl"
        v-model="query"
        class="search-bar__input"
        type="search"
        placeholder="Search class, method, route..."
        autocomplete="off"
        spellcheck="false"
        :aria-controls="resultsId"
        @input="onInput"
        @pointerdown.stop="focusInput"
        @focus="onInput"
      />
      <button
        v-if="hasQuery"
        class="search-bar__clear"
        type="button"
        aria-label="Clear search"
        @click="clearSearch"
      >
        Clear
      </button>
    </div>

    <div v-if="showResults" :id="resultsId" class="search-bar__results">
      <button
        v-for="node in results"
        :key="node.id"
        class="search-bar__result"
        :class="{ 'search-bar__result--selected': node.id === selectedNodeId }"
        type="button"
        @click="selectNode(node)"
      >
        <span class="search-bar__result-name">{{ node.name }}</span>
        <span class="search-bar__result-meta">{{ node.type }} · {{ node.fullName }}</span>
      </button>

      <p v-if="!hasResults" class="search-bar__empty" role="status">No matching nodes.</p>
    </div>
  </div>
</template>

<style scoped>
.search-bar {
  position: relative;
  z-index: 20;
  isolation: isolate;
  width: min(36rem, 100%);
  color: #e5e7eb;
}

.search-bar__label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.search-bar__control {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  box-sizing: border-box;
  min-height: 3rem;
  padding: 0.5rem;
  border: 1px solid rgba(96, 165, 250, 0.35);
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.92);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

.search-bar__input {
  flex: 1;
  min-width: 0;
  min-height: 2rem;
  border: 0;
  outline: 0;
  appearance: none;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 1rem;
  line-height: 1.5rem;
  touch-action: manipulation;
}

.search-bar__input::placeholder {
  color: #9ca3af;
}

.search-bar__clear {
  border: 0;
  border-radius: 999px;
  padding: 0.25rem 0.625rem;
  background: rgba(55, 65, 81, 0.8);
  color: #d1d5db;
  cursor: pointer;
}

.search-bar__clear:hover,
.search-bar__clear:focus-visible {
  background: #374151;
  color: #ffffff;
}

.search-bar__results {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 0;
  left: 0;
  max-height: 20rem;
  overflow: auto;
  border: 1px solid #1f2937;
  border-radius: 0.875rem;
  background: rgba(17, 24, 39, 0.96);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.4);
}

.search-bar__result {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 0.25rem;
  border: 0;
  border-bottom: 1px solid #1f2937;
  padding: 0.75rem 0.875rem;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.search-bar__result:last-of-type {
  border-bottom: 0;
}

.search-bar__result:hover,
.search-bar__result:focus-visible,
.search-bar__result--selected {
  background: rgba(37, 99, 235, 0.22);
}

.search-bar__result-name {
  font-weight: 600;
}

.search-bar__result-meta {
  overflow: hidden;
  color: #9ca3af;
  font-size: 0.8125rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-bar__empty {
  margin: 0;
  padding: 0.75rem 0.875rem;
  color: #9ca3af;
  font-size: 0.875rem;
}

@media (max-width: 93.75rem) {
  .search-bar {
    width: min(36rem, calc(100% - 2rem));
  }
}

@media (max-width: 56rem) {
  .search-bar {
    width: calc(100% - 2rem);
  }
}
</style>
