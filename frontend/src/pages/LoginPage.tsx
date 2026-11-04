import { useState } from 'react'
import {
  ArrowRight, CircleAlert, Eye, EyeOff, LoaderCircle, LockKeyhole, ShieldCheck, ShoppingBag, UserRound,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import AuthSplitLayout from '../components/auth/AuthSplitLayout'
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
  const passwordField = useAsciiPasswordInput()
  const { login } = useAuth()

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const identity = String(form.get('identity') ?? '').trim()
    const password = String(form.get('password') ?? '')
    if (submitting) return
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

  const socialLogin = (provider: string) => setMessage(p('form.socialUnavailable', { provider }))

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
        aria-busy={submitting}
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
        <button className="login-submit" type="submit" disabled={submitting}>
          {submitting ? <><LoaderCircle className="login-spinner" size={19} /> {p('form.submitting')}</> : <>{p('form.submit')} <ArrowRight size={19} /></>}
        </button>
        {message && <p id="login-message" className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>}
      </form>
      <div className="login-divider"><span />{p('dividerSocial')}<span /></div>
      <div className="social-logins">
        <button type="button" disabled={submitting} onClick={() => socialLogin(p('socialGoogle'))}><i className="google-g">G</i>{p('socialGoogle')}</button>
        <button type="button" disabled={submitting} onClick={() => socialLogin(p('socialFacebook'))}><i className="facebook-f">f</i>{p('socialFacebook')}</button>
        <button type="button" disabled={submitting} onClick={() => socialLogin(p('socialApple'))}>
          <svg className="apple-mark" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path fill="currentColor" d="M17.1 12.5c0-2 1.6-3 1.7-3.1-1-1.4-2.5-1.6-3-1.6-1.3-.1-2.5.8-3.2.8-.6 0-1.6-.8-2.7-.7-1.4 0-2.7.8-3.4 2-1.5 2.5-.4 6.3 1 8.3.6 1 1.4 2 2.5 1.9 1-.1 1.4-.6 2.7-.6 1.2 0 1.6.6 2.7.6 1.1 0 1.8-1 2.4-1.9.8-1.1 1.1-2.2 1.2-2.3-.1 0-2.3-.9-2.3-3.4ZM15 6.4c.5-.7 1-1.7.9-2.7-.9 0-2 .6-2.6 1.3-.5.6-1 1.6-.9 2.5 1 .1 1.9-.5 2.6-1.1Z" /></svg>
          {p('socialApple')}
        </button>
      </div>
      <p className="login-security"><ShieldCheck size={15} /> {p('securityNote')}</p>
      <p className="signup-line">{p('noAccountPrompt')} <a href="/signup">{p('signupLink')}</a></p>
    </AuthSplitLayout>
  )
}
