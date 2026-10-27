import { request, type ProductPage, type PublicProduct } from './api'

/* ── Shop collections (V41) ─────────────────────────────────────────────── */

export type MerchantCollection = {
  id: string
  slug: string
  nameTh: string
  nameEn?: string | null
  description?: string | null
  coverMediaId?: string | null
  coverUrl?: string | null
  sortOrder: number
  published: boolean
  productCount: number
  createdDateTime?: string | null
  modifiedDateTime?: string | null
}
export type MerchantCollectionProduct = { productId: string; slug: string; nameTh: string; nameEn?: string | null; status: string; basePrice?: number | string | null; sortOrder: number }
export type MerchantCollectionDetail = { collection: MerchantCollection; products: MerchantCollectionProduct[] }
export type CollectionWrite = { slug?: string | null; nameTh: string; nameEn?: string | null; description?: string | null; coverMediaId?: string | null; published?: boolean }

export type PublicCollection = { slug: string; name: string; nameTh: string; nameEn?: string | null; description?: string | null; coverUrl?: string | null; productCount: number }
export type PublicCollectionDetail = { collection: PublicCollection; products: ProductPage<PublicProduct> }

const merchantBase = (shopId: number, suffix = '') => `/api/merchant/shops/${shopId}/collections${suffix}`
const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) })

export const collectionApi = {
  list: (shopId: number) => request<MerchantCollection[]>(merchantBase(shopId), { cache: 'no-store' }, true),
  get: (shopId: number, id: string) => request<MerchantCollectionDetail>(merchantBase(shopId, `/${id}`), { cache: 'no-store' }, true),
  create: (shopId: number, payload: CollectionWrite) => request<MerchantCollectionDetail>(merchantBase(shopId), json('POST', payload), true),
  update: (shopId: number, id: string, payload: CollectionWrite) => request<MerchantCollectionDetail>(merchantBase(shopId, `/${id}`), json('PUT', payload), true),
  remove: (shopId: number, id: string) => request<void>(merchantBase(shopId, `/${id}`), { method: 'DELETE' }, true),
  reorder: (shopId: number, ids: string[]) => request<MerchantCollection[]>(merchantBase(shopId, '/order'), json('PUT', { ids }), true),
  addProducts: (shopId: number, id: string, productIds: string[]) => request<MerchantCollectionDetail>(merchantBase(shopId, `/${id}/products`), json('POST', { productIds }), true),
  setProducts: (shopId: number, id: string, productIds: string[]) => request<MerchantCollectionDetail>(merchantBase(shopId, `/${id}/products`), json('PUT', { productIds }), true),
  removeProduct: (shopId: number, id: string, productId: string) => request<MerchantCollectionDetail>(merchantBase(shopId, `/${id}/products/${productId}`), { method: 'DELETE' }, true),
}

const shopBase = (slug: string) => `/api/shops/${encodeURIComponent(slug)}`

/** Public storefront reads only (never the merchant API). */
export const publicCollectionApi = {
  list: (slug: string, lang?: string) => request<PublicCollection[]>(`${shopBase(slug)}/collections${lang ? `?lang=${encodeURIComponent(lang)}` : ''}`, { cache: 'no-store' }),
  get: (slug: string, collectionSlug: string, query: { page?: number; size?: number; lang?: string } = {}) => {
    const qs = new URLSearchParams()
    if (query.page != null) qs.set('page', String(query.page))
    if (query.size != null) qs.set('size', String(query.size))
    if (query.lang) qs.set('lang', query.lang)
    const tail = qs.toString()
    return request<PublicCollectionDetail>(`${shopBase(slug)}/collections/${encodeURIComponent(collectionSlug)}${tail ? `?${tail}` : ''}`, { cache: 'no-store' })
  },
}

/* ── Product configurator (V42, behind FEATURES.productConfigurator) ────── */

export type LocalizedLabel = { th: string; en?: string | null; zh?: string | null }
export type ConfiguratorStepType = 'SINGLE' | 'MULTI' | 'TEXT'
export type ConfiguratorCharset = 'ANY' | 'LATIN' | 'ALNUM' | 'THAI_LATIN'
export type ConfiguratorOption = { id: string; label: LocalizedLabel; priceDelta: string; mediaId?: string | null; mediaUrl?: string | null; available?: boolean; stock?: number | null }
export type ConfiguratorStep = {
  id: string
  type: ConfiguratorStepType
  label: LocalizedLabel
  required: boolean
  min?: number
  max?: number
  maxLength?: number
  charset?: ConfiguratorCharset
  priceDelta?: string
  options?: ConfiguratorOption[]
}
export type ConfiguratorSchema = { steps: ConfiguratorStep[] }
export type MerchantConfigurator = { productId: string; enabled: boolean; schema: ConfiguratorSchema; version: number; modifiedDateTime?: string | null }
export type PublicConfigurator = { productId: string; version: number; basePrice: number | string; currency: string; steps: ConfiguratorStep[] }
export type ConfiguratorSelections = Record<string, string | string[]>
export type ConfiguratorQuoteLine = { stepId: string; optionId?: string | null; label: string; priceDelta: number | string }
export type ConfiguratorQuote = {
  complete: boolean
  missing: string[]
  basePrice: number | string
  lines: ConfiguratorQuoteLine[]
  total: number | string
  currency: string
  version: number
  configHash: string
  selections: ConfiguratorSelections
}

export const configuratorApi = {
  get: (shopId: number, productId: string) =>
    request<MerchantConfigurator>(`/api/merchant/shops/${shopId}/products/${productId}/configurator`, { cache: 'no-store' }, true),
  save: (shopId: number, productId: string, payload: { enabled: boolean; schema: ConfiguratorSchema; version: number }) =>
    request<MerchantConfigurator>(`/api/merchant/shops/${shopId}/products/${productId}/configurator`, json('PUT', payload), true),
}

export const publicConfiguratorApi = {
  get: (slug: string, productId: string) => request<PublicConfigurator>(`${shopBase(slug)}/products/${encodeURIComponent(productId)}/configurator`, { cache: 'no-store' }),
  quote: (slug: string, productId: string, selections: ConfiguratorSelections, version?: number) =>
    request<ConfiguratorQuote>(`${shopBase(slug)}/products/${encodeURIComponent(productId)}/configurator/quote`, json('POST', { selections, version })),
}

export function labelFor(label: LocalizedLabel | null | undefined, lang: string): string {
  if (!label) return ''
  if (lang.startsWith('en') && label.en) return label.en
  if (lang.startsWith('zh') && label.zh) return label.zh
  return label.th
}
