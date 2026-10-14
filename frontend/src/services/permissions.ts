import type { AuthUser, UserRole } from './api'

/**
 * Frontend view of the permission matrix.
 *
 * The backend is the only authority: every API checks permissions itself (deny by default) and the effective
 * permissions shown here come from `GET /api/auth/me` / login / refresh, which read `auth_role_permissions`,
 * `platform_roles` and `shop_members` from the database. This module only decides what to show, hide or disable.
 * Codes are the `auth_permissions.code` values from `backend/src/main/resources/db/seed/seed_reference_data.sql`.
 */
export type PermissionScope = 'platform' | 'shop'
export type RoutePermission = { permission: string; scope: PermissionScope }

/**
 * Permission needed to open a page. Pages without a dedicated code in the seed use the closest existing one
 * (marked "interim") until the generator adds PROMOTION_* / FINANCE_* / PLATFORM_CATEGORY_* codes.
 */
export const ROUTE_PERMISSIONS: Record<string, RoutePermission> = {
  '/admin': { permission: 'PLATFORM_SHOP_READ', scope: 'platform' },
  '/admin/stores': { permission: 'PLATFORM_APPLICATION_READ', scope: 'platform' },
  '/admin/store-applications': { permission: 'PLATFORM_APPLICATION_READ', scope: 'platform' },
  '/admin/categories': { permission: 'PLATFORM_SETTING_UPDATE', scope: 'platform' }, // interim
  '/admin/promotions': { permission: 'PLATFORM_SETTING_UPDATE', scope: 'platform' }, // interim
  '/admin/users': { permission: 'PLATFORM_ACCOUNT_READ', scope: 'platform' },
  '/admin/roles': { permission: 'PLATFORM_ACCOUNT_READ', scope: 'platform' }, // role catalog read (GET /api/admin/roles)
  '/admin/role-assignments': { permission: 'PLATFORM_ACCOUNT_READ', scope: 'platform' },
  '/merchant/dashboard': { permission: 'SHOP_READ', scope: 'shop' },
  '/merchant/products': { permission: 'PRODUCT_READ', scope: 'shop' },
  '/merchant/orders': { permission: 'ORDER_READ', scope: 'shop' },
  '/merchant/customers': { permission: 'CUSTOMER_READ', scope: 'shop' },
  '/merchant/promotions': { permission: 'SHOP_READ', scope: 'shop' }, // interim: no PROMOTION_* code yet
  '/merchant/reports': { permission: 'REPORT_READ', scope: 'shop' },
  '/merchant/finance': { permission: 'REPORT_READ', scope: 'shop' }, // interim: no FINANCE_* code yet
  '/merchant/shipping': { permission: 'SHIPMENT_READ', scope: 'shop' },
  '/merchant/store': { permission: 'SHOP_READ', scope: 'shop' },
  '/merchant/settings': { permission: 'SHOP_READ', scope: 'shop' },
  '/merchant/reinstatement': { permission: 'SHOP_READ', scope: 'shop' },
}

/** Merchant sidebar labels that navigate without an href (MerchantDashboardPage). */
export const MERCHANT_MENU_PATHS: Record<string, string> = {
  'หน้าหลัก': '/merchant/dashboard',
  'จัดการสินค้า': '/merchant/products',
  'คำสั่งซื้อ': '/merchant/orders',
  'ลูกค้า': '/merchant/customers',
  'โปรโมชั่น/คูปอง': '/merchant/promotions',
  'รายงาน': '/merchant/reports',
  'การเงิน': '/merchant/finance',
  'การจัดส่ง': '/merchant/shipping',
  'ร้านของฉัน': '/merchant/store',
  'ตั้งค่า': '/merchant/settings',
}

/** Shop actions without a dedicated code in the seed (interim mapping, see backend/README.md). */
export const INTERIM_PERMISSIONS = {
  ORDER_CONFIRM: 'ORDER_UPDATE',
  PROMOTION_MANAGE: 'SHOP_UPDATE',
} as const

/** Active PLATFORM_ADMIN: the backend grants every permission in every shop, including codes added later. */
export function isSuperAdmin(user: AuthUser | null | undefined): boolean {
  return !!user?.superAdmin
}

/** Account/platform level permission (BUYER_*, SHOP_APPLICATION_*, PLATFORM_*). */
export function hasPermission(user: AuthUser | null | undefined, permission: string): boolean {
  return isSuperAdmin(user) || !!user?.permissions?.includes(permission)
}

/** Store permission inside one shop; a shop the user is not an active member of grants nothing (except to a super admin). */
export function hasShopPermission(user: AuthUser | null | undefined, permission: string, shopId: number | null | undefined): boolean {
  if (shopId == null) return false
  if (isSuperAdmin(user)) return true
  return !!user?.shopPermissions?.some(shop => shop.shopId === shopId && shop.permissions.includes(permission))
}

/** May open the admin area: super admin or any PLATFORM_* permission (platform_roles; users.role no longer exists). */
export function hasPlatformAccess(user: AuthUser | null | undefined): boolean {
  if (!user) return false
  return isSuperAdmin(user) || !!user.permissions?.some(code => code.startsWith('PLATFORM_'))
}

/** May open the merchant area: an active membership with permissions in at least one shop (owner or team member). */
export function hasMerchantAccess(user: AuthUser | null | undefined): boolean {
  if (!user) return false
  // `role` is derived by the backend from the same memberships; the fallback only matters for a session cached
  // before shopPermissions existed
  return (user.shopPermissions?.length ?? 0) > 0 || user.role === 'MERCHANT'
}

/**
 * Which kind of account to present (labels, home links, redirects), derived from the effective permissions so that
 * the result matches the backend's derived `role` (platform_roles / shop_members). Falls back to `role` only when
 * the backend sent no permission fields.
 */
export function accountKind(user: AuthUser | null | undefined): UserRole {
  if (!user) return 'CUSTOMER'
  if (user.permissions === undefined && user.superAdmin === undefined) return user.role
  if (hasPlatformAccess(user)) return 'ADMIN'
  if (hasMerchantAccess(user)) return 'MERCHANT'
  return 'CUSTOMER'
}

/** Landing page after login / for "my dashboard" links. */
export function homePath(user: AuthUser | null | undefined): string {
  const kind = accountKind(user)
  return kind === 'ADMIN' ? '/admin' : kind === 'MERCHANT' ? '/merchant/dashboard' : '/'
}

export function routePermission(path: string): RoutePermission | undefined {
  const clean = path.split(/[?#]/)[0].replace(/\/+$/, '') || '/'
  return ROUTE_PERMISSIONS[clean]
}

/** Stable key used to skip re-renders when a re-sync returns the same effective permissions. */
export function permissionFingerprint(user: AuthUser | null | undefined): string {
  return JSON.stringify(user ?? null)
}
