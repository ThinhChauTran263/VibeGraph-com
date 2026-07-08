<script setup lang="ts">
defineOptions({ name: 'UiSpinner' })

/**
 * Spinner - lightweight CSS loading indicator.
 *
 * Honors a `size` token (sm/md/lg) so callers can scale it inline next to
 * button labels (sm) or as a standalone overlay (lg). Defaults to `md`.
 */
withDefaults(
  defineProps<{
    size?: 'sm' | 'md' | 'lg'
  }>(),
  { size: 'md' },
)
</script>

<template>
  <div class="spinner" :class="`spinner--${size}`" role="status" aria-label="Loading" />
</template>

<style scoped>
.spinner {
  border: 2px solid rgba(148, 163, 184, 0.3);
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.spinner--sm {
  width: 16px;
  height: 16px;
  border-width: 2px;
}

.spinner--md {
  width: 24px;
  height: 24px;
  border-width: 2px;
}

.spinner--lg {
  width: 32px;
  height: 32px;
  border-width: 3px;
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 1.6s;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
