import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  AlertTriangle, CalendarDays, Check, ChevronLeft, ChevronRight, Download, KeyRound, Mail,
  MoreVertical, Plus, Search, Settings, ShieldCheck, Store, Trash2, UserRound, UsersRound, X,
} from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { hasPlatformAccess } from '../services/permissions'
import {
  adminDirectoryApi, ApiError,
  type AdminActivityEvent, type AdminRoleSummary, type AdminUserDetail, type AdminUserFilterOptions, type AdminUserPage, type AdminUserQuery, type AdminUserRow, type AdminUserStats,
} from '../services/api'
import { formatChange, formatDate, formatDateTime, formatNumber, initialOf, NOT_ENABLED, percentOf } from '../services/adminFormat'
import '../styles/admin-dashboard.css'
import '../styles/admin-users.css'

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

function pageWindow(current: number, total: number): (number | '…')[] {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index)
  const pages = new Set([0, total - 1, current - 1, current, current + 1].filter(page => page >= 0 && page < total))
  const sorted = [...pages].sort((a, b) => a - b)
  const result: (number | '…')[] = []
  sorted.forEach((page, index) => { if (index > 0 && page - sorted[index - 1] > 1) result.push('…'); result.push(page) })
  return result
}

export default function AdminUsersPage() {
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
  const [dateOpen, setDateOpen] = useState(false)
  const [showTip, setShowTip] = useState(true)
  const [exporting, setExporting] = useState(false)
  const [notice, setNotice] = useState('')
  const [accountDialog, setAccountDialog] = useState<'create' | 'edit' | null>(null)
  const [accountForm, setAccountForm] = useState({ firstName: '', lastName: '', email: '', phone: '' })
  const [platformRoles, setPlatformRoles] = useState<AdminRoleSummary[]>([])
  const [roleChoice, setRoleChoice] = useState('')
  const [saving, setSaving] = useState(false)
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
        setSelectedId(current => current && value.items.some(item => item.id === current) ? current : value.items[0]?.id ?? current)
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

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const items = result?.items ?? []
  const totalItems = result?.totalItems ?? 0
  const totalPages = result?.totalPages ?? 0
  const selectedRow = items.find(item => item.id === selectedId) ?? null
  const apply = (next: Partial<Filters>) => { const merged = { ...draft, ...next }; setDraft(merged); setFilters(merged); setPage(0) }
  const search = () => { setFilters({ ...draft }); setPage(0); setReloadKey(key => key + 1) }
  const reset = () => { setDraft(EMPTY_FILTERS); setFilters(EMPTY_FILTERS); setPage(0); setDateOpen(false) }
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
    const message = action === 'suspend' ? 'ระงับบัญชีนี้และออกจากระบบทุกอุปกรณ์หรือไม่?' : 'คืนสถานะบัญชีนี้หรือไม่?'
    if (!window.confirm(message)) return
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
    if (!detail || !window.confirm(`ลบบัญชี ${fullName(detail)} หรือไม่? การลบเป็นแบบเก็บประวัติและไม่สามารถยกเลิกได้`)) return
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
  const rangeLabel = draft.from || draft.to ? `${formatDate(draft.from) || '…'} - ${formatDate(draft.to) || '…'}` : 'เลือกช่วงวันที่'
  const start = totalItems === 0 ? 0 : page * size + 1
  const end = Math.min(totalItems, page * size + items.length)
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
      <form className="user-filters" onSubmit={event => { event.preventDefault(); search() }}>
        <label className="user-search"><Search/><input value={draft.q} onChange={event => setDraft({ ...draft, q: event.target.value })} placeholder="ค้นหาผู้ใช้ (ชื่อ-นามสกุล, อีเมล, เบอร์โทร...)" aria-label="ค้นหาผู้ใช้งาน" maxLength={100}/></label>
        <label><span>บทบาท</span><select value={draft.role} onChange={event => apply({ role: event.target.value })}><option value="">ทั้งหมด</option>{options?.roles.map(option => <option key={option.value} value={option.value}>{option.label} ({formatNumber(option.count)})</option>)}{draft.role && !options?.roles.some(option => option.value === draft.role) && <option value={draft.role}>{draft.role}</option>}</select></label>
        <label><span>สถานะ</span><select value={draft.status} onChange={event => apply({ status: event.target.value })}><option value="">ทั้งหมด</option>{options?.statuses.map(option => <option key={option.value} value={option.value}>{option.label} ({formatNumber(option.count)})</option>)}</select></label>
        <div className="user-date"><span>วันที่สมัคร</span><button type="button" onClick={() => setDateOpen(open => !open)} aria-expanded={dateOpen}><CalendarDays/>{rangeLabel}</button>{dateOpen && <div className="user-date-pop">
          <label>ตั้งแต่<input type="date" value={draft.from} max={draft.to || undefined} onChange={event => apply({ from: event.target.value })}/></label>
          <label>ถึง<input type="date" value={draft.to} min={draft.from || undefined} onChange={event => apply({ to: event.target.value })}/></label>
          <div><button type="button" onClick={() => apply({ from: '', to: '' })}>ล้าง</button><button type="button" onClick={() => setDateOpen(false)}>ตกลง</button></div>
        </div>}</div>
        <button type="submit" className="user-find">ค้นหา</button><button type="button" className="user-reset" onClick={reset}>รีเซ็ต</button>
      </form>
      <section className="users-table-card"><header><h2>รายการผู้ใช้งาน ({formatNumber(totalItems)} รายการ)</h2><div><button onClick={exportCsv} disabled={exporting || listLoading || totalItems === 0} title={totalItems > 10000 ? 'นำออกได้สูงสุด 10,000 รายการ' : 'นำออกรายการตามตัวกรองปัจจุบันเป็นไฟล์ CSV'}><Download/>{exporting ? 'กำลังนำออก...' : 'นำออก (Export)'}</button><button disabled title={NOT_ENABLED}><Settings/>จัดการคอลัมน์</button></div></header>
        <div className="users-table-scroll"><div className="users-table">
          <div className="users-tr users-th"><input type="checkbox" aria-label="เลือกทั้งหมด" checked={items.length > 0 && selectedRows.length === items.length} onChange={() => setSelectedRows(selectedRows.length === items.length ? [] : items.map(item => item.id))}/><b>#</b><b>ชื่อ-นามสกุล</b><b>บทบาท</b><b>ร้านค้า</b><b>เบอร์โทร</b><b>วันที่สมัคร</b><b>สถานะ</b><b>การดำเนินการ</b><span/></div>
          {!listLoading && !listError && items.map((item, index) => <div className={`users-tr ${selectedId === item.id ? 'selected' : ''}`} key={item.id} onClick={() => setSelectedId(item.id)}>
            <input type="checkbox" aria-label={`เลือก ${fullName(item)}`} checked={selectedRows.includes(item.id)} onClick={event => event.stopPropagation()} onChange={() => toggle(item.id)}/>
            <span>{page * size + index + 1}</span>
            <div className="user-name"><i className={avatarTone(item)}>{initialOf(item.firstName)}</i><span><b>{fullName(item)}</b><small>{item.email}</small></span></div>
            <span><i className={`user-role ${roleTone(item.primaryRole.context, item.primaryRole.code)}`} title={item.roleCodes.join(', ') || item.primaryRole.code}>{item.primaryRole.nameTh ?? item.primaryRole.code}{item.roleCodes.length > 1 ? ` +${item.roleCodes.length - 1}` : ''}</i></span>
            <span title={item.shops.map(shop => shop.shopName).join(', ')}>{shopsLabel(item)}</span>
            <span>{item.phone}</span>
            <span className="user-registered">{formatDateTime(item.createdAt)}</span>
            <span><i className={`user-status ${item.status === 'ACTIVE' ? 'active' : 'suspended'}`}><small/>{STATUS_LABEL[item.status] ?? item.status}</i></span>
            <button className="user-detail-button" onClick={event => { event.stopPropagation(); setSelectedId(item.id) }}>ดูรายละเอียด</button>
            <button className="user-more" aria-label={`เมนู ${fullName(item)}`} disabled title={NOT_ENABLED} onClick={event => event.stopPropagation()}><MoreVertical/></button>
          </div>)}
          {listLoading && <div className="users-empty">กำลังโหลดรายชื่อผู้ใช้งาน...</div>}
          {!listLoading && listError && <div className="users-empty"><span>{listError} <button type="button" className="user-retry" onClick={() => setReloadKey(key => key + 1)}>ลองใหม่</button></span></div>}
          {!listLoading && !listError && items.length === 0 && <div className="users-empty">{totalItems === 0 && !Object.values(filters).some(Boolean) ? 'ยังไม่มีผู้ใช้งานในระบบ' : 'ไม่พบผู้ใช้งานที่ตรงกับตัวกรอง'}</div>}
        </div></div>
        <footer><div>แสดง <select aria-label="จำนวนรายการต่อหน้า" value={size} onChange={event => { setSize(Number(event.target.value)); setPage(0) }}>{PAGE_SIZES.map(value => <option key={value} value={value}>{value}</option>)}</select> รายการต่อหน้า</div><span>{formatNumber(start)} - {formatNumber(end)} จาก {formatNumber(totalItems)} รายการ</span>
          <nav aria-label="แบ่งหน้า"><button disabled={page === 0 || listLoading} onClick={() => setPage(page - 1)} aria-label="หน้าก่อนหน้า"><ChevronLeft/></button>{pageWindow(page, totalPages).map((value, index) => value === '…' ? <button key={`gap-${index}`} disabled>…</button> : <button key={value} className={value === page ? 'current' : ''} onClick={() => setPage(value)} disabled={listLoading}>{value + 1}</button>)}<button disabled={page >= totalPages - 1 || listLoading} onClick={() => setPage(page + 1)} aria-label="หน้าถัดไป"><ChevronRight/></button></nav>
        </footer>
      </section>
    </div>
    <aside className="user-detail"><header><h2>รายละเอียดผู้ใช้งาน</h2><button aria-label="ปิดรายละเอียด" onClick={() => setSelectedId(null)}><X/></button></header>
      {!selectedId ? <div className="user-detail-body user-detail-empty">เลือกผู้ใช้งานจากตารางเพื่อดูรายละเอียด</div> : <>
        <section className="user-summary"><i className={selectedRow ? avatarTone(selectedRow) : 'gray'}>{initialOf(head?.firstName ?? selectedRow?.firstName)}</i><div><b>{headName || '…'}</b>{headStatus && <span className={`user-status ${headStatus === 'ACTIVE' ? 'active' : 'suspended'}`}><small/>{STATUS_LABEL[headStatus] ?? headStatus}</span>}{headRole && <em className={`user-role ${roleTone(headRole.context, headRole.code)}`}>{headRole.nameTh ?? headRole.code}</em>}<p>{headShops}</p></div></section>
        <nav className="user-tabs">{(['ข้อมูลผู้ใช้', 'สิทธิ์การใช้งาน', 'ประวัติ'] as const).map(value => <button className={tab === value ? 'active' : ''} key={value} onClick={() => setTab(value)}>{value}</button>)}</nav>
        {detailState === 'loading' && !detail ? <div className="user-detail-body">กำลังโหลด...</div>
          : detailState === 'error' ? <div className="user-detail-body" role="alert">โหลดรายละเอียดไม่สำเร็จ: {detailError}</div>
          : !detail ? <div className="user-detail-body"/>
          : tab === 'ข้อมูลผู้ใช้' ? <div className="user-detail-body"><dl>
              <div><dt><UserRound/>ชื่อ-นามสกุล</dt><dd>{fullName(detail)}</dd></div>
              <div><dt><Mail/>อีเมล</dt><dd>{detail.email}{detail.emailVerifiedAt ? '' : ' (ยังไม่ยืนยัน)'}</dd></div>
              <div><dt><UserRound/>เบอร์โทร</dt><dd>{detail.phone}{detail.phoneVerifiedAt ? '' : ' (ยังไม่ยืนยัน)'}</dd></div>
              <div><dt><CalendarDays/>วันที่สมัคร</dt><dd>{formatDateTime(detail.createdAt)}</dd></div>
              <div><dt><ShieldCheck/>เข้าสู่ระบบล่าสุด</dt><dd>{detail.lastLoginAt ? formatDateTime(detail.lastLoginAt) : 'ยังไม่เคยเข้าสู่ระบบ'}</dd></div>
              <div><dt><ShieldCheck/>บทบาท</dt><dd>{detail.roles.map(role => role.nameTh ?? role.code).join(', ')}{detail.superAdmin ? ' (super admin)' : ''}</dd></div>
              <div><dt><UserRound/>ประเภทบัญชี</dt><dd>{detail.accountRole}</dd></div>
            </dl></div>
          : tab === 'สิทธิ์การใช้งาน' ? <div className="user-permissions">
              {detail.superAdmin && <p><Check/>ผู้ดูแลแพลตฟอร์ม (super admin) — ได้รับทุกสิทธิ์ในทุกร้าน</p>}
              <h4>สิทธิ์ระดับบัญชี/แพลตฟอร์ม ({detail.accountPermissions.length})</h4>
              {detail.accountPermissions.map(permission => <p key={permission.code} title={permission.code}><Check/>{permission.descriptionTh ?? permission.code}</p>)}
              {detail.shops.map(shop => <div key={shop.shopId}><h4>ร้าน {shop.shopName} · {shop.roleNameTh ?? shop.role} ({shop.permissions.length})</h4>{shop.permissions.map(permission => <p key={permission.code} title={permission.code}><Check/>{permission.descriptionTh ?? permission.code}</p>)}</div>)}
            </div>
          : <div className="user-history">
              {!canAudit ? <p><span>ไม่มีสิทธิ์ดูประวัติ (ต้องมี PLATFORM_AUDIT_READ)</span></p>
                : historyError ? <p><span>{historyError}</span></p>
                : history === null ? <p><span>กำลังโหลด...</span></p>
                : history.length === 0 ? <p><span>ยังไม่มีประวัติ</span></p>
                : history.map((event, index) => <p key={`${event.type}-${event.at}-${index}`}><span>{event.title}{event.shopName && !event.title.includes(event.shopName) ? ` · ${event.shopName}` : ''}</span><small>{formatDateTime(event.at)}{event.actorName && event.actorName !== event.subjectName ? ` · โดย ${event.actorName}` : ''}</small></p>)}
            </div>}
        {actionError && <p className="admin-action-error" role="alert">{actionError}</p>}
        {detail && <footer className="user-actions">
          {canUpdate && <button onClick={openEdit} disabled={saving || detail.superAdmin}><Settings/>แก้ไขข้อมูล</button>}
          {canAssignRole && !detail.superAdmin && <label className="user-role-assignment">บทบาทแพลตฟอร์ม
            <select value={roleChoice} onChange={event => setRoleChoice(event.target.value)} disabled={saving || detail.status !== 'ACTIVE'}>
              <option value="">ไม่มีบทบาท</option>
              {platformRoles.map(role => <option key={role.code} value={role.code}>{role.nameTh ?? role.code}</option>)}
            </select>
            <button onClick={saveRole} disabled={saving || detail.status !== 'ACTIVE' || roleChoice === selectedPlatformRole}>บันทึกบทบาท</button>
          </label>}
          {headStatus === 'ACTIVE' && canSuspend && <button className="suspend" onClick={() => setUserStatus('suspend')} disabled={saving || detail.superAdmin || detail.id === user.id}><AlertTriangle/>ระงับการใช้งาน</button>}
          {headStatus === 'SUSPENDED' && canRestore && <button onClick={() => setUserStatus('restore')} disabled={saving || detail.superAdmin || detail.id === user.id}><Check/>คืนสถานะ</button>}
          {canDelete && <button className="delete" onClick={deleteSelectedUser} disabled={saving || detail.superAdmin || detail.id === user.id}><Trash2/>ลบผู้ใช้งาน</button>}
        </footer>}
      </>}
    </aside>
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
    {showTip && <aside className="user-tip"><button aria-label="ปิดคำแนะนำ" onClick={() => setShowTip(false)}>×</button><h2>💡 คำแนะนำ</h2><p>คุณสามารถกำหนดบทบาทและสิทธิ์การใช้งานให้กับผู้ใช้งาน เพื่อควบคุมการเข้าถึงข้อมูลในระบบได้อย่างปลอดภัย</p></aside>}
    {notice && <div className="admin-toast" role="status">{notice}</div>}
  </main>
}
