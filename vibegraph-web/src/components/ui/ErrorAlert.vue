<script setup lang="ts">
defineProps<{
  title?: string
  message: string
}>()
</script>

<template>
  <div class="error-alert">
    <div class="error-content">
      <h4 v-if="title" class="error-title">{{ title }}</h4>
      <p class="error-message">{{ message }}</p>
    </div>
    <div v-if="$slots.default" class="error-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.error-alert {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-4);
  background-color: rgba(239, 68, 68, 0.1);
  color: var(--vg-danger);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: var(--vg-radius-sm);
  padding: var(--vg-space-4);
}

@media (min-width: 768px) {
  .error-alert {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

.error-title {
  margin: 0 0 var(--vg-space-2) 0;
  font-size: var(--vg-text-base);
  font-weight: 600;
  color: var(--vg-text);
}
.error-message {
  margin: 0;
  font-size: var(--vg-text-sm);
}
.error-actions {
  display: flex;
  gap: var(--vg-space-2);
}
</style>
