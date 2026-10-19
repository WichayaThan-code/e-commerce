import type { CSSProperties, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import '../../styles/skeleton.css'

type SkeletonProps = {
  width?: number | string
  height?: number | string
  radius?: number | string
  className?: string
  style?: CSSProperties
}

function toSize(value?: number | string) {
  if (value == null) return undefined
  return typeof value === 'number' ? `${value}px` : value
}

export function Skeleton({ width, height, radius, className, style }: SkeletonProps) {
  return (
    <span
      className={['skeleton', className].filter(Boolean).join(' ')}
      style={{
        width: toSize(width),
        height: toSize(height),
        borderRadius: toSize(radius),
        ...style,
      }}
      aria-hidden="true"
    />
  )
}

export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={['skeleton-text', className].filter(Boolean).join(' ')} aria-hidden="true">
      {Array.from({ length: Math.max(1, lines) }, (_, index) => (
        <Skeleton key={index} />
      ))}
    </div>
  )
}

export function SkeletonTable({
  rows = 5,
  cols = 4,
  className,
}: {
  rows?: number
  cols?: number
  className?: string
}) {
  return (
    <div className={['skeleton-table', className].filter(Boolean).join(' ')} aria-hidden="true">
      {Array.from({ length: Math.max(1, rows) }, (_, row) => (
        <div
          key={row}
          className="skeleton-table-row"
          style={{ gridTemplateColumns: `repeat(${Math.max(1, cols)}, minmax(0, 1fr))` }}
        >
          {Array.from({ length: Math.max(1, cols) }, (_, col) => (
            <Skeleton key={col} height={row === 0 ? 14 : 16} />
          ))}
        </div>
      ))}
    </div>
  )
}

export function SkeletonCard({ className }: { className?: string }) {
  return (
    <div className={['skeleton-card', className].filter(Boolean).join(' ')} aria-hidden="true">
      <Skeleton className="skeleton-media" radius={10} />
      <SkeletonText lines={2} />
      <Skeleton width="40%" height={18} />
    </div>
  )
}

export function SkeletonForm({ fields = 4, className }: { fields?: number; className?: string }) {
  return (
    <div className={['skeleton-form', className].filter(Boolean).join(' ')} aria-hidden="true">
      {Array.from({ length: Math.max(1, fields) }, (_, index) => (
        <div key={index} className="skeleton-form-field">
          <Skeleton className="label" />
          <Skeleton className="control" />
        </div>
      ))}
    </div>
  )
}

export function SkeletonBusy({
  busy,
  children,
  className,
  refetching = false,
}: {
  busy?: boolean
  children: ReactNode
  className?: string
  refetching?: boolean
}) {
  const { t } = useTranslation()
  return (
    <div
      className={['skeleton-busy', refetching ? 'is-refetching' : '', className].filter(Boolean).join(' ')}
      aria-busy={busy || refetching ? 'true' : undefined}
    >
      {(busy || refetching) && (
        <span className="visually-hidden">{t('common.loading', { defaultValue: 'กำลังโหลด…' })}</span>
      )}
      {children}
    </div>
  )
}

export function LoadError({
  message,
  onRetry,
  retryLabel,
}: {
  message: string
  onRetry?: () => void
  retryLabel?: string
}) {
  const { t } = useTranslation()
  return (
    <div className="feedback-error" role="alert">
      <p>{message}</p>
      {onRetry ? (
        <button type="button" onClick={onRetry}>
          {retryLabel || t('common.retry', { defaultValue: 'ลองใหม่' })}
        </button>
      ) : null}
    </div>
  )
}

export default Skeleton
