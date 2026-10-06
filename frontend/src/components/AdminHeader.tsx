import { FormEvent, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, ChevronDown, Search, ShoppingBag, Store } from 'lucide-react'
import { STORE_APPLICATIONS_CHANGED } from './AdminSidebar'
import LanguageSwitcher from './LanguageSwitcher'
import { useLanguage } from '../contexts/LanguageContext'
import { adminApplicationApi, type AdminStoreApplication, type AuthUser } from '../services/api'

type AdminHeaderProps = {
  user: AuthUser
  query?: string
  onSearch?: (value: string) => void
}

function appliedLabel(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(date)
}

export default function AdminHeader({ user, query = '', onSearch }: AdminHeaderProps) {
  const navigate = useNavigate()
  const { t } = useLanguage()
  const [text, setText] = useState(query)
  const [open, setOpen] = useState(false)
  const [pending, setPending] = useState<AdminStoreApplication[]>([])
  const notifyRef = useRef<HTMLDivElement>(null)

  useEffect(() => { setText(query) }, [query])

  useEffect(() => {
    let active = true
    const load = () => {
      adminApplicationApi.list()
        .then(rows => { if (active) setPending(rows.filter(row => row.status === 'PENDING_REVIEW')) })
        .catch(() => { if (active) setPending([]) })
    }
    load()
    window.addEventListener(STORE_APPLICATIONS_CHANGED, load)
    return () => {
      active = false
      window.removeEventListener(STORE_APPLICATIONS_CHANGED, load)
    }
  }, [open])

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false) }
    const onPointer = (event: MouseEvent) => {
      if (!notifyRef.current?.contains(event.target as Node)) setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onPointer)
    return () => {
      window.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onPointer)
    }
  }, [open])

  const search = (event: FormEvent) => {
    event.preventDefault()
    const value = text.trim()
    if (onSearch) {
      onSearch(value)
      return
    }
    navigate(`/admin/store-applications?q=${encodeURIComponent(value)}`)
  }

  const openShop = (shopId: number) => {
    setOpen(false)
    navigate(`/admin/store-applications?shop=${shopId}`)
  }

  return (
    <header className="admin-topbar">
      <a className="admin-brand" href="/admin">
        <span><ShoppingBag /><i>✓</i></span>
        <div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div>
      </a>
      <form onSubmit={search}>
        <button aria-label={t('admin.searchAction')} type="submit"><Search /></button>
        <input aria-label={t('admin.searchLabel')} value={text} onChange={event => setText(event.target.value)} placeholder={t('admin.search')} />
      </form>
      <span className="topbar-spacer" />
      <div className="admin-tools">
      <LanguageSwitcher />
      <div className="admin-notify" ref={notifyRef}>
        <button className="admin-bell" type="button" aria-label={t('admin.notify')} aria-expanded={open} onClick={() => setOpen(value => !value)}>
          <Bell />
          {pending.length > 0 && <i>{pending.length}</i>}
        </button>
        {open && (
          <section className="admin-notify-panel" role="dialog" aria-label={t('admin.notifyList')}>
            <header>
              <b>{t('admin.pendingTitle')}</b>
              <small>{pending.length} {t('admin.shopCount')}</small>
            </header>
            {pending.length === 0 ? <p className="admin-notify-empty">{t('admin.empty')}</p> : (
              <ul>
                {pending.map(shop => (
                  <li key={shop.shopId}>
                    <button type="button" onClick={() => openShop(shop.shopId)}>
                      <Store />
                      <span>
                        <b>{shop.shopName}</b>
                        <small>{t('admin.waiting')} · {shop.ownerName || t('admin.unknownOwner')}{shop.appliedAt ? ` · ${appliedLabel(shop.appliedAt)}` : ''}</small>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </div>
      </div>
      <div className="admin-profile">
        <span>{user.firstName.charAt(0).toUpperCase()}</span>
        <p><b>{user.email}</b><small>Administrator</small></p>
        <ChevronDown />
      </div>
    </header>
  )
}
