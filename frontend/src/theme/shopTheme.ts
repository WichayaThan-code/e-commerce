/** Storefront theme types and helpers. Presets come from GET /theme/presets — only a minimal fallback lives here. */

import {
  NEW_BLOCK_TYPES, defaultBlockContent, resolveBlockContent, resolveLocalized,
  type BlockContent, type LocalizedString,
} from './blockContent'
import {
  SHOP_THEME_FONT_ROWS,
  SHOP_THEME_FONTS,
  type ShopThemeFontKey,
} from './shopThemeFontCatalog'

export { SHOP_THEME_FONTS, type ShopThemeFontKey }

export const SHOP_THEME_TEMPLATES = ['GENERAL', 'FASHION', 'FOOD_BEVERAGE', 'CUSTOM'] as const
export type ShopThemeTemplateKey = (typeof SHOP_THEME_TEMPLATES)[number]
/** Templates shown in /merchant/store/theme picker (CUSTOM is retired from the UI). */
export const SHOP_THEME_PICKER_TEMPLATES = ['GENERAL', 'FASHION', 'FOOD_BEVERAGE'] as const satisfies ReadonlyArray<ShopThemeTemplateKey>

/** CJK system fallbacks — Thai families do not cover Chinese glyphs. */
const CJK_SANS = '"PingFang SC", "Microsoft YaHei", "Noto Sans SC"'
const CJK_SERIF = '"Songti SC", "Noto Serif SC"'

export type ShopThemeFontDefinition = {
  id: ShopThemeFontKey
  /** CSS `font-family` primary name (not an i18n label). */
  family: string
  stack: string
  /** Google Fonts css2 URL, or null when self-hosted / already in index.html. */
  href: string | null
  /** Real weights requested from the provider (no synthetic extras). */
  weights: readonly number[]
  /** Family includes Thai glyphs; otherwise Thai falls back to Noto Thai in the stack. */
  thaiNative: boolean
}

function googleFontHref(family: string, weights: readonly number[]): string {
  const encoded = family.replace(/ /g, '+')
  const axis = weights.length === 1 && weights[0] === 400
    ? ''
    : `:wght@${weights.join(';')}`
  return `https://fonts.googleapis.com/css2?family=${encoded}${axis}&display=swap`
}

function fontStack(family: string, kind: 'sans' | 'serif' | 'display' | 'hand'): string {
  if (kind === 'serif') return `"${family}", "Noto Serif Thai", ${CJK_SERIF}, serif`
  return `"${family}", "Noto Sans Thai", ${CJK_SANS}, sans-serif`
}

export const SHOP_SECTION_KEYS = [
  'HERO', 'BANNERS', 'CATEGORIES', 'TRUST', 'INTRO', 'FEATURED', 'BESTSELLERS', 'NEW_ARRIVALS', 'CATALOG', 'HOURS',
] as const
export type ShopSectionKey = (typeof SHOP_SECTION_KEYS)[number]

/**
 * AG4: block types = the ten legacy section keys + block-only content types. Only SHOP_SECTION_KEYS live in
 * `config.sections` (the backend requires exactly those ten); the new types exist only in `layout.blocks` (CUSTOM).
 */
export const SHOP_BLOCK_TYPES = [...SHOP_SECTION_KEYS, ...NEW_BLOCK_TYPES] as const
export type ShopBlockType = (typeof SHOP_BLOCK_TYPES)[number]

export function isSectionKey(type: string): type is ShopSectionKey {
  return (SHOP_SECTION_KEYS as readonly string[]).includes(type)
}

export const SHOP_TRUST_ICONS = ['AUTHENTIC', 'FAST_SHIP', 'RETURNS', 'SECURE_PAY', 'CUSTOM'] as const
export type ShopTrustIcon = (typeof SHOP_TRUST_ICONS)[number]

export type ThemeBanner = { imageUrl: string; title?: string; link?: string; mediaId?: string }
/** sub: optional plain-text sub-line (80) overriding the default i18n one (tsf templates). */
export type ThemeTrustItem = { icon: ShopTrustIcon; label: string; enabled: boolean; sub?: string }
/** tsf announcement bar (plain text, max 2 x 120); empty = falls back to the first trust labels. */
export type ThemeAnnouncement = { items: string[] }
export type ThemeTrust = { enabled: boolean; items: ThemeTrustItem[] }

/** AG5: MEDIA_FULL (full-bleed image / video / gradient hero with stickers) exists for CUSTOM themes only. */
export type HeroLayout = 'FULL' | 'STRIP' | 'MEDIA_FULL' | 'SPLIT' | 'POSTER'
export type CatalogVariant = 'GRID' | 'LARGE' | 'MENU'
export type SectionVariant = HeroLayout | CatalogVariant | null

export const SHOP_CARD_STYLES = ['FLAT', 'BORDERED', 'SHADOW'] as const
export type ShopCardStyle = (typeof SHOP_CARD_STYLES)[number]

export const SHOP_DENSITIES = ['COMPACT', 'COMFORTABLE'] as const
export type ShopDensity = (typeof SHOP_DENSITIES)[number]

export const SHOP_BUTTON_SHAPES = ['ROUNDED', 'PILL', 'SQUARE'] as const
export type ShopButtonShape = (typeof SHOP_BUTTON_SHAPES)[number]

export const SHOP_SHADOWS = ['NONE', 'SOFT', 'MEDIUM', 'STRONG'] as const
export type ShopShadow = (typeof SHOP_SHADOWS)[number]

export const SHOP_IMAGE_RATIOS = ['SQUARE', 'PORTRAIT', 'LANDSCAPE'] as const
export type ShopImageRatio = (typeof SHOP_IMAGE_RATIOS)[number]

export const SHOP_CTA_STYLES = ['PILL', 'ROUNDED', 'SQUARE', 'NONE'] as const
export type ShopCtaStyle = (typeof SHOP_CTA_STYLES)[number]

export const SHOP_TITLE_ALIGNS = ['LEFT', 'CENTER'] as const
export type ShopTitleAlign = (typeof SHOP_TITLE_ALIGNS)[number]

export const SHOP_SECTION_BACKGROUNDS = ['NONE', 'SURFACE', 'PRIMARY', 'GRADIENT'] as const
export type ShopSectionBackground = (typeof SHOP_SECTION_BACKGROUNDS)[number]

export const SHOP_SECTION_ALIGNS = ['LEFT', 'CENTER'] as const
export type ShopSectionAlign = (typeof SHOP_SECTION_ALIGNS)[number]

export const SHOP_PADDING_Y = ['COMPACT', 'NORMAL', 'LARGE'] as const
export type ShopPaddingY = (typeof SHOP_PADDING_Y)[number]

export type ThemeColors = {
  primary: string
  secondary: string
  background: string
  text: string
  /** Optional muted accent (e.g. fashion gold) for hairlines / hover / price. */
  accent?: string
  surface?: string
  muted?: string
  ctaBackground?: string
  ctaText?: string
  border?: string
  /** GENERAL (themes spec) only: strong hairline, sale price, focus ring. */
  borderStrong?: string
  sale?: string
  focus?: string
}

export type ThemeStyle = {
  radius: number
  cardStyle: ShopCardStyle
  density: ShopDensity
  buttonShape: ShopButtonShape
  shadow?: ShopShadow
  headingTextShadow?: boolean
}

export type ThemeGradientStop = { color: string; at: number }
export type ThemeGradient = { angle: number; stops: ThemeGradientStop[] }
export type ThemeGradients = {
  hero?: ThemeGradient
  page?: ThemeGradient
  section?: ThemeGradient
}

export type ThemeProductCard = {
  imageRatio?: ShopImageRatio
  ctaStyle?: ShopCtaStyle
  showPrice?: boolean
  edgeToEdge?: boolean
  titleAlign?: ShopTitleAlign
}

export type ThemeSectionSetting = {
  background?: ShopSectionBackground
  align?: ShopSectionAlign
  columns?: 2 | 3 | 4 | 5
  paddingY?: ShopPaddingY
}

export type ThemeSectionSettings = Partial<Record<ShopSectionKey, ThemeSectionSetting>>

/** AG3: repeatable CUSTOM layout blocks (`layout.blocks`). */
export const SHOP_BLOCK_BACKGROUNDS = ['NONE', 'SURFACE', 'PRIMARY', 'GRADIENT', 'IMAGE'] as const
export type ShopBlockBackground = (typeof SHOP_BLOCK_BACKGROUNDS)[number]

export const MAX_LAYOUT_BLOCKS = 30
export const LAYOUT_BLOCK_ID_PATTERN = /^[a-z0-9-]{1,40}$/
/** Block types that may appear only once per shop page. */
export const SINGLETON_BLOCK_TYPES: readonly ShopBlockType[] = ['HERO', 'HOURS']
export const PRODUCT_BLOCK_TYPES: readonly ShopBlockType[] = ['FEATURED', 'BESTSELLERS', 'NEW_ARRIVALS', 'CATALOG']

export type BlockSettings = {
  background: ShopBlockBackground
  gradient?: ThemeGradient
  backgroundMediaId?: string
  /** Derived by the API from backgroundMediaId (never sent back as an editable field). */
  backgroundUrl?: string
  /** Dark overlay over an IMAGE background, 0?80 (%). */
  overlay?: number
  align: ShopSectionAlign
  columns?: 2 | 3 | 4 | 5
  paddingY: ShopPaddingY
  fullWidth: boolean
}

export type LayoutBlock = {
  id: string
  type: ShopBlockType
  enabled: boolean
  variant?: SectionVariant
  settings: BlockSettings
  /** `{}` for the ten legacy section types; typed per block type (see blockContent.ts) for STEPS/FEATURE/GALLERY/SOCIAL/RICH_TEXT. */
  content: BlockContent
}

export type ThemeLayout = { blocks: LayoutBlock[] }

/** AG5 hero extras (CUSTOM + MEDIA_FULL only; the backend strips every key below for other templates). */
export const HERO_BACKGROUND_TYPES = ['IMAGE', 'VIDEO', 'GRADIENT'] as const
export type HeroBackgroundType = (typeof HERO_BACKGROUND_TYPES)[number]
export const HERO_TEXT_ALIGNS = ['LEFT', 'CENTER'] as const
export type HeroTextAlign = (typeof HERO_TEXT_ALIGNS)[number]
export const HERO_HEADLINE_SIZES = ['L', 'XL'] as const
export type HeroHeadlineSize = (typeof HERO_HEADLINE_SIZES)[number]

/** Brand options for every template (absent = LEFT / LEFT, the previous look). */
export type ThemeBrand = { logoPosition?: HeroTextAlign; textAlign?: HeroTextAlign }
export const MAX_HERO_STICKERS = 6
export const HERO_RANGES = {
  overlay: [0, 80],
  heightVh: [50, 100],
  x: [0, 100],
  y: [0, 100],
  size: [40, 240],
  rotate: [-30, 30],
} as const

export type ThemeHeroBackground = {
  type: HeroBackgroundType
  mediaId?: string
  posterMediaId?: string
  /** Derived by the API from mediaId / posterMediaId (never sent back as editable fields). */
  url?: string
  posterUrl?: string
}
export type ThemeHeroSticker = {
  mediaId: string
  x: number
  y: number
  size: number
  rotate: number
  hideOnMobile: boolean
  /** Derived by the API from mediaId. */
  url?: string
}
export type ThemeHeroCta = { label: string; link: string }

/** Hero slider (every template): up to {@link MAX_HERO_SLIDES} shop-media images; empty = the shop cover. */
export type ThemeHeroSlide = { slideMediaId: string; imageUrl: string }
export const MAX_HERO_SLIDES = 5

export type ThemeHero = {
  layout: HeroLayout
  slides?: ThemeHeroSlide[]
  title: string
  subtitle: string
  ctaLabel: string
  ctaLink: string
  /** GENERAL only: plain-text eyebrow + secondary CTA (backend drops them for other templates). */
  eyebrow?: string
  ctaSecondaryLabel?: string
  ctaSecondaryLink?: string
  /** GENERAL MEDIA_FULL decorative text stickers: max 3 x 24 chars plain text. */
  badges?: string[]
  background?: ThemeHeroBackground
  overlay?: number
  heightVh?: number
  textAlign?: HeroTextAlign
  headlineSize?: HeroHeadlineSize
  stickers?: ThemeHeroSticker[]
  ctaSecondary?: ThemeHeroCta
}

/** AG5 header / footer (CUSTOM only). Links are internal paths or https; text is LocalizedString `{th, en?}`. */
export const HEADER_LAYOUTS = ['LOGO_LEFT', 'LOGO_CENTER'] as const
export type HeaderLayout = (typeof HEADER_LAYOUTS)[number]
export const MAX_MENU_ITEMS = 8
export const MAX_FOOTER_COLUMNS = 4
export const MAX_COLUMN_LINKS = 8

export type ThemeChromeLink = { label: LocalizedString; link: string }
export type ThemeAnnouncementBar = { text: LocalizedString; link?: string; backgroundColor: string; textColor: string }
export type ThemeHeader = {
  layout: HeaderLayout
  background: ShopBlockBackground
  gradient?: ThemeGradient
  backgroundMediaId?: string
  /** Derived by the API from backgroundMediaId. */
  backgroundUrl?: string
  overlay?: number
  sticky: boolean
  menu: ThemeChromeLink[]
  showSearch: boolean
  showSocialIcons: boolean
  announcementBar?: ThemeAnnouncementBar
}
export type ThemeFooterColumn = { title?: LocalizedString; links: ThemeChromeLink[] }
export type ThemeFooter = {
  columns: ThemeFooterColumn[]
  about?: LocalizedString
  showContacts: boolean
  showSocial: boolean
  background: ShopBlockBackground
  gradient?: ThemeGradient
  backgroundMediaId?: string
  backgroundUrl?: string
  overlay?: number
  copyright?: string
}

/** AG5: custom WOFF2 fonts from the shop's FONT media; `font` (legacy string) stays the fallback stack. */
export type ThemeFontCustom = {
  headingMediaId?: string
  bodyMediaId?: string
  headingUrl?: string
  bodyUrl?: string
  fallback: ShopThemeFontKey
}

export type SectionConfig = {
  key: ShopSectionKey
  enabled: boolean
  variant: SectionVariant
}

/** StoryBlock (INTRO) copy for the tsf templates; plain text only. introMediaId = shop media IMAGE, imageUrl filled on read. */
export type ThemeIntro = {
  eyebrow?: string
  title?: string
  body?: string
  ctaLabel?: string
  ctaLink?: string
  introMediaId?: string
  imageUrl?: string
  reverse?: boolean
}

function plainTextList(raw: unknown, max: number): string[] {
  return Array.isArray(raw) ? raw.filter((item): item is string => typeof item === 'string').slice(0, max) : []
}

function resolveIntro(raw: unknown): ThemeIntro | undefined {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return undefined
  const row = raw as Record<string, unknown>
  const out: ThemeIntro = {}
  for (const key of ['eyebrow', 'title', 'body', 'ctaLabel', 'ctaLink', 'introMediaId', 'imageUrl'] as const) {
    if (typeof row[key] === 'string') out[key] = row[key] as string
  }
  if (typeof row.reverse === 'boolean') out.reverse = row.reverse
  return out
}

export type ThemeConfig = {
  colors: ThemeColors
  /** Heading font (and the body font too when fontBody is absent - older single-font themes). */
  font: ShopThemeFontKey
  fontBody?: ShopThemeFontKey
  brand?: ThemeBrand
  style: ThemeStyle
  hero: ThemeHero
  banners: ThemeBanner[]
  trust: ThemeTrust
  sections: SectionConfig[]
  gradients?: ThemeGradients
  productCard?: ThemeProductCard
  sectionSettings?: ThemeSectionSettings
  /** GENERAL / FASHION / FOOD_BEVERAGE only (StoryBlock). */
  intro?: ThemeIntro
  /** tsf templates only (announcement bar). */
  announcement?: ThemeAnnouncement
  /** CUSTOM only; always present (possibly synthesized from sections) after normalizeTheme. */
  layout?: ThemeLayout
  /** AG5, CUSTOM only. Absent = the existing mall header / tabs / footer stay as they are. */
  header?: ThemeHeader
  footer?: ThemeFooter
  fontCustom?: ThemeFontCustom
}

export type ShopTheme = {
  templateKey: ShopThemeTemplateKey
  config: ThemeConfig
}

/** Typed catalog used by editor samples, loaders, and CSS stacks (100 families). */
export const SHOP_THEME_FONT_CATALOG: readonly ShopThemeFontDefinition[] = SHOP_THEME_FONT_ROWS.map((row) => ({
  id: row.id,
  family: row.family,
  stack: fontStack(row.family, row.kind),
  href: 'selfHosted' in row && row.selfHosted ? null : googleFontHref(row.family, row.weights),
  weights: row.weights,
  thaiNative: row.thaiNative,
}))

export const SHOP_THEME_FONT_STACKS: Record<ShopThemeFontKey, string> = Object.fromEntries(
  SHOP_THEME_FONT_CATALOG.map((font) => [font.id, font.stack]),
) as Record<ShopThemeFontKey, string>

export const SHOP_THEME_FONT_HREF: Record<ShopThemeFontKey, string | null> = Object.fromEntries(
  SHOP_THEME_FONT_CATALOG.map((font) => [font.id, font.href]),
) as Record<ShopThemeFontKey, string | null>

export function shopThemeFontDefinition(font: ShopThemeFontKey): ShopThemeFontDefinition {
  return SHOP_THEME_FONT_CATALOG.find((row) => row.id === font)
    ?? SHOP_THEME_FONT_CATALOG.find((row) => row.id === 'IBM_PLEX_SANS_THAI')!
}

export const SHOP_BREAKPOINTS = {
  mobileMax: 767,
  tabletMin: 768,
  tabletMax: 1023,
  desktopMin: 1024,
} as const

export const PREVIEW_WIDTHS = { mobile: 375, tablet: 768, desktop: 1280 } as const

const HERO_VARIANTS = new Set<string>(['FULL', 'STRIP', 'SPLIT', 'POSTER'])
const CUSTOM_HERO_VARIANTS = new Set<string>(['FULL', 'STRIP', 'MEDIA_FULL', 'SPLIT', 'POSTER'])
const CATALOG_VARIANTS = new Set<string>(['GRID', 'LARGE', 'MENU'])

const DEFAULT_STYLE: ThemeStyle = {
  radius: 12,
  cardStyle: 'BORDERED',
  density: 'COMFORTABLE',
  buttonShape: 'ROUNDED',
}

/** Absolute minimum theme when API theme is null (no embedded template presets). */
export function minimalFallbackTheme(): ShopTheme {
  return {
    templateKey: 'GENERAL',
    config: {
      colors: { primary: '#087bf5', secondary: '#08245d', background: '#ffffff', text: '#132b50' },
      font: 'IBM_PLEX_SANS_THAI',
      style: { ...DEFAULT_STYLE },
      hero: { layout: 'FULL', title: '', subtitle: '', ctaLabel: '', ctaLink: '' },
      banners: [],
      trust: {
        enabled: true,
        items: [
          { icon: 'AUTHENTIC', label: 'ของแท้ 100%', enabled: true },
          { icon: 'FAST_SHIP', label: 'ส่งไว', enabled: true },
          { icon: 'RETURNS', label: 'คืนสินค้าได้', enabled: true },
          { icon: 'SECURE_PAY', label: 'ชำระเงินปลอดภัย', enabled: true },
        ],
      },
      sections: [
        { key: 'HERO', enabled: true, variant: 'FULL' },
        { key: 'BANNERS', enabled: true, variant: null },
        { key: 'CATEGORIES', enabled: true, variant: null },
        { key: 'TRUST', enabled: true, variant: null },
        { key: 'FEATURED', enabled: true, variant: 'GRID' },
        { key: 'BESTSELLERS', enabled: true, variant: 'GRID' },
        { key: 'NEW_ARRIVALS', enabled: true, variant: 'GRID' },
        { key: 'CATALOG', enabled: true, variant: 'GRID' },
        { key: 'INTRO', enabled: false, variant: null },
        { key: 'HOURS', enabled: false, variant: null },
      ],
    },
  }
}

/** @deprecated Use minimalFallbackTheme — kept for callers that still expect defaultShopTheme. */
export function defaultShopTheme(): ShopTheme {
  return minimalFallbackTheme()
}

/** Presets from GET /api/merchant/settings/theme/presets (filled by the theme editor). */
let presetRegistry: Partial<Record<ShopThemeTemplateKey, ThemeConfig>> = {}

export function registerThemePresets(rows: Array<{ templateKey?: string; config?: ThemeConfig | Record<string, unknown> }>) {
  const next: Partial<Record<ShopThemeTemplateKey, ThemeConfig>> = {}
  for (const row of rows) {
    const key = SHOP_THEME_TEMPLATES.includes(row.templateKey as ShopThemeTemplateKey)
      ? (row.templateKey as ShopThemeTemplateKey)
      : null
    if (!key || !row.config) continue
    const normalized = normalizeTheme({ templateKey: key, config: row.config as ThemeConfig }, { skipRegistry: true })
    next[key] = normalized.config
  }
  presetRegistry = next
}

export function presetForTemplate(template: ShopThemeTemplateKey): ThemeConfig {
  return structuredClone(presetRegistry[template] ?? minimalFallbackTheme().config)
}

export function shopThemeFontStack(font?: string | null) {
  if (font && font in SHOP_THEME_FONT_STACKS) return SHOP_THEME_FONT_STACKS[font as ShopThemeFontKey]
  return SHOP_THEME_FONT_STACKS.IBM_PLEX_SANS_THAI
}

function appendFontStylesheet(id: string, href: string) {
  if (typeof document === 'undefined' || document.getElementById(id)) return
  const link = document.createElement('link')
  link.id = id
  link.rel = 'stylesheet'
  link.href = href
  // Network failure must not crash the page — browser keeps system fallbacks.
  link.onerror = () => { link.remove() }
  document.head.appendChild(link)
}

export function ensureThemeFontLink(font: ShopThemeFontKey | string | null | undefined) {
  if (typeof document === 'undefined' || !font) return
  const key = font.trim().toUpperCase()
  if (!(key in SHOP_THEME_FONT_HREF)) return
  const typed = key as ShopThemeFontKey
  const href = SHOP_THEME_FONT_HREF[typed]
  if (href) appendFontStylesheet(`shop-theme-font-${key}`, href)
  // Latin-primary picks still need a Thai face for storefront copy.
  const def = shopThemeFontDefinition(typed)
  if (!def.thaiNative) {
    const fallback: ShopThemeFontKey = def.stack.includes('Noto Serif Thai') ? 'NOTO_SERIF_THAI' : 'NOTO_SANS_THAI'
    const fallbackHref = SHOP_THEME_FONT_HREF[fallback]
    if (fallbackHref) appendFontStylesheet(`shop-theme-font-${fallback}`, fallbackHref)
  }
}

function parseHex(hex: string): [number, number, number] | null {
  const raw = hex.replace('#', '').trim()
  const full = raw.length === 3 ? raw.split('').map((c) => c + c).join('') : raw
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return null
  return [
    Number.parseInt(full.slice(0, 2), 16),
    Number.parseInt(full.slice(2, 4), 16),
    Number.parseInt(full.slice(4, 6), 16),
  ]
}

function relativeLuminance(hex: string): number | null {
  const rgb = parseHex(hex)
  if (!rgb) return null
  const lin = (channel: number) => {
    const c = channel / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2])
}

/** WCAG contrast ratio between two hex colors (1–21). Returns 0 if either color is invalid. */
export function contrastRatio(foreground: string, background: string): number {
  const a = relativeLuminance(foreground)
  const b = relativeLuminance(background)
  if (a == null || b == null) return 0
  const lighter = Math.max(a, b)
  const darker = Math.min(a, b)
  return (lighter + 0.05) / (darker + 0.05)
}

export function meetsContrastAA(foreground: string, background: string, min = 4.5): boolean {
  return contrastRatio(foreground, background) >= min
}

export function contrastOnColor(hex: string): string {
  const luminance = relativeLuminance(hex)
  if (luminance == null) return '#ffffff'
  return luminance > 0.45 ? '#111111' : '#ffffff'
}

/** Pairs that should meet WCAG AA for storefront readability. */
export function themeContrastIssues(colors: ThemeColors): Array<'textBg' | 'primaryBtn' | 'secondaryStrip'> {
  const issues: Array<'textBg' | 'primaryBtn' | 'secondaryStrip'> = []
  if (!meetsContrastAA(colors.text, colors.background)) issues.push('textBg')
  if (!meetsContrastAA(contrastOnColor(colors.primary), colors.primary)) issues.push('primaryBtn')
  if (!meetsContrastAA(contrastOnColor(colors.secondary), colors.secondary)) issues.push('secondaryStrip')
  return issues
}

const SHADOW_CSS: Record<ShopShadow, string> = {
  NONE: 'none',
  SOFT: '0 2px 8px color-mix(in srgb, var(--shop-text) 6%, transparent)',
  MEDIUM: '0 6px 18px color-mix(in srgb, var(--shop-text) 10%, transparent)',
  STRONG: '0 12px 32px color-mix(in srgb, var(--shop-text) 16%, transparent)',
}

/** Build a safe linear-gradient from a structured token (never from raw CSS strings). */
export function gradientCss(gradient?: ThemeGradient | null): string | null {
  if (!gradient || !Array.isArray(gradient.stops) || gradient.stops.length < 2) return null
  const angle = Number.isFinite(gradient.angle) ? Math.max(0, Math.min(360, Math.round(gradient.angle))) : 180
  const stops = gradient.stops
    .filter((stop) => stop && /^#[0-9a-fA-F]{6}$/.test(stop.color) && Number.isFinite(stop.at))
    .map((stop) => `${stop.color} ${Math.max(0, Math.min(100, Math.round(stop.at)))}%`)
  if (stops.length < 2) return null
  return `linear-gradient(${angle}deg, ${stops.join(', ')})`
}

function styleCssVars(style: ThemeStyle, custom = false): Record<string, string> {
  const radius = Math.max(0, Math.min(32, style.radius))
  const gap = style.density === 'COMPACT' ? '12px' : '16px'
  const btnRadius =
    style.buttonShape === 'PILL' ? '999px'
    : style.buttonShape === 'SQUARE' ? '4px'
    : `${Math.max(4, Math.round(radius * 0.75))}px`
  const cardBorder =
    style.cardStyle === 'FLAT' ? 'transparent'
    : 'var(--shop-border)'
  // AG1 shadow tokens are CUSTOM-only; old templates keep the pre-AG1 card shadow exactly.
  const shadowToken = custom && SHOP_SHADOWS.includes(style.shadow as ShopShadow) ? (style.shadow as ShopShadow) : null
  // Product cards inside Mall panels use a thin border only (no stacked shadow) unless shadow token set.
  const cardShadow = shadowToken
    ? SHADOW_CSS[shadowToken]
    : style.cardStyle === 'SHADOW' ? '0 4px 12px color-mix(in srgb, var(--shop-text) 8%, transparent)'
    : 'none'
  const legacy = {
    '--shop-radius': `${radius}px`,
    '--shop-gap': gap,
    '--shop-btn-radius': btnRadius,
    '--shop-card-border': cardBorder,
    '--shop-card-shadow': cardShadow,
  }
  if (!custom) return legacy
  return {
    ...legacy,
    '--shop-elevation': cardShadow,
    '--shop-heading-shadow': style.headingTextShadow ? '0 1px 2px color-mix(in srgb, var(--shop-text) 25%, transparent)' : 'none',
  }
}

function optionalHex(value: unknown): string | undefined {
  return typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value) ? value : undefined
}

/** CUSTOM is the only template that reads AG1 tokens (extra colors, gradients, productCard, shadow). */
export function isCustomTemplate(templateKey?: string | null): boolean {
  return templateKey === 'CUSTOM'
}

/** Themes port: templates rendered by the dedicated .tsf-store tree (add FOOD_BEVERAGE here when its port lands). */
export const TSF_TREE_TEMPLATES: readonly ShopThemeTemplateKey[] = ['GENERAL', 'FASHION', 'FOOD_BEVERAGE']

export function usesTsfTree(templateKey?: string | null): boolean {
  return TSF_TREE_TEMPLATES.includes(templateKey as ShopThemeTemplateKey)
}

/** data-theme value for the .tsf-store tree ('general' | 'fashion'), undefined for legacy templates. */
export function tsfThemeName(templateKey?: string | null): string | undefined {
  if (!usesTsfTree(templateKey)) return undefined
  return templateKey === 'FOOD_BEVERAGE' ? 'food' : String(templateKey).toLowerCase()
}

/** Per-theme defaults for things non-CUSTOM templates cannot save (image ratio, category rail style). */
export function tsfThemeDefaults(templateKey?: string | null): { ratio: ShopImageRatio; categoryStyle: 'circle' | 'tile' | 'chip' } {
  if (templateKey === 'FASHION') return { ratio: 'PORTRAIT', categoryStyle: 'tile' }
  if (templateKey === 'FOOD_BEVERAGE') return { ratio: 'SQUARE', categoryStyle: 'chip' }
  return { ratio: 'SQUARE', categoryStyle: 'circle' }
}

/** Themes spec scales (GENERAL only): radius 0/4/12/20/999, spacing 4..96, type scale. */
const GENERAL_SCALE_VARS: Record<string, string> = {
  '--tsf-radius-none': '0px',
  '--tsf-radius-sm': '4px',
  '--tsf-radius-md': '12px',
  '--tsf-radius-lg': '20px',
  '--tsf-radius-pill': '999px',
  '--tsf-space-1': '4px',
  '--tsf-space-2': '8px',
  '--tsf-space-3': '12px',
  '--tsf-space-4': '16px',
  '--tsf-space-5': '24px',
  '--tsf-space-6': '32px',
  '--tsf-space-7': '48px',
  '--tsf-space-8': '64px',
  '--tsf-space-9': '96px',
  '--tsf-text-xs': '12px',
  '--tsf-text-sm': '14px',
  '--tsf-text-md': '16px',
  '--tsf-text-lg': '18px',
  '--tsf-text-xl': '22px',
  '--tsf-text-2xl': '28px',
  '--tsf-text-3xl': 'clamp(28px, 2.2vw + 18px, 44px)',
  '--tsf-leading-tight': '1.25',
  '--tsf-leading-body': '1.6',
  '--tsf-weight-heading': '700',
  '--tsf-max-width': '1440px',
}

/** GENERAL palette roles from the themes spec; derived from the 4 base colors when the shop has not saved them. */
function generalSpecCssVars(config: ThemeConfig): Record<string, string> {
  const { primary, background, text, accent } = config.colors
  // Same accent fallback as before (primary) so older GENERAL shops without an accent keep their tab / hover colors.
  const accentHex = optionalHex(accent) ?? primary
  const focus = optionalHex(config.colors.focus) ?? primary
  return {
    ...GENERAL_SCALE_VARS,
    '--shop-surface': `color-mix(in srgb, ${text} 5%, ${background})`,
    '--shop-muted': `color-mix(in srgb, ${text} 73%, ${background})`,
    '--shop-border': `color-mix(in srgb, ${text} 13%, ${background})`,
    '--shop-border-strong': optionalHex(config.colors.borderStrong) ?? `color-mix(in srgb, ${text} 55%, ${background})`,
    '--shop-sale': optionalHex(config.colors.sale) ?? '#c4271b',
    '--shop-focus': focus,
    '--shop-on-accent': contrastOnColor(accentHex),
    '--shop-primary-hover': `color-mix(in srgb, ${primary} 86%, #000)`,
  }
}

/**
 * CSS variables for the themed shell. Old templates (GENERAL/FASHION/FOOD_BEVERAGE) get exactly the
 * pre-AG1 variable set even if the config carries AG1 tokens; only CUSTOM emits the new variables.
 */
export function themeCssVars(config: ThemeConfig, templateKey?: ShopThemeTemplateKey | null): Record<string, string> {
  const custom = isCustomTemplate(templateKey)
  const { primary, secondary, background, text, accent } = config.colors
  const onPrimary = contrastOnColor(primary)
  const onSecondary = contrastOnColor(secondary)
  // Card panels keep the merchant background; page chrome is a soft tint so white themes still read as blocks.
  const bgLum = relativeLuminance(background)
  const pageBgMix = bgLum != null && bgLum > 0.92 ? 5 : 4
  const pageBg = `color-mix(in srgb, ${text} ${pageBgMix}%, ${background})`
  const accentHex = optionalHex(accent) ?? primary
  if (usesTsfTree(templateKey) || !templateKey) {
    return { ...legacyCssVars(), ...generalSpecCssVars(config) }
  }
  if (!custom) return legacyCssVars()
  function legacyCssVars(): Record<string, string> {
    return {
      '--shop-primary': primary,
      '--shop-secondary': secondary,
      '--shop-bg': background,
      '--shop-card-bg': background,
      '--shop-page-bg': pageBg,
      '--shop-text': text,
      '--shop-accent': accentHex,
      '--shop-font-heading': shopThemeFontStack(config.font),
      '--shop-font-body': shopThemeFontStack(themeBodyFont(config)),
      '--shop-font': shopThemeFontStack(themeBodyFont(config)),
      '--font-sans': shopThemeFontStack(themeBodyFont(config)),
      '--shop-muted': `color-mix(in srgb, ${text} 65%, ${background})`,
      '--shop-border': `color-mix(in srgb, ${text} 10%, ${background})`,
      '--shop-surface': `color-mix(in srgb, ${text} 4%, ${background})`,
      '--shop-on-primary': onPrimary,
      '--shop-primary-contrast': onPrimary,
      '--shop-on-secondary': onSecondary,
      '--shop-primary-soft': `color-mix(in srgb, ${primary} 12%, ${background})`,
      '--shop-tabs-h': '44px',
      ...styleCssVars(config.style ?? DEFAULT_STYLE, false),
    }
  }
  const surface = optionalHex(config.colors.surface) ?? `color-mix(in srgb, ${text} 4%, ${background})`
  const muted = optionalHex(config.colors.muted) ?? `color-mix(in srgb, ${text} 65%, ${background})`
  const border = optionalHex(config.colors.border) ?? `color-mix(in srgb, ${text} 10%, ${background})`
  const ctaBg = optionalHex(config.colors.ctaBackground) ?? primary
  const ctaText = optionalHex(config.colors.ctaText) ?? contrastOnColor(ctaBg)
  const heroGradient = gradientCss(config.gradients?.hero)
  const pageGradient = gradientCss(config.gradients?.page)
  const sectionGradient = gradientCss(config.gradients?.section)
  const product = config.productCard ?? {}
  const imageRatio =
    product.imageRatio === 'PORTRAIT' ? '3 / 4'
    : product.imageRatio === 'LANDSCAPE' ? '4 / 3'
    : '1 / 1'
  const ctaRadius =
    product.ctaStyle === 'PILL' ? '999px'
    : product.ctaStyle === 'SQUARE' ? '0px'
    : product.ctaStyle === 'NONE' ? '0px'
    : 'var(--shop-btn-radius)'
  return {
    '--shop-primary': primary,
    '--shop-secondary': secondary,
    '--shop-bg': background,
    '--shop-card-bg': background,
    '--shop-page-bg': pageGradient || pageBg,
    '--shop-text': text,
    '--shop-accent': accentHex,
    '--shop-font-heading': shopThemeFontStack(config.font),
    '--shop-font-body': shopThemeFontStack(themeBodyFont(config)),
    '--shop-font': shopThemeFontStack(themeBodyFont(config)),
    '--font-sans': shopThemeFontStack(themeBodyFont(config)),
    '--shop-muted': muted,
    '--shop-border': border,
    '--shop-surface': surface,
    '--shop-cta-bg': ctaBg,
    '--shop-cta-text': ctaText,
    '--shop-on-primary': onPrimary,
    '--shop-primary-contrast': onPrimary,
    '--shop-on-secondary': onSecondary,
    '--shop-primary-soft': `color-mix(in srgb, ${primary} 12%, ${background})`,
    '--shop-tabs-h': '44px',
    '--shop-product-image-ratio': imageRatio,
    '--shop-product-cta-radius': ctaRadius,
    '--shop-product-title-align': product.titleAlign === 'CENTER' ? 'center' : 'left',
    ...(heroGradient ? { '--shop-gradient-hero': heroGradient } : {}),
    ...(pageGradient ? { '--shop-gradient-page': pageGradient } : {}),
    ...(sectionGradient ? { '--shop-gradient-section': sectionGradient } : {}),
    ...styleCssVars(config.style ?? DEFAULT_STYLE, true),
  }
}

export function themeBodyFont(config: { font: ShopThemeFontKey; fontBody?: ShopThemeFontKey }): ShopThemeFontKey {
  return config.fontBody ?? config.font
}

function resolveOptionalFont(raw: unknown): ShopThemeFontKey | undefined {
  const key = typeof raw === 'string' ? raw.trim().toUpperCase() : ''
  return (SHOP_THEME_FONTS as readonly string[]).includes(key) ? (key as ShopThemeFontKey) : undefined
}

function resolveBrand(raw: unknown): ThemeBrand | undefined {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return undefined
  const row = raw as Record<string, unknown>
  const pick = (value: unknown): HeroTextAlign | undefined => {
    const key = typeof value === 'string' ? value.trim().toUpperCase() : ''
    return key === 'LEFT' || key === 'CENTER' ? key : undefined
  }
  const out: ThemeBrand = {}
  const logo = pick(row.logoPosition)
  const align = pick(row.textAlign)
  if (logo) out.logoPosition = logo
  if (align) out.textAlign = align
  return Object.keys(out).length ? out : undefined
}

function resolveFont(raw: unknown, fallback: ShopThemeFontKey): ShopThemeFontKey {
  const value = typeof raw === 'string' ? raw.trim().toUpperCase() : ''
  return SHOP_THEME_FONTS.includes(value as ShopThemeFontKey) ? (value as ShopThemeFontKey) : fallback
}

/** MEDIA_FULL is CUSTOM-only: every other template silently falls back (FULL) like the backend does. */
function resolveHeroLayout(raw: unknown, fallback: HeroLayout, custom = false): HeroLayout {
  const value = typeof raw === 'string' ? raw.trim().toUpperCase() : ''
  if (!custom && value === 'MEDIA_FULL') return 'FULL'
  const allowed = custom ? CUSTOM_HERO_VARIANTS : HERO_VARIANTS
  return allowed.has(value) ? (value as HeroLayout) : (!custom && fallback === 'MEDIA_FULL' ? 'FULL' : fallback)
}

function resolveSectionVariant(key: ShopSectionKey, raw: unknown, fallback: SectionVariant, custom = false): SectionVariant {
  if (raw == null || raw === '') return fallback
  const value = String(raw).trim().toUpperCase()
  if (key === 'HERO') {
    if (!custom && value === 'MEDIA_FULL') return 'FULL'
    return (custom ? CUSTOM_HERO_VARIANTS : HERO_VARIANTS).has(value) ? (value as HeroLayout) : fallback
  }
  if (key === 'FEATURED' || key === 'BESTSELLERS' || key === 'NEW_ARRIVALS' || key === 'CATALOG') {
    return CATALOG_VARIANTS.has(value) ? (value as CatalogVariant) : fallback
  }
  return null
}

/** AG2: mediaId (theme media library) is kept for CUSTOM only; every other template drops it. */
function resolveBanners(raw: unknown, fallback: ThemeBanner[], custom = false): ThemeBanner[] {
  if (!Array.isArray(raw)) return structuredClone(fallback)
  return raw
    .map((row) => {
      if (!row || typeof row !== 'object') return null
      const item = row as Record<string, unknown>
      const imageUrl = typeof item.imageUrl === 'string' ? item.imageUrl.trim() : ''
      const mediaId = custom && typeof item.mediaId === 'string' ? item.mediaId.trim() : ''
      if (!imageUrl && !mediaId) return null
      return {
        imageUrl,
        ...(mediaId ? { mediaId } : {}),
        title: typeof item.title === 'string' ? item.title : '',
        link: typeof item.link === 'string' ? item.link : '',
      } satisfies ThemeBanner
    })
    .filter(Boolean) as ThemeBanner[]
}

function resolveTrust(raw: unknown, fallback: ThemeTrust): ThemeTrust {
  if (!raw || typeof raw !== 'object') return structuredClone(fallback)
  const incoming = raw as Record<string, unknown>
  const itemsRaw = Array.isArray(incoming.items) ? incoming.items : fallback.items
  const items: ThemeTrustItem[] = itemsRaw.map((row, index) => {
    const base = fallback.items[index] || fallback.items[0]
    if (!row || typeof row !== 'object') return { ...base }
    const item = row as Record<string, unknown>
    const iconRaw = String(item.icon || base.icon).toUpperCase()
    const icon = SHOP_TRUST_ICONS.includes(iconRaw as ShopTrustIcon) ? (iconRaw as ShopTrustIcon) : base.icon
    return {
      icon,
      label: typeof item.label === 'string' ? item.label : base.label,
      enabled: typeof item.enabled === 'boolean' ? item.enabled : true,
      ...(typeof item.sub === 'string' ? { sub: item.sub } : {}),
    }
  })
  return {
    enabled: typeof incoming.enabled === 'boolean' ? incoming.enabled : fallback.enabled,
    items: items.length ? items : structuredClone(fallback.items),
  }
}

function resolveStyle(raw: unknown, fallback: ThemeStyle): ThemeStyle {
  const incoming = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : null
  if (!incoming) return { ...fallback }
  const radiusRaw = incoming.radius
  let radius = fallback.radius
  if (typeof radiusRaw === 'number' && Number.isFinite(radiusRaw)) radius = Math.max(0, Math.min(32, Math.round(radiusRaw)))
  else if (typeof radiusRaw === 'string' && /^\d+$/.test(radiusRaw.trim())) {
    radius = Math.max(0, Math.min(32, Number.parseInt(radiusRaw.trim(), 10)))
  }
  const cardStyle = SHOP_CARD_STYLES.includes(String(incoming.cardStyle || '').toUpperCase() as ShopCardStyle)
    ? (String(incoming.cardStyle).toUpperCase() as ShopCardStyle)
    : fallback.cardStyle
  const density = SHOP_DENSITIES.includes(String(incoming.density || '').toUpperCase() as ShopDensity)
    ? (String(incoming.density).toUpperCase() as ShopDensity)
    : fallback.density
  const buttonShape = SHOP_BUTTON_SHAPES.includes(String(incoming.buttonShape || '').toUpperCase() as ShopButtonShape)
    ? (String(incoming.buttonShape).toUpperCase() as ShopButtonShape)
    : fallback.buttonShape
  const shadowRaw = String(incoming.shadow || fallback.shadow || '').toUpperCase()
  const shadow = SHOP_SHADOWS.includes(shadowRaw as ShopShadow) ? (shadowRaw as ShopShadow) : fallback.shadow
  const headingTextShadow = typeof incoming.headingTextShadow === 'boolean'
    ? incoming.headingTextShadow
    : fallback.headingTextShadow
  return {
    radius, cardStyle, density, buttonShape,
    ...(shadow ? { shadow } : {}),
    ...(typeof headingTextShadow === 'boolean' ? { headingTextShadow } : {}),
  }
}

function resolveGradient(raw: unknown): ThemeGradient | undefined {
  if (!raw || typeof raw !== 'object') return undefined
  const row = raw as Record<string, unknown>
  let angle = 180
  if (typeof row.angle === 'number' && Number.isFinite(row.angle)) angle = Math.max(0, Math.min(360, Math.round(row.angle)))
  else if (typeof row.angle === 'string' && /^\d+$/.test(row.angle.trim())) {
    angle = Math.max(0, Math.min(360, Number.parseInt(row.angle.trim(), 10)))
  }
  if (!Array.isArray(row.stops)) return undefined
  const stops = row.stops
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const stop = item as Record<string, unknown>
      const color = optionalHex(stop.color)
      if (!color) return null
      let at = 0
      if (typeof stop.at === 'number' && Number.isFinite(stop.at)) at = Math.max(0, Math.min(100, Math.round(stop.at)))
      else if (typeof stop.at === 'string' && /^\d+$/.test(stop.at.trim())) {
        at = Math.max(0, Math.min(100, Number.parseInt(stop.at.trim(), 10)))
      } else return null
      return { color, at } satisfies ThemeGradientStop
    })
    .filter(Boolean) as ThemeGradientStop[]
  if (stops.length < 2 || stops.length > 4) return undefined
  return { angle, stops }
}

function resolveGradients(raw: unknown, fallback?: ThemeGradients): ThemeGradients | undefined {
  const incoming = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : null
  const base = fallback ?? {}
  const hero = resolveGradient(incoming?.hero) ?? base.hero
  const page = resolveGradient(incoming?.page) ?? base.page
  const section = resolveGradient(incoming?.section) ?? base.section
  if (!hero && !page && !section) return undefined
  return { ...(hero ? { hero } : {}), ...(page ? { page } : {}), ...(section ? { section } : {}) }
}

function resolveProductCard(raw: unknown, fallback?: ThemeProductCard): ThemeProductCard | undefined {
  const incoming = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : null
  const base = fallback ?? {}
  if (!incoming && !fallback) return undefined
  const imageRatioRaw = String(incoming?.imageRatio || base.imageRatio || '').toUpperCase()
  const ctaStyleRaw = String(incoming?.ctaStyle || base.ctaStyle || '').toUpperCase()
  const titleAlignRaw = String(incoming?.titleAlign || base.titleAlign || '').toUpperCase()
  const next: ThemeProductCard = {
    imageRatio: SHOP_IMAGE_RATIOS.includes(imageRatioRaw as ShopImageRatio) ? (imageRatioRaw as ShopImageRatio) : base.imageRatio,
    ctaStyle: SHOP_CTA_STYLES.includes(ctaStyleRaw as ShopCtaStyle) ? (ctaStyleRaw as ShopCtaStyle) : base.ctaStyle,
    titleAlign: SHOP_TITLE_ALIGNS.includes(titleAlignRaw as ShopTitleAlign) ? (titleAlignRaw as ShopTitleAlign) : base.titleAlign,
    showPrice: typeof incoming?.showPrice === 'boolean' ? incoming.showPrice : base.showPrice,
    edgeToEdge: typeof incoming?.edgeToEdge === 'boolean' ? incoming.edgeToEdge : base.edgeToEdge,
  }
  if (next.imageRatio == null && next.ctaStyle == null && next.titleAlign == null
    && next.showPrice == null && next.edgeToEdge == null) return undefined
  return next
}

function resolveSectionSettings(raw: unknown, fallback?: ThemeSectionSettings): ThemeSectionSettings | undefined {
  const incoming = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : null
  if (!incoming && !fallback) return undefined
  const out: ThemeSectionSettings = {}
  for (const key of SHOP_SECTION_KEYS) {
    const rowRaw = incoming?.[key]
    const base = fallback?.[key]
    const row = rowRaw && typeof rowRaw === 'object' ? (rowRaw as Record<string, unknown>) : null
    if (!row && !base) continue
    const backgroundRaw = String(row?.background || base?.background || '').toUpperCase()
    const alignRaw = String(row?.align || base?.align || '').toUpperCase()
    const paddingRaw = String(row?.paddingY || base?.paddingY || '').toUpperCase()
    let columns = base?.columns
    const columnsRaw = row?.columns
    if (typeof columnsRaw === 'number' && [2, 3, 4, 5].includes(columnsRaw)) columns = columnsRaw as 2 | 3 | 4 | 5
    else if (typeof columnsRaw === 'string' && /^[2345]$/.test(columnsRaw.trim())) {
      columns = Number.parseInt(columnsRaw.trim(), 10) as 2 | 3 | 4 | 5
    }
    const setting: ThemeSectionSetting = {
      background: SHOP_SECTION_BACKGROUNDS.includes(backgroundRaw as ShopSectionBackground)
        ? (backgroundRaw as ShopSectionBackground) : base?.background,
      align: SHOP_SECTION_ALIGNS.includes(alignRaw as ShopSectionAlign)
        ? (alignRaw as ShopSectionAlign) : base?.align,
      paddingY: SHOP_PADDING_Y.includes(paddingRaw as ShopPaddingY)
        ? (paddingRaw as ShopPaddingY) : base?.paddingY,
      ...(columns != null && ['FEATURED', 'BESTSELLERS', 'NEW_ARRIVALS', 'CATALOG'].includes(key) ? { columns } : {}),
    }
    if (setting.background || setting.align || setting.paddingY || setting.columns != null) out[key] = setting
  }
  return Object.keys(out).length ? out : undefined
}

/* ????????? AG3: layout.blocks ????????? */

/* AG5: hero media extras, header, footer, fontCustom (CUSTOM only; mirrors the backend whitelist) */

function recordOf(raw: unknown): Record<string, unknown> | null {
  return raw && typeof raw === 'object' && !Array.isArray(raw) ? (raw as Record<string, unknown>) : null
}

function trimmedText(raw: unknown): string | undefined {
  return typeof raw === 'string' && raw.trim() ? raw.trim() : undefined
}

/** Integer clamped to [min, max]; numeric strings are accepted; anything else is undefined. */
export function clampInt(raw: unknown, min: number, max: number): number | undefined {
  const num = typeof raw === 'number' ? raw
    : typeof raw === 'string' && /^-?\d+$/.test(raw.trim()) ? Number.parseInt(raw.trim(), 10) : Number.NaN
  return Number.isFinite(num) ? Math.max(min, Math.min(max, Math.round(num))) : undefined
}

function resolveHeroSlides(raw: unknown): ThemeHeroSlide[] {
  if (!Array.isArray(raw)) return []
  const out: ThemeHeroSlide[] = []
  for (const row of raw) {
    if (out.length >= MAX_HERO_SLIDES) break
    if (!row || typeof row !== 'object') continue
    const item = row as Record<string, unknown>
    const slideMediaId = typeof item.slideMediaId === 'string' ? item.slideMediaId.trim() : ''
    const imageUrl = typeof item.imageUrl === 'string' ? item.imageUrl.trim() : ''
    if (!slideMediaId || out.some((slide) => slide.slideMediaId === slideMediaId)) continue
    out.push({ slideMediaId, imageUrl })
  }
  return out
}

function resolveHeroExtras(raw: Record<string, unknown>): Partial<ThemeHero> {
  const out: Partial<ThemeHero> = {}
  const bg = recordOf(raw.background)
  const type = typeof bg?.type === 'string' ? bg.type.trim().toUpperCase() : ''
  if (bg && (HERO_BACKGROUND_TYPES as readonly string[]).includes(type)) {
    const mediaId = type === 'GRADIENT' ? undefined : trimmedText(bg.mediaId)
    const posterMediaId = type === 'VIDEO' ? trimmedText(bg.posterMediaId) : undefined
    const url = mediaId ? trimmedText(bg.url) : undefined
    const posterUrl = posterMediaId ? trimmedText(bg.posterUrl) : undefined
    out.background = {
      type: type as HeroBackgroundType,
      ...(mediaId ? { mediaId } : {}),
      ...(posterMediaId ? { posterMediaId } : {}),
      ...(url ? { url } : {}),
      ...(posterUrl ? { posterUrl } : {}),
    }
  }
  const overlay = clampInt(raw.overlay, ...HERO_RANGES.overlay)
  if (overlay != null) out.overlay = overlay
  const heightVh = clampInt(raw.heightVh, ...HERO_RANGES.heightVh)
  if (heightVh != null) out.heightVh = heightVh
  const align = typeof raw.textAlign === 'string' ? raw.textAlign.trim().toUpperCase() : ''
  if ((HERO_TEXT_ALIGNS as readonly string[]).includes(align)) out.textAlign = align as HeroTextAlign
  const size = typeof raw.headlineSize === 'string' ? raw.headlineSize.trim().toUpperCase() : ''
  if ((HERO_HEADLINE_SIZES as readonly string[]).includes(size)) out.headlineSize = size as HeroHeadlineSize
  if (Array.isArray(raw.stickers)) {
    const stickers: ThemeHeroSticker[] = []
    for (const item of raw.stickers) {
      const row = recordOf(item)
      const mediaId = row ? trimmedText(row.mediaId) : undefined
      if (!row || !mediaId) continue
      const url = trimmedText(row.url)
      stickers.push({
        mediaId,
        x: clampInt(row.x, ...HERO_RANGES.x) ?? 50,
        y: clampInt(row.y, ...HERO_RANGES.y) ?? 50,
        size: clampInt(row.size, ...HERO_RANGES.size) ?? 120,
        rotate: clampInt(row.rotate, ...HERO_RANGES.rotate) ?? 0,
        hideOnMobile: row.hideOnMobile === true,
        ...(url ? { url } : {}),
      })
      if (stickers.length >= MAX_HERO_STICKERS) break
    }
    out.stickers = stickers
  }
  const cta = recordOf(raw.ctaSecondary)
  const label = cta ? trimmedText(cta.label) : undefined
  if (cta && label) out.ctaSecondary = { label, link: trimmedText(cta.link) ?? '' }
  return out
}

function resolveChromeLinks(raw: unknown, max: number): ThemeChromeLink[] {
  if (!Array.isArray(raw)) return []
  const out: ThemeChromeLink[] = []
  for (const item of raw) {
    const row = recordOf(item)
    if (!row) continue
    const label = resolveLocalized(row.label)
    const link = trimmedText(row.link)
    if (!label && !link) continue
    out.push({ label: label ?? { th: '' }, link: link ?? '' })
    if (out.length >= max) break
  }
  return out
}

function resolveChromeBackground(row: Record<string, unknown>) {
  const raw = typeof row.background === 'string' ? row.background.trim().toUpperCase() : ''
  const background = SHOP_BLOCK_BACKGROUNDS.includes(raw as ShopBlockBackground) ? (raw as ShopBlockBackground) : 'NONE'
  const gradient = resolveGradient(row.gradient)
  const mediaId = background === 'IMAGE' ? trimmedText(row.backgroundMediaId) : undefined
  const backgroundUrl = mediaId ? trimmedText(row.backgroundUrl) : undefined
  const overlay = clampInt(row.overlay, ...HERO_RANGES.overlay)
  return {
    background,
    ...(gradient ? { gradient } : {}),
    ...(mediaId ? { backgroundMediaId: mediaId } : {}),
    ...(backgroundUrl ? { backgroundUrl } : {}),
    ...(overlay != null ? { overlay } : {}),
  }
}

/** `undefined` when the shop has no custom header (the storefront keeps the existing mall header / tabs). */
function resolveHeader(raw: unknown): ThemeHeader | undefined {
  const row = recordOf(raw)
  if (!row) return undefined
  const layoutRaw = typeof row.layout === 'string' ? row.layout.trim().toUpperCase() : ''
  const bar = recordOf(row.announcementBar)
  const text = bar ? resolveLocalized(bar.text) : undefined
  const barLink = bar ? trimmedText(bar.link) : undefined
  return {
    layout: HEADER_LAYOUTS.includes(layoutRaw as HeaderLayout) ? (layoutRaw as HeaderLayout) : 'LOGO_LEFT',
    ...resolveChromeBackground(row),
    sticky: row.sticky === true,
    menu: resolveChromeLinks(row.menu, MAX_MENU_ITEMS),
    showSearch: row.showSearch === true,
    showSocialIcons: row.showSocialIcons === true,
    ...(bar && text ? {
      announcementBar: {
        text,
        ...(barLink ? { link: barLink } : {}),
        backgroundColor: optionalHex(bar.backgroundColor) ?? '#111111',
        textColor: optionalHex(bar.textColor) ?? '#ffffff',
      },
    } : {}),
  }
}

function resolveFooter(raw: unknown): ThemeFooter | undefined {
  const row = recordOf(raw)
  if (!row) return undefined
  const columns: ThemeFooterColumn[] = []
  for (const item of Array.isArray(row.columns) ? row.columns : []) {
    const column = recordOf(item)
    if (!column) continue
    const title = resolveLocalized(column.title)
    const links = resolveChromeLinks(column.links, MAX_COLUMN_LINKS)
    if (!title && links.length === 0) continue
    columns.push({ ...(title ? { title } : {}), links })
    if (columns.length >= MAX_FOOTER_COLUMNS) break
  }
  const about = resolveLocalized(row.about)
  const copyright = trimmedText(row.copyright)
  return {
    columns,
    ...(about ? { about } : {}),
    showContacts: row.showContacts === true,
    showSocial: row.showSocial === true,
    ...resolveChromeBackground(row),
    ...(copyright ? { copyright } : {}),
  }
}

function resolveFontCustom(raw: unknown, font: ShopThemeFontKey): ThemeFontCustom | undefined {
  const row = recordOf(raw)
  if (!row) return undefined
  const headingMediaId = trimmedText(row.headingMediaId)
  const bodyMediaId = trimmedText(row.bodyMediaId)
  if (!headingMediaId && !bodyMediaId) return undefined
  const headingUrl = headingMediaId ? trimmedText(row.headingUrl) : undefined
  const bodyUrl = bodyMediaId ? trimmedText(row.bodyUrl) : undefined
  return {
    ...(headingMediaId ? { headingMediaId } : {}),
    ...(bodyMediaId ? { bodyMediaId } : {}),
    ...(headingUrl ? { headingUrl } : {}),
    ...(bodyUrl ? { bodyUrl } : {}),
    fallback: resolveFont(row.fallback, font),
  }
}

function defaultBlockVariant(type: ShopBlockType): SectionVariant {
  if (type === 'HERO') return 'FULL'
  return PRODUCT_BLOCK_TYPES.includes(type) ? 'GRID' : null
}

function legacyBlockId(type: ShopBlockType): string {
  if (type === 'HERO') return 'legacy-hero'
  if (type === 'FEATURED') return 'legacy-featured'
  return `sec-${type.toLowerCase().replace(/_/g, '-')}`
}

/** `base`, then `base-2`, `base-3`, ? until it is not in `used` (and fits the 40-char id limit). */
export function uniqueBlockId(base: string, used: Set<string>): string {
  const root = base.slice(0, 36)
  let id = base.slice(0, 40)
  let n = 2
  while (used.has(id)) id = `${root}-${n++}`
  return id
}

export function defaultBlockSettings(type: ShopBlockType): BlockSettings {
  return {
    background: 'NONE',
    align: 'LEFT',
    paddingY: 'NORMAL',
    fullWidth: false,
    ...(PRODUCT_BLOCK_TYPES.includes(type) ? { columns: 3 as const } : {}),
  }
}

/** A fresh block for the editor ("add block"); id is unique among `usedIds`. */
export function createLayoutBlock(type: ShopBlockType, usedIds: Iterable<string>): LayoutBlock {
  const used = new Set(usedIds)
  const variant = defaultBlockVariant(type)
  return {
    id: uniqueBlockId(`blk-${type.toLowerCase().replace(/_/g, '-')}`, used),
    type,
    enabled: true,
    ...(variant ? { variant } : {}),
    settings: defaultBlockSettings(type),
    content: defaultBlockContent(type),
  }
}

/** Mirror of the backend migration: legacy sections + sectionSettings ? blocks (same order, same visuals). */
export function synthesizeBlocks(
  sections: SectionConfig[],
  sectionSettings: ThemeSectionSettings | undefined,
  heroLayout: HeroLayout,
): LayoutBlock[] {
  const used = new Set<string>()
  return sections.slice(0, MAX_LAYOUT_BLOCKS).map((section) => {
    const id = uniqueBlockId(legacyBlockId(section.key), used)
    used.add(id)
    const legacy = sectionSettings?.[section.key]
    const settings: BlockSettings = {
      background: legacy?.background ?? 'NONE',
      align: legacy?.align ?? 'LEFT',
      paddingY: legacy?.paddingY ?? 'NORMAL',
      fullWidth: false,
      ...(legacy?.columns != null && PRODUCT_BLOCK_TYPES.includes(section.key) ? { columns: legacy.columns } : {}),
    }
    const variant = section.key === 'HERO'
      ? resolveSectionVariant('HERO', section.variant, heroLayout, true)
      : resolveSectionVariant(section.key, section.variant, defaultBlockVariant(section.key), true)
    return {
      id,
      type: section.key,
      enabled: section.enabled,
      ...(variant ? { variant } : {}),
      settings,
      content: {},
    }
  })
}

function resolveBlockSettings(raw: unknown, type: ShopBlockType): BlockSettings {
  const row = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : {}
  const upper = (value: unknown) => (typeof value === 'string' ? value.trim().toUpperCase() : '')
  const background = SHOP_BLOCK_BACKGROUNDS.includes(upper(row.background) as ShopBlockBackground)
    ? (upper(row.background) as ShopBlockBackground) : 'NONE'
  const align = SHOP_SECTION_ALIGNS.includes(upper(row.align) as ShopSectionAlign) ? (upper(row.align) as ShopSectionAlign) : 'LEFT'
  const paddingY = SHOP_PADDING_Y.includes(upper(row.paddingY) as ShopPaddingY) ? (upper(row.paddingY) as ShopPaddingY) : 'NORMAL'
  const gradient = resolveGradient(row.gradient)
  const columnsNum = typeof row.columns === 'number' ? row.columns
    : typeof row.columns === 'string' && /^\d+$/.test(row.columns.trim()) ? Number.parseInt(row.columns.trim(), 10) : NaN
  const overlayNum = typeof row.overlay === 'number' ? row.overlay
    : typeof row.overlay === 'string' && /^\d+$/.test(row.overlay.trim()) ? Number.parseInt(row.overlay.trim(), 10) : NaN
  const mediaId = background === 'IMAGE' && typeof row.backgroundMediaId === 'string' && row.backgroundMediaId.trim()
    ? row.backgroundMediaId.trim() : undefined
  const backgroundUrl = mediaId && typeof row.backgroundUrl === 'string' && row.backgroundUrl.trim() ? row.backgroundUrl.trim() : undefined
  return {
    background,
    ...(gradient ? { gradient } : {}),
    ...(mediaId ? { backgroundMediaId: mediaId } : {}),
    ...(backgroundUrl ? { backgroundUrl } : {}),
    ...(Number.isFinite(overlayNum) ? { overlay: Math.max(0, Math.min(80, Math.round(overlayNum))) } : {}),
    align,
    ...([2, 3, 4, 5].includes(columnsNum) && PRODUCT_BLOCK_TYPES.includes(type) ? { columns: columnsNum as 2 | 3 | 4 | 5 } : {}),
    paddingY,
    fullWidth: row.fullWidth === true,
  }
}

/** Sanitize incoming blocks; falls back to synthesizing from the legacy sections when none are usable. */
function resolveLayout(
  raw: unknown,
  sections: SectionConfig[],
  sectionSettings: ThemeSectionSettings | undefined,
  heroLayout: HeroLayout,
): ThemeLayout {
  const list = raw && typeof raw === 'object' && Array.isArray((raw as { blocks?: unknown }).blocks)
    ? ((raw as { blocks: unknown[] }).blocks)
    : []
  const used = new Set<string>()
  const singletons = new Set<ShopBlockType>()
  const blocks: LayoutBlock[] = []
  for (const item of list) {
    if (blocks.length >= MAX_LAYOUT_BLOCKS) break
    if (!item || typeof item !== 'object') continue
    const row = item as Record<string, unknown>
    const typeRaw = typeof row.type === 'string' ? row.type.trim().toUpperCase() : ''
    if (!SHOP_BLOCK_TYPES.includes(typeRaw as ShopBlockType)) continue
    const type = typeRaw as ShopBlockType
    if (SINGLETON_BLOCK_TYPES.includes(type)) {
      if (singletons.has(type)) continue
      singletons.add(type)
    }
    const idRaw = typeof row.id === 'string' ? row.id : ''
    const id = uniqueBlockId(LAYOUT_BLOCK_ID_PATTERN.test(idRaw) ? idRaw : `blk-${type.toLowerCase().replace(/_/g, '-')}`, used)
    used.add(id)
    const variant = isSectionKey(type) ? resolveSectionVariant(type, row.variant, defaultBlockVariant(type), true) : null
    blocks.push({
      id,
      type,
      enabled: typeof row.enabled === 'boolean' ? row.enabled : true,
      ...(variant ? { variant } : {}),
      settings: resolveBlockSettings(row.settings, type),
      content: resolveBlockContent(type, row.content),
    })
  }
  return { blocks: blocks.length ? blocks : synthesizeBlocks(sections, sectionSettings, heroLayout) }
}

/** CUSTOM configs always carry blocks (used when the editor switches a non-CUSTOM draft to CUSTOM). */
export function withSynthesizedLayout(config: ThemeConfig): ThemeConfig {
  if (config.layout?.blocks?.length) return config
  return { ...config, layout: { blocks: synthesizeBlocks(config.sections, config.sectionSettings, config.hero.layout) } }
}

/** Derive the legacy `sections` list from blocks (first-seen type order, missing types appended disabled). */
export function sectionsFromBlocks(blocks: LayoutBlock[], previous: SectionConfig[]): SectionConfig[] {
  const out: SectionConfig[] = []
  const seen = new Set<ShopSectionKey>()
  const prev = new Map(previous.map((row) => [row.key, row]))
  const variantOf = (key: ShopSectionKey, block?: LayoutBlock): SectionVariant => {
    if (!sectionSupportsVariant(key)) return null
    return resolveSectionVariant(key, block?.variant, prev.get(key)?.variant ?? defaultBlockVariant(key), true)
  }
  for (const block of blocks) {
    // AG4: block-only content types never map to a legacy section.
    if (!isSectionKey(block.type) || seen.has(block.type)) continue
    seen.add(block.type)
    out.push({ key: block.type, enabled: block.enabled, variant: variantOf(block.type, block) })
  }
  for (const key of SHOP_SECTION_KEYS) {
    if (!seen.has(key)) out.push({ key, enabled: false, variant: variantOf(key) })
  }
  return out
}

type NormalizeOptions = { skipRegistry?: boolean }

/**
 * Normalize a theme payload from the API.
 * Never silently drop template/font/variant/style values the backend accepted.
 * Gap-fill only from registered presets (or minimal fallback).
 */
export function normalizeTheme(raw?: Partial<ShopTheme> | null, options: NormalizeOptions = {}): ShopTheme {
  const fallback = minimalFallbackTheme()
  if (!raw) return structuredClone(fallback)

  const templateKey = SHOP_THEME_TEMPLATES.includes(raw.templateKey as ShopThemeTemplateKey)
    ? (raw.templateKey as ShopThemeTemplateKey)
    : fallback.templateKey

  const base = !options.skipRegistry && presetRegistry[templateKey]
    ? presetRegistry[templateKey]!
    : fallback.config

  const incoming = raw.config
  if (!incoming || typeof incoming !== 'object') {
    const cloned = structuredClone(base)
    if (!isCustomTemplate(templateKey)) {
      delete cloned.layout
      return { templateKey, config: cloned }
    }
    return { templateKey, config: withSynthesizedLayout(cloned) }
  }

  const custom = isCustomTemplate(templateKey)
  const colorIn = (incoming.colors || {}) as ThemeColors
  const colorBase = base.colors as ThemeColors
  const pickColor = (key: keyof ThemeColors, required = false) => {
    const value = optionalHex(colorIn[key]) ?? optionalHex(colorBase[key])
    if (required) return value || (colorBase[key] as string) || '#000000'
    return value
  }
  // Old templates: pre-AG1 color resolution, and AG1 token keys are ignored even if present.
  const legacyColor = (key: 'primary' | 'secondary' | 'background' | 'text') =>
    typeof colorIn[key] === 'string' && colorIn[key] ? (colorIn[key] as string) : (colorBase[key] as string)
  const colors: ThemeColors = !custom ? {
    primary: legacyColor('primary'),
    secondary: legacyColor('secondary'),
    background: legacyColor('background'),
    text: legacyColor('text'),
    ...(pickColor('accent') ? { accent: pickColor('accent') } : {}),
    // tsf-tree templates (GENERAL / FASHION) keep the spec palette roles; FOOD_BEVERAGE ignores them like other AG1 keys.
    ...(usesTsfTree(templateKey) && optionalHex(colorIn.borderStrong) ? { borderStrong: optionalHex(colorIn.borderStrong) } : {}),
    ...(usesTsfTree(templateKey) && optionalHex(colorIn.sale) ? { sale: optionalHex(colorIn.sale) } : {}),
    ...(usesTsfTree(templateKey) && optionalHex(colorIn.focus) ? { focus: optionalHex(colorIn.focus) } : {}),
  } : {
    primary: pickColor('primary', true)!,
    secondary: pickColor('secondary', true)!,
    background: pickColor('background', true)!,
    text: pickColor('text', true)!,
    ...(pickColor('accent') ? { accent: pickColor('accent') } : {}),
    ...(pickColor('surface') ? { surface: pickColor('surface') } : {}),
    ...(pickColor('muted') ? { muted: pickColor('muted') } : {}),
    ...(pickColor('ctaBackground') ? { ctaBackground: pickColor('ctaBackground') } : {}),
    ...(pickColor('ctaText') ? { ctaText: pickColor('ctaText') } : {}),
    ...(pickColor('border') ? { border: pickColor('border') } : {}),
  }

  const font = resolveFont(incoming.font, base.font)
  const brandRaw = incoming as unknown as Record<string, unknown>
  const fontBody = resolveOptionalFont(brandRaw.fontBody)
  const brand = resolveBrand(brandRaw.brand)
  const resolvedStyle = resolveStyle(incoming.style, base.style ?? DEFAULT_STYLE)
  const style: ThemeStyle = custom ? resolvedStyle : {
    radius: resolvedStyle.radius,
    cardStyle: resolvedStyle.cardStyle,
    density: resolvedStyle.density,
    buttonShape: resolvedStyle.buttonShape,
  }
  const banners = resolveBanners(incoming.banners, base.banners ?? fallback.config.banners, custom)
  const trust = resolveTrust(incoming.trust, base.trust ?? fallback.config.trust)
  const gradients = custom ? resolveGradients(incoming.gradients, base.gradients) : undefined
  const productCard = custom ? resolveProductCard(incoming.productCard, base.productCard) : undefined
  const sectionSettings = custom ? resolveSectionSettings(incoming.sectionSettings, base.sectionSettings) : undefined
  const heroIncoming = incoming.hero || {}
  const hero: ThemeHero = {
    // Themes port: GENERAL may use MEDIA_FULL (poster) too; FASHION / FOOD_BEVERAGE fall back to FULL.
    layout: resolveHeroLayout(heroIncoming.layout ?? (incoming.sections?.find((s) => s.key === 'HERO')?.variant), base.hero.layout, custom || templateKey === 'GENERAL'),
    title: typeof heroIncoming.title === 'string' ? heroIncoming.title : base.hero.title,
    subtitle: typeof heroIncoming.subtitle === 'string' ? heroIncoming.subtitle : base.hero.subtitle,
    ctaLabel: typeof heroIncoming.ctaLabel === 'string' ? heroIncoming.ctaLabel : base.hero.ctaLabel,
    ctaLink: typeof heroIncoming.ctaLink === 'string' ? heroIncoming.ctaLink : base.hero.ctaLink,
    slides: resolveHeroSlides('slides' in heroIncoming ? heroIncoming.slides : base.hero.slides),
    ...(templateKey === 'GENERAL' ? {
      eyebrow: typeof heroIncoming.eyebrow === 'string' ? heroIncoming.eyebrow : (base.hero.eyebrow ?? ''),
      ctaSecondaryLabel: typeof heroIncoming.ctaSecondaryLabel === 'string' ? heroIncoming.ctaSecondaryLabel : (base.hero.ctaSecondaryLabel ?? ''),
      ctaSecondaryLink: typeof heroIncoming.ctaSecondaryLink === 'string' ? heroIncoming.ctaSecondaryLink : (base.hero.ctaSecondaryLink ?? ''),
      badges: plainTextList((heroIncoming as { badges?: unknown }).badges ?? base.hero.badges, 3),
    } : {}),
    // AG5: hero media extras are CUSTOM-only (other templates never carry or read them).
    ...(custom ? resolveHeroExtras(heroIncoming) : {}),
  }

  const introValue = usesTsfTree(templateKey)
    ? resolveIntro('intro' in (incoming as object) ? (incoming as { intro?: unknown }).intro : base.intro)
    : undefined

  const announcementRaw = recordOf('announcement' in (incoming as object) ? (incoming as { announcement?: unknown }).announcement : base.announcement)
  const announcementValue: ThemeAnnouncement | undefined = usesTsfTree(templateKey) && announcementRaw
    ? { items: plainTextList(announcementRaw.items, 2) }
    : undefined

  const byKey = new Map((incoming.sections || []).map((row) => [row.key, row]))
  const sections: SectionConfig[] = SHOP_SECTION_KEYS.map((key) => {
    const row = byKey.get(key)
    const preset = base.sections.find((item) => item.key === key) || fallback.config.sections.find((item) => item.key === key)!
    return {
      key,
      enabled: typeof row?.enabled === 'boolean' ? row.enabled : preset.enabled,
      variant: resolveSectionVariant(key, row?.variant, preset.variant, custom || (key === 'HERO' && templateKey === 'GENERAL')),
    }
  })

  const config: ThemeConfig = {
    colors, font, style, hero, banners, trust, sections,
    ...(gradients ? { gradients } : {}),
    ...(productCard ? { productCard } : {}),
    ...(sectionSettings ? { sectionSettings } : {}),
    ...(introValue ? { intro: introValue } : {}),
    ...(announcementValue ? { announcement: announcementValue } : {}),
    ...(fontBody ? { fontBody } : {}),
    ...(brand ? { brand } : {}),
  }

  let finalSections = sections
  if (incoming.sections?.length) {
    const ordered = incoming.sections
      .map((row) => sections.find((item) => item.key === row.key))
      .filter(Boolean) as SectionConfig[]
    const missing = sections.filter((item) => !ordered.some((row) => row.key === item.key))
    if (ordered.length > 0) finalSections = [...ordered, ...missing]
  }

  // AG3: only CUSTOM keeps layout.blocks (synthesized from sections + sectionSettings when absent).
  const layout = custom
    ? resolveLayout((incoming as { layout?: unknown }).layout, finalSections, sectionSettings, hero.layout)
    : undefined

  // AG5: header / footer / fontCustom exist for CUSTOM only; absent means the existing mall header and footer.
  const rawIncoming = incoming as unknown as Record<string, unknown>
  const header = custom ? resolveHeader(rawIncoming.header) : undefined
  const footer = custom ? resolveFooter(rawIncoming.footer) : undefined
  const fontCustom = custom ? resolveFontCustom(rawIncoming.fontCustom, font) : undefined

  return {
    templateKey,
    config: {
      ...config,
      sections: finalSections,
      ...(layout ? { layout } : {}),
      ...(header ? { header } : {}),
      ...(footer ? { footer } : {}),
      ...(fontCustom ? { fontCustom } : {}),
    },
  }
}

export function sectionSupportsVariant(key: ShopSectionKey) {
  return key === 'HERO' || key === 'FEATURED' || key === 'BESTSELLERS' || key === 'NEW_ARRIVALS' || key === 'CATALOG'
}

export function variantsForSection(key: ShopSectionKey, custom = false): SectionVariant[] {
  if (key === 'HERO') return custom ? ['FULL', 'SPLIT', 'POSTER', 'STRIP', 'MEDIA_FULL'] : ['FULL', 'SPLIT', 'POSTER', 'STRIP']
  if (key === 'FEATURED' || key === 'BESTSELLERS' || key === 'NEW_ARRIVALS' || key === 'CATALOG') {
    return ['GRID', 'LARGE', 'MENU']
  }
  return []
}
