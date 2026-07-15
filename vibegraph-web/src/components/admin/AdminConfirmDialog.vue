<script setup lang="ts">
withDefaults(
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
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="dialog-backdrop" role="presentation" @click.self="emit('cancel')">
      <section
        class="dialog"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="`${title.replace(/\s+/g, '-').toLowerCase()}-title`"
      >
        <div class="dialog__marker" :class="{ danger: tone === 'danger' }" aria-hidden="true"></div>
        <div class="dialog__body">
          <h2 :id="`${title.replace(/\s+/g, '-').toLowerCase()}-title`">{{ title }}</h2>
          <p>{{ message }}</p>
        </div>
        <div class="dialog__actions">
          <button type="button" class="btn secondary" :disabled="busy" @click="emit('cancel')">
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
