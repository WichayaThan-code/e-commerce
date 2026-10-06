import { useTranslation } from 'react-i18next'
import { localeTag } from '../i18n'

export function usePublicTranslation() {
  const { t, i18n } = useTranslation(['public', 'common'])
  const tp = (key: string, options?: Record<string, unknown>) => t(key, { ns: 'public', ...options })
  const tc = (key: string, options?: Record<string, unknown>) => t(key, { ns: 'common', ...options })
  return { tp, tc, localeTag: localeTag(i18n.language) }
}
