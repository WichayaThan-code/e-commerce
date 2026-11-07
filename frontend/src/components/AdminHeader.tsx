import { FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronDown, Search } from 'lucide-react'
import BrandLogo from './BrandLogo'
import AdminNotifyBell from './admin/AdminNotifyBell'
import LanguageSwitcher from './LanguageSwitcher'
import { useLanguage } from '../contexts/LanguageContext'
import type { AuthUser } from '../services/api'
import { primaryRoleLabelTh } from '../utils/roleLabels'

type AdminHeaderProps = {
  user: AuthUser
  query?: string
  onSearch?: (value: string) => void
}

export default function AdminHeader({ user, query = '', onSearch }: AdminHeaderProps) {
  const navigate = useNavigate()
  const { t } = useLanguage()
  const [text, setText] = useState(query)
  const displayName = `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.email
  const initial = (user.firstName || user.email || 'A').charAt(0).toUpperCase()
  const roleLabel = primaryRoleLabelTh(user)

  useEffect(() => { setText(query) }, [query])

  const search = (event: FormEvent) => {
    event.preventDefault()
    const value = text.trim()
    if (onSearch) {
      onSearch(value)
      return
    }
    navigate(`/admin/store-applications?q=${encodeURIComponent(value)}`)
  }

  return (
    <header className="admin-topbar">
      <a className="admin-brand" href="/" aria-label="ThaiShopFun">
        <BrandLogo variant="withName" className="admin-brand-logo" alt="" />
      </a>
      <form onSubmit={search}>
        <button aria-label={t('admin.searchAction')} type="submit"><Search /></button>
        <input aria-label={t('admin.searchLabel')} value={text} onChange={event => setText(event.target.value)} placeholder={t('admin.search')} />
      </form>
      <div className="admin-topbar-actions">
        <LanguageSwitcher />
        <AdminNotifyBell />
        <div className="admin-user-chip" title={user.email}>
          <span className="admin-user-avatar" aria-hidden="true">{initial}</span>
          <span className="admin-user-copy">
            <strong>{displayName}</strong>
            <em>{roleLabel}</em>
          </span>
          <ChevronDown className="admin-user-caret" size={16} aria-hidden="true" />
        </div>
      </div>
    </header>
  )
}
