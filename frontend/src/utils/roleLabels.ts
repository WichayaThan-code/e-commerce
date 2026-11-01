import type { AuthUser } from '../services/api'

/**
 * Thai labels from backend seed `auth_roles.name_th`
 * (backend/src/main/resources/db/seed/seed_reference_data.sql).
 * sort_order there is display order; privilege rank below matches AdminUserDirectoryService
 * (platform first, then store role with the lowest sort_order / highest shop power).
 */
export const AUTH_ROLE_LABEL_TH: Record<string, string> = {
  GUEST: 'ผู้เยี่ยมชม',
  BUYER: 'ผู้ซื้อ',
  APPLICANT: 'ผู้สมัครเปิดร้าน',
  STORE_OWNER: 'เจ้าของร้าน',
  STORE_ADMIN: 'แอดมินร้าน',
  STORE_MANAGER: 'ผู้จัดการร้าน',
  STORE_SALES: 'ฝ่ายขาย',
  STORE_WAREHOUSE: 'คลังสินค้า',
  STORE_VIEWER: 'ผู้ดูอย่างเดียว',
  PLATFORM_ADMIN: 'ผู้ดูแลแพลตฟอร์ม',
}

/** Higher = more privileged (used when the user holds several roleCodes). */
const AUTH_ROLE_RANK: Record<string, number> = {
  PLATFORM_ADMIN: 1000,
  STORE_OWNER: 900,
  STORE_ADMIN: 800,
  STORE_MANAGER: 700,
  STORE_SALES: 600,
  STORE_WAREHOUSE: 500,
  STORE_VIEWER: 400,
  APPLICANT: 300,
  BUYER: 200,
  GUEST: 100,
  // legacy AuthUser.role
  ADMIN: 1000,
  MERCHANT: 900,
  CUSTOMER: 200,
}

const FALLBACK_TH = 'ผู้ดูแลระบบ'

export function roleLabelTh(code: string | null | undefined): string {
  if (!code) return FALLBACK_TH
  const key = code.trim().toUpperCase()
  return AUTH_ROLE_LABEL_TH[key] ?? FALLBACK_TH
}

/** Single highest-privilege role label for header / greeting chips. */
export function primaryRoleLabelTh(user: AuthUser | null | undefined): string {
  if (!user) return FALLBACK_TH
  if (user.superAdmin) return AUTH_ROLE_LABEL_TH.PLATFORM_ADMIN
  const codes = [...(user.roleCodes ?? [])]
  if (user.role === 'ADMIN' && !codes.includes('PLATFORM_ADMIN')) codes.push('PLATFORM_ADMIN')
  if (user.role === 'MERCHANT' && !codes.some((c) => c.startsWith('STORE_'))) codes.push('STORE_OWNER')
  if (codes.length === 0 && user.role === 'CUSTOMER') return AUTH_ROLE_LABEL_TH.BUYER
  if (codes.length === 0) return FALLBACK_TH
  let best = codes[0]
  let bestRank = AUTH_ROLE_RANK[best] ?? 0
  for (const code of codes) {
    const rank = AUTH_ROLE_RANK[code] ?? 0
    if (rank > bestRank) {
      best = code
      bestRank = rank
    }
  }
  return roleLabelTh(best)
}
