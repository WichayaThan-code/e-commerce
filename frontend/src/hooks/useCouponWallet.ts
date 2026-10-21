import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../contexts/AuthContext'
import { walletApi, type CollectedCoupon } from '../services/api'

/**
 * Buyer coupon wallet of one shop (GET /api/me/coupons?shopId=). Shared by ShopCouponStrip (storefront) and
 * CartCouponOffers (cart): a collect on one updates the other through WALLET_EVENT. Guests / no shop = empty, no request.
 */
const WALLET_EVENT = 'tsf:coupon-wallet'
const cache = new Map<string, CollectedCoupon[]>()
const keyOf = (userId: string, shopId: number) => `${userId}:${shopId}`

export function useCouponWallet(shopId: number | null | undefined, refreshKey?: unknown) {
  const { user, loading } = useAuth()
  const userId = user?.id ?? null
  const key = userId && shopId != null ? keyOf(userId, shopId) : null
  const [state, setState] = useState<{ key: string | null; rows: CollectedCoupon[]; ready: boolean }>(() => ({
    key, rows: key ? cache.get(key) ?? [] : [], ready: key ? cache.has(key) : !loading,
  }))

  useEffect(() => {
    if (loading) return
    if (!key || shopId == null) { setState({ key, rows: [], ready: true }); return }
    let alive = true
    const cached = cache.get(key)
    setState({ key, rows: cached ?? [], ready: cached !== undefined })
    walletApi.list(shopId)
      .then((rows) => { const list = Array.isArray(rows) ? rows : []; cache.set(key, list); if (alive) setState({ key, rows: list, ready: true }) })
      // The wallet must never break the storefront / cart: behave as "nothing collected".
      .catch(() => { if (alive) setState((current) => ({ key, rows: current.key === key ? current.rows : [], ready: true })) })
    const onChange = (event: Event) => {
      if ((event as CustomEvent<string>).detail === key && alive) setState({ key, rows: cache.get(key) ?? [], ready: true })
    }
    window.addEventListener(WALLET_EVENT, onChange)
    return () => { alive = false; window.removeEventListener(WALLET_EVENT, onChange) }
  }, [key, shopId, loading, refreshKey])

  /** POST /api/me/coupons, then re-reads the wallet. Throws ApiError (caller shows the toast). */
  const collect = useCallback(async (code: string) => {
    if (!key || shopId == null) throw new Error('LOGIN_REQUIRED')
    const result = await walletApi.collect(shopId, code)
    const rows = await walletApi.list(shopId).catch(() => cache.get(key) ?? [])
    cache.set(key, Array.isArray(rows) ? rows : [])
    window.dispatchEvent(new CustomEvent(WALLET_EVENT, { detail: key }))
    return result
  }, [key, shopId])

  const current = state.key === key
  const rows = current ? state.rows : []
  return {
    signedIn: Boolean(userId),
    authLoading: loading,
    coupons: rows,
    codes: new Set(rows.map((row) => row.code.toUpperCase())),
    ready: !loading && (current ? state.ready : !key),
    collect,
  }
}
