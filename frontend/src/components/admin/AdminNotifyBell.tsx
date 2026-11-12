import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, FileCheck2, RotateCcw, Store } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../contexts/AuthContext'
import {
  adminNotificationsApi,
  type AdminNotificationItem,
  type AdminNotificationType,
} from '../../services/api'

const POLL_MS = 45_000

function badgeLabel(count: number) {
  if (count <= 0) return null
  return count > 99 ? '99+' : String(count)
}

function TypeIcon({ type }: { type: string }) {
  switch (type as AdminNotificationType) {
    case 'SHOP_APPLICATION_SUBMITTED':
      return <FileCheck2 aria-hidden />
    case 'SHOP_REINSTATEMENT_REQUESTED':
      return <Store aria-hidden />
    case 'REFUND_DISPUTED':
      return <RotateCcw aria-hidden />
    default:
      return <Bell aria-hidden />
  }
}

function formatWhen(iso: string, locale: string) {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat(locale, {
    timeZone: 'Asia/Bangkok',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function titleFor(item: AdminNotificationItem, t: (key: string) => string) {
  const key = `notifications.types.${item.type}`
  const translated = t(key)
  return translated === key ? t('notifications.types.UNKNOWN') : translated
}

/** Durable admin inbox bell — polls every 45s; opens internal href from the API only. */
export default function AdminNotifyBell() {
  const { t, i18n } = useTranslation('admin')
  const { user } = useAuth()
  const navigate = useNavigate()
  const userId = user?.id ?? ''
  const [open, setOpen] = useState(false)
  const [unread, setUnread] = useState(0)
  const [items, setItems] = useState<AdminNotificationItem[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [markingAll, setMarkingAll] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const requestGen = useRef(0)
  const locale = i18n.language?.startsWith('zh') ? 'zh-CN' : i18n.language?.startsWith('en') ? 'en-GB' : 'th-TH'

  const refreshUnread = useCallback(() => {
    if (!userId || document.hidden) return
    const gen = requestGen.current
    adminNotificationsApi
      .unreadCount()
      .then((data) => {
        if (gen !== requestGen.current) return
        setUnread(data.count)
      })
      .catch(() => {})
  }, [userId])

  const loadPage = useCallback(async (cursor?: string | null, append = false) => {
    if (!userId) return
    const gen = requestGen.current
    if (append) setLoadingMore(true)
    else setLoading(true)
    setError('')
    try {
      const page = await adminNotificationsApi.list({ cursor: cursor || undefined, size: 20 })
      if (gen !== requestGen.current) return
      setItems((prev) => (append ? [...prev, ...page.items] : page.items))
      setNextCursor(page.nextCursor ?? null)
      setUnread(page.unreadCount)
    } catch {
      if (gen !== requestGen.current) return
      setError(t('notifications.loadError'))
      if (!append) setItems([])
    } finally {
      if (gen === requestGen.current) {
        setLoading(false)
        setLoadingMore(false)
      }
    }
  }, [t, userId])

  useEffect(() => {
    requestGen.current += 1
    setOpen(false)
    setUnread(0)
    setItems([])
    setNextCursor(null)
    setError('')
    if (!userId) return
    refreshUnread()
    const timer = window.setInterval(refreshUnread, POLL_MS)
    const onFocus = () => refreshUnread()
    const onVis = () => { if (!document.hidden) refreshUnread() }
    window.addEventListener('focus', onFocus)
    document.addEventListener('visibilitychange', onVis)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', onFocus)
      document.removeEventListener('visibilitychange', onVis)
    }
  }, [userId, refreshUnread])

  useEffect(() => {
    if (!open) return
    void loadPage(null, false)
  }, [open, loadPage])

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false) }
    const onPointer = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onPointer)
    return () => {
      window.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onPointer)
    }
  }, [open])

  const openItem = async (item: AdminNotificationItem) => {
    if (item.unread) {
      setItems((prev) => prev.map((row) => (row.id === item.id ? { ...row, unread: false } : row)))
      setUnread((count) => Math.max(0, count - 1))
      try {
        await adminNotificationsApi.markRead(item.id)
      } catch {
        refreshUnread()
      }
    }
    setOpen(false)
    const href = item.href?.startsWith('/admin') ? item.href : '/admin'
    navigate(href)
  }

  const markAll = async () => {
    if (markingAll || unread <= 0) return
    setMarkingAll(true)
    const previous = unread
    setUnread(0)
    setItems((prev) => prev.map((row) => ({ ...row, unread: false })))
    try {
      await adminNotificationsApi.markAllRead()
    } catch {
      setUnread(previous)
      void loadPage(null, false)
      refreshUnread()
    } finally {
      setMarkingAll(false)
    }
  }

  const badge = badgeLabel(unread)

  return (
    <div className="admin-notify" ref={rootRef}>
      <button
        className="admin-bell"
        type="button"
        aria-label={t('notifications.bell')}
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => setOpen((value) => !value)}
      >
        <Bell />
        {badge && <i>{badge}</i>}
      </button>
      {open && (
        <section className="admin-notify-panel" role="dialog" aria-label={t('notifications.list')}>
          <header>
            <b>{t('notifications.title')}</b>
            <div className="admin-notify-tools">
              <small>{t('notifications.unread', { count: unread })}</small>
              <button type="button" className="admin-notify-read-all" disabled={markingAll || unread <= 0} onClick={() => void markAll()}>
                {t('notifications.markAll')}
              </button>
            </div>
          </header>
          {loading ? (
            <p className="admin-notify-empty">{t('notifications.loading')}</p>
          ) : error ? (
            <div className="admin-notify-empty">
              <p>{error}</p>
              <button type="button" onClick={() => void loadPage(null, false)}>{t('notifications.retry')}</button>
            </div>
          ) : items.length === 0 ? (
            <p className="admin-notify-empty">{t('notifications.empty')}</p>
          ) : (
            <ul>
              {items.map((item) => (
                <li key={item.id} className={item.unread ? 'is-unread' : undefined}>
                  <button type="button" onClick={() => void openItem(item)}>
                    <TypeIcon type={item.type} />
                    <span>
                      <b>{titleFor(item, t)}</b>
                      <small>
                        {item.shopName ? item.shopName : t('notifications.unknownShop')}
                        {item.createdAt ? ` · ${formatWhen(item.createdAt, locale)}` : ''}
                      </small>
                    </span>
                    {item.unread && <em className="admin-notify-dot" aria-hidden />}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {nextCursor && !loading && !error && (
            <footer>
              <button type="button" disabled={loadingMore} onClick={() => void loadPage(nextCursor, true)}>
                {loadingMore ? t('notifications.loading') : t('notifications.loadMore')}
              </button>
            </footer>
          )}
        </section>
      )}
    </div>
  )
}
