import { LogOut, ShoppingBag, Store, UserRound } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import '../styles/dashboard.css'

export default function DashboardPage() {
  const {user,loading,logout} = useAuth()
  if (loading) return <main className="auth-loading">กำลังตรวจสอบการเข้าสู่ระบบ...</main>
  if (!user) { window.location.replace('/login'); return null }
  if (user.role==='ADMIN') { window.location.replace('/admin'); return null }
  if (user.role==='MERCHANT') { window.location.replace('/merchant/dashboard'); return null }
  if (user.role==='CUSTOMER') { window.location.replace('/'); return null }
  const signOut = async () => { await logout(); window.location.replace('/') }
  return <main className="dashboard-page">
    <header><a className="brand" href="/"><span className="brand-mark"><ShoppingBag/><i>✓</i></span><span><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></span></a><button onClick={signOut}><LogOut/>ออกจากระบบ</button></header>
    <section className="dashboard-welcome"><span><UserRound/></span><div><p>ยินดีต้อนรับ</p><h1>{user.firstName} {user.lastName}</h1><small>{user.email} · {user.phone}</small></div></section>
    <section className="dashboard-card"><Store/><div><h2>บัญชีของคุณพร้อมใช้งานแล้ว</h2><p>คุณเข้าสู่ระบบด้วยสิทธิ์ {user.role}. ขั้นตอนถัดไปสามารถเลือกซื้อสินค้า หรือสมัครเปิดร้านค้าได้</p><a href="/">กลับไปเลือกซื้อสินค้า</a></div></section>
  </main>
}
