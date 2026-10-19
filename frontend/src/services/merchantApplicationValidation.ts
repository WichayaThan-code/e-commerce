import i18n from '../i18n'
import type { MerchantOwnerPayload } from './api'
import type { MerchantDraft, OwnerDraft } from './merchantDraft'

export type FieldErrors = Record<string, string>

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const URL = /^https?:\/\/\S+$/i

function text(key: string) {
  return i18n.t(key, { ns: 'merchant' })
}

export function focusFirstInvalid(errors: FieldErrors) {
  const name = Object.keys(errors)[0]
  if (!name) return
  const el = document.querySelector<HTMLElement>(`[data-field="${name}"], [name="${name}"]`)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  const focusable = el.matches('input,select,textarea,button') ? el : el.querySelector<HTMLElement>('input,select,textarea,button')
  focusable?.focus()
}

export function mergeFieldError(prev: FieldErrors, field: string, message?: string): FieldErrors {
  const next = { ...prev }
  if (message) next[field] = message
  else delete next[field]
  return next
}

export function citizenIdDigits(value: string) {
  return value.replace(/\D/g, '')
}

/** Thai national ID display: x-xxxx-xxxxx-xx-x (max 13 digits). */
export function formatCitizenId(value: string) {
  const digits = citizenIdDigits(value).slice(0, 13)
  if (digits.length <= 1) return digits
  if (digits.length <= 5) return `${digits.slice(0, 1)}-${digits.slice(1)}`
  if (digits.length <= 10) return `${digits.slice(0, 1)}-${digits.slice(1, 5)}-${digits.slice(5)}`
  if (digits.length <= 12) return `${digits.slice(0, 1)}-${digits.slice(1, 5)}-${digits.slice(5, 10)}-${digits.slice(10)}`
  return `${digits.slice(0, 1)}-${digits.slice(1, 5)}-${digits.slice(5, 10)}-${digits.slice(10, 12)}-${digits.slice(12)}`
}

/** Thai mobile display: xxx-xxx-xxxx (max 10 digits). */
export function formatPhoneNumber(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 10)
  if (digits.length <= 3) return digits
  if (digits.length <= 6) return `${digits.slice(0, 3)}-${digits.slice(3)}`
  return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`
}

export function digitsOnly(value: string, maxLength?: number) {
  const digits = value.replace(/\D/g, '')
  return typeof maxLength === 'number' ? digits.slice(0, maxLength) : digits
}

export function citizenIdChecksumOk(value: string) {
  const digits = citizenIdDigits(value)
  if (digits.length !== 13) return false
  let sum = 0
  for (let index = 0; index < 12; index += 1) sum += Number(digits[index]) * (13 - index)
  return (11 - (sum % 11)) % 10 === Number(digits[12])
}

export type ShopInput = {
  shopName: string
  shopNameEnglish: string
  description: string
  category: string
  website: string
  lineId: string
  facebookUrl: string
  instagramUrl: string
  tiktokUrl: string
  address: string
  province: string
  district: string
  subdistrict: string
  postalCode: string
  logoBase64?: string
}

export function validateShop(input: ShopInput, requireLogo = true): FieldErrors {
  const errors: FieldErrors = {}
  const shopName = input.shopName.trim()
  if (!shopName) errors.shopName = text('validation.shopName')
  else if (shopName.length > 100) errors.shopName = text('validation.shopName')
  if (input.shopNameEnglish.trim().length > 100) errors.shopNameEnglish = text('validation.shopNameEn')
  if (input.description.trim().length > 500) errors.description = text('validation.description')
  if (!input.category.trim()) errors.category = text('validation.category')
  if (!input.address.trim() || input.address.trim().length > 500) errors.address = text('validation.address')
  if (!input.province.trim()) errors.province = text('validation.province')
  if (!input.district.trim()) errors.district = text('validation.district')
  if (!input.subdistrict.trim()) errors.subdistrict = text('validation.subdistrict')
  if (!/^[0-9]{5}$/.test(input.postalCode.trim())) errors.postalCode = text('validation.postalCode')
  for (const key of ['website', 'facebookUrl', 'instagramUrl', 'tiktokUrl'] as const) {
    const value = input[key].trim()
    if (!value) continue
    if (value.length > 500 || !URL.test(value)) errors[key] = text('validation.url')
  }
  if (input.lineId.trim().length > 500) errors.lineId = text('validation.lineId')
  if (requireLogo && !input.logoBase64) errors.logo = text('validation.logo')
  return errors
}

export function validateOwner(input: OwnerDraft): FieldErrors {
  const errors: FieldErrors = {}
  if (!input.title.trim()) errors.title = text('validation.title')
  if (!input.firstName.trim() || input.firstName.trim().length > 100) errors.firstName = text('validation.firstName')
  if (!input.lastName.trim() || input.lastName.trim().length > 100) errors.lastName = text('validation.lastName')
  if (!citizenIdChecksumOk(input.citizenId)) errors.citizenId = text('validation.citizenId')
  if (!input.birthDate) errors.birthDate = text('validation.birthDate')
  if (!input.nationality.trim()) errors.nationality = text('validation.nationality')
  const phoneDigits = input.phone.replace(/\D/g, '')
  if (phoneDigits.length < 9 || phoneDigits.length > 10) errors.phone = text('validation.phone')
  if (!EMAIL.test(input.email.trim())) errors.email = text('validation.email')
  if (!input.houseNo.trim()) errors.houseNo = text('validation.houseNo')
  if (!input.province.trim()) errors.province = text('validation.province')
  if (!input.district.trim()) errors.district = text('validation.district')
  if (!input.subdistrict.trim()) errors.subdistrict = text('validation.subdistrict')
  if (!/^[0-9]{5}$/.test(input.postalCode.trim())) errors.postalCode = text('validation.postalCode')
  if (!input.bankName.trim()) errors.bankName = text('validation.bankName')
  if (!/^[0-9]{8,20}$/.test(input.bankAccountNumber.replace(/\D/g, ''))) errors.bankAccountNumber = text('validation.bankAccountNumber')
  if (!input.bankAccountName.trim()) errors.bankAccountName = text('validation.bankAccountName')
  return errors
}

export function ownerPayload(input: OwnerDraft): MerchantOwnerPayload {
  return {
    ownerType: input.ownerType === 'company' ? 'COMPANY' : 'PERSON',
    title: input.title.trim(),
    firstName: input.firstName.trim(),
    lastName: input.lastName.trim(),
    citizenId: citizenIdDigits(input.citizenId),
    birthDate: input.birthDate,
    nationality: input.nationality.trim(),
    phone: input.phone.trim(),
    email: input.email.trim(),
    houseNo: input.houseNo.trim(),
    villageNo: input.villageNo.trim(),
    building: input.building.trim(),
    alley: input.alley.trim(),
    road: input.road.trim(),
    province: input.province.trim(),
    district: input.district.trim(),
    subdistrict: input.subdistrict.trim(),
    postalCode: input.postalCode.trim(),
    bankName: input.bankName.trim(),
    bankAccountNumber: input.bankAccountNumber.replace(/\D/g, ''),
    bankAccountName: input.bankAccountName.trim(),
  }
}

export const MAX_DOCUMENT_BYTES = 5 * 1024 * 1024
export const MAX_LOGO_BYTES = 2 * 1024 * 1024

export function documentFileError(file: File) {
  if (file.size > MAX_DOCUMENT_BYTES) return text('validation.fileSize')
  const name = file.name.toLowerCase()
  const allowed = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/webp' || file.type === 'application/pdf'
    || /\.(jpe?g|png|webp|pdf)$/.test(name)
  return allowed ? null : text('validation.fileType')
}

export function validateDocuments(ownerType: OwnerDraft['ownerType'] | undefined, uploaded: string[]): FieldErrors {
  const errors: FieldErrors = {}
  if (!uploaded.includes('IDENTITY')) errors.IDENTITY = text('validation.IDENTITY')
  if (!uploaded.includes('ADDRESS')) errors.ADDRESS = text('validation.ADDRESS')
  if (ownerType === 'company' && !uploaded.includes('BUSINESS')) errors.BUSINESS = text('validation.BUSINESS')
  return errors
}

export type ReviewGap = { href: string; message: string }

export function reviewGap(draft: MerchantDraft, uploaded: string[]): ReviewGap | null {
  const shopErrors = validateShop({
    shopName: draft.shopName || '',
    shopNameEnglish: draft.shopNameEnglish || '',
    description: draft.description || '',
    category: draft.category || '',
    website: draft.website || '',
    lineId: draft.lineId || '',
    facebookUrl: draft.facebookUrl || '',
    instagramUrl: draft.instagramUrl || '',
    tiktokUrl: draft.tiktokUrl || '',
    address: draft.address || '',
    province: draft.province || '',
    district: draft.district || '',
    subdistrict: draft.subdistrict || '',
    postalCode: draft.postalCode || '',
    logoBase64: draft.logoBase64 || (uploaded.includes('LOGO') ? 'server' : ''),
  }, !uploaded.includes('LOGO'))
  if (Object.keys(shopErrors).length > 0) return { href: '/merchant/register', message: text('validation.stepShop') }
  if (!draft.ownerId) {
    if (!draft.owner || Object.keys(validateOwner(draft.owner)).length > 0) return { href: '/merchant/owner-info', message: text('validation.stepOwner') }
  }
  const docs = validateDocuments(draft.owner?.ownerType, uploaded)
  if (Object.keys(docs).length > 0) return { href: '/merchant/documents', message: text('validation.stepDocuments') }
  return null
}
