import { useCallback, useLayoutEffect, useRef, useState, type AnimationEvent, type ChangeEvent, type ClipboardEvent, type CompositionEvent, type FormEvent, type KeyboardEvent } from 'react'
import { AUTOFILL_ANIMATION } from './autofillSync'
import { sanitizePassword } from '../utils/passwordRules'

function countRemovedBefore(raw: string, sanitized: string, cursor: number): number {
  let removed = 0
  let i = 0
  let j = 0
  while (i < cursor) {
    if (j < sanitized.length && raw[i] === sanitized[j]) {
      i += 1
      j += 1
    } else {
      removed += 1
      i += 1
    }
  }
  return removed
}

type Controlled = {
  value: string
  onValueChange: (value: string) => void
}

type Uncontrolled = {
  value?: undefined
  onValueChange?: undefined
}

export type UseAsciiPasswordInputOptions = Controlled | Uncontrolled

/** Shared sanitize + Caps Lock + IME end handling for password fields. */
export function useAsciiPasswordInput(options: UseAsciiPasswordInputOptions = {}) {
  const controlled = typeof options.value === 'string' && typeof options.onValueChange === 'function'
  const value = controlled ? options.value : undefined
  const onValueChange = controlled ? options.onValueChange : undefined

  const inputRef = useRef<HTMLInputElement | null>(null)
  const selectionRef = useRef<{ start: number; end: number } | null>(null)
  const [asciiWarning, setAsciiWarning] = useState(false)
  const [capsLockOn, setCapsLockOn] = useState(false)

  useLayoutEffect(() => {
    const el = inputRef.current
    const sel = selectionRef.current
    if (!el || !sel) return
    el.setSelectionRange(sel.start, sel.end)
    selectionRef.current = null
  })

  const commit = useCallback((raw: string, cursor: number | null) => {
    const next = sanitizePassword(raw)
    setAsciiWarning(next !== raw)
    if (cursor != null) {
      const removed = countRemovedBefore(raw, next, cursor)
      const pos = Math.max(0, cursor - removed)
      selectionRef.current = { start: pos, end: pos }
    }
    if (controlled && onValueChange) onValueChange(next)
    else if (inputRef.current && inputRef.current.value !== next) inputRef.current.value = next
    return next
  }, [controlled, onValueChange])

  const onChange = useCallback((event: ChangeEvent<HTMLInputElement>) => {
    commit(event.target.value, event.target.selectionStart)
  }, [commit])

  const onInput = useCallback((event: FormEvent<HTMLInputElement>) => {
    const el = event.currentTarget
    commit(el.value, el.selectionStart)
  }, [commit])

  const onAnimationStart = useCallback((event: AnimationEvent<HTMLInputElement>) => {
    if (event.animationName !== AUTOFILL_ANIMATION) return
    const el = event.currentTarget
    commit(el.value, el.selectionStart)
  }, [commit])

  const onCompositionEnd = useCallback((event: CompositionEvent<HTMLInputElement>) => {
    const el = event.currentTarget
    commit(el.value, el.selectionStart)
  }, [commit])

  const onPaste = useCallback((event: ClipboardEvent<HTMLInputElement>) => {
    event.preventDefault()
    const el = event.currentTarget
    const pasted = event.clipboardData.getData('text')
    const start = el.selectionStart ?? (controlled ? (value?.length ?? 0) : el.value.length)
    const end = el.selectionEnd ?? start
    const current = controlled ? (value ?? '') : el.value
    const sanitizedPaste = sanitizePassword(pasted)
    const merged = current.slice(0, start) + pasted + current.slice(end)
    commit(merged, start + sanitizedPaste.length)
  }, [commit, controlled, value])

  const syncCaps = useCallback((event: KeyboardEvent<HTMLInputElement>) => {
    setCapsLockOn(event.getModifierState('CapsLock'))
  }, [])

  const inputProps = {
    ref: inputRef,
    inputMode: 'text' as const,
    lang: 'en',
    autoCapitalize: 'off' as const,
    autoCorrect: 'off' as const,
    spellCheck: false as const,
    ...(controlled ? { value: value as string } : {}),
    onChange,
    onInput,
    onAnimationStart,
    onPaste,
    onCompositionEnd,
    onKeyDown: syncCaps,
    onKeyUp: syncCaps,
  }

  return { inputProps, inputRef, asciiWarning, capsLockOn }
}
