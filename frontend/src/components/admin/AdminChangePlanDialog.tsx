import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import ConfirmDialog from '../form/ConfirmDialog'
import AdminSelect from './AdminSelect'
import { errorMessage, formatBytes, formatCount } from './PlatformBits'
import {
  adminPlatformApi,
  type AdminPlan,
  type AdminPlanCatalog,
  type AdminShopUsage,
  type ShopActionResponse,
} from '../../services/api'

type Props = {
  open: boolean
  shopId: number
  shopName: string
  currentPlanCode?: string | null
  usage: AdminShopUsage | null
  busy?: boolean
  onClose: () => void
  onAssigned: (response: ShopActionResponse) => void
}

function overLimitWarnings(
  usage: AdminShopUsage,
  plan: AdminPlan | null,
  td: (key: string, options?: Record<string, unknown>) => string,
): string[] {
  if (!plan?.limits) return []
  const limits = plan.limits
  const rows: Array<{ used: number; limit: number | null | undefined; key: string; format?: (n: number) => string }> = [
    { used: usage.products?.total ?? 0, limit: limits.products, key: 'products' },
    { used: usage.orders?.last30Days ?? 0, limit: limits.ordersPerMonth, key: 'orders' },
    { used: usage.staff?.active ?? 0, limit: limits.staff, key: 'staff' },
    { used: usage.storage?.trackedBytes ?? 0, limit: limits.storageBytes, key: 'storage', format: (n) => formatBytes(n) },
  ]
  return rows
    .filter((row) => row.limit != null && row.used > row.limit)
    .map((row) => {
      const fmt = row.format ?? ((n: number) => formatCount(n))
      return td(`over.${row.key}`, { used: fmt(row.used), limit: fmt(row.limit as number) })
    })
}

/** Shared admin dialog for PUT /api/admin/shops/{id}/plan (stores + usage pages). */
export default function AdminChangePlanDialog({
  open,
  shopId,
  shopName,
  currentPlanCode,
  usage,
  busy: busyExternal = false,
  onClose,
  onAssigned,
}: Props) {
  const { t } = useTranslation('admin')
  const td = (key: string, options?: Record<string, unknown>) =>
    t(`adminPlatform.stores.detail.changePlanDialog.${key}`, options)

  const [catalog, setCatalog] = useState<AdminPlanCatalog | null>(null)
  const [planPick, setPlanPick] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    setError('')
    setPlanPick((currentPlanCode ?? '').trim())
    let cancelled = false
    void adminPlatformApi.plans()
      .then((plans) => { if (!cancelled) setCatalog(plans) })
      .catch((err) => { if (!cancelled) setError(errorMessage(err, td('loadError'))) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, shopId, currentPlanCode])

  const options = useMemo(() => {
    const noPlan = t('adminPlatform.stores.detail.noPlan')
    return [
      { value: '', label: noPlan },
      ...(catalog?.plans ?? []).map((plan) => ({ value: plan.code, label: plan.name || plan.code })),
    ]
  }, [catalog, t])

  const picked = (catalog?.plans ?? []).find((plan) => plan.code === planPick) ?? null
  const warnings = usage && picked ? overLimitWarnings(usage, picked, td) : []
  const locked = busy || busyExternal

  const apply = async () => {
    if (locked) return
    setBusy(true)
    setError('')
    try {
      const code = planPick.trim() || null
      const response = await adminPlatformApi.assignShopPlan(shopId, code)
      onAssigned(response)
      onClose()
    } catch (err) {
      setError(errorMessage(err, td('error')))
    } finally {
      setBusy(false)
    }
  }

  if (!open) return null

  return (
    <ConfirmDialog
      title={td('title', { name: shopName })}
      message={td('message')}
      cancelLabel={t('confirm.cancel')}
      confirmLabel={td('confirm')}
      confirmDisabled={locked || !catalog}
      escapeDisabled={locked}
      onCancel={() => { if (!locked) onClose() }}
      onConfirm={() => void apply()}
    >
      <AdminSelect
        label={td('selectLabel')}
        options={options}
        value={planPick}
        onChange={setPlanPick}
        disabled={locked || !catalog}
      />
      {warnings.length > 0 ? (
        <ul className="store-plan-warn" role="alert">
          {warnings.map((line) => <li key={line}>{line}</li>)}
        </ul>
      ) : null}
      {error ? <p className="form-field-error" role="alert">{error}</p> : null}
    </ConfirmDialog>
  )
}
