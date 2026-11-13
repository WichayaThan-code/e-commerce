import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import ConfirmDialog from '../form/ConfirmDialog'
import AdminSelect from './AdminSelect'
import '../../styles/admin-platform.css'

export const REASON_MIN = 5
export const REASON_MAX = 1000
export const REASON_TEXT_MAX_OPTIONAL = 500

/** Shared suspend reason codes (must match backend pattern A-Z / 0-9 / _). */
export const SUSPEND_REASON_CODES = [
  'ADMIN_SUSPEND',
  'POLICY_VIOLATION',
  'FRAUD',
  'ABUSE',
  'OTHER',
] as const

export const isValidReason = (value: string, min = REASON_MIN, max = REASON_MAX) => {
  const length = value.trim().length
  return length >= min && length <= max
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
  /** When set, show a reason-code dropdown. */
  reasonCodes?: readonly string[] | { value: string; label: string }[]
  /** Suspend: confirm disabled until a code is chosen. */
  requireReasonCode?: boolean
  /** 'cards': reason codes as a radio-chip group instead of a dropdown. */
  reasonCodeStyle?: 'select' | 'cards'
  /** Shown above the reason fields (e.g. what happens on suspend). */
  notice?: ReactNode
  /** When true, free-text reason is optional (0–reasonMax chars). */
  reasonOptional?: boolean
  reasonMax?: number
  reasonMin?: number
  onCancel: () => void
  onConfirm: (reason: string, reasonCode?: string) => void
}

/**
 * ConfirmDialog with a reason textarea. Used for platform suspend / reactivate / order interventions.
 * Optional reason-code dropdown for suspend (bulk and legacy store-application paths).
 */
export default function ReasonDialog({
  title, message, confirmLabel, danger = true, busy = false, error = '', children, extraValid = true,
  reasonCodes, requireReasonCode = false, reasonOptional = false, reasonCodeStyle = 'select', notice,
  reasonMax = reasonOptional ? REASON_TEXT_MAX_OPTIONAL : REASON_MAX,
  reasonMin = reasonOptional ? 0 : REASON_MIN,
  onCancel, onConfirm,
}: ReasonDialogProps) {
  const { t } = useTranslation('admin')
  const [reason, setReason] = useState('')
  const [reasonCode, setReasonCode] = useState('')
  const trimmed = reason.trim()
  const textOk = reasonOptional
    ? trimmed.length <= reasonMax
    : isValidReason(reason, reasonMin, reasonMax)
  const codeOk = !requireReasonCode || reasonCode.length > 0
  const valid = textOk && codeOk
  const touched = reason.length > 0
  const codeOptions = (reasonCodes ?? []).map(item =>
    typeof item === 'string'
      ? { value: item, label: t(`adminPlatform.reason.codes.${item}`, { defaultValue: item }) }
      : item,
  )

  return (
    <ConfirmDialog
      title={title}
      message={message}
      cancelLabel={t('confirm.cancel')}
      confirmLabel={confirmLabel ?? t('confirm.confirm')}
      danger={danger}
      confirmDisabled={busy || !valid || !extraValid}
      onCancel={onCancel}
      onConfirm={() => { if (valid && extraValid && !busy) onConfirm(trimmed, reasonCode || undefined) }}
    >
      <div className="ap-reason">
        {notice ? <div className="ap-reason-notice">{notice}</div> : null}
        {codeOptions.length > 0 && reasonCodeStyle === 'cards' && (
          <fieldset className="ap-reason-codes">
            <legend>
              {t('adminPlatform.reason.codeLabel')}
              {requireReasonCode ? <em aria-hidden="true"> *</em> : null}
            </legend>
            <div className="ap-reason-chips" role="radiogroup" aria-required={requireReasonCode || undefined}>
              {codeOptions.map(option => (
                <label key={option.value} className={reasonCode === option.value ? 'is-checked' : undefined}>
                  <input
                    type="radio"
                    name="ap-reason-code"
                    value={option.value}
                    checked={reasonCode === option.value}
                    onChange={() => setReasonCode(option.value)}
                  />
                  <span>{option.label}</span>
                </label>
              ))}
            </div>
          </fieldset>
        )}
        {codeOptions.length > 0 && reasonCodeStyle === 'select' && (
          <AdminSelect
            label={t('adminPlatform.reason.codeLabel')}
            value={reasonCode}
            onChange={setReasonCode}
            options={[
              { value: '', label: t('adminPlatform.reason.codePlaceholder') },
              ...codeOptions,
            ]}
          />
        )}
        <label>
          {/* With a reason code above, the text box is the details, so it must not repeat the "reason" label. */}
          <span>{codeOptions.length > 0
            ? (reasonOptional ? t('adminPlatform.reason.detailsOptionalLabel') : t('adminPlatform.reason.detailsLabel'))
            : (reasonOptional ? t('adminPlatform.reason.optionalLabel') : t('adminPlatform.reason.label'))}</span>
          <textarea
            value={reason}
            onChange={event => setReason(event.target.value)}
            minLength={reasonOptional ? undefined : reasonMin}
            maxLength={reasonMax}
            rows={4}
            required={!reasonOptional}
            aria-invalid={touched && !textOk}
            placeholder={codeOptions.length > 0 ? t('adminPlatform.reason.detailsPlaceholder') : t('adminPlatform.reason.placeholder')}
          />
        </label>
        <small className={touched && !textOk ? 'invalid' : ''}>
          <span>{reasonOptional
            ? t('adminPlatform.reason.optionalHint', { max: reasonMax })
            : t('adminPlatform.reason.hint', { min: reasonMin, max: reasonMax })}</span>
          <span className="num">{trimmed.length}/{reasonMax}</span>
        </small>
        {children}
        {error && <p className="ap-error" role="alert">{error}</p>}
      </div>
    </ConfirmDialog>
  )
}
