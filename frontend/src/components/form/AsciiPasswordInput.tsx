import { type InputHTMLAttributes, type Ref } from 'react'
import { useTranslation } from 'react-i18next'
import type { useAsciiPasswordInput } from '../../hooks/useAsciiPasswordInput'

type Field = ReturnType<typeof useAsciiPasswordInput>

type NativeProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'type' | 'value' | 'onChange' | 'onPaste' | 'onKeyDown' | 'onKeyUp' | 'onCompositionStart' | 'onCompositionEnd' | 'inputMode' | 'lang' | 'autoCapitalize' | 'autoCorrect' | 'spellCheck' | 'ref'
>

export type AsciiPasswordInputProps = NativeProps & {
  field: Field
  visible?: boolean
  inputRef?: Ref<HTMLInputElement>
}

/** Drop-in password input wired to useAsciiPasswordInput (hints stay outside flex wrappers). */
export function AsciiPasswordInput({ field, visible = false, inputRef, ...rest }: AsciiPasswordInputProps) {
  const { ref: fieldRef, ...inputProps } = field.inputProps
  return (
    <input
      {...rest}
      {...inputProps}
      ref={(node) => {
        fieldRef.current = node
        if (typeof inputRef === 'function') inputRef(node)
        else if (inputRef) (inputRef as { current: HTMLInputElement | null }).current = node
      }}
      type={visible ? 'text' : 'password'}
    />
  )
}

export type PasswordInputHintsProps = {
  asciiWarning: boolean
  capsLockOn: boolean
  className?: string
}

/** Charset / Caps Lock alerts — place under the input wrapper, not inside it. */
export function PasswordInputHints({ asciiWarning, capsLockOn, className = 'signup-field-error' }: PasswordInputHintsProps) {
  const { t } = useTranslation('auth')
  return (
    <>
      {asciiWarning ? <span className={className} role="alert">{t('passwordField.asciiOnly')}</span> : null}
      {capsLockOn ? <span className={className} role="status">{t('passwordField.capsLock')}</span> : null}
    </>
  )
}

export default AsciiPasswordInput
