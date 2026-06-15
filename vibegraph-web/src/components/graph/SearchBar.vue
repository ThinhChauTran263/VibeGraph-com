<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import type { GraphNode } from '@/types/graph'

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
const query = ref('')
const isOpen = ref(false)

const results = computed(() => {
  const term = query.value.trim().toLowerCase()
  if (!term) return []

  return props.nodes
    .filter((node) => {
      const name = node.name.toLowerCase()
      const fullName = node.fullName.toLowerCase()
      return name.includes(term) || fullName.includes(term)
    })
    .slice(0, 8)
})

const hasQuery = computed(() => query.value.trim().length > 0)
const hasResults = computed(() => results.value.length > 0)
const showResults = computed(() => isOpen.value && hasQuery.value)

function onInput(): void {
  isOpen.value = true
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
  <div class="search-bar" role="search">
    <label class="search-bar__label" :for="inputId">Search graph nodes</label>
    <div class="search-bar__control">
      <input
        :id="inputId"
        v-model="query"
        class="search-bar__input"
        type="search"
        placeholder="Search class, method, route..."
        autocomplete="off"
        spellcheck="false"
        :aria-controls="resultsId"
        @input="onInput"
        @focus="onInput"
      />
      <button v-if="hasQuery" class="search-bar__clear" type="button" aria-label="Clear search" @click="clearSearch">
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
  position: absolute;
  top: 1rem;
  left: 50%;
  z-index: 20;
  width: min(28rem, calc(100% - 2rem));
  transform: translateX(-50%);
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
  border: 0;
  outline: 0;
  background: transparent;
  color: inherit;
  font: inherit;
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
  margin-top: 0.5rem;
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
</style>
