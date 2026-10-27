/**
 * AG5: custom fonts for CUSTOM storefronts. The merchant only picks WOFF2 files from their own media library
 * (`fontCustom.headingMediaId` / `bodyMediaId`); the stylesheet below is generated here from our own public media URLs
 * and a generated per-shop family name. No merchant-supplied CSS, URL or font name ever reaches the page.
 */
import { mediaUrl } from '../services/api'
import { shopThemeFontStack, type ThemeConfig, type ThemeFontCustom } from './shopTheme'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const SAFE_SLUG = /^[a-z0-9][a-z0-9_-]{0,98}$/i

/** Small deterministic hash so the family name is stable per shop but never merchant-controlled text. */
function slugHash(slug: string): string {
  let hash = 5381
  for (let index = 0; index < slug.length; index += 1) {
    hash = ((hash << 5) + hash + slug.charCodeAt(index)) >>> 0
  }
  return hash.toString(36)
}

/** `shop-font-{slugHash}`; `-h` / `-b` suffixes name the heading / body faces. */
export function shopFontFamilyPrefix(slug: string): string {
  return `shop-font-${slugHash(slug)}`
}

function fontSourceUrl(slug: string, mediaId: string | undefined): string | null {
  if (!mediaId || !UUID.test(mediaId) || !SAFE_SLUG.test(slug)) return null
  return mediaUrl(`/api/shops/${encodeURIComponent(slug)}/theme-media/${mediaId}`)
}

type CustomFontFaces = { css: string; body?: string; heading?: string }

/** `@font-face` rules (font-display: swap) plus the resulting font stacks, or null when nothing custom is usable. */
export function customFontFaces(slug: string, config: Pick<ThemeConfig, 'font' | 'fontCustom'>): CustomFontFaces | null {
  const custom: ThemeFontCustom | undefined = config.fontCustom
  if (!custom) return null
  const prefix = shopFontFamilyPrefix(slug)
  const rules: string[] = []
  const faces: Partial<Record<'body' | 'heading', string>> = {}
  const fallbackStack = shopThemeFontStack(custom.fallback ?? config.font)
  const add = (role: 'body' | 'heading', mediaId: string | undefined, suffix: string) => {
    const src = fontSourceUrl(slug, mediaId)
    if (!src) return
    const family = `${prefix}-${suffix}`
    rules.push(`@font-face{font-family:"${family}";src:url("${src}") format("woff2");font-weight:100 900;font-style:normal;font-display:swap}`)
    faces[role] = family
  }
  add('body', custom.bodyMediaId, 'b')
  add('heading', custom.headingMediaId, 'h')
  if (rules.length === 0) return null
  const body = faces.body ? `"${faces.body}", ${fallbackStack}` : shopThemeFontStack(config.font)
  const heading = faces.heading ? `"${faces.heading}", ${body}` : undefined
  return { css: rules.join('\n'), body: faces.body ? body : undefined, heading }
}

/** CSS variables for the shell: `--shop-font` / `--font-sans` follow the custom body face; headings get `--shop-font-heading`. */
export function customFontVars(slug: string, config: Pick<ThemeConfig, 'font' | 'fontCustom'>): Record<string, string> {
  const faces = customFontFaces(slug, config)
  if (!faces) return {}
  return {
    ...(faces.body ? { '--shop-font': faces.body, '--font-sans': faces.body } : {}),
    ...(faces.heading ? { '--shop-font-heading': faces.heading } : {}),
  }
}
