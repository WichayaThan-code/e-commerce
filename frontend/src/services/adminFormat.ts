/** Display helpers for the admin directory pages (numbers / dates / month-over-month change). */
import type { AdminMetric } from './api'

export const NOT_ENABLED = 'ยังไม่เปิดใช้งาน'

export function formatNumber(value: number | null | undefined) {
  return value === null || value === undefined ? '—' : value.toLocaleString('en-US')
}

const dateTime = new Intl.DateTimeFormat('th-TH-u-ca-gregory', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Bangkok' })
const dateOnly = new Intl.DateTimeFormat('th-TH-u-ca-gregory', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'Asia/Bangkok' })

export function formatDateTime(value: string | null | undefined) {
  return value ? dateTime.format(new Date(value)) : '-'
}

export function formatDate(value: string | null | undefined) {
  return value ? dateOnly.format(new Date(`${value}T00:00:00+07:00`)) : ''
}

/** "↑ 15%" / "↓ 3%" / "0%" or "-" when there is no previous-month baseline. */
export function formatChange(metric: AdminMetric | null | undefined): { text: string; tone: 'up' | 'down' | 'flat' } {
  if (!metric || metric.changePercent === null) return { text: '-', tone: 'flat' }
  const value = metric.changePercent
  const rounded = Math.abs(value) >= 10 ? Math.round(Math.abs(value)).toString() : Math.abs(value).toFixed(1).replace(/\.0$/, '')
  if (value > 0) return { text: `↑ ${rounded}%`, tone: 'up' }
  if (value < 0) return { text: `↓ ${rounded}%`, tone: 'down' }
  return { text: '0%', tone: 'flat' }
}

export function percentOf(part: number | undefined, total: number | undefined) {
  if (part === undefined || !total) return '-'
  return `${((part * 100) / total).toFixed(1)}%`
}

export function initialOf(name: string | null | undefined) {
  return (name ?? '').trim().charAt(0).toUpperCase() || '?'
}
