import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import ConfirmDialog from '../form/ConfirmDialog'
import '../../styles/admin-platform.css'

export const REASON_MIN = 5
export const REASON_MAX = 1000

export const isValidReason = (value: string) => {
  const length = value.trim().length
  return length >= REASON_MIN && length <= REASON_MAX
}

type ReasonDialogProps = {
  title: string
  message: string
  confirmLabel?: ReactNode
  danger?: boolean
  busy?: boolean
  error?: string
  /** Extra form controls rendered under the reason box (tracking number, restock, amount ...). */
  children?: ReactNode
  /** Extra condition that must hold before the confirm button is enabled. */
  extraValid?: boolean
  onCancel: () => void
  onConfirm: (reason: string) => void
}

/**
 * ConfirmDialog with a mandatory reason textarea (5–1000 characters). Used for every destructive platform action
 * (suspend / reactivate / order intervention); the backend validates the same bounds.
 */
export default function ReasonDialog({
  title, message, confirmLabel, danger = true, busy = false, error = '', children, extraValid = true, onCancel, onConfirm,
}: ReasonDialogProps) {
  const { t } = useTranslation('admin')
  const [reason, setReason] = useState('')
  const valid = isValidReason(reason)
  const touched = reason.length > 0

  return (
    <ConfirmDialog
      title={title}
      message={message}
      cancelLabel={t('confirm.cancel')}
      confirmLabel={confirmLabel ?? t('confirm.confirm')}
      danger={danger}
      confirmDisabled={busy || !valid || !extraValid}
      onCancel={onCancel}
      onConfirm={() => { if (valid && extraValid && !busy) onConfirm(reason.trim()) }}
    >
      <div className="ap-reason">
        <label>
          <span>{t('adminPlatform.reason.label')}</span>
          <textarea
            value={reason}
            onChange={event => setReason(event.target.value)}
            minLength={REASON_MIN}
            maxLength={REASON_MAX}
            rows={4}
            required
            aria-invalid={touched && !valid}
            placeholder={t('adminPlatform.reason.placeholder')}
          />
        </label>
        <small className={touched && !valid ? 'invalid' : ''}>
          <span>{t('adminPlatform.reason.hint', { min: REASON_MIN, max: REASON_MAX })}</span>
          <span className="num">{reason.trim().length}/{REASON_MAX}</span>
        </small>
        {children}
        {error && <p className="ap-error" role="alert">{error}</p>}
      </div>
    </ConfirmDialog>
  )
}
