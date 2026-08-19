<script setup lang="ts" generic="T extends string | number">
/**
 * ThemedSelect - dark-themed dropdown that replaces the native <select>.
 *
 * The native popup is OS-rendered (light-blue highlight on Windows) and can
 * never follow the app's token theme, so this component renders the open
 * menu itself while keeping the WAI-ARIA listbox keyboard contract: the
 * trigger button owns focus, ArrowUp/Down move aria-activedescendant,
 * Enter/Space commit, Escape closes, pointer users get hover + click.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

export interface ThemedSelectOption<T extends string | number> {
  value: T
  label: string
}

const props = defineProps<{
  modelValue: T
  options: ThemedSelectOption<T>[]
  /** Id placed on the trigger button so an external <label for> can point at it. */
  inputId?: string
  disabled?: boolean
}>()

const emit = defineEmits<{ (e: 'update:modelValue', value: T): void }>()

const open = ref(false)
const activeIndex = ref(0)
const rootRef = ref<HTMLElement | null>(null)
const listRef = ref<HTMLElement | null>(null)

const selectedIndex = computed(() =>
  props.options.findIndex((option) => option.value === props.modelValue),
)
const currentLabel = computed(() => {
  const match = selectedIndex.value >= 0 ? props.options[selectedIndex.value] : undefined
  return match?.label ?? ''
})
const listboxId = computed(() => `${props.inputId ?? 'vg-select'}-listbox`)
const optionId = (index: number) => `${listboxId.value}-option-${index}`

function openMenu(): void {
  if (props.disabled) return
  activeIndex.value = selectedIndex.value >= 0 ? selectedIndex.value : 0
  open.value = true
}

function closeMenu(): void {
  open.value = false
}

function toggleMenu(): void {
  if (open.value) closeMenu()
  else openMenu()
}

function commit(index: number): void {
  const option = props.options[index]
  if (!option) return
  emit('update:modelValue', option.value)
  closeMenu()
}

function moveActive(delta: number): void {
  const next = Math.min(props.options.length - 1, Math.max(0, activeIndex.value + delta))
  activeIndex.value = next
  listRef.value?.children[next]?.scrollIntoView({ block: 'nearest' })
}

function onTriggerKeydown(event: KeyboardEvent): void {
  if (props.disabled) return
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      if (!open.value) openMenu()
      else moveActive(1)
      break
    case 'ArrowUp':
      event.preventDefault()
      if (!open.value) openMenu()
      else moveActive(-1)
      break
    case 'Home':
      if (open.value) {
        event.preventDefault()
        activeIndex.value = 0
      }
      break
    case 'End':
      if (open.value) {
        event.preventDefault()
        activeIndex.value = props.options.length - 1
      }
      break
    case 'Enter':
    case ' ':
      event.preventDefault()
      if (!open.value) openMenu()
      else commit(activeIndex.value)
      break
    case 'Escape':
      if (open.value) {
        event.preventDefault()
        closeMenu()
      }
      break
    case 'Tab':
      closeMenu()
      break
  }
}

function onPointerDownOutside(event: PointerEvent): void {
  if (open.value && rootRef.value && !rootRef.value.contains(event.target as Node)) {
    closeMenu()
  }
}

onMounted(() => document.addEventListener('pointerdown', onPointerDownOutside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDownOutside))
</script>

<template>
  <div ref="rootRef" class="vg-select" :class="{ 'vg-select--open': open }">
    <button
      :id="inputId"
      type="button"
      class="vg-select__trigger"
      :disabled="disabled"
      aria-haspopup="listbox"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="listboxId"
      :aria-activedescendant="open ? optionId(activeIndex) : undefined"
      @click="toggleMenu"
      @keydown="onTriggerKeydown"
    >
      <span class="vg-select__value">{{ currentLabel }}</span>
      <svg
        class="vg-select__chevron"
        width="12"
        height="8"
        viewBox="0 0 12 8"
        aria-hidden="true"
      >
        <path
          d="M1 1.5l5 5 5-5"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>
    <ul :id="listboxId" ref="listRef" v-show="open" role="listbox" class="vg-select__menu">
      <li
        v-for="(option, index) in options"
        :id="optionId(index)"
        :key="String(option.value)"
        role="option"
        :aria-selected="option.value === modelValue ? 'true' : 'false'"
        class="vg-select__option"
        :class="{
          'vg-select__option--active': index === activeIndex,
          'vg-select__option--selected': option.value === modelValue,
        }"
        @pointerenter="activeIndex = index"
        @pointerdown.prevent
        @click="commit(index)"
      >
        {{ option.label }}
      </li>
    </ul>
  </div>
</template>

<style scoped>
.vg-select {
  position: relative;
  min-width: 0;
}

.vg-select__trigger {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  height: 2.5rem;
  padding: 0 0.625rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface-2);
  color: var(--vg-text);
  font: inherit;
  font-size: var(--vg-text-sm);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--vg-dur-fast) ease,
    background-color var(--vg-dur-fast) ease;
}

.vg-select__trigger:hover:not(:disabled) {
  border-color: rgba(96, 165, 250, 0.65);
}

.vg-select__trigger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.vg-select--open .vg-select__trigger {
  border-color: var(--vg-blue-bright);
}

.vg-select__value {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vg-select__chevron {
  flex: 0 0 auto;
  color: var(--vg-text-muted);
  transition: transform var(--vg-dur-fast) var(--vg-ease-out);
}

.vg-select--open .vg-select__chevron {
  transform: rotate(180deg);
}

.vg-select__menu {
  position: absolute;
  top: calc(100% + 0.375rem);
  left: 0;
  right: 0;
  z-index: 30;
  margin: 0;
  padding: 0.25rem;
  list-style: none;
  max-height: 16rem;
  overflow-y: auto;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius-sm);
  background: var(--vg-surface-2);
  box-shadow: var(--vg-shadow-lg);
  transform-origin: top center;
  animation: vg-select-pop var(--vg-dur-fast) var(--vg-ease-out);
}

@keyframes vg-select-pop {
  from {
    opacity: 0;
    transform: translateY(-4px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.vg-select__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.4375rem 0.5rem;
  border-radius: calc(var(--vg-radius-sm) - 4px);
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
  cursor: pointer;
  transition:
    background-color var(--vg-dur-fast) ease,
    color var(--vg-dur-fast) ease;
}

.vg-select__option--active {
  background: var(--vg-surface-3);
}

.vg-select__option--selected {
  color: var(--vg-blue-bright);
  font-weight: 600;
}

/* Small accent dot instead of a glyph so the selected row stays scannable. */
.vg-select__option--selected::after {
  content: '';
  width: 0.375rem;
  height: 0.375rem;
  border-radius: 50%;
  background: var(--vg-blue-bright);
}
</style>
