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
  shopName?:string;shopNameEn?:string;description?:string;category?:string;saleType?:'OWNER'|'RESELLER';website?:string;logoPath?:string
  shopAddress?:string;shopProvince?:string;shopDistrict?:string;shopSubdistrict?:string;shopPostalCode?:string
  ownerType?:'PERSON'|'COMPANY';title?:string;firstName?:string;lastName?:string;citizenIdMasked?:string;birthDate?:string;nationality?:string;phone?:string;email?:string
  houseNo?:string;villageNo?:string;building?:string;alley?:string;road?:string;province?:string;district?:string;subdistrict?:string;postalCode?:string
  bankName?:string;bankAccountMasked?:string;bankAccountName?:string;documents:MerchantDocument[];submittedAt?:string
}
export type MerchantShopPayload = {shopName:string;shopNameEn:string;description:string;category:string;saleType:'OWNER'|'RESELLER';website:string;address:string;province:string;district:string;subdistrict:string;postalCode:string}
export type MerchantOwnerPayload = {ownerType:'PERSON'|'COMPANY';title:string;firstName:string;lastName:string;citizenId:string;birthDate:string;nationality:string;phone:string;email:string;houseNo:string;villageNo:string;building:string;alley:string;road:string;province:string;district:string;subdistrict:string;postalCode:string;bankName:string;bankAccountNumber:string;bankAccountName:string}

export const merchantApi = {
  getDraft: () => request<MerchantApplication>('/api/merchant-applications/draft', {}, true),
  saveShop: (payload:MerchantShopPayload) => request<MerchantApplication>('/api/merchant-applications/draft/shop', {method:'PUT',body:JSON.stringify(payload)}, true),
  saveOwner: (payload:MerchantOwnerPayload) => request<MerchantApplication>('/api/merchant-applications/draft/owner', {method:'PUT',body:JSON.stringify(payload)}, true),
  uploadDocument: (type:DocumentType,file:File) => {const body=new FormData();body.append('file',file);return request<MerchantApplication>(`/api/merchant-applications/draft/documents/${type}`,{method:'POST',body},true)},
  submit: () => request<MerchantApplication>('/api/merchant-applications/draft/submit',{method:'POST'},true),
}
