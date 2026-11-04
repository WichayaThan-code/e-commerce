import {
  Check,
  FileCheck,
  FileType,
  HardDrive,
  Headphones,
  Image,
  Lock,
  MessageCircle,
  ShieldCheck,
} from 'lucide-react'
import { useLanguage } from '../../contexts/LanguageContext'

export type OnboardingAsideTips = {
  title: string
  items: string[]
}

export type OnboardingAsideHelp = {
  title: string
  lead: string
  contactLabel: string
  href?: string
}

export type OnboardingAsideSafe = {
  title: string
  items: string[]
}

type Props = {
  safe: OnboardingAsideSafe
  tips: OnboardingAsideTips
  help: OnboardingAsideHelp
  trustLabel: string
  safeSummaryLabel: string
}

const TIP_ICONS = [FileCheck, Image, HardDrive, FileType] as const

function SafeList({ items }: { items: string[] }) {
  return (
    <ul className="onb-check">
      {items.map((line) => (
        <li key={line}>
          <span className="onb-check-icon" aria-hidden="true">
            <Check strokeWidth={3} />
          </span>
          <span>{line}</span>
        </li>
      ))}
    </ul>
  )
}

/** Right-rail confidence / help card — styles scoped under .onb-right only. */
export default function OnboardingAside({ safe, tips, help, trustLabel, safeSummaryLabel }: Props) {
  const { onboarding: ob } = useLanguage()
  const contactHref = help.href ?? '#contact'
  const safeLead = ob.common.safeLead

  return (
    <aside className="onb-right">
      <details className="onb-safe-mobile">
        <summary>
          <span className="onb-icon" aria-hidden="true">
            <ShieldCheck />
          </span>
          {safeSummaryLabel}
        </summary>
        <SafeList items={safe.items} />
      </details>

      <div className="onb-right-banner onb-desktop-safe">
        <span className="onb-right-banner-icon" aria-hidden="true">
          <ShieldCheck />
        </span>
        <div className="onb-right-banner-copy">
          <h3>{safe.title}</h3>
          <p>{safeLead}</p>
        </div>
      </div>

      <section className="onb-section onb-desktop-safe onb-safe-body">
        <SafeList items={safe.items} />
      </section>

      <section className="onb-section onb-mobile-extra">
        <div className="onb-section-head">
          <h3>{tips.title}</h3>
        </div>
        <ul className="onb-tips">
          {tips.items.map((line, index) => {
            const Icon = TIP_ICONS[index % TIP_ICONS.length]
            return (
              <li key={line}>
                <Icon aria-hidden="true" />
                <span>{line}</span>
              </li>
            )
          })}
        </ul>
      </section>

      <section className="onb-section onb-help">
        <div className="onb-help-box">
          <div className="onb-help-head">
            <Headphones aria-hidden="true" />
            <h3>{help.title}</h3>
          </div>
          <p>{help.lead}</p>
          <a className="onb-contact" href={contactHref}>
            <MessageCircle aria-hidden="true" />
            {help.contactLabel}
          </a>
        </div>
      </section>

      <div className="onb-trust">
        <Lock aria-hidden="true" />
        <span>{trustLabel}</span>
      </div>
    </aside>
  )
}
