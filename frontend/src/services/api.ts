import i18n from '../i18n'

export type UserRole = 'CUSTOMER' | 'MERCHANT' | 'ADMIN'
/** Effective store permissions in one shop (shop_members role → auth_role_permissions). */
export type ShopPermissionGrant = { shopId:number; role:string; permissions:string[] }
/**
 * `role` is the legacy account role kept for routing. `roleCodes`, `permissions` (account + platform level) and
 * `shopPermissions` are the effective permissions resolved from the database on every login/refresh/me call;
 * `superAdmin` (active PLATFORM_ADMIN) means every permission in every shop.
 */
export type AuthUser = { id:string; firstName:string; lastName:string; email:string; phone:string; role:UserRole; roleCodes?:string[]; permissions?:string[]; shopPermissions?:ShopPermissionGrant[]; superAdmin?:boolean }
type AuthResponse = { accessToken:string; expiresIn:number; user:AuthUser }
export type RegisterPayload = { firstName:string; lastName:string; email:string; phone:string; password:string }
export type OtpPurpose = 'SIGNUP'|'LOGIN'|'PHONE_VERIFY'|'EMAIL_VERIFY'|'PASSWORD_RESET'|'STEP_UP'
export type SendOtpResponse = { otpId:string; expiresIn:number; destinationMasked:string; channel:string; code:string|null }
export type VerifyOtpResponse = { verified:boolean; purpose:string; resetToken:string|null }
export type ForgotPasswordResponse = { message:string; resetToken:string|null }

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

async function request<T>(path:string, init:RequestInit = {}, authenticate = false, retry = true):Promise<T> {
  if (authenticate && !accessToken) await refreshSession()
  const headers = new Headers(init.headers)
  withLanguage(headers)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (authenticate && accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  let response:Response
  try {
    response = await fetch(`${API_URL}${path}`, {...init, headers, credentials:'include'})
  } catch {
    throw new ApiError('NETWORK_ERROR', 'เชื่อมต่อระบบไม่ได้ กรุณาตรวจสอบว่า Backend กำลังทำงาน', 0)
  }
  if (response.status === 401 && authenticate && retry) {
    try {
      await refreshSession()
      return request<T>(path, init, true, false)
    } catch { accessToken = null }
  }
  if (!response.ok) {
    const error: { code?: string; message?: string; jobId?: string; fieldErrors?: ApiFieldError[] } = await response.json().catch(() => ({ code: response.status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED', message: 'เกิดข้อผิดพลาด กรุณาลองใหม่' }))
    if (authenticate && !path.startsWith('/api/auth/') && (response.status === 403 || error.code === 'SHOP_NOT_FOUND')) {
      window.dispatchEvent(new Event(PERMISSIONS_STALE_EVENT))
    }
    const apiError = new ApiError(error.code || (response.status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED'), error.message || 'เกิดข้อผิดพลาด กรุณาลองใหม่', response.status, error.jobId, error.fieldErrors, error as Record<string, unknown>)
    if (response.status === 401 && authenticate && !window.location.pathname.startsWith('/login')) {
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
  sendOtp: (destination:string, purpose:OtpPurpose) => request<SendOtpResponse>('/api/auth/otp/send', {method:'POST', body:JSON.stringify({destination,purpose})}),
  verifyOtp: (otpId:string, code:string) => request<VerifyOtpResponse>('/api/auth/otp/verify', {method:'POST', body:JSON.stringify({otpId,code})}),
  forgotPassword: (email:string) => request<ForgotPasswordResponse>('/api/auth/forgot-password', {method:'POST', body:JSON.stringify({email})}),
  resetPassword: (token:string, newPassword:string) => request<{message:string}>('/api/auth/reset-password', {method:'POST', body:JSON.stringify({token,newPassword})}),
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
export type ThemeHero = { layout:'FULL'|'STRIP'; title:string; subtitle:string; ctaLabel:string; ctaLink:string }
export type SectionConfig = { key:'HERO'|'BANNERS'|'CATEGORIES'|'TRUST'|'INTRO'|'FEATURED'|'BESTSELLERS'|'NEW_ARRIVALS'|'CATALOG'|'HOURS'; enabled:boolean; variant:'FULL'|'STRIP'|'GRID'|'LARGE'|'MENU'|null }
export type ThemeBanner = { imageUrl:string; title?:string; link?:string }
export type ThemeTrustItem = { icon:string; label:string; enabled:boolean }
export type ThemeTrust = { enabled:boolean; items:ThemeTrustItem[] }
export type ThemeConfig = { colors:ThemeColors; font:string; style?:ThemeStyle; hero:ThemeHero; banners?:ThemeBanner[]; trust?:ThemeTrust; sections:SectionConfig[] }
export type ShopTheme = { templateKey:'GENERAL'|'FASHION'|'FOOD_BEVERAGE'; config:ThemeConfig }

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
  publicPhone:string; publicEmail:string
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
  submit: () => request<MerchantApplication>('/api/merchant-applications/draft/submit',{method:'POST'},true),
  shops: () => request<MerchantShopOption[]>('/api/merchant/shops', {}, true),
  storeAccess: (shopId?:number|null) => request<StoreAccess>(withShop('/api/merchant/store-access', shopId), {}, true),
  getSettings: (shopId?:number|null) => request<MerchantSettings>(withShop('/api/merchant/settings', shopId), {}, true),
  saveSettings: (payload: MerchantSettingsPayload, shopId?:number|null) => request<MerchantSettings>(withShop('/api/merchant/settings', shopId), { method: 'PUT', body: JSON.stringify(payload) }, true),
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

async function downloadFile(path:string, filename:string) {
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

export const adminApplicationApi = {
  list: () => request<AdminStoreApplication[]>('/api/admin/store-applications', {}, true),
  approve: (shopId:number) => request<AdminStoreApplication>(`/api/admin/store-applications/${shopId}/approve`, {method:'POST'}, true),
  reject: (shopId:number) => request<AdminStoreApplication>(`/api/admin/store-applications/${shopId}/reject`, {method:'POST'}, true),
  suspend: (shopId:number) => request<AdminStoreApplication>(`/api/admin/store-applications/${shopId}/suspend`, {method:'POST'}, true),
  bulk: (action:'approve'|'reject'|'suspend'|'reactivate', shopIds:number[]) => request<{updated:number; skipped:number; applications:AdminStoreApplication[]}>(`/api/admin/store-applications/bulk/${action}`, {method:'POST', body:JSON.stringify({shopIds})}, true),
  reactivate: (shopId:number) => request<AdminStoreApplication>(`/api/admin/store-applications/${shopId}/reactivate`, {method:'POST'}, true),
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
export type ProductSort = 'newest' | 'name' | 'price_asc' | 'price_desc' | 'stock'
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

export type CheckoutAddress = {
  recipientName: string; phone: string; addressLine1: string; addressLine2?: string
  subdistrict?: string; district: string; province: string; postalCode: string
}
export type OrderItem = { id: string; productId: string; skuId: string; productName: string; optionLabel?: string | null; skuCode?: string | null; imageUrl?: string | null; unitPrice: Money; quantity: number; discountAmount?: Money | null; lineTotal: Money }
export type OrderDetail = {
  id: string; orderNumber: string; shopId: number; shopName: string; status: string; paymentStatus: string; fulfillmentStatus?: string | null
  currency: string; subtotal: Money; discountTotal: Money; shippingFee: Money; grandTotal: Money; couponCode?: string | null
  shipping?: { methodId?: string | null; methodName?: string | null; carrierCode?: string | null } | null
  placedAt?: string | null; items?: OrderItem[] | null; itemCount?: number | null; totalQuantity?: number | null
}
export type CheckoutResult = { replayed: boolean; orders: OrderDetail[]; grandTotal: Money; currency: string }
export type PaymentAccount = { provider: string; displayName?: string | null; bankName?: string | null; accountNumber?: string | null; accountName?: string | null; promptPayId?: string | null; active: boolean; masked: boolean }
export type OrderPaymentInfo = { orderId: string; orderNumber: string; orderStatus: string; paymentStatus: string; amountDue: Money; currency: string; canSubmitSlip: boolean; accounts: PaymentAccount[] }

export const checkoutApi = {
  /** idempotencyKey: 8?100 chars [A-Za-z0-9._:-]; reuse the same key when retrying the same attempt. */
  place: (idempotencyKey: string, shippingAddress: CheckoutAddress, buyerNote?: string) =>
    request<CheckoutResult>('/api/checkout', { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify({ shippingAddress, buyerNote: buyerNote || undefined }) }, true),
}
export const buyerOrderApi = {
  get: (orderId: string) => request<OrderDetail>(`/api/orders/${orderId}`, {}, true),
  paymentInfo: (orderId: string) => request<OrderPaymentInfo>(`/api/orders/${orderId}/payment-info`, {}, true),
}
