import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'

/** Paths an unverified signed-in user may still open (public + auth/verify). */
function isAllowedWhileUnverified(pathname: string): boolean {
  if (
    pathname === '/'
    || pathname === '/verify-email'
    || pathname === '/login'
    || pathname === '/signup'
    || pathname === '/forgot-password'
    || pathname === '/reset-password'
    || pathname === '/logout'
    || pathname === '/help'
    // FUTURE: ดีลพิเศษ — เปิดเมื่อพร้อมใช้งาน
    // || pathname === '/deals'
    || pathname === '/favorites'
    || pathname === '/recommendations'
    || pathname === '/best-sellers'
    || pathname === '/thai-brands'
    || pathname === '/articles'
    || pathname === '/cart'
    || pathname === '/track-order'
    || pathname === '/shops'
    || pathname === '/search'
  ) return true
  if (pathname.startsWith('/shop/')) return true
  return false
}

/**
 * When the signed-in user has emailVerified === false, send them to /verify-email
 * unless they are on a public/auth page.
 */
export default function EmailVerifiedGate() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (!loading && user && user.emailVerified === false && !isAllowedWhileUnverified(location.pathname)) {
    const next = `${location.pathname}${location.search}`
    return <Navigate to={`/verify-email?next=${encodeURIComponent(next)}`} replace />
  }

  return <Outlet />
}
