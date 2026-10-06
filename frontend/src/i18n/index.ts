import i18n from 'i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { initReactI18next } from 'react-i18next'

import thAdmin from '../locales/th/admin.json'
import thAuth from '../locales/th/auth.json'
import thCommon from '../locales/th/common.json'
import thMerchant from '../locales/th/merchant.json'
import thPublic from '../locales/th/public.json'
import enAdmin from '../locales/en/admin.json'
import enAuth from '../locales/en/auth.json'
import enCommon from '../locales/en/common.json'
import enMerchant from '../locales/en/merchant.json'
import enPublic from '../locales/en/public.json'
import zhAdmin from '../locales/zh/admin.json'
import zhAuth from '../locales/zh/auth.json'
import zhCommon from '../locales/zh/common.json'
import zhMerchant from '../locales/zh/merchant.json'
import zhPublic from '../locales/zh/public.json'

export const locales = ['th', 'en', 'zh'] as const
export type Locale = (typeof locales)[number]

export const localeMeta: Record<Locale, { code: string; name: string }> = {
  th: { code: 'TH', name: 'ไทย' },
  en: { code: 'ENG', name: 'English' },
  zh: { code: 'CN', name: '中文' },
}

export const STORAGE_KEY = 'tsf_locale'

export const namespaces = ['common', 'auth', 'public', 'merchant', 'admin'] as const
export type AppNamespace = (typeof namespaces)[number]

const resources = {
  th: { common: thCommon, auth: thAuth, public: thPublic, merchant: thMerchant, admin: thAdmin },
  en: { common: enCommon, auth: enAuth, public: enPublic, merchant: enMerchant, admin: enAdmin },
  zh: { common: zhCommon, auth: zhAuth, public: zhPublic, merchant: zhMerchant, admin: zhAdmin },
} as const

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'th',
    defaultNS: 'common',
    ns: [...namespaces],
    supportedLngs: [...locales],
    nonExplicitSupportedLngs: true,
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: STORAGE_KEY,
      caches: ['localStorage'],
    },
  })

i18n.on('languageChanged', (lng) => {
  const base = lng.startsWith('zh') ? 'zh-CN' : lng.startsWith('en') ? 'en' : lng.startsWith('th') ? 'th' : lng
  document.documentElement.lang = base === 'zh' ? 'zh-CN' : base
})

export function localeTag(locale?: string) {
  const lng = locale ?? i18n.language
  if (lng.startsWith('zh')) return 'zh-CN'
  if (lng.startsWith('en')) return 'en-US'
  return 'th-TH'
}

export default i18n
