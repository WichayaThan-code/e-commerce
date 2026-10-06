import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import type { PermissionScope } from '../services/permissions'

type PermissionRouteProps = {
  permission: string
  scope?: PermissionScope
  children: ReactNode
}

/**
 * Route guard driven by the permission matrix. Signed-out users and the loading state fall through to the page,
 * which keeps its existing login redirect. Permitted users see the page unchanged.
 */
export default function PermissionRoute({ permission, scope = 'platform', children }: PermissionRouteProps) {
  const { user, loading } = useAuth()
  const { can, canInShop } = usePermissions()
  if (loading || !user) return <>{children}</>
  const allowed = scope === 'shop' ? canInShop(permission) : can(permission)
  return allowed ? <>{children}</> : <AccessDenied />
}

export function AccessDenied() {
  return (
    <main className="merchant-auth-gate">
      <section role="alert">
        <h1>ไม่มีสิทธิ์เข้าถึงหน้านี้</h1>
        <p>บัญชีของคุณยังไม่ได้รับสิทธิ์ใช้งานส่วนนี้ หากคิดว่าเป็นความผิดพลาด กรุณาติดต่อเจ้าของร้านหรือผู้ดูแลระบบ</p>
        <div>
          <Link className="primary" to="/">กลับหน้าแรก</Link>
        </div>
      </section>
    </main>
  )
}
