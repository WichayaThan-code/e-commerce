import { Facebook, Globe2, Instagram, Link2, MessageCircle } from 'lucide-react'
import FormField from '../form/FormField'
import FormGrid from '../form/FormGrid'
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
  errors?: Record<string, string>
  onBlur?: (key: keyof ShopSocialDraft) => void
}

const fields: {
  key: keyof ShopSocialDraft
  icon: typeof Globe2
  labelKey: keyof MerchantOnboardingCopy['social']
  phKey: keyof MerchantOnboardingCopy['social']
}[] = [
  { key: 'website', icon: Globe2, labelKey: 'website', phKey: 'websitePh' },
  { key: 'lineId', icon: MessageCircle, labelKey: 'line', phKey: 'linePh' },
  { key: 'facebookUrl', icon: Facebook, labelKey: 'facebook', phKey: 'facebookPh' },
  { key: 'instagramUrl', icon: Instagram, labelKey: 'instagram', phKey: 'instagramPh' },
  { key: 'tiktokUrl', icon: Link2, labelKey: 'tiktok', phKey: 'tiktokPh' },
]

export default function MerchantSocialFields({ copy, value, onChange, errors, onBlur }: Props) {
  return (
    <fieldset className="merchant-social-section">
      <legend>{copy.sectionTitle}</legend>
      <p className="merchant-social-hint">{copy.sectionHint}</p>
      <FormGrid>
        {fields.map(({ key, icon, labelKey, phKey }) => (
          <FormField key={key} className="flush" label={copy[labelKey]} icon={icon} error={errors?.[key]}>
            <input
              name={key}
              type="text"
              inputMode="text"
              autoComplete="off"
              placeholder={copy[phKey]}
              value={value[key]}
              aria-invalid={Boolean(errors?.[key])}
              onChange={(event) => onChange({ ...value, [key]: event.target.value })}
              onBlur={() => onBlur?.(key)}
            />
          </FormField>
        ))}
      </FormGrid>
    </fieldset>
  )
}
