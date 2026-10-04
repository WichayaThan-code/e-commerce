import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, ArrowRight, Check, CircleAlert, FileText, LoaderCircle, RotateCcw, ShieldCheck, X } from 'lucide-react'

export type AgreementStep = 'terms' | 'privacy'

type AgreementSection = { title: string; body: string }

export default function SignupAgreement({ initialStep, busy, error, onClose, onAccept }: {
  initialStep: AgreementStep
  busy: boolean
  error: string
  onClose: () => void
  onAccept: () => void
}) {
  const { t } = useTranslation('auth')
  const [step, setStep] = useState<AgreementStep>(initialStep)
  const dialog = useRef<HTMLDialogElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const body = useRef<HTMLDivElement>(null)
  const alert = useRef<HTMLParagraphElement>(null)
  const close = () => { if (!busy) onClose() }

  const sectionsKey = step === 'terms' ? 'signupAgreement.termsSections' : 'signupAgreement.privacySections'
  const sections = t(sectionsKey, { returnObjects: true }) as AgreementSection[]

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    dialog.current?.showModal()
    document.body.style.overflow = 'hidden'
    return () => {
      dialog.current?.close()
      document.body.style.overflow = overflow
      previous?.focus()
    }
  }, [])
  useEffect(() => {
    body.current?.scrollTo(0, 0)
    heading.current?.focus()
  }, [step])
  useEffect(() => { if (error) alert.current?.focus() }, [error])

  const title = step === 'terms' ? t('signupAgreement.termsTitle') : t('signupAgreement.privacyTitle')
  const intro = step === 'terms' ? t('signupAgreement.introTerms') : t('signupAgreement.introPrivacy')
  const footerHint = step === 'terms' ? t('signupAgreement.footerNextTerms') : t('signupAgreement.footerAccept')

  const secondaryLabel = error
    ? t('signupAgreement.btnEdit')
    : step === 'privacy'
      ? t('signupAgreement.btnBack')
      : t('signupAgreement.btnLater')

  return <dialog ref={dialog} className="agreement-dialog" aria-busy={busy} aria-labelledby="agreement-title" aria-describedby="agreement-intro" onCancel={event => { event.preventDefault(); close() }}>
    <div className="agreement-header">
      <span className="agreement-icon">{step === 'terms' ? <FileText/> : <ShieldCheck/>}</span>
      <div><span className="agreement-eyebrow">{t('signupAgreement.eyebrow')}</span><h2 ref={heading} tabIndex={-1} id="agreement-title">{title}</h2></div>
      <button type="button" className="agreement-close" aria-label={t('signupAgreement.closeAria')} disabled={busy} onClick={close}><X size={20}/></button>
    </div>
    <ol className="agreement-steps" aria-label={t('signupAgreement.stepsAria')}>
      <li className={step === 'terms' ? 'current' : 'done'} aria-current={step === 'terms' ? 'step' : undefined}><span>{step === 'privacy' ? <Check size={14}/> : '1'}</span>{t('signupAgreement.stepTerms')}</li>
      <li className={step === 'privacy' ? 'current' : ''} aria-current={step === 'privacy' ? 'step' : undefined}><span>2</span>{t('signupAgreement.stepPrivacy')}</li>
    </ol>
    <div className="agreement-body" ref={body} tabIndex={0}>
      <p id="agreement-intro" className="agreement-intro">{intro}</p>
      {sections.map(({ title: sectionTitle, body: sectionBody }, index) => (
        <section key={sectionTitle}><h3><span>{String(index + 1).padStart(2, '0')}</span>{sectionTitle}</h3><p>{sectionBody}</p></section>
      ))}
      <a className="agreement-help" href="/help" target="_blank" rel="noopener noreferrer">{t('signupAgreement.helpLink')} <ArrowRight size={14}/></a>
    </div>
    <div className="agreement-footer">
      {error && <p className="agreement-alert" ref={alert} tabIndex={-1} role="alert"><CircleAlert size={16} aria-hidden="true"/><span>{error}{t('signupAgreement.errorSuffix')}</span></p>}
      <p>{footerHint}</p>
      <div><button className="agreement-secondary" type="button" disabled={busy} onClick={() => step === 'privacy' && !error ? setStep('terms') : close()}><ArrowLeft size={16}/>{secondaryLabel}</button>
        <button className="login-submit" type="button" disabled={busy} onClick={() => step === 'terms' ? setStep('privacy') : onAccept()}>
          {step === 'terms' ? <>{t('signupAgreement.btnNext')} <ArrowRight size={17}/></>
            : busy ? <><LoaderCircle className="login-spinner" size={18}/> {t('signupAgreement.btnCreating')}</>
            : error ? <><RotateCcw size={17}/> {t('signupAgreement.btnRetry')}</>
            : <><Check size={18}/> {t('signupAgreement.btnAcceptCreate')}</>}
        </button></div>
    </div>
  </dialog>
}
