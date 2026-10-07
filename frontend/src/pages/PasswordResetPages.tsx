import { useState } from 'react'
import { ArrowLeft, ArrowRight, CheckCircle2, CircleAlert, Eye, EyeOff, LoaderCircle, LockKeyhole, Mail, ShoppingBag } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import LanguageSwitcher from '../components/LanguageSwitcher'
import { ApiError, authApi } from '../services/api'
import '../styles/login.css'
import '../styles/password-reset.css'

function ResetBrand() {
  const { t } = useTranslation('auth')
  return (
    <a className="brand login-brand" href="/" aria-label={t('passwordResetPage.brandAriaHome')}>
      <span className="brand-mark"><ShoppingBag size={24} /><i>✓</i></span>
      <span><b>ThaiShop<span>Fun</span></b><small>{t('passwordResetPage.brandTagline')}</small></span>
    </a>
  )
}

export default function PasswordResetPages() {
  const { t } = useTranslation('auth')
  const p = (key: string) => t(`passwordResetPage.${key}`)
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const isReset = location.pathname === '/reset-password'
  const token = searchParams.get('token')?.trim() ?? ''
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [sent, setSent] = useState(false)
  const [completed, setCompleted] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const passwordValid = password.length >= 8 && /[A-Za-z]/.test(password) && /\d/.test(password)

  const submitEmail = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.trim())) {
      setMessage(p('invalidEmail'))
      return
    }
    setSubmitting(true)
    setMessage('')
    try {
      await authApi.forgotPassword(email.trim())
      setSent(true)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : p('sendFailed'))
    } finally {
      setSubmitting(false)
    }
  }

  const submitNewPassword = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    if (!token) {
      setMessage(p('invalidLink'))
      return
    }
    if (!passwordValid) {
      setMessage(p('passwordRules'))
      return
    }
    if (password !== confirmPassword) {
      setMessage(p('passwordMismatch'))
      return
    }
    setSubmitting(true)
    setMessage('')
    try {
      await authApi.resetPassword(token, password)
      setSuccessMessage(p('resetSuccess'))
      setCompleted(true)
    } catch (error) {
      setMessage(error instanceof ApiError && error.code === 'INVALID_RESET_TOKEN' ? p('invalidLink') : error instanceof Error ? error.message : p('resetFailed'))
    } finally {
      setSubmitting(false)
    }
  }

  const heading = completed ? 'successTitle' : sent ? 'sentTitle' : isReset ? 'resetTitle' : 'forgotTitle'
  const subtitle = completed ? 'successSubtitle' : sent ? 'sentSubtitle' : isReset ? 'resetSubtitle' : 'forgotSubtitle'

  return (
    <main className="login-page password-reset-page">
      <section className="login-story">
        <div className="login-story-image" />
        <div className="login-story-content">
          <ResetBrand />
        </div>
      </section>
      <section className="login-form-side">
        <nav className="login-top-nav" aria-label={p('navAria')}>
          <Link to="/login"><ArrowLeft size={16} /> {p('backToLogin')}</Link>
          <div className="login-nav-tools"><LanguageSwitcher /><a href="/help">{t('help')}</a></div>
        </nav>
        <div className="login-card">
          <div className="login-title">
            <span className="login-welcome-icon">{completed ? <CheckCircle2 size={25} /> : isReset ? <LockKeyhole size={25} /> : <Mail size={25} />}</span>
            <span className="login-kicker">{p('kicker')}</span>
            <h2>{p(heading)}</h2>
            <p>{p(subtitle)}</p>
          </div>

          {!isReset && !sent && <form onSubmit={submitEmail} noValidate aria-busy={submitting}>
            <label className="field-label" htmlFor="reset-email">{p('emailLabel')}</label>
            <div className="login-field">
              <Mail />
              <input id="reset-email" name="email" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder={p('emailPlaceholder')} />
            </div>
            <button className="login-submit" type="submit" disabled={submitting || !email.trim()}>
              {submitting ? <><LoaderCircle className="login-spinner" size={19} /> {p('sending')}</> : <>{p('sendEmail')} <ArrowRight size={19} /></>}
            </button>
          </form>}

          {isReset && !completed && token && <form onSubmit={submitNewPassword} noValidate aria-busy={submitting}>
            <label className="field-label" htmlFor="new-password">{p('newPasswordLabel')}</label>
            <div className="login-field">
              <LockKeyhole />
              <input id="new-password" name="newPassword" type={showPassword ? 'text' : 'password'} autoComplete="new-password" required value={password} onChange={(event) => setPassword(event.target.value)} placeholder={p('newPasswordPlaceholder')} />
              <button type="button" onClick={() => setShowPassword((visible) => !visible)} aria-pressed={showPassword} aria-label={showPassword ? p('hidePassword') : p('showPassword')}>
                {showPassword ? <EyeOff /> : <Eye />}
              </button>
            </div>
            <p className={`password-reset-rules${passwordValid ? ' is-valid' : ''}`}>{p('passwordRules')}</p>
            <label className="field-label" htmlFor="confirm-password">{p('confirmPasswordLabel')}</label>
            <div className="login-field">
              <LockKeyhole />
              <input id="confirm-password" name="confirmPassword" type={showConfirmPassword ? 'text' : 'password'} autoComplete="new-password" required value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} placeholder={p('confirmPasswordPlaceholder')} />
              <button type="button" onClick={() => setShowConfirmPassword((visible) => !visible)} aria-pressed={showConfirmPassword} aria-label={showConfirmPassword ? p('hidePassword') : p('showPassword')}>
                {showConfirmPassword ? <EyeOff /> : <Eye />}
              </button>
            </div>
            <button className="login-submit" type="submit" disabled={submitting || !password || !confirmPassword}>
              {submitting ? <><LoaderCircle className="login-spinner" size={19} /> {p('resetting')}</> : <>{p('resetPassword')} <ArrowRight size={19} /></>}
            </button>
          </form>}

          {sent && <div className="password-reset-confirmation" role="status"><CheckCircle2 size={22} /><p>{p('sentNotice')}</p></div>}
          {completed && <div className="password-reset-confirmation" role="status"><CheckCircle2 size={22} /><p>{successMessage}</p></div>}
          {message && <p className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>}
          {(sent || completed || (!isReset && !sent)) && <p className="password-reset-footer"><Link to="/login">{completed ? p('loginNow') : p('backToLogin')}</Link></p>}
          {isReset && !token && !message && <p className="login-message" role="alert"><CircleAlert size={18} /><span>{p('invalidLink')}</span></p>}
        </div>
        <footer className="login-footer"><p>{p('footer')}</p></footer>
      </section>
    </main>
  )
}
