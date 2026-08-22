<script setup lang="ts">
/**
 * BrandMark - VibeGraph wordmark + logo glyph.
 *
 * Renders the official VibeGraph logo (node-graph mark) next to an optional
 * wordmark. `size` controls the glyph height; the wordmark is optional so it
 * can sit in tight toolbars (glyph-only) or full nav bars (with label).
 */
import logoUrl from '@/assets/images/Icon/vibegraph-logo.png'
import type { RouteLocationRaw } from 'vue-router'

withDefaults(
  defineProps<{
    size?: number
    showWordmark?: boolean
    glyphTo?: RouteLocationRaw
    glyphAriaLabel?: string
  }>(),
  { size: 28, showWordmark: true, glyphAriaLabel: 'VibeGraph home' },
)
</script>

<template>
  <span class="brand" :style="{ '--glyph': `${size}px` }">
    <RouterLink v-if="glyphTo" class="brand__glyph-link" :to="glyphTo" :aria-label="glyphAriaLabel">
      <img
        class="brand__glyph"
        :src="logoUrl"
        :width="size"
        :height="size"
        alt="VibeGraph logo"
        decoding="async"
      />
    </RouterLink>
    <img
      v-else
      class="brand__glyph"
      :src="logoUrl"
      :width="size"
      :height="size"
      alt="VibeGraph logo"
      decoding="async"
    />
    <span v-if="showWordmark" class="brand__word">
      Vibe<span class="brand__word-accent">Graph</span>
    </span>
  </span>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
}

.brand__glyph {
  display: block;
  flex-shrink: 0;
  object-fit: contain;
  border-radius: 6px;
}

.brand__glyph-link {
  display: inline-flex;
  flex-shrink: 0;
  border-radius: 6px;
}

.brand__word {
  font-family: var(--vg-font-display);
  font-weight: 600;
  font-size: calc(var(--glyph) * 0.62);
  letter-spacing: -0.01em;
  color: var(--vg-text);
}

.brand__word-accent {
  background: var(--vg-grad-brand);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
</style>
