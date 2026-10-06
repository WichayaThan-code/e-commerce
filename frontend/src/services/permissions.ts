import type { AuthUser } from './api'

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
  '/admin/roles': { permission: 'PLATFORM_ACCOUNT_READ', scope: 'platform' }, // interim
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

/** Account/platform level permission (BUYER_*, SHOP_APPLICATION_*, PLATFORM_*). */
export function hasPermission(user: AuthUser | null | undefined, permission: string): boolean {
  return !!user?.permissions?.includes(permission)
}

/** Store permission inside one shop; a shop the user is not an active member of grants nothing. */
export function hasShopPermission(user: AuthUser | null | undefined, permission: string, shopId: number | null | undefined): boolean {
  if (shopId == null) return false
  return !!user?.shopPermissions?.some(shop => shop.shopId === shopId && shop.permissions.includes(permission))
}

export function routePermission(path: string): RoutePermission | undefined {
  const clean = path.split(/[?#]/)[0].replace(/\/+$/, '') || '/'
  return ROUTE_PERMISSIONS[clean]
}

/** Stable key used to skip re-renders when a re-sync returns the same effective permissions. */
export function permissionFingerprint(user: AuthUser | null | undefined): string {
  return JSON.stringify(user ?? null)
}
