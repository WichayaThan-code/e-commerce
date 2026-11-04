import type { AnimationEvent, ChangeEvent, FormEvent } from 'react'

/** Must match `@keyframes onAutoFillStart` in form-invalid.css */
export const AUTOFILL_ANIMATION = 'onAutoFillStart'

type TextControl = HTMLInputElement | HTMLTextAreaElement

type AutofillSyncOptions = {
  onValue: (value: string) => void
  onClearError?: () => void
  transform?: (value: string) => string
}

function syncFromElement(el: TextControl, options: AutofillSyncOptions) {
  const raw = el.value
  const next = options.transform ? options.transform(raw) : raw
  if (options.transform && next !== raw) el.value = next
  options.onValue(next)
  options.onClearError?.()
}

/** Handlers that keep React state in sync when Chrome/Edge autofills (onChange alone often misses). */
export function autofillSyncHandlers(options: AutofillSyncOptions) {
  const sync = (el: TextControl) => syncFromElement(el, options)

  return {
    onChange: (event: ChangeEvent<TextControl>) => sync(event.currentTarget),
    onInput: (event: FormEvent<TextControl>) => sync(event.currentTarget),
    onAnimationStart: (event: AnimationEvent<TextControl>) => {
      if (event.animationName !== AUTOFILL_ANIMATION) return
      sync(event.currentTarget)
    },
  }
}

export function isAutofillAnimation(name: string) {
  return name === AUTOFILL_ANIMATION
}

/** Read a named text control from a form, ignoring RadioNodeList collisions. */
export function readFormTextValue(form: HTMLFormElement, name: string, fallback = ''): string {
  const el = form.elements.namedItem(name)
  if (!el) return fallback
  if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) return el.value
  if (typeof (el as { value?: unknown }).value === 'string') return (el as { value: string }).value
  return fallback
}
