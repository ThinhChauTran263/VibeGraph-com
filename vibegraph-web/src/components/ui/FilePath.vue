<script setup lang="ts">
/**
 * FilePath — compact, expandable file-path display.
 *
 * Absolute import-workspace paths are long and noisy (D:\…\uploads\<uuid>\source\…). By default
 * this shows just the trailing segments; the user clicks to reveal the full path only when they
 * want it. Always renders forward slashes for readability regardless of OS separators.
 */
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    path: string
    /** Optional 1-based line number appended as `:line` (code symbols only). */
    line?: number | null
    /** Trailing segments to show when collapsed. */
    segments?: number
  }>(),
  { line: null, segments: 2 },
)

const expanded = ref(false)

const normalized = computed(() => props.path.replace(/\\/g, '/'))

const parts = computed(() => normalized.value.split('/').filter(Boolean))

const truncatable = computed(() => parts.value.length > props.segments)

const lineSuffix = computed(() =>
  typeof props.line === 'number' && props.line > 0 ? `:${props.line}` : '',
)

const collapsed = computed(() => {
  if (!truncatable.value) return normalized.value + lineSuffix.value
  return '…/' + parts.value.slice(-props.segments).join('/') + lineSuffix.value
})

const full = computed(() => normalized.value + lineSuffix.value)

const display = computed(() => (expanded.value ? full.value : collapsed.value))

function toggle(): void {
  if (truncatable.value) expanded.value = !expanded.value
}
</script>

<template>
  <button
    v-if="truncatable"
    type="button"
    class="file-path"
    :class="{ 'file-path--expanded': expanded }"
    :aria-expanded="expanded"
    :title="expanded ? 'Click to collapse path' : full"
    @click.stop="toggle"
  >
    <span class="file-path__text">{{ display }}</span>
  </button>
  <span v-else class="file-path file-path--static" :title="full">{{ display }}</span>
</template>

<style scoped>
.file-path {
  display: inline-flex;
  align-items: baseline;
  gap: 0.4rem;
  max-width: 100%;
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  color: #94a3b8;
  font: inherit;
  font-size: 0.75rem;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
}

.file-path--static {
  cursor: default;
}

.file-path__text {
  overflow-wrap: anywhere;
}

.file-path:hover .file-path__text,
.file-path:focus-visible .file-path__text {
  color: #cbd5e1;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.file-path:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
  border-radius: 0.25rem;
}
</style>
