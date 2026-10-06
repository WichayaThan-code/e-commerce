/**
 * One-time exporter: legacy TS i18n modules -> src/locales/{lang}/{namespace}.json
 * Run: npx tsx scripts/export-legacy-locales.ts
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { messages, type MessageKey } from '../src/i18n/messages'
import { publicCopy } from '../src/i18n/publicCopy'
import { merchantOnboardingCopy } from '../src/i18n/merchantOnboardingCopy'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const localesDir = join(root, 'src', 'locales')

const authKeys = ['auth.login', 'auth.signup', 'login.back', 'login.help'] as const
const adminKeys = [
  'admin.search',
  'admin.searchAction',
  'admin.searchLabel',
  'admin.notify',
  'admin.notifyList',
  'admin.pendingTitle',
  'admin.shopCount',
  'admin.empty',
  'admin.waiting',
  'admin.unknownOwner',
] as const

function pickMessages(lang: keyof typeof messages, keys: readonly MessageKey[]) {
  const out: Record<string, string> = {}
  for (const key of keys) {
    const short = key.includes('.') ? key.split('.').slice(1).join('.') : key
    out[short] = messages[lang][key]
  }
  return out
}

function pickCommon(lang: keyof typeof messages) {
  const skip = new Set<string>([...authKeys, ...adminKeys])
  const out: Record<string, string> = {}
  for (const key of Object.keys(messages.th) as MessageKey[]) {
    if (skip.has(key)) continue
    if (key.startsWith('merchant.')) {
      const short = key.replace('merchant.', '')
      if (!out.merchant) out.merchant = {} as Record<string, string>
      ;(out.merchant as Record<string, string>)[short] = messages[lang][key]
      continue
    }
    const parts = key.split('.')
    let cursor: Record<string, unknown> = out
    for (let i = 0; i < parts.length - 1; i++) {
      const p = parts[i]
      if (!cursor[p] || typeof cursor[p] !== 'object') cursor[p] = {}
      cursor = cursor[p] as Record<string, unknown>
    }
    cursor[parts[parts.length - 1]] = messages[lang][key]
  }
  return out
}

function serializePublic(lang: keyof typeof publicCopy) {
  const raw = publicCopy[lang]
  return JSON.parse(
    JSON.stringify(raw, (_key, value) => {
      if (typeof value === 'function') {
        const sample = value('{{name}}')
        if (typeof sample === 'string' && sample.includes('{{name}}')) return sample
        const sample2 = value('{{label}}')
        if (typeof sample2 === 'string' && sample2.includes('{{label}}')) return sample2
        const sampleQ = value('__Q__')
        if (typeof sampleQ === 'string' && sampleQ.includes('__Q__')) return sampleQ.replaceAll('__Q__', '{{q}}')
        const sampleRef = value('__REF__')
        if (typeof sampleRef === 'string' && sampleRef.includes('__REF__')) return sampleRef.replaceAll('__REF__', '{{ref}}')
        const samplePath = value('__PATH__')
        if (typeof samplePath === 'string' && samplePath.includes('__PATH__')) return samplePath.replaceAll('__PATH__', '{{path}}')
        return String(value(''))
      }
      return value
    }),
  )
}

function writeLocale(lang: string, namespace: string, data: unknown) {
  const dir = join(localesDir, lang)
  mkdirSync(dir, { recursive: true })
  writeFileSync(join(dir, `${namespace}.json`), `${JSON.stringify(data, null, 2)}\n`, 'utf8')
}

for (const lang of ['th', 'en', 'zh'] as const) {
  writeLocale(lang, 'common', pickCommon(lang))
  writeLocale(lang, 'auth', pickMessages(lang, authKeys))
  writeLocale(lang, 'admin', pickMessages(lang, adminKeys))
  writeLocale(lang, 'public', serializePublic(lang))
  writeLocale(lang, 'merchant', merchantOnboardingCopy[lang])
}

console.log('Wrote locale JSON under src/locales/{th,en,zh}/')
