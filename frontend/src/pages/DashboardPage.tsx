import { LogOut, Store, UserRound } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../contexts/AuthContext'
import { accountKind } from '../services/permissions'
import LanguageSwitcher from '../components/LanguageSwitcher'
import '../styles/dashboard.css'
import SiteBrand from "../components/SiteBrand";

export default function DashboardPage() {
  const { user, loading, logout } = useAuth()
  const { t } = useTranslation('common')
  if (loading) return <main className="auth-loading">{t('dashboard.checkingAuth')}</main>
  if (!user) { window.location.replace('/login'); return null }
  if (accountKind(user) === 'ADMIN') { window.location.replace('/admin'); return null }
  if (accountKind(user) === 'MERCHANT') { window.location.replace('/merchant/dashboard'); return null }
  if (accountKind(user) === 'CUSTOMER') { window.location.replace('/'); return null }
  const signOut = async () => { await logout(); window.location.replace('/') }
  return <main className="dashboard-page">
    <header><SiteBrand homeAria="ThaiShopFun" href="/" /><div className="dashboard-tools"><LanguageSwitcher /><button className="dashboard-logout" onClick={signOut}><LogOut/>{t('account.logout')}</button></div></header>
    <section className="dashboard-welcome"><span><UserRound/></span><div><p>{t('dashboard.welcome')}</p><h1>{user.firstName} {user.lastName}</h1><small>{user.email} · {user.phone}</small></div></section>
    <section className="dashboard-card"><Store/><div><h2>{t('dashboard.readyTitle')}</h2><p>{t('dashboard.readyBody', { role: accountKind(user) })}</p><a href="/">{t('dashboard.shopLink')}</a></div></section>
  </main>
}
