import { readonly, ref } from 'vue'

export type SiteLocale = 'zh-CN' | 'en-US'

const STORAGE_KEY = 'myoj:locale'
const stored = typeof localStorage === 'undefined' ? null : localStorage.getItem(STORAGE_KEY)
const currentLocale = ref<SiteLocale>(stored === 'en-US' ? 'en-US' : 'zh-CN')

function syncDocumentLocale(locale: SiteLocale) {
  if (typeof document === 'undefined') return
  document.documentElement.lang = locale
}

syncDocumentLocale(currentLocale.value)

export function setSiteLocale(locale: SiteLocale) {
  currentLocale.value = locale
  syncDocumentLocale(locale)
  if (typeof localStorage !== 'undefined') localStorage.setItem(STORAGE_KEY, locale)
}

export function useSiteLocale() {
  return readonly(currentLocale)
}

export function localize(zh: string, en: string): string {
  return currentLocale.value === 'en-US' ? en : zh
}

export function formatSiteDate(
  value: string | number | Date,
  options?: Intl.DateTimeFormatOptions,
) {
  return new Intl.DateTimeFormat(currentLocale.value, options).format(new Date(value))
}
