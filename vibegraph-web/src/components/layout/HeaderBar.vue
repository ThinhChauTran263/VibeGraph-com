<script setup lang="ts">
/**
 * HeaderBar - Top navigation bar.
 * Contains: brand mark, project selector, search bar, settings button.
 */
import BrandMark from '@/components/ui/BrandMark.vue'
</script>

<template>
  <header class="header-bar">
    <RouterLink class="header-bar__brand" :to="{ name: 'home' }" aria-label="VibeGraph home">
      <BrandMark :size="26" />
    </RouterLink>
    <!-- TODO: Project selector, SearchBar, settings -->
  </header>
</template>

<style scoped>
.header-bar {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0 1rem;
  border-bottom: 1px solid #2a2a2a;
}

.header-bar__brand {
  display: inline-flex;
  align-items: center;
}
</style>
