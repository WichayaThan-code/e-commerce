import type { TFunction } from 'i18next'
import type { MessageKey } from './messages'

/** Maps legacy flat message keys to i18next namespace + path. */
export function translateMessageKey(t: TFunction, key: MessageKey): string {
  if (key.startsWith('admin.')) return t(key.slice('admin.'.length), { ns: 'admin' })
  if (key.startsWith('auth.')) return t(key.slice('auth.'.length), { ns: 'auth' })
  if (key.startsWith('login.')) return t(key.slice('login.'.length), { ns: 'auth' })
  if (key.startsWith('merchant.')) return t(`merchant.${key.slice('merchant.'.length)}`, { ns: 'common' })
  return t(key, { ns: 'common' })
}
