export type MenuMode = 'none' | 'month' | 'year'

export const WEEKDAYS_TH = ['อา', 'จ', 'อ', 'พ', 'พฤ', 'ศ', 'ส']
export const WEEKDAYS_EN = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']
export const MONTHS_TH = ['มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน', 'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม']
export const MONTHS_EN = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

export function parseIso(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const [y, m, d] = value.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  if (date.getFullYear() !== y || date.getMonth() !== m - 1 || date.getDate() !== d) return null
  return date
}

export function toIso(date: Date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

export function daysInMonth(year: number, month: number) {
  return new Date(year, month + 1, 0).getDate()
}

export function buildMonthCells(view: Date) {
  const year = view.getFullYear()
  const month = view.getMonth()
  const firstDow = new Date(year, month, 1).getDay()
  const total = daysInMonth(year, month)
  const prevTotal = daysInMonth(year, month - 1)
  const items: { date: Date; inMonth: boolean }[] = []
  for (let i = firstDow - 1; i >= 0; i -= 1) {
    items.push({ date: new Date(year, month - 1, prevTotal - i), inMonth: false })
  }
  for (let day = 1; day <= total; day += 1) {
    items.push({ date: new Date(year, month, day), inMonth: true })
  }
  let nextDay = 1
  while (items.length < 42) {
    items.push({ date: new Date(year, month + 1, nextDay), inMonth: false })
    nextDay += 1
  }
  return items
}
