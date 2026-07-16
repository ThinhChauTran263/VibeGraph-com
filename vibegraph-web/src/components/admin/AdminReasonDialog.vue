<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description: string
    confirmLabel?: string
    busy?: boolean
    requireFinalConfirm?: boolean
  }>(),
  {
    confirmLabel: 'Apply',
    busy: false,
    requireFinalConfirm: false,
  },
)

const emit = defineEmits<{
  (e: 'submit', payload: { safeReason: string; reason: string }): void
  (e: 'cancel'): void
}>()

const safeReason = ref('')
const reason = ref('')
const checked = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    safeReason.value = ''
    reason.value = ''
    checked.value = false
  },
)

function submit(): void {
  const safe = safeReason.value.trim()
  if (!safe || (props.requireFinalConfirm && !checked.value)) return
  emit('submit', {
    safeReason: safe,
    reason: reason.value.trim() || safe,
  })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="dialog-backdrop" role="presentation" @click.self="emit('cancel')">
      <form class="dialog" role="dialog" aria-modal="true" @submit.prevent="submit">
        <div class="dialog__marker" aria-hidden="true"></div>
        <div class="dialog__body">
          <h2>{{ title }}</h2>
          <p>{{ description }}</p>

          <label class="field" for="safe-reason">
            <span>User-visible reason</span>
            <textarea
              id="safe-reason"
              v-model="safeReason"
              required
              maxlength="240"
              rows="3"
              placeholder="Explain the account restriction in safe user-facing language."
            ></textarea>
          </label>

          <label class="field" for="internal-reason">
            <span>Internal reason</span>
            <textarea
              id="internal-reason"
              v-model="reason"
              maxlength="500"
              rows="3"
              placeholder="Optional admin-only detail. Defaults to the user-visible reason."
            ></textarea>
          </label>

          <label v-if="requireFinalConfirm" class="check-row">
            <input v-model="checked" type="checkbox" />
            <span>I understand this disables sign-in and API access.</span>
          </label>
        </div>

        <div class="dialog__actions">
          <button type="button" class="btn secondary" :disabled="busy" @click="emit('cancel')">
            Cancel
          </button>
          <button
            type="submit"
            class="btn danger"
            :disabled="busy || !safeReason.trim() || (requireFinalConfirm && !checked)"
          >
            {{ busy ? 'Working...' : confirmLabel }}
          </button>
        </div>
      </form>
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
  width: min(100%, 34rem);
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: var(--vg-radius);
  background:
    linear-gradient(180deg, rgba(20, 30, 52, 0.98), rgba(11, 17, 32, 0.99)), var(--vg-surface);
  box-shadow: 0 28px 80px -42px rgba(2, 6, 23, 0.95);
}

.dialog__marker {
  height: 0.25rem;
  background: linear-gradient(90deg, var(--vg-danger), #f97316);
}

.dialog__body {
  display: grid;
  gap: var(--vg-space-4);
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
  margin: calc(var(--vg-space-3) * -1) 0 0;
  color: var(--vg-text-muted);
  line-height: 1.55;
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-2);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

textarea {
  min-height: 5.75rem;
  resize: vertical;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: rgba(2, 6, 23, 0.42);
  color: var(--vg-text);
  padding: var(--vg-space-3);
  font: inherit;
  letter-spacing: 0;
  text-transform: none;
}

textarea:focus {
  outline: none;
  border-color: var(--vg-blue-bright);
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.16);
}

.check-row {
  display: flex;
  align-items: center;
  gap: var(--vg-space-3);
  padding: var(--vg-space-3);
  border: 1px solid rgba(239, 68, 68, 0.24);
  border-radius: var(--vg-radius-sm);
  background: rgba(239, 68, 68, 0.08);
  color: var(--vg-text);
  font-weight: 700;
}

.check-row input {
  width: 1.1rem;
  height: 1.1rem;
  accent-color: var(--vg-danger);
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

.btn.danger {
  border: 1px solid rgba(239, 68, 68, 0.58);
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
