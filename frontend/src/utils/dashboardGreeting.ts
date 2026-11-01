/** Placeholders that must never appear as the dashboard greeting name. */
const BAD_NAME = /^(customer|guest|user|n\/a|null|undefined|-)$/i

function usable(value: string | null | undefined): string | null {
  const text = (value ?? '').trim()
  if (!text || BAD_NAME.test(text)) return null
  return text
}

/**
 * Display name for dashboard welcome:
 * firstName → first + last → email local-part. Never "Customer"/placeholders.
 */
export function dashboardGreetingName(user: {
  firstName?: string | null
  lastName?: string | null
  email?: string | null
} | null | undefined): string {
  if (!user) return 'ผู้ใช้งาน'
  const first = usable(user.firstName)
  if (first) return first
  const last = usable(user.lastName)
  const full = [first, last].filter(Boolean).join(' ').trim()
  if (full) return full
  if (last) return last
  const local = (user.email ?? '').trim().split('@')[0]?.trim()
  return local || 'ผู้ใช้งาน'
}

/** Hour 0–23 in Asia/Bangkok. */
export function bangkokHour(date: Date = new Date()): number {
  const raw = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Bangkok',
    hour: 'numeric',
    hourCycle: 'h23',
  }).formatToParts(date).find((part) => part.type === 'hour')?.value
  const hour = Number(raw)
  return Number.isFinite(hour) ? hour % 24 : 0
}

/**
 * Neutral Thai greeting by Bangkok clock:
 * 05:00–11:59 morning · 12:00–17:59 afternoon · otherwise evening.
 */
export function thaiTimeGreeting(date: Date = new Date()): string {
  const hour = bangkokHour(date)
  if (hour >= 5 && hour < 12) return 'สวัสดีตอนเช้า'
  if (hour >= 12 && hour < 18) return 'สวัสดีตอนบ่าย'
  return 'สวัสดีตอนเย็น'
}

export type ShopStatusChip = { label: string; tone: 'ok' | 'warn' | 'info' | 'danger' }

export function shopStatusChip(status: string | null | undefined): ShopStatusChip | null {
  switch ((status ?? '').toUpperCase()) {
    case 'APPROVED':
      return { label: 'เปิดขาย', tone: 'ok' }
    case 'SUSPENDED':
      return { label: 'ปิดชั่วคราว', tone: 'warn' }
    case 'PENDING_REVIEW':
      return { label: 'รออนุมัติ', tone: 'info' }
    case 'REJECTED':
      return { label: 'ไม่ผ่านอนุมัติ', tone: 'danger' }
    default:
      return null
  }
}

export function shopInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return `${parts[0].charAt(0)}${parts[1].charAt(0)}`.toUpperCase()
}
