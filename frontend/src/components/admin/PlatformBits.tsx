import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronFirst, ChevronLast, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { ApiError, type PlatformPage } from '../../services/api'
import '../../styles/admin-platform.css'
import '../../styles/admin-form-controls.css'
import '../../styles/list-pagination.css'
import SearchableSelect from '../SearchableSelect'

export const PAGE_SIZES = [10, 20, 50, 100]

export type ListParams = Record<string, string | number>

/**
 * Paged list loader for the platform pages. `applied` holds the filters that were submitted (empty values are dropped);
 * callers reset the page themselves when they apply new filters. totalItems in the result is the server's count.
 */
export function usePlatformList<T>(
  load: (params: ListParams) => Promise<PlatformPage<T>>,
  applied: Record<string, string | number | undefined>,
  options: { enabled?: boolean; errorText: string; initialSize?: number },
) {
  const { enabled = true, errorText, initialSize = 20 } = options
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(initialSize)
  const [reloadKey, setReloadKey] = useState(0)
  const [result, setResult] = useState<PlatformPage<T> | null>(null)
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')
  const loadRef = useRef(load)
  loadRef.current = load
  const errorTextRef = useRef(errorText)
  errorTextRef.current = errorText
  const appliedKey = JSON.stringify(applied)

  useEffect(() => {
    if (!enabled) { setLoading(false); return }
    let cancelled = false
    const params: ListParams = { page, size }
    for (const [key, value] of Object.entries(JSON.parse(appliedKey) as Record<string, string | number | undefined>)) {
      if (value === undefined || value === '') continue
      params[key] = value
    }
    setLoading(true)
    setError('')
    loadRef.current(params)
      .then(value => { if (!cancelled) setResult(value) })
      .catch(reason => { if (!cancelled) { setResult(null); setError(errorMessage(reason, errorTextRef.current)) } })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [enabled, appliedKey, page, size, reloadKey])

  return { page, setPage, size, setSize, result, loading, error, reload: () => setReloadKey(key => key + 1) }
}

export function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError && error.message ? error.message : fallback
}

/** Server count shown only as a KPI: it is whatever the API returned, never extrapolated. */
export function formatCount(value: number | null | undefined) {
  return value === null || value === undefined ? '—' : value.toLocaleString('en-US')
}

type PagerProps = {
  /** 0-based page index */
  page: number
  size: number
  totalItems: number
  totalPages: number
  itemCount: number
  loading?: boolean
  sizeOptions?: number[]
  /** When false, omit the page-size control (page already lacks one). */
  showSize?: boolean
  sizeDisabled?: boolean
  className?: string
  onPage: (page: number) => void
  onSize: (size: number) => void
}

/** Admin list pagination — visual match for /merchant/orders MerchantPagination. */
export function Pager({
  page,
  size,
  totalItems,
  totalPages,
  itemCount,
  loading = false,
  sizeOptions = PAGE_SIZES,
  showSize = true,
  sizeDisabled = false,
  className,
  onPage,
  onSize,
}: PagerProps) {
  const { t } = useTranslation('admin')
  const pages = Math.max(1, totalPages || 1)
  const start = totalItems === 0 ? 0 : page * size + 1
  const end = totalItems === 0 ? 0 : Math.min(totalItems, page * size + itemCount)
  const windowSize = Math.min(5, pages)
  const windowStart = Math.max(0, Math.min(page - 2, pages - windowSize))
  const options = sizeOptions.includes(size) ? sizeOptions : [...sizeOptions, size].sort((a, b) => a - b)

  return (
    <footer className={`pm-pagination ap-pager${className ? ` ${className}` : ''}`}>
      <span>{t('adminPlatform.common.showing', { start: formatCount(start), end: formatCount(end), total: formatCount(totalItems) })}</span>
      {showSize ? (
        <div className="pm-page-size">
          <span>{t('adminPlatform.common.perPage')}</span>
          <SearchableSelect
            compact
            disabled={sizeDisabled || loading}
            label={t('adminPlatform.common.perPage')}
            options={options.map(String)}
            value={String(size)}
            onChange={(picked) => {
              const next = Number(picked)
              if (options.includes(next)) onSize(next)
            }}
          />
        </div>
      ) : null}
      <nav aria-label={t('adminPlatform.common.pagination')}>
        <button type="button" aria-label={t('adminPlatform.common.first')} disabled={page <= 0 || loading} onClick={() => onPage(0)}>
          <ChevronFirst />
        </button>
        <button type="button" aria-label={t('adminPlatform.common.prev')} disabled={page <= 0 || loading} onClick={() => onPage(Math.max(0, page - 1))}>
          <ChevronLeft />
        </button>
        {Array.from({ length: windowSize }, (_, index) => {
          const number = windowStart + index
          return (
            <button
              key={number}
              type="button"
              className={number === page ? 'active' : ''}
              aria-current={number === page ? 'page' : undefined}
              disabled={loading}
              onClick={() => onPage(number)}
            >
              {number + 1}
            </button>
          )
        })}
        <button type="button" aria-label={t('adminPlatform.common.next')} disabled={page + 1 >= pages || loading} onClick={() => onPage(Math.min(pages - 1, page + 1))}>
          <ChevronRight />
        </button>
        <button type="button" aria-label={t('adminPlatform.common.last')} disabled={page + 1 >= pages || loading} onClick={() => onPage(pages - 1)}>
          <ChevronLast />
        </button>
      </nav>
    </footer>
  )
}

type DrawerProps = {
  title: string
  subtitle?: ReactNode
  onClose: () => void
  children: ReactNode
  wide?: boolean
  /** Optional sticky footer (outside the scroll body). Omit when there are no actions. */
  footer?: ReactNode
  className?: string
}

/** Right-hand detail drawer. Escape closes it unless a confirm dialog is open on top (that one handles Escape itself). */
export function Drawer({ title, subtitle, onClose, children, wide = false, footer, className }: DrawerProps) {
  const { t } = useTranslation('admin')
  const closeRef = useRef(onClose)
  const panelRef = useRef<HTMLElement>(null)
  const closeBtnRef = useRef<HTMLButtonElement>(null)
  const previousFocus = useRef<HTMLElement | null>(null)
  closeRef.current = onClose

  useEffect(() => {
    previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeBtnRef.current?.focus()
    return () => {
      document.body.style.overflow = previousOverflow
      previousFocus.current?.focus?.()
    }
  }, [])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !document.querySelector('.pm-dialog')) {
        closeRef.current()
        return
      }
      if (event.key !== 'Tab' || !panelRef.current) return
      const focusable = panelRef.current.querySelectorAll<HTMLElement>(
        'a[href],button:not([disabled]),textarea:not([disabled]),input:not([disabled]),select:not([disabled]),[tabindex]:not([tabindex="-1"])',
      )
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className="ap-drawer-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget && !document.querySelector('.pm-dialog')) onClose() }}>
      <aside
        ref={panelRef}
        className={`ap-drawer${wide ? ' wide' : ''}${className ? ` ${className}` : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header>
          <div>
            <h2 id="ap-drawer-title">{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <button ref={closeBtnRef} type="button" onClick={onClose} aria-label={t('adminPlatform.common.close')}><X /></button>
        </header>
        <div className="ap-drawer-body">{children}</div>
        {footer ? <div className="ap-drawer-footer">{footer}</div> : null}
      </aside>
    </div>
  )
}

export function formatBytes(bytes: number | null | undefined) {
  if (bytes === null || bytes === undefined) return '—'
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit += 1 }
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[unit]}`
}

export function Pill({ tone, children }: { tone: 'ok' | 'warn' | 'bad' | 'info' | 'muted'; children: ReactNode }) {
  return <i className={`ap-pill ${tone}`}>{children}</i>
}

/** Colour of a status badge from its code. */
export function statusTone(code: string | null | undefined): 'ok' | 'warn' | 'bad' | 'info' | 'muted' {
  const value = (code ?? '').toUpperCase()
  if (['ACTIVE', 'APPROVED', 'PAID', 'COMPLETED', 'DELIVERED', 'SUCCESS', 'SUCCEEDED', 'CONFIRMED', 'FULFILLED', 'SUCCEED'].includes(value)) return 'ok'
  if (['SUSPENDED', 'CANCELLED', 'REJECTED', 'FAILED', 'DENIED', 'FAILURE', 'DELIVERY_FAILED', 'REFUNDED', 'DISPUTED', 'RETURNED'].includes(value)) return 'bad'
  if (['PENDING', 'PENDING_PAYMENT', 'PENDING_REVIEW', 'UNPAID', 'REFUND_REQUESTED', 'REQUESTED', 'PARTIALLY_REFUNDED', 'PARTIAL', 'PAID_AWAITING_MERCHANT_CONFIRM', 'PROOF_UPLOADED'].includes(value)) return 'warn'
  if (value) return 'info'
  return 'muted'
}
