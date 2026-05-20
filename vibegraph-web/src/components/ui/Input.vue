<script setup lang="ts">
defineProps<{
  modelValue: string
  placeholder?: string
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <input
    class="input"
    :value="modelValue"
    :placeholder="placeholder"
    @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  />
</template>

<style scoped>
.input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #2a2a2a;
  border-radius: 6px;
  background: transparent;
  width: 100%;
}
</style>
