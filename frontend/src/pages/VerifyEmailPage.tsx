import { useEffect, useRef, useState } from 'react'
import { ArrowRight, CheckCircle2, CircleAlert, LoaderCircle, MailCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import AuthSplitLayout from '../components/auth/AuthSplitLayout'
import EmailOtpPanel from '../components/auth/EmailOtpPanel'
import { useAuth } from '../contexts/AuthContext'
import { useOtpTimer } from '../hooks/useOtpTimer'
import { ApiError, authApi } from '../services/api'
import { maskEmail } from '../utils/maskContact'
import { safeInternalPath } from '../utils/safeInternalPath'

function resendCooldownSeconds(error: ApiError): number {
  const raw = error.details?.retryAfter
  const value = typeof raw === 'number' ? raw : Number(raw)
  return Number.isFinite(value) && value > 0 ? Math.ceil(value) : 60
}

export default function VerifyEmailPage() {
  const { t } = useTranslation('auth')
  const p = (key: string, options?: Record<string, unknown>) => t(`verifyEmailPage.${key}`, options)
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { user, loading, logout, reload } = useAuth()
  const linkOtpId = searchParams.get('otpId')?.trim() ?? ''
  const linkToken = searchParams.get('token')?.trim() ?? ''
  const hasLink = Boolean(linkOtpId && linkToken)
  const nextPath = safeInternalPath(searchParams.get('next'), '/')

  const [otpId, setOtpId] = useState<string | null>(null)
  const [otpCode, setOtpCode] = useState('')
  const [otpAttempts, setOtpAttempts] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [resending, setResending] = useState(false)
  const [message, setMessage] = useState('')
  const [linkStatus, setLinkStatus] = useState<'idle' | 'working' | 'ok' | 'failed'>(hasLink ? 'working' : 'idle')
  const otpTimer = useOtpTimer()
  const linkTried = useRef(false)
  const autoSendTried = useRef(false)
  const verifyBusy = useRef(false)

  useEffect(() => {
    if (!hasLink || linkTried.current) return
    linkTried.current = true
    setLinkStatus('working')
    void (async () => {
      try {
        const response = await authApi.verifyOtpLink(linkOtpId, linkToken)
        if (!response.verified) throw new ApiError('OTP_NOT_VERIFIED', t('verifyEmailPage.linkFailed'), 400)
        setLinkStatus('ok')
        try { await reload() } catch { /* session may be on another device */ }
      } catch (error) {
        setLinkStatus('failed')
        setMessage(error instanceof Error ? error.message : t('verifyEmailPage.linkFailed'))
      }
    })()
  }, [hasLink, linkOtpId, linkToken, reload, t])

  const sendOtp = async () => {
    if (!user?.email || resending || submitting) return
    setResending(true)
    setMessage('')
    try {
      const response = await authApi.sendOtp(user.email, 'EMAIL_VERIFY', true)
      setOtpId(response.otpId)
      setOtpCode('')
      setOtpAttempts(0)
      otpTimer.start(response.expiresIn)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'OTP_RATE_LIMITED') {
        otpTimer.applyResendCooldown(resendCooldownSeconds(error))
        setMessage(t('signupPage.otp.resendTooSoon'))
      } else {
        setMessage(error instanceof Error ? error.message : t('signupPage.otp.sendFailed'))
      }
    } finally {
      setResending(false)
    }
  }

  useEffect(() => {
    if (hasLink || loading || !user || user.emailVerified || autoSendTried.current) return
    autoSendTried.current = true
    void sendOtp()
    // intentionally once when the signed-in unverified user opens the page
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasLink, loading, user?.id, user?.emailVerified])

  useEffect(() => {
    if (linkStatus !== 'failed' || !user || user.emailVerified || autoSendTried.current) return
    autoSendTried.current = true
    void sendOtp()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [linkStatus, user?.id, user?.emailVerified])

  const verifyCode = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (verifyBusy.current || resending || !otpId || otpTimer.expired || otpAttempts >= 5) return
    const input = event.currentTarget.elements.namedItem('otpCode') as HTMLInputElement | null
    const code = (input?.value || otpCode).replace(/\D/g, '').slice(0, 6)
    setOtpCode(code)
    if (code.length !== 6) return
    verifyBusy.current = true
    setSubmitting(true)
    setMessage('')
    try {
      const response = await authApi.verifyOtp(otpId, code)
      if (!response.verified) throw new ApiError('OTP_NOT_VERIFIED', t('signupPage.otp.verifyFailed'), 400)
      await reload()
      navigate(nextPath, { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INVALID_OTP') {
        const next = otpAttempts + 1
        setOtpAttempts(next)
        if (next >= 5) otpTimer.markExpired()
      } else if (error instanceof ApiError && error.code === 'OTP_EXPIRED') {
        otpTimer.markExpired()
      } else if (error instanceof ApiError && error.code === 'OTP_LOCKED') {
        setOtpAttempts(5)
        otpTimer.markExpired()
      }
      setMessage(error instanceof Error ? error.message : t('signupPage.otp.verifyFailed'))
    } finally {
      verifyBusy.current = false
      setSubmitting(false)
    }
  }

  const onLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  if (loading) {
    return (
      <AuthSplitLayout navAriaLabel={p('navAria')}>
        <div className="login-title">
          <span className="login-welcome-icon"><MailCheck size={25} /></span>
          <span className="login-kicker">{p('kicker')}</span>
          <h2>{p('title')}</h2>
          <p>{p('loading')}</p>
        </div>
        <p className="login-message" role="status"><LoaderCircle className="login-spinner" size={18} /><span>{p('loading')}</span></p>
      </AuthSplitLayout>
    )
  }

  if (!hasLink && (!user || user.emailVerified)) {
    return <NavigateHome />
  }

  const masked = maskEmail(user?.email)
  const showOtpForm = (!hasLink && user && !user.emailVerified)
    || (hasLink && linkStatus === 'failed' && user && !user.emailVerified)

  return (
    <AuthSplitLayout navAriaLabel={p('navAria')}>
      <div className="login-title">
        <span className="login-welcome-icon">
          {hasLink && linkStatus === 'ok' ? <CheckCircle2 size={25} /> : <MailCheck size={25} />}
        </span>
        <span className="login-kicker">{p('kicker')}</span>
        <h2 id="verify-otp-label">
          {hasLink && linkStatus === 'working' ? p('linkTitle')
            : hasLink && linkStatus === 'ok' ? p('linkSuccessTitle')
              : hasLink && linkStatus === 'failed' ? p('linkFailedTitle')
                : p('title')}
        </h2>
        <p>
          {hasLink && linkStatus === 'working' ? p('linkSubtitle')
            : hasLink && linkStatus === 'ok' ? p('linkSuccessSubtitle')
              : hasLink && linkStatus === 'failed' ? p('linkFailedSubtitle')
                : p('subtitle', { email: masked })}
        </p>
      </div>

      {hasLink && linkStatus === 'working' && (
        <p className="login-message" role="status">
          <LoaderCircle className="login-spinner" size={18} />
          <span>{p('linkWorking')}</span>
        </p>
      )}

      {hasLink && linkStatus === 'ok' && (
        <>
          <p className="login-security"><CheckCircle2 size={15} /> {p('linkSuccessNote')}</p>
          <button className="login-submit" type="button" onClick={() => navigate(nextPath, { replace: true })}>
            {p('continue')} <ArrowRight size={19} />
          </button>
        </>
      )}

      {hasLink && linkStatus === 'failed' && message && (
        <p className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>
      )}

      {hasLink && linkStatus === 'failed' && user && !user.emailVerified && !otpId && (
        <button className="login-submit" type="button" disabled={resending || !otpTimer.canResend} onClick={() => void sendOtp()}>
          {resending
            ? <><LoaderCircle className="login-spinner" size={19} /> {p('resending')}</>
            : p('resendOtp')}
        </button>
      )}

      {hasLink && linkStatus === 'failed' && !user && (
        <p className="signup-line"><a href="/login">{p('backToLogin')}</a></p>
      )}

      {showOtpForm && user && (
        <EmailOtpPanel
          variant="login"
          email={user.email}
          otpId={otpId}
          otpCode={otpCode}
          onOtpCodeChange={setOtpCode}
          otpTimer={otpTimer}
          otpAttempts={otpAttempts}
          submitting={submitting}
          resending={resending}
          message={hasLink ? '' : message}
          onVerify={verifyCode}
          onResend={() => void sendOtp()}
          verifyLabel={p('verify')}
          verifyingLabel={p('verifying')}
          afterForm={(
            <p className="signup-line">
              <button type="button" onClick={() => void onLogout()}>{p('logout')}</button>
            </p>
          )}
        />
      )}

      {user && linkStatus !== 'ok' && !showOtpForm && (
        <p className="signup-line">
          <button type="button" onClick={() => void onLogout()}>{p('logout')}</button>
        </p>
      )}
    </AuthSplitLayout>
  )
}

function NavigateHome() {
  const navigate = useNavigate()
  useEffect(() => { navigate('/', { replace: true }) }, [navigate])
  return null
}
