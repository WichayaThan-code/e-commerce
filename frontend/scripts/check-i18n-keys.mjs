/**
 * Compare translation key paths across th / en / zh for each namespace JSON file.
 * Run: node scripts/check-i18n-keys.mjs
 */
import { readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const localesRoot = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'locales')
const langs = ['th', 'en', 'zh']

function flatten(obj, prefix = '') {
  const keys = []
  if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
    if (prefix) keys.push(prefix)
    return keys
  }
  for (const [key, value] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...flatten(value, path))
    } else {
      keys.push(path)
    }
  }
  return keys
}

function loadNamespace(lang, ns) {
  const path = join(localesRoot, lang, `${ns}.json`)
  return JSON.parse(readFileSync(path, 'utf8'))
}

const namespaces = readdirSync(join(localesRoot, 'th'))
  .filter((f) => f.endsWith('.json'))
  .map((f) => f.replace(/\.json$/, ''))

let exitCode = 0

for (const ns of namespaces) {
  const byLang = Object.fromEntries(langs.map((lang) => [lang, new Set(flatten(loadNamespace(lang, ns)))]))
  const all = new Set([...byLang.th, ...byLang.en, ...byLang.zh])
  const missing = []
  for (const key of [...all].sort()) {
    for (const lang of langs) {
      if (!byLang[lang].has(key)) missing.push({ lang, ns, key })
    }
  }
  if (missing.length) {
    exitCode = 1
    console.error(`\n[${ns}] missing keys (${missing.length}):`)
    for (const row of missing.slice(0, 40)) {
      console.error(`  ${row.lang}: ${row.key}`)
    }
    if (missing.length > 40) console.error(`  ... and ${missing.length - 40} more`)
  } else {
    console.log(`[${ns}] OK (${all.size} keys)`)
  }
}

process.exit(exitCode)
