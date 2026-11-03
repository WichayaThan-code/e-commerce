/** Already-masked email from admin API / prior FE pass: `s***@domain`. */
function alreadyMaskedEmail(value: string): boolean {
  return /^\S\*{3}@/.test(value) || value === '***' || /^\*@/.test(value)
}

/** Already-masked phone forms produced by this helper / AdminContactMask. */
function alreadyMaskedPhone(value: string): boolean {
  return (
    /^\d{1,2}x-xxx-\d{4}$/.test(value)
    || /^xxx-xxx-\d{4}$/.test(value)
    || /^••••\d{4}$/.test(value)
    || value === '****'
    || value === '—'
    || value === '•••-•••-••••'
  )
}

/** Strip non-digits; convert +66 / 66… to leading 0. */
export function normalizePhoneDigits(value: string): string {
  let digits = value.replace(/\D/g, '')
  if (digits.startsWith('66') && digits.length >= 11) {
    digits = `0${digits.slice(2)}`
  }
  return digits
}

/** Mask phone for admin UI: `0812345678` / `+66812345678` → `08x-xxx-5678`. Idempotent. */
export function maskPhone(value: string | null | undefined): string {
  if (value == null || !String(value).trim() || value === '-') return '—'
  const raw = String(value).trim()
  if (alreadyMaskedPhone(raw)) return raw
  const digits = normalizePhoneDigits(raw)
  if (digits.length < 4) return '****'
  const last4 = digits.slice(-4)
  if (digits.length >= 10) return `${digits.slice(0, 2)}x-xxx-${last4}`
  if (digits.length >= 7) return `xxx-xxx-${last4}`
  return `••••${last4}`
}

/** Mask email for admin UI: `somchai@example.com` → `s***@example.com`. Idempotent. */
export function maskEmail(value: string | null | undefined): string {
  if (value == null || !String(value).trim() || value === '-') return '—'
  const email = String(value).trim()
  if (alreadyMaskedEmail(email)) return email
  const at = email.indexOf('@')
  if (at <= 0) return '***'
  const local = email.slice(0, at)
  const domain = email.slice(at)
  if (local.length === 1) return `*${domain}`
  return `${local.charAt(0)}***${domain}`
}
