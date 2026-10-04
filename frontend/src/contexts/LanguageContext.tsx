import { createContext, useContext, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import i18n, { localeTag, locales, type Locale } from '../i18n'
import type { MerchantOnboardingCopy } from '../i18n/merchantOnboardingCopy'
import type { PublicCopy } from '../i18n/publicCopy'
import { type MessageKey } from '../i18n/messages'
import { translateMessageKey } from '../i18n/translateMessageKey'

type LanguageContextValue = {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: (key: MessageKey) => string
  /** @deprecated Prefer useTranslation('public') on public pages */
  copy: PublicCopy
  /** @deprecated Prefer useTranslation('merchant') on onboarding pages */
  onboarding: MerchantOnboardingCopy
  localeTag: string
}

const LanguageContext = createContext<LanguageContextValue | null>(null)

function resolveLocale(language: string): Locale {
  if (language.startsWith('zh')) return 'zh'
  if (language.startsWith('en')) return 'en'
  if (language.startsWith('th')) return 'th'
  return locales.includes(language as Locale) ? (language as Locale) : 'th'
}

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const { i18n: inst } = useTranslation()

  const value = useMemo<LanguageContextValue>(() => {
    const locale = resolveLocale(inst.language)
    return {
      locale,
      setLocale: (next) => {
        void inst.changeLanguage(next)
      },
      t: (key) => translateMessageKey(inst.t.bind(inst), key),
      copy: inst.getResourceBundle(locale, 'public') as PublicCopy,
      onboarding: inst.getResourceBundle(locale, 'merchant') as MerchantOnboardingCopy,
      localeTag: localeTag(inst.language),
    }
  }, [inst, inst.language])

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>
}

export function useLanguage() {
  const value = useContext(LanguageContext)
  if (!value) throw new Error('useLanguage must be used inside LanguageProvider')
  return value
}

export { i18n }
