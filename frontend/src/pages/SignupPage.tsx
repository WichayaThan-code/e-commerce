import { useMemo, useRef, useState } from 'react'
import {
  ArrowLeft, ArrowRight, Check, CircleAlert, Eye, EyeOff, Gift, Heart, LoaderCircle, LockKeyhole,
  Mail, Phone, ShieldCheck, ShoppingBag, Store, Truck,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import '../styles/login.css'
import '../styles/signup.css'
import '../styles/form-fields.css'
import '../styles/form-invalid.css'
import { AsciiPasswordInput, PasswordInputHints } from '../components/form/AsciiPasswordInput'
import FieldError from '../components/form/FieldError'
import FormField from '../components/form/FormField'
import FormGrid from '../components/form/FormGrid'
import SignupAgreement, { type AgreementStep } from '../components/SignupAgreement'
import { useAuth } from '../contexts/AuthContext'
import LanguageSwitcher from '../components/LanguageSwitcher'
import SiteBrand from '../components/SiteBrand'
import { AUTOFILL_ANIMATION, autofillSyncHandlers } from '../hooks/autofillSync'
import { useAsciiPasswordInput } from '../hooks/useAsciiPasswordInput'
import { formatPhoneNumber } from '../services/merchantApplicationValidation'
import { isPasswordValid, passwordRules } from '../utils/passwordRules'

const fieldNames = ['firstName', 'lastName', 'email', 'phone', 'password', 'confirmPassword'] as const
type FieldName = (typeof fieldNames)[number]
const perkKeys = ['variety', 'payment', 'delivery'] as const
const perkIcons = { variety: Gift, payment: ShieldCheck, delivery: Truck }

function SignupBrand({ ariaLabel }: { ariaLabel: string }) {
  return <SiteBrand homeAria={ariaLabel} className="signup-brand" href="/" />
}

export default function SignupPage() {
  const { t } = useTranslation('auth')
  const p = (key: string, options?: Record<string, unknown>) => t(`signupPage.${key}`, options)
  const label = (name: FieldName) => t(`signupPage.fieldLabels.${name}`)
  const [phone, setPhone] = useState('')
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
  const passwordField = useAsciiPasswordInput({ value: password, onValueChange: setPassword })
  const confirmField = useAsciiPasswordInput({ value: confirm, onValueChange: setConfirm })
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
    if (!next.password && !isPasswordValid(password)) next.password = p('form.errorPasswordRules')
    if (!next.confirmPassword && password !== confirm) next.confirmPassword = p('form.errorPasswordMismatch')
    return next
  }

  const createAccount = async (agreementAccepted: boolean) => {
    if (registrationPending.current || !formRef.current) return
    const form = formRef.current
    const data = new FormData(form)
    // Autofill may fill the DOM without React onChange — sync controlled fields first.
    const syncedPhone = formatPhoneNumber(String(data.get('phone') ?? phone))
    const passwordInput = form.elements.namedItem('password') as HTMLInputElement | null
    const confirmInput = form.elements.namedItem('confirmPassword') as HTMLInputElement | null
    const syncedPassword = passwordInput?.value ?? password
    const syncedConfirm = confirmInput?.value ?? confirm
    setPhone(syncedPhone)
    setPassword(syncedPassword)
    setConfirm(syncedConfirm)
    data.set('phone', syncedPhone)
    data.set('password', syncedPassword)
    data.set('confirmPassword', syncedConfirm)
    const nextErrors = validate(data)
    // validate() reads password/confirm from React state — override with DOM-synced values after autofill
    delete nextErrors.password
    delete nextErrors.confirmPassword
    if (!syncedPassword.trim()) nextErrors.password = p('form.fieldRequired', { label: label('password') })
    else if (!isPasswordValid(syncedPassword)) nextErrors.password = p('form.errorPasswordRules')
    if (!syncedConfirm.trim()) nextErrors.confirmPassword = p('form.fieldRequired', { label: label('confirmPassword') })
    else if (syncedPassword !== syncedConfirm) nextErrors.confirmPassword = p('form.errorPasswordMismatch')
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
      phone: syncedPhone.replace(/[\s-]/g, ''),
      password: syncedPassword,
    }
    try {
      await register(payload)
      setAgreement(null)
      const next = new URLSearchParams(window.location.search).get('next')
      const safeNext = next && next.startsWith('/') && !next.startsWith('//') ? next : null
      window.location.replace(safeNext ? `/verify-email?next=${encodeURIComponent(safeNext)}` : '/verify-email')
    } catch (error) {
      const reason = error instanceof Error ? error.message : p('form.errorFailed')
      setAgreementError(reason)
      setMessage(reason)
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

  const fieldProps = (name: FieldName) => ({
    'aria-invalid': Boolean(errors[name]),
    'aria-describedby': [name === 'password' ? 'signup-password-rules' : '', errors[name] ? `signup-error-${name}` : ''].filter(Boolean).join(' ') || undefined,
  })
  const fieldError = (name: FieldName) => (
    <FieldError text={errors[name]} id={`signup-error-${name}`} />
  )
  const clearField = (name: string) => {
    if (!(fieldNames as readonly string[]).includes(name)) return
    setErrors((previous) => {
      const next = { ...previous }
      delete next[name as FieldName]
      if (name === 'password') delete next.confirmPassword
      return next
    })
    setMessage('')
  }
  const phoneHandlers = autofillSyncHandlers({
    onValue: setPhone,
    onClearError: () => clearField('phone'),
    transform: formatPhoneNumber,
  })
  const syncAutofillField = (input: HTMLInputElement) => {
    const name = input.name
    clearField(name)
    if (name === 'phone') setPhone(formatPhoneNumber(input.value))
  }

  return (
    <main className="login-page signup-page">
      <section className="login-story">
        <div className="login-story-image" />
        <div className="login-story-content">
          <SignupBrand ariaLabel={p('brandAriaHome')} />
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
            <span className="login-kicker">{p('form.kicker')}</span>
            <h2>{p('form.title')}</h2>
            <p>{p('form.subtitle')}</p>
          </div>
          <form
            ref={formRef}
            onSubmit={submit}
            noValidate
            aria-busy={submitting}
            onChange={(event) => {
              if (!(event.target instanceof HTMLInputElement)) return
              syncAutofillField(event.target)
            }}
            onInput={(event) => {
              if (!(event.target instanceof HTMLInputElement)) return
              syncAutofillField(event.target)
            }}
            onAnimationStart={(event) => {
              if (!(event.target instanceof HTMLInputElement)) return
              if (event.animationName !== AUTOFILL_ANIMATION) return
              syncAutofillField(event.target)
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
              <input
                name="phone"
                {...fieldProps('phone')}
                required
                type="tel"
                inputMode="tel"
                maxLength={12}
                value={phone}
                {...phoneHandlers}
                placeholder={p('form.phonePlaceholder')}
                autoComplete="tel"
              />
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
                <AsciiPasswordInput
                  field={passwordField}
                  name="password"
                  {...fieldProps('password')}
                  required
                  visible={showPassword}
                  placeholder={p('form.passwordPlaceholder')}
                  autoComplete="new-password"
                />
                <button type="button" onClick={() => setShowPassword((v) => !v)} aria-pressed={showPassword} aria-label={showPassword ? p('form.hidePassword') : p('form.showPassword')}>{showPassword ? <EyeOff /> : <Eye />}</button>
              </div>
              <PasswordInputHints asciiWarning={passwordField.asciiWarning} capsLockOn={passwordField.capsLockOn} />
              {fieldError('password')}
            </label>
            <label>
              {p('form.confirmPasswordLabel')}
              <div className="signup-input">
                <LockKeyhole />
                <AsciiPasswordInput
                  field={confirmField}
                  name="confirmPassword"
                  {...fieldProps('confirmPassword')}
                  required
                  visible={showConfirm}
                  placeholder={p('form.confirmPasswordPlaceholder')}
                  autoComplete="new-password"
                />
                <button type="button" onClick={() => setShowConfirm((v) => !v)} aria-pressed={showConfirm} aria-label={showConfirm ? p('form.hideConfirmPassword') : p('form.showConfirmPassword')}>{showConfirm ? <EyeOff /> : <Eye />}</button>
              </div>
              <PasswordInputHints asciiWarning={confirmField.asciiWarning} capsLockOn={confirmField.capsLockOn} />
              {fieldError('confirmPassword')}
            </label>
            <p className="signup-agreement-note"><ShieldCheck size={17} aria-hidden="true" /><span>{p('form.agreementNote')}</span></p>
            <button ref={submitRef} className="login-submit signup-submit" type="submit" disabled={submitting}>
              {submitting ? <><LoaderCircle className="login-spinner" size={19} /> {p('form.submitting')}</> : <>{p('form.submit')} <ArrowRight size={19} /></>}
            </button>
            {message && <p className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>}
          </form>
          <div className="login-divider"><span />{p('dividerSocial')}<span /></div>
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
