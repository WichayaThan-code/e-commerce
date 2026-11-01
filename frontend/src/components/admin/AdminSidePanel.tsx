import { useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import '../../styles/admin-side-panel.css'

export type AdminSidePanelTab = { id: string; label: string }

type Props = {
  variant: 'drawer' | 'aside'
  title?: string
  open?: boolean
  onClose?: () => void
  tabs?: AdminSidePanelTab[]
  activeTab?: string
  onTabChange?: (id: string) => void
  headerExtra?: ReactNode
  footer?: ReactNode
  children: ReactNode
  className?: string
  /** aside without chrome — just layout slot for stacked cards */
  bare?: boolean
}

export default function AdminSidePanel({
  variant,
  title,
  open = true,
  onClose,
  tabs,
  activeTab,
  onTabChange,
  headerExtra,
  footer,
  children,
  className = '',
  bare = false,
}: Props) {
  useEffect(() => {
    if (variant !== 'drawer' || !open || !onClose) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [variant, open, onClose])

  if (variant === 'aside') {
    if (bare) {
      return (
        <aside className={`admin-side-panel admin-side-panel--aside admin-side-panel--bare ${className}`.trim()}>
          {children}
        </aside>
      )
    }
    return (
      <aside className={`admin-side-panel admin-side-panel--aside ${className}`.trim()}>
        {title ? (
          <header className="admin-side-panel__header">
            <h2>{title}</h2>
            {headerExtra}
          </header>
        ) : null}
        {tabs && tabs.length > 0 ? (
          <nav className="admin-side-panel__tabs" aria-label={title}>
            {tabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                className={activeTab === tab.id ? 'active' : ''}
                onClick={() => onTabChange?.(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        ) : null}
        <div className="admin-side-panel__body">{children}</div>
        {footer ? <footer className="admin-side-panel__footer">{footer}</footer> : null}
      </aside>
    )
  }

  if (!open) return null

  const drawer = (
    <div
      className="admin-side-panel-overlay"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose?.()
      }}
    >
      <aside
        className={`admin-side-panel admin-side-panel--drawer ${className}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header className="admin-side-panel__header">
          <h2>{title}</h2>
          <div className="admin-side-panel__header-actions">
            {headerExtra}
            {onClose ? (
              <button type="button" className="admin-side-panel__close" aria-label="ปิดแผง" onClick={onClose}>
                <X />
              </button>
            ) : null}
          </div>
        </header>
        {tabs && tabs.length > 0 ? (
          <nav className="admin-side-panel__tabs" aria-label={title}>
            {tabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                className={activeTab === tab.id ? 'active' : ''}
                onClick={() => onTabChange?.(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        ) : null}
        <div className="admin-side-panel__body">{children}</div>
        {footer ? <footer className="admin-side-panel__footer">{footer}</footer> : null}
      </aside>
    </div>
  )

  return createPortal(drawer, document.body)
}
