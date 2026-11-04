import { ArrowRight, CircleAlert, KeyRound, LoaderCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { autofillSyncHandlers } from '../../hooks/autofillSync'
import { formatOtpCountdown, type OtpTimer } from '../../hooks/useOtpTimer'

type EmailOtpPanelProps = {
  email: string
  otpId: string | null
  otpCode: string
  onOtpCodeChange: (value: string) => void
  otpTimer: OtpTimer
  otpAttempts: number
  submitting: boolean
  resending: boolean
  message: string
  onVerify: (event: React.FormEvent<HTMLFormElement>) => void
  onResend: () => void
  verifyLabel?: string
  verifyingLabel?: string
  /** Default `signup` keeps SignupPage markup/classes unchanged. */
  variant?: 'signup' | 'login'
  /** Extra content after the form (e.g. logout), login variant only. */
  afterForm?: React.ReactNode
}

/** Shared EMAIL_VERIFY OTP form (signup + /verify-email). */
export default function EmailOtpPanel({
  email,
  otpId,
  otpCode,
  onOtpCodeChange,
  otpTimer,
  otpAttempts,
  submitting,
  resending,
  message,
  onVerify,
  onResend,
  verifyLabel,
  verifyingLabel,
  variant = 'signup',
  afterForm,
}: EmailOtpPanelProps) {
  const { t } = useTranslation('auth')
  const p = (key: string, options?: Record<string, unknown>) => t(`signupPage.${key}`, options)
  const otpBlocked = otpTimer.expired || otpAttempts >= 5
  const otpInputDisabled = submitting || resending || !otpId || otpBlocked
  const inputId = variant === 'login' ? 'verify-otp-code' : 'signup-otp-code'
  const timerId = variant === 'login' ? 'verify-otp-timer' : 'signup-otp-timer'
  const expiredId = variant === 'login' ? 'verify-otp-expired' : 'signup-otp-expired'
  const helpId = variant === 'login' ? 'verify-otp-help' : 'signup-otp-help'
  const otpHandlers = autofillSyncHandlers({
    onValue: onOtpCodeChange,
    transform: (value) => value.replace(/\D/g, '').slice(0, 6),
  })

  if (variant === 'login') {
    return (
      <>
        <p className="signup-otp-email" hidden>{email}</p>
        <form onSubmit={onVerify} noValidate aria-busy={submitting}>
          <label className="field-label" htmlFor={inputId}>{p('otp.codeLabel')}</label>
          <div className="login-field">
            <KeyRound />
            <input
              id={inputId}
              name="otpCode"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              pattern="[0-9]{6}"
              maxLength={6}
              required
              value={otpCode}
              {...otpHandlers}
              aria-describedby={[otpTimer.expired ? expiredId : otpId ? timerId : '', helpId].filter(Boolean).join(' ')}
              aria-label={p('otp.codeLabel')}
              disabled={otpInputDisabled}
              autoFocus
              placeholder="000000"
            />
          </div>
          {otpTimer.expired ? (
            <p id={expiredId} className="password-field-hint" role="status">{p('otp.expired')}</p>
          ) : otpId ? (
            <p
              id={timerId}
              className={`password-field-hint${otpTimer.remainingSeconds < 60 ? '' : ' otp-timer-hint'}`}
              role="timer"
              aria-live="off"
            >
              {p('otp.expiresIn', { time: formatOtpCountdown(otpTimer.remainingSeconds) })}
            </p>
          ) : null}
          <p id={helpId} className="otp-help-hint">{p('otp.helpText')}</p>
          {otpAttempts > 0 && <p className="password-field-hint" role="status">{p('otp.attempts', { count: otpAttempts })}</p>}
          <button
            className="forgot"
            type="button"
            onClick={() => void onResend()}
            disabled={resending || submitting || !email || !otpTimer.canResend}
          >
            {resending
              ? <><LoaderCircle className="login-spinner" size={16} />{p('otp.resending')}</>
              : !otpTimer.canResend
                ? p('otp.resendIn', { time: formatOtpCountdown(otpTimer.resendRemainingSeconds) })
                : p('otp.resend')}
          </button>
          <button className="login-submit" type="submit" disabled={otpInputDisabled || otpCode.length !== 6}>
            {submitting
              ? <><LoaderCircle className="login-spinner" size={19} /> {verifyingLabel ?? p('otp.verifying')}</>
              : <>{verifyLabel ?? p('otp.verify')} <ArrowRight size={19} /></>}
          </button>
          {message && <p id="login-message" className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>}
        </form>
        {afterForm}
      </>
    )
  }

  return (
    <section className="signup-otp" aria-labelledby="signup-otp-label">
      <p className="signup-otp-email" hidden>{email}</p>
      <form onSubmit={onVerify} aria-busy={submitting}>
        <label htmlFor={inputId}>{p('otp.codeLabel')}</label>
        <input
          id={inputId}
          name="otpCode"
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]{6}"
          maxLength={6}
          required
          value={otpCode}
          {...otpHandlers}
          aria-describedby={[otpTimer.expired ? expiredId : otpId ? timerId : '', helpId].filter(Boolean).join(' ')}
          aria-label={p('otp.codeLabel')}
          disabled={otpInputDisabled}
          autoFocus
        />
        {otpTimer.expired ? (
          <p id={expiredId} className="signup-otp-expired-msg" role="status">{p('otp.expired')}</p>
        ) : otpId ? (
          <p
            id={timerId}
            className={`signup-otp-timer${otpTimer.remainingSeconds < 60 ? ' is-urgent' : ''}`}
            role="timer"
            aria-live="off"
          >
            {p('otp.expiresIn', { time: formatOtpCountdown(otpTimer.remainingSeconds) })}
          </p>
        ) : null}
        <p id={helpId}>{p('otp.helpText')}</p>
        {otpAttempts > 0 && <p className="signup-otp-attempts" role="status">{p('otp.attempts', { count: otpAttempts })}</p>}
        <button className="login-submit signup-submit" type="submit" disabled={otpInputDisabled || otpCode.length !== 6}>
          {submitting
            ? <><LoaderCircle className="login-spinner" size={19} /> {verifyingLabel ?? p('otp.verifying')}</>
            : <>{verifyLabel ?? p('otp.verify')} <ArrowRight size={19} /></>}
        </button>
      </form>
      <button
        className="signup-otp-resend"
        type="button"
        onClick={() => void onResend()}
        disabled={resending || submitting || !email || !otpTimer.canResend}
      >
        {resending
          ? <><LoaderCircle className="login-spinner" size={16} />{p('otp.resending')}</>
          : !otpTimer.canResend
            ? p('otp.resendIn', { time: formatOtpCountdown(otpTimer.resendRemainingSeconds) })
            : p('otp.resend')}
      </button>
      {message && <p className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>}
    </section>
  )
}
