import type { ReactNode } from 'react'
import '../../styles/merchant-onboarding-layout.css'
import OnboardingProgress from './OnboardingProgress'

type LayoutProps = {
  children: ReactNode
  aside: ReactNode
  className?: string
}

/** 3-column shell: left rail | original form card | right aside. */
export default function MerchantOnboardingLayout({ children, aside, className }: LayoutProps) {
  const classes = ['onboard-layout', className].filter(Boolean).join(' ')
  return (
    <div className={classes}>
      <div className="onb-left-col">
        <div className="onb-left-sticky">
          <OnboardingProgress />
        </div>
      </div>
      <div className="onboard-main">{children}</div>
      <div className="onb-right-col">{aside}</div>
    </div>
  )
}
