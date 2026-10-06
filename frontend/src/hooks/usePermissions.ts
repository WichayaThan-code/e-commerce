import { useMemo } from 'react'
import { useAuth } from '../contexts/AuthContext'
import { useOptionalActiveShop } from '../contexts/ActiveShopContext'
import { hasPermission, hasShopPermission, MERCHANT_MENU_PATHS, routePermission } from '../services/permissions'

export const NO_PERMISSION_TITLE = 'คุณไม่มีสิทธิ์ทำรายการนี้'

/**
 * Permission helper for hiding/disabling UI.
 * - can(code): account/platform permission (PLATFORM_*, SHOP_APPLICATION_*, BUYER_*)
 * - canInShop(code, shopId?): store permission in the given shop (defaults to the active shop)
 * - canVisit(path): whether the page behind a route may be opened
 * - canSeeMenuItem(item): filter for the merchant sidebar tuples ([Icon, label, href?, badge?])
 * - deniedTitle(allowed): tooltip for a disabled control
 */
export function usePermissions() {
  const { user } = useAuth()
  const activeShop = useOptionalActiveShop()
  const shopId = activeShop?.shopId ?? null
  return useMemo(() => {
    const can = (permission: string) => hasPermission(user, permission)
    const canInShop = (permission: string, targetShopId: number | null = shopId) => hasShopPermission(user, permission, targetShopId)
    const canVisit = (path: string) => {
      const rule = routePermission(path)
      if (!rule) return true
      return rule.scope === 'shop' ? canInShop(rule.permission) : can(rule.permission)
    }
    const canSeeMenuItem = (item: readonly unknown[]) => {
      const href = item.find((value): value is string => typeof value === 'string' && value.startsWith('/'))
      const label = typeof item[1] === 'string' ? item[1] : ''
      const path = href ?? MERCHANT_MENU_PATHS[label]
      return path ? canVisit(path) : true
    }
    const deniedTitle = (allowed: boolean) => (allowed ? undefined : NO_PERMISSION_TITLE)
    return { user, shopId, can, canInShop, canVisit, canSeeMenuItem, deniedTitle }
  }, [user, shopId])
}
