import { CircleAlert } from 'lucide-react'

type FieldErrorProps = {
  text?: string
  id?: string
}

/** Inline field error matching SignupPage (`signup-field-error` + CircleAlert). */
export default function FieldError({ text, id }: FieldErrorProps) {
  if (!text) return null
  return (
    <span className="signup-field-error" id={id} role="alert">
      <CircleAlert size={14} aria-hidden="true" />
      {text}
    </span>
  )
}
