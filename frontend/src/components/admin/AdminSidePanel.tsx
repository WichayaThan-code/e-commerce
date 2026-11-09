import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import '../../styles/admin-side-panel.css'
import AdminDetailDrawer, { type AdminDetailDrawerTab } from './AdminDetailDrawer'

export type AdminSidePanelTab = AdminDetailDrawerTab

type Props = {
  variant: 'drawer' | 'aside'
  title?: string
  open?: boolean
  onClose?: () => void
  tabs?: AdminSidePanelTab[]
  activeTab?: string
  onTabChange?: (id: string) => void
  headerExtra?: ReactNode
  meta?: ReactNode
  footer?: ReactNode
  footerClassName?: string
  children: ReactNode
  className?: string
  /** aside without chrome — just layout slot for stacked cards */
  bare?: boolean
  avatar?: ReactNode
  avatarFallback?: ReactNode
  avatarClassName?: string
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
  meta,
  footer,
  footerClassName,
  children,
  className = '',
  bare = false,
  avatar,
  avatarFallback,
  avatarClassName,
}: Props) {
  const { t } = useTranslation('admin')

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

  if (!onClose) return null

  return (
    <AdminDetailDrawer
      open={open}
      onClose={onClose}
      title={title || ''}
      meta={meta}
      headerActions={headerExtra}
      tabs={tabs}
      activeTab={activeTab}
      onTabChange={onTabChange}
      footer={footer}
      footerClassName={footerClassName}
      className={className}
      avatar={avatar}
      avatarFallback={avatarFallback}
      avatarClassName={avatarClassName}
      closeLabel={t('adminPlatform.common.close', { defaultValue: 'ปิด' })}
      wide
    >
      {children}
    </AdminDetailDrawer>
  )
}
