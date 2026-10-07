import { useEffect, useMemo, useState, type ComponentType } from 'react'
import { Check, ChevronDown, ChevronLeft, ChevronRight, Crown, Eye, FileCheck2, Headphones, LockKeyhole, MoreVertical, Package, Pencil, Plus, Search, Shield, ShieldCheck, ShoppingBag, Store, UserRound, UsersRound } from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { hasPlatformAccess } from '../services/permissions'
import { adminDirectoryApi, ApiError, type AdminActivityEvent, type AdminRoleDetail, type AdminRoleSummary, type AdminUserPage, type AdminUserStats } from '../services/api'
import { formatChange, formatDateTime, formatNumber, initialOf, NOT_ENABLED } from '../services/adminFormat'
import '../styles/admin-dashboard.css'
import '../styles/admin-roles.css'

type Icon = ComponentType<{ size?: number }>
const PAGE_SIZE = 10
/** Presentation only (icon + colour per role code / context); every value shown comes from the API. */
const ROLE_LOOK: Record<string, [string, Icon]> = {
  PLATFORM_ADMIN: ['crown', Crown], STORE_OWNER: ['owner', Store], STORE_ADMIN: ['manager', ShieldCheck], STORE_MANAGER: ['manager', UserRound],
  STORE_SALES: ['staff', Headphones], STORE_WAREHOUSE: ['finance', Package], STORE_VIEWER: ['moderator', Eye],
  BUYER: ['staff', ShoppingBag], APPLICANT: ['finance', FileCheck2], GUEST: ['moderator', Eye],
}
const lookOf = (role: { code: string; context: string }) => ROLE_LOOK[role.code] ?? (role.context === 'PLATFORM' ? ['crown', Crown] : role.context === 'STORE' ? ['manager', UserRound] : ['moderator', Shield])
const COUNT_HINT: Record<AdminRoleSummary['assignment'], string> = {
  PLATFORM_ROLES: 'นับจาก platform_roles ที่ยังไม่ถูกเพิกถอน',
  SHOP_MEMBERS: 'นับจาก shop_members ที่ใช้งานอยู่ (คนเดียวนับครั้งเดียว)',
  IMPLICIT: 'ทุกบัญชีที่ใช้งานอยู่ได้รับบทบาทนี้โดยปริยาย',
  PUBLIC: 'ผู้เยี่ยมชมที่ไม่ได้เข้าสู่ระบบ — ไม่ผูกกับบัญชี',
}
const errorText = (error: unknown) => error instanceof ApiError ? error.message : 'โหลดข้อมูลไม่สำเร็จ'

export default function AdminRolesPage() {
  const { user, loading } = useAuth()
  const { can } = usePermissions()
  const allowed = !!user && hasPlatformAccess(user)
  const canAudit = can('PLATFORM_AUDIT_READ')
  const [query, setQuery] = useState('')
  const [activeTab, setActiveTab] = useState<'บทบาท' | 'ผู้ใช้งาน'>('บทบาท')
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [roles, setRoles] = useState<AdminRoleSummary[] | null>(null)
  const [rolesError, setRolesError] = useState('')
  const [stats, setStats] = useState<AdminUserStats | null>(null)
  const [statsError, setStatsError] = useState('')
  const [selectedCode, setSelectedCode] = useState<string | null>(null)
  const [detail, setDetail] = useState<AdminRoleDetail | null>(null)
  const [detailError, setDetailError] = useState('')
  const [members, setMembers] = useState<AdminUserPage | null>(null)
  const [membersError, setMembersError] = useState('')
  const [activity, setActivity] = useState<AdminActivityEvent[] | null>(null)
  const [activityError, setActivityError] = useState('')
  const [showAllActivity, setShowAllActivity] = useState(false)

  useEffect(() => {
    if (!allowed) return
    let cancelled = false
    setRolesError('')
    setStatsError('')
    adminDirectoryApi.roles()
      .then(value => { if (cancelled) return; setRoles(value); setSelectedCode(current => current ?? value.find(role => role.superAdmin)?.code ?? value[0]?.code ?? null) })
      .catch(error => { if (!cancelled) setRolesError(errorText(error)) })
    adminDirectoryApi.userStats().then(value => { if (!cancelled) setStats(value) }).catch(error => { if (!cancelled) setStatsError(errorText(error)) })
    return () => { cancelled = true }
  }, [allowed, reloadKey])

  useEffect(() => {
    if (!allowed || !canAudit) return
    let cancelled = false
    adminDirectoryApi.roleActivity(20).then(value => { if (!cancelled) setActivity(value) }).catch(error => { if (!cancelled) setActivityError(errorText(error)) })
    return () => { cancelled = true }
  }, [allowed, canAudit, reloadKey])

  useEffect(() => {
    setDetail(null)
    setDetailError('')
    if (!allowed || !selectedCode) return
    let cancelled = false
    adminDirectoryApi.role(selectedCode).then(value => { if (!cancelled) setDetail(value) }).catch(error => { if (!cancelled) setDetailError(errorText(error)) })
    return () => { cancelled = true }
  }, [allowed, selectedCode])

  const selected = roles?.find(role => role.code === selectedCode) ?? null
  const listable = selected?.assignment === 'PLATFORM_ROLES' || selected?.assignment === 'SHOP_MEMBERS'
  useEffect(() => {
    setMembers(null)
    setMembersError('')
    if (!allowed || activeTab !== 'ผู้ใช้งาน' || !selectedCode || !listable) return
    let cancelled = false
    adminDirectoryApi.users({ role: selectedCode, size: 5, sort: 'name', dir: 'asc' })
      .then(value => { if (!cancelled) setMembers(value) })
      .catch(error => { if (!cancelled) setMembersError(errorText(error)) })
    return () => { cancelled = true }
  }, [allowed, activeTab, selectedCode, listable])

  const filtered = useMemo(() => (roles ?? []).filter(role => `${role.nameTh ?? ''} ${role.code} ${role.descriptionTh ?? ''}`.toLowerCase().includes(query.trim().toLowerCase())), [roles, query])

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages - 1)
  const visible = filtered.slice(currentPage * PAGE_SIZE, currentPage * PAGE_SIZE + PAGE_SIZE)
  const activeRoles = roles?.filter(role => role.active).length
  const totalChange = formatChange(stats?.total)
  const statValue = (value: number | undefined) => stats ? formatNumber(value) : statsError ? '-' : '…'
  const [tone, SelectedIcon] = selected ? lookOf(selected) : ['moderator', Shield] as [string, Icon]
  const shownActivity = activity ? activity.slice(0, showAllActivity ? 20 : 3) : []
  const permissionTotal = detail ? detail.groups.reduce((sum, group) => sum + group.permissions.length, 0) : 0

  return <main className="admin-shell roles-shell"><AdminHeader user={user} />
    <AdminSidebar active="roles" className="roles-sidebar"/>
    <div className="admin-content roles-content">
      <section className="roles-heading"><div className="roles-title"><span><UsersRound/></span><div><h1>บทบาทและสิทธิ์</h1><p>จัดการบทบาทและสิทธิ์การใช้งานในระบบ ThaiShopFun</p><small>“กำหนดบทบาท ควบคุมสิทธิ์การเข้าถึง เพื่อความปลอดภัยของข้อมูล”</small></div></div><button className="add-role" disabled title={`${NOT_ENABLED} — บทบาทมาจาก seed_reference_data.sql`}><Plus/>เพิ่มบทบาทใหม่</button></section>
      <section className="role-kpis">
        <article className="users"><span><UsersRound/></span><div><b>ผู้ใช้งานทั้งหมด</b><strong>{statValue(stats?.total.value)} <em className={totalChange.tone}>{stats ? totalChange.text : ''}</em></strong><small>{statsError || 'จากเดือนก่อน'}</small></div></article>
        <article className="count"><span><Shield/></span><div><b>บทบาททั้งหมด</b><strong>{roles ? formatNumber(roles.length) : rolesError ? '-' : '…'} <em>-</em></strong><small>บทบาทที่ใช้งาน {formatNumber(activeRoles)}</small></div></article>
        <article className="active"><span><Check/></span><div><b>ผู้ใช้งานที่เปิดใช้งาน</b><strong>{statValue(stats?.active)} <em className="flat">-</em></strong><small>ใช้งานได้ปกติ</small></div></article>
        <article className="suspended"><span><LockKeyhole/></span><div><b>ผู้ใช้งานที่ถูกระงับ</b><strong>{statValue(stats?.suspended)} <em className="flat">-</em></strong><small>ไม่สามารถเข้าสู่ระบบได้</small></div></article>
      </section>
      <section className="roles-card"><header><nav><button className={activeTab === 'บทบาท' ? 'active' : ''} onClick={() => setActiveTab('บทบาท')}>บทบาท (Roles)</button><button className={activeTab === 'ผู้ใช้งาน' ? 'active' : ''} onClick={() => setActiveTab('ผู้ใช้งาน')}>ผู้ใช้งาน (Users)</button></nav><label><Search/><input value={query} onChange={event => { setQuery(event.target.value); setPage(0) }} placeholder="ค้นหาบทบาท (เช่น แอดมิน, เจ้าของร้าน...)" aria-label="ค้นหาบทบาท"/></label></header>
        {activeTab === 'บทบาท' ? <>
          <h2>รายการบทบาท ({formatNumber(filtered.length)} รายการ)</h2>
          <div className="roles-table"><div className="roles-tr roles-th"><b>#</b><b>ชื่อบทบาท</b><b>คำอธิบาย</b><b>จำนวนผู้ใช้งาน</b><b>สถานะ</b><b>การดำเนินการ</b><span/></div>
            {visible.map((role, index) => { const [roleTone, RoleIcon] = lookOf(role); return <div className={`roles-tr ${selectedCode === role.code ? 'selected' : ''}`} key={role.code} onClick={() => setSelectedCode(role.code)}>
              <span>{currentPage * PAGE_SIZE + index + 1}</span>
              <div className="role-name"><i className={roleTone}><RoleIcon/></i><span><b>{role.nameTh ?? role.code}</b><small>{role.code}</small></span></div>
              <p><span>{role.descriptionTh ?? '-'}</span></p>
              <strong title={COUNT_HINT[role.assignment]}>{formatNumber(role.userCount)}</strong>
              <span><i className={`role-status ${role.active ? 'on' : 'off'}`} title={role.superAdmin ? 'super admin: ได้รับทุกสิทธิ์' : `สิทธิ์ที่ผูกไว้ ${role.permissionCount} รายการ`}><small/>{role.active ? 'ใช้งาน' : 'ไม่ใช้งาน'}</i></span>
              <button className="role-manage" onClick={event => { event.stopPropagation(); setSelectedCode(role.code) }}>จัดการ</button>
              <button className="role-more" aria-label={`เมนู ${role.nameTh ?? role.code}`} disabled title={NOT_ENABLED} onClick={event => event.stopPropagation()}><MoreVertical/></button>
            </div> })}
            {!roles && !rolesError && <div className="roles-empty">กำลังโหลดบทบาท...</div>}
            {rolesError && <div className="roles-empty">{rolesError} <button type="button" onClick={() => setReloadKey(key => key + 1)}>ลองใหม่</button></div>}
            {roles && filtered.length === 0 && <div className="roles-empty">{roles.length === 0 ? 'ยังไม่มีบทบาทในตาราง auth_roles' : 'ไม่พบบทบาทที่ตรงกับคำค้นหา'}</div>}
          </div>
          <footer><div>แสดง <select aria-label="จำนวนรายการต่อหน้า" value={PAGE_SIZE} disabled><option>{PAGE_SIZE}</option></select> รายการต่อหน้า</div><span>{filtered.length === 0 ? 0 : currentPage * PAGE_SIZE + 1} - {currentPage * PAGE_SIZE + visible.length} จาก {formatNumber(filtered.length)} รายการ</span><nav><button disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)} aria-label="หน้าก่อนหน้า"><ChevronLeft/></button><button className="current">{currentPage + 1}</button><button disabled={currentPage >= totalPages - 1} onClick={() => setPage(currentPage + 1)} aria-label="หน้าถัดไป"><ChevronRight/></button></nav></footer>
        </> : <div className="role-users-empty"><UsersRound/>
          <h2>{selected ? `ผู้ใช้งานในบทบาท “${selected.nameTh ?? selected.code}” (${formatNumber(selected.userCount)})` : 'จัดการผู้ใช้งานตามบทบาท'}</h2>
          {!selected ? <p>เลือกบทบาทเพื่อดูรายชื่อผู้ใช้งาน</p>
            : !listable ? <p>{COUNT_HINT[selected.assignment]}</p>
            : membersError ? <p>{membersError}</p>
            : !members ? <p>กำลังโหลด...</p>
            : members.items.length === 0 ? <p>ยังไม่มีผู้ใช้งานในบทบาทนี้</p>
            : <ul className="role-user-list">{members.items.map(item => <li key={item.id}><b>{`${item.firstName} ${item.lastName}`.trim()}</b><small>{item.email}{item.shops.length ? ` · ${[...new Set(item.shops.map(shop => shop.shopName))].join(', ')}` : ''}</small></li>)}{members.totalItems > members.items.length && <li><small>และอีก {formatNumber(members.totalItems - members.items.length)} คน</small></li>}</ul>}
          <button onClick={() => window.location.assign(listable && selected ? `/admin/users?role=${encodeURIComponent(selected.code)}` : '/admin/users')}>เปิดหน้าผู้ใช้งาน</button>
        </div>}
      </section>
      <section className="security-tip"><i><ShieldCheck/></i><div><b>เคล็ดลับความปลอดภัย</b><span>ควรกำหนดสิทธิ์ตามหน้าที่ที่จำเป็น และตรวจสอบบัญชีผู้ใช้งานเป็นประจำ</span></div><button disabled title={NOT_ENABLED}>ดูแนวทางความปลอดภัย →</button></section>
    </div>
    <aside className="role-detail"><header><h2>รายละเอียดบทบาท</h2><div><button disabled title={`${NOT_ENABLED} — สิทธิ์ของบทบาทถูกสร้างจาก seed_reference_data.sql การแก้ที่นี่จะถูกเขียนทับเมื่อรัน seed ใหม่`}><Pencil/>แก้ไข</button><ChevronDown/></div></header>
      {!selected ? <section className="role-detail-summary"><div><small>{rolesError || 'เลือกบทบาทเพื่อดูรายละเอียด'}</small></div></section> : <>
        <section className="role-detail-summary"><i className={tone}><SelectedIcon/></i><div><b>{selected.nameTh ?? selected.code}</b><small>{selected.code} · {selected.context}{selected.assignable ? '' : ' · กำหนดโดยระบบ'}</small></div><span className={`role-status ${selected.active ? 'on' : 'off'}`}><small/>{selected.active ? 'ใช้งาน' : 'ไม่ใช้งาน'}</span></section>
        <h3>สิทธิ์การใช้งาน{detail ? ` (${formatNumber(selected.superAdmin ? detail.catalogPermissionCount : permissionTotal)})` : ''}</h3>
        <ul>
          {detailError && <li>{detailError}</li>}
          {!detail && !detailError && <li>กำลังโหลด...</li>}
          {detail && selected.superAdmin && <li className="role-perm-all"><Check/>ได้รับทุกสิทธิ์ในระบบ ({formatNumber(detail.catalogPermissionCount)} รายการ) ทั้งปัจจุบันและที่เพิ่มในอนาคต — super admin</li>}
          {detail && !selected.superAdmin && permissionTotal === 0 && <li>ยังไม่มีสิทธิ์ที่ผูกกับบทบาทนี้</li>}
          {detail?.groups.map(group => [<li className="role-perm-group" key={`g-${group.module}`}>{group.module}{selected.superAdmin ? ' (ผูกไว้ใน seed)' : ''}</li>, ...group.permissions.map(permission => <li key={permission.code} title={permission.code}><Check/>{permission.descriptionTh ?? permission.code}{permission.active ? '' : ' (เลิกใช้)'}</li>)])}
        </ul>
      </>}
    </aside>
    <aside className={`role-history ${showAllActivity ? 'expanded' : ''}`}><header><h2>ประวัติการดำเนินการล่าสุด</h2><button onClick={() => setShowAllActivity(value => !value)} disabled={!activity || activity.length <= 3}>{showAllActivity ? 'ย่อ ↑' : 'ดูทั้งหมด →'}</button></header>
      {!canAudit ? <p className="role-history-empty">ไม่มีสิทธิ์ดูประวัติ (ต้องมี PLATFORM_AUDIT_READ)</p>
        : activityError ? <p className="role-history-empty">{activityError}</p>
        : !activity ? <p className="role-history-empty">กำลังโหลด...</p>
        : activity.length === 0 ? <p className="role-history-empty">ยังไม่มีประวัติการดำเนินการ</p>
        : shownActivity.map((event, index) => <div key={`${event.type}-${event.at}-${index}`}><i>{initialOf(event.subjectName)}</i><p><b>{event.subjectName ?? '-'}</b><span>{event.title}{event.actorName && event.actorName !== event.subjectName ? ` · โดย ${event.actorName}` : ''}</span></p><small>{formatDateTime(event.at)}</small></div>)}
    </aside>
  </main>
}
