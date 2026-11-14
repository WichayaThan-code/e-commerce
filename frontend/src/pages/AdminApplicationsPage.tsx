import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import {
  Ban, Check, CheckCircle2, CircleX, Clock3, Download, Eye, FileCheck2, Plus, Search, X,
} from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar, { refreshAdminStoreApplications } from '../components/AdminSidebar'
import AdminApplicationDrawer, { type ApplicationDetailTab } from '../components/admin/AdminApplicationDrawer'
import AdminSelect from '../components/admin/AdminSelect'
import AdminDateRange from '../components/admin/AdminDateRange'
import { Pager } from '../components/admin/PlatformBits'
import { useAuth } from '../contexts/AuthContext'
import { hasPlatformAccess } from '../services/permissions'
import { usePermissions } from '../hooks/usePermissions'
import { adminApplicationApi, ApiError, mediaUrl, type AdminStoreApplication } from '../services/api'
import { maskPhone } from '../utils/maskContact'
import '../styles/admin-dashboard.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import '../styles/product-management.css'
import '../styles/order-management.css'
import '../styles/customer-management.css'
import '../styles/form-fields.css'
import '../styles/admin-applications.css'

type ApplicationStatus = AdminStoreApplication['status']
type DetailTab = ApplicationDetailTab
type ApplicationRecord = {
  id: number
  logo: string
  logoClass: string
  logoUrl: string | null
  shop: string
  owner: string
  phone: string
  province: string
  category: string
  categoryClass: string
  appliedAt: string
  appliedAtIso: string
  updatedAt: string
  status: ApplicationStatus
  email: string
  address: string
  description: string
  website: string
  lineId: string
  facebookUrl: string
  instagramUrl: string
  tiktokUrl: string
  documents: { id: string; label: string; filename: string; fileSize: number }[]
  otherShops: string
}

function appliedLabel(value: string, locale: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const tag = locale.startsWith('zh') ? 'zh-CN' : locale.startsWith('en') ? 'en-US' : 'th-TH'
  return new Intl.DateTimeFormat(tag, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}

function fileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function share(count: number, total: number) {
  if (!total) return '0.0'
  return ((count / total) * 100).toFixed(1)
}

function socialLink(value: string) {
  const text = value?.trim() || '-'
  if (text === '-') return { href: undefined, label: '-' }
  if (text.startsWith('http://') || text.startsWith('https://')) return { href: text, label: text }
  if (text.startsWith('@')) return { href: undefined, label: text }
  return { href: `https://${text}`, label: text }
}

function mapApplication(item: AdminStoreApplication, locale: string): ApplicationRecord {
  return {
    id: item.shopId,
    logo: item.shopName.trim().charAt(0) || 'S',
    logoClass: 'garden',
    logoUrl: item.logoUrl || null,
    shop: item.shopName,
    owner: item.ownerName || '-',
    phone: item.phone || '-',
    province: item.province || '-',
    category: item.category || '-',
    categoryClass: 'garden-cat',
    appliedAt: appliedLabel(item.appliedAt, locale),
    appliedAtIso: item.appliedAt || '',
    updatedAt: appliedLabel(item.updatedAt, locale),
    status: item.status,
    email: item.email || '-',
    address: item.address || '-',
    description: item.description || '-',
    website: item.website || '',
    lineId: item.lineId || '',
    facebookUrl: item.facebookUrl || '',
    instagramUrl: item.instagramUrl || '',
    tiktokUrl: item.tiktokUrl || '',
    documents: item.documents.map(document => ({ id: document.id, label: document.label, filename: document.filename, fileSize: document.fileSize })),
    otherShops: (item.otherShops || []).map(shop => shop.shopName).join(', '),
  }
}

function statusOmTone(status: ApplicationStatus) {
  if (status === 'APPROVED') return 'delivered'
  if (status === 'PENDING_REVIEW') return 'waiting'
  if (status === 'SUSPENDED') return 'shipping'
  return 'cancelled'
}

function StatusPill({ status, label }: { status: ApplicationStatus; label: string }) {
  const Icon =
    status === 'APPROVED' ? CheckCircle2
      : status === 'PENDING_REVIEW' ? Clock3
        : status === 'SUSPENDED' ? Ban
          : CircleX
  return (
    <span className={`om-pill app-status-pill ${statusOmTone(status)}`}>
      <Icon aria-hidden="true" />
      {label}
    </span>
  )
}

function ShopMark({ item }: { item: ApplicationRecord }) {
  return <i className={item.logoClass}>{item.logoUrl ? <img src={mediaUrl(item.logoUrl)} alt="" /> : item.logo}</i>
}

export default function AdminApplicationsPage() {
  const { t, i18n } = useTranslation('admin')
  const tp = (key: string, options?: Record<string, unknown>) => t(`adminPlatform.applications.${key}`, options)
  const { user, loading } = useAuth()
  const { can, deniedTitle } = usePermissions()
  const canReview = can('PLATFORM_APPLICATION_REVIEW')
  const canAudit = can('PLATFORM_AUDIT_READ')
  const canRevealContact = can('PLATFORM_CONTACT_REVEAL')
  const [params] = useSearchParams()
  const [records, setRecords] = useState<ApplicationRecord[]>([])
  const [loaded, setLoaded] = useState(false)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [category, setCategory] = useState('')
  const [province, setProvince] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [selectedRows, setSelectedRows] = useState<number[]>([])
  const [tab, setTab] = useState<DetailTab>('info')
  const [notice, setNotice] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [busy, setBusy] = useState(false)
  const [revealedContact, setRevealedContact] = useState<{ shopId: number; phone: string; email: string } | null>(null)
  const [revealBusy, setRevealBusy] = useState(false)
  const revealContactOf = (shopId: number, toast: boolean) => {
    if (revealedContact?.shopId === shopId) return Promise.resolve(revealedContact)
    setRevealBusy(true)
    return adminApplicationApi.revealContact(shopId)
      .then(contact => {
        const next = { shopId, phone: contact.phone || '', email: contact.email || '' }
        setRevealedContact(next)
        if (toast) notify(tp('contactReveal.revealedToast'))
        return next
      })
      .catch(error => {
        notify(error instanceof ApiError ? error.message : tp('contactReveal.revealError'))
        return null
      })
      .finally(() => setRevealBusy(false))
  }
  const locale = i18n.language || 'th'

  const categoryOptions = useMemo(() => [
    { value: '', label: t('adminPlatform.common.all') },
    ...[...new Set(records.map(item => item.category).filter(Boolean))].sort().map(value => ({ value, label: value })),
  ], [records, t, i18n.language])

  const provinceOptions = useMemo(() => [
    { value: '', label: t('adminPlatform.common.all') },
    ...[...new Set(records.map(item => item.province).filter(Boolean))].sort().map(value => ({ value, label: value })),
  ], [records, t, i18n.language])

  const filtered = useMemo(() => records.filter(item => {
    const haystack = `${item.shop} ${item.owner} ${item.email} ${item.phone}`.toLowerCase()
    if (!haystack.includes(query.trim().toLowerCase())) return false
    if (status && item.status !== status) return false
    if (category && item.category !== category) return false
    if (province && item.province !== province) return false
    if (dateFrom || dateTo) {
      const applied = Date.parse(item.appliedAtIso)
      if (Number.isNaN(applied)) return false
      if (dateFrom) {
        const from = Date.parse(dateFrom)
        if (!Number.isNaN(from) && applied < from) return false
      }
      if (dateTo) {
        const to = Date.parse(dateTo)
        if (!Number.isNaN(to) && applied > to + 86_399_999) return false
      }
    }
    return true
  }), [records, query, status, category, province, dateFrom, dateTo])

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage = Math.min(page, pageCount)
  const visible = filtered.slice((safePage - 1) * pageSize, safePage * pageSize)
  const canSelect = (item: ApplicationRecord) => item.status === 'PENDING_REVIEW'
  const pageTargets = visible.filter(item => item.status === 'PENDING_REVIEW')
  const selected = records.find(item => item.id === selectedId)
  const pending = records.filter(item => item.status === 'PENDING_REVIEW').length
  const approved = records.filter(item => item.status === 'APPROVED').length
  const rejected = records.filter(item => item.status === 'REJECTED').length

  useEffect(() => {
    if (!user || !hasPlatformAccess(user)) return
    let active = true
    adminApplicationApi.list().then(rows => {
      if (!active) return
      const mapped = rows.map(item => mapApplication(item, locale))
      setRecords(mapped)
    }).catch(error => {
      if (!active) return
      setRecords([])
      setNotice(error instanceof ApiError ? error.message : tp('loadError'))
    }).finally(() => { if (active) setLoaded(true) })
    return () => { active = false }
  }, [user, locale])

  useEffect(() => { setPage(1) }, [query, status, category, province, dateFrom, dateTo, pageSize])
  useEffect(() => { if (page > pageCount) setPage(pageCount) }, [page, pageCount])
  useEffect(() => {
    const shop = Number(params.get('shop'))
    if (shop) setSelectedId(shop)
    const keyword = params.get('q')
    if (keyword != null) setQuery(keyword)
  }, [params])

  if (loading || (hasPlatformAccess(user) && !loaded)) {
    return <main className="auth-loading">{t('adminPlatform.common.checkingAccess')}</main>
  }
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const notify = (text: string) => { setNotice(text); window.setTimeout(() => setNotice(''), 1900) }
  const reset = () => { setQuery(''); setStatus(''); setCategory(''); setProvince(''); setDateFrom(''); setDateTo(''); setPage(1) }
  const toggleRow = (item: ApplicationRecord) => {
    if (selectedRows.includes(item.id)) { setSelectedRows(rows => rows.filter(id => id !== item.id)); return }
    if (!canSelect(item)) return
    setSelectedRows(rows => [...rows, item.id])
  }
  const togglePage = () => {
    const ids = pageTargets.map(item => item.id)
    if (!ids.length) return
    const allOn = ids.every(id => selectedRows.includes(id))
    setSelectedRows(rows => allOn ? rows.filter(id => !ids.includes(id)) : [...new Set([...rows, ...ids])])
  }
  const exportExcel = async () => {
    try {
      await adminApplicationApi.exportExcel()
      notify(tp('exportExcelToast'))
    } catch (error) {
      notify(error instanceof ApiError ? error.message : tp('exportExcelError'))
    }
  }
  const downloadDocument = async (documentId: string, filename: string) => {
    if (!selected) return
    try {
      await adminApplicationApi.downloadDocument(selected.id, documentId, filename)
    } catch (error) {
      notify(error instanceof ApiError ? error.message : tp('downloadError'))
    }
  }
  const replaceRecord = (item: AdminStoreApplication) => {
    const next = mapApplication(item, locale)
    setRecords(rows => rows.map(row => row.id === next.id ? next : row))
    setSelectedId(next.id)
  }
  const decide = async (action: 'approve' | 'reject') => {
    if (!selected || busy || selected.status !== 'PENDING_REVIEW') return
    setBusy(true)
    try {
      const updated = action === 'approve'
        ? await adminApplicationApi.approve(selected.id)
        : await adminApplicationApi.reject(selected.id)
      replaceRecord(updated)
      refreshAdminStoreApplications()
      notify(tp(action === 'approve' ? 'approvedToast' : 'rejectedToast', { name: updated.shopName }))
    } catch (error) {
      notify(error instanceof ApiError ? error.message : tp('saveError'))
    } finally {
      setBusy(false)
    }
  }
  const runBulk = async (action: 'approve' | 'reject') => {
    if (!selectedRows.length || busy) return
    setBusy(true)
    try {
      const result = await adminApplicationApi.bulk(action, selectedRows)
      if (result.applications.length) {
        const mapped = result.applications.map(item => mapApplication(item, locale))
        setRecords(rows => rows.map(row => mapped.find(next => next.id === row.id) ?? row))
      }
      setSelectedRows([])
      refreshAdminStoreApplications()
      notify(result.updated
        ? tp(action === 'approve' ? 'bulkApprovedToast' : 'bulkRejectedToast', { count: result.updated })
        : tp(action === 'approve' ? 'bulkNoneApprove' : 'bulkNoneReject'))
    } catch (error) {
      notify(error instanceof ApiError ? error.message : tp('saveError'))
    } finally {
      setBusy(false)
    }
  }
  const statusText = (code: ApplicationStatus) => tp(`status.${code}`)

  return <main className="admin-shell applications-shell">
    <AdminHeader user={user} query={query} onSearch={setQuery} />
    <AdminSidebar active="applications" className="applications-sidebar" />

    <div className="admin-content applications-content">
      <section className="applications-heading">
        <div className="applications-title">
          <span><FileCheck2 /></span>
          <div>
            <h1>{tp('title')}</h1>
            <p>{tp('subtitle')}</p>
            <small>{tp('tagline')}</small>
          </div>
        </div>
        <div className="application-tools">
          <button className="manual-store" disabled={!can('PLATFORM_APPLICATION_READ')} title={deniedTitle(can('PLATFORM_APPLICATION_READ'))} onClick={exportExcel}>
            <Download />{tp('exportExcel')}
          </button>
          <button className="manual-store" onClick={() => notify(tp('addManualToast'))}>
            <Plus />{tp('addManual')}
          </button>
        </div>
      </section>
      <section className="application-kpis">
        <article className="total"><span><Clock3 /></span><div><b>{tp('kpiAll')}</b><strong>{records.length} <em>{share(records.length, records.length)}%</em></strong><small>{tp('kpiAllHint')}</small></div></article>
        <article className="review"><span><span className="hourglass">⌛</span></span><div><b>{tp('kpiPending')}</b><strong>{pending} <em>{share(pending, records.length)}%</em></strong><small>{tp('kpiShare', { pct: share(pending, records.length) })}</small></div></article>
        <article className="approved"><span><Check /></span><div><b>{tp('kpiApproved')}</b><strong>{approved} <em>{share(approved, records.length)}%</em></strong><small>{tp('kpiShare', { pct: share(approved, records.length) })}</small></div></article>
        <article className="rejected"><span><CircleX /></span><div><b>{tp('kpiRejected')}</b><strong>{rejected} <em>{share(rejected, records.length)}%</em></strong><small>{tp('kpiShare', { pct: share(rejected, records.length) })}</small></div></article>
      </section>
      <section className="pm-table-card om-card applications-table-card">
        <div className="om-tabs" role="tablist">
          {([
            ['all', tp('kpiAll')],
            ['PENDING_REVIEW', tp('status.PENDING_REVIEW')],
            ['APPROVED', tp('status.APPROVED')],
            ['REJECTED', tp('status.REJECTED')],
            ['SUSPENDED', tp('status.SUSPENDED')],
          ] as const).map(([key, label]) => {
            const active = (status || 'all') === key
            return (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={active}
                className={active ? 'active' : ''}
                onClick={() => {
                  setStatus(key === 'all' ? '' : key)
                  setPage(1)
                }}
              >
                {label}
              </button>
            )
          })}
        </div>

        <form
          className="om-filters application-filters"
          onSubmit={event => {
            event.preventDefault()
            setPage(1)
            notify(tp('foundToast', { count: filtered.length }))
          }}
        >
          <div className="om-filters-main">
            <div className="om-filter-search">
              <span className="form-label">{tp('searchLabel')}</span>
              <label className="om-search">
                <Search aria-hidden />
                <input
                  value={query}
                  onChange={event => setQuery(event.target.value)}
                  placeholder={tp('searchPlaceholder')}
                  aria-label={tp('searchAria')}
                />
              </label>
            </div>
            <AdminSelect label={tp('categoryLabel')} value={category} onChange={value => { setCategory(value); setPage(1) }} options={categoryOptions} />
            <AdminSelect label={tp('provinceLabel')} value={province} onChange={value => { setProvince(value); setPage(1) }} options={provinceOptions} />
            <AdminDateRange label={tp('dateRangeLabel')} from={dateFrom} to={dateTo} onChange={range => { setDateFrom(range.from); setDateTo(range.to) }} />
            <div className="om-filters-actions">
              <button type="button" onClick={reset}>{tp('reset')}</button>
              <button type="submit" className="primary">{tp('search')}</button>
            </div>
          </div>
        </form>

        {(query || category || province || dateFrom || dateTo || status) ? (
          <div className="om-active-filters" role="list">
            {query ? (
              <div className="om-filter-chip" role="listitem">
                <span>{tp('searchLabel')}: {query}</span>
                <button type="button" aria-label={tp('searchLabel')} onClick={() => setQuery('')}>
                  <X size={14} aria-hidden />
                </button>
              </div>
            ) : null}
            {status ? (
              <div className="om-filter-chip" role="listitem">
                <span>{tp('statusLabel')}: {statusText(status as ApplicationStatus)}</span>
                <button type="button" aria-label={tp('statusLabel')} onClick={() => setStatus('')}>
                  <X size={14} aria-hidden />
                </button>
              </div>
            ) : null}
            {category ? (
              <div className="om-filter-chip" role="listitem">
                <span>{tp('categoryLabel')}: {category}</span>
                <button type="button" aria-label={tp('categoryLabel')} onClick={() => setCategory('')}>
                  <X size={14} aria-hidden />
                </button>
              </div>
            ) : null}
            {province ? (
              <div className="om-filter-chip" role="listitem">
                <span>{tp('provinceLabel')}: {province}</span>
                <button type="button" aria-label={tp('provinceLabel')} onClick={() => setProvince('')}>
                  <X size={14} aria-hidden />
                </button>
              </div>
            ) : null}
            {(dateFrom || dateTo) ? (
              <div className="om-filter-chip" role="listitem">
                <span>{tp('dateRangeLabel')}: {[dateFrom, dateTo].filter(Boolean).join(' – ')}</span>
                <button type="button" aria-label={tp('dateRangeLabel')} onClick={() => { setDateFrom(''); setDateTo('') }}>
                  <X size={14} aria-hidden />
                </button>
              </div>
            ) : null}
            <button type="button" className="om-clear-all" onClick={reset}>{tp('reset')}</button>
          </div>
        ) : null}

        {selectedRows.length > 0 ? (
          <div className="bulk-actions applications-bulk-bar">
            <span>{tp('selectedCount', { count: selectedRows.length })}</span>
            <button className="approve" disabled={busy || !canReview} title={deniedTitle(canReview)} onClick={() => runBulk('approve')}>{tp('bulkApprove')}</button>
            <button className="reject" disabled={busy || !canReview} title={deniedTitle(canReview)} onClick={() => runBulk('reject')}>{tp('bulkReject')}</button>
          </div>
        ) : null}

        <div className="om-table-wrap applications-table-wrap">
          <table>
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    aria-label={tp('selectAllAria')}
                    disabled={pageTargets.length === 0}
                    checked={pageTargets.length > 0 && pageTargets.every(item => selectedRows.includes(item.id))}
                    onChange={togglePage}
                  />
                </th>
                <th>{tp('colId')}</th>
                <th>{tp('colShop')}</th>
                <th>{tp('colOwner')}</th>
                <th>{tp('colProvince')}</th>
                <th>{tp('colCategory')}</th>
                <th>{tp('colAppliedAt')}</th>
                <th>{tp('colStatus')}</th>
                <th>{tp('colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {visible.map(item => {
                const isOpen = selectedId === item.id
                return (
                  <tr
                    key={item.id}
                    className={isOpen ? 'selected' : undefined}
                    onClick={() => { setSelectedId(item.id); setTab('info'); setRevealedContact(null) }}
                  >
                    <td onClick={event => event.stopPropagation()}>
                      <input
                        type="checkbox"
                        aria-label={tp('selectRowAria', { name: item.shop })}
                        disabled={!selectedRows.includes(item.id) && !canSelect(item)}
                        checked={selectedRows.includes(item.id)}
                        onChange={() => toggleRow(item)}
                      />
                    </td>
                    <td><b className="num">{item.id}</b></td>
                    <td>
                      <div className="application-shop">
                        <ShopMark item={item} />
                        <b>{item.shop}</b>
                      </div>
                    </td>
                    <td>
                      <div className="om-customer-cell">
                        <b>{item.owner}</b>
                        <span className="om-muted">{maskPhone(item.phone)}</span>
                      </div>
                    </td>
                    <td>{item.province}</td>
                    <td><span className={`application-category ${item.categoryClass}`}>{item.category}</span></td>
                    <td className="applied-at">{item.appliedAt}</td>
                    <td>
                      <StatusPill status={item.status} label={statusText(item.status)} />
                    </td>
                    <td onClick={event => event.stopPropagation()}>
                      <div className="om-row-actions">
                        <button
                          type="button"
                          aria-label={item.status === 'PENDING_REVIEW' ? tp('review') : tp('viewDetail')}
                          onClick={() => { setSelectedId(item.id); setTab('info'); setRevealedContact(null) }}
                        >
                          <Eye aria-hidden />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {filtered.length === 0 ? <div className="om-empty">{tp('emptyFiltered')}</div> : null}
        </div>

        <Pager
          page={safePage - 1}
          size={pageSize}
          totalItems={filtered.length}
          totalPages={pageCount}
          itemCount={visible.length}
          sizeOptions={[10, 25]}
          onPage={next => setPage(next + 1)}
          onSize={next => { setPageSize(next); setPage(1) }}
        />
      </section>
    </div>

    <AdminApplicationDrawer
      open={selectedId != null}
      item={selected ?? null}
      tab={tab}
      onTabChange={setTab}
      onClose={() => { setSelectedId(null); setRevealedContact(null) }}
      statusLabel={statusText}
      statusTone={statusOmTone}
      labels={{
        close: tp('detailTitle'),
        tabInfo: tp('tabInfo'),
        tabDocuments: tp('tabDocuments'),
        tabHistory: tp('tabHistory'),
        appliedAt: date => tp('appliedAt', { date }),
        fieldShopId: tp('fieldShopId'),
        fieldShopName: tp('fieldShopName'),
        fieldOwner: tp('fieldOwner'),
        otherShops: names => tp('otherShops', { names }),
        fieldEmail: tp('fieldEmail'),
        fieldPhone: tp('fieldPhone'),
        fieldProvince: tp('fieldProvince'),
        fieldAddress: tp('fieldAddress'),
        fieldCategory: tp('fieldCategory'),
        fieldDescription: tp('fieldDescription'),
        socialTitle: tp('socialTitle'),
        socialWebsite: tp('socialWebsite'),
        socialEmpty: tp('socialEmpty'),
        socialOpen: tp('socialOpen'),
        download: tp('download'),
        noDocumentsTitle: tp('noDocumentsTitle'),
        historySubmitted: tp('historySubmitted'),
        historyActive: tp('historyActive'),
        historyRejected: tp('historyRejected'),
        historyPending: tp('historyPending'),
        historyPendingHint: tp('historyPendingHint'),
        historySuspended: tp('historySuspended'),
        auditLink: t('adminPlatform.audit.openForTarget'),
        contactOwner: tp('contactOwner'),
        contactEmpty: tp('contactEmpty'),
        contactEmail: tp('contactEmail'),
        contactPhone: tp('contactPhone'),
        contactMenu: tp('contactMenu'),
        revealButton: tp('contactReveal.button'),
        revealed: tp('contactReveal.revealed'),
        approveShop: tp('approveShop'),
        reject: tp('reject'),
        emptyDetail: tp('emptyDetail'),
      }}
      canReview={canReview}
      canRevealContact={canRevealContact}
      canAudit={canAudit}
      deniedTitle={deniedTitle}
      busy={busy}
      revealBusy={revealBusy}
      revealedContact={revealedContact}
      onRevealContact={() => {
        if (!selected || revealedContact?.shopId === selected.id) return
        void revealContactOf(selected.id, true)
      }}
      onEnsureContact={async () => {
        if (!selected) return null
        if (revealedContact?.shopId === selected.id) return revealedContact
        return revealContactOf(selected.id, false)
      }}
      onApprove={() => decide('approve')}
      onReject={() => decide('reject')}
      onDownloadDocument={downloadDocument}
      fileSizeLabel={fileSize}
      socialLink={socialLink}
    />
    {notice && <div className="admin-toast" role="status">{notice}</div>}
  </main>
}
