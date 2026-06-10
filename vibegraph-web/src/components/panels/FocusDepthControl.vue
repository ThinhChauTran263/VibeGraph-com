<script setup lang="ts">
import { useFilters } from '@/composables/useFilters'

const { focusDepth, setFocusDepth } = useFilters()

const depthOptions = [
  { label: 'All', value: -1 },
  { label: '1 hop', value: 1 },
  { label: '2 hops', value: 2 },
  { label: '3 hops', value: 3 },
  { label: '5 hops', value: 5 },
]
</script>

<template>
  <section class="focus-depth-control" aria-labelledby="focus-depth-heading">
    <h3 id="focus-depth-heading">Focus mode</h3>
    <div class="focus-depth-control__options" role="group" aria-label="Focus depth">
      <button
        v-for="option in depthOptions"
        :key="option.value"
        class="focus-depth-control__option"
        :class="{ 'focus-depth-control__option--active': focusDepth === option.value }"
        type="button"
        :aria-pressed="focusDepth === option.value"
        @click="setFocusDepth(option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.focus-depth-control {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  border: 1px solid rgba(96, 165, 250, 0.25);
  border-radius: 1rem;
  background: rgba(17, 24, 39, 0.94);
  color: #e5e7eb;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

.focus-depth-control h3 {
  margin: 0;
  font-size: 0.8125rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #93c5fd;
}

.focus-depth-control__options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.focus-depth-control__option {
  border: 1px solid #374151;
  border-radius: 999px;
  padding: 0.375rem 0.625rem;
  background: rgba(31, 41, 55, 0.85);
  color: #d1d5db;
  cursor: pointer;
}

.focus-depth-control__option:hover,
.focus-depth-control__option:focus-visible,
.focus-depth-control__option--active {
  border-color: rgba(96, 165, 250, 0.7);
  background: rgba(37, 99, 235, 0.28);
  color: #eff6ff;
}
</style>
