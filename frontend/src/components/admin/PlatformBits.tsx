import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight, X } from 'lucide-react'
import { ApiError, type PlatformPage } from '../../services/api'
import '../../styles/admin-platform.css'
import '../../styles/admin-form-controls.css'
import AdminSelect from './AdminSelect'

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

function pageWindow(current: number, total: number): (number | '…')[] {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index)
  const pages = new Set([0, total - 1, current - 1, current, current + 1].filter(page => page >= 0 && page < total))
  const sorted = [...pages].sort((a, b) => a - b)
  const result: (number | '…')[] = []
  sorted.forEach((page, index) => {
    if (index > 0 && page - sorted[index - 1] > 1) result.push('…')
    result.push(page)
  })
  return result
}

type PagerProps = {
  page: number
  size: number
  totalItems: number
  totalPages: number
  itemCount: number
  loading?: boolean
  onPage: (page: number) => void
  onSize: (size: number) => void
}

export function Pager({ page, size, totalItems, totalPages, itemCount, loading = false, onPage, onSize }: PagerProps) {
  const { t } = useTranslation('admin')
  const start = totalItems === 0 ? 0 : page * size + 1
  const end = page * size + itemCount
  return (
    <footer className="ap-pager">
      <div>
        <span>{t('adminPlatform.common.perPage')}</span>
        <AdminSelect compact className="adm-page-size" label={t('adminPlatform.common.perPage')} value={String(size)} onChange={value => onSize(Number(value))}
          options={PAGE_SIZES.map(value => ({ value: String(value), label: String(value) }))} />
      </div>
      <span className="num">{t('adminPlatform.common.range', { start: formatCount(start), end: formatCount(end), total: formatCount(totalItems) })}</span>
      <nav aria-label={t('adminPlatform.common.pagination')}>
        <button type="button" disabled={page <= 0 || loading} onClick={() => onPage(page - 1)} aria-label={t('adminPlatform.common.prev')}><ChevronLeft /></button>
        {pageWindow(page, totalPages).map((value, index) => value === '…'
          ? <button type="button" key={`gap-${index}`} disabled>…</button>
          : <button type="button" key={value} className={value === page ? 'current' : ''} disabled={loading} onClick={() => onPage(value)}>{value + 1}</button>)}
        <button type="button" disabled={page >= totalPages - 1 || loading} onClick={() => onPage(page + 1)} aria-label={t('adminPlatform.common.next')}><ChevronRight /></button>
      </nav>
    </footer>
  )
}

type DrawerProps = { title: string; subtitle?: ReactNode; onClose: () => void; children: ReactNode; wide?: boolean }

/** Right-hand detail drawer. Escape closes it unless a confirm dialog is open on top (that one handles Escape itself). */
export function Drawer({ title, subtitle, onClose, children, wide = false }: DrawerProps) {
  const { t } = useTranslation('admin')
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !document.querySelector('.pm-dialog')) closeRef.current()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])
  return (
    <div className="ap-drawer-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}>
      <aside className={`ap-drawer${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <header>
          <div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>
          <button type="button" onClick={onClose} aria-label={t('adminPlatform.common.close')}><X /></button>
        </header>
        <div className="ap-drawer-body">{children}</div>
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
