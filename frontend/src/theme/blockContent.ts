/**
 * AG4: content payloads of the CUSTOM-only block types (STEPS, FEATURE, GALLERY, SOCIAL, RICH_TEXT).
 * They live in `layout.blocks[].content` only and are never part of the legacy `sections` list.
 * All merchant text is a LocalizedString `{ th, en? }`. The resolvers are tolerant (partial / empty content never
 * throws) and mirror the backend whitelist; the backend stays the source of truth for validation.
 */

import { clampCrop, type ImageCrop } from '../components/shop/imageCrop'

export type { ImageCrop }

export const NEW_BLOCK_TYPES = ['STEPS', 'FEATURE', 'GALLERY', 'SOCIAL', 'RICH_TEXT', 'COLLECTIONS'] as const
export type NewBlockType = (typeof NEW_BLOCK_TYPES)[number]

export type LocalizedString = { th: string; en?: string }

export const STEP_ICONS = ['AUTHENTIC', 'FAST_SHIP', 'RETURNS', 'SECURE_PAY', 'CUSTOM', 'NUMBER'] as const
export type StepIcon = (typeof STEP_ICONS)[number]
export const FEATURE_LAYOUTS = ['IMAGE_LEFT', 'IMAGE_RIGHT'] as const
export type FeatureLayout = (typeof FEATURE_LAYOUTS)[number]
export const GALLERY_LAYOUTS = ['GRID', 'MASONRY', 'CAROUSEL'] as const
export type GalleryLayout = (typeof GALLERY_LAYOUTS)[number]
export const SOCIAL_PROVIDERS = ['TIKTOK'] as const
export type SocialProvider = (typeof SOCIAL_PROVIDERS)[number]

export const MIN_STEPS = 2
export const MAX_STEPS = 6
export const MAX_BULLETS = 5
export const MAX_GALLERY_ITEMS = 24
export const MAX_VIDEOS = 6
export const MAX_RICH_TEXT = 5000
export const TIKTOK_ID_PATTERN = /^\d{10,25}$/
export const MAX_COLLECTION_CARDS = 12
export const DEFAULT_COLLECTION_CARDS = 6
export const COLLECTION_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

export type BlockCta = { label?: LocalizedString; link?: string }
export type StepItem = {
  icon: StepIcon
  mediaId?: string
  mediaUrl?: string
  title?: LocalizedString
  body?: LocalizedString
  crop?: ImageCrop
  mobileCrop?: ImageCrop
}
export type StepsContent = { title?: LocalizedString; subtitle?: LocalizedString; steps: StepItem[]; cta?: BlockCta }
export type FeatureContent = {
  mediaId?: string
  /** Derived by the API from mediaId. */
  mediaUrl?: string
  title?: LocalizedString
  body?: LocalizedString
  bullets: LocalizedString[]
  cta?: BlockCta
  productId?: string
  /** Derived by the API while the product is still published. */
  productSlug?: string
  layout: FeatureLayout
  crop?: ImageCrop
  mobileCrop?: ImageCrop
}
export type GalleryItem = {
  mediaId: string
  url?: string
  caption?: LocalizedString
  link?: string
  crop?: ImageCrop
  mobileCrop?: ImageCrop
}
export type GalleryContent = { title?: LocalizedString; items: GalleryItem[]; layout: GalleryLayout }
export type SocialContent = { title?: LocalizedString; provider: SocialProvider; videoIds: string[] }
export type RichTextContent = { markdown?: LocalizedString; html?: LocalizedString }
/** V41 COLLECTIONS: empty `collectionSlugs` = all published collections (in the shop's order), capped by `limit`. */
export type CollectionsContent = { title?: LocalizedString; subtitle?: LocalizedString; collectionSlugs: string[]; limit: number }

/** `content` of any block; legacy section blocks always carry `{}`. */
export type BlockContent = Record<string, unknown>

export function isNewBlockType(type: string): type is NewBlockType {
  return (NEW_BLOCK_TYPES as readonly string[]).includes(type)
}

// ───────────────────────── helpers ─────────────────────────

const INTERNAL_LINK = /^\/(?!\/)[\w\-./?#=&%~+:@,]*$/
const HTTPS_LINK = /^https:\/\/[\w\-./?#=&%~+:@,]+$/i

/** Internal path or https URL only (same rule as the backend). */
export function safeBlockLink(link: unknown): { href: string; external: boolean } | null {
  if (typeof link !== 'string') return null
  const value = link.trim()
  if (!value || value.length > 500) return null
  if (INTERNAL_LINK.test(value)) return { href: value, external: false }
  if (HTTPS_LINK.test(value)) return { href: value, external: true }
  return null
}

/** The only embed URL the storefront ever builds; merchants supply a numeric TikTok video id, never HTML. */
export function tiktokEmbedUrl(id: unknown): string | null {
  return typeof id === 'string' && TIKTOK_ID_PATTERN.test(id) ? `https://www.tiktok.com/embed/v2/${id}` : null
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : null
}

function text(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined
}

/** `{th, en?}`; a plain string becomes `{th}`; blank / odd values become undefined. */
export function resolveLocalized(raw: unknown): LocalizedString | undefined {
  if (typeof raw === 'string') return raw.trim() ? { th: raw } : undefined
  const row = asRecord(raw)
  if (!row) return undefined
  const th = typeof row.th === 'string' ? row.th : ''
  const en = text(row.en)
  if (!th.trim() && !en) return undefined
  return { th, ...(en ? { en } : {}) }
}

/** Text for the active UI language: en/zh prefer the English copy, th (and any fallback) uses Thai, then the other. */
export function localizedText(value: LocalizedString | undefined, language: string | undefined): string {
  if (!value) return ''
  const lang = (language ?? 'th').toLowerCase()
  const preferEn = lang.startsWith('en') || lang.startsWith('zh')
  const first = preferEn ? value.en : value.th
  const second = preferEn ? value.th : value.en
  return (first?.trim() ? first : second?.trim() ? second : '') ?? ''
}

/** True when neither language has any text. */
export function isLocalizedEmpty(value: LocalizedString | undefined): boolean {
  return !value || (!value.th.trim() && !value.en?.trim())
}

function resolveCta(raw: unknown): BlockCta | undefined {
  const row = asRecord(raw)
  if (!row) return undefined
  const label = resolveLocalized(row.label)
  const link = text(row.link)?.trim()
  if (!label && !link) return undefined
  return { ...(label ? { label } : {}), ...(link ? { link } : {}) }
}

function pickEnum<T extends string>(raw: unknown, allowed: readonly T[], fallback: T): T {
  const value = typeof raw === 'string' ? raw.trim().toUpperCase() : ''
  return (allowed as readonly string[]).includes(value) ? (value as T) : fallback
}

// ───────────────────────── resolvers ─────────────────────────

export function resolveStepsContent(raw: unknown): StepsContent {
  const row = asRecord(raw) ?? {}
  const steps = (Array.isArray(row.steps) ? row.steps : []).slice(0, MAX_STEPS).map((item): StepItem => {
    const step = asRecord(item) ?? {}
    const icon = pickEnum(step.icon, STEP_ICONS, 'NUMBER')
    const mediaId = icon === 'CUSTOM' ? text(step.mediaId)?.trim() : undefined
    const mediaUrl = mediaId ? text(step.mediaUrl) : undefined
    const title = resolveLocalized(step.title)
    const body = resolveLocalized(step.body)
    const crop = mediaId ? clampCrop(step.crop) : undefined
    const mobileCrop = mediaId ? clampCrop(step.mobileCrop) : undefined
    return {
      icon,
      ...(mediaId ? { mediaId } : {}),
      ...(mediaUrl ? { mediaUrl } : {}),
      ...(title ? { title } : {}),
      ...(body ? { body } : {}),
      ...(crop ? { crop } : {}),
      ...(mobileCrop ? { mobileCrop } : {}),
    }
  })
  const title = resolveLocalized(row.title)
  const subtitle = resolveLocalized(row.subtitle)
  const cta = resolveCta(row.cta)
  return { ...(title ? { title } : {}), ...(subtitle ? { subtitle } : {}), steps, ...(cta ? { cta } : {}) }
}

export function resolveFeatureContent(raw: unknown): FeatureContent {
  const row = asRecord(raw) ?? {}
  const mediaId = text(row.mediaId)?.trim()
  const mediaUrl = mediaId ? text(row.mediaUrl) : undefined
  const title = resolveLocalized(row.title)
  const body = resolveLocalized(row.body)
  const bullets = (Array.isArray(row.bullets) ? row.bullets : [])
    .map(resolveLocalized)
    .filter((item): item is LocalizedString => Boolean(item))
    .slice(0, MAX_BULLETS)
  const cta = resolveCta(row.cta)
  const productId = text(row.productId)?.trim()
  const productSlug = productId ? text(row.productSlug) : undefined
  const crop = mediaId ? clampCrop(row.crop) : undefined
  const mobileCrop = mediaId ? clampCrop(row.mobileCrop) : undefined
  return {
    ...(mediaId ? { mediaId } : {}),
    ...(mediaUrl ? { mediaUrl } : {}),
    ...(title ? { title } : {}),
    ...(body ? { body } : {}),
    bullets,
    ...(cta ? { cta } : {}),
    ...(productId ? { productId } : {}),
    ...(productSlug ? { productSlug } : {}),
    layout: pickEnum(row.layout, FEATURE_LAYOUTS, 'IMAGE_LEFT'),
    ...(crop ? { crop } : {}),
    ...(mobileCrop ? { mobileCrop } : {}),
  }
}

export function resolveGalleryContent(raw: unknown): GalleryContent {
  const row = asRecord(raw) ?? {}
  const items: GalleryItem[] = []
  for (const entry of Array.isArray(row.items) ? row.items : []) {
    const item = asRecord(entry)
    const mediaId = item ? text(item.mediaId)?.trim() : undefined
    if (!item || !mediaId) continue
    const url = text(item.url)
    const caption = resolveLocalized(item.caption)
    const link = text(item.link)?.trim()
    const crop = clampCrop(item.crop)
    const mobileCrop = clampCrop(item.mobileCrop)
    items.push({
      mediaId,
      ...(url ? { url } : {}),
      ...(caption ? { caption } : {}),
      ...(link ? { link } : {}),
      ...(crop ? { crop } : {}),
      ...(mobileCrop ? { mobileCrop } : {}),
    })
    if (items.length >= MAX_GALLERY_ITEMS) break
  }
  const title = resolveLocalized(row.title)
  return { ...(title ? { title } : {}), items, layout: pickEnum(row.layout, GALLERY_LAYOUTS, 'GRID') }
}

export function resolveSocialContent(raw: unknown): SocialContent {
  const row = asRecord(raw) ?? {}
  const videoIds = (Array.isArray(row.videoIds) ? row.videoIds : [])
    .filter((id): id is string => typeof id === 'string')
    .map((id) => id.trim())
    .filter((id) => TIKTOK_ID_PATTERN.test(id))
    .slice(0, MAX_VIDEOS)
  const title = resolveLocalized(row.title)
  return { ...(title ? { title } : {}), provider: 'TIKTOK', videoIds }
}

export function resolveRichTextContent(raw: unknown): RichTextContent {
  const row = asRecord(raw) ?? {}
  const markdown = resolveLocalized(row.markdown)
  const html = resolveLocalized(row.html)
  return { ...(markdown ? { markdown } : {}), ...(html ? { html } : {}) }
}

export function resolveCollectionsContent(raw: unknown): CollectionsContent {
  const row = asRecord(raw) ?? {}
  const seen = new Set<string>()
  const collectionSlugs: string[] = []
  for (const entry of Array.isArray(row.collectionSlugs) ? row.collectionSlugs : []) {
    const slug = typeof entry === 'string' ? entry.trim().toLowerCase() : ''
    if (!COLLECTION_SLUG_PATTERN.test(slug) || seen.has(slug)) continue
    seen.add(slug)
    collectionSlugs.push(slug)
    if (collectionSlugs.length >= MAX_COLLECTION_CARDS) break
  }
  const rawLimit = typeof row.limit === 'number' && Number.isInteger(row.limit) ? row.limit : DEFAULT_COLLECTION_CARDS
  const limit = Math.min(MAX_COLLECTION_CARDS, Math.max(1, rawLimit))
  const title = resolveLocalized(row.title)
  const subtitle = resolveLocalized(row.subtitle)
  return { ...(title ? { title } : {}), ...(subtitle ? { subtitle } : {}), collectionSlugs, limit }
}

/** Normalize a block's content from the API; legacy section blocks always get `{}`. */
export function resolveBlockContent(type: string, raw: unknown): BlockContent {
  switch (type) {
    case 'STEPS': return resolveStepsContent(raw)
    case 'FEATURE': return resolveFeatureContent(raw)
    case 'GALLERY': return resolveGalleryContent(raw)
    case 'SOCIAL': return resolveSocialContent(raw)
    case 'RICH_TEXT': return resolveRichTextContent(raw)
    case 'COLLECTIONS': return resolveCollectionsContent(raw)
    default: return {}
  }
}

/** Starting content for a block added in the editor (valid per backend rules, nothing to display yet). */
export function defaultBlockContent(type: string): BlockContent {
  switch (type) {
    case 'STEPS':
      return { steps: [{ icon: 'NUMBER' }, { icon: 'NUMBER' }, { icon: 'NUMBER' }] } satisfies StepsContent
    case 'FEATURE':
      return { bullets: [], layout: 'IMAGE_LEFT' } satisfies FeatureContent
    case 'GALLERY':
      return { items: [], layout: 'GRID' } satisfies GalleryContent
    case 'SOCIAL':
      return { provider: 'TIKTOK', videoIds: [] } satisfies SocialContent
    case 'RICH_TEXT':
      return {} satisfies RichTextContent
    case 'COLLECTIONS':
      return { collectionSlugs: [], limit: DEFAULT_COLLECTION_CARDS } satisfies CollectionsContent
    default:
      return {}
  }
}
