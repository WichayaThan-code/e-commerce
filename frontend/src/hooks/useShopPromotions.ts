import { useEffect, useState } from 'react'
import { shopApi, type PublicPromotion } from '../services/api'

/** Shared by ShopTabs (tab target) and ShopCouponStrip so one storefront visit makes one request per minute. */
const TTL_MS = 60_000
type Entry = { at: number; data?: PublicPromotion[]; promise?: Promise<PublicPromotion[]> }
const cache = new Map<string, Entry>()

function load(slug: string): Promise<PublicPromotion[]> {
  const entry = cache.get(slug)
  if (entry?.promise) return entry.promise
  const promise = shopApi.promotions(slug)
    .then((rows) => { cache.set(slug, { at: Date.now(), data: Array.isArray(rows) ? rows : [] }); return cache.get(slug)!.data! })
    // A failing/missing endpoint must never break the storefront: behave as "no promotions".
    .catch(() => { cache.set(slug, { at: Date.now(), data: [] }); return [] })
  cache.set(slug, { at: entry?.at ?? 0, data: entry?.data, promise })
  return promise
}

function fresh(slug: string): PublicPromotion[] | undefined {
  const entry = cache.get(slug)
  return entry?.data && Date.now() - entry.at < TTL_MS ? entry.data : undefined
}

/** Public storefront promotions. `slug = null` (theme editor preview) never calls the API. */
export function useShopPromotions(slug: string | null) {
  const [state, setState] = useState<{ slug: string | null; rows: PublicPromotion[]; ready: boolean }>(() => {
    const cached = slug ? fresh(slug) : []
    return { slug, rows: cached ?? [], ready: cached !== undefined }
  })

  useEffect(() => {
    if (!slug) { setState({ slug, rows: [], ready: true }); return }
    const cached = fresh(slug)
    if (cached) { setState({ slug, rows: cached, ready: true }); return }
    let alive = true
    setState((current) => current.slug === slug ? current : { slug, rows: [], ready: false })
    void load(slug).then((rows) => { if (alive) setState({ slug, rows, ready: true }) })
    return () => { alive = false }
  }, [slug])

  const current = state.slug === slug
  return { promotions: current ? state.rows : [], ready: current && state.ready }
}
