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

const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8081').replace(/\/$/, '')
let accessToken: string | null = null
let refreshInFlight: Promise<AuthResponse> | null = null

/** Fired when the backend denies an action (403 / shop no longer reachable) so the UI re-reads its permissions. */
export const PERMISSIONS_STALE_EVENT = 'tsf:permissions-stale'

export type ApiFieldError = { field: string; code: string; message: string }

export class ApiError extends Error {
  fieldErrors: ApiFieldError[]
  constructor(public code:string, message:string, public status:number, public jobId?:string, fieldErrors?: ApiFieldError[]) {
    super(message)
    this.fieldErrors = fieldErrors ?? []
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
    const apiError = new ApiError(error.code || (response.status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED'), error.message || 'เกิดข้อผิดพลาด กรุณาลองใหม่', response.status, error.jobId, error.fieldErrors)
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
export type TranslationMeta = { origin: 'MACHINE' | 'HUMAN'; status: 'PENDING' | 'DONE' | 'FAILED' }

export type PublicShop = {
  name:string;nameEn?:string;nameZh?:string;description?:string;descriptionEn?:string;descriptionZh?:string;category:string
  address:string;province:string;district:string;subdistrict:string;postalCode:string
  slug:string;path:string;status:'PENDING_REVIEW'|'APPROVED'|'REJECTED'|'SUSPENDED';logoUrl?:string|null;coverUrl?:string|null
  shopId:number
}
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
}
export type ShopHour = { open:boolean; start:string; end:string }
export type MerchantSettingsPayload = {
  shopName:string; shopNameEn:string; description:string; category:string
  phone:string; email:string
  website:string; lineId:string; facebookUrl:string; instagramUrl:string; tiktokUrl:string
  address:string; province:string; district:string; subdistrict:string; postalCode:string
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

export const adminDirectoryApi = {
  users: (query:AdminUserQuery) => request<AdminUserPage>(`/api/admin/users${queryString(query)}`, {}, true),
  userStats: () => request<AdminUserStats>('/api/admin/users/stats', {}, true),
  userFilterOptions: () => request<AdminUserFilterOptions>('/api/admin/users/filter-options', {}, true),
  user: (id:string) => request<AdminUserDetail>(`/api/admin/users/${id}`, {}, true),
  userHistory: (id:string, limit = 50) => request<AdminActivityEvent[]>(`/api/admin/users/${id}/history?limit=${limit}`, {}, true),
  exportUsers: ({page:_page, size:_size, ...query}:AdminUserQuery) => downloadFile(`/api/admin/users/export${queryString(query)}`, 'users.csv'),
  roles: () => request<AdminRoleSummary[]>('/api/admin/roles', {}, true),
  role: (code:string) => request<AdminRoleDetail>(`/api/admin/roles/${encodeURIComponent(code)}`, {}, true),
  roleActivity: (limit = 20) => request<AdminActivityEvent[]>(`/api/admin/roles/activity?limit=${limit}`, {}, true),
}

export type ProductCategory = { id:number; nameTh:string; nameEn:string; nameZh:string }

export const catalogApi = {
  categories: () => request<ProductCategory[]>('/api/categories'),
}

export const shopApi = {
  list: () => request<PublicShop[]>('/api/shops'),
  getBySlug: (slug:string) => request<PublicShop>(`/api/shops/${encodeURIComponent(slug)}`),
  products: (slug:string, query: Pick<ProductListQuery, 'q' | 'categoryId' | 'sort' | 'page' | 'size' | 'priceBands'> = {}) => request<ProductPage<PublicProduct>>(`/api/shops/${encodeURIComponent(slug)}/products${productQuery(query)}`),
  product: (slug:string, productSlug:string) => request<PublicProduct>(`/api/shops/${encodeURIComponent(slug)}/products/${encodeURIComponent(productSlug)}`),
}

export type ProductStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type ProductType = 'SIMPLE' | 'VARIABLE'
export type StockFilter = 'in' | 'low' | 'out'
export type ProductSort = 'newest' | 'name' | 'price_asc' | 'price_desc' | 'stock'
export type ProductListQuery = {
  q?: string
  status?: ProductStatus
  categoryId?: number
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
  version: number
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
}
export type ProductPage<T> = { items: T[]; page: number; size: number; total: number }
export type ProductImportResult = { created: number; failed: number; errors: { row: number; message: string }[] }
export type ProductImportAccepted = { jobId: string; total: number }
export type ProductImportJob = {
  jobId: string
  shopId: number
  userId: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  total: number
  processed: number
  created: number
  failed: number
  errors: { row: number; message: string }[]
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
  skus: SkuWrite[]
}
export type StockAdjustment = { skuId: string; entryType: string; onHand: number; available: number; version: number }
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
}

function productQuery(query: ProductListQuery) {
  const search = new URLSearchParams()
  if (query.q) search.set('q', query.q)
  if (query.status) search.set('status', query.status)
  if (query.categoryId != null) search.set('categoryId', String(query.categoryId))
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
  list: (shopId: number, query: ProductListQuery = {}) => request<ProductPage<Product>>(shopProducts(shopId, `/products${productQuery(query)}`), {}, true),
  get: (shopId: number, productId: string) => request<Product>(shopProducts(shopId, `/products/${productId}`), {}, true),
  create: (shopId: number, payload: ProductWrite) => request<Product>(shopProducts(shopId, '/products'), { method: 'POST', body: JSON.stringify(payload) }, true),
  update: (shopId: number, productId: string, payload: ProductWrite) => request<Product>(shopProducts(shopId, `/products/${productId}`), { method: 'PUT', body: JSON.stringify(payload) }, true),
  useAutoTranslation: (shopId: number, productId: string, payload: { field: 'name' | 'description'; locale: 'en' | 'zh' }) =>
    request<Product>(shopProducts(shopId, `/products/${productId}/translations/use-auto`), { method: 'POST', body: JSON.stringify(payload) }, true),
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
  adjustStock: (shopId: number, skuId: string, payload: { entryType: 'RECEIVE' | 'ADJUST'; quantityDelta: number; reason?: string; version: number; idempotencyKey?: string }) => request<StockAdjustment>(shopProducts(shopId, `/skus/${skuId}/stock-adjustments`), { method: 'POST', body: JSON.stringify(payload) }, true),
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
  importExcel: (shopId: number, file: File, onUploadProgress?: (percent: number) => void) => {
    const body = new FormData()
    body.append('file', file)
    return uploadForm<ProductImportAccepted>(shopProducts(shopId, '/products/import'), body, (ratio) => onUploadProgress?.(Math.round(ratio * 100)))
  },
  importStatus: (shopId: number, jobId: string) => request<ProductImportJob>(shopProducts(shopId, `/products/import/${jobId}`), {}, true),
  importBatch: (shopId: number, payloads: ProductWrite[]) => request<ProductImportResult>(shopProducts(shopId, '/products/batch'), { method: 'POST', body: JSON.stringify(payloads) }, true),
}
