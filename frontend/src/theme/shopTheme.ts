/** Storefront theme types and helpers. Presets come from GET /theme/presets — only a minimal fallback lives here. */

export const SHOP_THEME_TEMPLATES = ['GENERAL', 'FASHION', 'FOOD_BEVERAGE'] as const
export type ShopThemeTemplateKey = (typeof SHOP_THEME_TEMPLATES)[number]

export const SHOP_THEME_FONTS = ['NOTO_SANS_THAI', 'NOTO_SERIF_THAI', 'PROMPT', 'MITR', 'IBM_PLEX_SANS_THAI'] as const
export type ShopThemeFontKey = (typeof SHOP_THEME_FONTS)[number]

export const SHOP_SECTION_KEYS = [
  'HERO', 'BANNERS', 'CATEGORIES', 'TRUST', 'INTRO', 'FEATURED', 'BESTSELLERS', 'NEW_ARRIVALS', 'CATALOG', 'HOURS',
] as const
export type ShopSectionKey = (typeof SHOP_SECTION_KEYS)[number]

export const SHOP_TRUST_ICONS = ['AUTHENTIC', 'FAST_SHIP', 'RETURNS', 'SECURE_PAY', 'CUSTOM'] as const
export type ShopTrustIcon = (typeof SHOP_TRUST_ICONS)[number]

export type ThemeBanner = { imageUrl: string; title?: string; link?: string }
export type ThemeTrustItem = { icon: ShopTrustIcon; label: string; enabled: boolean }
export type ThemeTrust = { enabled: boolean; items: ThemeTrustItem[] }

export type HeroLayout = 'FULL' | 'STRIP'
export type CatalogVariant = 'GRID' | 'LARGE' | 'MENU'
export type SectionVariant = HeroLayout | CatalogVariant | null

export const SHOP_CARD_STYLES = ['FLAT', 'BORDERED', 'SHADOW'] as const
export type ShopCardStyle = (typeof SHOP_CARD_STYLES)[number]

export const SHOP_DENSITIES = ['COMPACT', 'COMFORTABLE'] as const
export type ShopDensity = (typeof SHOP_DENSITIES)[number]

export const SHOP_BUTTON_SHAPES = ['ROUNDED', 'PILL', 'SQUARE'] as const
export type ShopButtonShape = (typeof SHOP_BUTTON_SHAPES)[number]

export type ThemeColors = {
  primary: string
  secondary: string
  background: string
  text: string
  /** Optional muted accent (e.g. fashion gold) for hairlines / hover / price. */
  accent?: string
}

export type ThemeStyle = {
  radius: number
  cardStyle: ShopCardStyle
  density: ShopDensity
  buttonShape: ShopButtonShape
}

export type ThemeHero = {
  layout: HeroLayout
  title: string
  subtitle: string
  ctaLabel: string
  ctaLink: string
}

export type SectionConfig = {
  key: ShopSectionKey
  enabled: boolean
  variant: SectionVariant
}

export type ThemeConfig = {
  colors: ThemeColors
  font: ShopThemeFontKey
  style: ThemeStyle
  hero: ThemeHero
  banners: ThemeBanner[]
  trust: ThemeTrust
  sections: SectionConfig[]
}

export type ShopTheme = {
  templateKey: ShopThemeTemplateKey
  config: ThemeConfig
}

export const SHOP_THEME_FONT_STACKS: Record<ShopThemeFontKey, string> = {
  NOTO_SANS_THAI: '"Noto Sans Thai", sans-serif',
  NOTO_SERIF_THAI: '"Noto Serif Thai", serif',
  PROMPT: '"Prompt", "Noto Sans Thai", sans-serif',
  MITR: '"Mitr", "Noto Sans Thai", sans-serif',
  IBM_PLEX_SANS_THAI: '"IBM Plex Sans Thai", "Noto Sans Thai", sans-serif',
}

export const SHOP_THEME_FONT_HREF: Record<ShopThemeFontKey, string | null> = {
  NOTO_SANS_THAI: 'https://fonts.googleapis.com/css2?family=Noto+Sans+Thai:wght@400;500;600;700&display=swap',
  NOTO_SERIF_THAI: 'https://fonts.googleapis.com/css2?family=Noto+Serif+Thai:wght@400;500;600;700&display=swap',
  PROMPT: 'https://fonts.googleapis.com/css2?family=Prompt:wght@400;500;600;700&display=swap',
  MITR: 'https://fonts.googleapis.com/css2?family=Mitr:wght@400;500;600;700&display=swap',
  IBM_PLEX_SANS_THAI: null,
}

export const SHOP_BREAKPOINTS = {
  mobileMax: 767,
  tabletMin: 768,
  tabletMax: 1023,
  desktopMin: 1024,
} as const

export const PREVIEW_WIDTHS = { mobile: 375, tablet: 768, desktop: 1280 } as const

const HERO_VARIANTS = new Set<string>(['FULL', 'STRIP'])
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
          { icon: 'SECURE_PAY', label: 'ชำระปลอดภัย', enabled: true },
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

export function ensureThemeFontLink(font: ShopThemeFontKey) {
  if (typeof document === 'undefined') return
  const href = SHOP_THEME_FONT_HREF[font]
  if (!href) return
  const id = `shop-theme-font-${font}`
  if (document.getElementById(id)) return
  const link = document.createElement('link')
  link.id = id
  link.rel = 'stylesheet'
  link.href = href
  document.head.appendChild(link)
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

function styleCssVars(style: ThemeStyle): Record<string, string> {
  const radius = Math.max(0, Math.min(32, style.radius))
  const gap = style.density === 'COMPACT' ? '12px' : '16px'
  const btnRadius =
    style.buttonShape === 'PILL' ? '999px'
    : style.buttonShape === 'SQUARE' ? '4px'
    : `${Math.max(4, Math.round(radius * 0.75))}px`
  const cardBorder =
    style.cardStyle === 'FLAT' ? 'transparent'
    : 'var(--shop-border)'
  // Product cards inside Mall panels use a thin border only (no stacked shadow).
  const cardShadow =
    style.cardStyle === 'SHADOW' ? '0 4px 12px color-mix(in srgb, var(--shop-text) 8%, transparent)'
    : 'none'
  return {
    '--shop-radius': `${radius}px`,
    '--shop-gap': gap,
    '--shop-btn-radius': btnRadius,
    '--shop-card-border': cardBorder,
    '--shop-card-shadow': cardShadow,
  }
}

export function themeCssVars(config: ThemeConfig): Record<string, string> {
  const { primary, secondary, background, text, accent } = config.colors
  const onPrimary = contrastOnColor(primary)
  const onSecondary = contrastOnColor(secondary)
  // Card panels keep the merchant background; page chrome is a soft tint so white themes still read as blocks.
  const bgLum = relativeLuminance(background)
  const pageBgMix = bgLum != null && bgLum > 0.92 ? 5 : 4
  const pageBg = `color-mix(in srgb, ${text} ${pageBgMix}%, ${background})`
  const accentHex = typeof accent === 'string' && /^#[0-9a-fA-F]{6}$/.test(accent) ? accent : primary
  return {
    '--shop-primary': primary,
    '--shop-secondary': secondary,
    '--shop-bg': background,
    '--shop-card-bg': background,
    '--shop-page-bg': pageBg,
    '--shop-text': text,
    '--shop-accent': accentHex,
    '--shop-font': shopThemeFontStack(config.font),
    '--font-sans': shopThemeFontStack(config.font),
    '--shop-muted': `color-mix(in srgb, ${text} 65%, ${background})`,
    '--shop-border': `color-mix(in srgb, ${text} 10%, ${background})`,
    '--shop-surface': `color-mix(in srgb, ${text} 4%, ${background})`,
    '--shop-on-primary': onPrimary,
    '--shop-primary-contrast': onPrimary,
    '--shop-on-secondary': onSecondary,
    '--shop-primary-soft': `color-mix(in srgb, ${primary} 12%, ${background})`,
    '--shop-tabs-h': '44px',
    ...styleCssVars(config.style ?? DEFAULT_STYLE),
  }
}

function resolveFont(raw: unknown, fallback: ShopThemeFontKey): ShopThemeFontKey {
  const value = typeof raw === 'string' ? raw.trim().toUpperCase() : ''
  return SHOP_THEME_FONTS.includes(value as ShopThemeFontKey) ? (value as ShopThemeFontKey) : fallback
}

function resolveHeroLayout(raw: unknown, fallback: HeroLayout): HeroLayout {
  const value = typeof raw === 'string' ? raw.trim().toUpperCase() : ''
  return HERO_VARIANTS.has(value) ? (value as HeroLayout) : fallback
}

function resolveSectionVariant(key: ShopSectionKey, raw: unknown, fallback: SectionVariant): SectionVariant {
  if (raw == null || raw === '') return fallback
  const value = String(raw).trim().toUpperCase()
  if (key === 'HERO') return HERO_VARIANTS.has(value) ? (value as HeroLayout) : fallback
  if (key === 'FEATURED' || key === 'BESTSELLERS' || key === 'NEW_ARRIVALS' || key === 'CATALOG') {
    return CATALOG_VARIANTS.has(value) ? (value as CatalogVariant) : fallback
  }
  return null
}

function resolveBanners(raw: unknown, fallback: ThemeBanner[]): ThemeBanner[] {
  if (!Array.isArray(raw)) return structuredClone(fallback)
  return raw
    .map((row) => {
      if (!row || typeof row !== 'object') return null
      const item = row as Record<string, unknown>
      const imageUrl = typeof item.imageUrl === 'string' ? item.imageUrl.trim() : ''
      if (!imageUrl) return null
      return {
        imageUrl,
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
  return { radius, cardStyle, density, buttonShape }
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
    return { templateKey, config: structuredClone(base) }
  }

  const accentRaw = (incoming.colors as ThemeColors | undefined)?.accent ?? (base.colors as ThemeColors).accent
  const colors: ThemeColors = {
    primary: typeof incoming.colors?.primary === 'string' && incoming.colors.primary ? incoming.colors.primary : base.colors.primary,
    secondary: typeof incoming.colors?.secondary === 'string' && incoming.colors.secondary ? incoming.colors.secondary : base.colors.secondary,
    background: typeof incoming.colors?.background === 'string' && incoming.colors.background ? incoming.colors.background : base.colors.background,
    text: typeof incoming.colors?.text === 'string' && incoming.colors.text ? incoming.colors.text : base.colors.text,
    ...(typeof accentRaw === 'string' && /^#[0-9a-fA-F]{6}$/.test(accentRaw) ? { accent: accentRaw } : {}),
  }

  const font = resolveFont(incoming.font, base.font)
  const style = resolveStyle(incoming.style, base.style ?? DEFAULT_STYLE)
  const banners = resolveBanners(incoming.banners, base.banners ?? fallback.config.banners)
  const trust = resolveTrust(incoming.trust, base.trust ?? fallback.config.trust)
  const heroIncoming = incoming.hero || {}
  const hero: ThemeHero = {
    layout: resolveHeroLayout(heroIncoming.layout ?? (incoming.sections?.find((s) => s.key === 'HERO')?.variant), base.hero.layout),
    title: typeof heroIncoming.title === 'string' ? heroIncoming.title : base.hero.title,
    subtitle: typeof heroIncoming.subtitle === 'string' ? heroIncoming.subtitle : base.hero.subtitle,
    ctaLabel: typeof heroIncoming.ctaLabel === 'string' ? heroIncoming.ctaLabel : base.hero.ctaLabel,
    ctaLink: typeof heroIncoming.ctaLink === 'string' ? heroIncoming.ctaLink : base.hero.ctaLink,
  }

  const byKey = new Map((incoming.sections || []).map((row) => [row.key, row]))
  const sections: SectionConfig[] = SHOP_SECTION_KEYS.map((key) => {
    const row = byKey.get(key)
    const preset = base.sections.find((item) => item.key === key) || fallback.config.sections.find((item) => item.key === key)!
    return {
      key,
      enabled: typeof row?.enabled === 'boolean' ? row.enabled : preset.enabled,
      variant: resolveSectionVariant(key, row?.variant, preset.variant),
    }
  })

  const config: ThemeConfig = { colors, font, style, hero, banners, trust, sections }

  if (incoming.sections?.length) {
    const ordered = incoming.sections
      .map((row) => sections.find((item) => item.key === row.key))
      .filter(Boolean) as SectionConfig[]
    const missing = sections.filter((item) => !ordered.some((row) => row.key === item.key))
    if (ordered.length > 0) {
      return { templateKey, config: { ...config, sections: [...ordered, ...missing] } }
    }
  }

  return { templateKey, config }
}

export function sectionSupportsVariant(key: ShopSectionKey) {
  return key === 'HERO' || key === 'FEATURED' || key === 'BESTSELLERS' || key === 'NEW_ARRIVALS' || key === 'CATALOG'
}

export function variantsForSection(key: ShopSectionKey): SectionVariant[] {
  if (key === 'HERO') return ['FULL', 'STRIP']
  if (key === 'FEATURED' || key === 'BESTSELLERS' || key === 'NEW_ARRIVALS' || key === 'CATALOG') {
    return ['GRID', 'LARGE', 'MENU']
  }
  return []
}
