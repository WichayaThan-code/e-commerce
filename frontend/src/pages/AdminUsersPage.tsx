import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import {
  AlertTriangle, CalendarDays, Check, Download, Mail,
  MoreVertical, Plus, Search, Settings, ShieldCheck, Store, Trash2, UserRound, UsersRound, X,
} from 'lucide-react'
import { Pager } from '../components/admin/PlatformBits'
import { SkeletonForm, SkeletonTable, SkeletonText, SkeletonBusy, LoadError } from '../components/feedback/Skeleton'
import ConfirmDialog from '../components/form/ConfirmDialog'
import { useDelayedLoading } from '../hooks/useDelayedLoading'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { hasPlatformAccess } from '../services/permissions'
import {
  adminDirectoryApi, ApiError,
  type AdminActivityEvent, type AdminRoleSummary, type AdminUserDetail, type AdminUserFilterOptions, type AdminUserPage, type AdminUserQuery, type AdminUserRow, type AdminUserStats,
} from '../services/api'
import { formatChange, formatDateTime, formatNumber, initialOf, NOT_ENABLED, percentOf } from '../services/adminFormat'
import '../styles/admin-dashboard.css'
import '../styles/admin-users.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import AdminSelect from '../components/admin/AdminSelect'
import AdminDateRange from '../components/admin/AdminDateRange'
import AdminSidePanel from '../components/admin/AdminSidePanel'
import '../styles/admin-side-panel.css'

const USERS_TIP_KEY = 'tsf_admin_users_tip_dismissed'

type Tab = 'ข้อมูลผู้ใช้' | 'สิทธิ์การใช้งาน' | 'ประวัติ'
type Filters = { q: string; role: string; status: string; from: string; to: string }
const EMPTY_FILTERS: Filters = { q: '', role: '', status: '', from: '', to: '' }
const PAGE_SIZES = [10, 25, 50, 100]
const STATUS_LABEL: Record<string, string> = { ACTIVE: 'ใช้งาน', SUSPENDED: 'ระงับ' }

const roleTone = (context: string | null | undefined, code?: string) =>
  context === 'PLATFORM' ? 'admin' : code === 'STORE_OWNER' || context === 'STORE' ? 'owner' : 'general'
const avatarTone = (row: { primaryRole: { context: string | null; code: string } }) =>
  ({ admin: 'orange', owner: 'purple', general: 'gray' } as const)[roleTone(row.primaryRole.context, row.primaryRole.code)]
const fullName = (row: { firstName: string; lastName: string }) => `${row.firstName} ${row.lastName}`.trim()
const shopsLabel = (row: AdminUserRow) => {
  const names = [...new Set(row.shops.map(shop => shop.shopName))]
  return names.length === 0 ? '-' : names.length === 1 ? names[0] : `${names[0]} +${names.length - 1}`
}
const errorText = (error: unknown) => error instanceof ApiError ? error.message : 'โหลดข้อมูลไม่สำเร็จ'

export default function AdminUsersPage() {
  const { t } = useTranslation('admin')
  const { user, loading } = useAuth()
  const { can } = usePermissions()
  const allowed = !!user && hasPlatformAccess(user)
  const canAudit = can('PLATFORM_AUDIT_READ')
  const initialRole = useMemo(() => new URLSearchParams(window.location.search).get('role') ?? '', [])
  const [draft, setDraft] = useState<Filters>({ ...EMPTY_FILTERS, role: initialRole })
  const [filters, setFilters] = useState<Filters>({ ...EMPTY_FILTERS, role: initialRole })
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [reloadKey, setReloadKey] = useState(0)
  const [result, setResult] = useState<AdminUserPage | null>(null)
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [stats, setStats] = useState<AdminUserStats | null>(null)
  const [statsError, setStatsError] = useState('')
  const [options, setOptions] = useState<AdminUserFilterOptions | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<AdminUserDetail | null>(null)
  const [detailState, setDetailState] = useState<'idle' | 'loading' | 'error'>('idle')
  const [detailError, setDetailError] = useState('')
  const [history, setHistory] = useState<AdminActivityEvent[] | null>(null)
  const [historyError, setHistoryError] = useState('')
  const [selectedRows, setSelectedRows] = useState<string[]>([])
  const [tab, setTab] = useState<Tab>('ข้อมูลผู้ใช้')
  const [showTip, setShowTip] = useState(() => {
    try { return localStorage.getItem(USERS_TIP_KEY) !== '1' } catch { return true }
  })
  const dismissTip = () => {
    setShowTip(false)
    try { localStorage.setItem(USERS_TIP_KEY, '1') } catch { /* ignore */ }
  }
  const [exporting, setExporting] = useState(false)
  const [notice, setNotice] = useState('')
  const [accountDialog, setAccountDialog] = useState<'create' | 'edit' | null>(null)
  const [accountForm, setAccountForm] = useState({ firstName: '', lastName: '', email: '', phone: '' })
  const [platformRoles, setPlatformRoles] = useState<AdminRoleSummary[]>([])
  const [roleChoice, setRoleChoice] = useState('')
  const [saving, setSaving] = useState(false)
  const [userConfirm, setUserConfirm] = useState<'suspend' | 'restore' | 'delete' | null>(null)
  const [actionError, setActionError] = useState('')

  const notify = useCallback((message: string) => { setNotice(message); window.setTimeout(() => setNotice(''), 2400) }, [])
  const canCreate = can('PLATFORM_ACCOUNT_CREATE')
  const canUpdate = can('PLATFORM_ACCOUNT_UPDATE')
  const canSuspend = can('PLATFORM_ACCOUNT_SUSPEND')
  const canRestore = can('PLATFORM_ACCOUNT_RESTORE')
  const canDelete = can('PLATFORM_ACCOUNT_DELETE')
  const canAssignRole = can('PLATFORM_ACCOUNT_ROLE_ASSIGN')
  const query: AdminUserQuery = useMemo(() => ({ q: filters.q.trim(), role: filters.role, status: filters.status, from: filters.from, to: filters.to }), [filters])

  // live search: typing applies after a short pause
  useEffect(() => {
    if (draft.q === filters.q) return
    const timer = window.setTimeout(() => { setFilters(current => ({ ...current, q: draft.q })); setPage(0) }, 350)
    return () => window.clearTimeout(timer)
  }, [draft.q, filters.q])

  useEffect(() => {
    if (!allowed) return
    let cancelled = false
    setStatsError('')
    adminDirectoryApi.userStats().then(value => { if (!cancelled) setStats(value) }).catch(error => { if (!cancelled) setStatsError(errorText(error)) })
    adminDirectoryApi.userFilterOptions().then(value => { if (!cancelled) setOptions(value) }).catch(() => { /* filters keep their current value */ })
    adminDirectoryApi.roles().then(value => {
      if (!cancelled) setPlatformRoles(value.filter(role => role.context === 'PLATFORM' && role.assignable && !role.superAdmin))
    }).catch(error => { if (!cancelled) setActionError(errorText(error)) })
    return () => { cancelled = true }
  }, [allowed, reloadKey])

  useEffect(() => {
    if (!allowed) return
    let cancelled = false
    setListLoading(true)
    setListError('')
    adminDirectoryApi.users({ ...query, page, size })
      .then(value => {
        if (cancelled) return
        setResult(value)
        setSelectedRows([])
        setSelectedId(current => (current && value.items.some(item => item.id === current) ? current : null))
      })
      .catch(error => { if (!cancelled) { setResult(null); setListError(errorText(error)) } })
      .finally(() => { if (!cancelled) setListLoading(false) })
    return () => { cancelled = true }
  }, [allowed, query, page, size, reloadKey])

  useEffect(() => {
    if (!allowed || !selectedId) { setDetail(null); setDetailState('idle'); setDetailError(''); return }
    let cancelled = false
    setDetail(null)
    setDetailState('loading')
    setDetailError('')
    adminDirectoryApi.user(selectedId)
      .then(value => { if (!cancelled) { setDetail(value); setDetailState('idle') } })
      .catch(error => { if (!cancelled) { setDetail(null); setDetailError(errorText(error)); setDetailState('error') } })
    return () => { cancelled = true }
  }, [allowed, selectedId])

  useEffect(() => {
    setHistory(null)
    setHistoryError('')
    if (!allowed || !selectedId || tab !== 'ประวัติ' || !canAudit) return
    let cancelled = false
    adminDirectoryApi.userHistory(selectedId)
      .then(value => { if (!cancelled) setHistory(value) })
      .catch(error => { if (!cancelled) setHistoryError(errorText(error)) })
    return () => { cancelled = true }
  }, [allowed, selectedId, tab, canAudit])

  useEffect(() => {
    setRoleChoice(detail?.roles.find(role => role.context === 'PLATFORM')?.code ?? '')
  }, [detail])

  const { showSkeleton, markHasData, isRefetching } = useDelayedLoading(listLoading, 150)
  useEffect(() => {
    if (!listLoading && result) markHasData()
  }, [listLoading, result, markHasData])

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const items = result?.items ?? []
  const totalItems = result?.totalItems ?? 0
  const totalPages = result?.totalPages ?? 0
  const selectedRow = items.find(item => item.id === selectedId) ?? null
  const apply = (next: Partial<Filters>) => { const merged = { ...draft, ...next }; setDraft(merged); setFilters(merged); setPage(0) }
  const search = () => { setFilters({ ...draft }); setPage(0); setReloadKey(key => key + 1) }
  const reset = () => { setDraft(EMPTY_FILTERS); setFilters(EMPTY_FILTERS); setPage(0) }
  const toggle = (id: string) => setSelectedRows(rows => rows.includes(id) ? rows.filter(row => row !== id) : [...rows, id])
  const openCreate = () => {
    setAccountForm({ firstName: '', lastName: '', email: '', phone: '' })
    setActionError('')
    setAccountDialog('create')
  }
  const openEdit = () => {
    if (!detail) return
    setAccountForm({ firstName: detail.firstName, lastName: detail.lastName, email: detail.email, phone: detail.phone })
    setActionError('')
    setAccountDialog('edit')
  }
  const saveAccount = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSaving(true)
    setActionError('')
    try {
      const saved = accountDialog === 'create'
        ? await adminDirectoryApi.createUser(accountForm)
        : detail ? await adminDirectoryApi.updateUser(detail.id, {
          firstName: accountForm.firstName,
          lastName: accountForm.lastName,
          phone: accountForm.phone,
        }) : null
      if (!saved) return
      setDetail(saved)
      setSelectedId(saved.id)
      setAccountDialog(null)
      setReloadKey(key => key + 1)
      notify(accountDialog === 'create' ? 'สร้างบัญชีแล้ว ส่งลิงก์ตั้งรหัสผ่านไปยังอีเมลแล้ว' : 'บันทึกข้อมูลผู้ใช้แล้ว')
    } catch (error) {
      setActionError(errorText(error))
    } finally {
      setSaving(false)
    }
  }
  const setUserStatus = async (action: 'suspend' | 'restore') => {
    if (!detail) return
    setUserConfirm(null)
    setSaving(true)
    setActionError('')
    try {
      const updated = action === 'suspend'
        ? await adminDirectoryApi.suspendUser(detail.id)
        : await adminDirectoryApi.restoreUser(detail.id)
      setDetail(updated)
      setReloadKey(key => key + 1)
      notify(action === 'suspend' ? 'ระงับบัญชีและเพิกถอน session แล้ว' : 'คืนสถานะบัญชีแล้ว')
    } catch (error) {
      setActionError(errorText(error))
    } finally {
      setSaving(false)
    }
  }
  const deleteSelectedUser = async () => {
    if (!detail) return
    setUserConfirm(null)
    setSaving(true)
    setActionError('')
    try {
      await adminDirectoryApi.deleteUser(detail.id)
      setDetail(null)
      setSelectedId(null)
      setReloadKey(key => key + 1)
      notify('ลบบัญชีผู้ใช้แล้ว')
    } catch (error) {
      setActionError(errorText(error))
    } finally {
      setSaving(false)
    }
  }
  const saveRole = async () => {
    if (!detail) return
    setSaving(true)
    setActionError('')
    try {
      const updated = await adminDirectoryApi.assignRole(detail.id, roleChoice || null)
      setDetail(updated)
      setReloadKey(key => key + 1)
      notify(roleChoice ? 'กำหนดบทบาทแล้ว' : 'เพิกถอนบทบาทแพลตฟอร์มแล้ว')
    } catch (error) {
      setActionError(errorText(error))
    } finally {
      setSaving(false)
    }
  }
  const exportCsv = async () => {
    setExporting(true)
    try {
      await adminDirectoryApi.exportUsers(query)
      notify(`นำออกข้อมูลผู้ใช้งาน ${formatNumber(Math.min(totalItems, 10000))} รายการแล้ว`)
    } catch (error) {
      notify(`นำออกไม่สำเร็จ: ${errorText(error)}`)
    } finally {
      setExporting(false)
    }
  }
  const kpi = (metric: AdminUserStats['total'] | undefined) => {
    const change = formatChange(metric)
    return <>{stats ? formatNumber(metric?.value) : statsError ? '-' : '…'} <em className={change.tone}>{stats ? change.text : ''}</em></>
  }
  const head = detail ?? null
  const headName = head ? fullName(head) : selectedRow ? fullName(selectedRow) : ''
  const headStatus = head?.status ?? selectedRow?.status
  const headRole = head?.primaryRole ?? selectedRow?.primaryRole
  const headShops = head ? [...new Set(head.shops.map(shop => shop.shopName))].join(', ') || '-' : selectedRow ? shopsLabel(selectedRow) : ''
  const selectedPlatformRole = detail?.roles.find(role => role.context === 'PLATFORM')?.code ?? ''

  return <main className="admin-shell users-shell">
    <AdminHeader user={user} />
    <AdminSidebar active="users" className="users-sidebar"/>
    <div className="admin-content users-content">
      <section className="users-heading"><div className="users-title"><span><UserRound/></span><div><h1>ผู้ใช้งาน</h1><p>จัดการผู้ใช้งานทั้งหมดในระบบ ThaiShopFun</p><small>“เพิ่ม แก้ไข และกำหนดสิทธิ์ผู้ใช้งาน เพื่อความปลอดภัยและประสิทธิภาพในการทำงาน”</small></div></div>{canCreate && <button className="add-user" onClick={openCreate}><Plus/>เพิ่มผู้ใช้งาน</button>}</section>
      <section className="user-kpis">
        <article className="all"><span><UsersRound/></span><div><b>ผู้ใช้งานทั้งหมด</b><strong>{kpi(stats?.total)}</strong><small>{statsError || 'จากเดือนก่อน'}</small></div></article>
        <article className="admins"><span><UserRound/></span><div><b>ผู้ใช้แอดมิน</b><strong>{kpi(stats?.admins)}</strong><small>คิดเป็น {percentOf(stats?.admins.value, stats?.total.value)}</small></div></article>
        <article className="owners"><span><Store/></span><div><b>เจ้าของร้าน</b><strong>{kpi(stats?.storeOwners)}</strong><small>คิดเป็น {percentOf(stats?.storeOwners.value, stats?.total.value)}</small></div></article>
        <article className="general"><span><UsersRound/></span><div><b>ผู้ใช้งานทั่วไป</b><strong>{kpi(stats?.generalUsers)}</strong><small>คิดเป็น {percentOf(stats?.generalUsers.value, stats?.total.value)}</small></div></article>
      </section>
      {showTip ? (
        <p className="admin-inline-tip" role="note">
          <span>ⓘ คุณสามารถกำหนดบทบาทและสิทธิ์การใช้งานให้กับผู้ใช้งาน เพื่อควบคุมการเข้าถึงข้อมูลในระบบได้อย่างปลอดภัย</span>
          <button type="button" aria-label="ปิดคำแนะนำ" onClick={dismissTip}>×</button>
        </p>
      ) : null}
      <form className="ap-filters user-filters" onSubmit={event => { event.preventDefault(); search() }}>
        <div className="ap-filter-search wide">
          <span className="ap-filter-label">ค้นหา</span>
          <label className="user-search adm-search ap-search"><Search/><input value={draft.q} onChange={event => setDraft({ ...draft, q: event.target.value })} placeholder="ค้นหาผู้ใช้ (ชื่อ-นามสกุล, อีเมล, เบอร์โทร...)" aria-label="ค้นหาผู้ใช้งาน" maxLength={100}/></label>
        </div>
        <AdminSelect label="บทบาท" value={draft.role} onChange={value => apply({ role: value })} options={[{ value: '', label: 'ทั้งหมด' }, ...(options?.roles ?? []).map(option => ({ value: option.value, label: `${option.label} (${formatNumber(option.count)})` })), ...(draft.role && !options?.roles.some(option => option.value === draft.role) ? [{ value: draft.role, label: draft.role }] : [])]}/>
        <AdminSelect label="สถานะ" value={draft.status} onChange={value => apply({ status: value })} options={[{ value: '', label: 'ทั้งหมด' }, ...(options?.statuses ?? []).map(option => ({ value: option.value, label: `${option.label} (${formatNumber(option.count)})` }))]}/>
        <AdminDateRange className="user-date-range" label="วันที่สมัคร" from={draft.from} to={draft.to} onChange={range => apply({ from: range.from, to: range.to })}/>
        <div className="ap-filter-actions">
          <button type="button" className="ap-ghost user-reset" onClick={reset}>รีเซ็ต</button>
          <button type="submit" className="ap-primary user-find">ค้นหา</button>
        </div>
      </form>
      <section className="users-table-card"><header><h2>รายการผู้ใช้งาน ({formatNumber(totalItems)} รายการ)</h2><div><button onClick={exportCsv} disabled={exporting || listLoading || totalItems === 0} title={totalItems > 10000 ? 'นำออกได้สูงสุด 10,000 รายการ' : 'นำออกรายการตามตัวกรองปัจจุบันเป็นไฟล์ CSV'}><Download/>{exporting ? 'กำลังนำออก...' : 'นำออก (Export)'}</button><button disabled title={NOT_ENABLED}><Settings/>จัดการคอลัมน์</button></div></header>
        <div className="users-table-scroll"><div className="users-table">
          <div className="users-tr users-th"><input type="checkbox" aria-label="เลือกทั้งหมด" checked={items.length > 0 && selectedRows.length === items.length} onChange={() => setSelectedRows(selectedRows.length === items.length ? [] : items.map(item => item.id))}/><b>#</b><b>ชื่อ-นามสกุล</b><b>บทบาท</b><b>ร้านค้า</b><b>เบอร์โทร</b><b>วันที่สมัคร</b><b>สถานะ</b><b>การดำเนินการ</b><span/></div>
          {showSkeleton && listLoading && !result?.items.length ? <SkeletonTable rows={8} cols={8} /> : null}
          <SkeletonBusy refetching={isRefetching} busy={isRefetching}>
          {!listError && items.map((item, index) => <div className={`users-tr ${selectedId === item.id ? 'selected' : ''}`} key={item.id} onClick={() => setSelectedId(item.id)}>
            <input type="checkbox" aria-label={`เลือก ${fullName(item)}`} checked={selectedRows.includes(item.id)} onClick={event => event.stopPropagation()} onChange={() => toggle(item.id)}/>
            <span className="num">{page * size + index + 1}</span>
            <div className="user-name"><i className={avatarTone(item)}>{initialOf(item.firstName)}</i><span><b>{fullName(item)}</b><small>{item.email}</small></span></div>
            <span><i className={`user-role ${roleTone(item.primaryRole.context, item.primaryRole.code)}`} title={item.roleCodes.join(', ') || item.primaryRole.code}>{item.primaryRole.nameTh ?? item.primaryRole.code}{item.roleCodes.length > 1 ? ` +${item.roleCodes.length - 1}` : ''}</i></span>
            <span title={item.shops.map(shop => shop.shopName).join(', ')}>{shopsLabel(item)}</span>
            <span>{item.phone}</span>
            <span className="user-registered">{formatDateTime(item.createdAt)}</span>
            <span><i className={`user-status ${item.status === 'ACTIVE' ? 'active' : 'suspended'}`}><small/>{STATUS_LABEL[item.status] ?? item.status}</i></span>
            <button className="user-detail-button" onClick={event => { event.stopPropagation(); setSelectedId(item.id) }}>ดูรายละเอียด</button>
            <button className="user-more" aria-label={`เมนู ${fullName(item)}`} disabled title={NOT_ENABLED} onClick={event => event.stopPropagation()}><MoreVertical/></button>
          </div>)}
          </SkeletonBusy>
          {!listLoading && listError && <LoadError message={listError} onRetry={() => setReloadKey(key => key + 1)} />}
          {!listLoading && !listError && items.length === 0 && <div className="users-empty">{totalItems === 0 && !Object.values(filters).some(Boolean) ? 'ยังไม่มีผู้ใช้งานในระบบ' : 'ไม่พบผู้ใช้งานที่ตรงกับตัวกรอง'}</div>}
        </div></div>
        <Pager
          page={page}
          size={size}
          totalItems={totalItems}
          totalPages={totalPages}
          itemCount={items.length}
          loading={listLoading}
          sizeOptions={PAGE_SIZES}
          onPage={setPage}
          onSize={next => { setSize(next); setPage(0) }}
        />
      </section>
    </div>
    <AdminSidePanel
      variant="drawer"
      className="user-detail application-drawer"
      open={selectedId != null}
      onClose={() => setSelectedId(null)}
      title={headName || 'รายละเอียดผู้ใช้งาน'}
      avatar={<span>{initialOf(head?.firstName ?? selectedRow?.firstName)}</span>}
      avatarClassName={selectedRow ? avatarTone(selectedRow) : 'gray'}
      meta={
        <>
          {headStatus ? (
            <span className={`om-pill app-status-pill ${headStatus === 'ACTIVE' ? 'ok' : 'bad'}`}>
              {headStatus === 'ACTIVE' ? <Check aria-hidden /> : <AlertTriangle aria-hidden />}
              {STATUS_LABEL[headStatus] ?? headStatus}
            </span>
          ) : null}
          {headRole ? <span className="om-muted">{headRole.nameTh ?? headRole.code}</span> : null}
        </>
      }
      tabs={[
        { id: 'ข้อมูลผู้ใช้', label: 'ข้อมูลผู้ใช้', icon: UserRound },
        { id: 'สิทธิ์การใช้งาน', label: 'สิทธิ์การใช้งาน', icon: ShieldCheck },
        { id: 'ประวัติ', label: 'ประวัติ', icon: CalendarDays },
      ]}
      activeTab={tab}
      onTabChange={(id) => setTab(id as Tab)}
      footer={detail ? (
        <>
          <div className="application-drawer-actions__secondary">
            {canUpdate && <button type="button" className="quiet" onClick={openEdit} disabled={saving || detail.superAdmin}><Settings aria-hidden /><span>แก้ไขข้อมูล</span></button>}
            {canDelete && <button type="button" className="danger" onClick={() => setUserConfirm('delete')} disabled={saving || detail.superAdmin || detail.id === user.id}><Trash2 aria-hidden /><span>ลบผู้ใช้งาน</span></button>}
          </div>
          {(headStatus === 'ACTIVE' && canSuspend) || (headStatus === 'SUSPENDED' && canRestore) ? (
            <div className="application-drawer-actions__primary">
              {headStatus === 'ACTIVE' && canSuspend ? (
                <button type="button" className="danger" onClick={() => setUserConfirm('suspend')} disabled={saving || detail.superAdmin || detail.id === user.id}><AlertTriangle aria-hidden /><span>ระงับ</span></button>
              ) : null}
              {headStatus === 'SUSPENDED' && canRestore ? (
                <button type="button" className="primary" onClick={() => setUserConfirm('restore')} disabled={saving || detail.superAdmin || detail.id === user.id}><Check aria-hidden /><span>คืนสถานะ</span></button>
              ) : null}
            </div>
          ) : null}
        </>
      ) : null}
      footerClassName="application-drawer-actions"
    >
      {!selectedId ? <p className="om-empty">เลือกผู้ใช้งานจากตารางเพื่อดูรายละเอียด</p> : <>
        {detailState === 'loading' && !detail ? <SkeletonForm fields={5} />
          : detailState === 'error' ? <p className="ap-error" role="alert">โหลดรายละเอียดไม่สำเร็จ: {detailError}</p>
          : !detail ? null
          : tab === 'ข้อมูลผู้ใช้' ? (
            <>
              {(headRole || headShops !== '-') ? (
                <p className="user-drawer-shops om-muted">
                  {headRole ? <span className={`om-pill ${roleTone(headRole.context, headRole.code)}`}>{headRole.nameTh ?? headRole.code}</span> : null}
                  {headStatus ? <span className={`om-pill ${headStatus === 'ACTIVE' ? 'ok' : 'bad'}`}>{STATUS_LABEL[headStatus] ?? headStatus}</span> : null}
                  {headShops !== '-' ? <span>{headShops}</span> : null}
                </p>
              ) : null}
              {canAssignRole && !detail.superAdmin ? (
                <label className="user-role-assignment">
                  <span>บทบาทแพลตฟอร์ม</span>
                  <AdminSelect compact className="user-role-select" label="บทบาทแพลตฟอร์ม" value={roleChoice} onChange={setRoleChoice} disabled={saving || detail.status !== 'ACTIVE'}
                    options={[{ value: '', label: 'ไม่มีบทบาท' }, ...platformRoles.map(role => ({ value: role.code, label: role.nameTh ?? role.code }))]} />
                </label>
              ) : null}
              <dl className="cm-contact">
                <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><UserRound /></span><dt>ชื่อ-นามสกุล</dt><dd>{fullName(detail)}</dd></div>
                <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><Mail /></span><dt>อีเมล</dt><dd>{detail.email}{detail.emailVerifiedAt ? '' : ' (ยังไม่ยืนยัน)'}</dd></div>
                <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><UserRound /></span><dt>เบอร์โทร</dt><dd>{detail.phone}{detail.phoneVerifiedAt ? '' : ' (ยังไม่ยืนยัน)'}</dd></div>
                <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><CalendarDays /></span><dt>วันที่สมัคร</dt><dd>{formatDateTime(detail.createdAt)}</dd></div>
                <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><ShieldCheck /></span><dt>เข้าสู่ระบบล่าสุด</dt><dd>{detail.lastLoginAt ? formatDateTime(detail.lastLoginAt) : 'ยังไม่เคยเข้าสู่ระบบ'}</dd></div>
                <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><ShieldCheck /></span><dt>บทบาท</dt><dd>{detail.roles.map(role => role.nameTh ?? role.code).join(', ')}{detail.superAdmin ? ' (super admin)' : ''}</dd></div>
                <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><UsersRound /></span><dt>ประเภทบัญชี</dt><dd>{detail.accountRole}</dd></div>
              </dl>
            </>
          )
          : tab === 'สิทธิ์การใช้งาน' ? <div className="detail-documents user-permissions">
              {detail.superAdmin && <p><Check /><span><b>ผู้ดูแลแพลตฟอร์ม (super admin)</b><small>ได้รับทุกสิทธิ์ในทุกร้าน</small></span></p>}
              <p><ShieldCheck /><span><b>สิทธิ์ระดับบัญชี/แพลตฟอร์ม</b><small>{detail.accountPermissions.length} รายการ</small></span></p>
              {detail.accountPermissions.map(permission => <p key={permission.code} title={permission.code}><Check /><span><b>{permission.descriptionTh ?? permission.code}</b><small>{permission.code}</small></span></p>)}
              {detail.shops.map(shop => (
                <div key={shop.shopId}>
                  <p><Store /><span><b>ร้าน {shop.shopName}</b><small>{shop.roleNameTh ?? shop.role} · {shop.permissions.length} สิทธิ์</small></span></p>
                  {shop.permissions.map(permission => <p key={permission.code} title={permission.code}><Check /><span><b>{permission.descriptionTh ?? permission.code}</b><small>{permission.code}</small></span></p>)}
                </div>
              ))}
            </div>
          : <div className="detail-history user-history">
              {!canAudit ? <p><AlertTriangle /><span><b>ไม่มีสิทธิ์ดูประวัติ</b><small>ต้องมี PLATFORM_AUDIT_READ</small></span></p>
                : historyError ? <p><AlertTriangle /><span><b>{historyError}</b></span></p>
                : history === null ? <SkeletonText lines={3} />
                : history.length === 0 ? <p><CalendarDays /><span><b>ยังไม่มีประวัติ</b></span></p>
                : history.map((event, index) => (
                  <p key={`${event.type}-${event.at}-${index}`}>
                    <CalendarDays />
                    <span>
                      <b>{event.title}{event.shopName && !event.title.includes(event.shopName) ? ` · ${event.shopName}` : ''}</b>
                      <small>{formatDateTime(event.at)}{event.actorName && event.actorName !== event.subjectName ? ` · โดย ${event.actorName}` : ''}</small>
                    </span>
                  </p>
                ))}
              {canAudit && selectedId ? (
                <p><a href={`/admin/audit-logs?targetType=USER&targetId=${encodeURIComponent(selectedId)}&kind=BUSINESS`}>{t('adminPlatform.audit.openForTarget')}</a></p>
              ) : null}
            </div>}
        {actionError && <p className="ap-error" role="alert">{actionError}</p>}
      </>}
    </AdminSidePanel>
    {accountDialog && <div className="admin-modal-backdrop" role="presentation">
      <form className="admin-management-dialog" onSubmit={saveAccount} role="dialog" aria-modal="true" aria-labelledby="account-dialog-title">
        <header><h2 id="account-dialog-title">{accountDialog === 'create' ? 'เพิ่มผู้ใช้งาน' : 'แก้ไขข้อมูลผู้ใช้'}</h2><button type="button" onClick={() => setAccountDialog(null)} aria-label="ปิด"><X/></button></header>
        <label>ชื่อ<input required maxLength={100} value={accountForm.firstName} onChange={event => setAccountForm({ ...accountForm, firstName: event.target.value })}/></label>
        <label>นามสกุล<input required maxLength={100} value={accountForm.lastName} onChange={event => setAccountForm({ ...accountForm, lastName: event.target.value })}/></label>
        <label>อีเมล<input required type="email" maxLength={255} value={accountForm.email} disabled={accountDialog === 'edit'} onChange={event => setAccountForm({ ...accountForm, email: event.target.value })}/></label>
        <label>เบอร์โทรศัพท์<input required type="tel" pattern="[0-9+ \-]{9,20}" value={accountForm.phone} onChange={event => setAccountForm({ ...accountForm, phone: event.target.value })}/></label>
        {accountDialog === 'create' && <p>ระบบจะส่งลิงก์ตั้งรหัสผ่านไปยังอีเมลที่ระบุ</p>}
        {actionError && <p className="admin-action-error" role="alert">{actionError}</p>}
        <footer><button type="button" onClick={() => setAccountDialog(null)} disabled={saving}>ยกเลิก</button><button type="submit" disabled={saving}>{saving ? 'กำลังบันทึก...' : 'บันทึก'}</button></footer>
      </form>
    </div>}
    {notice && <div className="admin-toast" role="status">{notice}</div>}
    {userConfirm && detail && (
      <ConfirmDialog
        title={t(userConfirm === 'delete' ? 'confirm.deleteUserTitle' : userConfirm === 'suspend' ? 'confirm.suspendUserTitle' : 'confirm.restoreUserTitle')}
        message={t(userConfirm === 'delete' ? 'confirm.deleteUserBody' : userConfirm === 'suspend' ? 'confirm.suspendUserBody' : 'confirm.restoreUserBody', { name: fullName(detail) })}
        cancelLabel={t('confirm.cancel')}
        confirmLabel={t('confirm.confirm')}
        danger={userConfirm !== 'restore'}
        confirmDisabled={saving}
        onCancel={() => setUserConfirm(null)}
        onConfirm={() => void (userConfirm === 'delete' ? deleteSelectedUser() : setUserStatus(userConfirm))}
      />
    )}
  </main>
}
