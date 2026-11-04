import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import FieldError from './FieldError'

type FormFieldProps = {
  label: ReactNode
  icon?: LucideIcon
  error?: string
  errorId?: string
  /** Right-side meta (e.g. character count) on the same row as FieldError. */
  aside?: ReactNode
  className?: string
  multiline?: boolean
  children: ReactNode
}

export default function FormField({ label, icon: Icon, error, errorId, aside, className, multiline, children }: FormFieldProps) {
  const classes = ['form-field', className].filter(Boolean).join(' ')
  const controlClasses = [
    'form-control',
    multiline ? 'form-control-area' : '',
    error ? 'is-invalid' : '',
  ]
    .filter(Boolean)
    .join(' ')
  const showMeta = Boolean(error || aside)
  return (
    <label className={classes}>
      <span className="form-label">{label}</span>
      <span className={controlClasses}>
        {Icon ? <Icon aria-hidden="true" /> : null}
        {children}
      </span>
      {showMeta ? (
        <span className="form-field-meta">
          <FieldError text={error} id={errorId} />
          {aside ? <span className="field-meta-aside">{aside}</span> : null}
        </span>
      ) : null}
    </label>
  )
}

/** @deprecated Prefer `import FieldError from '../form/FieldError'`. Kept for existing imports. */
export { default as FieldError } from './FieldError'
