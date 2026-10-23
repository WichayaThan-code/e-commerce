import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ScrollText, Search, X } from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { LoadError, SkeletonTable } from '../components/feedback/Skeleton'
import { Pager, Pill, errorMessage, formatCount, statusTone } from '../components/admin/PlatformBits'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { hasPlatformAccess } from '../services/permissions'
import { formatDateTime } from '../services/adminFormat'
import { adminPlatformApi, type AdminAuditLogView, type PlatformPage } from '../services/api'
import '../styles/admin-dashboard.css'
import '../styles/admin-platform.css'

type Filters = { q: string; action: string; resourceType: string; shopId: string; outcome: string; kind: string; from: string; to: string }
const EMPTY: Filters = { q: '', action: '', resourceType: '', shopId: '', outcome: '', kind: '', from: '', to: '' }
const OUTCOMES = ['SUCCESS', 'DENIED', 'FAILURE']
const KINDS = ['ALL', 'BUSINESS', 'HTTP']

const json = (value: unknown) => JSON.stringify(value ?? null, null, 2)

export default function AdminAuditLogPage() {
  const { t } = useTranslation('admin')
  const tp = (key: string, options?: Record<string, unknown>) => t(`adminPlatform.${key}`, options)
  const { user, loading } = useAuth()
  const { can, deniedTitle } = usePermissions()
  const allowed = !!user && hasPlatformAccess(user)
  const canRead = can('PLATFORM_AUDIT_READ')

  const [draft, setDraft] = useState<Filters>(EMPTY)
  const [filters, setFilters] = useState<Filters>(EMPTY)
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
    text('q'); text('action'); text('resourceType'); text('outcome'); text('kind'); text('from'); text('to')
    const shop = Number(filters.shopId)
    if (filters.shopId.trim() && Number.isInteger(shop) && shop > 0) params.shopId = shop
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
  const hasFilter = Object.values(filters).some(Boolean)

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
            <label className="ap-search wide"><Search /><input value={draft.q} onChange={event => set('q', event.target.value)} placeholder={tp('audit.filters.q')} aria-label={tp('audit.filters.q')} maxLength={100} /></label>
            <label><span>{tp('audit.filters.action')}</span><input value={draft.action} onChange={event => set('action', event.target.value)} maxLength={100} /></label>
            <label><span>{tp('audit.filters.resourceType')}</span><input value={draft.resourceType} onChange={event => set('resourceType', event.target.value)} maxLength={50} /></label>
            <label><span>{tp('audit.filters.shopId')}</span><input value={draft.shopId} inputMode="numeric" pattern="[0-9]*" className="num" onChange={event => set('shopId', event.target.value.replace(/\D/g, ''))} /></label>
            <label><span>{tp('audit.filters.outcome')}</span>
              <select value={draft.outcome} onChange={event => set('outcome', event.target.value)}>
                <option value="">{tp('common.all')}</option>
                {OUTCOMES.map(value => <option key={value} value={value}>{tp(`outcome.${value}`)}</option>)}
              </select>
            </label>
            <label><span>{tp('audit.filters.kind')}</span>
              <select value={draft.kind} onChange={event => set('kind', event.target.value)}>
                <option value="">{tp('common.all')}</option>
                {KINDS.map(value => <option key={value} value={value}>{tp(`audit.kind.${value}`)}</option>)}
              </select>
            </label>
            <label><span>{tp('common.from')}</span><input type="date" value={draft.from} max={draft.to || undefined} onChange={event => set('from', event.target.value)} /></label>
            <label><span>{tp('common.to')}</span><input type="date" value={draft.to} min={draft.from || undefined} onChange={event => set('to', event.target.value)} /></label>
            <div className="ap-filter-actions">
              <button type="submit" className="ap-primary">{tp('common.search')}</button>
              <button type="button" className="ap-ghost" onClick={reset}>{tp('common.reset')}</button>
            </div>
          </form>

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
                      <td><b>{row.actor?.name || row.actor?.email || '—'}</b><small>{row.actor?.type ?? '—'}</small></td>
                      <td className="num">{row.action}</td>
                      <td><b>{row.target?.type ?? '—'}</b><small>{row.target?.shopName ?? ''}</small></td>
                      <td><Pill tone={statusTone(row.outcome)}>{tp(`outcome.${row.outcome}`, { defaultValue: row.outcome })}</Pill></td>
                      <td className="num">{row.reasonCode || '—'}</td>
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
        <div className="ap-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setSelected(null) }}>
          <section className="ap-modal" role="dialog" aria-modal="true" aria-labelledby="audit-dialog-title">
            <header>
              <h2 id="audit-dialog-title">{tp('audit.detail.title')} <span className="num">#{selected.id}</span></h2>
              <button type="button" aria-label={tp('common.close')} onClick={() => setSelected(null)}><X /></button>
            </header>
            {detailError && <p className="ap-error" role="alert">{detailError}</p>}
            <dl className="ap-fields">
              <div><dt>{tp('audit.col.occurredAt')}</dt><dd className="num">{formatDateTime(selected.occurredAt)}</dd></div>
              <div><dt>{tp('audit.col.actor')}</dt><dd>{selected.actor?.name || '—'} <small>({selected.actor?.type ?? '—'})</small>{selected.actor?.email && <small className="block">{selected.actor.email}</small>}</dd></div>
              <div><dt>{tp('audit.col.action')}</dt><dd className="num">{selected.action}</dd></div>
              <div><dt>{tp('audit.col.target')}</dt><dd>{selected.target?.type ?? '—'}{selected.target?.shopName ? ` · ${selected.target.shopName}` : ''}{selected.target?.id && <small className="block num">{selected.target.id}</small>}</dd></div>
              <div><dt>{tp('audit.col.outcome')}</dt><dd><Pill tone={statusTone(selected.outcome)}>{tp(`outcome.${selected.outcome}`, { defaultValue: selected.outcome })}</Pill></dd></div>
              <div><dt>{tp('audit.col.reasonCode')}</dt><dd className="num">{selected.reasonCode || '—'}</dd></div>
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
          </section>
        </div>
      )}
    </main>
  )
}
