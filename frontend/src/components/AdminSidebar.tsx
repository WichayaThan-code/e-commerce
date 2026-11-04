import { useEffect, useState } from 'react'
import {
  Activity, BarChart3, Bell, Building2, CircleDollarSign, ClipboardList, FileCheck2, FileText,
  Gauge, Headphones, Home, LogOut, Megaphone, Newspaper, ReceiptText, Settings, ShoppingBag, Store, Tags,
  TrendingUp, UserCog, UsersRound, WalletCards,
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { adminApplicationApi } from '../services/api'

export const STORE_APPLICATIONS_CHANGED = 'store-applications-changed'

export function refreshAdminStoreApplications() {
  window.dispatchEvent(new Event(STORE_APPLICATIONS_CHANGED))
}

export type AdminSection = 'home' | 'stores' | 'applications' | 'categories' | 'help-center' | 'articles' | 'promotions' | 'users' | 'roles' | 'role-assignments' | 'orders' | 'audit' | 'merchants' | 'usage' | 'system-status'

const links: { key: AdminSection; label: string; href: string; icon: typeof Store }[] = [
  { key: 'stores', label: 'ร้านค้า', href: '/admin/stores', icon: Store },
  { key: 'merchants', label: 'ผู้ขายและแพ็กเกจ', href: '/admin/merchants', icon: Building2 },
  { key: 'usage', label: 'การใช้งาน (Usage)', href: '/admin/usage', icon: Gauge },
  { key: 'orders', label: 'คำสั่งซื้อทั้งหมด', href: '/admin/orders', icon: ShoppingBag },
  { key: 'applications', label: 'ใบสมัครร้านค้า', href: '/admin/store-applications', icon: FileCheck2 },
  { key: 'categories', label: 'หมวดหมู่สินค้า', href: '/admin/categories', icon: Tags },
  { key: 'help-center', label: 'ศูนย์ช่วยเหลือ', href: '/admin/help-center', icon: Headphones },
  { key: 'articles', label: 'บทความ', href: '/admin/articles', icon: Newspaper },
  { key: 'promotions', label: 'โปรโมชั่น/แคมเปญ', href: '/admin/promotions', icon: Megaphone },
  { key: 'users', label: 'ผู้ใช้งาน', href: '/admin/users', icon: UsersRound },
  { key: 'roles', label: 'บทบาทและสิทธิ์', href: '/admin/roles', icon: UserCog },
  { key: 'role-assignments', label: 'ผูกผู้ใช้กับ role', href: '/admin/role-assignments', icon: UserCog },
  { key: 'audit', label: 'บันทึกการใช้งาน', href: '/admin/audit-logs', icon: FileCheck2 },
  { key: 'system-status', label: 'สถานะระบบ', href: '/admin/system-status', icon: Activity },
]

const groups: { label: string; keys: AdminSection[] }[] = [
  { label: 'จัดการร้านค้า', keys: ['stores', 'merchants', 'usage', 'orders', 'applications', 'categories', 'help-center', 'articles', 'promotions'] },
  { label: 'จัดการผู้ใช้', keys: ['users', 'roles', 'role-assignments'] },
]

const placeholders = [
  { label: 'การเงิน', items: [[CircleDollarSign, 'รายได้/ค่าธรรมเนียม'], [WalletCards, 'การชำระเงิน'], [ReceiptText, 'ใบแจ้งหนี้']] },
  { label: 'รายงาน', items: [[BarChart3, 'รายงานภาพรวม'], [TrendingUp, 'รายงานการขาย'], [FileText, 'รายงานการเงิน']] },
] as const

type AdminSidebarProps = {
  active: AdminSection
  className?: string
  showHelp?: boolean
  extended?: boolean
}

export default function AdminSidebar({ active, className = '', showHelp = false, extended = false }: AdminSidebarProps) {
  const { logout } = useAuth()
  const { can, canVisit } = usePermissions()
  const canReadApplications = can('PLATFORM_APPLICATION_READ')
  const [pending, setPending] = useState(0)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (!canReadApplications) return
    let activeRequest = true
    const load = () => {
      adminApplicationApi.list()
        .then(rows => { if (activeRequest) setPending(rows.filter(row => row.status === 'PENDING_REVIEW').length) })
        .catch(() => { if (activeRequest) setPending(0) })
    }
    load()
    const timer = window.setInterval(load, 15000)
    window.addEventListener(STORE_APPLICATIONS_CHANGED, load)
    window.addEventListener('focus', load)
    return () => {
      activeRequest = false
      window.clearInterval(timer)
      window.removeEventListener(STORE_APPLICATIONS_CHANGED, load)
      window.removeEventListener('focus', load)
    }
  }, [canReadApplications])

  const unavailable = (label: string) => {
    setNotice(`${label}: อยู่ระหว่างเชื่อมข้อมูล`)
    window.setTimeout(() => setNotice(''), 1900)
  }
  const signOut = async () => {
    await logout()
    window.location.replace('/')
  }
  const item = (key: AdminSection) => links.find(link => link.key === key)!

  return (
    <aside className={`admin-sidebar ${className}`.trim()}>
      {canVisit('/admin/dashboard') && <a className={active === 'home' ? 'active' : ''} href="/admin/dashboard"><Home />หน้าแรก</a>}
      {groups.filter(group => group.keys.some(key => canVisit(item(key).href))).map(group => (
        <section key={group.label}>
          <h3>{group.label}</h3>
          {group.keys.filter(key => canVisit(item(key).href)).map(key => {
            const link = item(key)
            const Icon = link.icon
            return (
              <a className={active === key ? 'active' : ''} href={link.href} key={key}>
                <Icon />{link.label}
                {key === 'applications' && <i>{pending}</i>}
              </a>
            )
          })}
        </section>
      ))}
      {placeholders.map(group => (
        <section key={group.label}>
          <h3>{group.label}</h3>
          {group.items.map(([Icon, label]) => (
            <button key={label} type="button" onClick={() => unavailable(label)}><Icon />{label}</button>
          ))}
        </section>
      ))}
      <section>
        <h3>ระบบ</h3>
        <button type="button" onClick={() => unavailable('ตั้งค่า')}><Settings />ตั้งค่า</button>
        {extended && <>
          <button type="button" onClick={() => unavailable('จัดการเนื้อหา')}><ClipboardList />จัดการเนื้อหา</button>
          <button type="button" onClick={() => unavailable('แจ้งเตือน')}><Bell />แจ้งเตือน</button>
        </>}
        {canVisit(item('audit').href) && (
          <a className={active === 'audit' ? 'active' : ''} href={item('audit').href}><FileCheck2 />{item('audit').label}</a>
        )}
        {can('PLATFORM_SYSTEM_READ') && (
          <a className={active === 'system-status' ? 'active' : ''} href={item('system-status').href}><Activity />{item('system-status').label}</a>
        )}
      </section>
      {showHelp && <a className="admin-help" href="/help"><Headphones />ศูนย์ช่วยเหลือ</a>}
      <button className="admin-logout" type="button" onClick={signOut}><LogOut />ออกจากระบบ</button>
      {notice && <div className="admin-toast" role="status">{notice}</div>}
    </aside>
  )
}
