import type { ReactNode } from 'react'
import { ArrowLeft, ArrowRight, Heart, ShieldCheck, Store, Truck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import LanguageSwitcher from '../LanguageSwitcher'
import SiteBrand from '../SiteBrand'
import '../../styles/login.css'
import '../../styles/form-invalid.css'

const benefitKeys = ['variety', 'payment', 'support'] as const
const benefitIcons = { variety: Truck, payment: ShieldCheck, support: Heart }

type AuthSplitLayoutProps = {
  navAriaLabel: string
  children: ReactNode
}

/** Shared /login split shell (story + form column). Card body is supplied by the page. */
export default function AuthSplitLayout({ navAriaLabel, children }: AuthSplitLayoutProps) {
  const { t } = useTranslation('auth')
  const p = (key: string, options?: Record<string, unknown>) => t(`loginPage.${key}`, options)

  return (
    <main className="login-page">
      <section className="login-story">
        <div className="login-story-image" />
        <div className="login-story-content">
          <SiteBrand homeAria={p('brandAriaHome')} className="login-brand" href="/" />
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
        <nav className="login-top-nav" aria-label={navAriaLabel}>
          <a href="/"><ArrowLeft size={16} /> {t('back')}</a>
          <div className="login-nav-tools"><LanguageSwitcher /><a href="/help">{t('help')}</a></div>
        </nav>
        <div className="login-card">
          {children}
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
