import { Check } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useLanguage } from '../../contexts/LanguageContext'
import '../../styles/register-stepper.css'

export type RegisterStep = 1 | 2 | 3 | 4

export const MERCHANT_STEP_HREF: Record<RegisterStep, string> = {
  1: '/merchant/register',
  2: '/merchant/owner-info',
  3: '/merchant/documents',
  4: '/merchant/review',
}

type Props = {
  currentStep: RegisterStep
}

/** On-card 1–4 stepper shared by merchant apply pages. */
export default function RegisterStepper({ currentStep }: Props) {
  const { onboarding: ob } = useLanguage()
  const label = ob.common.stepsNavLabel

  return (
    <ol className="merchant-steps" aria-label={label}>
      {ob.steps.map((step, index) => {
        const number = (index + 1) as RegisterStep
        const done = number < currentStep
        const active = number === currentStep
        const className = done ? 'done' : active ? 'active' : ''
        const marker = (
          <>
            <span aria-hidden="true">{done ? <Check strokeWidth={3} /> : number}</span>
            <small>{step.title}</small>
          </>
        )
        return (
          <li
            key={step.title}
            className={className}
            aria-current={active ? 'step' : undefined}
          >
            {done ? (
              <Link to={MERCHANT_STEP_HREF[number]} aria-label={step.title}>
                {marker}
              </Link>
            ) : (
              marker
            )}
          </li>
        )
      })}
    </ol>
  )
}
