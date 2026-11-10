import i18n from '../i18n'

export type UserRole = 'CUSTOMER' | 'MERCHANT' | 'ADMIN'
/** Effective store permissions in one shop (shop_members role → auth_role_permissions). */
export type ShopPermissionGrant = { shopId:number; role:string; permissions:string[] }
/**
 * `role` is the legacy account role kept for routing. `roleCodes`, `permissions` (account + platform level) and
 * `shopPermissions` are the effective permissions resolved from the database on every login/refresh/me call;
 * `superAdmin` (active PLATFORM_ADMIN) means every permission in every shop.
 */
export type AuthUser = { id:string; firstName:string; lastName:string; email:string; phone:string; role:UserRole; emailVerified?:boolean; phoneVerified?:boolean; roleCodes?:string[]; permissions?:string[]; shopPermissions?:ShopPermissionGrant[]; superAdmin?:boolean }
type AuthResponse = { accessToken:string; expiresIn:number; user:AuthUser }
export type RegisterPayload = { firstName:string; lastName:string; email:string; phone:string; password:string }
export type OtpPurpose = 'SIGNUP'|'LOGIN'|'PHONE_VERIFY'|'EMAIL_VERIFY'|'PASSWORD_RESET'|'STEP_UP'
export type SendOtpResponse = { otpId:string; expiresIn:number; destinationMasked:string; channel:string; code:string|null }
export type VerifyOtpResponse = { verified:boolean; purpose:string; resetToken:string|null }
export type ForgotPasswordResponse = { message:string; resetToken:string|null }
export type OAuthProviderId = 'google' | 'facebook'
export type OAuthProviders = { google: boolean; facebook: boolean }

const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8081').replace(/\/$/, '')
let accessToken: string | null = null
let refreshInFlight: Promise<AuthResponse> | null = null

/** Fired when the backend denies an action (403 / shop no longer reachable) so the UI re-reads its permissions. */
export const PERMISSIONS_STALE_EVENT = 'tsf:permissions-stale'

export type ApiFieldError = { field: string; code: string; message: string }

export class ApiError extends Error {
  fieldErrors: ApiFieldError[]
  /** Raw error body (e.g. 409 INSUFFICIENT_STOCK: availableQuantity, inCartQuantity, canAddQuantity). */
  details: Record<string, unknown>
  constructor(public code:string, message:string, public status:number, public jobId?:string, fieldErrors?: ApiFieldError[], details?: Record<string, unknown>) {
    super(message)
    this.fieldErrors = fieldErrors ?? []
    this.details = details ?? {}
  }
}

function acceptLanguageHeader() {
  const lng = i18n.language || 'th'
  if (lng.startsWith('zh')) return 'zh-CN,zh;q=0.9'
  if (lng.startsWith('en')) return 'en-US,en;q=0.9'
  return 'th-TH,th;q=0.9'
}

function withLanguage(headers: Headers) {
  headers.set('Accept-Language', acceptLanguageHeader())
}

/** Extra request flags. Defaults preserve existing call-site behaviour. */
export type RequestFlags = { redirectOn401?: boolean }

export async function request<T>(path:string, init:RequestInit = {}, authenticate = false, retry = true, flags: RequestFlags = {}):Promise<T> {
  const redirectOn401 = flags.redirectOn401 !== false
  if (authenticate && !accessToken) await refreshSession()
  const headers = new Headers(init.headers)
  withLanguage(headers)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (authenticate && accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  let response:Response
  try {
    response = await fetch(`${API_URL}${path}`, {...init, headers, credentials:'include'})
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    if (error && typeof error === 'object' && 'name' in error && (error as { name?: string }).name === 'AbortError') throw error
    throw new ApiError('NETWORK_ERROR', 'เชื่อมต่อระบบไม่ได้ กรุณาตรวจสอบว่า Backend กำลังทำงาน', 0)
  }
  if (response.status === 401 && authenticate && retry) {
    try {
      await refreshSession()
      return request<T>(path, init, true, false, flags)
    } catch { accessToken = null }
  }
  if (!response.ok) {
    const error: { code?: string; message?: string; jobId?: string; fieldErrors?: ApiFieldError[] } = await response.json().catch(() => ({ code: response.status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED', message: 'เกิดข้อผิดพลาด กรุณาลองใหม่' }))
    if (authenticate && !path.startsWith('/api/auth/') && (response.status === 403 || error.code === 'SHOP_NOT_FOUND')) {
      window.dispatchEvent(new Event(PERMISSIONS_STALE_EVENT))
    }
    const apiError = new ApiError(error.code || (response.status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED'), error.message || 'เกิดข้อผิดพลาด กรุณาลองใหม่', response.status, error.jobId, error.fieldErrors, error as Record<string, unknown>)
    if (response.status === 403 && error.code === 'EMAIL_NOT_VERIFIED' && !window.location.pathname.startsWith('/verify-email')) {
      const next = `${window.location.pathname}${window.location.search}`
      window.location.assign(`/verify-email?next=${encodeURIComponent(next)}`)
    }
    if (response.status === 401 && authenticate && redirectOn401 && !window.location.pathname.startsWith('/login')) {
      const next = `${window.location.pathname}${window.location.search}`
      window.location.assign(`/login?next=${encodeURIComponent(next)}`)
    }
    throw apiError
  }
  return response.status === 204 ? undefined as T : response.json()
}

function remember(response:AuthResponse) { accessToken = response.accessToken; return response.user }
function refreshSession() {
  if (!refreshInFlight) refreshInFlight=request<AuthResponse>('/api/auth/refresh',{method:'POST'},false,false).then(response=>{remember(response);return response}).finally(()=>{refreshInFlight=null})
  return refreshInFlight
}

export const authApi = {
  login: (identity:string, password:string) => request<AuthResponse>('/api/auth/login', {method:'POST', body:JSON.stringify({identity,password})}).then(remember),
  register: (payload:RegisterPayload) => request<AuthResponse>('/api/auth/register', {method:'POST', body:JSON.stringify(payload)}).then(remember),
  oauthProviders: () => request<OAuthProviders>('/api/auth/oauth/providers'),
  oauthLogin: (provider: OAuthProviderId, idToken: string, fullName?: string) =>
    request<AuthResponse>(`/api/auth/oauth/${provider}`, {
      method: 'POST',
      body: JSON.stringify({ idToken, fullName: fullName || undefined }),
    }).then(remember),
  sendOtp: (destination:string, purpose:OtpPurpose, authenticate = false) => request<SendOtpResponse>('/api/auth/otp/send', {method:'POST', body:JSON.stringify({destination,purpose})}, authenticate),
  verifyOtp: (otpId:string, code:string) => request<VerifyOtpResponse>('/api/auth/otp/verify', {method:'POST', body:JSON.stringify({otpId,code})}),
  verifyOtpLink: (otpId:string, token:string) => request<VerifyOtpResponse>('/api/auth/otp/verify-link', {method:'POST', body:JSON.stringify({otpId,token})}),
  forgotPassword: (email:string) => request<ForgotPasswordResponse>('/api/auth/forgot-password', {method:'POST', body:JSON.stringify({email})}),
  resetPassword: (token:string, newPassword:string) => request<{message:string}>('/api/auth/reset-password', {method:'POST', body:JSON.stringify({token,newPassword})}),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<{ message: string }>('/api/auth/change-password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) }, true),
  refresh: () => refreshSession().then(response=>response.user),
  me: () => request<AuthUser>('/api/auth/me', {}, true),
  logout: async () => { try { await request<void>('/api/auth/logout', {method:'POST'}) } finally { accessToken = null } },
}

export type ApplicationStatus = 'DRAFT'|'SUBMITTED'|'UNDER_REVIEW'|'APPROVED'|'REJECTED'
export type DocumentType = 'LOGO'|'IDENTITY'|'PORTRAIT'|'ADDRESS'|'BUSINESS'
export type MerchantDocument = {id:string;type:DocumentType;originalFilename:string;contentType:string;fileSize:number;sha256:string;uploadedAt:string}
export type MerchantApplication = {
  id:string;status:ApplicationStatus;referenceNo?:string
  shopName?:string;shopNameEn?:string;description?:string;category?:string;saleType?:'OWNER'|'RESELLER';website?:string;lineId?:string;facebookUrl?:string;instagramUrl?:string;tiktokUrl?:string;logoPath?:string
  shopAddress?:string;shopProvince?:string;shopDistrict?:string;shopSubdistrict?:string;shopPostalCode?:string
  ownerType?:'PERSON'|'COMPANY';title?:string;firstName?:string;lastName?:string;citizenIdMasked?:string;birthDate?:string;nationality?:string;phone?:string;email?:string
  houseNo?:string;villageNo?:string;building?:string;alley?:string;road?:string;province?:string;district?:string;subdistrict?:string;postalCode?:string
  bankName?:string;bankAccountMasked?:string;bankAccountName?:string;documents:MerchantDocument[];submittedAt?:string
  shopPath?:string|null; ownerId?:string|null
}
export type MerchantOwner = {
  id:string; ownerType:'PERSON'|'COMPANY'; title:string; firstName:string; lastName:string
  citizenIdMasked?:string|null; phone:string; email:string; sensitiveReview:string
}
export type TranslationMeta = { origin: 'MACHINE' | 'HUMAN' | 'MANUAL'; status: 'PENDING' | 'DONE' | 'FAILED' }

export type ThemeColors = { primary:string; secondary:string; background:string; text:string; accent?:string }
export type ThemeStyle = { radius:number; cardStyle:'FLAT'|'BORDERED'|'SHADOW'; density:'COMPACT'|'COMFORTABLE'; buttonShape:'ROUNDED'|'PILL'|'SQUARE' }
export type ThemeHero = { layout:'FULL'|'STRIP'|'MEDIA_FULL'|'SPLIT'|'POSTER'; title:string; subtitle:string; ctaLabel:string; ctaLink:string }
export type SectionConfig = { key:'HERO'|'BANNERS'|'CATEGORIES'|'TRUST'|'INTRO'|'FEATURED'|'BESTSELLERS'|'NEW_ARRIVALS'|'CATALOG'|'HOURS'; enabled:boolean; variant:'FULL'|'STRIP'|'MEDIA_FULL'|'SPLIT'|'POSTER'|'GRID'|'LARGE'|'MENU'|null }
export type ThemeBanner = { imageUrl:string; title?:string; link?:string; mediaId?:string }
export type ThemeMediaKind = 'IMAGE'|'VIDEO'|'FONT'
export type ThemeMediaItem = {
  id:string; kind:ThemeMediaKind; originalFilename:string; mimeType:string; sizeBytes:number
  width?:number|null; height?:number|null; url:string; createdAt:string
}
export type ThemeTrustItem = { icon:string; label:string; enabled:boolean }
export type ThemeTrust = { enabled:boolean; items:ThemeTrustItem[] }
export type ThemeConfig = { colors:ThemeColors; font:string; style?:ThemeStyle; hero:ThemeHero; banners?:ThemeBanner[]; trust?:ThemeTrust; sections:SectionConfig[] }
export type ShopTheme = { templateKey:'GENERAL'|'FASHION'|'FOOD_BEVERAGE'|'CUSTOM'; config:ThemeConfig }

export type ShopPolicyType = 'shipping' | 'returns' | 'privacy' | 'terms'
export type ShopPolicyDto = {
  type: ShopPolicyType
  title?: string | null
  body: string
  updatedAt?: string | null
}

export type PublicShop = {
  name:string;nameEn?:string;nameZh?:string;description?:string;descriptionEn?:string;descriptionZh?:string;category:string
  address:string;province:string;district:string;subdistrict:string;postalCode:string
  latitude?: number | null
  longitude?: number | null
  slug:string;path:string;status:'PENDING_REVIEW'|'APPROVED'|'REJECTED'|'SUSPENDED';logoUrl?:string|null;coverUrl?:string|null
  shopId:number
  openingHours?: ShopHour[]
  theme?: ShopTheme | null
  createdAt?: string | null
  productCount?: number | null
  phone?: string | null
  email?: string | null
  website?: string | null
  lineId?: string | null
  facebookUrl?: string | null
  instagramUrl?: string | null
  tiktokUrl?: string | null
}
export type PublicShopCategory = { id:number; nameTh:string; nameEn?:string|null; nameZh?:string|null; productCount:number; slug?:string|null }
export type AdminDocument = { id:string; type:DocumentType; label:string; filename:string; fileSize:number }
export type AdminStoreApplication = {
  shopId:number; shopName:string; ownerName:string; phone:string; email:string
  province:string; category:string; address:string; description:string; website:string
  lineId:string; facebookUrl:string; instagramUrl:string; tiktokUrl:string
  appliedAt:string; updatedAt:string; status:'PENDING_REVIEW'|'APPROVED'|'REJECTED'|'SUSPENDED'
  slug:string; logoUrl?:string|null; documents:AdminDocument[]; reinstatementMessage?:string|null
  otherShops?: { shopId:number; shopName:string; status:AdminStoreApplication['status'] }[]
}
export type ReinstatementRequest = { shopId:number; status:'PENDING'|'APPROVED'|'REJECTED'|null; message:string|null; createdAt:string|null }
export type StoreAccess = { approved:boolean; hasShop:boolean; status:'PENDING_REVIEW'|'APPROVED'|'REJECTED'|'SUSPENDED'|null; shopName?:string|null; slug?:string|null; shopId?:number|null }
export type MerchantSettings = {
  shopId:number; status:'PENDING_REVIEW'|'APPROVED'|'REJECTED'|'SUSPENDED'; canOperate:boolean
  shopName:string; shopNameEn?:string|null; description?:string|null; category:string
  phone:string; email:string
  website?:string|null; lineId?:string|null; facebookUrl?:string|null; instagramUrl?:string|null; tiktokUrl?:string|null
  address:string; province:string; district:string; subdistrict:string; postalCode:string
  latitude?: number | null
  longitude?: number | null
  coverUrl?:string|null; logoUrl?:string|null; slug?:string|null; createdAt?:string|null
  openingHours?: ShopHour[]
  publicPhone?: string | null
  publicEmail?: string | null
}
export type ShopHour = { open:boolean; start:string; end:string }
export type MerchantSettingsPayload = {
  shopName:string; shopNameEn:string; description:string; category:string
  phone:string; email:string
  website:string; lineId:string; facebookUrl:string; instagramUrl:string; tiktokUrl:string
  address:string; province:string; district:string; subdistrict:string; postalCode:string
  latitude?: number | null
  longitude?: number | null
  publicPhone:string; publicEmail:string
}
export type MerchantChannelsPayload = {
  website:string; lineId:string; facebookUrl:string; instagramUrl:string; tiktokUrl:string
}
export type ShopPoliciesPayload = {
  policies: { type: ShopPolicyType; title: string; body: string }[]
}
export function mediaUrl(path?:string|null) {
  if (!path) return ''
  return path.startsWith('http') ? path : `${API_URL}${path}`
}
export type MerchantShopPayload = {shopName:string;shopNameEn:string;description:string;category:string;saleType:'OWNER'|'RESELLER';website:string;lineId:string;facebookUrl:string;instagramUrl:string;tiktokUrl:string;address:string;province:string;district:string;subdistrict:string;postalCode:string}
export type MerchantOwnerPayload = {ownerType:'PERSON'|'COMPANY';title:string;firstName:string;lastName:string;citizenId:string;birthDate:string;nationality:string;phone:string;email:string;houseNo:string;villageNo:string;building:string;alley:string;road:string;province:string;district:string;subdistrict:string;postalCode:string;bankName:string;bankAccountNumber:string;bankAccountName:string}
export type MerchantShopOption = { shopId:number; shopName:string; status:NonNullable<StoreAccess['status']>; slug:string; ownerId?:string|null }

function withShop(path:string, shopId?:number|null) {
  if (shopId == null) return path
  return `${path}${path.includes('?') ? '&' : '?'}shopId=${shopId}`
}

export const merchantApi = {
  getDraft: () => request<MerchantApplication>('/api/merchant-applications/draft', {}, true),
  saveShop: (payload:MerchantShopPayload) => request<MerchantApplication>('/api/merchant-applications/draft/shop', {method:'PUT',body:JSON.stringify(payload)}, true),
  saveOwner: (payload:MerchantOwnerPayload) => request<MerchantApplication>('/api/merchant-applications/draft/owner', {method:'PUT',body:JSON.stringify(payload)}, true),
  validateOwner: (payload:MerchantOwnerPayload) => request<void>('/api/merchant-applications/validate-owner', {method:'POST',body:JSON.stringify(payload)}, true),
  owners: () => request<MerchantOwner[]>('/api/merchant/owners', {}, true),
  updateOwner: (id:string, payload:MerchantOwnerPayload) => request<MerchantOwner>(`/api/merchant/owners/${id}`, {method:'PUT', body:JSON.stringify(payload)}, true),
  attachOwner: (ownerId:string) => request<MerchantApplication>(`/api/merchant-applications/draft/owner/${ownerId}`, {method:'POST'}, true),
  uploadDocument: (type:DocumentType,file:File) => {const body=new FormData();body.append('file',file);return request<MerchantApplication>(`/api/merchant-applications/draft/documents/${type}`,{method:'POST',body},true)},
  documentBlob: (type: DocumentType) => authorizedBlob(`/api/merchant-applications/draft/documents/${type}`),
  submit: () => request<MerchantApplication>('/api/merchant-applications/draft/submit',{method:'POST'},true),
  shops: () => request<MerchantShopOption[]>('/api/merchant/shops', {}, true),
  storeAccess: (shopId?:number|null) => request<StoreAccess>(withShop('/api/merchant/store-access', shopId), {}, true),
  getSettings: (shopId?:number|null) => request<MerchantSettings>(withShop('/api/merchant/settings', shopId), {}, true),
  saveSettings: (payload: MerchantSettingsPayload, shopId?:number|null) => request<MerchantSettings>(withShop('/api/merchant/settings', shopId), { method: 'PUT', body: JSON.stringify(payload) }, true),
  /** Field-scoped contact channels (website/LINE/social). Does not overwrite other shop settings. */
  saveChannels: (payload: MerchantChannelsPayload, shopId?:number|null) => request<MerchantSettings>(withShop('/api/merchant/settings/channels', shopId), { method: 'PUT', body: JSON.stringify(payload) }, true),
  saveHours: (days: ShopHour[], shopId?:number|null) => request<MerchantSettings>(withShop('/api/merchant/settings/hours', shopId), { method: 'PUT', body: JSON.stringify({ days }) }, true),
  uploadCover: (file: File, shopId?:number|null) => { const body = new FormData(); body.append('file', file); return request<MerchantSettings>(withShop('/api/merchant/settings/cover', shopId), { method: 'POST', body }, true) },
  uploadLogo: (file: File, shopId?:number|null) => { const body = new FormData(); body.append('file', file); return request<MerchantSettings>(withShop('/api/merchant/settings/logo', shopId), { method: 'POST', body }, true) },
  getTheme: (shopId?:number|null) => request<ShopTheme>(withShop('/api/merchant/settings/theme', shopId), {}, true),
  saveTheme: (payload: ShopTheme, shopId?:number|null) => request<ShopTheme>(withShop('/api/merchant/settings/theme', shopId), { method: 'PUT', body: JSON.stringify(payload) }, true),
  getThemePresets: (shopId?:number|null) => request<ShopTheme[]>(withShop('/api/merchant/settings/theme/presets', shopId), {}, true),
  getPolicies: (shopId?:number|null) => request<ShopPolicyDto[]>(withShop('/api/merchant/settings/policies', shopId), {}, true),
  savePolicies: (payload: ShopPoliciesPayload, shopId?:number|null) => request<ShopPolicyDto[]>(withShop('/api/merchant/settings/policies', shopId), { method: 'PUT', body: JSON.stringify(payload) }, true),
  openAnotherShop: (ownerId:string) => request<MerchantApplication>(`/api/merchant/shops?ownerId=${ownerId}`, { method: 'POST' }, true),
  reinstatement: (shopId:number) => request<ReinstatementRequest>(`/api/merchant/reinstatement?shopId=${shopId}`, {}, true),
  submitReinstatement: (shopId:number, message:string) => request<ReinstatementRequest>(`/api/merchant/reinstatement?shopId=${shopId}`, { method: 'POST', body: JSON.stringify({ message }) }, true),
}

export async function downloadFile(path:string, filename:string) {
  if (!accessToken) await refreshSession()
  const load = async () => {
    const headers = new Headers()
    withLanguage(headers)
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
    return fetch(`${API_URL}${path}`, {headers, credentials:'include'})
  }
  let response = await load()
  if (response.status === 401) {
    await refreshSession()
    response = await load()
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({code:'REQUEST_FAILED', message:'ดาวน์โหลดไฟล์ไม่สำเร็จ'}))
    throw new ApiError(error.code || 'REQUEST_FAILED', error.message || 'ดาวน์โหลดไฟล์ไม่สำเร็จ', response.status)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

/** Merchant customers Excel export (drops page/size/selected). */
export function exportCustomerInsights(
  shopId: number,
  query: Record<string, string | number | undefined | null> = {},
) {
  const params: Record<string, string | number | undefined | null> = { ...query }
  delete params.page
  delete params.size
  delete params.selected
  const qs = queryOf(params)
  return downloadFile(`/api/merchant/shops/${shopId}/customer-insights/export${qs}`, `customers-${shopId}.xlsx`)
}

export type StoreApplicationReason = { reasonCode?: string; reason?: string }
export type AdminContactReveal = { phone: string | null; email: string | null }

export const adminApplicationApi = {
  list: () => request<AdminStoreApplication[]>('/api/admin/store-applications', {}, true),
  approve: (shopId:number) => request<AdminStoreApplication>(`/api/admin/store-applications/${shopId}/approve`, {method:'POST'}, true),
  reject: (shopId:number) => request<AdminStoreApplication>(`/api/admin/store-applications/${shopId}/reject`, {method:'POST'}, true),
  suspend: (shopId:number, body: StoreApplicationReason) =>
    request<AdminStoreApplication>(`/api/admin/store-applications/${shopId}/suspend`, { method: 'POST', body: JSON.stringify(body) }, true),
  bulk: (action:'approve'|'reject'|'suspend'|'reactivate', shopIds:number[], reason?: StoreApplicationReason) =>
    request<{updated:number; skipped:number; applications:AdminStoreApplication[]}>(
      `/api/admin/store-applications/bulk/${action}`,
      { method: 'POST', body: JSON.stringify({ shopIds, ...(reason ?? {}) }) },
      true,
    ),
  reactivate: (shopId:number, body: StoreApplicationReason = {}) =>
    request<AdminStoreApplication>(`/api/admin/store-applications/${shopId}/reactivate`, { method: 'POST', body: JSON.stringify(body) }, true),
  /** PLATFORM_CONTACT_REVEAL — full phone/email for this session only; audited as CONTACT_REVEALED. */
  revealContact: (shopId: number) =>
    request<AdminContactReveal>(`/api/admin/store-applications/${shopId}/contact`, { method: 'POST', body: '{}' }, true),
  exportExcel: () => downloadFile('/api/admin/store-applications/export', 'store-applications.xlsx'),
  downloadDocument: (shopId:number, documentId:string, filename:string) => downloadFile(`/api/admin/store-applications/${shopId}/documents/${documentId}`, filename),
}

// ---- admin directory (users / roles & permissions) — read-only views over users, platform_roles, shop_members, auth_* and audit_logs
export type AdminRoleRef = { code:string; nameTh:string|null; context:string|null }
export type AdminShopRef = { shopId:number; shopName:string; shopStatus:string; role:string; roleNameTh:string|null }
export type AdminUserStatus = 'ACTIVE'|'SUSPENDED'
export type AdminUserRow = { id:string; firstName:string; lastName:string; email:string; phone:string; status:AdminUserStatus; accountRole:string; primaryRole:AdminRoleRef; roleCodes:string[]; shops:AdminShopRef[]; superAdmin:boolean; createdAt:string; lastLoginAt:string|null }
export type AdminUserPage = { items:AdminUserRow[]; page:number; size:number; totalItems:number; totalPages:number }
export type AdminMetric = { value:number; previous:number|null; changePercent:number|null }
export type AdminUserStats = { total:AdminMetric; admins:AdminMetric; storeOwners:AdminMetric; generalUsers:AdminMetric; active:number; suspended:number; roleCount:number; previousPeriodEnd:string; generatedAt:string }
export type AdminFilterOption = { value:string; label:string; count:number }
export type AdminUserFilterOptions = { roles:AdminFilterOption[]; statuses:AdminFilterOption[] }
export type AdminPermissionInfo = { code:string; module:string|null; scope:string|null; descriptionTh:string|null; active:boolean }
export type AdminShopAccess = { shopId:number; shopName:string; shopStatus:string; role:string; roleNameTh:string|null; joinedAt:string|null; permissions:AdminPermissionInfo[] }
export type AdminUserDetail = { id:string; firstName:string; lastName:string; email:string; phone:string; status:AdminUserStatus; accountRole:string; superAdmin:boolean; createdAt:string; lastLoginAt:string|null; emailVerifiedAt:string|null; phoneVerifiedAt:string|null; primaryRole:AdminRoleRef; roles:AdminRoleRef[]; accountPermissions:AdminPermissionInfo[]; shops:AdminShopAccess[] }
export type AdminActivityEvent = { at:string; type:string; title:string; subjectName:string|null; actorName:string|null; roleCode:string|null; roleNameTh:string|null; shopName:string|null; source:string }
export type AdminRoleSummary = { code:string; nameTh:string|null; descriptionTh:string|null; context:string; assignable:boolean; sortOrder:number; policyVersion:string; assignment:'PLATFORM_ROLES'|'SHOP_MEMBERS'|'IMPLICIT'|'PUBLIC'; userCount:number|null; permissionCount:number; active:boolean; superAdmin:boolean }
export type AdminRoleDetail = { role:AdminRoleSummary; groups:{ module:string; permissions:AdminPermissionInfo[] }[]; catalogPermissionCount:number }
export type AdminUserQuery = { q?:string; role?:string; status?:string; from?:string; to?:string; sort?:string; dir?:'asc'|'desc'; page?:number; size?:number }

function queryString(params:Record<string, string|number|undefined>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== '') search.set(key, String(value)) })
  const text = search.toString()
  return text ? `?${text}` : ''
}

// --- added during merge: types used by admin role/user pages and the product brand filter (from our side) ---
export type AdminRoleMutation = { context:AdminRoleContext; nameTh:string; descriptionTh:string; permissions:string[] }
export type AdminShopOption = { shopId:number; shopName:string; status:string }
export type AdminShopRoleMember = { userId:string; firstName:string; lastName:string; email:string; role:string; roleNameTh:string|null; active:boolean; joinedAt:string|null }
export type AdminCreateUser = { firstName:string; lastName:string; email:string; phone:string }
export type AdminUpdateUser = { firstName:string; lastName:string; phone:string }
export type AdminRoleContext = 'PLATFORM'|'STORE'
export type ProductBrand = { name: string; productCount: number }

export const adminDirectoryApi = {
  users: (query:AdminUserQuery) => request<AdminUserPage>(`/api/admin/users${queryString(query)}`, {}, true),
  createUser: (body:AdminCreateUser) => request<AdminUserDetail>('/api/admin/users', {method:'POST', body:JSON.stringify(body)}, true),
  updateUser: (id:string, body:AdminUpdateUser) => request<AdminUserDetail>(`/api/admin/users/${id}`, {method:'PUT', body:JSON.stringify(body)}, true),
  assignRole: (id:string, roleCode:string|null) => request<AdminUserDetail>(`/api/admin/users/${id}/role`, {method:'PUT', body:JSON.stringify({roleCode})}, true),
  suspendUser: (id:string) => request<AdminUserDetail>(`/api/admin/users/${id}/suspend`, {method:'POST'}, true),
  restoreUser: (id:string) => request<AdminUserDetail>(`/api/admin/users/${id}/restore`, {method:'POST'}, true),
  deleteUser: (id:string) => request<void>(`/api/admin/users/${id}`, {method:'DELETE'}, true),
  userStats: () => request<AdminUserStats>('/api/admin/users/stats', {}, true),
  userFilterOptions: () => request<AdminUserFilterOptions>('/api/admin/users/filter-options', {}, true),
  user: (id:string) => request<AdminUserDetail>(`/api/admin/users/${id}`, {}, true),
  userHistory: (id:string, limit = 50) => request<AdminActivityEvent[]>(`/api/admin/users/${id}/history?limit=${limit}`, {}, true),
  exportUsers: ({page:_page, size:_size, ...query}:AdminUserQuery) => downloadFile(`/api/admin/users/export${queryString(query)}`, 'users.csv'),
  roles: () => request<AdminRoleSummary[]>('/api/admin/roles', {}, true),
  platformPermissions: () => request<AdminPermissionInfo[]>('/api/admin/roles/permissions', {}, true),
  role: (code:string) => request<AdminRoleDetail>(`/api/admin/roles/${encodeURIComponent(code)}`, {}, true),
  createRole: (body:AdminRoleMutation) => request<AdminRoleDetail>('/api/admin/roles', {method:'POST', body:JSON.stringify(body)}, true),
  updateRole: (code:string, body:AdminRoleMutation) => request<AdminRoleDetail>(`/api/admin/roles/${encodeURIComponent(code)}`, {method:'PUT', body:JSON.stringify(body)}, true),
  deleteRole: (code:string) => request<void>(`/api/admin/roles/${encodeURIComponent(code)}`, {method:'DELETE'}, true),
  roleActivity: (limit = 20) => request<AdminActivityEvent[]>(`/api/admin/roles/activity?limit=${limit}`, {}, true),
  shopsForRoleAssignment: () => request<AdminShopOption[]>('/api/admin/shop-role-assignments/shops', {}, true),
  shopRoleMembers: (shopId:number) => request<AdminShopRoleMember[]>(`/api/admin/shop-role-assignments?shopId=${shopId}`, {}, true),
  assignShopRole: (shopId:number, userId:string, roleCode:string) => request<AdminShopRoleMember>(`/api/admin/shop-role-assignments/${shopId}/${userId}`, {method:'PUT', body:JSON.stringify({roleCode})}, true),
  removeShopRole: (shopId:number, userId:string) => request<void>(`/api/admin/shop-role-assignments/${shopId}/${userId}`, {method:'DELETE'}, true),
}

export type ProductCategory = { id:number; nameTh:string; nameEn:string; nameZh:string }

export const catalogApi = {
  categories: () => request<ProductCategory[]>('/api/categories'),
}

export const shopApi = {
  list: () => request<PublicShop[]>('/api/shops'),
  getBySlug: (slug:string) => request<PublicShop>(`/api/shops/${encodeURIComponent(slug)}`, { cache: 'no-store' }),
  categories: (slug:string) => request<PublicShopCategory[]>(`/api/shops/${encodeURIComponent(slug)}/categories`, { cache: 'no-store' }),
  products: (slug:string, query: Pick<ProductListQuery, 'q' | 'categoryId' | 'sort' | 'page' | 'size' | 'priceBands'> = {}) => request<ProductPage<PublicProduct>>(`/api/shops/${encodeURIComponent(slug)}/products${productQuery(query)}`),
  product: (slug:string, productSlug:string) => request<PublicProduct>(`/api/shops/${encodeURIComponent(slug)}/products/${encodeURIComponent(productSlug)}`),
  policies: (slug:string) => request<ShopPolicyDto[]>(`/api/shops/${encodeURIComponent(slug)}/policies`, { cache: 'no-store' }),
  /** GET /api/shops/{slug}/promotions — active, in-date promotions shown on the storefront (no usage data). */
  promotions: (slug:string) => request<PublicPromotion[]>(`/api/shops/${encodeURIComponent(slug)}/promotions`, { cache: 'no-store' }),
}

export type MarketplaceSearchShop = {
  slug: string; name: string; nameEn?: string | null; nameZh?: string | null
  category?: string | null; province?: string | null; logoUrl?: string | null; productCount: number
}
export type MarketplaceSearchBrand = { name: string; productCount: number }
export type MarketplaceSearchShopRef = { slug: string; name: string; nameEn?: string | null }
export type MarketplaceSearchProduct = { product: PublicProduct; shop: MarketplaceSearchShopRef }
export type MarketplaceSearchSort = 'newest' | 'price_asc' | 'price_desc' | 'name'
export type MarketplaceSearchQuery = { q: string; brand?: string; sort?: MarketplaceSearchSort; page?: number; size?: number }
export type MarketplaceSearchResponse = {
  q: string
  brand?: string | null
  shops: MarketplaceSearchShop[]
  brands: MarketplaceSearchBrand[]
  products: ProductPage<MarketplaceSearchProduct> & { totalPages?: number }
}

export const searchApi = {
  search: ({ q, brand, sort, page, size }: MarketplaceSearchQuery, signal?: AbortSignal) => {
    const params = new URLSearchParams()
    params.set('q', q)
    if (brand) params.set('brand', brand)
    if (sort) params.set('sort', sort)
    if (page != null) params.set('page', String(page))
    if (size != null) params.set('size', String(size))
    return request<MarketplaceSearchResponse>(`/api/search?${params.toString()}`, { signal })
  },
}

export type ProductStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type ProductType = 'SIMPLE' | 'VARIABLE'
export type StockFilter = 'in' | 'low' | 'out'
export type ProductSort = 'newest' | 'name' | 'price_asc' | 'price_desc' | 'stock' | 'best_selling'
export type ProductListQuery = {
  q?: string
  status?: ProductStatus
  categoryId?: number
  brand?: string
  stock?: StockFilter
  sort?: ProductSort
  page?: number
  size?: number
  priceBands?: string
}
export type ProductSku = {
  id: string
  skuCode: string
  barcode?: string | null
  optionLabel?: string | null
  optionValues?: Record<string, string>
  price: number | string
  costPrice?: number | string | null
  compareAtPrice?: number | string | null
  currency?: string | null
  weightGrams?: number | null
  active: boolean
  onHand: number
  reservedQty: number
  available: number
  lowStockThreshold?: number | null
  safetyStock?: number
  version: number
  imageId?: string | null
}
export type ProductImage = { id: string; url: string; sortOrder: number; primary: boolean; altText?: string | null }
export type Product = {
  id: string
  shopId: number
  slug: string
  nameTh: string
  nameEn?: string | null
  nameZh?: string | null
  descriptionTh?: string | null
  descriptionEn?: string | null
  descriptionZh?: string | null
  status: ProductStatus
  productType: ProductType
  categoryId?: number | null
  brand?: string | null
  basePrice?: number | string | null
  compareAtPrice?: number | string | null
  currency?: string | null
  weightGrams?: number | null
  publishedAt?: string | null
  archivedAt?: string | null
  modifiedAt?: string | null
  skus: ProductSku[]
  images: ProductImage[]
  nameEnMeta?: TranslationMeta | null
  nameZhMeta?: TranslationMeta | null
  descriptionEnMeta?: TranslationMeta | null
  descriptionZhMeta?: TranslationMeta | null
  optionSchema?: { name: string; values: string[] }[]
  variantCount?: number
  totalStock?: number
  minPrice?: number | string | null
  maxPrice?: number | string | null
  lowStockCount?: number
}
export type PublicSku = {
  id: string
  skuCode: string
  optionLabel?: string | null
  optionValues?: Record<string, string>
  price: number | string
  compareAtPrice?: number | string | null
  currency?: string | null
  available: number
  imageId?: string | null
}
export type PublicProduct = {
  id: string
  slug: string
  nameTh: string
  nameEn?: string | null
  nameZh?: string | null
  descriptionTh?: string | null
  descriptionEn?: string | null
  descriptionZh?: string | null
  categoryId?: number | null
  brand?: string | null
  basePrice?: number | string | null
  compareAtPrice?: number | string | null
  currency?: string | null
  skus: PublicSku[]
  images: ProductImage[]
  optionSchema?: { name: string; values: string[] }[]
  /** All-time sold quantity; filled by the shop product list (null elsewhere). */
  soldCount?: number | null
  /** ISO timestamp the product was created (public storefront DTO). */
  createdAt?: string | null
}
/** GET /api/products/best-sellers row: public product fields + rank / sold quantity / shop. rating & reviewCount are null until reviews exist. */
export type BestSellerProduct = {
  rank: number
  soldCount: number
  id: string
  slug: string
  nameTh: string
  nameEn?: string | null
  nameZh?: string | null
  categoryId?: number | null
  brand?: string | null
  basePrice?: number | string | null
  compareAtPrice?: number | string | null
  currency?: string | null
  skus: PublicSku[]
  images: ProductImage[]
  shopSlug: string
  shopName: string
  shopNameEn?: string | null
  rating?: number | null
  reviewCount?: number | null
}
export type BestSellerQuery = { categoryId?: number; categorySlug?: string; sort?: 'best_selling' | 'top_rated'; shopSlug?: string; period?: '7d' | '30d' | 'all'; minPrice?: number; maxPrice?: number; page?: number; size?: number }
export const bestSellerApi = {
  list: (query: BestSellerQuery = {}) => {
    const params = new URLSearchParams()
    Object.entries(query).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') params.set(key, String(value)) })
    const qs = params.toString()
    return request<ProductPage<BestSellerProduct>>(`/api/products/best-sellers${qs ? `?${qs}` : ''}`)
  },
}
export type RecommendationSort = 'featured' | 'best' | 'price_asc' | 'price_desc' | 'rating' | 'newest'
export type RecommendationQuery = { categoryId?: number; minPrice?: number; maxPrice?: number; minRating?: number; brands?: string; sort?: RecommendationSort; page?: number; size?: number }
const VIEW_SESSION_KEY = 'tsf.viewSession'
function viewSessionId() {
  try {
    let id = localStorage.getItem(VIEW_SESSION_KEY)
    if (!id || !/^[A-Za-z0-9_-]{8,100}$/.test(id)) {
      id = (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`).replace(/[^A-Za-z0-9_-]/g, '')
      localStorage.setItem(VIEW_SESSION_KEY, id)
    }
    return id
  } catch { return '' }
}
/** Public call that sends the bearer token only when already signed in (personalization); on 401 retries anonymously, never redirects. */
async function optionalAuthRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const session = viewSessionId()
  if (session) headers.set('X-View-Session', session)
  if (accessToken) {
    const authed = new Headers(headers)
    authed.set('Authorization', `Bearer ${accessToken}`)
    try { return await request<T>(path, { ...init, headers: authed }) } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error
    }
  }
  return request<T>(path, { ...init, headers })
}
export const recommendationApi = {
  list: (query: RecommendationQuery = {}) => {
    const params = new URLSearchParams()
    Object.entries(query).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') params.set(key, String(value)) })
    const qs = params.toString()
    return optionalAuthRequest<ProductPage<BestSellerProduct>>(`/api/products/recommendations${qs ? `?${qs}` : ''}`)
  },
  recordView: (productId: string) => optionalAuthRequest<void>('/api/products/views', { method: 'POST', body: JSON.stringify({ productId }) }),
  recentlyViewed: (size = 12) => request<BestSellerProduct[]>(`/api/products/recently-viewed?size=${size}`, {}, true, false),
}
export type ProductReview = { id: string; rating: number; comment?: string | null; reviewerName: string; createdAt: string }
export type ProductReviewPage = { items: ProductReview[]; page: number; size: number; total: number; ratingAvg?: number | null; reviewCount: number }
export const reviewApi = {
  list: (productIdOrSlug: string, query: { shopSlug?: string; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams()
    Object.entries(query).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') params.set(key, String(value)) })
    const qs = params.toString()
    return request<ProductReviewPage>(`/api/products/${encodeURIComponent(productIdOrSlug)}/reviews${qs ? `?${qs}` : ''}`)
  },
  create: (body: { orderItemId: string; rating: number; comment?: string }) =>
    request<ProductReview>('/api/me/reviews', { method: 'POST', body: JSON.stringify(body) }, true),
}
export type ProductPage<T> = { items: T[]; page: number; size: number; total: number }
export type ProductImportError = { row: number; message: string; productKey?: string | null; skuCode?: string | null }
export type ProductImportResult = { created: number; failed: number; skuCreated?: number; errors: ProductImportError[] }
export type ProductImportAccepted = { jobId: string; total: number }
export type ProductImportJob = {
  jobId: string
  shopId: number
  userId: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  total: number
  processed: number
  created: number
  skuCreated?: number
  failed: number
  errors: ProductImportError[]
  currentRow: number | null
  startedAt: string | null
  finishedAt: string | null
}
export type SkuWrite = {
  id?: string
  skuCode: string
  barcode?: string | null
  optionLabel?: string | null
  optionValues?: Record<string, string>
  price: number
  compareAtPrice?: number | null
  weightGrams?: number | null
  initialStock?: number | null
  costPrice?: number | null
  imageId?: string | null
  active?: boolean
  safetyStock?: number | null
}
export type ProductWrite = {
  nameTh: string
  nameEn?: string | null
  nameZh?: string | null
  descriptionTh?: string | null
  descriptionEn?: string | null
  descriptionZh?: string | null
  categoryId?: number | null
  productType: ProductType
  brand?: string | null
  basePrice?: number | null
  compareAtPrice?: number | null
  weightGrams?: number | null
  optionSchema?: { name: string; values: string[] }[]
  confirmRemoveStocked?: boolean
  skus: SkuWrite[]
}
export type StockAdjustment = { skuId: string; entryType: string; onHand: number; available: number; safetyStock: number; version: number }
export type LedgerEntry = {
  id: number
  skuId: string
  skuCode: string
  entryType: string
  quantityDelta: number
  onHandBefore: number
  onHandAfter: number
  reason?: string | null
  createdAt?: string | null
  actorName?: string | null
  reservedBefore?: number
  reservedAfter?: number
}

function productQuery(query: ProductListQuery) {
  const search = new URLSearchParams()
  if (query.q) search.set('q', query.q)
  if (query.status) search.set('status', query.status)
  if (query.categoryId != null) search.set('categoryId', String(query.categoryId))
  if (query.brand) search.set('brand', query.brand)
  if (query.stock) search.set('stock', query.stock)
  if (query.sort) search.set('sort', query.sort)
  if (query.page != null) search.set('page', String(query.page))
  if (query.size != null) search.set('size', String(query.size))
  if (query.priceBands) search.set('priceBands', query.priceBands)
  const text = search.toString()
  return text ? `?${text}` : ''
}

function shopProducts(shopId: number, path = '') {
  return `/api/merchant/shops/${shopId}${path}`
}

export async function authorizedBlob(path: string): Promise<Blob> {
  if (!accessToken) await refreshSession()
  const load = async () => {
    const headers = new Headers()
    withLanguage(headers)
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
    return fetch(`${API_URL}${path}`, { headers, credentials: 'include' })
  }
  let response = await load()
  if (response.status === 401) {
    await refreshSession()
    response = await load()
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({ code: 'REQUEST_FAILED', message: 'โหลดรูปไม่สำเร็จ', fieldErrors: [] as ApiFieldError[] }))
    throw new ApiError(error.code || 'REQUEST_FAILED', error.message || 'โหลดรูปไม่สำเร็จ', response.status, undefined, error.fieldErrors)
  }
  return response.blob()
}

function uploadForm<T>(path: string, body: FormData, onProgress?: (ratio: number) => void): Promise<T> {
  const send = () => new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_URL}${path}`)
    xhr.withCredentials = true
    xhr.setRequestHeader('Accept-Language', acceptLanguageHeader())
    if (accessToken) xhr.setRequestHeader('Authorization', `Bearer ${accessToken}`)
    xhr.upload.onprogress = (event) => {
      if (onProgress && event.lengthComputable) onProgress(event.loaded / event.total)
    }
    xhr.onload = () => {
      if (xhr.status === 401) {
        reject(new ApiError('UNAUTHORIZED', 'กรุณาเข้าสู่ระบบใหม่', 401))
        return
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        let error: { code?: string; message?: string; jobId?: string; fieldErrors?: ApiFieldError[] } = { code: xhr.status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED', message: 'อัปโหลดไม่สำเร็จ' }
        try { error = JSON.parse(xhr.responseText) } catch { /* keep fallback */ }
        if (xhr.status === 401 && !window.location.pathname.startsWith('/login')) {
          const next = `${window.location.pathname}${window.location.search}`
          window.location.assign(`/login?next=${encodeURIComponent(next)}`)
        }
        reject(new ApiError(error.code || (xhr.status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED'), error.message || 'อัปโหลดไม่สำเร็จ', xhr.status, error.jobId, error.fieldErrors))
        return
      }
      resolve(JSON.parse(xhr.responseText) as T)
    }
    xhr.onerror = () => reject(new ApiError('NETWORK_ERROR', 'เชื่อมต่อระบบไม่ได้ กรุณาตรวจสอบว่า Backend กำลังทำงาน', 0))
    xhr.send(body)
  })
  return (async () => {
    if (!accessToken) await refreshSession()
    try {
      return await send()
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        await refreshSession()
        return send()
      }
      throw error
    }
  })()
}

/** AG2: CUSTOM theme media library (images / MP4 / WOFF2). Errors keep the backend code (e.g. THEME_MEDIA_QUOTA_EXCEEDED). */
export const themeMediaApi = {
  upload: (kind: ThemeMediaKind, file: File, shopId?: number | null, onProgress?: (ratio: number) => void) => {
    const body = new FormData()
    body.append('kind', kind)
    body.append('file', file)
    return uploadForm<ThemeMediaItem>(withShop('/api/merchant/settings/theme/media', shopId), body, onProgress)
  },
  list: (kind?: ThemeMediaKind, shopId?: number | null) => {
    const path = withShop('/api/merchant/settings/theme/media', shopId)
    return request<ThemeMediaItem[]>(kind ? `${path}${path.includes('?') ? '&' : '?'}kind=${kind}` : path, {}, true)
  },
  remove: (id: string, shopId?: number | null) =>
    request<void>(withShop(`/api/merchant/settings/theme/media/${id}`, shopId), { method: 'DELETE' }, true),
}

export const productApi = {
  brands: (shopId: number) => request<ProductBrand[]>(shopProducts(shopId, '/products/brands'), {}, true),
  list: (shopId: number, query: ProductListQuery = {}) => request<ProductPage<Product>>(shopProducts(shopId, `/products${productQuery(query)}`), {}, true),
  get: (shopId: number, productId: string) => request<Product>(shopProducts(shopId, `/products/${productId}`), {}, true),
  create: (shopId: number, payload: ProductWrite) => request<Product>(shopProducts(shopId, '/products'), { method: 'POST', body: JSON.stringify(payload) }, true),
  update: (shopId: number, productId: string, payload: ProductWrite) => request<Product>(shopProducts(shopId, `/products/${productId}`), { method: 'PUT', body: JSON.stringify(payload) }, true),
  useAutoTranslation: (shopId: number, productId: string, payload: { field: 'name' | 'description'; locale: 'en' | 'zh' }) =>
    request<Product>(shopProducts(shopId, `/products/${productId}/translations/use-auto`), { method: 'POST', body: JSON.stringify(payload) }, true),
  retryTranslation: (shopId: number, productId: string, payload: { field: string; locale: 'en' | 'zh' }) =>
    request<Product>(shopProducts(shopId, `/products/${productId}/translations/retry`), { method: 'POST', body: JSON.stringify(payload) }, true),
  publish: (shopId: number, productId: string) => request<Product>(shopProducts(shopId, `/products/${productId}/publish`), { method: 'POST' }, true),
  archive: (shopId: number, productId: string) => request<Product>(shopProducts(shopId, `/products/${productId}/archive`), { method: 'POST' }, true),
  unarchive: (shopId: number, productId: string) => request<Product>(shopProducts(shopId, `/products/${productId}/unarchive`), { method: 'POST' }, true),
  addImage: (shopId: number, productId: string, file: File, onProgress?: (ratio: number) => void) => {
    const body = new FormData()
    body.append('file', file)
    return uploadForm<Product>(shopProducts(shopId, `/products/${productId}/images`), body, onProgress)
  },
  deleteImage: (shopId: number, productId: string, imageId: string) => request<Product>(shopProducts(shopId, `/products/${productId}/images/${imageId}`), { method: 'DELETE' }, true),
  orderImages: (shopId: number, productId: string, imageIds: string[], primaryImageId: string) => request<Product>(shopProducts(shopId, `/products/${productId}/images/order`), { method: 'PUT', body: JSON.stringify({ imageIds, primaryImageId }) }, true),
  adjustStock: (shopId: number, skuId: string, payload: { entryType: 'RECEIVE' | 'ISSUE' | 'ADJUST' | 'RESERVE' | 'RELEASE'; quantityDelta: number; reason?: string; version: number; idempotencyKey?: string }) => request<StockAdjustment>(shopProducts(shopId, `/skus/${skuId}/stock-adjustments`), { method: 'POST', body: JSON.stringify(payload) }, true),
  adjustStockBulk: (shopId: number, productId: string, payload: { idempotencyKey?: string; items: { skuId: string; entryType: 'RECEIVE' | 'ISSUE' | 'ADJUST' | 'RESERVE' | 'RELEASE'; quantityDelta: number; version: number; reason?: string }[] }) => request<StockAdjustment[]>(shopProducts(shopId, `/products/${productId}/stock-adjustments/bulk`), { method: 'POST', body: JSON.stringify(payload) }, true),
  variantCombinations: (shopId: number, payload: { skuPrefix?: string; optionGroups: { name: string; values: { value: string }[] }[] }) =>
    request<{ optionLabel: string; optionValues: Record<string, string>; skuCode: string }[]>(shopProducts(shopId, '/products/variant-combinations'), { method: 'POST', body: JSON.stringify(payload) }, true),
  checkSkus: (shopId: number, codes: string[], excludeProductId?: string) => {
    const search = new URLSearchParams()
    if (codes.length) search.set('codes', codes.join(','))
    if (excludeProductId) search.set('excludeProductId', excludeProductId)
    return request<{ taken: string[] }>(shopProducts(shopId, `/skus/check?${search.toString()}`), {}, true)
  },
  ledger: (shopId: number, productId: string) => request<LedgerEntry[]>(shopProducts(shopId, `/products/${productId}/ledger`), {}, true),
  exportExcel: async (shopId: number) => {
    const blob = await authorizedBlob(shopProducts(shopId, '/products/export'))
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'products.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  },
  downloadTemplate: async (shopId: number) => {
    const blob = await authorizedBlob(shopProducts(shopId, '/products/import-template'))
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'product-import-template.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  },
  importExcel: (shopId: number, file: File, onUploadProgress?: (percent: number) => void) => {
    const body = new FormData()
    body.append('file', file)
    return uploadForm<ProductImportAccepted>(shopProducts(shopId, '/products/import'), body, (ratio) => onUploadProgress?.(Math.round(ratio * 100)))
  },
  importStatus: (shopId: number, jobId: string) => request<ProductImportJob>(shopProducts(shopId, `/products/import/${jobId}`), {}, true),
  importBatch: (shopId: number, payloads: ProductWrite[]) => request<ProductImportResult>(shopProducts(shopId, '/products/batch'), { method: 'POST', body: JSON.stringify(payloads) }, true),
}

/* ---------------------------------------------------------------- promotions (V27) */

export type PromotionType = 'PERCENT' | 'FIXED_AMOUNT' | 'FREE_SHIPPING'
/** Computed by the backend in Asia/Bangkok: EXPIRED > PAUSED > UPCOMING > ACTIVE. */
export type PromotionStatus = 'UPCOMING' | 'ACTIVE' | 'PAUSED' | 'EXPIRED'

export type Promotion = {
  id: string
  code: string | null
  name: string
  type: PromotionType
  value: number
  minOrderAmount: number | null
  maxDiscountAmount: number | null
  /** ISO instant (UTC). */
  startsAt: string
  endsAt: string | null
  usageLimitTotal: number | null
  usageLimitPerBuyer: number | null
  showOnStorefront: boolean
  active: boolean
  status: PromotionStatus
  usedCount: number
  discountTotal: number
  createdAt: string
  updatedAt: string
}

export type PromotionSummary = {
  total: number; active: number; upcoming: number; paused: number; expired: number
  percent: number; fixedAmount: number; freeShipping: number
  usageCount: number; discountTotal: number
}

export type PromotionList = { items: Promotion[]; page: number; size: number; total: number; totalPages: number; summary: PromotionSummary }

export type PromotionQuery = { q?: string; status?: PromotionStatus | ''; type?: PromotionType[]; page?: number; size?: number }

/** Dates: local "YYYY-MM-DDTHH:mm" (read as Asia/Bangkok) or ISO with offset. FREE_SHIPPING ignores value. */
export type PromotionWrite = {
  name: string
  code: string
  type: PromotionType
  value: number | null
  minOrderAmount: number | null
  maxDiscountAmount: number | null
  startsAt: string
  endsAt: string | null
  usageLimitTotal: number | null
  usageLimitPerBuyer: number | null
  showOnStorefront: boolean
  active?: boolean
}

/** Storefront coupon card: safe fields only. */
export type PublicPromotion = {
  code: string
  name: string
  type: PromotionType
  value: number
  minOrderAmount: number | null
  maxDiscountAmount: number | null
  startsAt: string
  endsAt: string | null
}

function promotionQuery({ q, status, type, page, size }: PromotionQuery) {
  const search = new URLSearchParams()
  if (q?.trim()) search.set('q', q.trim())
  if (status) search.set('status', status)
  if (type?.length) search.set('type', type.join(','))
  if (page) search.set('page', String(page))
  if (size) search.set('size', String(size))
  const text = search.toString()
  return text ? `?${text}` : ''
}

/** /api/merchant/shops/{shopId}/promotions ? PROMOTION_READ (reads) / PROMOTION_MANAGE (writes). */
/**
 * Merchant coupons = Supitcha's primary API /api/merchant/shops/{shopId}/coupons (R1: PROMOTION_READ / PROMOTION_MANAGE,
 * showOnStorefront, status, summary, delete). The N2 page keeps its Promotion / PromotionList / PromotionWrite shapes;
 * this client maps field names both ways (minSubtotal <-> minOrderAmount, maxDiscount <-> maxDiscountAmount,
 * usageLimitPerCustomer <-> usageLimitPerBuyer, FIXED <-> FIXED_AMOUNT, totalItems -> total, disable/enable = pause/resume).
 */
type CouponDto = {
  id: string; code: string; name: string; type: 'PERCENT' | 'FIXED' | 'FREE_SHIPPING'; value: Money
  maxDiscount: Money | null; minSubtotal: Money | null; startsAt: string; endsAt: string | null
  usageLimitTotal: number | null; usageLimitPerCustomer: number | null; usedCount: number; active: boolean
  showOnStorefront: boolean; discountTotal: Money; status: PromotionStatus; createdAt: string; updatedAt: string
}
type CouponListDto = { items: CouponDto[]; page: number; size: number; totalItems: number; totalPages: number; summary: PromotionSummary }
const optionalNumber = (value: Money | null | undefined) => (value == null ? null : Number(value))
const fromCoupon = (row: CouponDto): Promotion => ({
  id: row.id, code: row.code, name: row.name, type: row.type === 'FIXED' ? 'FIXED_AMOUNT' : row.type, value: Number(row.value),
  minOrderAmount: optionalNumber(row.minSubtotal), maxDiscountAmount: optionalNumber(row.maxDiscount),
  startsAt: row.startsAt, endsAt: row.endsAt, usageLimitTotal: row.usageLimitTotal, usageLimitPerBuyer: row.usageLimitPerCustomer,
  showOnStorefront: row.showOnStorefront, active: row.active, status: row.status, usedCount: row.usedCount,
  discountTotal: Number(row.discountTotal ?? 0), createdAt: row.createdAt, updatedAt: row.updatedAt,
})
/** Form dates are local "YYYY-MM-DDTHH:mm" in Asia/Bangkok; the coupon API takes ISO instants. */
const bangkokInstant = (value: string | null) => {
  if (!value) return null
  if (/[zZ]|[+-]\d{2}:\d{2}$/.test(value)) return value
  return `${value.length === 16 ? `${value}:00` : value}+07:00`
}
const toCoupon = (payload: PromotionWrite) => ({
  code: payload.code, name: payload.name, type: payload.type === 'FIXED_AMOUNT' ? 'FIXED' : payload.type,
  value: payload.type === 'FREE_SHIPPING' ? 0 : payload.value,
  maxDiscount: payload.type === 'FIXED_AMOUNT' ? null : payload.maxDiscountAmount, minSubtotal: payload.minOrderAmount,
  startsAt: bangkokInstant(payload.startsAt), endsAt: bangkokInstant(payload.endsAt),
  usageLimitTotal: payload.usageLimitTotal, usageLimitPerCustomer: payload.usageLimitPerBuyer,
  active: payload.active ?? true, showOnStorefront: payload.showOnStorefront,
})
const COUPON_FIELDS: Record<string, string> = { minSubtotal: 'minOrderAmount', maxDiscount: 'maxDiscountAmount', usageLimitPerCustomer: 'usageLimitPerBuyer' }
const COUPON_CODES: Record<string, string> = { COUPON_IN_USE: 'PROMOTION_IN_USE', COUPON_NOT_FOUND: 'PROMOTION_NOT_FOUND' }
/** Keep the N2 page's error handling (PROMOTION_IN_USE, form field names) working on the coupon API. */
function couponCall<T>(call: Promise<T>): Promise<T> {
  return call.catch((error: unknown) => {
    if (!(error instanceof ApiError)) throw error
    const fieldErrors = error.fieldErrors.map((item) => ({ ...item, field: COUPON_FIELDS[item.field] ?? item.field }))
    throw new ApiError(COUPON_CODES[error.code] ?? error.code, error.message, error.status, error.jobId, fieldErrors, error.details)
  })
}
const couponPath = (shopId: number, path = '') => shopProducts(shopId, `/coupons${path}`)

export const promotionApi = {
  list: (shopId: number, query: PromotionQuery = {}) => couponCall(request<CouponListDto>(couponPath(shopId, promotionQuery(query)), { cache: 'no-store' }, true))
    .then((body): PromotionList => ({ items: body.items.map(fromCoupon), page: body.page, size: body.size, total: body.totalItems, totalPages: body.totalPages, summary: body.summary })),
  get: (shopId: number, id: string) => couponCall(request<CouponDto>(couponPath(shopId, `/${id}`), { cache: 'no-store' }, true)).then(fromCoupon),
  create: (shopId: number, payload: PromotionWrite) => couponCall(request<CouponDto>(couponPath(shopId), { method: 'POST', body: JSON.stringify(toCoupon(payload)) }, true)).then(fromCoupon),
  update: (shopId: number, id: string, payload: PromotionWrite) => couponCall(request<CouponDto>(couponPath(shopId, `/${id}`), { method: 'PUT', body: JSON.stringify(toCoupon(payload)) }, true)).then(fromCoupon),
  pause: (shopId: number, id: string) => couponCall(request<CouponDto>(couponPath(shopId, `/${id}/disable`), { method: 'POST' }, true)).then(fromCoupon),
  resume: (shopId: number, id: string) => couponCall(request<CouponDto>(couponPath(shopId, `/${id}/enable`), { method: 'POST' }, true)).then(fromCoupon),
  remove: (shopId: number, id: string) => couponCall(request<void>(couponPath(shopId, `/${id}`), { method: 'DELETE' }, true)),
}

// ---------------------------------------------------------------------------
// Cart / checkout / buyer orders (backend: cart/, order/, payment/ ? Phase 1)
// ---------------------------------------------------------------------------
export type Money = number | string
export type CartLineStatus = 'OK' | 'PRICE_CHANGED' | 'INSUFFICIENT_STOCK' | 'OUT_OF_STOCK' | 'PRODUCT_UNAVAILABLE'
export type CartTotals = { subtotal: Money; discount: Money; shippingFee: Money; shippingDiscount: Money; grandTotal: Money }
export type CartCounts = { lineCount: number; itemQuantity: number; selectedLineCount: number; selectedQuantity: number }
export type CartLine = {
  id: string; skuId: string; productId: string; productName: string; productSlug: string; skuCode: string
  optionLabel?: string | null; options?: Record<string, string> | null; imageUrl?: string | null
  quantity: number; selected: boolean; unitPrice: Money; priceSnapshot: Money; compareAtPrice?: Money | null
  priceChanged: boolean; lineTotal: Money; availableQuantity: number; maxQuantity: number; status: CartLineStatus
  /** V43: product-configurator snapshot; absent for normal lines. */
  configuration?: ConfigurationSnapshot | null
}
/** V43: server-quoted configurator snapshot stored on a cart line / order item (labels are plain text). */
export type ConfigurationSnapshot = {
  version?: number; selections?: Record<string, unknown>; configDelta?: Money; currency?: string; configHash?: string
  lines?: { stepId: string; stepLabel?: Record<string, string> | null; optionId?: string | null; label?: Record<string, string> | null; text?: string | null; priceDelta?: Money }[]
}
export type CartCoupon = {
  code: string; type: 'PERCENT' | 'FIXED' | 'FREE_SHIPPING'; value: Money; maxDiscount?: Money | null; minSubtotal?: Money | null
  applicable: boolean; reason?: string | null; message?: string | null; discountAmount: Money; shippingDiscountAmount: Money
}
export type CartShippingOption = { id: string; name: string; carrierCode?: string | null; feeType: string; fee: Money; estimatedDaysMin?: number | null; estimatedDaysMax?: number | null }
export type CartShipping = {
  methodId?: string | null; methodName?: string | null; feeType?: string | null; autoSelected: boolean; fee: Money
  freeShippingThreshold?: Money | null; amountToFreeShipping?: Money | null; totalWeightGrams: number; options: CartShippingOption[]
}
export type CartGroup = { shopId: number; shopName: string; shopSlug: string; items: CartLine[]; coupon?: CartCoupon | null; shipping?: CartShipping | null; totals: CartTotals; selectedQuantity: number }
export type CartActionResult = { itemId?: string; skuId?: string; requestedQuantity: number; appliedQuantity: number; adjusted: boolean; code?: string | null; message?: string | null }
export type CartMergeNote = { skuId: string; code: string; message: string; requestedQuantity: number; appliedQuantity: number }
export type Cart = { currency: string; groups: CartGroup[]; summary: CartTotals; counts: CartCounts; expiresAt?: string | null; lastAction?: CartActionResult | null; mergeNotes?: CartMergeNote[] | null }
export type CartIssue = { code: string; severity: 'ERROR' | 'WARNING' | 'INFO'; shopId?: number | null; itemId?: string | null; skuId?: string | null; field?: string | null; message: string }
export type CartValidation = { valid: boolean; checkedAt: string; issues: CartIssue[]; cart: Cart }
export type CartLinePatch = { quantity?: number; selected?: boolean; skuId?: string }

/** Converts API money (number or numeric string) to a number; null/invalid → 0. */
export const moneyValue = (value: Money | null | undefined) => {
  const number = Number(value ?? 0)
  return Number.isFinite(number) ? number : 0
}

/** Signed-in buyers only (R2): the guest cart (/api/guest-cart, X-Cart-Token) was removed. */
function cartCall<T>(path: string, init: RequestInit = {}): Promise<T> {
  return request<T>(`/api/cart${path}`, init, true)
}
const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) })

export const cartApi = {
  get: () => cartCall<Cart>(''),
  add: (skuId: string, quantity: number, capToAvailable = true) => cartCall<Cart>('/items', json('POST', { skuId, quantity, capToAvailable })),
  /** V43: configured line; the server re-quotes the selections (flag-gated) and keys the line by its config hash. */
  addConfigured: (skuId: string, quantity: number, configuration: { selections: Record<string, unknown>; version?: number }, capToAvailable = true) =>
    cartCall<Cart>('/items', json('POST', { skuId, quantity, capToAvailable, configuration })),
  update: (itemId: string, patch: CartLinePatch) => cartCall<Cart>(`/items/${itemId}`, json('PATCH', patch)),
  remove: (itemId: string) => cartCall<Cart>(`/items/${itemId}`, { method: 'DELETE' }),
  removeMany: (itemIds: string[]) => cartCall<Cart>('/items/remove', json('POST', { itemIds })),
  select: (itemIds: string[], selected: boolean) => cartCall<Cart>('/selection', json('PUT', { itemIds, selected })),
  applyCoupon: (shopId: number, code: string) => cartCall<Cart>(`/shops/${shopId}/coupon`, json('POST', { code })),
  removeCoupon: (shopId: number) => cartCall<Cart>(`/shops/${shopId}/coupon`, { method: 'DELETE' }),
  chooseShipping: (shopId: number, shippingMethodId: string | null) => cartCall<Cart>(`/shops/${shopId}/shipping-method`, json('PUT', { shippingMethodId })),
  acceptPrices: () => cartCall<Cart>('/accept-prices', { method: 'POST' }),
  validate: () => cartCall<CartValidation>('/validate', { method: 'POST' }),
}

/** Coupon wallet (V31): a buyer collects a public shop code on the storefront before the cart accepts it. */
export type CollectedCouponStatus = 'USABLE' | 'UPCOMING' | 'USED' | 'EXHAUSTED' | 'EXPIRED' | 'INACTIVE'
export type CollectedCoupon = {
  shopId: number; shopName: string; shopSlug: string; code: string; name: string; type: PromotionType; value: number
  minOrderAmount?: number | null; maxDiscountAmount?: number | null; startsAt?: string | null; endsAt?: string | null
  collectedAt: string; status: CollectedCouponStatus
}
export type CollectCouponResult = { code: string; shopId: number; alreadyCollected: boolean; collectedAt: string }
export const walletApi = {
  list: (shopId?: number) => request<CollectedCoupon[]>(`/api/me/coupons${shopId != null ? `?shopId=${shopId}` : ''}`, { cache: 'no-store' }, true),
  collect: (shopId: number, code: string) => request<CollectCouponResult>('/api/me/coupons', json('POST', { shopId, code: code.trim().toUpperCase() }), true),
}

/** Buyer address book (V1 buyer_addresses): /api/me/addresses — BUYER_ADDRESS_READ / BUYER_ADDRESS_UPDATE. */
export type BuyerAddress = {
  id: string; label?: string | null; recipientName: string; phone: string
  addressLine1: string; addressLine2?: string | null; subdistrict?: string | null
  district: string; province: string; postalCode: string; countryCode?: string
  isDefault: boolean; updatedAt: string
}
export type BuyerAddressRequest = {
  label?: string; recipientName: string; phone: string; addressLine1: string; addressLine2?: string
  subdistrict?: string; district: string; province: string; postalCode: string; isDefault?: boolean
}
export const addressBookApi = {
  list: () => request<BuyerAddress[]>('/api/me/addresses', { cache: 'no-store' }, true),
  create: (body: BuyerAddressRequest) => request<BuyerAddress>('/api/me/addresses', json('POST', body), true),
  update: (id: string, body: BuyerAddressRequest) => request<BuyerAddress>(`/api/me/addresses/${id}`, json('PUT', body), true),
  remove: (id: string) => request<void>(`/api/me/addresses/${id}`, { method: 'DELETE' }, true),
}

export type CheckoutAddress = {
  recipientName: string; phone: string; addressLine1: string; addressLine2?: string
  subdistrict?: string; district: string; province: string; postalCode: string
}
export type OrderItem = { id: string; productId: string; skuId: string; productName: string; optionLabel?: string | null; skuCode?: string | null; imageUrl?: string | null; unitPrice: Money; quantity: number; discountAmount?: Money | null; lineTotal: Money; configuration?: ConfigurationSnapshot | null }
export type OrderDetail = {
  id: string; orderNumber: string; shopId: number; shopName: string; status: string; paymentStatus: string; fulfillmentStatus?: string | null
  currency: string; subtotal: Money; discountTotal: Money; shippingFee: Money; grandTotal: Money; couponCode?: string | null
  shipping?: { methodId?: string | null; methodName?: string | null; carrierCode?: string | null } | null
  placedAt?: string | null; items?: OrderItem[] | null; itemCount?: number | null; totalQuantity?: number | null
  shipment?: { status?: string | null; carrierCode?: string | null; trackingNumber?: string | null; packedAt?: string | null; dispatchedAt?: string | null; deliveredAt?: string | null } | null
  shippingAddress?: { recipientName?: string | null; phone?: string | null; addressLine1?: string | null; addressLine2?: string | null; subdistrict?: string | null; district?: string | null; province?: string | null; postalCode?: string | null; masked?: boolean } | null
  buyerNote?: string | null; cancelReason?: string | null; paidAt?: string | null; cancelledAt?: string | null; completedAt?: string | null
  allowedTransitions?: string[] | null; paidAmount?: Money | null; refundedAmount?: Money | null
  // merchant order management (Y2)
  channel?: string | null; merchantConfirmStatus?: string | null; taxTotal?: Money | null; buyerUserId?: string | null; shopCustomerId?: string | null
  statusBeforeRefund?: string | null; merchantConfirmedAt?: string | null; version?: number; itemsHidden?: boolean; refundableAmount?: Money | null
  awaitingAcceptance?: boolean | null; paymentMethod?: string | null; codFee?: Money | null; termsAcceptedAt?: string | null
  /** Merchant-only (AB1); never render on buyer pages. */
  internalNote?: string | null
  tags?: { id: string; name: string; color: string | null; usageCount?: number }[] | null
}
export type OrderLinePreview = {
  productName: string
  optionLabel?: string | null
  imageUrl?: string | null
  quantity: number
}
export type OrderSummary = {
  id: string; orderNumber: string; shopId: number; shopName: string; status: string; paymentStatus: string
  fulfillmentStatus?: string | null; grandTotal: Money; currency: string; itemCount?: number | null
  totalQuantity?: number | null; placedAt?: string | null; paidAt?: string | null; version: number
  awaitingAcceptance: boolean; paymentMethod?: string | null
  /** Merchant-only optional fields (AB1). */
  tags?: { id: string; name: string; color: string | null; usageCount?: number }[] | null
  trackingNumber?: string | null; carrierCode?: string | null
  /** Merchant + accepted only (AD3). */
  customerName?: string | null; customerKey?: string | null
  /** Line previews for list cards (image + name); expand under thumb shows all. */
  previewItems?: OrderLinePreview[] | null
}
export type OrderPage = { items: OrderSummary[]; page: number; size: number; totalItems: number; totalPages: number }
export type OrderTimelineEntry = { id: number; event: string; fromStatus?: string | null; toStatus?: string | null; actorType?: string | null; reason?: string | null; at: string }
export type PaymentProofView = { id: string; status: string; referenceNo?: string | null; amountDeclared?: Money | null; amountMismatch?: boolean; transferredAt?: string | null; fileUrl?: string | null; contentType?: string | null; uploadedAt?: string | null; rejectReason?: string | null; bankName?: string | null; reviewedAt?: string | null; proofType?: string | null }
export type PaymentView = { id: string; provider: string; status: string; amount: Money; attemptNo: number; createdAt?: string | null; proofs?: PaymentProofView[] | null; currency?: string | null; capturedAt?: string | null; failureCode?: string | null; history?: PaymentStatusEntry[] | null }
export type PaymentStatusEntry = { id?: number | null; event: string; fromStatus?: string | null; toStatus?: string | null; actorType?: string | null; actorUserId?: string | null; reason?: string | null; metadata?: Record<string, unknown> | null; at: string }
export type CheckoutResult = { replayed: boolean; orders: OrderDetail[]; grandTotal: Money; currency: string }
export type PaymentAccount = { provider: string; displayName?: string | null; bankName?: string | null; accountNumber?: string | null; accountName?: string | null; promptPayId?: string | null; active: boolean; masked: boolean }
export type OrderPaymentInfo = { orderId: string; orderNumber: string; orderStatus: string; paymentStatus: string; amountDue: Money; currency: string; canSubmitSlip: boolean; accounts: PaymentAccount[]; payments?: PaymentView[] | null; paymentMethod?: string | null; codFee?: Money | null }

export type CheckoutRequestBody = {
  shippingAddress: CheckoutAddress; buyerNote?: string; acceptTerms?: boolean; paymentMethod?: 'BANK_TRANSFER' | 'COD'
  termsVersion?: string; shippingAddressId?: string
}
export type CheckoutExtras = { termsVersion?: string; shippingAddressId?: string }
export const checkoutApi = {
  /** idempotencyKey: 8?100 chars [A-Za-z0-9._:-]; reuse the same key when retrying the same attempt. */
  place: (
    idempotencyKey: string,
    shippingAddress: CheckoutAddress,
    buyerNote?: string,
    acceptTerms?: boolean,
    paymentMethod?: 'BANK_TRANSFER' | 'COD',
    extras?: CheckoutExtras,
  ) => {
    const body: CheckoutRequestBody = {
      shippingAddress,
      buyerNote: buyerNote || undefined,
      acceptTerms: acceptTerms ? true : undefined,
      paymentMethod: paymentMethod === 'COD' ? 'COD' : undefined,
    }
    if (extras?.termsVersion) body.termsVersion = extras.termsVersion
    if (extras?.shippingAddressId) body.shippingAddressId = extras.shippingAddressId
    return request<CheckoutResult>('/api/checkout', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(body),
    }, true)
  },
  paymentMethods: (shopId: number) => request<PaymentMethodsView>(`/api/checkout/payment-methods?shopId=${shopId}`, {}, true),
}
export const buyerOrderApi = {
  get: (orderId: string) => request<OrderDetail>(`/api/orders/${orderId}`, {}, true),
  paymentInfo: (orderId: string) => request<OrderPaymentInfo>(`/api/orders/${orderId}/payment-info`, {}, true),
  list: (params: {
    status?: string; page?: number; size?: number; q?: string
    paymentStatus?: string; fulfillmentStatus?: string; from?: string; to?: string
    sort?: string; direction?: string
    signal?: AbortSignal
  }) => {
    const query = new URLSearchParams()
    if (params.status) query.set('status', params.status)
    if (params.q?.trim()) query.set('q', params.q.trim())
    if (params.paymentStatus) query.set('paymentStatus', params.paymentStatus)
    if (params.fulfillmentStatus) query.set('fulfillmentStatus', params.fulfillmentStatus)
    if (params.from) query.set('from', params.from)
    if (params.to) query.set('to', params.to)
    query.set('page', String(params.page ?? 0)); query.set('size', String(params.size ?? 10))
    query.set('sort', params.sort || 'placedAt')
    query.set('direction', params.direction || 'desc')
    return request<OrderPage>(`/api/orders?${query}`, { signal: params.signal }, true)
  },
  timeline: (orderId: string) => request<OrderTimelineEntry[]>(`/api/orders/${orderId}/timeline`, {}, true),
  submitSlip: (orderId: string, form: FormData) => request<PaymentView>(`/api/orders/${orderId}/payments`, { method: 'POST', body: form }, true),
  cancel: (orderId: string, reason: string) => request<unknown>(`/api/orders/${orderId}/cancel`, { method: 'POST', body: JSON.stringify({ reason }) }, true),
  confirmReceived: (orderId: string) => request<unknown>(`/api/orders/${orderId}/confirm-received`, { method: 'POST', body: JSON.stringify({}) }, true),
  shipment: (orderId: string) => request<ShipmentDetail>(`/api/orders/${orderId}/shipment`, {}, true),
  payments: (orderId: string) => request<PaymentView[]>(`/api/orders/${orderId}/payments`, {}, true),
  refundRequest: (orderId: string, body: { reason: string; amount?: number }) =>
    request<TransitionResponse>(`/api/orders/${orderId}/refund-request`, { method: 'POST', body: JSON.stringify(body) }, true),
  refunds: (orderId: string) => request<RefundSummary>(`/api/orders/${orderId}/refunds`, {}, true),
  confirmRefundReceived: (orderId: string, refundId: string, note?: string) =>
    request<RefundActionResponse>(`/api/orders/${orderId}/refunds/${refundId}/confirm-received`, {
      method: 'POST', body: JSON.stringify(note ? { note } : {}),
    }, true),
  disputeRefund: (orderId: string, refundId: string, note: string) =>
    request<RefundActionResponse>(`/api/orders/${orderId}/refunds/${refundId}/dispute`, {
      method: 'POST', body: JSON.stringify({ note }),
    }, true),
}

/* ---------------------------------------------------------------- merchant shop settings (Y1) */
export type ShippingMethod = {
  id: string; shopId: number; name: string; carrierCode?: string | null
  feeType: 'FLAT' | 'WEIGHT' | 'FREE'; feeAmount?: number | null; extraFeePerKg?: number | null
  freeShippingMinAmount?: number | null; estimatedDaysMin?: number | null; estimatedDaysMax?: number | null; active: boolean
}
export type ShippingMethodInput = Omit<ShippingMethod, 'id' | 'shopId' | 'active'> & { active?: boolean }
export type PaymentAccountInput = { displayName?: string; bankName?: string; accountNumber?: string; accountName?: string; promptPayId?: string; active?: boolean }
export type PaymentMethodView = { method: 'BANK_TRANSFER' | 'COD'; enabled: boolean; fee?: number | null; maxOrderAmount?: number | null; accountsConfigured?: boolean | null; updatedAt?: string | null }
export type PaymentMethodsView = { shopId: number; methods: PaymentMethodView[] }
export type Carrier = { code: string; nameTh: string; nameEn: string; trackingPattern?: string | null; active: boolean; sortOrder: number }

const shopPath = (shopId: number, path: string) => `/api/merchant/shops/${shopId}${path}`

export const carrierApi = {
  list: () => request<Carrier[]>('/api/carriers', {}, true),
}

export const merchantShopSettingsApi = {
  shippingMethods: (shopId: number) => request<ShippingMethod[]>(shopPath(shopId, '/shipping-methods'), {}, true),
  createShippingMethod: (shopId: number, body: ShippingMethodInput) =>
    request<ShippingMethod>(shopPath(shopId, '/shipping-methods'), { method: 'POST', body: JSON.stringify(body) }, true),
  updateShippingMethod: (shopId: number, methodId: string, body: ShippingMethodInput) =>
    request<ShippingMethod>(shopPath(shopId, `/shipping-methods/${methodId}`), { method: 'PUT', body: JSON.stringify(body) }, true),
  setShippingMethodActive: (shopId: number, methodId: string, active: boolean) =>
    request<ShippingMethod>(shopPath(shopId, `/shipping-methods/${methodId}/${active ? 'enable' : 'disable'}`), { method: 'POST' }, true),
  paymentAccounts: (shopId: number) => request<PaymentAccount[]>(shopPath(shopId, '/payment-accounts'), {}, true),
  savePaymentAccount: (shopId: number, provider: 'BANK_TRANSFER' | 'PROMPTPAY', body: PaymentAccountInput) =>
    request<PaymentAccount>(shopPath(shopId, `/payment-accounts/${provider}`), { method: 'PUT', body: JSON.stringify(body) }, true),
  deactivatePaymentAccount: (shopId: number, provider: 'BANK_TRANSFER' | 'PROMPTPAY') =>
    request<PaymentAccount>(shopPath(shopId, `/payment-accounts/${provider}`), { method: 'DELETE' }, true),
  paymentMethods: (shopId: number) => request<PaymentMethodsView>(shopPath(shopId, '/payment-methods'), {}, true),
  saveCod: (shopId: number, body: { enabled: boolean; fee?: number | null; maxOrderAmount?: number | null }) =>
    request<PaymentMethodsView>(shopPath(shopId, '/payment-methods/cod'), { method: 'PUT', body: JSON.stringify(body) }, true),
}

/* ---------------------------------------------------------------- merchant order management (Y2) */
export type AllowedTransition = { toStatus?: string | null; action: string; reasonRequired: boolean; trackingRequired: boolean }
export type AllowedTransitions = { status?: string | null; paymentStatus?: string | null; fulfillmentStatus?: string | null; version: number; transitions: AllowedTransition[] }
export type TransitionBody = { reason?: string; carrierCode?: string; trackingNumber?: string; status?: string; provider?: string; amount?: number; paymentReference?: string; restock?: boolean; expectedVersion?: number; shippedAt?: string }
export type TransitionResponse = { changed: boolean; fromStatus: string; toStatus: string; order: OrderDetail }

function queryOf(params: Record<string, string | number | boolean | null | undefined>) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

const merchantOrders = (shopId: number, path = '') => shopPath(shopId, `/orders${path}`)

/** A1 GET /api/merchant/shops/{shopId}/dashboard/summary (ORDER_READ). date = optional YYYY-MM-DD, Asia/Bangkok day. */
export type MerchantDashboardRange = 'today' | '7d' | '30d' | '1y'
export type MerchantDashboardSummary = { range: string; from: string; to: string; days: number; date: string | null; timezone: string; currency: string; sales: number | string; paidOrderCount: number; orderCount: number; previousFrom: string; previousTo: string; previousSales: number | string; previousOrderCount: number; todaySales: number | string; todayPaidOrderCount: number; todayOrderCount: number; pendingOrders: number }
export type MerchantDashboardTopCustomer = {
  customerKey: string
  displayName: string
  orderCount: number
  totalAmount: number | string
  sharePercent: number | string
}
export type MerchantDashboardInsights = {
  range: string; from: string; to: string; days: number; timezone: string; currency: string; bucket: 'day' | 'week' | 'month'
  sales: number | string; paidOrderCount: number
  series: { start: string; end: string; sales: number | string; orders: number }[]
  paymentMethods: { method: string; orders: number; sales: number | string }[]
  topProducts: { productId: string | null; name: string; imageUrl: string | null; quantity: number; sales: number | string }[]
  topCustomers: MerchantDashboardTopCustomer[]
}
export const merchantDashboardApi = {
  /** period: { range } or { from, to } (YYYY-MM-DD, Bangkok, inclusive, max 3 years) or { date } */
  summary: (shopId: number, period: { range?: MerchantDashboardRange; from?: string; to?: string; date?: string } = {}) =>
    request<MerchantDashboardSummary>(`${shopPath(shopId, '/dashboard/summary')}${queryOf(period)}`, { cache: 'no-store' }, true),
  /** A12 GET .../dashboard/insights: same period + paid rule as summary. bucket = day (<=31 days) | week | month. */
  insights: (shopId: number, period: { range?: MerchantDashboardRange; from?: string; to?: string; date?: string } = {}) =>
    request<MerchantDashboardInsights>(`${shopPath(shopId, '/dashboard/insights')}${queryOf(period)}`, { cache: 'no-store' }, true),
}

/**
 * Merchant reports: GET /api/merchant/shops/{shopId}/reports/overview (REPORT_READ) and .../export.csv (REPORT_EXPORT).
 * Money fields are numbers or decimal strings; every *GrowthPct is null when the previous period has no base (show "—").
 * gmv = paid orders grand_total (placed_at, Asia/Bangkok); itemSales = paid order item totals; refundedAmount = COMPLETED
 * refunds by completed_at (separate, not subtracted); estimatedFee/Net are 5% ESTIMATES (not profit / not a payout).
 */
export type MerchantReportPeriod = { range?: MerchantDashboardRange; from?: string; to?: string }
export type MerchantReportTab = 'overview' | 'sales' | 'products' | 'orders' | 'customers' | 'promotions' | 'fees' | 'shipping'
type ReportMoney = number | string
type ReportGrowth = number | null
export type MerchantReportKpis = {
  gmv: ReportMoney; previousGmv: ReportMoney; gmvGrowthPct: ReportGrowth
  itemSales: ReportMoney; previousItemSales: ReportMoney; itemSalesGrowthPct: ReportGrowth
  paidOrderCount: number; previousPaidOrderCount: number; paidOrderGrowthPct: ReportGrowth
  allOrderCount: number; previousAllOrderCount: number; allOrderGrowthPct: ReportGrowth
  refundedAmount: ReportMoney; previousRefundedAmount: ReportMoney; refundedGrowthPct: ReportGrowth; refundCount: number
  estimatedFee: ReportMoney; previousEstimatedFee: ReportMoney; estimatedFeeGrowthPct: ReportGrowth
  estimatedNet: ReportMoney; previousEstimatedNet: ReportMoney; estimatedNetGrowthPct: ReportGrowth
}
export type MerchantReportCustomerSummary = {
  uniqueCustomers: number; previousUniqueCustomers: number; uniqueGrowthPct: ReportGrowth
  newCustomers: number; previousNewCustomers: number; newGrowthPct: ReportGrowth
  returningCustomers: number; previousReturningCustomers: number; returningGrowthPct: ReportGrowth
  averageOrdersPerCustomer: ReportMoney; previousAverageOrdersPerCustomer: ReportMoney; averageOrdersGrowthPct: ReportGrowth
  averageOrderValue: ReportMoney; previousAverageOrderValue: ReportMoney; averageOrderValueGrowthPct: ReportGrowth
}
export type MerchantReportPromotionSummary = {
  redemptionCount: number; previousRedemptionCount: number; redemptionGrowthPct: ReportGrowth
  discountTotal: ReportMoney; previousDiscountTotal: ReportMoney; discountGrowthPct: ReportGrowth
  paidOrdersWithCoupon: number; previousPaidOrdersWithCoupon: number; couponOrderGrowthPct: ReportGrowth
  attachRatePct: ReportMoney; previousAttachRatePct: ReportMoney; attachRateGrowthPct: ReportGrowth
}
export type MerchantReportShippingSummary = {
  shippingFeeCollected: ReportMoney; previousShippingFeeCollected: ReportMoney; shippingFeeGrowthPct: ReportGrowth
  freeShippingOrderCount: number; previousFreeShippingOrderCount: number; freeShippingGrowthPct: ReportGrowth
  averageShippingFee: ReportMoney; previousAverageShippingFee: ReportMoney; averageShippingFeeGrowthPct: ReportGrowth
  paidOrderCount: number
}
export type MerchantReportShipmentOps = {
  dispatchedCount: number; previousDispatchedCount: number; dispatchedGrowthPct: ReportGrowth
  deliveredCount: number; previousDeliveredCount: number; deliveredGrowthPct: ReportGrowth
  failedCount: number; previousFailedCount: number; failedGrowthPct: ReportGrowth
  returnedCount: number; previousReturnedCount: number; returnedGrowthPct: ReportGrowth
}
export type MerchantReportOverview = {
  range: string; from: string; to: string; days: number; timezone: string; currency: string; bucket: 'day' | 'week' | 'month'
  previousFrom: string; previousTo: string; feeEstimateRate: number | string
  kpis: MerchantReportKpis
  series: { start: string; end: string; gmv: ReportMoney; orders: number; estimatedFee: ReportMoney; estimatedNet: ReportMoney }[]
  /** key = category slug | UNCATEGORIZED | OTHER (names null for the two synthetic keys) */
  categories: { key: string; nameTh: string | null; nameEn: string | null; nameZh: string | null; itemSales: ReportMoney; quantity: number; sharePercent: number | string }[]
  /** channel = STOREFRONT | SHOPEE | TIKTOK | LAZADA | LINE_OA | MANUAL | OMS_IMPORT | UNKNOWN */
  channels: { channel: string; orders: number; gmv: ReportMoney; sharePercent: number | string }[]
  topProducts: { productId: string | null; name: string; imageUrl: string | null; quantity: number; itemSales: ReportMoney }[]
  customerSummary: MerchantReportCustomerSummary
  /** NO_SALES | NO_BASELINE | GMV_UP | GMV_DOWN | GMV_FLAT | NO_NEW_CUSTOMERS | NEW_CUSTOMERS_DOWN | REFUNDS_PRESENT */
  adviceFlags: string[]
  promotionSummary: MerchantReportPromotionSummary
  promoTypes: { promoType: string; redemptions: number; discountTotal: ReportMoney; sharePercent: number | string }[]
  topCoupons: { code: string; promoType: string; redemptions: number; discountTotal: ReportMoney }[]
  shippingSummary: MerchantReportShippingSummary
  carriers: { carrier: string; orders: number; shippingFee: ReportMoney; sharePercent: number | string }[]
  shipmentOps: MerchantReportShipmentOps
}
export const merchantReportsApi = {
  overview: (shopId: number, period: MerchantReportPeriod = {}, signal?: AbortSignal) =>
    request<MerchantReportOverview>(`${shopPath(shopId, '/reports/overview')}${queryOf(period)}`, { cache: 'no-store', signal }, true),
  /** Real .xlsx workbook (Excel Table + meta). Labels follow Accept-Language / lang. */
  exportXlsx: (shopId: number, period: MerchantReportPeriod = {}, tab: MerchantReportTab = 'overview') => {
    const label = period.range ?? `${period.from ?? ''}_${period.to ?? ''}`
    return downloadFile(`${shopPath(shopId, '/reports/export.xlsx')}${queryOf({ ...period, tab })}`, `thaishopfun-report-${tab}-${label}.xlsx`)
  },
  /** @deprecated use exportXlsx — kept as alias during transition */
  exportCsv: (shopId: number, period: MerchantReportPeriod = {}, tab: MerchantReportTab = 'overview') =>
    merchantReportsApi.exportXlsx(shopId, period, tab),
}

/** Platform-held shop wallet + withdrawals (not GMV / BANK_TRANSFER / COD sales). */
export type ShopWalletBalance = {
  available: number | string
  reserved: number | string
  pendingWithdrawals: number | string
  currency: string
  holdingPolicy: string
  canRequestWithdrawal: boolean
}
export type ShopWithdrawalStatus = 'REQUESTED' | 'APPROVED' | 'PROCESSING' | 'PAID' | 'REJECTED' | 'CANCELLED' | 'NEEDS_REVIEW'
export type ShopWithdrawal = {
  id: string
  shopId: number
  shopName: string | null
  amount: number | string
  currency: string
  status: ShopWithdrawalStatus
  accountProvider: string
  accountBankName: string | null
  accountName: string | null
  accountNumberMasked: string
  accountPromptPayMasked: string | null
  requestedAt: string
  reviewedAt: string | null
  rejectReason: string | null
  paidAt: string | null
  transferReference: string | null
  transferAmount: number | string | null
  transferDate: string | null
  cancelledAt: string | null
  events?: { fromStatus: string | null; toStatus: string; eventCode: string; createdAt: string; note: string | null }[]
}
export type ShopWithdrawalPage = { items: ShopWithdrawal[]; page: number; size: number; totalItems: number; totalPages: number }
export type PaymentAccountSummary = { provider: string; bankName: string | null; accountName: string | null; accountNumber: string | null; promptPayId: string | null; active: boolean; masked: boolean }

export type ShopTaxProfile = {
  legalName: string | null
  taxId: string | null
  branchCode: string
  vatRegistered: boolean
  vatRate: number | string | null
  canIssueTaxInvoice: boolean
  taxInvoiceBlockedReason: string | null
  policyNote: string
  updatedAt: string | null
}
export type ShopInvoiceDocType = 'INVOICE' | 'TAX_INVOICE'
export type ShopInvoiceStatus = 'DRAFT' | 'ISSUED' | 'CANCELLED'
export type ShopInvoiceSummary = {
  id: string
  docType: ShopInvoiceDocType
  status: ShopInvoiceStatus
  documentNumber: string | null
  buyerName: string
  grandTotal: number | string
  taxAmount: number | string
  currency: string
  sourceType: string
  sourceRef: string | null
  createdAt: string
  issuedAt: string | null
}
export type ShopInvoiceDetail = ShopInvoiceSummary & {
  sourceId: string
  sellerLegalName: string
  sellerTaxId: string | null
  sellerBranchCode: string | null
  sellerAddress: string
  sellerVatRegistered: boolean
  buyerTaxId: string | null
  buyerAddress: string
  buyerPhone: string | null
  buyerEmail: string | null
  subtotal: number | string
  discountTotal: number | string
  shippingTotal: number | string
  taxableBase: number | string
  vatRateApplied: number | string | null
  note: string | null
  cancelledAt: string | null
  cancelReason: string | null
  lines: {
    lineNo: number
    description: string
    quantity: number | string
    unitPrice: number | string
    lineDiscount: number | string
    lineSubtotal: number | string
    taxAmount: number | string
  }[]
  events: { fromStatus: string | null; toStatus: string; eventCode: string; at: string; note: string | null }[]
  disclaimer: string
}
export type ShopInvoicePage = { items: ShopInvoiceSummary[]; page: number; size: number; totalItems: number; totalPages: number }

export const merchantFinanceApi = {
  wallet: (shopId: number, signal?: AbortSignal) =>
    request<ShopWalletBalance>(shopPath(shopId, '/finance/wallet'), { cache: 'no-store', signal }, true),
  withdrawals: (shopId: number, params: Record<string, string | number | null | undefined> = {}, signal?: AbortSignal) =>
    request<ShopWithdrawalPage>(`${shopPath(shopId, '/finance/withdrawals')}${queryOf(params)}`, { cache: 'no-store', signal }, true),
  withdrawal: (shopId: number, id: string) =>
    request<ShopWithdrawal>(shopPath(shopId, `/finance/withdrawals/${id}`), { cache: 'no-store' }, true),
  createWithdrawal: (shopId: number, body: { amount: number; currency?: string; accountProvider: string; idempotencyKey: string }) =>
    request<ShopWithdrawal>(shopPath(shopId, '/finance/withdrawals'), json('POST', body), true),
  cancelWithdrawal: (shopId: number, id: string) =>
    request<ShopWithdrawal>(shopPath(shopId, `/finance/withdrawals/${id}/cancel`), json('POST', {}), true),
  exportWithdrawalsCsv: (shopId: number, params: Record<string, string | number | null | undefined> = {}) =>
    downloadFile(`${shopPath(shopId, '/finance/withdrawals/export.csv')}${queryOf(params)}`, `withdrawals-${shopId}.csv`),
  taxProfile: (shopId: number, signal?: AbortSignal) =>
    request<ShopTaxProfile>(shopPath(shopId, '/finance/invoices/tax-profile'), { cache: 'no-store', signal }, true),
  upsertTaxProfile: (shopId: number, body: { legalName?: string | null; taxId?: string | null; branchCode?: string | null; vatRegistered?: boolean; vatRate?: number | null }) =>
    request<ShopTaxProfile>(shopPath(shopId, '/finance/invoices/tax-profile'), json('PUT', body), true),
  invoices: (shopId: number, params: Record<string, string | number | null | undefined> = {}, signal?: AbortSignal) =>
    request<ShopInvoicePage>(`${shopPath(shopId, '/finance/invoices')}${queryOf(params)}`, { cache: 'no-store', signal }, true),
  invoice: (shopId: number, id: string) =>
    request<ShopInvoiceDetail>(shopPath(shopId, `/finance/invoices/${id}`), { cache: 'no-store' }, true),
  createInvoice: (shopId: number, body: { orderId: string; docType: ShopInvoiceDocType; buyerTaxId?: string; buyerEmail?: string; note?: string; idempotencyKey: string }) =>
    request<ShopInvoiceDetail>(shopPath(shopId, '/finance/invoices'), json('POST', body), true),
  updateInvoiceDraft: (shopId: number, id: string, body: { buyerName: string; buyerTaxId?: string | null; buyerAddress: string; buyerPhone?: string | null; buyerEmail?: string | null; note?: string | null }) =>
    request<ShopInvoiceDetail>(shopPath(shopId, `/finance/invoices/${id}`), json('PATCH', body), true),
  issueInvoice: (shopId: number, id: string, idempotencyKey: string) =>
    request<ShopInvoiceDetail>(shopPath(shopId, `/finance/invoices/${id}/issue`), json('POST', { idempotencyKey }), true),
  cancelInvoice: (shopId: number, id: string, reason: string) =>
    request<ShopInvoiceDetail>(shopPath(shopId, `/finance/invoices/${id}/cancel`), json('POST', { reason }), true),
  exportInvoicesCsv: (shopId: number, params: Record<string, string | number | null | undefined> = {}) =>
    downloadFile(`${shopPath(shopId, '/finance/invoices/export.csv')}${queryOf(params)}`, `invoices-${shopId}.csv`),
  downloadInvoicePdf: (shopId: number, id: string, filename: string) =>
    downloadFile(shopPath(shopId, `/finance/invoices/${id}/pdf`), filename),
}

export const adminPayoutApi = {
  list: (params: Record<string, string | number | null | undefined> = {}) =>
    request<ShopWithdrawalPage>(`/api/admin/payouts${queryOf(params)}`, { cache: 'no-store' }, true),
  get: (id: string) => request<ShopWithdrawal>(`/api/admin/payouts/${id}`, { cache: 'no-store' }, true),
  approve: (id: string) => request<ShopWithdrawal>(`/api/admin/payouts/${id}/approve`, json('POST', {}), true),
  reject: (id: string, reason: string) => request<ShopWithdrawal>(`/api/admin/payouts/${id}/reject`, json('POST', { reason }), true),
  processing: (id: string) => request<ShopWithdrawal>(`/api/admin/payouts/${id}/processing`, json('POST', {}), true),
  needsReview: (id: string, reason: string) => request<ShopWithdrawal>(`/api/admin/payouts/${id}/needs-review`, json('POST', { reason }), true),
  paid: (id: string, body: { transferReference: string; transferAmount?: number; transferDate: string; externalTransferKey?: string; note?: string }) =>
    request<ShopWithdrawal>(`/api/admin/payouts/${id}/paid`, json('POST', body), true),
  credit: (shopId: number, body: { amount: number; currency?: string; idempotencyKey: string; note?: string; sourceReference?: string }) =>
    request<ShopWalletBalance>(`/api/admin/payouts/shops/${shopId}/credits`, json('POST', body), true),
}

export const merchantOrderApi = {
  /** query: status, paymentStatus, fulfillmentStatus, from, to, q, page, size, sort, direction, acceptance, customer (opaque insight key) */
  list: (shopId: number, params: Record<string, string | number | boolean | null | undefined> = {}) =>
    request<OrderPage>(`${merchantOrders(shopId)}${queryOf(params)}`, { cache: 'no-store' }, true),
  /** ORDER_EXPORT — same filters as list (drops page/size); real .xlsx of all matching rows. */
  exportXlsx: (shopId: number, params: Record<string, string | number | boolean | null | undefined> = {}) => {
    const query = { ...params }
    delete query.page
    delete query.size
    return downloadFile(`${merchantOrders(shopId, '/export')}${queryOf(query)}`, `orders-${shopId}.xlsx`)
  },
  get: (shopId: number, orderId: string) => request<OrderDetail>(merchantOrders(shopId, `/${orderId}`), { cache: 'no-store' }, true),
  timeline: (shopId: number, orderId: string) => request<OrderTimelineEntry[]>(merchantOrders(shopId, `/${orderId}/timeline`), { cache: 'no-store' }, true),
  transitions: (shopId: number, orderId: string) => request<AllowedTransitions>(merchantOrders(shopId, `/${orderId}/transitions`), { cache: 'no-store' }, true),
  /** POST …/orders/{id}/{endpoint}: accept | mark-paid | confirm | process | ship | deliver | cancel */
  act: (shopId: number, orderId: string, endpoint: string, body?: TransitionBody) =>
    request<TransitionResponse>(merchantOrders(shopId, `/${orderId}/${endpoint}`), json('POST', body ?? {}), true),
  approveRefund: (shopId: number, orderId: string, body?: { restock?: boolean }) =>
    request<RefundActionResponse>(merchantOrders(shopId, `/${orderId}/refund/approve`), json('POST', body ?? {}), true),
  rejectRefund: (shopId: number, orderId: string, body: { reason: string }) =>
    request<RefundActionResponse>(merchantOrders(shopId, `/${orderId}/refund/reject`), json('POST', body), true),
}

/* ---------------------------------------------------------------- merchant payments / refunds (Y3) */
export type RefundView = {
  id: string; status: string; amount: Money; currency?: string | null; reason?: string | null; requesterType?: string | null; requestedBy?: string | null
  requestedAt?: string | null; approvedAt?: string | null; rejectedAt?: string | null; rejectReason?: string | null; restock?: boolean | null
  proofReference?: string | null; proofTransferredAt?: string | null; proofUploadedAt?: string | null; proofFileUrl?: string | null; completedAt?: string | null
  buyerAckStatus?: string | null; buyerAckAt?: string | null; buyerAckNote?: string | null; history?: PaymentStatusEntry[] | null
}
export type RefundSummary = {
  orderId: string; orderStatus: string; paymentStatus: string; paidAmount?: Money | null; refundedAmount?: Money | null; refundableAmount?: Money | null
  refunds: RefundView[]
}
export type PaymentActionResponse = { changed: boolean; payment: PaymentView; order: OrderDetail }
export type RefundActionResponse = { changed: boolean; refund: RefundView; order: OrderDetail }
export type CodCollectBody = { amount: number | string; collectedAt?: string; reference?: string; reason?: string; acceptAmountMismatch?: boolean; expectedVersion?: number }

export const merchantPaymentApi = {
  list: (shopId: number, orderId: string) => request<PaymentView[]>(merchantOrders(shopId, `/${orderId}/payments`), { cache: 'no-store' }, true),
  confirm: (shopId: number, orderId: string, paymentId: string, body?: { reason?: string; acceptAmountMismatch?: boolean; expectedVersion?: number }) =>
    request<PaymentActionResponse>(merchantOrders(shopId, `/${orderId}/payments/${paymentId}/confirm`), json('POST', body ?? {}), true),
  reject: (shopId: number, orderId: string, paymentId: string, body: { reason: string; expectedVersion?: number }) =>
    request<PaymentActionResponse>(merchantOrders(shopId, `/${orderId}/payments/${paymentId}/reject`), json('POST', body), true),
  /** COD cash collected: JSON, or multipart when an evidence file is attached. */
  collectCod: (shopId: number, orderId: string, body: CodCollectBody, file?: File | null) => {
    const path = merchantOrders(shopId, `/${orderId}/cod/collect`)
    if (!file) return request<PaymentActionResponse>(path, json('POST', body), true)
    const form = new FormData()
    form.set('amount', String(body.amount))
    if (body.collectedAt) form.set('collectedAt', body.collectedAt)
    if (body.reference) form.set('reference', body.reference)
    if (body.reason) form.set('reason', body.reason)
    if (body.acceptAmountMismatch) form.set('acceptAmountMismatch', 'true')
    if (body.expectedVersion != null) form.set('expectedVersion', String(body.expectedVersion))
    form.set('file', file)
    return request<PaymentActionResponse>(path, { method: 'POST', body: form }, true)
  },
  refunds: (shopId: number, orderId: string) => request<RefundSummary>(merchantOrders(shopId, `/${orderId}/refunds`), { cache: 'no-store' }, true),
  approveRefund: (shopId: number, orderId: string, refundId: string, body?: { restock?: boolean }) =>
    request<RefundActionResponse>(merchantOrders(shopId, `/${orderId}/refunds/${refundId}/approve`), json('POST', body ?? {}), true),
  rejectRefund: (shopId: number, orderId: string, refundId: string, body: { reason: string }) =>
    request<RefundActionResponse>(merchantOrders(shopId, `/${orderId}/refunds/${refundId}/reject`), json('POST', body), true),
  /** multipart: file (refund slip), reference?, transferredAt? */
  uploadRefundProof: (shopId: number, orderId: string, refundId: string, form: FormData) =>
    request<RefundActionResponse>(merchantOrders(shopId, `/${orderId}/refunds/${refundId}/proof`), { method: 'POST', body: form }, true),
  slipPath: (shopId: number, orderId: string, proofId: string) => merchantOrders(shopId, `/${orderId}/payments/proofs/${proofId}/file`),
  refundProofPath: (shopId: number, orderId: string, refundId: string) => merchantOrders(shopId, `/${orderId}/refunds/${refundId}/proof-file`),
}

/* ---------------------------------------------------------------- merchant shipments (Y6) */
export type TrackingEventView = {
  id: number; eventType: string; source?: string | null; actorType?: string | null; actorUserId?: string | null; fromStatus?: string | null
  status?: string | null; carrierStatusCode?: string | null; note?: string | null; location?: string | null; eventAt: string; recordedAt?: string | null
}
export type ShipmentSummary = {
  id: string; orderId: string; orderNumber: string; orderStatus: string; status: string; carrierCode?: string | null; trackingNumber?: string | null
  shippedAt?: string | null; deliveredAt?: string | null; lastTrackingEventAt?: string | null; version: number
}
export type ShipmentDetail = {
  id: string; orderId: string; orderNumber: string; orderStatus: string; orderVersion: number; paymentMethod?: string | null
  status: string; carrierCode?: string | null; carrierName?: string | null; trackingNumber?: string | null; trackingUrl?: string | null
  packedAt?: string | null; shippedAt?: string | null; pickedUpAt?: string | null; deliveredAt?: string | null; failedAt?: string | null
  failureReason?: string | null; returnedAt?: string | null; lastTrackingEventAt?: string | null; version: number
  allowedStatuses?: string[] | null; events?: TrackingEventView[] | null
}
export type ShipmentActionResponse = {
  changed: boolean; fromStatus?: string | null; toStatus?: string | null; orderFromStatus?: string | null; orderToStatus?: string | null; shipment: ShipmentDetail
}
export type ShopCarrierView = { code: string; nameTh: string; nameEn: string; trackingPattern?: string | null; active: boolean; enabled: boolean }
export type ShopCarriersResponse = { restricted: boolean; carriers: ShopCarrierView[] }
export type ShipmentUpdateBody = {
  status?: string; reason?: string; note?: string; location?: string; eventAt?: string; carrierStatusCode?: string
  returned?: boolean; expectedVersion?: number; expectedOrderVersion?: number
}
export type ShipmentCreateBody = { carrierCode?: string; trackingNumber?: string; shippedAt?: string; note?: string; expectedOrderVersion?: number }
export type ShipmentTrackingBody = { carrierCode?: string; trackingNumber?: string; reason?: string; expectedVersion?: number }

const shipmentOf = (shopId: number, orderId: string, path = '') => shopPath(shopId, `/orders/${orderId}/shipment${path}`)

export const merchantShipmentApi = {
  list: (shopId: number, params: Record<string, string | number | boolean | null | undefined> = {}) =>
    request<{ items: ShipmentSummary[]; page: number; size: number; totalItems: number; totalPages: number }>(`${shopPath(shopId, '/shipments')}${queryOf(params)}`, { cache: 'no-store' }, true),
  get: (shopId: number, orderId: string) => request<ShipmentDetail>(shipmentOf(shopId, orderId), { cache: 'no-store' }, true),
  create: (shopId: number, orderId: string, body?: ShipmentCreateBody) =>
    request<ShipmentActionResponse>(shipmentOf(shopId, orderId), json('POST', body ?? {}), true),
  updateTracking: (shopId: number, orderId: string, body: ShipmentTrackingBody) =>
    request<ShipmentActionResponse>(shipmentOf(shopId, orderId, '/tracking'), json('PUT', body), true),
  event: (shopId: number, orderId: string, body: ShipmentUpdateBody) =>
    request<ShipmentActionResponse>(shipmentOf(shopId, orderId, '/events'), json('POST', body), true),
  inTransit: (shopId: number, orderId: string, body?: ShipmentUpdateBody) =>
    request<ShipmentActionResponse>(shipmentOf(shopId, orderId, '/in-transit'), json('POST', body ?? {}), true),
  outForDelivery: (shopId: number, orderId: string, body?: ShipmentUpdateBody) =>
    request<ShipmentActionResponse>(shipmentOf(shopId, orderId, '/out-for-delivery'), json('POST', body ?? {}), true),
  delivered: (shopId: number, orderId: string, body?: ShipmentUpdateBody) =>
    request<ShipmentActionResponse>(shipmentOf(shopId, orderId, '/delivered'), json('POST', body ?? {}), true),
  failed: (shopId: number, orderId: string, body: ShipmentUpdateBody) =>
    request<ShipmentActionResponse>(shipmentOf(shopId, orderId, '/failed'), json('POST', body), true),
  returned: (shopId: number, orderId: string, body: ShipmentUpdateBody) =>
    request<ShipmentActionResponse>(shipmentOf(shopId, orderId, '/returned'), json('POST', body), true),
  carriers: (shopId: number) => request<ShopCarriersResponse>(shopPath(shopId, '/carriers'), { cache: 'no-store' }, true),
  setCarrier: (shopId: number, code: string, enabled: boolean) =>
    request<ShopCarriersResponse>(shopPath(shopId, `/carriers/${encodeURIComponent(code)}`), json('PUT', { enabled }), true),
}

/* ---------------------------------------------------------------- admin platform (Y7) */
export type PlatformPage<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number }
export type ReasonRequest = { reason: string; reasonCode?: string }
export type AdminSuspension = {
  status?: string | null; suspendedAt?: string | null; suspendedBy?: string | null; suspendedByName?: string | null
  reasonCode?: string | null; reason?: string | null; restoredAt?: string | null; restoredBy?: string | null; restoredByName?: string | null
}
export type AdminUserRef = {
  id: string; firstName?: string | null; lastName?: string | null; email?: string | null; phone?: string | null; status?: string | null
}
export type AdminMerchantRow = {
  merchantId: string; ownerType?: string | null; displayName?: string | null; email?: string | null; phone?: string | null
  province?: string | null; status: string; user?: AdminUserRef | null; shopCount: number; approvedShops: number
  suspendedShops: number; pendingShops: number; createdAt?: string | null; suspendedAt?: string | null
}
export type AdminMerchantShopRef = {
  shopId: number; name?: string | null; slug?: string | null; status?: string | null; suspendReasonCode?: string | null; createdAt?: string | null
}
export type AdminMerchantCounts = {
  shops: number; approvedShops: number; suspendedShops: number; products: number; orders: number; activeStaff: number
}
export type AdminMerchantDetail = {
  merchantId: string; ownerType?: string | null; title?: string | null; firstName?: string | null; lastName?: string | null
  email?: string | null; phone?: string | null; province?: string | null; district?: string | null; sensitiveReview?: string | null
  status: string; suspension?: AdminSuspension | null; user?: AdminUserRef | null; shops?: AdminMerchantShopRef[] | null
  counts?: AdminMerchantCounts | null; createdAt?: string | null; updatedAt?: string | null
}
export type MerchantActionResponse = { changed: boolean; merchant: AdminMerchantDetail; affectedShopIds?: number[] | null }
export type AdminShopRow = {
  shopId: number; name: string; nameEn?: string | null; slug?: string | null; status: string; category?: string | null
  province?: string | null; merchantId?: string | null; merchantName?: string | null; merchantStatus?: string | null
  owner?: AdminUserRef | null; activeMembers: number; products: number; orders: number
  /** Paid GMV for orders placed in the last 30 days. */
  salesLast30Days?: number | string | null
  createdAt?: string | null; publishedAt?: string | null; suspendedAt?: string | null
}
export type AdminShopMemberView = {
  userId: string; firstName?: string | null; lastName?: string | null; email?: string | null; role?: string | null
  roleNameTh?: string | null; status?: string | null; joinedAt?: string | null; removedAt?: string | null
}
export type AdminPlanLimits = {
  products?: number | null; ordersPerMonth?: number | null; staff?: number | null; storageBytes?: number | null
}
export type AdminProductUsage = {
  total: number; published: number; draft: number; archived: number; activeSkus: number; images: number
}
export type AdminOrderUsage = {
  total: number; last30Days: number; open: number; completed: number; cancelled: number; paidGrandTotal?: Money | null
}
export type AdminStaffUsage = { active: number; byRole?: Record<string, number> | null }
export type AdminStorageUsage = {
  mediaFiles: number; mediaBytes: number; documents: number; documentBytes: number; productImages: number
  trackedBytes: number; note?: string | null
}
export type AdminShopUsage = {
  shopId: number; shopName: string; status: string; planCode?: string | null; limits?: AdminPlanLimits | null
  products?: AdminProductUsage | null; orders?: AdminOrderUsage | null; staff?: AdminStaffUsage | null
  customers: number; activeCoupons: number; storage?: AdminStorageUsage | null; computedAt?: string | null
  slug?: string | null
}
export type AdminPlanUsageSummary = {
  planCode?: string | null; planName?: string | null; shopCount: number
  products: number; staff: number; storageBytes: number; ordersLast30Days: number
}
export type AdminShopDetail = {
  shopId: number; name: string; nameEn?: string | null; slug?: string | null; status: string; category?: string | null
  description?: string | null; address?: string | null; province?: string | null; district?: string | null
  subdistrict?: string | null; postalCode?: string | null; merchantId?: string | null; merchantName?: string | null
  merchantStatus?: string | null; owner?: AdminUserRef | null; suspension?: AdminSuspension | null
  pendingReinstatementMessage?: string | null; members?: AdminShopMemberView[] | null; usage?: AdminShopUsage | null
  createdAt?: string | null; updatedAt?: string | null; publishedAt?: string | null
}
export type ShopActionResponse = { changed: boolean; shop: AdminShopDetail }
export type AdminPlan = { code: string; name: string; limits?: AdminPlanLimits | null }
export type AdminPlanCatalog = { source: string; plans: AdminPlan[]; note?: string | null }
export type AdminAuditActor = { id?: string | null; type?: string | null; name?: string | null; email?: string | null; role?: string | null }
export type AdminAuditTarget = { type?: string | null; id?: string | null; shopId?: number | null; shopName?: string | null; name?: string | null }
export type AdminAuditLogView = {
  id: number; occurredAt: string; actor?: AdminAuditActor | null; action: string; target?: AdminAuditTarget | null
  permission?: string | null; outcome?: string | null; reasonCode?: string | null; reason?: string | null
  before?: Record<string, unknown> | null; after?: Record<string, unknown> | null
  ipAddress?: string | null; userAgent?: string | null; requestId?: string | null; authContext?: string | null
  policyVersion?: string | null; kind?: string | null
}

const adminPath = (path: string) => `/api/admin${path}`

/** B5 GET /api/admin/dashboard/summary (PLATFORM_SHOP_READ). Default period = today when omitted; FE uses 7d. */
export type AdminDashboardRange = 'today' | '7d' | '30d' | '1y'
export type AdminDashboardSeriesPoint = { start: string; end: string; sales: number | string; orders: number }
export type AdminDashboardTopShop = { shopId: number; shopName: string; slug: string; sales: number | string; orderCount: number }
export type AdminDashboardCounts = {
  shopsTotal: number; shopsPendingReview: number; shopsApproved: number; shopsRejected: number; shopsSuspended: number
  pendingApplications: number; usersTotal: number; usersNewInPeriod: number
}
export type AdminDashboardRecentOrder = {
  orderNumber: string; shopId: number; shopName: string; grandTotal: number | string
  status: string; paymentStatus: string; placedAt: string | null
}
export type AdminDashboardPaymentMethod = { method: string; amount: number | string; orderCount: number }
/** Masked email only — never raw user id / phone / full email. */
export type AdminDashboardRecentUser = { displayName: string; emailMasked: string; role: string; createdAt: string | null }
export type AdminDashboardSummary = {
  range: string; from: string; to: string; days: number; timezone: string; currency: string; bucket: 'day' | 'week' | 'month'
  totalSales: number | string; orderCount: number; paidOrderCount: number
  previousFrom: string; previousTo: string; previousSales: number | string; previousOrderCount: number; previousPaidOrderCount: number
  series: AdminDashboardSeriesPoint[]; topShops: AdminDashboardTopShop[]; counts: AdminDashboardCounts
  recentOrders: AdminDashboardRecentOrder[]; paymentMethods: AdminDashboardPaymentMethod[]; recentUsers: AdminDashboardRecentUser[]
}
export const adminDashboardApi = {
  summary: (period: { range?: AdminDashboardRange; from?: string; to?: string } = {}) =>
    request<AdminDashboardSummary>(`${adminPath('/dashboard/summary')}${queryOf(period)}`, { cache: 'no-store' }, true),
}

export const adminPlatformApi = {
  merchants: (params: Record<string, string | number | boolean | null | undefined> = {}) =>
    request<PlatformPage<AdminMerchantRow>>(`${adminPath('/merchants')}${queryOf(params)}`, { cache: 'no-store' }, true),
  merchant: (merchantId: string) =>
    request<AdminMerchantDetail>(adminPath(`/merchants/${merchantId}`), { cache: 'no-store' }, true),
  suspendMerchant: (merchantId: string, body: ReasonRequest) =>
    request<MerchantActionResponse>(adminPath(`/merchants/${merchantId}/suspend`), json('POST', body), true),
  reactivateMerchant: (merchantId: string, body: ReasonRequest) =>
    request<MerchantActionResponse>(adminPath(`/merchants/${merchantId}/reactivate`), json('POST', body), true),
  shops: (params: Record<string, string | number | boolean | null | undefined> = {}) =>
    request<PlatformPage<AdminShopRow>>(`${adminPath('/shops')}${queryOf(params)}`, { cache: 'no-store' }, true),
  shop: (shopId: number) =>
    request<AdminShopDetail>(adminPath(`/shops/${shopId}`), { cache: 'no-store' }, true),
  shopUsage: (shopId: number) =>
    request<AdminShopUsage>(adminPath(`/shops/${shopId}/usage`), { cache: 'no-store' }, true),
  suspendShop: (shopId: number, body: ReasonRequest) =>
    request<ShopActionResponse>(adminPath(`/shops/${shopId}/suspend`), json('POST', body), true),
  reactivateShop: (shopId: number, body: ReasonRequest) =>
    request<ShopActionResponse>(adminPath(`/shops/${shopId}/reactivate`), json('POST', body), true),
  plans: () => request<AdminPlanCatalog>(adminPath('/plans'), { cache: 'no-store' }, true),
  planUsage: (params: Record<string, string | number | boolean | null | undefined> = {}) =>
    request<PlatformPage<AdminShopUsage>>(`${adminPath('/plans/usage')}${queryOf(params)}`, { cache: 'no-store' }, true),
  planUsageSummary: (params: Record<string, string | number | boolean | null | undefined> = {}) =>
    request<AdminPlanUsageSummary[]>(`${adminPath('/plans/usage-summary')}${queryOf(params)}`, { cache: 'no-store' }, true),
  auditLogs: (params: Record<string, string | number | boolean | null | undefined> = {}) =>
    request<PlatformPage<AdminAuditLogView>>(`${adminPath('/audit-logs')}${queryOf(params)}`, { cache: 'no-store' }, true),
  auditLog: (id: number) =>
    request<AdminAuditLogView>(adminPath(`/audit-logs/${id}`), { cache: 'no-store' }, true),
  /** GET /api/admin/system/status — PLATFORM_SYSTEM_READ (no auto login redirect on 401). */
  getSystemStatus: (init: RequestInit = {}) =>
    request<AdminSystemStatus>(adminPath('/system/status'), { cache: 'no-store', ...init }, true, true, { redirectOn401: false }),
}

export type AdminSystemOverall = 'UP' | 'DEGRADED' | 'DOWN'
export type AdminSystemComponentStatus = 'UP' | 'DOWN' | 'UNKNOWN'
export type AdminSystemAppInfo = {
  version: string
  buildTime?: string | null
  startedAt: string
  uptimeSeconds: number
  javaVersion: string
  profile: string
}
export type AdminSystemComponent = {
  key: 'database' | 'migrations' | 'mail' | 'storage' | string
  status: AdminSystemComponentStatus
  latencyMs?: number | null
  detail?: string | null
  pending?: number | null
  failed?: number | null
  freeBytes?: number | null
  totalBytes?: number | null
}
export type AdminSystemStatus = {
  overall: AdminSystemOverall
  checkedAt: string
  app: AdminSystemAppInfo
  components: AdminSystemComponent[]
}

export const adminOrderApi = {
  list: (params: Record<string, string | number | boolean | null | undefined> = {}) =>
    request<OrderPage>(`${adminPath('/orders')}${queryOf(params)}`, { cache: 'no-store' }, true),
  get: (orderId: string) =>
    request<OrderDetail>(adminPath(`/orders/${orderId}`), { cache: 'no-store' }, true),
  timeline: (orderId: string) =>
    request<OrderTimelineEntry[]>(adminPath(`/orders/${orderId}/timeline`), { cache: 'no-store' }, true),
  transitions: (orderId: string) =>
    request<AllowedTransitions>(adminPath(`/orders/${orderId}/transitions`), { cache: 'no-store' }, true),
  transition: (orderId: string, body: TransitionBody & { status?: string; reason: string }) =>
    request<TransitionResponse>(adminPath(`/orders/${orderId}/transition`), json('POST', body), true),
  payments: (orderId: string) =>
    request<PaymentView[]>(adminPath(`/orders/${orderId}/payments`), { cache: 'no-store' }, true),
  confirmPayment: (orderId: string, paymentId: string, body: { reason: string; acceptAmountMismatch?: boolean; expectedVersion?: number }) =>
    request<PaymentActionResponse>(adminPath(`/orders/${orderId}/payments/${paymentId}/confirm`), json('POST', body), true),
  rejectPayment: (orderId: string, paymentId: string, body: { reason: string; expectedVersion?: number }) =>
    request<PaymentActionResponse>(adminPath(`/orders/${orderId}/payments/${paymentId}/reject`), json('POST', body), true),
  collectCod: (orderId: string, body: CodCollectBody & { reason: string }) =>
    request<PaymentActionResponse>(adminPath(`/orders/${orderId}/cod/collect`), json('POST', body), true),
  refunds: (orderId: string) =>
    request<RefundSummary>(adminPath(`/orders/${orderId}/refunds`), { cache: 'no-store' }, true),
  approveRefund: (orderId: string, refundId: string, body: { reason: string; restock?: boolean; note?: string }) =>
    request<RefundActionResponse>(adminPath(`/orders/${orderId}/refunds/${refundId}/approve`), json('POST', body), true),
  rejectRefund: (orderId: string, refundId: string, body: { reason: string; note?: string }) =>
    request<RefundActionResponse>(adminPath(`/orders/${orderId}/refunds/${refundId}/reject`), json('POST', body), true),
  resolveRefund: (orderId: string, refundId: string, body: { reason: string; note?: string }) =>
    request<RefundActionResponse>(adminPath(`/orders/${orderId}/refunds/${refundId}/resolve`), json('POST', body), true),
  shipments: (params: Record<string, string | number | boolean | null | undefined> = {}) =>
    request<PlatformPage<ShipmentSummary>>(`${adminPath('/shipments')}${queryOf(params)}`, { cache: 'no-store' }, true),
  shipment: (orderId: string) =>
    request<ShipmentDetail>(adminPath(`/orders/${orderId}/shipment`), { cache: 'no-store' }, true),
  shipmentStatus: (orderId: string, body: ShipmentUpdateBody & { reason: string }) =>
    request<ShipmentActionResponse>(adminPath(`/orders/${orderId}/shipment/status`), json('POST', body), true),
  shipmentTracking: (orderId: string, body: ShipmentTrackingBody) =>
    request<ShipmentActionResponse>(adminPath(`/orders/${orderId}/shipment/tracking`), json('PUT', body), true),
  proofFilePath: (orderId: string, proofId: string) => adminPath(`/orders/${orderId}/payments/proofs/${proofId}/file`),
  refundProofPath: (orderId: string, refundId: string) => adminPath(`/orders/${orderId}/refunds/${refundId}/proof-file`),
}

/* ---------------------------------------------------------------- merchant notifications (AE1) */
export type MerchantNotificationType =
  | 'ORDER_NEW' | 'ORDER_PAID' | 'ORDER_CANCELLED' | 'REFUND_REQUESTED' | 'REFUND_DISPUTED' | 'REFUND_COMPLETED'
  | 'PAYMENT_SLIP_PENDING' | 'CUSTOMER_NEW' | 'LOW_STOCK' | 'PRODUCT_REVIEW_NEW' | 'SHIPMENT_EXCEPTION'
  | 'SHOP_APPROVED' | 'SHOP_REJECTED' | 'SHOP_SUSPENDED' | 'SHOP_RESTORED'
  | 'SHOP_PLAN_CHANGED' | 'SHOP_MEMBER_ROLE_CHANGED' | 'SHOP_MEMBER_REMOVED'

export type MerchantNotificationItem = {
  key: string
  type: MerchantNotificationType
  titleParams: Record<string, string | number>
  orderNo?: string | null
  customerKey?: string | null
  amount?: number | string | null
  createdAt: string
  read: boolean
}

export type MerchantNotificationPage = {
  items: MerchantNotificationItem[]
  nextCursor: string | null
}

export const merchantNotificationsApi = {
  list: (shopId: number, params: { cursor?: string | null; size?: number } = {}) =>
    request<MerchantNotificationPage>(
      `${shopPath(shopId, '/notifications')}${queryOf({ cursor: params.cursor || undefined, size: params.size })}`,
      { cache: 'no-store' },
      true,
    ),
  unreadCount: (shopId: number) =>
    request<{ count: number }>(shopPath(shopId, '/notifications/unread-count'), { cache: 'no-store' }, true),
  markRead: (shopId: number, key: string) =>
    request<void>(shopPath(shopId, `/notifications/${encodeURIComponent(key)}/read`), { method: 'POST' }, true),
  markAllRead: (shopId: number) =>
    request<void>(shopPath(shopId, '/notifications/read-all'), { method: 'POST' }, true),
}

/** Fired after notification preferences save so the merchant bell refreshes list/badge. */
export const MERCHANT_NOTIFICATION_PREFS_EVENT = 'tsf:merchant-notification-prefs'

export type NotificationChannelInfo = { supported: boolean; editable: boolean }

export type NotificationCategoryInfo = {
  code: string
  mandatory: boolean
  defaultInApp: boolean
  inAppEnabled: boolean
  editable: boolean
  viewerPermission: string
  eventTypes: string[]
  policyReason: string
  viewerHasPermission: boolean
}

export type NotificationPreferences = {
  shopId: number
  shopName: string
  version: number
  channels: Record<string, NotificationChannelInfo>
  categories: NotificationCategoryInfo[]
}

export type NotificationPreferencesSave = {
  version: number
  categories: { code: string; inAppEnabled: boolean }[]
}

export const merchantNotificationPreferencesApi = {
  get: (shopId: number) =>
    request<NotificationPreferences>(
      shopPath(shopId, '/notification-preferences'),
      { cache: 'no-store' },
      true,
    ),
  save: (shopId: number, body: NotificationPreferencesSave) =>
    request<NotificationPreferences>(
      shopPath(shopId, '/notification-preferences'),
      { method: 'PUT', body: JSON.stringify(body) },
      true,
    ),
}

export type AdminNotificationType =
  | 'SHOP_APPLICATION_SUBMITTED'
  | 'SHOP_REINSTATEMENT_REQUESTED'
  | 'REFUND_DISPUTED'

export type AdminNotificationItem = {
  id: string
  type: AdminNotificationType | string
  entityType: string
  entityId: string
  shopId?: number | null
  shopName?: string | null
  href: string
  unread: boolean
  createdAt: string
}

export type AdminNotificationPage = {
  items: AdminNotificationItem[]
  nextCursor?: string | null
  unreadCount: number
}

export const adminNotificationsApi = {
  list: (params: { cursor?: string | null; size?: number } = {}) =>
    request<AdminNotificationPage>(
      `/api/admin/notifications${queryOf({ cursor: params.cursor || undefined, size: params.size })}`,
      { cache: 'no-store' },
      true,
    ),
  unreadCount: () =>
    request<{ count: number }>('/api/admin/notifications/unread-count', { cache: 'no-store' }, true),
  markRead: (id: string) =>
    request<void>(`/api/admin/notifications/${encodeURIComponent(id)}/read`, { method: 'POST' }, true),
  markAllRead: () =>
    request<void>('/api/admin/notifications/read-all', { method: 'POST' }, true),
}

/** AB3: public order tracking (no login; POST /api/public/orders/lookup). PII is masked by the backend. */
export type PublicOrderStatus = {
  orderNumber: string; shopName: string; placedAt?: string | null; status: string; paymentStatus: string; paymentMethod?: string | null
  fulfillmentStatus?: string | null; grandTotal: Money; currency: string; paidAt?: string | null; merchantConfirmedAt?: string | null
  cancelledAt?: string | null; completedAt?: string | null
  recipient?: { name?: string | null; phone?: string | null; province?: string | null; postalCode?: string | null } | null
  items?: { productName: string; optionLabel?: string | null; imageUrl?: string | null; quantity: number; lineTotal: Money }[] | null
  shipment?: {
    status: string; carrierCode?: string | null; carrierName?: string | null; trackingNumber?: string | null; trackingUrl?: string | null
    packedAt?: string | null; shippedAt?: string | null; deliveredAt?: string | null
    events?: { id: number; eventType: string; status?: string | null; location?: string | null; eventAt: string }[] | null
  } | null
}
export const publicOrderApi = {
  lookup: (orderNumber: string, contact: string) => request<PublicOrderStatus>('/api/public/orders/lookup', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ orderNumber, contact }),
  }),
}
/** AB3: backend share page with Open Graph tags (Facebook / LINE previews). null when the API is on localhost (crawlers cannot reach it). */
export function productShareUrl(shopSlug: string, productSlug: string): string | null {
  if (/\/\/(localhost|127\.|0\.0\.0\.0|\[::1\])/.test(API_URL)) return null
  return `${API_URL}/share/p/${encodeURIComponent(shopSlug)}/${encodeURIComponent(productSlug)}`
}


/** Buyer favourites (V45 user_favorites): /api/me/favorites - CART_READ / CART_UPDATE. Never carries user ids. */
export type FavoriteItem = { productId: string; productSlug: string; shopSlug: string; createdAt: string }
export type FavoriteToggle = { productId: string; favorite: boolean; alreadyFavorite: boolean }
export const favoritesApi = {
  list: (shopSlug?: string) => request<FavoriteItem[]>(`/api/me/favorites${shopSlug ? `?shopSlug=${encodeURIComponent(shopSlug)}` : ''}`, { cache: 'no-store' }, true),
  add: (productId: string) => request<FavoriteToggle>('/api/me/favorites', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ productId }) }, true),
  remove: (productId: string) => request<void>(`/api/me/favorites/${encodeURIComponent(productId)}`, { method: 'DELETE' }, true),
}

/* ---------------------------------------------------------------- Help Center (public + admin) */
export type HelpTopic = { id: string; slug: string; icon: string; title: string; description: string; faqCount: number }
export type HelpFaq = {
  id: string; topicSlug: string; audience: string; question: string; answer: string
  helpfulYes: number; helpfulNo: number; lastReviewedAt: string | null
}
export type HelpTopicDetail = HelpTopic & { faqs: HelpFaq[] }
export type HelpGuide = { id: string; slug: string; title: string; summary: string; url: string }
export type HelpAudience = 'ALL' | 'CUSTOMER' | 'MERCHANT'
export type HelpFaqParams = { lang: string; topic?: string; q?: string; audience?: string }
export type HelpFeedbackResult = { faqId: string; counted: boolean; helpfulYes: number; helpfulNo: number }

export const helpApi = {
  topics: (lang: string) => request<HelpTopic[]>(`/api/help/topics${queryOf({ lang })}`, { cache: 'no-store' }),
  topicBySlug: (slug: string, lang: string) => request<HelpTopicDetail>(`/api/help/topics/${encodeURIComponent(slug)}${queryOf({ lang })}`, { cache: 'no-store' }),
  faqs: (params: HelpFaqParams) => request<HelpFaq[]>(`/api/help/faqs${queryOf(params)}`, { cache: 'no-store' }),
  faqById: (id: string, lang: string) => request<HelpFaq>(`/api/help/faqs/${encodeURIComponent(id)}${queryOf({ lang })}`, { cache: 'no-store' }),
  /** Anonymous callers pass a stable sessionKey; a signed-in token (sent when available) takes precedence server-side. */
  submitFeedback: (id: string, helpful: boolean, sessionKey?: string, signedIn = false) =>
    request<HelpFeedbackResult>(`/api/help/faqs/${encodeURIComponent(id)}/feedback`, json('POST', { helpful, sessionKey }), signedIn, true, { redirectOn401: false }),
  guides: (lang: string) => request<HelpGuide[]>(`/api/help/guides${queryOf({ lang })}`, { cache: 'no-store' }),
}

/** Platform support hours — day 0 = Monday … 6 = Sunday (same order as merchant ShopHour[]). */
export type SupportContactDayHours = { day: number; open: boolean; start: string; end: string }
export type SupportContactPublic = {
  chat?: { url?: string | null; hours?: SupportContactDayHours[] } | null
  line?: { id?: string | null; url?: string | null } | null
  email?: { address?: string | null } | null
  phone?: { number?: string | null; hours?: SupportContactDayHours[] } | null
}
export type SupportContactAdmin = {
  chat: { enabled: boolean; url?: string | null; hours: SupportContactDayHours[] }
  line: { enabled: boolean; id?: string | null; url?: string | null }
  email: { enabled: boolean; address: string }
  phone: { enabled: boolean; number: string; hours: SupportContactDayHours[] }
}

export const platformContactApi = {
  get: () => request<SupportContactPublic>('/api/platform/contact', { cache: 'no-store' }),
}

export const platformSettingsAdminApi = {
  contact: () => request<SupportContactAdmin>(adminPath('/settings/contact'), { cache: 'no-store' }, true),
  updateContact: (body: SupportContactAdmin) => request<SupportContactAdmin>(adminPath('/settings/contact'), json('PUT', body), true),
}

export type AdminHelpTopic = {
  id: string; slug: string; icon: string; sortOrder: number; active: boolean
  titleTh: string; titleEn?: string | null; titleZh?: string | null
  descriptionTh?: string | null; descriptionEn?: string | null; descriptionZh?: string | null
  faqCount: number; createdAt: string; updatedAt: string; updatedBy?: string | null
}
export type AdminHelpFaq = {
  id: string; topicId: string; topicSlug: string; sortOrder: number; active: boolean; audience: string
  questionTh: string; questionEn?: string | null; questionZh?: string | null
  answerTh: string; answerEn?: string | null; answerZh?: string | null
  helpfulYes: number; helpfulNo: number; lastReviewedAt: string | null
  createdAt: string; updatedAt: string; updatedBy?: string | null
}
export type AdminHelpGuide = {
  id: string; slug: string; sortOrder: number; active: boolean
  titleTh: string; titleEn?: string | null; titleZh?: string | null
  summaryTh?: string | null; summaryEn?: string | null; summaryZh?: string | null
  url: string; createdAt: string; updatedAt: string; updatedBy?: string | null
}
export type HelpTopicRequest = {
  slug: string; icon?: string; sortOrder?: number; active?: boolean
  titleTh: string; titleEn?: string; titleZh?: string
  descriptionTh?: string; descriptionEn?: string; descriptionZh?: string
}
export type HelpFaqRequest = {
  topicId: string; sortOrder?: number; active?: boolean; audience: string
  questionTh: string; questionEn?: string; questionZh?: string
  answerTh: string; answerEn?: string; answerZh?: string
}
export type HelpGuideRequest = {
  slug: string; sortOrder?: number; active?: boolean
  titleTh: string; titleEn?: string; titleZh?: string
  summaryTh?: string; summaryEn?: string; summaryZh?: string; url: string
}

const helpAdmin = (path: string) => adminPath(`/help${path}`)
export const helpAdminApi = {
  topics: () => request<AdminHelpTopic[]>(helpAdmin('/topics'), { cache: 'no-store' }, true),
  createTopic: (body: HelpTopicRequest) => request<AdminHelpTopic>(helpAdmin('/topics'), json('POST', body), true),
  updateTopic: (id: string, body: HelpTopicRequest) => request<AdminHelpTopic>(helpAdmin(`/topics/${id}`), json('PUT', body), true),
  deleteTopic: (id: string) => request<void>(helpAdmin(`/topics/${id}`), json('DELETE'), true),
  setTopicActive: (id: string, active: boolean) => request<AdminHelpTopic>(helpAdmin(`/topics/${id}/active`), json('PATCH', { active }), true),
  reorderTopics: (ids: string[]) => request<AdminHelpTopic[]>(helpAdmin('/topics/reorder'), json('PUT', { ids }), true),

  faqs: () => request<AdminHelpFaq[]>(helpAdmin('/faqs'), { cache: 'no-store' }, true),
  createFaq: (body: HelpFaqRequest) => request<AdminHelpFaq>(helpAdmin('/faqs'), json('POST', body), true),
  updateFaq: (id: string, body: HelpFaqRequest) => request<AdminHelpFaq>(helpAdmin(`/faqs/${id}`), json('PUT', body), true),
  deleteFaq: (id: string) => request<void>(helpAdmin(`/faqs/${id}`), json('DELETE'), true),
  setFaqActive: (id: string, active: boolean) => request<AdminHelpFaq>(helpAdmin(`/faqs/${id}/active`), json('PATCH', { active }), true),
  markFaqReviewed: (id: string) => request<AdminHelpFaq>(helpAdmin(`/faqs/${id}/reviewed`), json('PATCH'), true),
  reorderFaqs: (ids: string[]) => request<AdminHelpFaq[]>(helpAdmin('/faqs/reorder'), json('PUT', { ids }), true),

  guides: () => request<AdminHelpGuide[]>(helpAdmin('/guides'), { cache: 'no-store' }, true),
  createGuide: (body: HelpGuideRequest) => request<AdminHelpGuide>(helpAdmin('/guides'), json('POST', body), true),
  updateGuide: (id: string, body: HelpGuideRequest) => request<AdminHelpGuide>(helpAdmin(`/guides/${id}`), json('PUT', body), true),
  deleteGuide: (id: string) => request<void>(helpAdmin(`/guides/${id}`), json('DELETE'), true),
  setGuideActive: (id: string, active: boolean) => request<AdminHelpGuide>(helpAdmin(`/guides/${id}/active`), json('PATCH', { active }), true),
  reorderGuides: (ids: string[]) => request<AdminHelpGuide[]>(helpAdmin('/guides/reorder'), json('PUT', { ids }), true),
}

/* ---------------------------------------------------------------- Articles CMS (public + admin) */
export type ArticlePage<T> = { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export type ArticleCategoryRef = { id: string; slug: string; name: string }
export type ArticleCategoryView = { id: string; slug: string; name: string; description: string | null; articleCount: number }
export type ArticleCard = {
  id: string; slug: string; title: string; excerpt: string | null; category: ArticleCategoryRef | null; coverUrl: string | null
  readingMinutes: number; tags: string[]; audience: string; featured: boolean; publishedAt: string | null
}
export type ArticleRelatedShop = { id: number; slug: string; name: string }
export type ArticleRelatedProduct = { id: string; slug: string; name: string; shopSlug: string }
export type ArticleDetail = {
  id: string; slug: string; title: string; excerpt: string | null; bodyMd: string; category: ArticleCategoryRef | null
  coverUrl: string | null; readingMinutes: number; tags: string[]; audience: string; featured: boolean
  publishedAt: string | null; author: string | null; metaTitle: string | null; metaDescription: string | null
  related: ArticleCard[]; relatedShop: ArticleRelatedShop | null; relatedProducts: ArticleRelatedProduct[]
}
export type ArticleListParams = { lang: string; category?: string; q?: string; tag?: string; audience?: string; page?: number; size?: number }

export const articleApi = {
  list: (params: ArticleListParams) => request<ArticlePage<ArticleCard>>(`/api/articles${queryOf(params)}`, { cache: 'no-store' }),
  featured: (lang: string) => request<ArticleCard[]>(`/api/articles/featured${queryOf({ lang })}`, { cache: 'no-store' }),
  bySlug: (slug: string, lang: string) => request<ArticleDetail>(`/api/articles/${encodeURIComponent(slug)}${queryOf({ lang })}`, { cache: 'no-store' }),
  categories: (lang: string) => request<ArticleCategoryView[]>(`/api/article-categories${queryOf({ lang })}`, { cache: 'no-store' }),
}

export type ArticleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type ArticleAudience = 'ALL' | 'CUSTOMER' | 'MERCHANT'
export type AdminArticleSummary = {
  id: string; slug: string; status: ArticleStatus; audience: string; featured: boolean
  categoryId: string | null; categorySlug: string | null; titleTh: string; titleEn: string | null; titleZh: string | null
  coverUrl: string | null; readingMinutes: number; tags: string[]
  publishedAt: string | null; createdAt: string; updatedAt: string
}
export type AdminArticle = {
  id: string; slug: string; status: ArticleStatus; audience: string; featured: boolean
  categoryId: string | null; categorySlug: string | null
  titleTh: string; titleEn: string | null; titleZh: string | null
  excerptTh: string | null; excerptEn: string | null; excerptZh: string | null
  bodyMdTh: string | null; bodyMdEn: string | null; bodyMdZh: string | null
  coverUrl: string | null; readingMinutes: number; tags: string[]
  metaTitle: string | null; metaDescription: string | null
  relatedShopId: number | null; relatedProductIds: string[]
  authorId: string | null; authorName: string | null
  publishedAt: string | null; createdAt: string; updatedAt: string
}
export type AdminArticleCategory = {
  id: string; slug: string; sortOrder: number; active: boolean
  nameTh: string; nameEn: string | null; nameZh: string | null
  descriptionTh: string | null; descriptionEn: string | null; descriptionZh: string | null
  articleCount: number; createdAt: string; updatedAt: string
}
export type ArticleRequest = {
  slug?: string; categoryId?: string | null; audience?: string; featured?: boolean; readingMinutes?: number
  titleTh: string; titleEn?: string; titleZh?: string
  excerptTh?: string; excerptEn?: string; excerptZh?: string
  bodyMdTh?: string; bodyMdEn?: string; bodyMdZh?: string
  tags?: string[]; metaTitle?: string; metaDescription?: string
  relatedShopId?: number | null; relatedProductIds?: string[]
}
export type ArticleCategoryRequest = {
  slug: string; sortOrder?: number; active?: boolean
  nameTh: string; nameEn?: string; nameZh?: string
  descriptionTh?: string; descriptionEn?: string; descriptionZh?: string
}
export type AdminArticleListParams = { status?: string; category?: string; q?: string; page?: number; size?: number }

export const articleAdminApi = {
  list: (params: AdminArticleListParams) => request<ArticlePage<AdminArticleSummary>>(adminPath(`/articles${queryOf(params)}`), { cache: 'no-store' }, true),
  get: (id: string) => request<AdminArticle>(adminPath(`/articles/${id}`), { cache: 'no-store' }, true),
  create: (body: ArticleRequest) => request<AdminArticle>(adminPath('/articles'), json('POST', body), true),
  update: (id: string, body: ArticleRequest) => request<AdminArticle>(adminPath(`/articles/${id}`), json('PUT', body), true),
  remove: (id: string) => request<void>(adminPath(`/articles/${id}`), json('DELETE'), true),
  setStatus: (id: string, status: ArticleStatus, publishedAt?: string | null) =>
    request<AdminArticle>(adminPath(`/articles/${id}/status`), json('PATCH', { status, publishedAt: publishedAt ?? undefined }), true),
  setFeatured: (id: string, featured: boolean) => request<AdminArticle>(adminPath(`/articles/${id}/featured`), json('PATCH', { featured }), true),
  uploadCover: (id: string, file: File, onProgress?: (ratio: number) => void) => {
    const body = new FormData()
    body.append('file', file)
    return uploadForm<AdminArticle>(adminPath(`/articles/${id}/cover`), body, onProgress)
  },
  removeCover: (id: string) => request<AdminArticle>(adminPath(`/articles/${id}/cover`), json('DELETE'), true),

  categories: () => request<AdminArticleCategory[]>(adminPath('/article-categories'), { cache: 'no-store' }, true),
  createCategory: (body: ArticleCategoryRequest) => request<AdminArticleCategory>(adminPath('/article-categories'), json('POST', body), true),
  updateCategory: (id: string, body: ArticleCategoryRequest) => request<AdminArticleCategory>(adminPath(`/article-categories/${id}`), json('PUT', body), true),
  setCategoryActive: (id: string, active: boolean) => request<AdminArticleCategory>(adminPath(`/article-categories/${id}/active`), json('PATCH', { active }), true),
  reorderCategories: (ids: string[]) => request<AdminArticleCategory[]>(adminPath('/article-categories/reorder'), json('PUT', { ids }), true),
  deleteCategory: (id: string) => request<void>(adminPath(`/article-categories/${id}`), json('DELETE'), true),
}

/* ---------------------------------------------------------------- merchant shop team (invites / members) */
export type TeamPageMeta = { page: number; size: number; totalItems: number; totalPages: number }
export type TeamPermissionItem = { code: string; descriptionTh: string; sensitive: boolean }
export type TeamPermissionGroup = { module: string; items: TeamPermissionItem[] }
export type TeamMemberSummary = {
  userId: string; firstName: string | null; lastName: string | null; email: string
  role: string; roleName: string | null; status: string; joinedAt: string | null; active: boolean
}
export type TeamMemberEvent = { action: string; note: string | null; at: string | null }
export type TeamMemberDetail = TeamMemberSummary & {
  removedAt: string | null; permissions: string[]; permissionGroups: TeamPermissionGroup[]; events: TeamMemberEvent[]
}
export type TeamInvitationSummary = {
  id: string; email: string; role: string; roleName: string | null; status: string
  expiresAt: string; createdAt: string; acceptedAt: string | null; invitedByName: string | null; actionable: boolean
}
export type TeamInvitationCreated = {
  id: string; email: string; role: string; status: string; expiresAt: string
  inviteUrl: string; emailQueued: boolean; deliveryNote: string
}
export type TeamRoleOption = {
  code: string; nameTh: string; nameEn: string; assignableByActor: boolean
  permissions: string[]; permissionGroups: TeamPermissionGroup[]; sensitive: boolean
}
export type TeamStaffQuota = { staffLimit: number | null; staffUsed: number; quotaEnforced: boolean; quotaNote: string | null }
export type TeamRoleCatalog = { roles: TeamRoleOption[]; actorRole: string; staffQuota: TeamStaffQuota }
export type TeamInvitePreview = {
  shopName: string; inviterName: string; role: string; roleName: string; status: string; expiresAt: string
  permissions: string[]; permissionGroups: TeamPermissionGroup[]
  emailMatchesCaller: boolean; callerAuthenticated: boolean; callerEmailVerified: boolean; conflictCode: string | null
}
export type TeamMemberListParams = { q?: string; role?: string; status?: string; page?: number; size?: number }
export type TeamInviteListParams = { q?: string; status?: string; page?: number; size?: number }

export const merchantTeamApi = {
  roles: (shopId: number) =>
    request<TeamRoleCatalog>(shopPath(shopId, '/team/roles'), { cache: 'no-store' }, true),
  members: (shopId: number, params: TeamMemberListParams = {}) =>
    request<{ items: TeamMemberSummary[]; page: TeamPageMeta; totalActive: number }>(
      `${shopPath(shopId, '/team/members')}${queryOf(params)}`, { cache: 'no-store' }, true),
  member: (shopId: number, userId: string) =>
    request<TeamMemberDetail>(shopPath(shopId, `/team/members/${userId}`), { cache: 'no-store' }, true),
  changeRole: (shopId: number, userId: string, role: string) =>
    request<TeamMemberSummary>(shopPath(shopId, `/team/members/${userId}`), json('PATCH', { role }), true),
  removeMember: (shopId: number, userId: string, reason?: string) =>
    request<void>(shopPath(shopId, `/team/members/${userId}`), json('DELETE', { reason: reason ?? 'MERCHANT_REMOVAL' }), true),
  invitations: (shopId: number, params: TeamInviteListParams = {}) =>
    request<{ items: TeamInvitationSummary[]; page: TeamPageMeta; totalPending: number }>(
      `${shopPath(shopId, '/team/invitations')}${queryOf(params)}`, { cache: 'no-store' }, true),
  createInvite: (shopId: number, body: { email: string; role: string }) =>
    request<TeamInvitationCreated>(shopPath(shopId, '/team/invitations'), json('POST', body), true),
  resendInvite: (shopId: number, invitationId: string) =>
    request<TeamInvitationCreated>(shopPath(shopId, `/team/invitations/${invitationId}/resend`), json('POST'), true),
  revokeInvite: (shopId: number, invitationId: string) =>
    request<void>(shopPath(shopId, `/team/invitations/${invitationId}/revoke`), json('POST'), true),
}

/** Public preview (optional auth). Accept requires auth. */
export const shopInvitationApi = {
  preview: (token: string, authenticate = false) =>
    request<TeamInvitePreview>(`/api/invitations/${encodeURIComponent(token)}`, { cache: 'no-store' }, authenticate, true, { redirectOn401: false }),
  accept: (token: string) =>
    request<TeamMemberSummary>(`/api/invitations/${encodeURIComponent(token)}/accept`, json('POST'), true),
}

/* ---------------------------------------------------------------- merchant shop security activity (AUDIT_READ) */
export type MerchantSecurityActivityRow = {
  id: number
  occurredAt: string
  actorName: string | null
  actorType: string
  action: string
  outcome: string
  summary: string | null
  reasonCode: string | null
}
export type MerchantSecurityActivityDetail = MerchantSecurityActivityRow & {
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
}
export type MerchantSecurityActivityPage = {
  items: MerchantSecurityActivityRow[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  supportedActions: string[]
}
export type MerchantSecurityActivityParams = {
  action?: string
  outcome?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export const merchantSecurityActivityApi = {
  list: (shopId: number, params: MerchantSecurityActivityParams = {}) =>
    request<MerchantSecurityActivityPage>(
      `${shopPath(shopId, '/security-activity')}${queryOf(params)}`,
      { cache: 'no-store' },
      true,
    ),
  detail: (shopId: number, id: number) =>
    request<MerchantSecurityActivityDetail>(
      shopPath(shopId, `/security-activity/${id}`),
      { cache: 'no-store' },
      true,
    ),
}

/* ---------------------------------------------------------------- merchant plan usage (SHOP_READ; metrics may be RESTRICTED) */
export type MerchantPlanLimitKind = 'FINITE' | 'ZERO' | 'UNLIMITED' | 'NOT_APPLICABLE' | 'UNKNOWN'
export type MerchantPlanMetricStatus =
  | 'OK' | 'WARN' | 'FULL' | 'OVER' | 'UNLIMITED' | 'ZERO_QUOTA' | 'NOT_APPLICABLE' | 'UNKNOWN' | 'RESTRICTED'
export type MerchantPlanMetric = {
  code: string
  availability: 'AVAILABLE' | 'RESTRICTED'
  permissionRequired: string
  viewerHasPermission: boolean
  used: number | null
  limit: { kind: MerchantPlanLimitKind; value: number | null }
  unit: 'COUNT' | 'BYTES'
  completeness: 'COMPLETE' | 'PARTIAL' | 'UNKNOWN'
  period: {
    kind: 'NONE' | 'ROLLING_DAYS'
    rollingDays: number | null
    start: string | null
    end: string | null
  }
  definition: { source: string; includes: string; excludes: string; note: string }
  status: MerchantPlanMetricStatus
  remaining: number | null
  usedRatio: number | null
  breakdown: Record<string, number>
}
export type MerchantPlanUsage = {
  shopId: number
  shopName: string
  shopStatus: string
  plan: {
    assignment: 'ASSIGNED' | 'UNASSIGNED'
    code: string | null
    name: string | null
    resolution: 'ACTIVE' | 'RETIRED' | 'UNKNOWN' | 'NONE'
    note: string | null
  }
  asOf: string
  timezone: string
  displayPolicy: { warnAtRatio: number; note: string }
  metrics: MerchantPlanMetric[]
  enforcement: { metricCode: string; level: string; detail: string }[]
}

export const merchantPlanUsageApi = {
  get: (shopId: number) =>
    request<MerchantPlanUsage>(shopPath(shopId, '/plan-usage'), { cache: 'no-store' }, true),
}