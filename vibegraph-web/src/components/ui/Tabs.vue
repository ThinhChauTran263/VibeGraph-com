<script setup lang="ts">
defineOptions({ name: 'UiTabs' })

/**
 * Tabs - Generic tab component.
 *
 * Usage:
 *   <Tabs :tabs="[{label: 'A'}, {label: 'B'}]" v-model="active" />
 */
</script>

<template>
  <div class="tabs">
    <!-- TODO: Tab triggers + tab content slot -->
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  flex-direction: column;
}
</style>
