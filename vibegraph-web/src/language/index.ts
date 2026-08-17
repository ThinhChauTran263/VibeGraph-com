import { createI18n } from 'vue-i18n'
import enUS from './locales/en-US.json'

export const supportedLocales = ['en-US', 'vi-VN'] as const
export type AppLocale = (typeof supportedLocales)[number]
export type MessageSchema = typeof enUS

export const LOCALE_STORAGE_KEY = 'vg_locale'
export const DEFAULT_LOCALE: AppLocale = 'en-US'

function isAppLocale(value: string | null): value is AppLocale {
  return supportedLocales.some((locale) => locale === value)
}

function getStoredLocale(): AppLocale {
  const storedLocale = localStorage.getItem(LOCALE_STORAGE_KEY)
  return isAppLocale(storedLocale) ? storedLocale : DEFAULT_LOCALE
}

// F-M4: only the default locale ships in the main bundle; the other locale loads on
// demand when it is first selected (~140KB of translations out of the initial chunk).
// The cast satisfies createI18n's full-record type while vi-VN registers lazily below.
export const i18n = createI18n<[MessageSchema], AppLocale, false>({
  legacy: false,
  locale: DEFAULT_LOCALE,
  fallbackLocale: DEFAULT_LOCALE,
  messages: {
    'en-US': enUS,
  } as Record<AppLocale, MessageSchema>,
})

const localeLoaders: Record<AppLocale, () => Promise<{ default: MessageSchema }>> = {
  'en-US': () => Promise.resolve({ default: enUS }),
  'vi-VN': () => import('./locales/vi-VN.json'),
}
const loadedLocales = new Set<AppLocale>(['en-US'])

/**
 * Switch the active locale, loading its message bundle on first use. Resolves after the
 * bundle is applied; already-loaded locales apply synchronously (no await is hit).
 */
export async function setLocale(locale: AppLocale): Promise<void> {
  if (!loadedLocales.has(locale)) {
    i18n.global.setLocaleMessage(locale, (await localeLoaders[locale]()).default)
    loadedLocales.add(locale)
  }
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_STORAGE_KEY, locale)
  document.documentElement.lang = locale
}

document.documentElement.lang = getStoredLocale()

// A stored non-default locale starts on the eager fallback, then flips over as soon as
// its lazy bundle lands (same tick in practice — a dynamic import of a local JSON chunk).
const storedLocale = getStoredLocale()
if (storedLocale !== DEFAULT_LOCALE) {
  void setLocale(storedLocale)
}

export default i18n
