/** Printable ASCII without spaces (English letters, digits, symbols). */
export const ALLOWED = /^[\x21-\x7E]*$/

/** Strip characters outside the allowed printable-ASCII set. */
export function sanitizePassword(value: string): string {
  return value.replace(/[^\x21-\x7E]/g, '')
}

/** Strength checklist shared by signup / reset (aligned with AuthDtos). */
export function passwordRules(value: string): { length: boolean; number: boolean; letter: boolean } {
  return {
    length: value.length >= 8,
    number: /\d/.test(value),
    letter: /[A-Za-z]/.test(value),
  }
}

/** True when value satisfies length + letter + digit + ALLOWED charset. */
export function isPasswordValid(value: string): boolean {
  const rules = passwordRules(value)
  return rules.length && rules.number && rules.letter && ALLOWED.test(value) && value.length > 0
}
