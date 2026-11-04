import { useCallback, useEffect, useState } from 'react'

const DEFAULT_RESEND_COOLDOWN_SECONDS = 60

export type OtpTimer = {
  remainingSeconds: number
  expired: boolean
  resendRemainingSeconds: number
  canResend: boolean
  start: (expiresInSeconds: number, resendCooldownSeconds?: number) => void
  markExpired: () => void
  applyResendCooldown: (seconds: number) => void
}

/** OTP expiry + resend cooldown driven by wall-clock timestamps (tick every 1s). */
export function useOtpTimer(defaultResendCooldownSeconds = DEFAULT_RESEND_COOLDOWN_SECONDS): OtpTimer {
  const [expiresAt, setExpiresAt] = useState<number | null>(null)
  const [resendAt, setResendAt] = useState<number | null>(null)
  const [forcedExpired, setForcedExpired] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (expiresAt == null && resendAt == null) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [expiresAt, resendAt])

  const start = useCallback((expiresInSeconds: number, resendCooldownSeconds = defaultResendCooldownSeconds) => {
    const t = Date.now()
    setForcedExpired(false)
    setExpiresAt(t + Math.max(0, expiresInSeconds) * 1000)
    setResendAt(t + Math.max(0, resendCooldownSeconds) * 1000)
    setNow(t)
  }, [defaultResendCooldownSeconds])

  const markExpired = useCallback(() => {
    setForcedExpired(true)
  }, [])

  const applyResendCooldown = useCallback((seconds: number) => {
    const t = Date.now()
    setResendAt(t + Math.max(0, seconds) * 1000)
    setNow(t)
  }, [])

  const remainingSeconds = expiresAt == null ? 0 : Math.max(0, Math.ceil((expiresAt - now) / 1000))
  const expired = forcedExpired || (expiresAt != null && remainingSeconds <= 0)
  const resendRemainingSeconds = resendAt == null ? 0 : Math.max(0, Math.ceil((resendAt - now) / 1000))
  const started = expiresAt != null || resendAt != null
  const canResend = !started || expired || resendRemainingSeconds <= 0

  return { remainingSeconds, expired, resendRemainingSeconds, canResend, start, markExpired, applyResendCooldown }
}

export function formatOtpCountdown(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
