import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ScrollText, Search, X } from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { LoadError, SkeletonTable } from '../components/feedback/Skeleton'
import { Pager, Pill, errorMessage, formatCount, statusTone } from '../components/admin/PlatformBits'
import AdminDetailDrawer from '../components/admin/AdminDetailDrawer'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { hasPlatformAccess } from '../services/permissions'
import { formatDateTime } from '../services/adminFormat'
import { adminPlatformApi, type AdminAuditLogView, type PlatformPage } from '../services/api'
import '../styles/admin-dashboard.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import AdminSelect from '../components/admin/AdminSelect'
import AdminDateRange from '../components/admin/AdminDateRange'

type Filters = { q: string; action: string; resourceType: string; resourceId: string; shopId: string; outcome: string; kind: string; from: string; to: string }
const EMPTY: Filters = { q: '', action: '', resourceType: '', resourceId: '', shopId: '', outcome: '', kind: '', from: '', to: '' }
const OUTCOMES = ['SUCCESS', 'DENIED', 'FAILURE']
const KINDS = ['ALL', 'BUSINESS', 'HTTP']
/** Existing persisted action names (past tense) — keep as-is; do not rename to MERCHANT_SUSPEND etc. */
const STATUS_ACTIONS = [
  'MERCHANT_SUSPENDED',
  'MERCHANT_REACTIVATED',
  'SHOP_SUSPENDED',
  'SHOP_REACTIVATED',
  'ACCOUNT_SUSPENDED',
  'ACCOUNT_RESTORED',
] as const

const json = (value: unknown) => JSON.stringify(value ?? null, null, 2)

function statusOf(data: Record<string, unknown> | null | undefined): string | null {
  const value = data?.status
  return typeof value === 'string' && value ? value : null
}

function filtersFromSearch(): Filters {
  if (typeof window === 'undefined') return EMPTY
  const params = new URLSearchParams(window.location.search)
  const rawType = (params.get('targetType') || params.get('resourceType') || '').toUpperCase()
  const targetId = params.get('targetId') || params.get('resourceId') || ''
  const shopFromQuery = params.get('shopId') || ''
  // SHOP + numeric id → shopId filter (audit resource_id is UUID; shop_no is what UIs pass)
  const shopTarget = rawType === 'SHOP' && /^\d+$/.test(targetId.trim())
  const resourceType = rawType === 'MERCHANT' ? 'OWNER' : shopTarget ? 'SHOP' : rawType
  return {
    q: params.get('q') || '',
    action: params.get('action') || '',
    resourceType: shopTarget ? '' : resourceType,
    resourceId: shopTarget ? '' : targetId,
    shopId: shopTarget ? targetId.trim() : shopFromQuery,
    outcome: params.get('outcome') || '',
    kind: params.get('kind') || '',
    from: params.get('from') || '',
    to: params.get('to') || '',
  }
}

export default function AdminAuditLogPage() {
  const { t } = useTranslation('admin')
  const tp = (key: string, options?: Record<string, unknown>) => t(`adminPlatform.${key}`, options)
  const { user, loading } = useAuth()
  const { can, deniedTitle } = usePermissions()
  const allowed = !!user && hasPlatformAccess(user)

  const canRead = can('PLATFORM_AUDIT_READ')
  const initial = useMemo(() => filtersFromSearch(), [])
  const [draft, setDraft] = useState<Filters>(initial)
  const [filters, setFilters] = useState<Filters>(initial)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [reloadKey, setReloadKey] = useState(0)
  const [result, setResult] = useState<PlatformPage<AdminAuditLogView> | null>(null)
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [selected, setSelected] = useState<AdminAuditLogView | null>(null)
  const [detailError, setDetailError] = useState('')

  const query = useMemo(() => {
    const params: Record<string, string | number> = { page, size }
    const text = (key: keyof Filters) => { const value = filters[key].trim(); if (value) params[key] = value }
    text('q'); text('action'); text('outcome'); text('kind'); text('from'); text('to')
    const shop = Number(filters.shopId)
    if (filters.shopId.trim() && Number.isInteger(shop) && shop > 0) {
      params.shopId = shop
      // Prefer shopId over resourceType=SHOP without UUID — AdminAuditLogService resolves shop_no
    } else {
      text('resourceType'); text('resourceId')
    }
    return params
  }, [filters, page, size])

  useEffect(() => {
    if (!allowed || !canRead) { setListLoading(false); return }
    let cancelled = false
    setListLoading(true)
    setListError('')
    adminPlatformApi.auditLogs(query)
      .then(value => { if (!cancelled) setResult(value) })
      .catch(error => { if (!cancelled) { setResult(null); setListError(errorMessage(error, tp('errors.load'))) } })
      .finally(() => { if (!cancelled) setListLoading(false) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allowed, canRead, query, reloadKey])

  const selectedId = selected?.id ?? null
  useEffect(() => {
    if (selectedId === null) return
    let cancelled = false
    setDetailError('')
    adminPlatformApi.auditLog(selectedId)
      .then(value => { if (!cancelled) setSelected(value) })
      .catch(error => { if (!cancelled) setDetailError(errorMessage(error, tp('errors.detail'))) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  useEffect(() => {
    if (!selected) return
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setSelected(null) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [selected])

  if (loading) return <main className="auth-loading">{tp('common.checkingAccess')}</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const items = result?.items ?? []
  const submit = (event: FormEvent) => { event.preventDefault(); setFilters(draft); setPage(0); setReloadKey(key => key + 1) }
  const reset = () => { setDraft(EMPTY); setFilters(EMPTY); setPage(0) }
  const set = (key: keyof Filters, value: string) => setDraft(current => ({ ...current, [key]: value }))
  const clearFilters = (...keys: (keyof Filters)[]) => {
    setDraft(current => {
      const next = { ...current }
      for (const key of keys) next[key] = ''
      return next
    })
    setFilters(current => {
      const next = { ...current }
      for (const key of keys) next[key] = ''
      return next
    })
    setPage(0)
  }
  const hasFilter = Object.values(filters).some(Boolean)
  const actionLabel = (action: string) => tp(`audit.actions.${action}`, { defaultValue: action })
  const reasonCodeLabel = (code: string | null | undefined) => {
    if (!code) return '—'
    return tp(`reason.codes.${code}`, { defaultValue: code })
  }
  const targetLabel = (row: AdminAuditLogView) => row.target?.name || row.target?.shopName || null
  const beforeStatus = statusOf(selected?.before ?? null)
  const afterStatus = statusOf(selected?.after ?? null)
  const chips: { key: keyof Filters; label: string }[] = []
  if (filters.shopId.trim()) chips.push({ key: 'shopId', label: tp('audit.chip.shop', { id: filters.shopId }) })
  if (filters.resourceType.trim() || filters.resourceId.trim()) {
    chips.push({
      key: filters.resourceId.trim() ? 'resourceId' : 'resourceType',
      label: tp('audit.chip.target', { type: filters.resourceType || '—', id: filters.resourceId || '—' }),
    })
  }
  if (filters.action.trim()) chips.push({ key: 'action', label: tp('audit.chip.action', { action: actionLabel(filters.action) }) })
  if (filters.kind.trim()) chips.push({ key: 'kind', label: tp('audit.chip.kind', { kind: tp(`audit.kind.${filters.kind}`, { defaultValue: filters.kind }) }) })

  return (
    <main className="admin-shell platform-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="audit" className="platform-sidebar" />
      <div className="admin-content platform-content">
        <section className="ap-heading">
          <span className="ap-heading-icon"><ScrollText /></span>
          <div>
            <h1>{tp('audit.title')}</h1>
            <p>{tp('audit.subtitle')}</p>
          </div>
          <div className="ap-kpi" aria-live="polite">
            <small>{tp('common.totalFromServer')}</small>
            <strong className="num">{result ? formatCount(result.totalItems) : '—'}</strong>
          </div>
        </section>

        {!canRead ? <div className="ap-card"><p className="ap-empty" title={deniedTitle(false)}>{tp('errors.noPermission')}</p></div> : <>
          <form className="ap-filters" onSubmit={submit}>
            <div className="ap-filter-search wide">
              <span className="ap-filter-label">{tp('common.search')}</span>
              <label className="ap-search adm-search"><Search /><input value={draft.q} onChange={event => set('q', event.target.value)} placeholder={tp('audit.filters.q')} aria-label={tp('audit.filters.q')} maxLength={100} /></label>
            </div>
            <AdminSelect label={tp('audit.filters.action')} value={draft.action} onChange={value => set('action', value)}
              options={[
                { value: '', label: tp('common.all') },
                ...STATUS_ACTIONS.map(value => ({ value, label: tp(`audit.actions.${value}`) })),
                ...(draft.action && !(STATUS_ACTIONS as readonly string[]).includes(draft.action)
                  ? [{ value: draft.action, label: draft.action }] : []),
              ]} />
            <label><span>{tp('audit.filters.resourceType')}</span><input value={draft.resourceType} onChange={event => set('resourceType', event.target.value)} maxLength={50} /></label>
            <label><span>{tp('audit.filters.resourceId')}</span><input value={draft.resourceId} onChange={event => set('resourceId', event.target.value)} maxLength={64} className="num" /></label>
            <label><span>{tp('audit.filters.shopId')}</span><input value={draft.shopId} inputMode="numeric" pattern="[0-9]*" className="num" onChange={event => set('shopId', event.target.value.replace(/\D/g, ''))} /></label>
            <AdminSelect label={tp('audit.filters.outcome')} value={draft.outcome} onChange={value => set('outcome', value)}
              options={[{ value: '', label: tp('common.all') }, ...OUTCOMES.map(value => ({ value, label: tp(`outcome.${value}`) }))]} />
            <AdminSelect label={tp('audit.filters.kind')} value={draft.kind} onChange={value => set('kind', value)}
              options={[{ value: '', label: tp('common.all') }, ...KINDS.map(value => ({ value, label: tp(`audit.kind.${value}`) }))]} />
            <AdminDateRange from={draft.from} to={draft.to} onChange={range => { set('from', range.from); set('to', range.to) }} />
            <div className="ap-filter-actions">
              <button type="button" className="ap-ghost" onClick={reset}>{tp('common.reset')}</button>
              <button type="submit" className="ap-primary">{tp('common.search')}</button>
            </div>
          </form>
          {chips.length > 0 && (
            <div className="ap-filter-chips" role="list" aria-label={tp('audit.filters.action')}>
              {chips.map(chip => (
                <button type="button" className="ap-filter-chip" role="listitem" key={chip.key} onClick={() => {
                  if (chip.key === 'resourceId' || chip.key === 'resourceType') clearFilters('resourceId', 'resourceType')
                  else clearFilters(chip.key)
                }}>
                  <span>{chip.label}</span>
                  <X aria-hidden />
                </button>
              ))}
            </div>
          )}

          <section className="ap-card">
            <div className="ap-table-scroll">
              <table className="ap-table">
                <thead>
                  <tr>
                    <th>{tp('audit.col.occurredAt')}</th><th>{tp('audit.col.actor')}</th><th>{tp('audit.col.action')}</th>
                    <th>{tp('audit.col.target')}</th><th>{tp('audit.col.outcome')}</th><th>{tp('audit.col.reasonCode')}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(row => (
                    <tr key={row.id} className="clickable" tabIndex={0} onClick={() => { setDetailError(''); setSelected(row) }}
                      onKeyDown={event => { if (event.key === 'Enter') { setDetailError(''); setSelected(row) } }}>
                      <td className="num nowrap">{formatDateTime(row.occurredAt)}</td>
                      <td><b>{row.actor?.name || row.actor?.email || '—'}</b><small>{row.actor?.role || row.actor?.type || '—'}</small></td>
                      <td><b>{actionLabel(row.action)}</b><small className="num">{row.action}</small></td>
                      <td><b>{row.target?.type ?? '—'}</b><small>{targetLabel(row) ?? ''}</small></td>
                      <td><Pill tone={statusTone(row.outcome)}>{tp(`outcome.${row.outcome}`, { defaultValue: row.outcome })}</Pill></td>
                      <td>{row.reasonCode ? <><b>{reasonCodeLabel(row.reasonCode)}</b><small className="num">{row.reasonCode}</small></> : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {listLoading && items.length === 0 && <SkeletonTable rows={8} cols={6} />}
              {!listLoading && listError && <LoadError message={listError} onRetry={() => setReloadKey(key => key + 1)} />}
              {!listLoading && !listError && items.length === 0 && <p className="ap-empty">{hasFilter ? tp('audit.emptyFiltered') : tp('audit.empty')}</p>}
            </div>
            <Pager page={page} size={size} totalItems={result?.totalItems ?? 0} totalPages={result?.totalPages ?? 0} itemCount={items.length}
              loading={listLoading} onPage={setPage} onSize={value => { setSize(value); setPage(0) }} />
          </section>
        </>}
      </div>

      {selected && (
        <AdminDetailDrawer
          className="audit-detail-drawer"
          wide
          title={tp('audit.detail.title')}
          meta={
            <>
              <span className="om-muted num">#{selected.id}</span>
              <Pill tone={statusTone(selected.outcome)}>{tp(`outcome.${selected.outcome}`, { defaultValue: selected.outcome })}</Pill>
            </>
          }
          avatarFallback={<ScrollText />}
          onClose={() => setSelected(null)}
          closeLabel={tp('common.close')}
        >
          {detailError && <p className="ap-error" role="alert">{detailError}</p>}
          <dl className="ap-fields">
            <div className="full"><dt>{tp('audit.detail.summary')}</dt><dd><b>{actionLabel(selected.action)}</b>{beforeStatus && afterStatus ? <small className="block">{tp('audit.detail.statusChange', { before: beforeStatus, after: afterStatus })}</small> : null}</dd></div>
            <div><dt>{tp('audit.col.occurredAt')}</dt><dd className="num">{formatDateTime(selected.occurredAt)}</dd></div>
            <div><dt>{tp('audit.col.actor')}</dt><dd>{selected.actor?.name || '—'} <small>({selected.actor?.role || selected.actor?.type || '—'})</small>{selected.actor?.email && <small className="block">{selected.actor.email}</small>}</dd></div>
            <div><dt>{tp('audit.col.action')}</dt><dd>{actionLabel(selected.action)}<small className="block num">{selected.action}</small></dd></div>
            <div><dt>{tp('audit.col.target')}</dt><dd>{selected.target?.type ?? '—'}{(selected.target?.name || selected.target?.shopName) ? ` · ${selected.target?.name || selected.target?.shopName}` : ''}{selected.target?.id && <small className="block num">{selected.target.id}</small>}</dd></div>
            <div><dt>{tp('audit.col.outcome')}</dt><dd><Pill tone={statusTone(selected.outcome)}>{tp(`outcome.${selected.outcome}`, { defaultValue: selected.outcome })}</Pill></dd></div>
            <div><dt>{tp('audit.col.reasonCode')}</dt><dd>{selected.reasonCode ? <>{reasonCodeLabel(selected.reasonCode)}<small className="block num">{selected.reasonCode}</small></> : '—'}</dd></div>
            <div className="full"><dt>{tp('audit.detail.reason')}</dt><dd>{selected.reason || '—'}</dd></div>
            <div><dt>{tp('audit.detail.permission')}</dt><dd className="num">{selected.permission || '—'}</dd></div>
            <div><dt>{tp('audit.detail.authContext')}</dt><dd className="num">{selected.authContext || '—'}</dd></div>
            <div><dt>{tp('audit.detail.requestId')}</dt><dd className="num">{selected.requestId || '—'}</dd></div>
            <div><dt>{tp('audit.detail.ipAddress')}</dt><dd className="num">{selected.ipAddress || '—'}</dd></div>
            <div className="full"><dt>{tp('audit.detail.userAgent')}</dt><dd className="num">{selected.userAgent || '—'}</dd></div>
          </dl>
          <div className="ap-diff">
            <div><h3>{tp('audit.detail.before')}</h3><pre className="num">{json(selected.before)}</pre></div>
            <div><h3>{tp('audit.detail.after')}</h3><pre className="num">{json(selected.after)}</pre></div>
          </div>
        </AdminDetailDrawer>
      )}
    </main>
  )
}
