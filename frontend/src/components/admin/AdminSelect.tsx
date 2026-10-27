import SearchableSelect from '../SearchableSelect'
import { labelOf, uniqueOptions, valueOf, type SelectOpt } from '../merchant/selectOptions'

type AdminSelectProps = {
  label: string
  value: string
  options: SelectOpt[]
  onChange: (value: string) => void
  placeholder?: string
  disabled?: boolean
  compact?: boolean
  required?: boolean
  className?: string
}

/**
 * Value-based wrapper around the shared SearchableSelect (same control as /merchant/orders) for admin pages.
 * Options are { value, label }; labels are made unique so a picked label maps back to exactly one value.
 * `required` keeps native form validation through a visually hidden input that mirrors the value.
 */
export default function AdminSelect({ label, value, options, onChange, placeholder, disabled, compact, required, className }: AdminSelectProps) {
  const opts = uniqueOptions(options)
  return (
    <div className={['adm-select', className].filter(Boolean).join(' ')}>
      <SearchableSelect
        label={label}
        required={required}
        compact={compact}
        disabled={disabled}
        placeholder={placeholder}
        options={opts.map(opt => opt.label)}
        value={labelOf(opts, value, '')}
        onChange={picked => {
          const next = valueOf(opts, picked)
          if (next !== value) onChange(next)
        }}
      />
      {required && <input className="adm-select-required" tabIndex={-1} aria-hidden="true" required value={value} onChange={() => undefined} />}
    </div>
  )
}
