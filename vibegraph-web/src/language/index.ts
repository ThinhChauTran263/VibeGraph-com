import { createI18n } from 'vue-i18n'
import enUS from './locales/en-US.json'
import viVN from './locales/vi-VN.json'

export const supportedLocales = ['en-US', 'vi-VN'] as const
export type AppLocale = (typeof supportedLocales)[number]
export type MessageSchema = typeof enUS

export const LOCALE_STORAGE_KEY = 'vg_locale'
export const DEFAULT_LOCALE: AppLocale = 'en-US'

function isAppLocale(value: string | null): value is AppLocale {
  return supportedLocales.some((locale) => locale === value)
}

function getInitialLocale(): AppLocale {
  const storedLocale = localStorage.getItem(LOCALE_STORAGE_KEY)
  return isAppLocale(storedLocale) ? storedLocale : DEFAULT_LOCALE
}

export const i18n = createI18n<[MessageSchema], AppLocale, false>({
  legacy: false,
  locale: getInitialLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages: {
    'en-US': enUS,
    'vi-VN': viVN,
  },
})

export function setLocale(locale: AppLocale): void {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_STORAGE_KEY, locale)
  document.documentElement.lang = locale
}

document.documentElement.lang = i18n.global.locale.value

export default i18n
