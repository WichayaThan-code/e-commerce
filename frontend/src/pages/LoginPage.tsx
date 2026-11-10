import { useState } from 'react'
import {
  ArrowRight, CircleAlert, Eye, EyeOff, LoaderCircle, LockKeyhole, ShieldCheck, ShoppingBag, UserRound,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import AuthSplitLayout from '../components/auth/AuthSplitLayout'
import SocialLoginButtons from '../components/auth/SocialLoginButtons'
import { AsciiPasswordInput, PasswordInputHints } from '../components/form/AsciiPasswordInput'
import { useAuth } from '../contexts/AuthContext'
import { AUTOFILL_ANIMATION } from '../hooks/autofillSync'
import { useAsciiPasswordInput } from '../hooks/useAsciiPasswordInput'
import { homePath } from '../services/permissions'

export default function LoginPage() {
  const { t } = useTranslation('auth')
  const p = (key: string, options?: Record<string, unknown>) => t(`loginPage.${key}`, options)
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [socialBusy, setSocialBusy] = useState(false)
  const passwordField = useAsciiPasswordInput()
  const { login } = useAuth()
  const formBusy = submitting || socialBusy

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const identity = String(form.get('identity') ?? '').trim()
    const password = String(form.get('password') ?? '')
    if (formBusy) return
    if (!identity || !password) {
      setMessage(p('form.errorIncomplete'))
      event.currentTarget.querySelector<HTMLInputElement>(!identity ? '#identity' : '#password')?.focus()
      return
    }
    setSubmitting(true)
    setMessage('')
    try {
      const authenticated = await login(identity, password)
      if (authenticated.emailVerified === false) {
        const next = new URLSearchParams(window.location.search).get('next')
        const suffix = next?.startsWith('/') && !next.startsWith('//') ? `?next=${encodeURIComponent(next)}` : ''
        window.location.replace(`/verify-email${suffix}`)
        return
      }
      const next = new URLSearchParams(window.location.search).get('next')
      const roleDestination = homePath(authenticated)
      const destination = next?.startsWith('/') && !next.startsWith('//') ? next : roleDestination
      window.location.replace(destination)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : p('form.errorFailed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthSplitLayout navAriaLabel={p('navAria')}>
      <div className="login-title">
        <span className="login-welcome-icon"><ShoppingBag size={25} /></span>
        <span className="login-kicker">{p('form.kicker')}</span>
        <h2>{p('form.title')}</h2>
        <p>{p('form.subtitle')}</p>
      </div>
      <form
        onSubmit={submit}
        noValidate
        aria-busy={formBusy}
        onInput={() => setMessage('')}
        onAnimationStart={(event) => {
          if (!(event.target instanceof HTMLInputElement)) return
          if (event.animationName !== AUTOFILL_ANIMATION) return
          setMessage('')
        }}
      >
        <label className="field-label" htmlFor="identity">{p('form.emailLabel')}</label>
        <div className="login-field">
          <UserRound />
          <input id="identity" name="identity" autoComplete="username" required aria-describedby={message ? 'login-message' : undefined} placeholder={p('form.emailPlaceholder')} />
        </div>
        <label className="field-label" htmlFor="password">{p('form.passwordLabel')}</label>
        <div className="login-field">
          <LockKeyhole />
          <AsciiPasswordInput
            field={passwordField}
            id="password"
            name="password"
            autoComplete="current-password"
            required
            visible={passwordVisible}
            aria-describedby={message ? 'login-message' : undefined}
            placeholder={p('form.passwordPlaceholder')}
          />
          <button type="button" onClick={() => setPasswordVisible((v) => !v)} aria-pressed={passwordVisible} aria-label={passwordVisible ? p('form.hidePassword') : p('form.showPassword')}>
            {passwordVisible ? <EyeOff /> : <Eye />}
          </button>
        </div>
        <PasswordInputHints asciiWarning={passwordField.asciiWarning} capsLockOn={passwordField.capsLockOn} className="password-field-hint" />
        <a className="forgot" href="/forgot-password">{p('form.forgotPassword')}</a>
        <button className="login-submit" type="submit" disabled={formBusy}>
          {submitting ? <><LoaderCircle className="login-spinner" size={19} /> {p('form.submitting')}</> : <>{p('form.submit')} <ArrowRight size={19} /></>}
        </button>
        {message && <p id="login-message" className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>}
      </form>
      <div className="login-divider"><span />{p('dividerSocial')}<span /></div>
      <SocialLoginButtons
        i18nPrefix="loginPage"
        disabled={submitting}
        onMessage={setMessage}
        onBusyChange={setSocialBusy}
      />
      <p className="login-security"><ShieldCheck size={15} /> {p('securityNote')}</p>
      <p className="signup-line">{p('noAccountPrompt')} <a href="/signup">{p('signupLink')}</a></p>
    </AuthSplitLayout>
  )
}
