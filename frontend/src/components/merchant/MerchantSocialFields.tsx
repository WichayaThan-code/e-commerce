import { Facebook, Globe2, Instagram, Link2, MessageCircle } from 'lucide-react'
import type { MerchantOnboardingCopy } from '../../i18n/merchantOnboardingCopy'

export type ShopSocialDraft = {
  website: string
  lineId: string
  facebookUrl: string
  instagramUrl: string
  tiktokUrl: string
}

type Props = {
  copy: MerchantOnboardingCopy['social']
  value: ShopSocialDraft
  onChange: (next: ShopSocialDraft) => void
}

const fields: {
  key: keyof ShopSocialDraft
  icon: typeof Globe2
  labelKey: keyof MerchantOnboardingCopy['social']
  phKey: keyof MerchantOnboardingCopy['social']
  className?: string
}[] = [
  { key: 'website', icon: Globe2, labelKey: 'website', phKey: 'websitePh' },
  { key: 'lineId', icon: MessageCircle, labelKey: 'line', phKey: 'linePh', className: 'social-line' },
  { key: 'facebookUrl', icon: Facebook, labelKey: 'facebook', phKey: 'facebookPh', className: 'social-facebook' },
  { key: 'instagramUrl', icon: Instagram, labelKey: 'instagram', phKey: 'instagramPh', className: 'social-instagram' },
  { key: 'tiktokUrl', icon: Link2, labelKey: 'tiktok', phKey: 'tiktokPh', className: 'social-tiktok' },
]

export default function MerchantSocialFields({ copy, value, onChange }: Props) {
  return (
    <fieldset className="merchant-social-section">
      <legend>{copy.sectionTitle}</legend>
      <p className="merchant-social-hint">{copy.sectionHint}</p>
      <div className="merchant-social-grid">
        {fields.map(({ key, icon: Icon, labelKey, phKey, className }) => (
          <label key={key} className={className}>
            <span>{copy[labelKey]}</span>
            <div className="merchant-icon-input">
              <Icon aria-hidden />
              <input
                name={key}
                type="text"
                inputMode="text"
                autoComplete="off"
                placeholder={copy[phKey]}
                value={value[key]}
                onChange={(event) => onChange({ ...value, [key]: event.target.value })}
              />
            </div>
          </label>
        ))}
      </div>
    </fieldset>
  )
}
