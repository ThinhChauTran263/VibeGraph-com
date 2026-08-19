<script setup lang="ts">
/**
 * ToastHost - global toast stack (bottom-right), rendered once in App.vue.
 *
 * Success/error toasts auto-dismiss; toasts may carry a "View project" style
 * action link. The region is aria-live polite so screen readers announce
 * outcomes without interrupting the user mid-task. Long-running import
 * progress is intentionally NOT surfaced here — the project card shows the
 * live analyzing state instead.
 */
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useToasts } from '@/stores/toasts'

const { t } = useI18n({ useScope: 'global' })
const toasts = useToasts()
</script>

<template>
  <div class="toast-host" role="region" :aria-label="t('toasts.region')">
    <TransitionGroup name="toast" tag="div" class="toast-host__stack" aria-live="polite">
      <article v-for="toast in toasts.toasts" :key="toast.id" class="toast" :class="`toast--${toast.kind}`">
        <span class="toast__dot" aria-hidden="true"></span>

        <div class="toast__body">
          <p class="toast__title">{{ toast.title }}</p>
          <p v-if="toast.message" class="toast__message">{{ toast.message }}</p>
          <div v-if="toast.actionRoute" class="toast__actions">
            <RouterLink class="toast__action" :to="toast.actionRoute">
              {{ toast.actionLabel ?? t('toasts.viewProject') }}
            </RouterLink>
          </div>
        </div>

        <button
          type="button"
          class="toast__close"
          :aria-label="t('toasts.dismiss')"
          @click="toasts.dismiss(toast.id)"
        >
          <AppIcon name="close" :size="14" />
        </button>
      </article>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  right: var(--vg-space-4);
  bottom: var(--vg-space-4);
  z-index: 200;
  pointer-events: none;
}
.toast-host__stack {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-3);
  width: min(24rem, calc(100vw - 2 * var(--vg-space-4)));
}
.toast {
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: var(--vg-space-3);
  padding: var(--vg-space-3);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: color-mix(in srgb, var(--vg-surface) 96%, transparent);
  box-shadow: var(--vg-shadow-lg);
  backdrop-filter: blur(8px);
}
.toast--success {
  border-color: rgba(34, 197, 94, 0.4);
}
.toast--error {
  border-color: rgba(239, 68, 68, 0.45);
}
.toast__dot {
  flex: 0 0 auto;
  width: 0.6rem;
  height: 0.6rem;
  margin-top: 0.45rem;
  border-radius: 50%;
  background: var(--vg-text-muted);
}
.toast--success .toast__dot {
  background: var(--vg-green-bright);
}
.toast--error .toast__dot {
  background: var(--vg-danger);
}
.toast__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.toast__title {
  margin: 0;
  color: var(--vg-text);
  font-size: var(--vg-text-sm);
  font-weight: 700;
  overflow-wrap: anywhere;
}
.toast__message {
  margin: 0;
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.toast__actions {
  margin-top: 0.15rem;
}
.toast__action {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--vg-blue-bright);
  font-size: var(--vg-text-xs);
  font-weight: 700;
  text-decoration: none;
}
.toast__action:hover {
  text-decoration: underline;
}
.toast__close {
  flex: 0 0 auto;
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--vg-text-muted);
  cursor: pointer;
}
.toast__close:hover {
  color: var(--vg-text);
  border-color: var(--vg-border);
  background: var(--vg-surface-2);
}
.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 200ms ease-out,
    transform 200ms ease-out;
}
.toast-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.toast-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active {
    transition: none;
  }
  .toast-enter-from,
  .toast-leave-to {
    transform: none;
  }
}
</style>
