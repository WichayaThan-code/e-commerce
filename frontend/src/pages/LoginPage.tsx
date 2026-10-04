import { useState } from 'react'
import {
  ArrowLeft, ArrowRight, CircleAlert, Eye, EyeOff, Heart, LoaderCircle, LockKeyhole, ShieldCheck,
  ShoppingBag, Store, Truck, UserRound,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import '../styles/login.css'
import { useAuth } from '../contexts/AuthContext'
import LanguageSwitcher from '../components/LanguageSwitcher'

function LoginBrand({ ariaLabel, tagline }: { ariaLabel: string; tagline: string }) {
  return (
    <a className="brand login-brand" href="/" aria-label={ariaLabel}>
      <span className="brand-mark"><ShoppingBag size={24} /><i>✓</i></span>
      <span><b>ThaiShop<span>Fun</span></b><small>{tagline}</small></span>
    </a>
  )
}

const benefitKeys = ['variety', 'payment', 'support'] as const
const benefitIcons = { variety: Truck, payment: ShieldCheck, support: Heart }

export default function LoginPage() {
  const { t } = useTranslation('auth')
  const p = (key: string, options?: Record<string, unknown>) => t(`loginPage.${key}`, options)
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
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
      const next = new URLSearchParams(window.location.search).get('next')
      const roleDestination = authenticated.role === 'ADMIN' ? '/admin' : authenticated.role === 'MERCHANT' ? '/merchant/dashboard' : '/'
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
    <main className="login-page">
      <section className="login-story">
        <div className="login-story-image" />
        <div className="login-story-content">
          <LoginBrand ariaLabel={p('brandAriaHome')} tagline={p('brandTagline')} />
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
            {benefitKeys.map((key) => {
              const Icon = benefitIcons[key]
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

      <section className="login-form-side">
        <nav className="login-top-nav" aria-label={p('navAria')}>
          <a href="/"><ArrowLeft size={16} /> {t('back')}</a>
          <div className="login-nav-tools"><LanguageSwitcher /><a href="/help">{t('help')}</a></div>
        </nav>
        <div className="login-card">
          <div className="login-title">
            <span className="login-welcome-icon"><ShoppingBag size={25} /></span>
            <span className="login-kicker">{p('form.kicker')}</span>
            <h2>{p('form.title')}</h2>
            <p>{p('form.subtitle')}</p>
          </div>
          <form onSubmit={submit} noValidate aria-busy={submitting}>
            <label className="field-label" htmlFor="identity">{p('form.emailLabel')}</label>
            <div className="login-field">
              <UserRound />
              <input id="identity" name="identity" autoComplete="username" required aria-describedby={message ? 'login-message' : undefined} placeholder={p('form.emailPlaceholder')} />
            </div>
            <label className="field-label" htmlFor="password">{p('form.passwordLabel')}</label>
            <div className="login-field">
              <LockKeyhole />
              <input id="password" name="password" type={passwordVisible ? 'text' : 'password'} autoComplete="current-password" required aria-describedby={message ? 'login-message' : undefined} placeholder={p('form.passwordPlaceholder')} />
              <button type="button" onClick={() => setPasswordVisible((v) => !v)} aria-pressed={passwordVisible} aria-label={passwordVisible ? p('form.hidePassword') : p('form.showPassword')}>
                {passwordVisible ? <EyeOff /> : <Eye />}
              </button>
            </div>
            <button className="forgot" type="button" onClick={() => setMessage(p('form.forgotPasswordMessage'))}>{p('form.forgotPassword')}</button>
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
        </div>
        <div className="login-merchant">
          <Store size={20} />
          <span>{p('merchantPrompt')} <a href="/merchant/register">{p('merchantLink')} <ArrowRight size={14} /></a></span>
        </div>
        <footer className="login-footer"><p>{p('footer')}</p></footer>
      </section>
    </main>
  )
}
