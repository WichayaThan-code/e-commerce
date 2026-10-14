import i18n from '../i18n'
import { ApiError, type ApiFieldError } from './api'
import { focusFirstInvalid, type FieldErrors } from './merchantApplicationValidation'

const KNOWN = new Set([
  'CITIZEN_ID_TAKEN',
  'OWNER_EXISTS',
  'SHOP_EXISTS',
  'DRAFT_CONFLICT',
  'APPLICATION_ALREADY_SUBMITTED',
  'APPLICATION_INCOMPLETE',
  'VALIDATION_FAILED',
  'FILE_TOO_LARGE',
  'SHOP_NOT_APPROVED',
  'NETWORK_ERROR',
  'INTERNAL_ERROR',
  'INVALID_FILE',
  'REQUEST_FAILED',
])

export function apiErrorText(code: string, fallback?: string) {
  if (KNOWN.has(code)) {
    const translated = i18n.t(`apiErrors.${code}`, { ns: 'merchant' })
    if (translated && translated !== `apiErrors.${code}`) return translated
  }
  return fallback || i18n.t('apiErrors.REQUEST_FAILED', { ns: 'merchant' })
}

export function describeApiError(error: unknown) {
  if (error instanceof ApiError) {
    const code = error.status >= 500 && !KNOWN.has(error.code) ? 'INTERNAL_ERROR' : error.status === 0 ? 'NETWORK_ERROR' : error.code
    return { code, message: apiErrorText(code, error.message), fieldErrors: error.fieldErrors }
  }
  return { code: 'NETWORK_ERROR', message: apiErrorText('NETWORK_ERROR'), fieldErrors: [] as ApiFieldError[] }
}

export function fieldErrorsFromApi(errors: ApiFieldError[]): FieldErrors {
  const map: FieldErrors = {}
  for (const item of errors) {
    if (!item.field || map[item.field]) continue
    map[item.field] = KNOWN.has(item.code) ? apiErrorText(item.code, item.message) : (item.message || apiErrorText('VALIDATION_FAILED'))
  }
  return map
}

export function showApiFailure(error: unknown, setBanner: (message: string) => void, setFields?: (errors: FieldErrors) => void) {
  const described = describeApiError(error)
  if (error instanceof ApiError && error.fieldErrors.length > 0 && setFields) {
    const fields = fieldErrorsFromApi(error.fieldErrors)
    setFields(fields)
    focusFirstInvalid(fields)
  }
  setBanner(described.message)
}
