<script setup lang="ts">
/**
 * Spinner - Loading indicator.
 */
defineProps<{
  size?: 'sm' | 'md' | 'lg'
}>()
</script>

<template>
  <div class="spinner">
    <!-- TODO: SVG spinner animation -->
  </div>
</template>

<style scoped>
.spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #2a2a2a;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
