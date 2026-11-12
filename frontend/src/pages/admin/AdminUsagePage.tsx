import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Gauge, Search } from 'lucide-react'
import AdminHeader from '../../components/AdminHeader'
import AdminSidebar from '../../components/AdminSidebar'
import { LoadError, SkeletonForm, SkeletonTable } from '../../components/feedback/Skeleton'
import AdminSelect from '../../components/admin/AdminSelect'
import AdminChangePlanDialog from '../../components/admin/AdminChangePlanDialog'
import ConfirmDialog from '../../components/form/ConfirmDialog'
import {
  Drawer, Pager, Pill, errorMessage, formatBytes, formatCount, statusTone, usePlatformList,
} from '../../components/admin/PlatformBits'
import { QuotaBar, buildUsageQuotas, quotaRatio, quotaTone } from '../../components/admin/QuotaBar'
import { useAuth } from '../../contexts/AuthContext'
import { usePermissions } from '../../hooks/usePermissions'
import { hasPlatformAccess } from '../../services/permissions'
import { formatBaht } from '../../components/cart/cartFormat'
import {
  ApiError,
  adminPlanPurchaseApi,
  adminPlatformApi,
  moneyValue,
  type AdminPlan,
  type AdminPlanCatalog,
  type AdminPlanUsageSummary,
  type AdminShopUsage,
  type PlanPurchaseOrder,
} from '../../services/api'
import '../../styles/admin-dashboard.css'
import '../../styles/admin-platform.css'
import '../../styles/admin-form-controls.css'

type TP = (key: string, options?: Record<string, unknown>) => string
type Filters = { q: string; status: string; plan: string }
const EMPTY: Filters = { q: '', status: '', plan: '' }
const SHOP_STATUSES = ['APPROVED', 'PENDING_REVIEW', 'SUSPENDED', 'REJECTED']
const NO_PLAN = '__NONE__'

type PlanSummary = {
  key: string
  code: string | null
  name: string
  description?: string | null
  priceMonthly?: number | string | null
  priceYearly?: number | string | null
  sortOrder?: number | null
  limits?: AdminPlan['limits']
  shopCount: number
  products: number
  staff: number
  storageBytes: number
  ordersLast30: number
}

function formatLimit(value: number | null | undefined, tp: TP, bytes = false): string {
  if (value == null) return tp('usage.unlimited')
  return bytes ? formatBytes(value) : formatCount(value)
}

function formatPlanMonthly(plan: AdminPlan, locale: string, tp: TP): string | null {
  if (plan.priceMonthly == null || moneyValue(plan.priceMonthly) <= 0) return null
  return tp('usage.priceMonthly', { price: formatBaht(plan.priceMonthly, locale) })
}

function planOptionLabel(plan: AdminPlan, locale: string, tp: TP): string {
  const price = formatPlanMonthly(plan, locale, tp)
  if (!price) return plan.name || plan.code
  return tp('usage.planFilterLabel', { name: plan.name || plan.code, price })
}

function summariesFromServer(
  rows: AdminPlanUsageSummary[],
  catalog: AdminPlanCatalog | null,
  tp: TP,
): PlanSummary[] {
  const byKey = new Map<string, PlanSummary>()
  const ensure = (code: string | null, name?: string | null) => {
    const key = code || NO_PLAN
    let row = byKey.get(key)
    if (!row) {
      row = {
        key,
        code,
        name: name || (code ? code : tp('usage.noPlan')),
        shopCount: 0,
        products: 0,
        staff: 0,
        storageBytes: 0,
        ordersLast30: 0,
      }
      byKey.set(key, row)
    }
    return row
  }
  for (const plan of catalog?.plans ?? []) {
    const bucket = ensure(plan.code, plan.name)
    bucket.priceMonthly = plan.priceMonthly
    bucket.priceYearly = plan.priceYearly
    bucket.description = plan.description
    bucket.sortOrder = plan.sortOrder
    bucket.limits = plan.limits
  }
  for (const row of rows) {
    const bucket = ensure(row.planCode ?? null, row.planName)
    bucket.shopCount = row.shopCount
    bucket.products = row.products
    bucket.staff = row.staff
    bucket.storageBytes = row.storageBytes
    bucket.ordersLast30 = row.ordersLast30Days
  }
  if (byKey.size === 0) ensure(null)
  return [...byKey.values()].sort((a, b) => {
    const ao = a.sortOrder ?? (a.code ? 99 : 100)
    const bo = b.sortOrder ?? (b.code ? 99 : 100)
    if (ao !== bo) return ao - bo
    return a.name.localeCompare(b.name)
  })
}

export default function AdminUsagePage() {
  const { t, i18n } = useTranslation('admin')
  const localeTag = i18n.language?.startsWith('zh') ? 'zh-CN' : i18n.language?.startsWith('en') ? 'en-US' : 'th-TH'
  const tp: TP = (key, options) => t(`adminPlatform.${key}`, options)
  const { user, loading } = useAuth()
  const { can, deniedTitle } = usePermissions()
  const canRead = can('PLATFORM_SHOP_READ')
  const canAssignPlan = can('PLATFORM_SHOP_SUSPEND')

  const [draft, setDraft] = useState(EMPTY)
  const [filters, setFilters] = useState(EMPTY)
  const [planTarget, setPlanTarget] = useState<AdminShopUsage | null>(null)
  const [planNotice, setPlanNotice] = useState('')
  const listFilters = useMemo(
    () => ({ q: filters.q, status: filters.status, plan: filters.plan }),
    [filters.q, filters.status, filters.plan],
  )
  const list = usePlatformList<AdminShopUsage>(
    params => adminPlatformApi.planUsage({ ...params, sort: 'shopId', dir: 'asc' }),
    listFilters,
    { enabled: canRead, errorText: tp('errors.load') },
  )

  const [catalog, setCatalog] = useState<AdminPlanCatalog | null>(null)
  const [summaryRows, setSummaryRows] = useState<AdminPlanUsageSummary[] | null>(null)
  const [summaryError, setSummaryError] = useState('')
  const [summaryKey, setSummaryKey] = useState(0)

  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detailKey, setDetailKey] = useState(0)
  const [detail, setDetail] = useState<AdminShopUsage | null>(null)
  const [detailError, setDetailError] = useState('')
  const [detailLoading, setDetailLoading] = useState(false)

  const [purchaseStatus, setPurchaseStatus] = useState('PENDING_REVIEW')
  const [purchases, setPurchases] = useState<PlanPurchaseOrder[] | null>(null)
  const [purchaseError, setPurchaseError] = useState('')
  const [purchaseKey, setPurchaseKey] = useState(0)
  const [purchaseView, setPurchaseView] = useState<PlanPurchaseOrder | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [rejectOpen, setRejectOpen] = useState(false)
  const [purchaseBusy, setPurchaseBusy] = useState(false)

  useEffect(() => {
    if (!canRead) return
    let cancelled = false
    setSummaryError('')
    Promise.all([
      adminPlatformApi.plans(),
      adminPlatformApi.planUsageSummary(),
    ])
      .then(([plans, summary]) => {
        if (cancelled) return
        setCatalog(plans)
        setSummaryRows(summary)
      })
      .catch(error => {
        if (cancelled) return
        setSummaryError(error instanceof ApiError && error.status === 403
          ? tp('errors.noPermission')
          : errorMessage(error, tp('errors.load')))
      })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canRead, summaryKey])

  useEffect(() => {
    if (!canRead) return
    let cancelled = false
    setPurchaseError('')
    adminPlanPurchaseApi.list(purchaseStatus || null)
      .then((rows) => { if (!cancelled) setPurchases(rows) })
      .catch((error) => {
        if (cancelled) return
        setPurchaseError(error instanceof ApiError && error.status === 403
          ? tp('errors.noPermission')
          : errorMessage(error, tp('errors.load')))
        setPurchases([])
      })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canRead, purchaseStatus, purchaseKey])

  useEffect(() => {
    if (selectedId == null || !canRead) { setDetail(null); setDetailError(''); return }
    let cancelled = false
    setDetailLoading(true)
    setDetailError('')
    setDetail(null)
    adminPlatformApi.shopUsage(selectedId)
      .then(value => { if (!cancelled) setDetail(value) })
      .catch(error => {
        if (cancelled) return
        const message = error instanceof ApiError && error.status === 403
          ? tp('errors.noPermission')
          : errorMessage(error, tp('errors.usage'))
        setDetailError(message)
      })
      .finally(() => { if (!cancelled) setDetailLoading(false) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId, canRead, detailKey])

  const summaries = useMemo(
    () => summariesFromServer(summaryRows ?? [], catalog, tp),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [summaryRows, catalog],
  )

  const planOptions = useMemo(() => {
    const fromCatalog: AdminPlan[] = catalog?.plans ?? []
    const codes = new Set(fromCatalog.map(plan => plan.code))
    const extras = (summaryRows ?? [])
      .map(row => row.planCode)
      .filter((code): code is string => !!code && !codes.has(code))
    const uniqueExtras = [...new Set(extras)]
    return [
      { value: '', label: tp('common.all') },
      { value: NO_PLAN, label: tp('usage.noPlan') },
      ...fromCatalog.map(plan => ({ value: plan.code, label: planOptionLabel(plan, localeTag, tp) })),
      ...uniqueExtras.map(code => ({ value: code, label: code })),
    ]
  }, [catalog, summaryRows, tp, localeTag])

  const items = list.result?.items ?? []
  const selectedRow = items.find(row => row.shopId === selectedId) ?? null

  if (loading) return <main className="auth-loading">{tp('common.checkingAccess')}</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setFilters(draft)
    list.setPage(0)
  }
  const reset = () => {
    setDraft(EMPTY)
    setFilters(EMPTY)
    list.setPage(0)
  }

  return (
    <main className="admin-shell platform-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="usage" className="platform-sidebar" />
      <div className="admin-content platform-content">
        <section className="ap-heading">
          <span className="ap-heading-icon"><Gauge /></span>
          <div>
            <h1>{tp('usage.title')}</h1>
            <p>{tp('usage.subtitle')}</p>
          </div>
          <div className="ap-kpi" aria-live="polite">
            <small>{tp('common.totalFromServer')}</small>
            <strong className="num">{list.result ? formatCount(list.result.totalItems) : '—'}</strong>
          </div>
        </section>

        {!canRead ? (
          <div className="ap-card"><p className="ap-empty" title={deniedTitle(false)}>{tp('errors.noPermission')}</p></div>
        ) : <>
          <section className="ap-card" aria-label={tp('usage.summaryAria')}>
            <header className="ap-card-head"><h2>{tp('usage.summaryTitle')}</h2></header>
            <div className="ap-pad">
              {summaryError ? <LoadError message={summaryError} onRetry={() => setSummaryKey(key => key + 1)} />
                : summaryRows == null ? <SkeletonForm fields={2} />
                : (
                  <div className="ap-usage ap-usage-summary ap-plan-catalog">
                    {summaries.map(plan => (
                      <article key={plan.key}>
                        <small>{plan.name}{plan.code ? ` · ${plan.code}` : ''}</small>
                        {plan.description ? <em className="ap-plan-desc">{plan.description}</em> : null}
                        <span className="ap-plan-price num">
                          {plan.priceMonthly != null && moneyValue(plan.priceMonthly) > 0
                            ? tp('usage.priceMonthly', { price: formatBaht(plan.priceMonthly, localeTag) })
                            : tp('usage.priceFree')}
                        </span>
                        {plan.priceYearly != null && moneyValue(plan.priceYearly) > 0 ? (
                          <span className="ap-plan-price-muted num">{tp('usage.priceYearly', { price: formatBaht(plan.priceYearly, localeTag) })}</span>
                        ) : null}
                        <strong className="num">{tp('usage.shopCount', { count: formatCount(plan.shopCount) })}</strong>
                        <ul className="ap-plan-limits">
                          <li>{tp('usage.limitProducts', { value: formatLimit(plan.limits?.products, tp) })}</li>
                          <li>{tp('usage.limitOrders', { value: formatLimit(plan.limits?.ordersPerMonth, tp) })}</li>
                          <li>{tp('usage.limitStaff', { value: formatLimit(plan.limits?.staff, tp) })}</li>
                          <li>{tp('usage.limitStorage', { value: formatLimit(plan.limits?.storageBytes, tp, true) })}</li>
                        </ul>
                      </article>
                    ))}
                  </div>
                )}
            </div>
          </section>

          <form className="ap-filters" onSubmit={submit}>
            <div className="ap-filter-search wide">
              <span className="ap-filter-label">{tp('common.search')}</span>
              <label className="ap-search adm-search">
                <Search />
                <input
                  value={draft.q}
                  onChange={event => setDraft({ ...draft, q: event.target.value })}
                  placeholder={tp('usage.filters.q')}
                  aria-label={tp('usage.filters.q')}
                  maxLength={100}
                />
              </label>
            </div>
            <AdminSelect
              label={tp('usage.filters.plan')}
              value={draft.plan}
              onChange={value => setDraft({ ...draft, plan: value })}
              options={planOptions}
            />
            <AdminSelect
              label={tp('common.status')}
              value={draft.status}
              onChange={value => setDraft({ ...draft, status: value })}
              options={[{ value: '', label: tp('common.all') }, ...SHOP_STATUSES.map(value => ({ value, label: tp(`status.${value}`) }))]}
            />
            <div className="ap-filter-actions">
              <button type="button" className="ap-ghost" onClick={reset}>{tp('common.reset')}</button>
              <button type="submit" className="ap-primary">{tp('common.search')}</button>
            </div>
          </form>

          <section className="ap-card" aria-label={tp('usage.purchases.aria')}>
            <header className="ap-card-head">
              <h2>{tp('usage.purchases.title')}</h2>
              <AdminSelect
                label={tp('usage.purchases.filterStatus')}
                value={purchaseStatus}
                onChange={setPurchaseStatus}
                options={[
                  { value: '', label: tp('common.all') },
                  { value: 'PENDING_REVIEW', label: tp('usage.purchases.status.PENDING_REVIEW') },
                  { value: 'PENDING_PAYMENT', label: tp('usage.purchases.status.PENDING_PAYMENT') },
                  { value: 'ACTIVE', label: tp('usage.purchases.status.ACTIVE') },
                  { value: 'REJECTED', label: tp('usage.purchases.status.REJECTED') },
                  { value: 'CANCELLED', label: tp('usage.purchases.status.CANCELLED') },
                  { value: 'EXPIRED', label: tp('usage.purchases.status.EXPIRED') },
                ]}
              />
            </header>
            <div className="ap-table-scroll">
              {purchaseError ? <LoadError message={purchaseError} onRetry={() => setPurchaseKey((k) => k + 1)} />
                : purchases == null ? <SkeletonTable rows={4} cols={6} />
                : purchases.length === 0 ? <p className="ap-empty">{tp('usage.purchases.empty')}</p>
                : (
                  <table className="ap-table">
                    <thead>
                      <tr>
                        <th>{tp('usage.purchases.col.shop')}</th>
                        <th>{tp('usage.purchases.col.plan')}</th>
                        <th>{tp('usage.purchases.col.cycle')}</th>
                        <th>{tp('usage.purchases.col.amount')}</th>
                        <th>{tp('usage.purchases.col.status')}</th>
                        <th>{tp('usage.purchases.col.created')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {purchases.map((row) => (
                        <tr
                          key={row.id}
                          className="clickable"
                          tabIndex={0}
                          onClick={() => setPurchaseView(row)}
                          onKeyDown={(event) => { if (event.key === 'Enter') setPurchaseView(row) }}
                        >
                          <td><b>{row.shopName}</b><small className="num">#{row.shopId}</small></td>
                          <td>{row.planName}</td>
                          <td>{tp(`usage.purchases.cycle.${row.billingCycle}`)}</td>
                          <td className="num">{formatBaht(row.amount, localeTag)}</td>
                          <td><Pill tone={row.status === 'PENDING_REVIEW' || row.status === 'PENDING_PAYMENT' ? 'warn' : row.status === 'ACTIVE' ? 'ok' : 'bad'}>{tp(`usage.purchases.status.${row.status}`)}</Pill></td>
                          <td>{new Date(row.createdAt).toLocaleString(localeTag)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
            </div>
          </section>

          <section className="ap-card">
            <header className="ap-card-head">
              <h2>{tp('usage.listTitle')}</h2>
              <span className="ap-kpi-inline">{tp('common.totalFromServer')}: <b className="num">{list.result ? formatCount(list.result.totalItems) : '—'}</b></span>
            </header>
            <div className="ap-table-scroll">
              <table className="ap-table">
                <thead>
                  <tr>
                    <th>{tp('usage.col.shop')}</th>
                    <th>{tp('usage.col.slug')}</th>
                    <th>{tp('common.status')}</th>
                    <th>{tp('usage.col.plan')}</th>
                    <th>{tp('usage.col.products')}</th>
                    <th>{tp('usage.col.orders')}</th>
                    <th>{tp('usage.col.staff')}</th>
                    <th>{tp('usage.col.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(row => (
                    <tr
                      key={row.shopId}
                      className={`clickable${selectedId === row.shopId ? ' selected' : ''}`}
                      tabIndex={0}
                      onClick={() => setSelectedId(row.shopId)}
                      onKeyDown={event => { if (event.key === 'Enter') setSelectedId(row.shopId) }}
                    >
                      <td><b>{row.shopName}</b><small className="num">#{row.shopId}</small></td>
                      <td className="num">{row.slug || '—'}</td>
                      <td><Pill tone={statusTone(row.status)}>{tp(`status.${row.status}`, { defaultValue: row.status })}</Pill></td>
                      <td className="num">{row.planCode || tp('usage.noPlan')}</td>
                      <td className="num">
                        {formatCount(row.products?.published ?? 0)}
                      </td>
                      <td className="num">{formatCount(row.orders?.total)}</td>
                      <td className="num">{formatCount(row.staff?.active)}</td>
                      <td>
                        <button
                          type="button"
                          className="ap-ghost"
                          disabled={!canAssignPlan}
                          title={deniedTitle(canAssignPlan)}
                          onClick={(event) => {
                            event.stopPropagation()
                            if (!canAssignPlan) return
                            setPlanTarget(row)
                          }}
                        >
                          {tp('usage.changePlan')}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {list.loading && items.length === 0 && <SkeletonTable rows={8} cols={8} />}
              {!list.loading && list.error && <LoadError message={list.error} onRetry={list.reload} />}
              {!list.loading && !list.error && items.length === 0 && <p className="ap-empty">{tp('usage.empty')}</p>}
            </div>
            <Pager
              page={list.page}
              size={list.size}
              totalItems={list.result?.totalItems ?? 0}
              totalPages={list.result?.totalPages ?? 0}
              itemCount={items.length}
              loading={list.loading}
              onPage={list.setPage}
              onSize={value => { list.setSize(value); list.setPage(0) }}
            />
          </section>
        </>}
      </div>

      {selectedId != null && (
        <Drawer
          title={detail?.shopName || selectedRow?.shopName || tp('usage.detailTitle')}
          subtitle={detail ? (
            <>
              <Pill tone={statusTone(detail.status)}>{tp(`status.${detail.status}`, { defaultValue: detail.status })}</Pill>
              <span className="om-muted">{detail.planCode || tp('usage.noPlan')} · {tp('usage.detailSubtitle', { id: selectedId })}</span>
            </>
          ) : tp('usage.detailSubtitle', { id: selectedId })}
          avatarFallback={<span>{(detail?.shopName || selectedRow?.shopName || 'U').trim().charAt(0)}</span>}
          onClose={() => setSelectedId(null)}
          wide
        >
          {detailError && !detail ? <LoadError message={detailError} onRetry={() => setDetailKey(key => key + 1)} />
            : detailLoading || !detail ? <SkeletonForm fields={4} />
            : (
              <>
                <div className="store-drawer-plan-toolbar">
                  <button
                    type="button"
                    className="ap-ghost"
                    disabled={!canAssignPlan}
                    title={deniedTitle(canAssignPlan)}
                    onClick={() => { if (canAssignPlan) setPlanTarget(detail) }}
                  >
                    {tp('usage.changePlan')}
                  </button>
                </div>
                <div className="ap-quota-list">
                  {buildUsageQuotas(detail, tp).map(row => {
                    const displayUsed = row.format ? row.format(row.used) : formatCount(row.used)
                    const displayLimit = row.limit == null
                      ? tp('usage.unlimited')
                      : (row.format ? row.format(row.limit) : formatCount(row.limit))
                    const pct = quotaRatio(row.used, row.limit)
                    const tone = quotaTone(row.used, row.limit, pct)
                    return (
                      <article key={row.key} className={`ap-quota-card ${tone}`}>
                        <header>
                          <b>{row.label}</b>
                          <strong className="num">{displayUsed}{row.limit != null ? ` / ${displayLimit}` : ` · ${tp('usage.unlimited')}`}</strong>
                        </header>
                        <QuotaBar
                          used={row.used}
                          limit={row.limit}
                          unlimitedLabel={tp('usage.unlimited')}
                          format={row.format}
                          showCaption={false}
                        />
                        {row.hint && <span>{row.hint}</span>}
                      </article>
                    )
                  })}
                </div>
              </>
            )}
        </Drawer>
      )}
      {planTarget ? (
        <AdminChangePlanDialog
          open
          shopId={planTarget.shopId}
          shopName={planTarget.shopName}
          currentPlanCode={planTarget.planCode}
          usage={planTarget}
          onClose={() => setPlanTarget(null)}
          onAssigned={(response) => {
            const next = response.shop.usage ?? null
            if (next) {
              setPlanTarget(null)
              if (selectedId === next.shopId) setDetail(next)
              setDetailKey((key) => key + 1)
              setSummaryKey((key) => key + 1)
              list.reload()
            } else {
              setPlanTarget(null)
              list.reload()
              setSummaryKey((key) => key + 1)
            }
            setPlanNotice(t('adminPlatform.stores.detail.changePlanDialog.success', { name: response.shop.name }))
            window.setTimeout(() => setPlanNotice(''), 2200)
          }}
        />
      ) : null}
      {purchaseView ? (
        <Drawer
          title={purchaseView.shopName}
          subtitle={`${purchaseView.planName} · ${tp(`usage.purchases.cycle.${purchaseView.billingCycle}`)} · ${formatBaht(purchaseView.amount, localeTag)}`}
          onClose={() => { setPurchaseView(null); setRejectOpen(false); setRejectReason('') }}
          wide
          footer={(
            <>
              {(purchaseView.status === 'PENDING_REVIEW' || purchaseView.status === 'PENDING_PAYMENT') && canAssignPlan ? (
                <>
                  <button
                    type="button"
                    className="ap-primary"
                    disabled={purchaseBusy}
                    onClick={() => {
                      setPurchaseBusy(true)
                      adminPlanPurchaseApi.approve(purchaseView.id)
                        .then((row) => {
                          setPurchaseView(row)
                          setPurchaseKey((k) => k + 1)
                          setSummaryKey((k) => k + 1)
                          list.reload()
                          setPlanNotice(tp('usage.purchases.approved'))
                          window.setTimeout(() => setPlanNotice(''), 2200)
                          setPurchaseView(null)
                        })
                        .catch((error) => setPlanNotice(error instanceof ApiError ? error.message : tp('errors.load')))
                        .finally(() => setPurchaseBusy(false))
                    }}
                  >
                    {tp('usage.purchases.approve')}
                  </button>
                  <button type="button" className="ap-ghost" disabled={purchaseBusy} onClick={() => setRejectOpen(true)}>
                    {tp('usage.purchases.reject')}
                  </button>
                </>
              ) : null}
              <button type="button" className="ap-ghost" onClick={() => setPurchaseView(null)}>{tp('common.close')}</button>
            </>
          )}
        >
          <dl className="ap-kv">
            <div><dt>{tp('usage.purchases.col.status')}</dt><dd>{tp(`usage.purchases.status.${purchaseView.status}`)}</dd></div>
            <div><dt>{tp('usage.purchases.col.created')}</dt><dd>{new Date(purchaseView.createdAt).toLocaleString(localeTag)}</dd></div>
            {purchaseView.rejectReason ? <div><dt>{tp('usage.purchases.rejectReason')}</dt><dd>{purchaseView.rejectReason}</dd></div> : null}
          </dl>
          {purchaseView.paymentProofFileId ? (
            <p>
              <a
                href={adminPlanPurchaseApi.proofUrl(purchaseView.id, purchaseView.paymentProofFileId)}
                target="_blank"
                rel="noreferrer"
              >
                {tp('usage.purchases.viewSlip')}
              </a>
            </p>
          ) : (
            <p className="ap-empty">{tp('usage.purchases.noSlip')}</p>
          )}
        </Drawer>
      ) : null}
      {rejectOpen && purchaseView ? (
        <ConfirmDialog
          title={tp('usage.purchases.rejectTitle')}
          message={tp('usage.purchases.rejectBody')}
          cancelLabel={t('confirm.cancel')}
          confirmLabel={purchaseBusy ? tp('usage.purchases.working') : tp('usage.purchases.rejectConfirm')}
          danger
          confirmDisabled={purchaseBusy || !rejectReason.trim()}
          onCancel={() => { setRejectOpen(false); setRejectReason('') }}
          onConfirm={() => {
            setPurchaseBusy(true)
            adminPlanPurchaseApi.reject(purchaseView.id, rejectReason.trim())
              .then(() => {
                setRejectOpen(false)
                setRejectReason('')
                setPurchaseView(null)
                setPurchaseKey((k) => k + 1)
                setPlanNotice(tp('usage.purchases.rejected'))
                window.setTimeout(() => setPlanNotice(''), 2200)
              })
              .catch((error) => setPlanNotice(error instanceof ApiError ? error.message : tp('errors.load')))
              .finally(() => setPurchaseBusy(false))
          }}
        >
          <label className="form-field">
            <span>{tp('usage.purchases.rejectReason')}</span>
            <textarea
              value={rejectReason}
              onChange={(event) => setRejectReason(event.target.value)}
              rows={3}
              aria-invalid={!rejectReason.trim()}
            />
          </label>
        </ConfirmDialog>
      ) : null}
      {planNotice ? <div className="admin-toast" role="status">{planNotice}</div> : null}
    </main>
  )
}
