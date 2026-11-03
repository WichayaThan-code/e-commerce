import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import {
  Building2, Check, ChevronLeft,
  ChevronRight, CircleX, Clock3, Download, Facebook, FileCheck2,
  FileText, Home, Instagram, LockKeyhole, Mail, MapPin,
  MessageCircle, MoreVertical, Phone, Plus, Search,
  UsersRound,
} from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar, { refreshAdminStoreApplications } from '../components/AdminSidebar'
import AdminSidePanel from '../components/admin/AdminSidePanel'
import AdminSelect from '../components/admin/AdminSelect'
import AdminDateRange from '../components/admin/AdminDateRange'
import { useAuth } from '../contexts/AuthContext'
import { hasPlatformAccess } from '../services/permissions'
import { usePermissions } from '../hooks/usePermissions'
import { adminApplicationApi, ApiError, mediaUrl, type AdminStoreApplication } from '../services/api'
import { maskEmail, maskPhone } from '../utils/maskContact'
import '../styles/admin-dashboard.css'
import '../styles/admin-applications.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import '../styles/admin-side-panel.css'

type ApplicationStatus = AdminStoreApplication['status']
type DetailTab = 'info' | 'documents' | 'history'
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

const STATUS_CODES: ApplicationStatus[] = ['APPROVED', 'PENDING_REVIEW', 'SUSPENDED', 'REJECTED']

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

function statusPillClass(status: ApplicationStatus) {
  if (status === 'APPROVED') return 'active'
  if (status === 'PENDING_REVIEW') return 'waiting'
  if (status === 'SUSPENDED') return 'blocked'
  return 'rejected'
}

function pageNumbers(current: number, count: number) {
  if (count <= 7) return Array.from({ length: count }, (_, index) => index + 1)
  const start = Math.max(1, Math.min(current, count - 4))
  return Array.from({ length: 5 }, (_, index) => start + index)
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
  const locale = i18n.language || 'th'

  const statusOptions = useMemo(() => [
    { value: '', label: t('adminPlatform.common.all') },
    ...STATUS_CODES.map(value => ({ value, label: tp(`status.${value}`) })),
  ], [t, i18n.language])

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
      setSelectedId(current => current ?? mapped[0]?.id ?? null)
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
  const from = filtered.length ? ((safePage - 1) * pageSize) + 1 : 0
  const to = Math.min(safePage * pageSize, filtered.length)
  const pages = pageNumbers(safePage, pageCount)
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
      <form className="ap-filters application-filters" onSubmit={event => { event.preventDefault(); setPage(1); notify(tp('foundToast', { count: filtered.length })) }}>
        <div className="ap-filter-search wide">
          <span className="ap-filter-label">{tp('searchLabel')}</span>
          <label className="ap-search adm-search application-search">
            <Search />
            <input value={query} onChange={event => setQuery(event.target.value)} placeholder={tp('searchPlaceholder')} aria-label={tp('searchAria')} />
          </label>
        </div>
        <AdminSelect label={tp('statusLabel')} value={status} onChange={value => { setStatus(value); setPage(1) }} options={statusOptions} />
        <AdminSelect label={tp('categoryLabel')} value={category} onChange={value => { setCategory(value); setPage(1) }} options={categoryOptions} />
        <AdminSelect label={tp('provinceLabel')} value={province} onChange={value => { setProvince(value); setPage(1) }} options={provinceOptions} />
        <AdminDateRange label={tp('dateRangeLabel')} from={dateFrom} to={dateTo} onChange={range => { setDateFrom(range.from); setDateTo(range.to) }} />
        <div className="ap-filter-actions">
          <button type="button" className="ap-ghost filter-reset" onClick={reset}>{tp('reset')}</button>
          <button type="submit" className="ap-primary filter-search">{tp('search')}</button>
        </div>
      </form>
      <section className="applications-table-card">
        <header>
          <h2>{tp('listTitle', { count: records.length })}</h2>
          {selectedRows.length > 0 && (
            <div className="bulk-actions">
              <span>{tp('selectedCount', { count: selectedRows.length })}</span>
              <button className="approve" disabled={busy || !canReview} title={deniedTitle(canReview)} onClick={() => runBulk('approve')}>{tp('bulkApprove')}</button>
              <button className="reject" disabled={busy || !canReview} title={deniedTitle(canReview)} onClick={() => runBulk('reject')}>{tp('bulkReject')}</button>
            </div>
          )}
        </header>
        <div className="applications-table-scroll">
          <div className="applications-table">
            <div className="applications-tr applications-th">
              <input type="checkbox" aria-label={tp('selectAllAria')} disabled={pageTargets.length === 0} checked={pageTargets.length > 0 && pageTargets.every(item => selectedRows.includes(item.id))} onChange={togglePage} />
              <b>{tp('colId')}</b><b>{tp('colShop')}</b><b>{tp('colOwner')}</b><b>{tp('colProvince')}</b><b>{tp('colCategory')}</b><b>{tp('colAppliedAt')}</b><b>{tp('colStatus')}</b><b>{tp('colActions')}</b><span />
            </div>
            {visible.map(item => (
              <div className={`applications-tr ${selectedId === item.id ? 'selected' : ''}`} key={item.id} onClick={() => { setSelectedId(item.id); setRevealedContact(null) }}>
                <input type="checkbox" aria-label={tp('selectRowAria', { name: item.shop })} disabled={!selectedRows.includes(item.id) && !canSelect(item)} checked={selectedRows.includes(item.id)} onClick={event => event.stopPropagation()} onChange={() => toggleRow(item)} />
                <span>{item.id}</span>
                <div className="application-shop"><ShopMark item={item} /><b>{item.shop}</b></div>
                <p><b>{item.owner}</b><span>{maskPhone(item.phone)}</span></p>
                <span>{item.province}</span>
                <span><i className={`application-category ${item.categoryClass}`}>{item.category}</i></span>
                <span className="applied-at">{item.appliedAt}</span>
                <span><i className={`status-pill ${statusPillClass(item.status)}`}><small />{statusText(item.status)}</i></span>
                <button className="review-button" onClick={event => { event.stopPropagation(); setSelectedId(item.id); setRevealedContact(null) }}>
                  {item.status === 'PENDING_REVIEW' ? tp('review') : tp('viewDetail')}
                </button>
                <button className="more-button" aria-label={tp('moreAria', { name: item.shop })} onClick={event => { event.stopPropagation(); notify(tp('moreToast', { name: item.shop })) }}>
                  <MoreVertical />
                </button>
              </div>
            ))}
            {filtered.length === 0 && <div className="applications-empty">{tp('emptyFiltered')}</div>}
          </div>
        </div>
        <footer>
          <div>
            {tp('show')}{' '}
            <AdminSelect compact className="adm-page-size" label={tp('pageSizeLabel')} value={String(pageSize)} onChange={value => { setPageSize(Number(value)); setPage(1) }} options={[{ value: '10', label: '10' }, { value: '25', label: '25' }]} />
            {' '}{tp('perPage')}
          </div>
          <span>{tp('range', { from, to, total: filtered.length })}</span>
          <nav aria-label={tp('paginationAria')}>
            <button disabled={safePage <= 1} onClick={() => setPage(value => Math.max(1, value - 1))}><ChevronLeft /></button>
            {pages.map(number => <button className={safePage === number ? 'current' : ''} key={number} onClick={() => setPage(number)}>{number}</button>)}
            <button disabled={safePage >= pageCount} onClick={() => setPage(value => Math.min(pageCount, value + 1))}><ChevronRight /></button>
          </nav>
        </footer>
      </section>
    </div>

    <AdminSidePanel
      variant="drawer"
      className="application-detail"
      open={selectedId != null}
      onClose={() => { setSelectedId(null); setRevealedContact(null) }}
      title={tp('detailTitle')}
      tabs={[
        { id: 'info', label: tp('tabInfo') },
        { id: 'documents', label: tp('tabDocuments') },
        { id: 'history', label: tp('tabHistory') },
      ]}
      activeTab={tab}
      onTabChange={(id) => setTab(id as DetailTab)}
      footer={selected ? (
        <>
          <button type="button" className="asp-secondary contact" onClick={() => {
            const email = revealedContact?.shopId === selected.id ? revealedContact.email : selected.email
            if (email && email !== '-' && !email.includes('*')) window.location.href = `mailto:${email}`
            else notify(tp('contactReveal.needRevealForMail'))
          }}>{tp('contactOwner')}</button>
          {canRevealContact && (
            <button type="button" className="asp-secondary" disabled={revealBusy || revealedContact?.shopId === selected.id}
              title={deniedTitle(canRevealContact)}
              onClick={() => {
                if (revealedContact?.shopId === selected.id) return
                setRevealBusy(true)
                adminApplicationApi.revealContact(selected.id)
                  .then(contact => {
                    setRevealedContact({
                      shopId: selected.id,
                      phone: contact.phone || '',
                      email: contact.email || '',
                    })
                    notify(tp('contactReveal.revealedToast'))
                  })
                  .catch(error => notify(error instanceof ApiError ? error.message : tp('contactReveal.revealError')))
                  .finally(() => setRevealBusy(false))
              }}>{revealedContact?.shopId === selected.id ? tp('contactReveal.revealed') : tp('contactReveal.button')}</button>
          )}
          {selected.status === 'PENDING_REVIEW' ? (
            <>
              <button type="button" className="asp-primary approve" disabled={busy || !canReview} title={deniedTitle(canReview)} onClick={() => decide('approve')}><Check />{tp('approveShop')}</button>
              <button type="button" className="asp-danger reject" disabled={busy || !canReview} title={deniedTitle(canReview)} onClick={() => decide('reject')}><CircleX />{tp('reject')}</button>
            </>
          ) : null}
        </>
      ) : null}
    >
      {selected ? (
        <>
          <section className="detail-summary">
            <ShopMark item={selected} />
            <div>
              <b>{selected.shop}</b>
              <span className={`status-pill ${statusPillClass(selected.status)}`}><small />{statusText(selected.status)}</span>
              <p>{tp('appliedAt', { date: selected.appliedAt })}</p>
            </div>
          </section>
          {tab === 'info' ? (
            <div className="detail-body">
              <dl>
                <div><dt><Home />{tp('fieldShopId')}</dt><dd>{selected.id}</dd></div>
                <div><dt><Home />{tp('fieldShopName')}</dt><dd>{selected.shop}</dd></div>
                <div>
                  <dt><UsersRound />{tp('fieldOwner')}</dt>
                  <dd>
                    {selected.owner}
                    {selected.otherShops ? <small className="owner-siblings">{tp('otherShops', { names: selected.otherShops })}</small> : null}
                  </dd>
                </div>
                <div><dt><Mail />{tp('fieldEmail')}</dt><dd>{revealedContact?.shopId === selected.id ? revealedContact.email : maskEmail(selected.email)}</dd></div>
                <div><dt><Phone />{tp('fieldPhone')}</dt><dd>{revealedContact?.shopId === selected.id ? revealedContact.phone : maskPhone(selected.phone)}</dd></div>
                <div><dt><MapPin />{tp('fieldProvince')}</dt><dd>{selected.province}</dd></div>
                <div><dt><MapPin />{tp('fieldAddress')}</dt><dd>{selected.address}</dd></div>
                <div><dt><FileText />{tp('fieldCategory')}</dt><dd>{selected.category}</dd></div>
                <div><dt><LockKeyhole />{tp('fieldDescription')}</dt><dd>{selected.description}</dd></div>
              </dl>
              <div className="social-row">
                <b><Building2 />{tp('socialTitle')}</b>
                {([
                  [tp('socialWebsite'), selected.website],
                  ['LINE', selected.lineId],
                  ['Facebook', selected.facebookUrl],
                  ['Instagram', selected.instagramUrl],
                  ['TikTok', selected.tiktokUrl],
                ] as const).map(([label, raw]) => {
                  const link = socialLink(raw || '')
                  return (
                    <p key={label}>
                      <span>
                        {label === 'LINE' ? <MessageCircle /> : label === 'Facebook' ? <Facebook /> : label === 'Instagram' ? <Instagram /> : null}
                        {label}
                      </span>
                      {link.href ? <a href={link.href} target="_blank" rel="noreferrer">{link.label}</a> : <a>{link.label}</a>}
                    </p>
                  )
                })}
              </div>
            </div>
          ) : tab === 'documents' ? (
            <div className="detail-documents">
              {selected.documents.length
                ? selected.documents.map(document => (
                  <p key={document.id}>
                    <FileCheck2 />
                    <span><b>{document.label}</b><small>{document.filename} · {fileSize(document.fileSize)}</small></span>
                    <button type="button" className="document-download" onClick={() => downloadDocument(document.id, document.filename)}>
                      <Download />{tp('download')}
                    </button>
                  </p>
                ))
                : <p><FileText /><span><b>{tp('noDocumentsTitle')}</b><small>-</small></span></p>}
            </div>
          ) : (
            <div className="detail-history">
              <p><Clock3 /><span><b>{tp('historySubmitted')}</b><small>{selected.appliedAt}</small></span></p>
              {selected.status === 'APPROVED' && <p><FileCheck2 /><span><b>{tp('historyActive')}</b><small>{selected.updatedAt}</small></span></p>}
              {selected.status === 'REJECTED' && <p><CircleX /><span><b>{tp('historyRejected')}</b><small>{selected.updatedAt}</small></span></p>}
              {selected.status === 'PENDING_REVIEW' && <p><FileCheck2 /><span><b>{tp('historyPending')}</b><small>{tp('historyPendingHint')}</small></span></p>}
              {selected.status === 'SUSPENDED' && <p><CircleX /><span><b>{tp('historySuspended')}</b><small>{selected.updatedAt}</small></span></p>}
              {canAudit && (
                <p className="ap-muted">
                  <a href={`/admin/audit-logs?targetType=SHOP&targetId=${selected.id}&kind=BUSINESS`}>{t('adminPlatform.audit.openForTarget')}</a>
                </p>
              )}
            </div>
          )}
        </>
      ) : <div className="detail-body"><p>{tp('emptyDetail')}</p></div>}
    </AdminSidePanel>
    {notice && <div className="admin-toast" role="status">{notice}</div>}
  </main>
}
