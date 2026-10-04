import { ArrowLeft, Headphones } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { MerchantOnboardingCopy } from '../../i18n/merchantOnboardingCopy'

type StepState = 'done' | 'active' | ''

type Props = {
  copy: MerchantOnboardingCopy
  activeStep: number
  className?: string
}

export default function MerchantOnboardingSidebar({ copy, activeStep, className = 'doc-sidebar' }: Props) {
  const c = copy.common
  return (
    <aside className={className}>
      <Link className="doc-back-home" to="/">
        <ArrowLeft />
        {c.backHome}
      </Link>
      <h2>{c.applyTitle}</h2>
      <p>
        {c.applyLead.split('\n').map((line, index) => (
          <span key={index}>
            {line}
            {index === 0 && c.applyLead.includes('\n') && <br />}
          </span>
        ))}
      </p>
      <div className="doc-flow">
        {copy.steps.map((step, index) => {
          const state: StepState = index < activeStep ? 'done' : index === activeStep ? 'active' : ''
          const number = state === 'done' ? '✓' : String(index + 1)
          return (
            <div className={state} key={step.title}>
              <span>{number}</span>
              <p>
                <b>{step.title}</b>
                <small>{step.text}</small>
              </p>
            </div>
          )
        })}
      </div>
      <div className="doc-help">
        <Headphones />
        <p>
          <b>{c.helpTitle}</b>
          <span>{c.helpText}</span>
          <a href="#contact">{c.contact}</a>
        </p>
      </div>
      <blockquote>
        {c.quote.split('\n').map((line, index) => (
          <span key={index}>
            {line}
            {index === 0 && c.quote.includes('\n') && <br />}
          </span>
        ))}{' '}
        <i>♡</i>
        <span />
      </blockquote>
    </aside>
  )
}
