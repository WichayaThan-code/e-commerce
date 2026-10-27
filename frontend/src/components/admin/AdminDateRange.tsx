import { useTranslation } from 'react-i18next'
import DateRangePicker from '../form/DateRangePicker'

type AdminDateRangeProps = {
  label?: string
  from: string
  to: string
  onChange: (value: { from: string; to: string }) => void
  className?: string
}

/** Date range filter for admin pages: the shared DateRangePicker used by /merchant/orders, with admin translations. */
export default function AdminDateRange({ label, from, to, onChange, className }: AdminDateRangeProps) {
  const { t } = useTranslation('admin')
  return (
    <DateRangePicker
      className={['adm-date-range', className].filter(Boolean).join(' ')}
      label={label ?? t('adminPlatform.common.dateRange')}
      from={from}
      to={to}
      clearLabel={t('adminPlatform.common.clear')}
      applyLabel={t('adminPlatform.common.apply')}
      presetsLabel={{
        d7: t('adminPlatform.common.presets.d7'),
        d30: t('adminPlatform.common.presets.d30'),
        month: t('adminPlatform.common.presets.month'),
      }}
      onChange={onChange}
    />
  )
}
