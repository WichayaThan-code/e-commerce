import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, Search, ShieldCheck, Trash2, UsersRound } from 'lucide-react'
import ConfirmDialog from '../components/form/ConfirmDialog'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { hasPlatformAccess } from '../services/permissions'
import {
  adminDirectoryApi, ApiError,
  type AdminRoleSummary, type AdminShopOption, type AdminShopRoleMember, type AdminUserPage,
} from '../services/api'
import '../styles/admin-dashboard.css'
import '../styles/admin-role-assignments.css'

const errorText = (error: unknown) => error instanceof ApiError ? error.message : 'โหลดข้อมูลไม่สำเร็จ'
const fullName = (member: Pick<AdminShopRoleMember, 'firstName' | 'lastName'>) => `${member.firstName} ${member.lastName}`.trim()

export default function AdminShopRoleAssignmentsPage() {
  const { t } = useTranslation('admin')
  const { user, loading } = useAuth()
  const { can } = usePermissions()
  const allowed = !!user && hasPlatformAccess(user)
  const canAssign = can('PLATFORM_ACCOUNT_ROLE_ASSIGN')
  const [shops, setShops] = useState<AdminShopOption[]>([])
  const [roles, setRoles] = useState<AdminRoleSummary[]>([])
  const [users, setUsers] = useState<AdminUserPage | null>(null)
  const [members, setMembers] = useState<AdminShopRoleMember[]>([])
  const [shopId, setShopId] = useState('')
  const [userQuery, setUserQuery] = useState('')
  const [userId, setUserId] = useState('')
  const [roleCode, setRoleCode] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const [memberConfirm, setMemberConfirm] = useState<AdminShopRoleMember | null>(null)

  const storeRoles = useMemo(() => roles.filter(role =>
    role.context === 'STORE' && role.assignable && role.active && role.code !== 'STORE_OWNER'), [roles])
  const selectedShopId = Number(shopId)

  useEffect(() => {
    if (!allowed) return
    let cancelled = false
    Promise.all([adminDirectoryApi.shopsForRoleAssignment(), adminDirectoryApi.roles()])
      .then(([shopRows, roleRows]) => {
        if (cancelled) return
        setShops(shopRows)
        setRoles(roleRows)
        setShopId(current => current || String(shopRows[0]?.shopId ?? ''))
        setRoleCode(current => current || roleRows.find(role => role.context === 'STORE' && role.assignable && role.active && role.code !== 'STORE_OWNER')?.code || '')
      })
      .catch(reason => { if (!cancelled) setError(errorText(reason)) })
    return () => { cancelled = true }
  }, [allowed, reload])

  useEffect(() => {
    if (!allowed) return
    let cancelled = false
    const timer = window.setTimeout(() => {
      adminDirectoryApi.users({ q: userQuery.trim(), status: 'ACTIVE', size: 100, sort: 'name', dir: 'asc' })
        .then(result => { if (!cancelled) setUsers(result) })
        .catch(reason => { if (!cancelled) setError(errorText(reason)) })
    }, 250)
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [allowed, userQuery, reload])

  useEffect(() => {
    if (!allowed || !selectedShopId) { setMembers([]); return }
    let cancelled = false
    adminDirectoryApi.shopRoleMembers(selectedShopId)
      .then(rows => { if (!cancelled) setMembers(rows) })
      .catch(reason => { if (!cancelled) setError(errorText(reason)) })
    return () => { cancelled = true }
  }, [allowed, selectedShopId, reload])

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const assign = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!canAssign || !selectedShopId || !userId || !roleCode) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const saved = await adminDirectoryApi.assignShopRole(selectedShopId, userId, roleCode)
      setNotice(`บันทึกบทบาท ${saved.roleNameTh ?? saved.role} ให้ ${fullName(saved)} แล้ว`)
      setUserId('')
      setReload(value => value + 1)
    } catch (reason) {
      setError(errorText(reason))
    } finally {
      setBusy(false)
    }
  }

  const selectMember = (member: AdminShopRoleMember) => {
    const userInResults = users?.items.some(candidate => candidate.id === member.userId)
    if (!userInResults) setUserQuery(member.email)
    setUserId(member.userId)
    setRoleCode(member.role)
  }

  const removeMember = async (member: AdminShopRoleMember) => {
    if (!canAssign) return
    setMemberConfirm(null)
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await adminDirectoryApi.removeShopRole(selectedShopId, member.userId)
      setNotice(`นำ ${fullName(member)} ออกจากร้านแล้ว`)
      setReload(value => value + 1)
    } catch (reason) {
      setError(errorText(reason))
    } finally {
      setBusy(false)
    }
  }

  return <main className="admin-shell role-assignments-shell">
    <AdminHeader user={user}/>
    <AdminSidebar active="role-assignments" className="role-assignments-sidebar"/>
    <div className="admin-content role-assignments-content">
      <header className="role-assignments-heading">
        <div><span><UsersRound/></span><section><h1>ผูกผู้ใช้กับ role ร้านค้า</h1><p>กำหนดบทบาทให้ผู้ใช้ภายในร้านที่เลือก สิทธิ์จะมีผลเฉพาะร้านนั้น</p></section></div>
        <a href="/admin/roles">จัดการบทบาทและสิทธิ์</a>
      </header>

      {error && <p className="role-assignment-alert" role="alert">{error}</p>}
      {notice && <p className="role-assignment-notice" role="status"><Check/>{notice}</p>}

      <section className="role-assignment-card">
        <h2>กำหนดบทบาทให้ผู้ใช้</h2>
        <form onSubmit={assign}>
          <label>ร้านค้า
            <select value={shopId} onChange={event => { setShopId(event.target.value); setUserId('') }} required>
              <option value="">เลือกร้านค้า</option>
              {shops.map(shop => <option key={shop.shopId} value={shop.shopId}>{shop.shopName} · #{shop.shopId} · {shop.status}</option>)}
            </select>
          </label>
          <label>ค้นหาผู้ใช้
            <span className="role-assignment-search"><Search/><input value={userQuery} onChange={event => setUserQuery(event.target.value)} placeholder="ชื่อ อีเมล หรือเบอร์โทร"/></span>
          </label>
          <label>ผู้ใช้
            <select value={userId} onChange={event => setUserId(event.target.value)} required>
              <option value="">เลือกผู้ใช้</option>
              {users?.items.map(candidate => <option key={candidate.id} value={candidate.id}>{`${candidate.firstName} ${candidate.lastName}`.trim()} · {candidate.email}</option>)}
            </select>
          </label>
          <label>role ร้านค้า
            <select value={roleCode} onChange={event => setRoleCode(event.target.value)} required>
              <option value="">เลือก role</option>
              {storeRoles.map(role => <option key={role.code} value={role.code}>{role.nameTh ?? role.code} · {role.code}</option>)}
            </select>
          </label>
          <button type="submit" disabled={busy || !canAssign || !shops.length || !storeRoles.length || !users?.items.length}>
            <ShieldCheck/>{busy ? 'กำลังบันทึก...' : 'บันทึก role'}
          </button>
        </form>
        {!canAssign && <p className="role-assignment-hint">บัญชีนี้ไม่มีสิทธิ์กำหนด role ให้ผู้ใช้</p>}
        {!shops.length && <p className="role-assignment-hint">ไม่พบร้านค้าที่เปิดใช้งานหรือถูกระงับ</p>}
        {!storeRoles.length && <p className="role-assignment-hint">ยังไม่มี role ร้านค้าที่เปิดใช้งาน กรุณาสร้าง role ในหน้าบทบาทและสิทธิ์</p>}
        {users?.items.length === 0 && <p className="role-assignment-hint">ไม่พบผู้ใช้ที่ใช้งานได้จากคำค้นหานี้</p>}
      </section>

      <section className="role-assignment-card">
        <h2>สมาชิกในร้าน {shops.find(shop => shop.shopId === selectedShopId)?.shopName ?? ''}</h2>
        <div className="role-assignment-table">
          <div className="role-assignment-row role-assignment-table-head"><b>ผู้ใช้</b><b>Role</b><b>สถานะ</b><b>การดำเนินการ</b></div>
          {members.map(member => <div className="role-assignment-row" key={member.userId}>
            <span><b>{fullName(member)}</b><small>{member.email}</small></span>
            <span>{member.roleNameTh ?? member.role}<small>{member.role}</small></span>
            <span className={member.active ? 'member-active' : 'member-inactive'}>{member.active ? 'ใช้งาน' : 'นำออกแล้ว'}</span>
            <span className="role-assignment-actions">
              {member.role !== 'STORE_OWNER' && <button type="button" disabled={busy || !canAssign} onClick={() => selectMember(member)}>{member.active ? 'เปลี่ยน role' : 'กำหนดใหม่'}</button>}
              {member.active && member.role !== 'STORE_OWNER' && <button type="button" className="remove-member" disabled={busy || !canAssign} onClick={() => setMemberConfirm(member)} aria-label={`นำ ${fullName(member)} ออกจากร้าน`}><Trash2/></button>}
              {member.role === 'STORE_OWNER' && <small>เจ้าของร้าน</small>}
            </span>
          </div>)}
          {members.length === 0 && <p className="role-assignment-empty">ยังไม่มีสมาชิกในร้านนี้</p>}
        </div>
      </section>
    </div>
    {memberConfirm && (
      <ConfirmDialog
        title={t('confirm.removeMemberTitle')}
        message={t('confirm.removeMemberBody', { name: fullName(memberConfirm) })}
        cancelLabel={t('confirm.cancel')}
        confirmLabel={t('confirm.confirm')}
        danger
        confirmDisabled={busy}
        onCancel={() => setMemberConfirm(null)}
        onConfirm={() => void removeMember(memberConfirm)}
      />
    )}
  </main>
}
