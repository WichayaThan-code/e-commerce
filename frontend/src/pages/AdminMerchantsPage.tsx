import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { Building2, Search } from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { LoadError, SkeletonForm, SkeletonTable } from '../components/feedback/Skeleton'
import ReasonDialog from '../components/admin/ReasonDialog'
import { Drawer, Pager, Pill, errorMessage, formatBytes, formatCount, statusTone, usePlatformList } from '../components/admin/PlatformBits'
import { formatBaht } from '../components/cart/cartFormat'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { hasPlatformAccess } from '../services/permissions'
import { formatDateTime } from '../services/adminFormat'
import {
  adminPlatformApi,
  type AdminMerchantDetail, type AdminMerchantRow, type AdminPlanCatalog, type AdminShopDetail, type AdminShopRow, type AdminShopUsage,
} from '../services/api'
import '../styles/admin-dashboard.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import AdminSelect from '../components/admin/AdminSelect'
import AdminDateRange from '../components/admin/AdminDateRange'

type Tab = 'merchants' | 'shops' | 'plans'
type TP = (key: string, options?: Record<string, unknown>) => string
type Notify = (message: string) => void

const MERCHANT_STATUSES = ['ACTIVE', 'SUSPENDED']
const OWNER_TYPES = ['PERSON', 'COMPANY']
const SHOP_STATUSES = ['APPROVED', 'PENDING_REVIEW', 'SUSPENDED', 'REJECTED']

const dash = (value: string | number | null | undefined) => (value === null || value === undefined || value === '' ? '—' : String(value))
const fullName = (parts: (string | null | undefined)[]) => parts.map(part => (part ?? '').trim()).filter(Boolean).join(' ') || '—'

/** Suspension block shared by merchant and shop drawers. */
function SuspensionInfo({ tp, suspension }: { tp: TP; suspension?: { status?: string | null; suspendedAt?: string | null; suspendedByName?: string | null; reasonCode?: string | null; reason?: string | null; restoredAt?: string | null; restoredByName?: string | null } | null }) {
  if (!suspension || (!suspension.suspendedAt && !suspension.restoredAt)) return null
  return (
    <section className="ap-section">
      <h3>{tp('common.suspension')}</h3>
      <dl className="ap-fields">
        {suspension.suspendedAt && <div><dt>{tp('common.suspendedAt')}</dt><dd className="num">{formatDateTime(suspension.suspendedAt)}{suspension.suspendedByName ? ` · ${suspension.suspendedByName}` : ''}</dd></div>}
        {suspension.reasonCode && <div><dt>{tp('common.reasonCode')}</dt><dd className="num">{suspension.reasonCode}</dd></div>}
        {suspension.reason && <div className="full"><dt>{tp('reason.label')}</dt><dd>{suspension.reason}</dd></div>}
        {suspension.restoredAt && <div><dt>{tp('common.restoredAt')}</dt><dd className="num">{formatDateTime(suspension.restoredAt)}{suspension.restoredByName ? ` · ${suspension.restoredByName}` : ''}</dd></div>}
      </dl>
    </section>
  )
}

function UsageGrid({ tp, usage, localeTag }: { tp: TP; usage: AdminShopUsage; localeTag: string }) {
  const limit = (value: number | null | undefined, format: (n: number) => string = formatCount) => (value === null || value === undefined ? tp('plans.noLimit') : format(value))
  return (
    <div className="ap-usage">
      <article><small>{tp('plans.products')}</small><strong className="num">{formatCount(usage.products?.total)}</strong><em>{tp('plans.limit')}: <span className="num">{limit(usage.limits?.products)}</span></em><span>{tp('plans.productsDetail', { published: formatCount(usage.products?.published), draft: formatCount(usage.products?.draft) })}</span></article>
      <article><small>{tp('plans.orders')}</small><strong className="num">{formatCount(usage.orders?.total)}</strong><em>{tp('plans.limitPerMonth')}: <span className="num">{limit(usage.limits?.ordersPerMonth)}</span></em><span>{tp('plans.ordersDetail', { last30: formatCount(usage.orders?.last30Days), open: formatCount(usage.orders?.open) })}</span></article>
      <article><small>{tp('plans.staff')}</small><strong className="num">{formatCount(usage.staff?.active)}</strong><em>{tp('plans.limit')}: <span className="num">{limit(usage.limits?.staff)}</span></em></article>
      <article><small>{tp('plans.storage')}</small><strong className="num">{formatBytes(usage.storage?.trackedBytes)}</strong><em>{tp('plans.limit')}: <span className="num">{limit(usage.limits?.storageBytes, formatBytes)}</span></em></article>
      <article><small>{tp('plans.paidTotal')}</small><strong className="num">{formatBaht(usage.orders?.paidGrandTotal, localeTag)}</strong></article>
      <article><small>{tp('plans.customers')}</small><strong className="num">{formatCount(usage.customers)}</strong><em>{tp('plans.coupons')}: <span className="num">{formatCount(usage.activeCoupons)}</span></em></article>
    </div>
  )
}

/* ------------------------------------------------------------------ merchants */

type MerchantFilters = { q: string; status: string; ownerType: string; shopStatus: string; from: string; to: string }
const EMPTY_MERCHANT_FILTERS: MerchantFilters = { q: '', status: '', ownerType: '', shopStatus: '', from: '', to: '' }

function MerchantsTab({ tp, notify, initialMerchantId }: { tp: TP; notify: Notify; initialMerchantId?: string | null }) {
  const { can, deniedTitle } = usePermissions()
  const canSuspend = can('PLATFORM_SHOP_SUSPEND')
  const canRestore = can('PLATFORM_SHOP_RESTORE')
  const canAudit = can('PLATFORM_AUDIT_READ')
  const [draft, setDraft] = useState(EMPTY_MERCHANT_FILTERS)
  const [filters, setFilters] = useState(EMPTY_MERCHANT_FILTERS)
  const list = usePlatformList<AdminMerchantRow>(params => adminPlatformApi.merchants(params), filters, { errorText: tp('errors.load') })
  const [selectedId, setSelectedId] = useState<string | null>(initialMerchantId ?? null)

  useEffect(() => {
    if (initialMerchantId) setSelectedId(initialMerchantId)
  }, [initialMerchantId])
  const [detail, setDetail] = useState<AdminMerchantDetail | null>(null)
  const [detailError, setDetailError] = useState('')
  const [confirm, setConfirm] = useState<'suspend' | 'reactivate' | null>(null)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    if (!selectedId) { setDetail(null); setDetailError(''); return }
    let cancelled = false
    setDetail(null)
    setDetailError('')
    adminPlatformApi.merchant(selectedId)
      .then(value => { if (!cancelled) setDetail(value) })
      .catch(error => { if (!cancelled) setDetailError(errorMessage(error, tp('errors.detail'))) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  const items = list.result?.items ?? []
  const set = (key: keyof MerchantFilters, value: string) => setDraft(current => ({ ...current, [key]: value }))
  const submit = (event: FormEvent) => { event.preventDefault(); setFilters(draft); list.setPage(0) }
  const reset = () => { setDraft(EMPTY_MERCHANT_FILTERS); setFilters(EMPTY_MERCHANT_FILTERS); list.setPage(0) }
  const closeDrawer = () => { setSelectedId(null); setConfirm(null); setActionError('') }

  const apply = async (reason: string) => {
    if (!detail || !confirm) return
    setBusy(true)
    setActionError('')
    try {
      const body = { reason }
      const response = confirm === 'suspend'
        ? await adminPlatformApi.suspendMerchant(detail.merchantId, body)
        : await adminPlatformApi.reactivateMerchant(detail.merchantId, body)
      setDetail(response.merchant)
      setConfirm(null)
      list.reload()
      const count = response.affectedShopIds?.length ?? 0
      notify(!response.changed
        ? tp('merchants.unchanged')
        : tp(confirm === 'suspend' ? 'merchants.suspended' : 'merchants.reactivated', { count: formatCount(count) }))
    } catch (error) {
      setActionError(errorMessage(error, tp('errors.action')))
    } finally {
      setBusy(false)
    }
  }

  const merchantName = detail?.merchantId === selectedId ? fullName([detail?.title, detail?.firstName, detail?.lastName]) : ''

  return <>
    <form className="ap-filters" onSubmit={submit}>
      <div className="ap-filter-search wide">
        <span className="ap-filter-label">{tp('common.search')}</span>
        <label className="ap-search adm-search"><Search /><input value={draft.q} onChange={event => set('q', event.target.value)} placeholder={tp('merchants.filters.q')} aria-label={tp('merchants.filters.q')} maxLength={100} /></label>
      </div>
      <AdminSelect label={tp('common.status')} value={draft.status} onChange={value => set('status', value)} options={[{ value: '', label: tp('common.all') }, ...MERCHANT_STATUSES.map(value => ({ value, label: tp(`status.${value}`) }))]} />
      <AdminSelect label={tp('merchants.filters.ownerType')} value={draft.ownerType} onChange={value => set('ownerType', value)} options={[{ value: '', label: tp('common.all') }, ...OWNER_TYPES.map(value => ({ value, label: tp(`ownerType.${value}`) }))]} />
      <AdminSelect label={tp('merchants.filters.shopStatus')} value={draft.shopStatus} onChange={value => set('shopStatus', value)} options={[{ value: '', label: tp('common.all') }, ...SHOP_STATUSES.map(value => ({ value, label: tp(`status.${value}`) }))]} />
      <AdminDateRange from={draft.from} to={draft.to} onChange={range => { set('from', range.from); set('to', range.to) }} />
      <div className="ap-filter-actions"><button type="button" className="ap-ghost" onClick={reset}>{tp('common.reset')}</button><button type="submit" className="ap-primary">{tp('common.search')}</button></div>
    </form>

    <section className="ap-card">
      <header className="ap-card-head"><h2>{tp('merchants.listTitle')}</h2><span className="ap-kpi-inline">{tp('common.totalFromServer')}: <b className="num">{list.result ? formatCount(list.result.totalItems) : '—'}</b></span></header>
      <div className="ap-table-scroll">
        <table className="ap-table">
          <thead><tr><th>{tp('merchants.col.name')}</th><th>{tp('merchants.col.ownerType')}</th><th>{tp('common.province')}</th><th>{tp('common.status')}</th><th>{tp('merchants.col.shops')}</th><th>{tp('common.createdAt')}</th></tr></thead>
          <tbody>
            {items.map(row => (
              <tr key={row.merchantId} className={`clickable${selectedId === row.merchantId ? ' selected' : ''}`} tabIndex={0}
                onClick={() => setSelectedId(row.merchantId)} onKeyDown={event => { if (event.key === 'Enter') setSelectedId(row.merchantId) }}>
                <td><b>{dash(row.displayName)}</b></td>
                <td>{tp(`ownerType.${row.ownerType}`, { defaultValue: dash(row.ownerType) })}</td>
                <td>{dash(row.province)}</td>
                <td><Pill tone={statusTone(row.status)}>{tp(`status.${row.status}`, { defaultValue: row.status })}</Pill></td>
                <td className="num">{formatCount(row.shopCount)}<small>{tp('merchants.shopBreakdown', { approved: formatCount(row.approvedShops), suspended: formatCount(row.suspendedShops), pending: formatCount(row.pendingShops) })}</small></td>
                <td className="num nowrap">{formatDateTime(row.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.loading && items.length === 0 && <SkeletonTable rows={8} cols={6} />}
        {!list.loading && list.error && <LoadError message={list.error} onRetry={list.reload} />}
        {!list.loading && !list.error && items.length === 0 && <p className="ap-empty">{tp('merchants.empty')}</p>}
      </div>
      <Pager page={list.page} size={list.size} totalItems={list.result?.totalItems ?? 0} totalPages={list.result?.totalPages ?? 0} itemCount={items.length}
        loading={list.loading} onPage={list.setPage} onSize={value => { list.setSize(value); list.setPage(0) }} />
    </section>

    {selectedId && (
      <Drawer
        title={merchantName || tp('merchants.detailTitle')}
        subtitle={detail ? (
          <>
            <Pill tone={statusTone(detail.status)}>{tp(`status.${detail.status}`, { defaultValue: detail.status })}</Pill>
            <span className="om-muted">{tp(`ownerType.${detail.ownerType}`, { defaultValue: dash(detail.ownerType) })}</span>
          </>
        ) : undefined}
        avatarFallback={<span>{(merchantName || 'M').trim().charAt(0) || 'M'}</span>}
        onClose={closeDrawer}
        footer={detail ? (
          <>
            {detail.status === 'ACTIVE' && <button type="button" className="ap-danger" disabled={!canSuspend || busy} title={deniedTitle(canSuspend)} onClick={() => { setActionError(''); setConfirm('suspend') }}>{tp('merchants.suspend')}</button>}
            {detail.status === 'SUSPENDED' && <button type="button" className="ap-primary" disabled={!canRestore || busy} title={deniedTitle(canRestore)} onClick={() => { setActionError(''); setConfirm('reactivate') }}>{tp('merchants.reactivate')}</button>}
          </>
        ) : undefined}
      >
        {detailError && <LoadError message={detailError} onRetry={() => { const id = selectedId; setSelectedId(null); window.setTimeout(() => setSelectedId(id), 0) }} />}
        {!detail && !detailError && <SkeletonForm fields={6} />}
        {detail && <>
          <section className="ap-section">
            <h3>{tp('merchants.contact')}</h3>
            <dl className="ap-fields">
              <div><dt>{tp('common.name')}</dt><dd>{fullName([detail.title, detail.firstName, detail.lastName])}</dd></div>
              <div><dt>{tp('common.email')}</dt><dd className="num">{dash(detail.email)}</dd></div>
              <div><dt>{tp('common.phone')}</dt><dd className="num">{dash(detail.phone)}</dd></div>
              <div><dt>{tp('common.province')}</dt><dd>{dash(detail.province)}{detail.district ? ` · ${detail.district}` : ''}</dd></div>
              <div><dt>{tp('merchants.account')}</dt><dd>{detail.user ? fullName([detail.user.firstName, detail.user.lastName]) : '—'}{detail.user?.status && <small className="block">{tp(`status.${detail.user.status}`, { defaultValue: detail.user.status })}</small>}</dd></div>
              <div><dt>{tp('common.createdAt')}</dt><dd className="num">{formatDateTime(detail.createdAt)}</dd></div>
            </dl>
          </section>
          <section className="ap-section">
            <h3>{tp('merchants.counts')}</h3>
            <div className="ap-counts">
              <span><b className="num">{formatCount(detail.counts?.shops)}</b>{tp('merchants.countShops')}</span>
              <span><b className="num">{formatCount(detail.counts?.approvedShops)}</b>{tp('status.APPROVED')}</span>
              <span><b className="num">{formatCount(detail.counts?.suspendedShops)}</b>{tp('status.SUSPENDED')}</span>
              <span><b className="num">{formatCount(detail.counts?.products)}</b>{tp('plans.products')}</span>
              <span><b className="num">{formatCount(detail.counts?.orders)}</b>{tp('plans.orders')}</span>
              <span><b className="num">{formatCount(detail.counts?.activeStaff)}</b>{tp('plans.staff')}</span>
            </div>
          </section>
          <SuspensionInfo tp={tp} suspension={detail.suspension} />
          <section className="ap-section">
            <h3>{tp('merchants.shopsOfMerchant')}</h3>
            {(detail.shops ?? []).length === 0 ? <p className="ap-muted">{tp('merchants.noShops')}</p> : (
              <ul className="ap-list">
                {(detail.shops ?? []).map(shop => (
                  <li key={shop.shopId}><span><b>{shop.name}</b><small className="num">#{shop.shopId} · {shop.slug}</small></span><Pill tone={statusTone(shop.status)}>{tp(`status.${shop.status}`, { defaultValue: shop.status })}</Pill></li>
                ))}
              </ul>
            )}
          </section>
          {canAudit && (
            <p className="ap-muted">
              <a href={`/admin/audit-logs?targetType=OWNER&targetId=${encodeURIComponent(detail.merchantId)}&kind=BUSINESS`}>{tp('audit.openForTarget')}</a>
            </p>
          )}
          {actionError && !confirm && <p className="ap-error" role="alert">{actionError}</p>}
        </>}
      </Drawer>
    )}
    {confirm && detail && (
      <ReasonDialog
        title={tp(confirm === 'suspend' ? 'merchants.confirmSuspendTitle' : 'merchants.confirmReactivateTitle')}
        message={tp(confirm === 'suspend' ? 'merchants.confirmSuspendBody' : 'merchants.confirmReactivateBody', { name: fullName([detail.title, detail.firstName, detail.lastName]), count: formatCount(detail.counts?.shops) })}
        danger={confirm === 'suspend'}
        busy={busy}
        error={actionError}
        onCancel={() => { setConfirm(null); setActionError('') }}
        onConfirm={reason => void apply(reason)}
      />
    )}
  </>
}

/* ------------------------------------------------------------------ shops */

type ShopFilters = { q: string; status: string; province: string; from: string; to: string }
const EMPTY_SHOP_FILTERS: ShopFilters = { q: '', status: '', province: '', from: '', to: '' }

function ShopsTab({ tp, notify, localeTag }: { tp: TP; notify: Notify; localeTag: string }) {
  const { can, deniedTitle } = usePermissions()
  const canSuspend = can('PLATFORM_SHOP_SUSPEND')
  const canRestore = can('PLATFORM_SHOP_RESTORE')
  const canAudit = can('PLATFORM_AUDIT_READ')
  const [draft, setDraft] = useState(EMPTY_SHOP_FILTERS)
  const [filters, setFilters] = useState(EMPTY_SHOP_FILTERS)
  const list = usePlatformList<AdminShopRow>(params => adminPlatformApi.shops(params), filters, { errorText: tp('errors.load') })
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<AdminShopDetail | null>(null)
  const [usage, setUsage] = useState<AdminShopUsage | null>(null)
  const [detailError, setDetailError] = useState('')
  const [usageError, setUsageError] = useState('')
  const [confirm, setConfirm] = useState<'suspend' | 'reactivate' | null>(null)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    if (selectedId === null) { setDetail(null); setUsage(null); setDetailError(''); setUsageError(''); return }
    let cancelled = false
    setDetail(null)
    setUsage(null)
    setDetailError('')
    setUsageError('')
    adminPlatformApi.shop(selectedId)
      .then(value => { if (!cancelled) setDetail(value) })
      .catch(error => { if (!cancelled) setDetailError(errorMessage(error, tp('errors.detail'))) })
    adminPlatformApi.shopUsage(selectedId)
      .then(value => { if (!cancelled) setUsage(value) })
      .catch(error => { if (!cancelled) setUsageError(errorMessage(error, tp('errors.usage'))) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  const items = list.result?.items ?? []
  const set = (key: keyof ShopFilters, value: string) => setDraft(current => ({ ...current, [key]: value }))
  const submit = (event: FormEvent) => { event.preventDefault(); setFilters(draft); list.setPage(0) }
  const reset = () => { setDraft(EMPTY_SHOP_FILTERS); setFilters(EMPTY_SHOP_FILTERS); list.setPage(0) }
  const closeDrawer = () => { setSelectedId(null); setConfirm(null); setActionError('') }
  const shownUsage = usage ?? detail?.usage ?? null

  const apply = async (reason: string) => {
    if (!detail || !confirm) return
    setBusy(true)
    setActionError('')
    try {
      const body = { reason }
      const response = confirm === 'suspend'
        ? await adminPlatformApi.suspendShop(detail.shopId, body)
        : await adminPlatformApi.reactivateShop(detail.shopId, body)
      setDetail(response.shop)
      setConfirm(null)
      list.reload()
      notify(!response.changed ? tp('shops.unchanged') : tp(confirm === 'suspend' ? 'shops.suspended' : 'shops.reactivated'))
    } catch (error) {
      setActionError(errorMessage(error, tp('errors.action')))
    } finally {
      setBusy(false)
    }
  }

  return <>
    <form className="ap-filters" onSubmit={submit}>
      <div className="ap-filter-search wide">
        <span className="ap-filter-label">{tp('common.search')}</span>
        <label className="ap-search adm-search"><Search /><input value={draft.q} onChange={event => set('q', event.target.value)} placeholder={tp('shops.filters.q')} aria-label={tp('shops.filters.q')} maxLength={100} /></label>
      </div>
      <AdminSelect label={tp('common.status')} value={draft.status} onChange={value => set('status', value)} options={[{ value: '', label: tp('common.all') }, ...SHOP_STATUSES.map(value => ({ value, label: tp(`status.${value}`) }))]} />
      <label><span>{tp('common.province')}</span><input value={draft.province} onChange={event => set('province', event.target.value)} maxLength={100} /></label>
      <AdminDateRange from={draft.from} to={draft.to} onChange={range => { set('from', range.from); set('to', range.to) }} />
      <div className="ap-filter-actions"><button type="button" className="ap-ghost" onClick={reset}>{tp('common.reset')}</button><button type="submit" className="ap-primary">{tp('common.search')}</button></div>
    </form>

    <section className="ap-card">
      <header className="ap-card-head"><h2>{tp('shops.listTitle')}</h2><span className="ap-kpi-inline">{tp('common.totalFromServer')}: <b className="num">{list.result ? formatCount(list.result.totalItems) : '—'}</b></span></header>
      <div className="ap-table-scroll">
        <table className="ap-table">
          <thead><tr><th>#</th><th>{tp('shops.col.name')}</th><th>{tp('shops.col.merchant')}</th><th>{tp('common.province')}</th><th>{tp('common.status')}</th><th>{tp('plans.staff')}</th><th>{tp('plans.products')}</th><th>{tp('plans.orders')}</th><th>{tp('common.createdAt')}</th></tr></thead>
          <tbody>
            {items.map(row => (
              <tr key={row.shopId} className={`clickable${selectedId === row.shopId ? ' selected' : ''}`} tabIndex={0}
                onClick={() => setSelectedId(row.shopId)} onKeyDown={event => { if (event.key === 'Enter') setSelectedId(row.shopId) }}>
                <td className="num">{row.shopId}</td>
                <td><b>{row.name}</b><small className="num">{row.slug}</small></td>
                <td>{dash(row.merchantName)}</td>
                <td>{dash(row.province)}</td>
                <td><Pill tone={statusTone(row.status)}>{tp(`status.${row.status}`, { defaultValue: row.status })}</Pill></td>
                <td className="num">{formatCount(row.activeMembers)}</td>
                <td className="num">{formatCount(row.products)}</td>
                <td className="num">{formatCount(row.orders)}</td>
                <td className="num nowrap">{formatDateTime(row.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.loading && items.length === 0 && <SkeletonTable rows={8} cols={9} />}
        {!list.loading && list.error && <LoadError message={list.error} onRetry={list.reload} />}
        {!list.loading && !list.error && items.length === 0 && <p className="ap-empty">{tp('shops.empty')}</p>}
      </div>
      <Pager page={list.page} size={list.size} totalItems={list.result?.totalItems ?? 0} totalPages={list.result?.totalPages ?? 0} itemCount={items.length}
        loading={list.loading} onPage={list.setPage} onSize={value => { list.setSize(value); list.setPage(0) }} />
    </section>

    {selectedId !== null && (
      <Drawer
        title={detail?.name || tp('shops.detailTitle')}
        subtitle={detail ? (
          <>
            <Pill tone={statusTone(detail.status)}>{tp(`status.${detail.status}`, { defaultValue: detail.status })}</Pill>
            <span className="om-muted num">#{detail.shopId} · {detail.slug}</span>
          </>
        ) : undefined}
        avatarFallback={<Building2 />}
        onClose={closeDrawer}
        wide
        footer={detail ? (
          <>
            {detail.status === 'APPROVED' && <button type="button" className="ap-danger" disabled={!canSuspend || busy} title={deniedTitle(canSuspend)} onClick={() => { setActionError(''); setConfirm('suspend') }}>{tp('shops.suspend')}</button>}
            {detail.status === 'SUSPENDED' && <button type="button" className="ap-primary" disabled={!canRestore || busy} title={deniedTitle(canRestore)} onClick={() => { setActionError(''); setConfirm('reactivate') }}>{tp('shops.reactivate')}</button>}
          </>
        ) : undefined}
      >
        {detailError && <LoadError message={detailError} onRetry={() => { const id = selectedId; setSelectedId(null); window.setTimeout(() => setSelectedId(id), 0) }} />}
        {!detail && !detailError && <SkeletonForm fields={6} />}
        {detail && <>
          <section className="ap-section">
            <h3>{tp('shops.info')}</h3>
            <dl className="ap-fields">
              <div><dt>{tp('shops.col.name')}</dt><dd>{detail.name}{detail.nameEn ? <small className="block">{detail.nameEn}</small> : null}</dd></div>
              <div><dt>{tp('shops.col.merchant')}</dt><dd>{dash(detail.merchantName)}{detail.merchantStatus && <small className="block">{tp(`status.${detail.merchantStatus}`, { defaultValue: detail.merchantStatus })}</small>}</dd></div>
              <div><dt>{tp('shops.owner')}</dt><dd>{detail.owner ? fullName([detail.owner.firstName, detail.owner.lastName]) : '—'}</dd></div>
              <div><dt>{tp('shops.category')}</dt><dd>{dash(detail.category)}</dd></div>
              <div className="full"><dt>{tp('shops.address')}</dt><dd>{[detail.address, detail.subdistrict, detail.district, detail.province, detail.postalCode].filter(Boolean).join(' ') || '—'}</dd></div>
              <div><dt>{tp('common.createdAt')}</dt><dd className="num">{formatDateTime(detail.createdAt)}</dd></div>
              <div><dt>{tp('shops.publishedAt')}</dt><dd className="num">{formatDateTime(detail.publishedAt)}</dd></div>
            </dl>
            {detail.pendingReinstatementMessage && <p className="ap-note"><b>{tp('shops.reinstatement')}:</b> {detail.pendingReinstatementMessage}</p>}
          </section>
          <SuspensionInfo tp={tp} suspension={detail.suspension} />
          <section className="ap-section">
            <h3>{tp('shops.usage')}</h3>
            {usageError && !shownUsage ? <p className="ap-error" role="alert">{usageError}</p>
              : shownUsage ? <UsageGrid tp={tp} usage={shownUsage} localeTag={localeTag} /> : <SkeletonForm fields={2} />}
          </section>
          <section className="ap-section">
            <h3>{tp('shops.members')} <span className="num">({formatCount((detail.members ?? []).length)})</span></h3>
            {(detail.members ?? []).length === 0 ? <p className="ap-muted">{tp('shops.noMembers')}</p> : (
              <ul className="ap-list">
                {(detail.members ?? []).map(member => (
                  <li key={`${member.userId}-${member.joinedAt ?? ''}`}><span><b>{fullName([member.firstName, member.lastName])}</b><small>{member.roleNameTh || member.role}</small></span><Pill tone={statusTone(member.status)}>{tp(`status.${member.status}`, { defaultValue: member.status })}</Pill></li>
                ))}
              </ul>
            )}
          </section>
          {canAudit && (
            <p className="ap-muted">
              <a href={`/admin/audit-logs?targetType=SHOP&targetId=${detail.shopId}&kind=BUSINESS`}>{tp('audit.openForTarget')}</a>
            </p>
          )}
          {actionError && !confirm && <p className="ap-error" role="alert">{actionError}</p>}
        </>}
      </Drawer>
    )}
    {confirm && detail && (
      <ReasonDialog
        title={tp(confirm === 'suspend' ? 'shops.confirmSuspendTitle' : 'shops.confirmReactivateTitle')}
        message={tp(confirm === 'suspend' ? 'shops.confirmSuspendBody' : 'shops.confirmReactivateBody', { name: detail.name })}
        danger={confirm === 'suspend'}
        busy={busy}
        error={actionError}
        onCancel={() => { setConfirm(null); setActionError('') }}
        onConfirm={reason => void apply(reason)}
      />
    )}
  </>
}

/* ------------------------------------------------------------------ plans & usage */

type PlanFilters = { q: string; status: string }
const EMPTY_PLAN_FILTERS: PlanFilters = { q: '', status: '' }

function PlansTab({ tp }: { tp: TP }) {
  const [catalog, setCatalog] = useState<AdminPlanCatalog | null>(null)
  const [catalogError, setCatalogError] = useState('')
  const [catalogKey, setCatalogKey] = useState(0)
  const [draft, setDraft] = useState(EMPTY_PLAN_FILTERS)
  const [filters, setFilters] = useState(EMPTY_PLAN_FILTERS)
  const list = usePlatformList<AdminShopUsage>(params => adminPlatformApi.planUsage(params), filters, { errorText: tp('errors.load') })

  useEffect(() => {
    let cancelled = false
    setCatalogError('')
    adminPlatformApi.plans()
      .then(value => { if (!cancelled) setCatalog(value) })
      .catch(error => { if (!cancelled) setCatalogError(errorMessage(error, tp('errors.load'))) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [catalogKey])

  const items = list.result?.items ?? []
  const noPlans = catalog?.source === 'NONE' || (catalog !== null && (catalog.plans ?? []).length === 0)
  const limit = (value: number | null | undefined, format: (n: number) => string = formatCount) => (value === null || value === undefined ? '—' : format(value))
  const submit = (event: FormEvent) => { event.preventDefault(); setFilters(draft); list.setPage(0) }

  return <>
    <section className="ap-card">
      <header className="ap-card-head"><h2>{tp('plans.catalogTitle')}</h2></header>
      <div className="ap-pad">
        {catalogError ? <LoadError message={catalogError} onRetry={() => setCatalogKey(key => key + 1)} />
          : !catalog ? <SkeletonForm fields={2} />
          : noPlans ? <p className="ap-empty" role="status">{catalog.note || tp('plans.emptyCatalog')}</p>
          : <div className="ap-table-scroll"><table className="ap-table">
              <thead><tr><th>{tp('plans.code')}</th><th>{tp('plans.name')}</th><th>{tp('plans.products')}</th><th>{tp('plans.ordersPerMonth')}</th><th>{tp('plans.staff')}</th><th>{tp('plans.storage')}</th></tr></thead>
              <tbody>{catalog.plans.map(plan => (
                <tr key={plan.code}><td className="num">{plan.code}</td><td><b>{plan.name}</b></td>
                  <td className="num">{limit(plan.limits?.products)}</td><td className="num">{limit(plan.limits?.ordersPerMonth)}</td>
                  <td className="num">{limit(plan.limits?.staff)}</td><td className="num">{limit(plan.limits?.storageBytes, formatBytes)}</td></tr>
              ))}</tbody>
            </table></div>}
        {catalog && !noPlans && catalog.note && <p className="ap-muted">{catalog.note}</p>}
      </div>
    </section>

    <form className="ap-filters" onSubmit={submit}>
      <div className="ap-filter-search wide">
        <span className="ap-filter-label">{tp('common.search')}</span>
        <label className="ap-search adm-search"><Search /><input value={draft.q} onChange={event => setDraft({ ...draft, q: event.target.value })} placeholder={tp('plans.filters.q')} aria-label={tp('plans.filters.q')} maxLength={100} /></label>
      </div>
      <AdminSelect label={tp('common.status')} value={draft.status} onChange={value => setDraft({ ...draft, status: value })} options={[{ value: '', label: tp('common.all') }, ...SHOP_STATUSES.map(value => ({ value, label: tp(`status.${value}`) }))]} />
      <div className="ap-filter-actions"><button type="button" className="ap-ghost" onClick={() => { setDraft(EMPTY_PLAN_FILTERS); setFilters(EMPTY_PLAN_FILTERS); list.setPage(0) }}>{tp('common.reset')}</button><button type="submit" className="ap-primary">{tp('common.search')}</button></div>
    </form>

    <section className="ap-card">
      <header className="ap-card-head"><h2>{tp('plans.usageTitle')}</h2><span className="ap-kpi-inline">{tp('common.totalFromServer')}: <b className="num">{list.result ? formatCount(list.result.totalItems) : '—'}</b></span></header>
      <div className="ap-table-scroll">
        <table className="ap-table">
          <thead><tr><th>{tp('shops.col.name')}</th><th>{tp('common.status')}</th><th>{tp('plans.plan')}</th><th>{tp('plans.products')}</th><th>{tp('plans.orders')}</th><th>{tp('plans.staff')}</th><th>{tp('plans.storage')}</th></tr></thead>
          <tbody>
            {items.map(row => (
              <tr key={row.shopId}>
                <td><b>{row.shopName}</b><small className="num">#{row.shopId}</small></td>
                <td><Pill tone={statusTone(row.status)}>{tp(`status.${row.status}`, { defaultValue: row.status })}</Pill></td>
                <td className="num">{row.planCode || tp('plans.noPlan')}</td>
                <td className="num">{formatCount(row.products?.total)}{row.limits?.products != null && <small>/ {formatCount(row.limits.products)}</small>}</td>
                <td className="num">{formatCount(row.orders?.total)}<small>{tp('plans.last30', { count: formatCount(row.orders?.last30Days) })}</small></td>
                <td className="num">{formatCount(row.staff?.active)}{row.limits?.staff != null && <small>/ {formatCount(row.limits.staff)}</small>}</td>
                <td className="num">{formatBytes(row.storage?.trackedBytes)}{row.limits?.storageBytes != null && <small>/ {formatBytes(row.limits.storageBytes)}</small>}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.loading && items.length === 0 && <SkeletonTable rows={8} cols={7} />}
        {!list.loading && list.error && <LoadError message={list.error} onRetry={list.reload} />}
        {!list.loading && !list.error && items.length === 0 && <p className="ap-empty">{tp('plans.empty')}</p>}
      </div>
      <Pager page={list.page} size={list.size} totalItems={list.result?.totalItems ?? 0} totalPages={list.result?.totalPages ?? 0} itemCount={items.length}
        loading={list.loading} onPage={list.setPage} onSize={value => { list.setSize(value); list.setPage(0) }} />
    </section>
  </>
}

/* ------------------------------------------------------------------ page */

export default function AdminMerchantsPage() {
  const { t, i18n } = useTranslation('admin')
  const tp: TP = (key, options) => t(`adminPlatform.${key}`, options)
  const { user, loading } = useAuth()
  const { can, deniedTitle } = usePermissions()
  const canRead = can('PLATFORM_SHOP_READ')
  const [searchParams] = useSearchParams()
  const merchantParam = searchParams.get('merchant')
  const [tab, setTab] = useState<Tab>('merchants')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (merchantParam) setTab('merchants')
  }, [merchantParam])

  useEffect(() => {
    if (!notice) return
    const timer = window.setTimeout(() => setNotice(''), 3600)
    return () => window.clearTimeout(timer)
  }, [notice])

  if (loading) return <main className="auth-loading">{tp('common.checkingAccess')}</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'merchants', label: tp('merchants.tab') },
    { key: 'shops', label: tp('shops.tab') },
    { key: 'plans', label: tp('plans.tab') },
  ]

  return (
    <main className="admin-shell platform-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="merchants" className="platform-sidebar" />
      <div className="admin-content platform-content">
        <section className="ap-heading">
          <span className="ap-heading-icon"><Building2 /></span>
          <div><h1>{tp('merchants.title')}</h1><p>{tp('merchants.subtitle')}</p></div>
        </section>
        {!canRead ? <div className="ap-card"><p className="ap-empty" title={deniedTitle(false)}>{tp('errors.noPermission')}</p></div> : <>
          <nav className="ap-tabs" role="tablist" aria-label={tp('merchants.title')}>
            {tabs.map(item => <button key={item.key} type="button" role="tab" aria-selected={tab === item.key} className={tab === item.key ? 'active' : ''} onClick={() => setTab(item.key)}>{item.label}</button>)}
          </nav>
          {tab === 'merchants' && <MerchantsTab tp={tp} notify={setNotice} initialMerchantId={merchantParam} />}
          {tab === 'shops' && <ShopsTab tp={tp} notify={setNotice} localeTag={i18n.language} />}
          {tab === 'plans' && <PlansTab tp={tp} />}
        </>}
      </div>
      {notice && <div className="ap-toast" role="status">{notice}</div>}
    </main>
  )
}
