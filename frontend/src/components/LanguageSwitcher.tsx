import { useEffect, useRef, useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import i18n, { type Locale, localeMeta, locales } from '../i18n'
import { translateMessageKey } from '../i18n/translateMessageKey'
import '../styles/language.css'

function Flag({ locale }: { locale: Locale }) {
  if (locale === 'th') {
    return (
      <svg className="lang-flag" viewBox="0 0 22 16" aria-hidden="true">
        <rect width="22" height="16" fill="#A51931" />
        <rect y="2.7" width="22" height="10.6" fill="#F4F5F8" />
        <rect y="5.35" width="22" height="5.3" fill="#2D2A4A" />
      </svg>
    )
  }
  if (locale === 'zh') {
    return (
      <svg className="lang-flag" viewBox="0 0 22 16" aria-hidden="true">
        <rect width="22" height="16" fill="#DE2910" />
        <polygon fill="#FFDE00" points="5.1,2.1 5.55,3.4 6.95,3.4 5.82,4.22 6.25,5.55 5.1,4.75 3.95,5.55 4.38,4.22 3.25,3.4 4.65,3.4" />
        <polygon fill="#FFDE00" points="8.7,1.5 8.95,2.15 9.65,2.15 9.08,2.55 9.3,3.2 8.7,2.8 8.1,3.2 8.32,2.55 7.75,2.15 8.45,2.15" />
        <polygon fill="#FFDE00" points="10.1,3.3 10.35,3.95 11.05,3.95 10.48,4.35 10.7,5 10.1,4.6 9.5,5 9.72,4.35 9.15,3.95 9.85,3.95" />
        <polygon fill="#FFDE00" points="10.1,5.7 10.35,6.35 11.05,6.35 10.48,6.75 10.7,7.4 10.1,7 9.5,7.4 9.72,6.75 9.15,6.35 9.85,6.35" />
        <polygon fill="#FFDE00" points="8.7,7.4 8.95,8.05 9.65,8.05 9.08,8.45 9.3,9.1 8.7,8.7 8.1,9.1 8.32,8.45 7.75,8.05 8.45,8.05" />
      </svg>
    )
  }
  return (
    <svg className="lang-flag" viewBox="0 0 22 16" aria-hidden="true">
      <rect width="22" height="16" fill="#012169" />
      <path d="M0 0 L22 16 M22 0 L0 16" stroke="#fff" strokeWidth="3.2" />
      <path d="M0 0 L22 16 M22 0 L0 16" stroke="#C8102E" strokeWidth="1.5" />
      <path d="M11 0 V16 M0 8 H22" stroke="#fff" strokeWidth="5.2" />
      <path d="M11 0 V16 M0 8 H22" stroke="#C8102E" strokeWidth="2.8" />
    </svg>
  )
}

export default function LanguageSwitcher() {
  const { i18n: inst } = useTranslation()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const locale: Locale = inst.language.startsWith('zh') ? 'zh' : inst.language.startsWith('en') ? 'en' : 'th'
  const current = localeMeta[locale]
  const t = (key: 'lang.choose') => translateMessageKey(inst.t.bind(inst), key)

  useEffect(() => {
    if (!open) return
    const onPointer = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    window.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      window.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="lang-switch" ref={root}>
      <button className="lang-current" type="button" aria-label={t('lang.choose')} aria-expanded={open} onClick={() => setOpen(value => !value)}>
        <Flag locale={locale} />
        <span>{current.code}</span>
        <ChevronDown size={14} />
      </button>
      {open && (
        <div className="lang-menu" role="listbox" aria-label={t('lang.choose')}>
          {locales.map(item => (
            <button
              key={item}
              type="button"
              role="option"
              aria-selected={item === locale}
              onClick={() => { void i18n.changeLanguage(item); setOpen(false) }}
            >
              <Flag locale={item} />
              <span>{localeMeta[item].code}</span>
              <small>{localeMeta[item].name}</small>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
