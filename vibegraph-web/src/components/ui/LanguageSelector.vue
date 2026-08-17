<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale, type AppLocale } from '@/language'

const { locale, t } = useI18n({ useScope: 'global' })

const currentLocale = computed<AppLocale>(() => (locale.value === 'vi-VN' ? 'vi-VN' : 'en-US'))
const localeCode = computed(() => (currentLocale.value === 'vi-VN' ? 'VN' : 'US'))
const nextLocale = computed<AppLocale>(() => (currentLocale.value === 'vi-VN' ? 'en-US' : 'vi-VN'))
const nextLocaleName = computed(() =>
  nextLocale.value === 'vi-VN' ? t('language.vietnamese') : t('language.english'),
)

function toggleLocale(): void {
  void setLocale(nextLocale.value)
}
</script>

<template>
  <button
    type="button"
    class="language-selector"
    :aria-label="`${t('language.label')}: ${nextLocaleName}`"
    :title="nextLocaleName"
    @click="toggleLocale"
  >
    {{ localeCode }}
  </button>
</template>

<style scoped>
.language-selector {
  width: 42px;
  min-width: 42px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius-sm);
  background: rgba(15, 23, 42, 0.42);
  color: var(--vg-text-muted);
  font-size: var(--vg-text-xs);
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1;
  cursor: pointer;
  transition:
    background-color var(--vg-dur-fast),
    border-color var(--vg-dur-fast),
    color var(--vg-dur-fast),
    transform var(--vg-dur-fast) var(--vg-ease-out);
}

.language-selector:hover {
  border-color: var(--vg-blue-bright);
  background: rgba(59, 130, 246, 0.12);
  color: var(--vg-text);
}

.language-selector:active {
  transform: translateY(1px);
}

.language-selector:focus-visible {
  outline: 2px solid var(--vg-blue-bright);
  outline-offset: 2px;
}
</style>
