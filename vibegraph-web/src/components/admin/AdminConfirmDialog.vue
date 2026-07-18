<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    message: string
    confirmLabel?: string
    cancelLabel?: string
    tone?: 'default' | 'danger'
    busy?: boolean
  }>(),
  {
    confirmLabel: 'Confirm',
    cancelLabel: 'Cancel',
    tone: 'default',
    busy: false,
  },
)

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()
const dialog = ref<HTMLElement | null>(null)
const cancelButton = ref<HTMLButtonElement | null>(null)
let previousFocus: HTMLElement | null = null

function dialogId(suffix: string): string {
  return `${props.title.replace(/\s+/g, '-').toLowerCase()}-${suffix}`
}
function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && !props.busy) {
    event.preventDefault()
    emit('cancel')
    return
  }
  if (event.key !== 'Tab' || !dialog.value) return

  const focusable = Array.from(
    dialog.value.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
    ),
  )
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (!first || !last) return
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
watch(
  () => props.open,
  async (isOpen) => {
    if (isOpen) {
      previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
      await nextTick()
      cancelButton.value?.focus()
    } else {
      previousFocus?.focus()
      previousFocus = null
    }
  },
)
onBeforeUnmount(() => previousFocus?.focus())
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="dialog-backdrop"
      role="presentation"
      @click.self="!busy && emit('cancel')"
    >
      <section
        ref="dialog"
        class="dialog"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="dialogId('title')"
        :aria-describedby="dialogId('description')"
        @keydown="handleKeydown"
      >
        <div class="dialog__marker" :class="{ danger: tone === 'danger' }" aria-hidden="true"></div>
        <div class="dialog__body">
          <h2 :id="dialogId('title')">{{ title }}</h2>
          <p :id="dialogId('description')">{{ message }}</p>
        </div>
        <div class="dialog__actions">
          <button
            ref="cancelButton"
            type="button"
            class="btn secondary"
            :disabled="busy"
            @click="emit('cancel')"
          >
            {{ cancelLabel }}
          </button>
          <button
            type="button"
            class="btn primary"
            :class="{ danger: tone === 'danger' }"
            :disabled="busy"
            @click="emit('confirm')"
          >
            {{ busy ? 'Working...' : confirmLabel }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 5000;
  display: grid;
  place-items: center;
  padding: var(--vg-space-4);
  background: rgba(2, 6, 23, 0.72);
  backdrop-filter: blur(10px);
}

.dialog {
  width: min(100%, 27rem);
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: var(--vg-radius);
  background:
    linear-gradient(180deg, rgba(20, 30, 52, 0.98), rgba(11, 17, 32, 0.99)), var(--vg-surface);
  box-shadow: 0 28px 80px -42px rgba(2, 6, 23, 0.95);
}

.dialog__marker {
  height: 0.25rem;
  background: linear-gradient(90deg, var(--vg-blue), var(--vg-green-bright));
}

.dialog__marker.danger {
  background: linear-gradient(90deg, var(--vg-danger), #f97316);
}

.dialog__body {
  padding: var(--vg-space-5) var(--vg-space-5) var(--vg-space-3);
}

h2 {
  margin: 0;
  color: var(--vg-text);
  font-family: var(--vg-font-display);
  font-size: 1.1rem;
  letter-spacing: 0;
}

p {
  margin: var(--vg-space-2) 0 0;
  color: var(--vg-text-muted);
  line-height: 1.55;
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--vg-space-3);
  padding: var(--vg-space-4) var(--vg-space-5) var(--vg-space-5);
}

.btn {
  min-width: 7rem;
  min-height: 2.75rem;
  border-radius: var(--vg-radius-sm);
  padding: 0 var(--vg-space-4);
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.btn.secondary {
  border: 1px solid var(--vg-border);
  background: rgba(148, 163, 184, 0.08);
  color: var(--vg-text);
}

.btn.primary {
  border: 1px solid var(--vg-blue);
  background: linear-gradient(135deg, var(--vg-blue), var(--vg-blue-deep));
  color: white;
}

.btn.primary.danger {
  border-color: rgba(239, 68, 68, 0.58);
  background: rgba(239, 68, 68, 0.18);
  color: #fecaca;
}

@media (max-width: 520px) {
  .dialog__actions {
    flex-direction: column-reverse;
  }

  .btn {
    width: 100%;
  }
}
</style>
