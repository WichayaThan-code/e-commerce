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
  'VALUE_TOO_LONG',
  'TOO_LONG',
  'REQUIRED_MISSING',
  'REFERENCE_INVALID',
  'DATA_INTEGRITY',
  'CONFLICT',
  'FILE_TOO_LARGE',
  'SHOP_NOT_APPROVED',
  'NETWORK_ERROR',
  'INTERNAL_ERROR',
  'INVALID_FILE',
  'REQUEST_FAILED',
  'SESSION_EXPIRED',
  'UNAUTHORIZED',
  'INVALID_REFRESH_TOKEN',
])

export function apiErrorText(code: string, fallback?: string, vars?: Record<string, unknown>) {
  if (KNOWN.has(code)) {
    const translated = i18n.t(`apiErrors.${code}`, { ns: 'merchant', ...vars })
    if (translated && translated !== `apiErrors.${code}`) return translated
  }
  return fallback || i18n.t('apiErrors.REQUEST_FAILED', { ns: 'merchant' })
}

export function describeApiError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return {
        code: 'SESSION_EXPIRED',
        message: apiErrorText('SESSION_EXPIRED', error.message),
        fieldErrors: error.fieldErrors,
        section: typeof error.details.section === 'string' ? error.details.section : undefined,
        requestId: typeof error.details.requestId === 'string' ? error.details.requestId : undefined,
      }
    }
    const code = error.status >= 500 && !KNOWN.has(error.code)
      ? 'INTERNAL_ERROR'
      : error.status === 0
        ? 'NETWORK_ERROR'
        : error.code
    let message = apiErrorText(code, error.message)
    const requestId = typeof error.details.requestId === 'string' ? error.details.requestId : undefined
    if ((code === 'INTERNAL_ERROR' || error.status >= 500) && requestId) {
      message = apiErrorText('INTERNAL_ERROR_REF', message, { requestId })
    }
    return {
      code,
      message,
      fieldErrors: error.fieldErrors,
      section: typeof error.details.section === 'string' ? error.details.section : undefined,
      requestId,
    }
  }
  return {
    code: 'NETWORK_ERROR',
    message: apiErrorText('NETWORK_ERROR'),
    fieldErrors: [] as ApiFieldError[],
    section: undefined as string | undefined,
    requestId: undefined as string | undefined,
  }
}

export function fieldErrorsFromApi(errors: ApiFieldError[]): FieldErrors {
  const map: FieldErrors = {}
  for (const item of errors) {
    if (!item.field || map[item.field]) continue
    if (item.code === 'TOO_LONG' || item.code === 'VALUE_TOO_LONG') {
      const max = item.params && typeof item.params.max === 'number' ? item.params.max : undefined
      map[item.field] = max
        ? apiErrorText('TOO_LONG', item.message, { max })
        : (item.message || apiErrorText('VALUE_TOO_LONG'))
      continue
    }
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
