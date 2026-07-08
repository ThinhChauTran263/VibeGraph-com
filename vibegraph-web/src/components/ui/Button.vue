<script setup lang="ts">
defineOptions({ name: 'UiButton' })

defineProps<{
  variant?: 'primary' | 'secondary' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
  disabled?: boolean
}>()
</script>

<template>
  <button class="btn">
    <slot />
  </button>
</template>

<style scoped>
.btn {
  padding: 0.5rem 1rem;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
}
</style>
