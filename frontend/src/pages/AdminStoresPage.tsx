import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  Check, ChevronDown, ChevronLeft, ChevronRight,
  CircleX, Clock3, Download, MoreVertical, Plus,
  Search, SlidersHorizontal, Store,
} from 'lucide-react'
import { SkeletonTable, SkeletonForm, LoadError } from '../components/feedback/Skeleton'
import ReasonDialog, { SUSPEND_REASON_CODES } from '../components/admin/ReasonDialog'
import { Drawer, Pill, errorMessage, statusTone } from '../components/admin/PlatformBits'
import { QuotaBar, buildUsageQuotas, quotaRatio, quotaTone } from '../components/admin/QuotaBar'
import { useDelayedLoading } from '../hooks/useDelayedLoading'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar, { refreshAdminStoreApplications } from '../components/AdminSidebar'
import { useAuth } from '../contexts/AuthContext'
import { hasPlatformAccess } from '../services/permissions'
import { usePermissions } from '../hooks/usePermissions'
import { formatDateTime } from '../services/adminFormat'
import { maskEmail, maskPhone } from '../utils/maskContact'
import {
  adminApplicationApi, adminPlatformApi, ApiError, mediaUrl,
  type AdminShopDetail, type AdminShopUsage, type AdminStoreApplication,
} from '../services/api'
import '../styles/admin-dashboard.css'
import '../styles/admin-stores.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import AdminSelect from '../components/admin/AdminSelect'

type StoreStatusCode = AdminStoreApplication['status']
type StoreRecord = {
  id: number
  logo: string
  logoUrl: string | null
  logoClass: string
  name: string
  handle: string
  slug: string
  products: number
  owner: string
  phone: string
  email: string
  category: string
  categoryClass: string
  province: string
  status: StoreStatusCode
  registeredAt: string
  sales: number
  trend: number | null
  appeal: string
  otherShops: string
}

const STATUS_FILTERS: Array<'ALL' | StoreStatusCode> = ['ALL', 'APPROVED', 'PENDING_REVIEW', 'SUSPENDED', 'REJECTED']

function share(count: number, total: number) {
  if (!total) return '0.0'
  return ((count / total) * 100).toFixed(1)
}

function mapStore(item: AdminStoreApplication): StoreRecord {
  return {
    id: item.shopId,
    logo: item.shopName.trim().charAt(0) || 'S',
    logoUrl: item.logoUrl || null,
    logoClass: 'garden',
    name: item.shopName,
    handle: item.slug ? `@${item.slug}` : '-',
    slug: item.slug || '',
    products: 0,
    owner: item.ownerName || '-',
    phone: item.phone || '-',
    email: item.email || '-',
    category: item.category || '-',
    categoryClass: 'home',
    province: item.province || '-',
    status: item.status,
    appeal: item.reinstatementMessage || '',
    otherShops: (item.otherShops || []).map((shop) => shop.shopName).join(', '),
    registeredAt: item.appliedAt,
    sales: 0,
    trend: null,
  }
}

function formatAppliedAt(value: string, locale: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const tag = locale.startsWith('zh') ? 'zh-CN' : locale.startsWith('en') ? 'en-US' : 'th-TH'
  return new Intl.DateTimeFormat(tag, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}

function formatCount(value: number, locale: string) {
  const tag = locale.startsWith('zh') ? 'zh-CN' : locale.startsWith('en') ? 'en-US' : 'th-TH'
  return value.toLocaleString(tag)
}

function ownerDisplayName(detail: AdminShopDetail | null): string {
  if (!detail) return '—'
  if (detail.owner) {
    const name = [detail.owner.firstName, detail.owner.lastName].map(p => (p ?? '').trim()).filter(Boolean).join(' ')
    if (name) return name
  }
  return detail.merchantName?.trim() || '—'
}

export default function AdminStoresPage(){
  const { t, i18n } = useTranslation('admin')
  const tp = (key: string, options?: Record<string, unknown>) => t(`adminPlatform.storesPage.${key}`, options)
  const td = (key: string, options?: Record<string, unknown>) => t(`adminPlatform.stores.detail.${key}`, options)
  const ta = (key: string, options?: Record<string, unknown>) => t(`adminPlatform.${key}`, options)
  const {user,loading}=useAuth()
  const {can,deniedTitle}=usePermissions()
  // pending/rejected stores open the application review; active stores are suspended, suspended ones restored
  const canManage=(store:StoreRecord)=>store.status==='PENDING_REVIEW'||store.status==='REJECTED'?can('PLATFORM_APPLICATION_READ'):can(store.status==='SUSPENDED'?'PLATFORM_SHOP_RESTORE':'PLATFORM_SHOP_SUSPEND')
  const canAudit=can('PLATFORM_AUDIT_READ')
  const canSuspend=can('PLATFORM_SHOP_SUSPEND')
  const canRestore=can('PLATFORM_SHOP_RESTORE')
  const navigate=useNavigate()
  const [rows,setRows]=useState<StoreRecord[]>([])
  const [loaded,setLoaded]=useState(false)
  const [busyId,setBusyId]=useState<number|null>(null)
  const [storeConfirm,setStoreConfirm]=useState<StoreRecord|null>(null)
  const [bulkConfirm,setBulkConfirm]=useState<'suspend'|'reactivate'|null>(null)
  const [confirmError,setConfirmError]=useState('')
  const [query,setQuery]=useState('')
  const [status,setStatus]=useState<'ALL' | StoreStatusCode>('ALL')
  const [category,setCategory]=useState('ALL')
  const [province,setProvince]=useState('ALL')
  const [notice,setNotice]=useState('')
  const [selected,setSelected]=useState<number[]>([])
  const [page,setPage]=useState(1)
  const [pageSize,setPageSize]=useState(10)

  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detailKey, setDetailKey] = useState(0)
  const [detail, setDetail] = useState<AdminShopDetail | null>(null)
  const [usage, setUsage] = useState<AdminShopUsage | null>(null)
  const [detailError, setDetailError] = useState('')
  const [usageError, setUsageError] = useState('')
  const [detailForbidden, setDetailForbidden] = useState(false)
  const [drawerConfirm, setDrawerConfirm] = useState<'suspend' | 'reactivate' | null>(null)
  const [drawerBusy, setDrawerBusy] = useState(false)
  const [drawerActionError, setDrawerActionError] = useState('')
  const [serverMatchIds, setServerMatchIds] = useState<number[] | null>(null)

  useEffect(() => {
    if (!user || !hasPlatformAccess(user)) return
    let active = true
    adminApplicationApi.list().then(data => {
      if (active) setRows(data.map(mapStore))
    }).catch(error => {
      if (!active) return
      setRows([])
      setNotice(error instanceof ApiError ? error.message : tp('loadError'))
    }).finally(() => { if (active) setLoaded(true) })
    return () => { active = false }
  }, [user])

  // Phone/email/name search against full DB values via GET /api/admin/shops?q= (response stays masked).
  useEffect(() => {
    if (!user || !hasPlatformAccess(user)) return
    let cancelled = false
    adminPlatformApi.shops({
      q: query.trim() || undefined,
      status: status === 'ALL' ? undefined : status,
      size: 500,
      sort: 'shopId',
      dir: 'asc',
    }).then(page => {
      if (!cancelled) setServerMatchIds(page.items.map(item => item.shopId))
    }).catch(() => {
      if (!cancelled) setServerMatchIds(null)
    })
    return () => { cancelled = true }
  }, [user, query, status])

  useEffect(() => {
    if (selectedId === null) {
      setDetail(null)
      setUsage(null)
      setDetailError('')
      setUsageError('')
      setDetailForbidden(false)
      return
    }
    let cancelled = false
    setDetail(null)
    setUsage(null)
    setDetailError('')
    setUsageError('')
    setDetailForbidden(false)
    adminPlatformApi.shop(selectedId)
      .then(value => { if (!cancelled) setDetail(value) })
      .catch(error => {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 403) {
          setDetailForbidden(true)
          setDetailError(td('forbidden'))
          return
        }
        setDetailError(errorMessage(error, td('loadError')))
      })
    adminPlatformApi.shopUsage(selectedId)
      .then(value => { if (!cancelled) setUsage(value) })
      .catch(error => {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 403) {
          setDetailForbidden(true)
          setUsageError(td('forbidden'))
          return
        }
        setUsageError(errorMessage(error, ta('errors.usage')))
      })
    return () => { cancelled = true }
  }, [selectedId, detailKey])

  const filtered=useMemo(()=>rows.filter(store=>{
    if (serverMatchIds && !serverMatchIds.includes(store.id)) return false
    if (status !== 'ALL' && store.status !== status) return false
    return (category==='ALL'||store.category===category)
      && (province==='ALL'||store.province===province)
  }),[rows,serverMatchIds,status,category,province])
  const pageCount=Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage=Math.min(page, pageCount)
  const visible=filtered.slice((safePage - 1) * pageSize, safePage * pageSize)
  const activeCount=rows.filter(store=>store.status==='APPROVED').length
  const pendingCount=rows.filter(store=>store.status==='PENDING_REVIEW').length
  const suspendedCount=rows.filter(store=>store.status==='SUSPENDED').length
  const selectedActive=useMemo(()=>selected.map(id=>rows.find(row=>row.id===id)).filter((row):row is StoreRecord=>!!row&&row.status==='APPROVED'),[selected,rows])
  const selectedSuspended=useMemo(()=>selected.map(id=>rows.find(row=>row.id===id)).filter((row):row is StoreRecord=>!!row&&row.status==='SUSPENDED'),[selected,rows])

  useEffect(() => { setPage(1) }, [query, status, category, province, pageSize])
  useEffect(() => { if (page > pageCount) setPage(pageCount) }, [page, pageCount])

  const listLoading = Boolean(user && hasPlatformAccess(user) && !loaded)
  const { showSkeleton, markHasData } = useDelayedLoading(listLoading, 150)
  useEffect(() => {
    if (loaded) markHasData()
  }, [loaded, markHasData])

  if(loading)return <main className="auth-loading">{t('adminPlatform.common.checkingAccess')}</main>
  if(!user){window.location.replace('/login');return null}
  if(!hasPlatformAccess(user)){window.location.replace('/dashboard');return null}

  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(''),1900)}
  const reset=()=>{setQuery('');setStatus('ALL');setCategory('ALL');setProvince('ALL')}
  const toggleAll=()=>setSelected(selected.length===visible.length&&visible.length>0?[]:visible.map(row=>row.id))
  const toggle=(id:number)=>setSelected(items=>items.includes(id)?items.filter(item=>item!==id):[...items,id])
  const openDetail = (id: number) => { setDrawerConfirm(null); setDrawerActionError(''); setSelectedId(id) }
  const closeDrawer = () => { setSelectedId(null); setDrawerConfirm(null); setDrawerActionError('') }
  const manage=async(store:StoreRecord)=>{
    if(store.status==='PENDING_REVIEW'||store.status==='REJECTED'){
      navigate(`/admin/store-applications?shop=${store.id}`)
      return
    }
    if(busyId)return
    setConfirmError('')
    setStoreConfirm(store)
  }
  const reloadRows=async()=>{
    try {
      const data=await adminApplicationApi.list()
      setRows(data.map(mapStore))
    } catch { /* keep current rows */ }
  }
  const refreshDetail = () => setDetailKey(key => key + 1)
  const applyStoreStatus=async(reason:string, reasonCode?:string)=>{
    const store=storeConfirm
    if(!store||busyId)return
    const reactivate=store.status==='SUSPENDED'
    setConfirmError('')
    setBusyId(store.id)
    try {
      if(reactivate) await adminPlatformApi.reactivateShop(store.id,{reason, reasonCode})
      else await adminPlatformApi.suspendShop(store.id,{reason, reasonCode})
      setStoreConfirm(null)
      await reloadRows()
      refreshAdminStoreApplications()
      if (selectedId === store.id) refreshDetail()
      notify(tp(reactivate?'reactivatedToast':'suspendedToast', { name: store.name }))
    } catch (error) {
      setConfirmError(error instanceof ApiError ? error.message : tp('saveError'))
    } finally {
      setBusyId(null)
    }
  }
  const applyBulk=async(reason:string, reasonCode?:string)=>{
    if(!bulkConfirm||busyId)return
    const ids=(bulkConfirm==='suspend'?selectedActive:selectedSuspended).map(row=>row.id)
    if(!ids.length)return
    setConfirmError('')
    setBusyId(-1)
    try {
      const result=await adminApplicationApi.bulk(bulkConfirm, ids, { reasonCode, reason: reason || undefined })
      setBulkConfirm(null)
      setSelected([])
      await reloadRows()
      refreshAdminStoreApplications()
      if (selectedId != null && ids.includes(selectedId)) refreshDetail()
      notify(result.updated
        ? t(bulkConfirm==='suspend'?'confirm.bulkSuspendedToast':'confirm.bulkReactivatedToast', { count: result.updated })
        : t('confirm.bulkNoneToast'))
    } catch (error) {
      setConfirmError(error instanceof ApiError ? error.message : tp('saveError'))
    } finally {
      setBusyId(null)
    }
  }
  const applyDrawerAction = async (reason: string, reasonCode?: string) => {
    if (!detail || !drawerConfirm || drawerBusy) return
    setDrawerBusy(true)
    setDrawerActionError('')
    try {
      // Backend ReasonRequest requires reason text (min 5); ReasonDialog enforces it (no ADMIN_SUSPEND fallback).
      const body = { reason, reasonCode }
      const response = drawerConfirm === 'suspend'
        ? await adminPlatformApi.suspendShop(detail.shopId, body)
        : await adminPlatformApi.reactivateShop(detail.shopId, body)
      setDetail(response.shop)
      setUsage(response.shop.usage ?? usage)
      setDrawerConfirm(null)
      await reloadRows()
      refreshAdminStoreApplications()
      notify(tp(drawerConfirm === 'suspend' ? 'suspendedToast' : 'reactivatedToast', { name: response.shop.name }))
    } catch (error) {
      setDrawerActionError(errorMessage(error, ta('errors.action')))
    } finally {
      setDrawerBusy(false)
    }
  }
  const from=filtered.length?((safePage-1)*pageSize)+1:0
  const to=Math.min(safePage*pageSize, filtered.length)
  const pages=Array.from({length:pageCount},(_,index)=>index+1).filter(number=>number===1||number===pageCount||Math.abs(number-safePage)<=2)
  const statusOptions=STATUS_FILTERS.map(value=>({
    value,
    label: value==='ALL' ? t('adminPlatform.common.all') : tp(`status.${value}`),
  }))
  const categoryOptions=[{ value: 'ALL', label: t('adminPlatform.common.all') }, ...[...new Set(rows.map(row=>row.category))].map(value=>({ value, label: value }))]
  const provinceOptions=[{ value: 'ALL', label: t('adminPlatform.common.all') }, ...[...new Set(rows.map(row=>row.province))].map(value=>({ value, label: value }))]
  const locale=i18n.language || 'th'
  const selectedRow = rows.find(row => row.id === selectedId) ?? null
  const shownUsage = usage ?? detail?.usage ?? null
  const suspension = detail?.suspension
  const showSuspension = detail?.status === 'SUSPENDED' && suspension && (suspension.suspendedAt || suspension.reasonCode || suspension.reason)
  const reasonCodeLabel = (code: string | null | undefined) => {
    if (!code) return ''
    return t(`adminPlatform.reason.codes.${code}`, { defaultValue: code })
  }

  return <main className="admin-shell stores-shell">
    <AdminHeader user={user} />

    <AdminSidebar active="stores" className="stores-sidebar"/>

    <div className="admin-content stores-content">
      <section className="stores-heading">
        <div className="stores-title"><span><Store/></span><div><h1>{tp('title')}</h1><p>{tp('subtitle')}</p></div></div>
        <button className="add-store" onClick={()=>notify(tp('addStoreToast'))}><Plus/>{tp('addStore')}</button>
      </section>

      <section className="store-kpis" aria-label={tp('kpiAria')}>
        <article className="all"><span><Store/></span><div><b>{tp('kpiAll')}</b><strong>{formatCount(rows.length, locale)}</strong><small>{tp('kpiAllHint')}</small></div></article>
        <article className="approved"><span><Check/></span><div><b>{tp('kpiApproved')}</b><strong>{formatCount(activeCount, locale)}</strong><small>{tp('kpiShare', { pct: share(activeCount, rows.length) })}</small></div></article>
        <article className="pending"><span><Clock3/></span><div><b>{tp('kpiPending')}</b><strong>{formatCount(pendingCount, locale)}</strong><small>{tp('kpiShare', { pct: share(pendingCount, rows.length) })}</small></div></article>
        <article className="suspended"><span><CircleX/></span><div><b>{tp('kpiSuspended')}</b><strong>{formatCount(suspendedCount, locale)}</strong><small>{tp('kpiShare', { pct: share(suspendedCount, rows.length) })}</small></div></article>
      </section>

      <form className="ap-filters store-filters" onSubmit={event => { event.preventDefault(); notify(tp('foundToast', { count: filtered.length })) }}>
        <div className="ap-filter-search wide">
          <span className="ap-filter-label">{tp('searchLabel')}</span>
          <label className="store-search adm-search ap-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder={tp('searchPlaceholder')} aria-label={tp('searchAria')}/></label>
        </div>
        <AdminSelect label={tp('statusLabel')} value={status} onChange={value=>setStatus(value as 'ALL' | StoreStatusCode)} options={statusOptions}/>
        <AdminSelect label={tp('categoryLabel')} value={category} onChange={setCategory} options={categoryOptions}/>
        <AdminSelect label={tp('provinceLabel')} value={province} onChange={setProvince} options={provinceOptions}/>
        <div className="ap-filter-actions">
          <button type="button" className="ap-ghost reset-filter" onClick={reset}>{tp('reset')}</button>
          <button type="submit" className="ap-primary search-filter">{tp('search')}</button>
        </div>
      </form>

      <section className="stores-table-card">
        <header>
          <h2>{tp('listTitle', { count: formatCount(rows.length, locale) })}</h2>
          <div>
            {selected.length>0&&(
              <div className="bulk-actions" style={{display:'inline-flex',gap:8,alignItems:'center',marginRight:12}}>
                <span>{tp('selectedCount', { count: selected.length })}</span>
                {selectedActive.length>0&&<button type="button" disabled={!!busyId||!canSuspend} title={deniedTitle(canSuspend)} onClick={()=>{setConfirmError('');setBulkConfirm('suspend')}}>{t('confirm.bulkSuspendTitle', { count: selectedActive.length })}</button>}
                {selectedSuspended.length>0&&<button type="button" disabled={!!busyId||!canRestore} title={deniedTitle(canRestore)} onClick={()=>{setConfirmError('');setBulkConfirm('reactivate')}}>{t('confirm.bulkReactivateTitle', { count: selectedSuspended.length })}</button>}
              </div>
            )}
            <button onClick={()=>notify(tp('exportToast'))}><Download/>{tp('export')}<ChevronDown/></button>
            <button onClick={()=>notify(tp('columnsToast'))}><SlidersHorizontal/>{tp('columns')}</button>
          </div>
        </header>
        <div className="stores-table-scroll"><div className="stores-table">
          <div className="stores-tr stores-th"><input type="checkbox" aria-label={tp('selectAllAria')} checked={visible.length>0&&visible.every(store=>selected.includes(store.id))} onChange={toggleAll}/><b>{tp('colId')}</b><b>{tp('colStore')}</b><b>{tp('colOwner')}</b><b>{tp('colCategory')}</b><b>{tp('colProvince')}</b><b>{tp('colStatus')}</b><b>{tp('colRegistered')}</b><b>{tp('colSales')}</b><b>{tp('colActions')}</b><span/></div>
          {showSkeleton && listLoading && rows.length === 0 ? <SkeletonTable rows={8} cols={9} /> : null}
          {loaded && rows.length === 0 && notice ? <LoadError message={notice} onRetry={() => window.location.reload()} /> : null}
          {visible.map(store=><div className={`stores-tr${selectedId===store.id?' selected':''}`} key={store.id} role="button" tabIndex={0}
            onClick={()=>openDetail(store.id)}
            onKeyDown={event=>{ if (event.key==='Enter' || event.key===' ') { event.preventDefault(); openDetail(store.id) } }}>
            <input type="checkbox" aria-label={tp('selectRowAria', { name: store.name })} checked={selected.includes(store.id)} onClick={event=>event.stopPropagation()} onChange={()=>toggle(store.id)}/><span className="num">{store.id}</span>
            <div className="store-identity"><i className={store.logoClass}>{store.logoUrl?<img src={mediaUrl(store.logoUrl)} alt="" />:store.logo}</i><p><b>{store.name}{store.status==='APPROVED'&&<small className="verified">✓</small>}</b><span>{store.handle} | {store.products} {tp('products')}</span>{store.appeal&&<em className="reinstatement-flag">{tp('appealFlag')}</em>}{canAudit&&<small className="ap-muted"><a href={`/admin/audit-logs?targetType=SHOP&targetId=${store.id}&kind=BUSINESS`} onClick={event=>event.stopPropagation()}>{t('adminPlatform.audit.openForTarget')}</a></small>}</p></div>
            <p className="owner"><b>{store.owner}</b><span>{maskPhone(store.phone)}</span>{store.otherShops ? <small className="owner-siblings">{tp('otherShops', { names: store.otherShops })}</small> : null}</p>
            <span><i className={`category-pill ${store.categoryClass}`}>{store.category}</i></span><span>{store.province}</span>
            <span><i className={`status-pill ${store.status==='APPROVED'?'active':store.status==='PENDING_REVIEW'?'waiting':'blocked'}`}><small/>{tp(`status.${store.status}`)}</i></span>
            <span className="registered">{formatAppliedAt(store.registeredAt, locale)}</span>
            <span className="sales"><b className="num">{tp('currencyPrefix')}{store.sales.toLocaleString()}</b>{store.trend!==null&&<em className={`num ${store.trend<0?'down':''}`}>{store.trend>0?'↑':'↓'} {Math.abs(store.trend)}%</em>}</span>
            <button className="manage" disabled={busyId===store.id||!canManage(store)} title={deniedTitle(canManage(store))} onClick={event=>{ event.stopPropagation(); void manage(store) }}>{store.status==='PENDING_REVIEW'||store.status==='REJECTED'?tp('review'):tp('manage')}</button>
            <button className="more" aria-label={tp('moreAria', { name: store.name })} onClick={event=>{ event.stopPropagation(); openDetail(store.id) }}><MoreVertical/></button>
          </div>)}
          {filtered.length===0&&<div className="stores-empty">{tp('emptyFiltered')}</div>}
        </div></div>
        <footer><div>{tp('show')} <AdminSelect compact className="adm-page-size" label={tp('pageSizeLabel')} value={String(pageSize)} onChange={value=>setPageSize(Number(value))} options={['10', '25', '50'].map(value=>({ value, label: value }))}/> {tp('perPage')}</div><span>{tp('range', { from, to, total: formatCount(rows.length, locale) })}</span><nav aria-label={tp('paginationAria')}><button disabled={safePage<=1} onClick={()=>setPage(value=>Math.max(1, value-1))}><ChevronLeft/></button>{pages.map(number=><button className={safePage===number?'current':''} key={number} onClick={()=>setPage(number)}>{number}</button>)}<button disabled={safePage>=pageCount} onClick={()=>setPage(value=>Math.min(pageCount, value+1))}><ChevronRight/></button></nav></footer>
      </section>
    </div>
    {notice&&<div className="admin-toast" role="status">{notice}</div>}
    {selectedId !== null && (
      <Drawer title={td('title')} subtitle={detail?.name || selectedRow?.name} onClose={closeDrawer} wide>
        {detailForbidden ? (
          <p className="ap-error" role="alert">{td('forbidden')}</p>
        ) : detailError && !detail ? (
          <LoadError message={detailError} onRetry={refreshDetail} />
        ) : !detail ? (
          <SkeletonForm fields={6} />
        ) : (
          <>
            <div className="ap-drawer-status">
              <Pill tone={statusTone(detail.status)}>{tp(`status.${detail.status}`, { defaultValue: detail.status })}</Pill>
              <span className="num">#{detail.shopId}</span>
            </div>
            <section className="ap-section">
              <h3>{td('info')}</h3>
              <div className="store-identity" style={{ marginBottom: 12 }}>
                <i className={selectedRow?.logoClass || 'garden'}>
                  {selectedRow?.logoUrl ? <img src={mediaUrl(selectedRow.logoUrl)} alt="" /> : (selectedRow?.logo || detail.name.trim().charAt(0) || 'S')}
                </i>
                <p>
                  <b>{detail.name}</b>
                  {detail.slug ? (
                    <span>
                      <a href={`/shop/${encodeURIComponent(detail.slug)}`} target="_blank" rel="noreferrer">
                        @{detail.slug}
                      </a>
                      {' · '}
                      <a href={`/shop/${encodeURIComponent(detail.slug)}`} target="_blank" rel="noreferrer">{td('openStorefront')}</a>
                    </span>
                  ) : null}
                </p>
              </div>
              <dl className="ap-fields">
                <div><dt>{td('status')}</dt><dd><Pill tone={statusTone(detail.status)}>{tp(`status.${detail.status}`, { defaultValue: detail.status })}</Pill></dd></div>
                <div><dt>{td('createdAt')}</dt><dd className="num">{formatDateTime(detail.createdAt)}</dd></div>
                <div>
                  <dt>{td('merchant')}</dt>
                  <dd>
                    {detail.merchantId ? (
                      <a href={`/admin/merchants?merchant=${encodeURIComponent(detail.merchantId)}`}>{detail.merchantName || ownerDisplayName(detail)}</a>
                    ) : (detail.merchantName || ownerDisplayName(detail))}
                    {detail.merchantStatus ? <small className="block">{ta(`status.${detail.merchantStatus}`, { defaultValue: detail.merchantStatus })}</small> : null}
                  </dd>
                </div>
                <div><dt>{td('owner')}</dt><dd>{ownerDisplayName(detail)}</dd></div>
                <div><dt>{td('phone')}</dt><dd className="num">{maskPhone(detail.owner?.phone ?? selectedRow?.phone)}</dd></div>
                <div><dt>{td('email')}</dt><dd className="num">{maskEmail(detail.owner?.email ?? selectedRow?.email)}</dd></div>
              </dl>
            </section>
            {showSuspension && (
              <section className="ap-section">
                <h3>{td('suspension')}</h3>
                <dl className="ap-fields">
                  {suspension?.suspendedAt && <div><dt>{td('suspendedAt')}</dt><dd className="num">{formatDateTime(suspension.suspendedAt)}</dd></div>}
                  {suspension?.reasonCode && <div><dt>{td('reasonCode')}</dt><dd>{reasonCodeLabel(suspension.reasonCode)}</dd></div>}
                  {suspension?.reason && <div className="full"><dt>{td('reason')}</dt><dd>{suspension.reason}</dd></div>}
                </dl>
              </section>
            )}
            <section className="ap-section">
              <h3>{td('planUsage')}</h3>
              <p className="ap-muted">{td('plan')}: <b>{shownUsage?.planCode || detail.usage?.planCode || td('noPlan')}</b></p>
              {usageError && !shownUsage ? <p className="ap-error" role="alert">{usageError}</p>
                : !shownUsage ? <SkeletonForm fields={3} />
                : (
                  <div className="ap-quota-list">
                    {buildUsageQuotas(shownUsage, ta).map(row => {
                      const displayUsed = row.format ? row.format(row.used) : String(row.used)
                      const displayLimit = row.limit == null
                        ? ta('usage.unlimited')
                        : (row.format ? row.format(row.limit) : String(row.limit))
                      const pct = quotaRatio(row.used, row.limit)
                      const tone = quotaTone(row.used, row.limit, pct)
                      return (
                        <article key={row.key} className={`ap-quota-card ${tone}`}>
                          <header>
                            <b>{row.label}</b>
                            <strong className="num">{displayUsed}{row.limit != null ? ` / ${displayLimit}` : ` · ${ta('usage.unlimited')}`}</strong>
                          </header>
                          <QuotaBar
                            used={row.used}
                            limit={row.limit}
                            unlimitedLabel={ta('usage.unlimited')}
                            format={row.format}
                            showCaption={false}
                          />
                          {row.hint && <span>{row.hint}</span>}
                        </article>
                      )
                    })}
                  </div>
                )}
            </section>
            {canAudit && (
              <p className="ap-muted">
                <a href={`/admin/audit-logs?targetType=SHOP&targetId=${detail.shopId}&kind=BUSINESS`}>{td('auditLink')}</a>
              </p>
            )}
            {drawerActionError && !drawerConfirm && <p className="ap-error" role="alert">{drawerActionError}</p>}
            <footer className="ap-drawer-actions">
              {detail.status === 'APPROVED' && (
                <button type="button" className="ap-danger" disabled={!canSuspend || drawerBusy} title={deniedTitle(canSuspend)}
                  onClick={() => { setDrawerActionError(''); setDrawerConfirm('suspend') }}>{td('suspend')}</button>
              )}
              {detail.status === 'SUSPENDED' && (
                <button type="button" className="ap-primary" disabled={!canRestore || drawerBusy} title={deniedTitle(canRestore)}
                  onClick={() => { setDrawerActionError(''); setDrawerConfirm('reactivate') }}>{td('reactivate')}</button>
              )}
            </footer>
          </>
        )}
      </Drawer>
    )}
    {storeConfirm && (
      <ReasonDialog
        title={t(storeConfirm.status === 'SUSPENDED' ? 'confirm.reactivateStoreTitle' : 'confirm.suspendStoreTitle')}
        message={[
          t(storeConfirm.status === 'SUSPENDED' ? 'confirm.reactivateStoreBody' : 'confirm.suspendStoreBody', { name: storeConfirm.name }),
          storeConfirm.status === 'SUSPENDED' && storeConfirm.appeal ? t('confirm.appealNote', { appeal: storeConfirm.appeal }) : '',
        ].filter(Boolean).join(' ')}
        confirmLabel={t('confirm.confirm')}
        danger={storeConfirm.status !== 'SUSPENDED'}
        busy={busyId === storeConfirm.id}
        error={confirmError}
        reasonCodes={storeConfirm.status === 'SUSPENDED' ? undefined : SUSPEND_REASON_CODES}
        requireReasonCode={storeConfirm.status !== 'SUSPENDED'}
        onCancel={() => { setStoreConfirm(null); setConfirmError('') }}
        onConfirm={(reason, reasonCode) => void applyStoreStatus(reason, reasonCode)}
      />
    )}
    {bulkConfirm && (
      <ReasonDialog
        title={t(bulkConfirm === 'suspend' ? 'confirm.bulkSuspendTitle' : 'confirm.bulkReactivateTitle', {
          count: bulkConfirm === 'suspend' ? selectedActive.length : selectedSuspended.length,
        })}
        message={t(bulkConfirm === 'suspend' ? 'confirm.bulkSuspendBody' : 'confirm.bulkReactivateBody', {
          count: bulkConfirm === 'suspend' ? selectedActive.length : selectedSuspended.length,
        })}
        confirmLabel={t('confirm.confirm')}
        danger={bulkConfirm === 'suspend'}
        busy={busyId === -1}
        error={confirmError}
        reasonCodes={bulkConfirm === 'suspend' ? SUSPEND_REASON_CODES : undefined}
        requireReasonCode={bulkConfirm === 'suspend'}
        reasonOptional
        onCancel={() => { setBulkConfirm(null); setConfirmError('') }}
        onConfirm={(reason, reasonCode) => void applyBulk(reason, reasonCode)}
      />
    )}
    {drawerConfirm && detail && (
      <ReasonDialog
        title={t(drawerConfirm === 'suspend' ? 'confirm.suspendStoreTitle' : 'confirm.reactivateStoreTitle')}
        message={t(drawerConfirm === 'suspend' ? 'confirm.suspendStoreBody' : 'confirm.reactivateStoreBody', { name: detail.name })}
        confirmLabel={t('confirm.confirm')}
        danger={drawerConfirm === 'suspend'}
        busy={drawerBusy}
        error={drawerActionError}
        reasonCodes={drawerConfirm === 'suspend' ? SUSPEND_REASON_CODES : undefined}
        requireReasonCode={drawerConfirm === 'suspend'}
        onCancel={() => { setDrawerConfirm(null); setDrawerActionError('') }}
        onConfirm={(reason, reasonCode) => void applyDrawerAction(reason, reasonCode)}
      />
    )}
  </main>
}
