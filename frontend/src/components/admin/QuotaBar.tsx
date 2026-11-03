import { formatBytes, formatCount } from './PlatformBits'
import type { AdminShopUsage } from '../../services/api'

type TP = (key: string, options?: Record<string, unknown>) => string

export type QuotaRow = {
  key: string
  label: string
  used: number
  limit: number | null
  format?: (n: number) => string
  hint?: string
}

/** limit null = unlimited; limit 0 + used 0 → 100% ok; limit 0 + used > 0 → over (no ÷0). */
export function quotaRatio(used: number, limit: number | null | undefined): number | null {
  if (limit == null) return null
  if (limit === 0) return 1
  return used / limit
}

export function quotaTone(used: number, limit: number | null | undefined, pct: number | null): 'ok' | 'warn' | 'over' {
  if (limit == null) return 'ok'
  if (limit === 0) return used > 0 ? 'over' : 'ok'
  if (pct == null) return 'ok'
  if (pct >= 1) return 'over'
  if (pct >= 0.8) return 'warn'
  return 'ok'
}

export function QuotaBar({
  used, limit, unlimitedLabel, format, showCaption = true,
}: {
  used: number
  limit: number | null
  unlimitedLabel: string
  format?: (n: number) => string
  showCaption?: boolean
}) {
  const pct = quotaRatio(used, limit)
  const width = pct == null ? 0 : Math.min(100, Math.round(pct * 100))
  const tone = quotaTone(used, limit, pct)
  const fmt = format ?? formatCount
  return (
    <div className="ap-quota">
      <div className={`ap-quota-bar ${tone}`} aria-hidden="true">
        <i style={{ width: pct == null ? '0%' : `${width}%` }} />
      </div>
      {showCaption && (
        <small className="num">
          {limit == null
            ? unlimitedLabel
            : `${fmt(used)} / ${fmt(limit)}${pct != null ? ` (${Math.round(pct * 100)}%)` : ''}`}
        </small>
      )}
    </div>
  )
}

/** Build quota rows for a shop usage payload. `tp` keys are under `adminPlatform.usage.metric.*`. */
export function buildUsageQuotas(usage: AdminShopUsage, tp: TP): QuotaRow[] {
  return [
    {
      key: 'products',
      label: tp('usage.metric.products'),
      used: usage.products?.total ?? 0,
      limit: usage.limits?.products ?? null,
      hint: tp('usage.metric.productsHint', {
        published: formatCount(usage.products?.published),
        draft: formatCount(usage.products?.draft),
        images: formatCount(usage.products?.images),
      }),
    },
    {
      key: 'images',
      label: tp('usage.metric.images'),
      used: usage.products?.images ?? 0,
      limit: null,
    },
    {
      key: 'orders',
      label: tp('usage.metric.ordersMonth'),
      used: usage.orders?.last30Days ?? 0,
      limit: usage.limits?.ordersPerMonth ?? null,
      hint: tp('usage.metric.ordersHint', {
        total: formatCount(usage.orders?.total),
        open: formatCount(usage.orders?.open),
      }),
    },
    {
      key: 'staff',
      label: tp('usage.metric.staff'),
      used: usage.staff?.active ?? 0,
      limit: usage.limits?.staff ?? null,
    },
    {
      key: 'storage',
      label: tp('usage.metric.storage'),
      used: usage.storage?.trackedBytes ?? 0,
      limit: usage.limits?.storageBytes ?? null,
      format: formatBytes,
    },
  ]
}
