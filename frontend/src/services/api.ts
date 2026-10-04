export type UserRole = 'CUSTOMER' | 'MERCHANT' | 'ADMIN'
export type AuthUser = { id:string; firstName:string; lastName:string; email:string; phone:string; role:UserRole }
type AuthResponse = { accessToken:string; expiresIn:number; user:AuthUser }
export type RegisterPayload = { firstName:string; lastName:string; email:string; phone:string; password:string }

const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8081').replace(/\/$/, '')
let accessToken: string | null = null
let refreshInFlight: Promise<AuthResponse> | null = null

export class ApiError extends Error {
  constructor(public code:string, message:string, public status:number) { super(message) }
}

async function request<T>(path:string, init:RequestInit = {}, authenticate = false, retry = true):Promise<T> {
  if (authenticate && !accessToken) await refreshSession()
  const headers = new Headers(init.headers)
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
    const error = await response.json().catch(() => ({code:'REQUEST_FAILED', message:'เกิดข้อผิดพลาด กรุณาลองใหม่'}))
    throw new ApiError(error.code || 'REQUEST_FAILED', error.message || 'เกิดข้อผิดพลาด กรุณาลองใหม่', response.status)
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
export type PublicShop = {
  name:string;nameEn?:string;description?:string;category:string
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

export type ProductCategory = { id:number; nameTh:string; nameEn:string; nameZh:string }

export const catalogApi = {
  categories: () => request<ProductCategory[]>('/api/categories'),
}

export const shopApi = {
  list: () => request<PublicShop[]>('/api/shops'),
  getBySlug: (slug:string) => request<PublicShop>(`/api/shops/${encodeURIComponent(slug)}`),
}
