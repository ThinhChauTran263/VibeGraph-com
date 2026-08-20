<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import type { Project } from '@/types/api'

const props = defineProps<{
  id?: string
  modelValue: string
  projects: Project[]
  label: string
  placeholder: string
  existingLabel: string
  existingProjectIds: Set<string>
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const open = ref(false)
const activeIndex = ref(-1)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const componentId = useId()
const panelId = `repository-options-${componentId}`
const selectedProject = computed(() =>
  props.projects.find((project) => project.id === props.modelValue),
)
const activeOptionId = computed(() => {
  const project = props.projects[activeIndex.value]
  return open.value && project ? `${panelId}-${project.id}` : undefined
})

function scrollActiveIntoView(): void {
  void nextTick(() => {
    document.getElementById(activeOptionId.value ?? '')?.scrollIntoView?.({ block: 'nearest' })
  })
}

function openList(preferredIndex?: number): void {
  if (!props.projects.length) return
  const selectedIndex = props.projects.findIndex((project) => project.id === props.modelValue)
  activeIndex.value = preferredIndex ?? (selectedIndex >= 0 ? selectedIndex : 0)
  open.value = true
  scrollActiveIntoView()
}

function toggleList(): void {
  if (open.value) {
    closeList()
    return
  }
  openList()
}

function closeList(): void {
  open.value = false
  activeIndex.value = -1
}

function moveActive(step: number): void {
  if (!open.value) {
    openList(step > 0 ? 0 : props.projects.length - 1)
    return
  }
  activeIndex.value = (activeIndex.value + step + props.projects.length) % props.projects.length
  scrollActiveIntoView()
}

function selectProject(project: Project): void {
  emit('update:modelValue', project.id)
  closeList()
  void nextTick(() => trigger.value?.focus())
}

function selectActive(): void {
  if (!open.value) {
    openList()
    return
  }
  const project = props.projects[activeIndex.value]
  if (project) selectProject(project)
}

function handleFocusOut(event: FocusEvent): void {
  const nextTarget = event.relatedTarget
  if (!(nextTarget instanceof Node) || !event.currentTarget) {
    closeList()
    return
  }
  if (!(event.currentTarget as HTMLElement).contains(nextTarget)) closeList()
}

function handlePointerDown(event: PointerEvent): void {
  if (root.value && !root.value.contains(event.target as Node)) closeList()
}

onMounted(() => document.addEventListener('pointerdown', handlePointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', handlePointerDown))
</script>

<template>
  <div ref="root" class="repository-select" @focusout="handleFocusOut">
    <button
      :id="id"
      ref="trigger"
      type="button"
      class="repository-select__trigger"
      role="combobox"
      aria-haspopup="listbox"
      aria-required="true"
      :aria-label="label"
      :aria-expanded="open"
      :aria-controls="panelId"
      :aria-activedescendant="activeOptionId"
      :disabled="!projects.length"
      data-test="repository-select-trigger"
      @click="toggleList"
      @keydown.down.prevent="moveActive(1)"
      @keydown.up.prevent="moveActive(-1)"
      @keydown.home.prevent="openList(0)"
      @keydown.end.prevent="openList(projects.length - 1)"
      @keydown.enter.prevent="selectActive"
      @keydown.space.prevent="selectActive"
      @keydown.esc.stop.prevent="closeList"
    >
      <span class="repository-select__leading" aria-hidden="true">
        <AppIcon name="repository" :size="18" />
      </span>
      <span class="repository-select__value" :class="{ placeholder: !selectedProject }">
        <span>{{ selectedProject?.name ?? placeholder }}</span>
        <small v-if="selectedProject && existingProjectIds.has(selectedProject.id)">
          {{ existingLabel }}
        </small>
      </span>
      <AppIcon class="repository-select__chevron" name="chevron" :size="18" />
    </button>

    <Transition name="repository-options">
      <ul
        v-if="open"
        :id="panelId"
        class="repository-select__panel"
        role="listbox"
        :aria-label="placeholder"
      >
        <li
          v-for="(project, index) in projects"
          :id="`${panelId}-${project.id}`"
          :key="project.id"
          class="repository-select__option"
          :class="{ active: index === activeIndex, selected: project.id === modelValue }"
          role="option"
          :aria-selected="project.id === modelValue"
          :data-test="`repository-option-${project.id}`"
          @mouseenter="activeIndex = index"
          @mousedown.prevent
          @click="selectProject(project)"
        >
          <span class="repository-select__option-icon" aria-hidden="true">
            <AppIcon name="repository" :size="17" />
          </span>
          <span class="repository-select__option-copy">
            <strong>{{ project.name }}</strong>
            <small v-if="existingProjectIds.has(project.id)">{{ existingLabel }}</small>
          </span>
          <span
            v-if="project.id === modelValue"
            class="repository-select__check"
            aria-hidden="true"
          >
            <AppIcon name="check" :size="17" />
          </span>
        </li>
      </ul>
    </Transition>
  </div>
</template>

<style scoped>
.repository-select {
  min-width: 0;
}

.repository-select__trigger {
  width: 100%;
  min-height: 52px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--vg-space-3);
  padding: 0.55rem 0.75rem;
  border: 1px solid var(--vg-border-strong);
  border-radius: var(--vg-radius);
  background: color-mix(in srgb, var(--vg-bg) 88%, transparent);
  color: var(--vg-text);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 180ms ease,
    background 180ms ease,
    box-shadow 180ms ease;
}

.repository-select__trigger:hover:not(:disabled),
.repository-select__trigger[aria-expanded='true'] {
  border-color: color-mix(in srgb, var(--vg-blue-bright) 68%, var(--vg-border));
  background: color-mix(in srgb, var(--vg-surface-2) 80%, var(--vg-bg));
}

.repository-select__trigger[aria-expanded='true'] {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--vg-blue-bright) 14%, transparent);
}

.repository-select__trigger:focus-visible {
  outline: 2px solid var(--vg-blue-bright);
  outline-offset: 2px;
}

.repository-select__trigger:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.repository-select__leading,
.repository-select__option-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 1px solid color-mix(in srgb, var(--vg-blue-bright) 24%, transparent);
  border-radius: var(--vg-radius-sm);
  background: color-mix(in srgb, var(--vg-blue-bright) 9%, transparent);
  color: var(--vg-blue-bright);
}

.repository-select__value,
.repository-select__option-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.repository-select__value > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 650;
}

.repository-select__value.placeholder {
  color: var(--vg-text-muted);
  font-weight: 500;
}

.repository-select__value small,
.repository-select__option-copy small {
  width: fit-content;
  padding: 0.1rem 0.4rem;
  border: 1px solid color-mix(in srgb, var(--vg-warning) 34%, transparent);
  border-radius: var(--vg-radius-pill);
  background: color-mix(in srgb, var(--vg-warning) 9%, transparent);
  color: var(--vg-warning);
  font-size: 0.6875rem;
  font-weight: 700;
  line-height: 1.35;
}

.repository-select__chevron {
  color: var(--vg-text-muted);
  transition: transform 180ms ease;
}

.repository-select__trigger[aria-expanded='true'] .repository-select__chevron {
  transform: rotate(180deg);
}

.repository-select__panel {
  max-height: min(13rem, 32dvh);
  margin: var(--vg-space-2) 0 0;
  padding: var(--vg-space-2);
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 1px solid color-mix(in srgb, var(--vg-blue-bright) 22%, var(--vg-border));
  border-radius: var(--vg-radius);
  background: linear-gradient(180deg, var(--vg-surface-2), var(--vg-bg-elev));
  box-shadow: var(--vg-shadow);
  list-style: none;
}

.repository-select__option {
  min-height: 54px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--vg-space-3);
  padding: 0.55rem 0.65rem;
  border: 1px solid transparent;
  border-radius: var(--vg-radius-sm);
  color: var(--vg-text-muted);
  cursor: pointer;
}

.repository-select__option + .repository-select__option {
  margin-top: var(--vg-space-1);
}

.repository-select__option.active {
  border-color: color-mix(in srgb, var(--vg-blue-bright) 24%, transparent);
  background: color-mix(in srgb, var(--vg-blue-bright) 10%, transparent);
}

.repository-select__option.selected {
  color: var(--vg-text);
}

.repository-select__option-copy strong {
  overflow-wrap: anywhere;
  color: inherit;
  font-size: var(--vg-text-sm);
  line-height: 1.35;
}

.repository-select__option-icon {
  width: 32px;
  height: 32px;
}

.repository-select__check {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: var(--vg-radius-pill);
  background: color-mix(in srgb, var(--vg-blue-bright) 14%, transparent);
  color: var(--vg-blue-bright);
}

.repository-options-enter-active,
.repository-options-leave-active {
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}

.repository-options-enter-from,
.repository-options-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (prefers-reduced-motion: reduce) {
  .repository-select__trigger,
  .repository-select__chevron,
  .repository-options-enter-active,
  .repository-options-leave-active {
    transition: none;
  }
}
</style>
