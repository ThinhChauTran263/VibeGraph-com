<script setup lang="ts">
/**
 * LogoTile - uniform brand tile for third-party logos.
 *
 * The supplied assets are JPEGs with baked backgrounds (mostly pure black, one
 * light for Java). Instead of letting those raw rectangles sit on the page (which
 * looked inconsistent — a white Java tile next to black ones), every logo now sits
 * on an IDENTICAL slate card (matching the site's other surfaces) with the mark on
 * its own rounded inner "chip". The chip background matches the asset's baked bg
 * (`tone="dark"` → near-black, `tone="light"` → white) so there is no JPEG halo,
 * while the outer card stays consistent across the whole wall. A visible caption
 * carries the brand name so identity never depends on a low-contrast mark.
 */
withDefaults(
  defineProps<{
    src: string
    label: string
    tone?: 'dark' | 'light'
    /** Lift very dark-on-black marks (Windsurf/Cursor) so they read on the chip. */
    boost?: boolean
  }>(),
  { tone: 'dark', boost: false },
)
</script>

<template>
  <figure class="logo-tile">
    <span class="logo-tile__coin">
      <span class="logo-tile__chip" :class="`logo-tile__chip--${tone}`">
        <img
          class="logo-tile__img"
          :class="{ 'logo-tile__img--boost': boost }"
          :src="src"
          :alt="`${label} logo`"
          loading="lazy"
          decoding="async"
        />
      </span>
    </span>
    <figcaption class="logo-tile__label">{{ label }}</figcaption>
  </figure>
</template>

<style scoped>
.logo-tile {
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.7rem;
}

/* Outer card — identical for every brand, matches the site's surface cards. */
.logo-tile__coin {
  position: relative;
  display: grid;
  place-items: center;
  width: 88px;
  height: 88px;
  padding: 14px;
  border-radius: var(--vg-radius-lg);
  border: 1px solid var(--vg-border);
  background: var(--vg-grad-surface);
  box-shadow: var(--vg-shadow-sm);
  overflow: hidden;
  /* Elevation + accent only on hover — no transform, so neighbouring tiles and
     the cursor hit-box never shift (stable interaction state). */
  transition: border-color var(--vg-dur) var(--vg-ease-out),
    box-shadow var(--vg-dur) var(--vg-ease-out);
}

/* Soft top sheen for depth. */
.logo-tile__coin::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 55%;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.06), transparent);
  pointer-events: none;
}

.logo-tile:hover .logo-tile__coin {
  border-color: var(--vg-border-strong);
  box-shadow: var(--vg-shadow-lg), 0 0 0 1px rgba(96, 165, 250, 0.28);
}

/* Inner chip — the brand mark on its own rounded badge. */
.logo-tile__chip {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  padding: 9px;
  border-radius: var(--vg-radius);
  overflow: hidden;
}

.logo-tile__chip--dark {
  background: #05070d;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
}

.logo-tile__chip--light {
  background: #f4f4f5;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.08);
}

.logo-tile__img {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
}

.logo-tile__img--boost {
  filter: brightness(2) contrast(1.05);
}

.logo-tile__label {
  font-family: var(--vg-font-display);
  font-size: var(--vg-text-sm);
  font-weight: 500;
  color: var(--vg-text-muted);
  transition: color var(--vg-dur-fast) var(--vg-ease-out);
}

.logo-tile:hover .logo-tile__label {
  color: var(--vg-text);
}

@media (prefers-reduced-motion: reduce) {
  .logo-tile__coin {
    transition: none;
  }
}
</style>
