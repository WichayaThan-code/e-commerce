/** Returns next only when it is a same-origin path (blocks open redirects). */
export function safeInternalPath(next: string | null | undefined, fallback = '/'): string {
  if (!next) return fallback
  const value = next.trim()
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('://')) return fallback
  return value
}
