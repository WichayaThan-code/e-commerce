import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { thaiTimeGreeting } from '../../utils/dashboardGreeting'
import '../../styles/dashboard-welcome.css'

export type DashboardWelcomeAction = {
  label: string
  count: number
  to: string
  tone?: 'warn' | 'danger' | 'info' | 'neutral'
}

type Props = {
  greetingName: string
  contextNode?: ReactNode
  actions?: DashboardWelcomeAction[]
  loading?: boolean
  /** Optional clock override for tests / demos (Bangkok rules still apply). */
  now?: Date
}

export default function DashboardWelcome({
  greetingName,
  contextNode,
  actions = [],
  loading = false,
  now,
}: Props) {
  const visible = actions.filter((item) => item.count > 0).slice(0, 3)
  const greeting = `${thaiTimeGreeting(now)}, ${greetingName}`

  return (
    <div className="dash-welcome" aria-busy={loading || undefined}>
      <h1 className="dash-welcome-title">{greeting}</h1>
      {contextNode ? <div className="dash-welcome-context">{contextNode}</div> : null}
      {loading ? (
        <div className="dash-welcome-skel-chips" aria-hidden="true">
          <span className="dash-welcome-skel-chip" />
          <span className="dash-welcome-skel-chip" />
          <span className="dash-welcome-skel-chip" />
        </div>
      ) : (
        <div className="dash-welcome-actions">
          {visible.length === 0 ? (
            <span className="dash-welcome-clear">✓ ไม่มีงานค้าง</span>
          ) : (
            visible.map((item) => (
              <Link
                key={item.to + item.label}
                to={item.to}
                className={`dash-welcome-chip ${item.tone ?? 'neutral'}`}
              >
                {item.label}
              </Link>
            ))
          )}
        </div>
      )}
    </div>
  )
}
