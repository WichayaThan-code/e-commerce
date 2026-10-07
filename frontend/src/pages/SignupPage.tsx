import { useMemo, useRef, useState } from 'react'
import {
  ArrowLeft, ArrowRight, Check, CircleAlert, Eye, EyeOff, Gift, Heart, LoaderCircle, LockKeyhole,
  Mail, Phone, ShieldCheck, ShoppingBag, Store, Truck,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import '../styles/login.css'
import '../styles/signup.css'
import '../styles/form-fields.css'
import FormField from '../components/form/FormField'
import FormGrid from '../components/form/FormGrid'
import SignupAgreement, { type AgreementStep } from '../components/SignupAgreement'
import { useAuth } from '../contexts/AuthContext'
import LanguageSwitcher from '../components/LanguageSwitcher'
import { ApiError, authApi } from '../services/api'

const fieldNames = ['firstName', 'lastName', 'email', 'phone', 'password', 'confirmPassword'] as const
type FieldName = (typeof fieldNames)[number]
const perkKeys = ['variety', 'payment', 'delivery'] as const
const perkIcons = { variety: Gift, payment: ShieldCheck, delivery: Truck }

function SignupBrand({ ariaLabel, tagline }: { ariaLabel: string; tagline: string }) {
  return (
    <a className="brand signup-brand" href="/" aria-label={ariaLabel}>
      <span className="brand-mark"><ShoppingBag size={24} /><i>✓</i></span>
      <span><b>ThaiShop<span>Fun</span></b><small>{tagline}</small></span>
    </a>
  )
}

const passwordRules = (value: string) => ({ length: value.length >= 8, number: /\d/.test(value), letter: /[A-Za-z]/.test(value) })

export default function SignupPage() {
  const { t } = useTranslation('auth')
  const p = (key: string, options?: Record<string, unknown>) => t(`signupPage.${key}`, options)
  const label = (name: FieldName) => t(`signupPage.fieldLabels.${name}`)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({})
  const formRef = useRef<HTMLFormElement>(null)
  const submitRef = useRef<HTMLButtonElement>(null)
  const registrationPending = useRef(false)
  const [agreement, setAgreement] = useState<AgreementStep | null>(null)
  const [agreementError, setAgreementError] = useState('')
  const [verificationEmail, setVerificationEmail] = useState('')
  const [otpId, setOtpId] = useState<string | null>(null)
  const [otpCode, setOtpCode] = useState('')
  const [otpAttempts, setOtpAttempts] = useState(0)
  const [resendAvailable, setResendAvailable] = useState(false)
  const [otpStep, setOtpStep] = useState(false)
  const { register } = useAuth()
  const rules = useMemo(() => passwordRules(password), [password])

  const focusField = (name: string) => (formRef.current?.elements.namedItem(name) as HTMLInputElement | null)?.focus()

  const validate = (data: FormData): Partial<Record<FieldName, string>> => {
    const next: Partial<Record<FieldName, string>> = {}
    for (const name of fieldNames) {
      if (!String(data.get(name) ?? '').trim()) next[name] = p('form.fieldRequired', { label: label(name) })
    }
    if (!next.email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(data.get('email')).trim())) next.email = p('form.errorEmailInvalid')
    if (!next.phone && !/^0\d{8,9}$/.test(String(data.get('phone')).replace(/[\s-]/g, ''))) next.phone = p('form.errorPhoneInvalid')
    if (!next.password && !Object.values(passwordRules(password)).every(Boolean)) next.password = p('form.errorPasswordRules')
    if (!next.confirmPassword && password !== confirm) next.confirmPassword = p('form.errorPasswordMismatch')
    return next
  }

  const createAccount = async (agreementAccepted: boolean) => {
    if (registrationPending.current || !formRef.current) return
    const data = new FormData(formRef.current)
    const nextErrors = validate(data)
    setErrors(nextErrors)
    setMessage('')
    setAgreementError('')
    const firstError = fieldNames.find((name) => nextErrors[name])
    if (firstError) {
      setAgreement(null)
      requestAnimationFrame(() => focusField(firstError))
      return
    }
    if (!agreementAccepted) {
      setAgreement('terms')
      return
    }
    registrationPending.current = true
    setSubmitting(true)
    const payload = {
      firstName: String(data.get('firstName')).trim(),
      lastName: String(data.get('lastName')).trim(),
      email: String(data.get('email')).trim(),
      phone: String(data.get('phone')).replace(/[\s-]/g, ''),
      password,
    }
    try {
      await register(payload)
      setVerificationEmail(payload.email)
      setOtpStep(true)
      setAgreement(null)
      setOtpCode('')
      setOtpAttempts(0)
      try {
        const response = await authApi.sendOtp(payload.email, 'EMAIL_VERIFY')
        setOtpId(response.otpId)
        setResendAvailable(false)
        setMessage('')
      } catch (error) {
        setOtpId(null)
        setResendAvailable(true)
        setMessage(error instanceof Error ? error.message : p('otp.sendFailed'))
      }
    } catch (error) {
      const reason = error instanceof Error ? error.message : p('form.errorFailed')
      setAgreementError(reason)
      setMessage(reason)
    } finally {
      registrationPending.current = false
      setSubmitting(false)
    }
  }

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (registrationPending.current) return
    void createAccount(false)
  }

  const acceptAgreement = () => void createAccount(true)
  const closeAgreement = () => {
    setAgreement(null)
    setAgreementError('')
    requestAnimationFrame(() => submitRef.current?.focus())
  }

  const verifyEmailCode = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (registrationPending.current || !otpId) return
    registrationPending.current = true
    setSubmitting(true)
    setMessage('')
    try {
      const response = await authApi.verifyOtp(otpId, otpCode)
      if (!response.verified) throw new ApiError('OTP_NOT_VERIFIED', p('otp.verifyFailed'), 400)
      window.location.replace('/dashboard')
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INVALID_OTP') {
        const next = otpAttempts + 1
        setOtpAttempts(next)
        if (next >= 5) setResendAvailable(true)
      } else if (error instanceof ApiError && (error.code === 'OTP_LOCKED' || error.code === 'OTP_EXPIRED')) {
        setOtpAttempts(5)
        setResendAvailable(true)
      }
      setMessage(error instanceof Error ? error.message : p('otp.verifyFailed'))
    } finally {
      registrationPending.current = false
      setSubmitting(false)
    }
  }

  const resendEmailCode = async () => {
    if (registrationPending.current || !verificationEmail) return
    registrationPending.current = true
    setSubmitting(true)
    setMessage('')
    try {
      const response = await authApi.sendOtp(verificationEmail, 'EMAIL_VERIFY')
      setOtpId(response.otpId)
      setOtpCode('')
      setOtpAttempts(0)
      setResendAvailable(false)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : p('otp.sendFailed'))
    } finally {
      registrationPending.current = false
      setSubmitting(false)
    }
  }

  const fieldProps = (name: FieldName) => ({
    'aria-invalid': Boolean(errors[name]),
    'aria-describedby': [name === 'password' ? 'signup-password-rules' : '', errors[name] ? `signup-error-${name}` : ''].filter(Boolean).join(' ') || undefined,
  })
  const fieldError = (name: FieldName) =>
    errors[name] ? (
      <span className="signup-field-error" id={`signup-error-${name}`}><CircleAlert size={14} aria-hidden="true" />{errors[name]}</span>
    ) : null

  return (
    <main className="login-page signup-page">
      <section className="login-story">
        <div className="login-story-image" />
        <div className="login-story-content">
          <SignupBrand ariaLabel={p('brandAriaHome')} tagline={p('brandTagline')} />
          <div className="story-copy">
            <span className="login-eyebrow"><span /> {p('story.eyebrow')}</span>
            <h1>{p('story.titleLine1')}<br /><em>{p('story.titleEmphasis')}</em></h1>
            <p>{p('story.descriptionLine1')}<br />{p('story.descriptionLine2')}</p>
          </div>
          <div className="story-note">
            <Heart size={18} />
            <span>{p('story.noteLine1')}<br /><b>{p('story.noteEmphasis')}</b></span>
          </div>
          <div className="login-benefits">
            {perkKeys.map((key) => {
              const Icon = perkIcons[key]
              return (
                <div key={key}>
                  <Icon size={19} />
                  <span>{p(`benefits.${key}.title`)}</span>
                </div>
              )
            })}
          </div>
        </div>
      </section>
      <section className="login-form-side signup-form-side">
        <nav className="login-top-nav" aria-label={p('navAria')}>
          <a href="/"><ArrowLeft size={16} /> {t('back')}</a>
          <div className="login-nav-tools"><LanguageSwitcher /><a href="/help">{t('help')}</a></div>
        </nav>
        <div className="login-card signup-card">
          <div className="login-title">
            <span className="login-welcome-icon"><ShoppingBag size={25} /></span>
            <span className="login-kicker">{p(otpStep ? 'otp.kicker' : 'form.kicker')}</span>
            <h2 id={otpStep ? 'signup-otp-label' : undefined}>{p(otpStep ? 'otp.title' : 'form.title')}</h2>
            <p>{p(otpStep ? 'otp.subtitle' : 'form.subtitle', { email: verificationEmail })}</p>
          </div>
          {!otpStep ? <form
            ref={formRef}
            onSubmit={submit}
            noValidate
            aria-busy={submitting}
            onChange={(event) => {
              if (!(event.target instanceof HTMLInputElement)) return
              const name = event.target.name as FieldName
              setErrors((previous) => {
                const next = { ...previous }
                delete next[name]
                if (name === 'password') delete next.confirmPassword
                return next
              })
              setMessage('')
            }}
          >
            <FormGrid>
              <FormField label={p('form.firstNameLabel')} error={errors.firstName} errorId="signup-error-firstName">
                <input name="firstName" {...fieldProps('firstName')} required placeholder={p('form.firstNamePlaceholder')} autoComplete="given-name" />
              </FormField>
              <FormField label={p('form.lastNameLabel')} error={errors.lastName} errorId="signup-error-lastName">
                <input name="lastName" {...fieldProps('lastName')} required placeholder={p('form.lastNamePlaceholder')} autoComplete="family-name" />
              </FormField>
            </FormGrid>
            <FormField label={p('form.emailLabel')} icon={Mail} error={errors.email} errorId="signup-error-email">
              <input name="email" {...fieldProps('email')} required type="email" placeholder={p('form.emailPlaceholder')} autoComplete="email" />
            </FormField>
            <FormField label={p('form.phoneLabel')} icon={Phone} error={errors.phone} errorId="signup-error-phone">
              <input name="phone" {...fieldProps('phone')} required type="tel" inputMode="tel" placeholder={p('form.phonePlaceholder')} autoComplete="tel" />
            </FormField>
            <label>
              {p('form.passwordLabel')}{' '}
              <span id="signup-password-rules" className="password-rules">
                <i className={rules.length ? 'ok' : ''}><Check />{p('form.passwordRuleLength')}</i>
                <i className={rules.number ? 'ok' : ''}><Check />{p('form.passwordRuleNumber')}</i>
                <i className={rules.letter ? 'ok' : ''}><Check />{p('form.passwordRuleLetter')}</i>
              </span>
              <div className="signup-input">
                <LockKeyhole />
                <input name="password" {...fieldProps('password')} required value={password} onChange={(e) => setPassword(e.target.value)} type={showPassword ? 'text' : 'password'} placeholder={p('form.passwordPlaceholder')} autoComplete="new-password" />
                <button type="button" onClick={() => setShowPassword((v) => !v)} aria-pressed={showPassword} aria-label={showPassword ? p('form.hidePassword') : p('form.showPassword')}>{showPassword ? <EyeOff /> : <Eye />}</button>
              </div>
              {fieldError('password')}
            </label>
            <label>
              {p('form.confirmPasswordLabel')}
              <div className="signup-input">
                <LockKeyhole />
                <input name="confirmPassword" {...fieldProps('confirmPassword')} required value={confirm} onChange={(e) => setConfirm(e.target.value)} type={showConfirm ? 'text' : 'password'} placeholder={p('form.confirmPasswordPlaceholder')} autoComplete="new-password" />
                <button type="button" onClick={() => setShowConfirm((v) => !v)} aria-pressed={showConfirm} aria-label={showConfirm ? p('form.hideConfirmPassword') : p('form.showConfirmPassword')}>{showConfirm ? <EyeOff /> : <Eye />}</button>
              </div>
              {fieldError('confirmPassword')}
            </label>
            <p className="signup-agreement-note"><ShieldCheck size={17} aria-hidden="true" /><span>{p('form.agreementNote')}</span></p>
            <button ref={submitRef} className="login-submit signup-submit" type="submit" disabled={submitting}>
              {submitting ? <><LoaderCircle className="login-spinner" size={19} /> {p('form.submitting')}</> : <>{p('form.submit')} <ArrowRight size={19} /></>}
            </button>
            {message && <p className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>}
          </form> : <section className="signup-otp" aria-labelledby="signup-otp-label">
            <form onSubmit={verifyEmailCode} aria-busy={submitting}>
              <label htmlFor="signup-otp-code">{p('otp.codeLabel')}</label>
              <input
                id="signup-otp-code"
                name="otpCode"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]{6}"
                maxLength={6}
                required
                value={otpCode}
                onChange={(event) => setOtpCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                aria-describedby="signup-otp-help"
                aria-label={p('otp.codeLabel')}
                disabled={submitting || !otpId || resendAvailable}
                autoFocus
              />
              <p id="signup-otp-help">{p('otp.helpText')}</p>
              {otpAttempts > 0 && <p className="signup-otp-attempts" role="status">{p('otp.attempts', { count: otpAttempts })}</p>}
              <button className="login-submit signup-submit" type="submit" disabled={submitting || !otpId || resendAvailable || otpCode.length !== 6}>
                {submitting ? <><LoaderCircle className="login-spinner" size={19} /> {p('otp.verifying')}</> : <>{p('otp.verify')} <ArrowRight size={19} /></>}
              </button>
            </form>
            {resendAvailable && <button className="signup-otp-resend" type="button" onClick={() => void resendEmailCode()} disabled={submitting}>
              {submitting ? <LoaderCircle className="login-spinner" size={16} /> : null}{p('otp.resend')}
            </button>}
            {message && <p className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>}
          </section>}
          {!otpStep && <><div className="login-divider"><span />{p('dividerSocial')}<span /></div>
          <div className="social-logins">
            {[p('socialGoogle'), p('socialFacebook'), p('socialApple')].map((provider) => (
              <button key={provider} type="button" disabled={submitting} onClick={() => setMessage(p('form.socialUnavailable', { provider }))}>
                {provider === p('socialGoogle') ? <i className="google-g" aria-hidden="true">G</i> : provider === p('socialFacebook') ? <i className="facebook-f" aria-hidden="true">f</i> : (
                  <svg className="apple-mark" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path fill="currentColor" d="M17.1 12.5c0-2 1.6-3 1.7-3.1-1-1.4-2.5-1.6-3-1.6-1.3-.1-2.5.8-3.2.8-.6 0-1.6-.8-2.7-.7-1.4 0-2.7.8-3.4 2-1.5 2.5-.4 6.3 1 8.3.6 1 1.4 2 2.5 1.9 1-.1 1.4-.6 2.7-.6 1.2 0 1.6.6 2.7.6 1.1 0 1.8-1 2.4-1.9.8-1.1 1.1-2.2 1.2-2.3-.1 0-2.3-.9-2.3-3.4ZM15 6.4c.5-.7 1-1.7.9-2.7-.9 0-2 .6-2.6 1.3-.5.6-1 1.6-.9 2.5 1 .1 1.9-.5 2.6-1.1Z" /></svg>
                )}
                {provider}
              </button>
            ))}
          </div>
          <p className="signup-line">{p('hasAccountPrompt')} <a href="/login">{p('loginLink')}</a></p>
          </>}
        </div>
        <div className="login-merchant">
          <Store size={20} />
          <span>{p('merchantPrompt')} <a href="/merchant/register">{p('merchantLink')} <ArrowRight size={14} /></a></span>
        </div>
        <footer className="login-footer"><p>{p('footer')}</p></footer>
      </section>
      {agreement && <SignupAgreement initialStep={agreement} busy={submitting} error={agreementError} onClose={closeAgreement} onAccept={acceptAgreement} />}
    </main>
  )
}
