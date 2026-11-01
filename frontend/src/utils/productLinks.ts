import { productShareUrl } from '../services/api'
import { shopProductPath } from '../services/shopRoutes'

/** Absolute storefront product URL: /shop/:shopSlug/product/:productSlug */
export function productPageUrl(shopSlug: string, productSlug: string): string {
  return `${window.location.origin}${shopProductPath(shopSlug, productSlug)}`
}

/**
 * Link for Copy / social share: backend OG `/share/p/...` when the API is public;
 * otherwise the storefront page URL (same rule as ShopProductPage).
 */
export function productShareLink(shopSlug: string, productSlug: string): string {
  return productShareUrl(shopSlug, productSlug) ?? productPageUrl(shopSlug, productSlug)
}

/** Published + both slugs present — used to enable storefront / copy / QR actions. */
export function canOpenStorefront(
  status: string | null | undefined,
  shopSlug?: string | null,
  productSlug?: string | null,
): boolean {
  return status === 'PUBLISHED' && !!shopSlug?.trim() && !!productSlug?.trim()
}

/** Clipboard with textarea fallback for insecure contexts (non-localhost http). */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    /* fall through to execCommand */
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.left = '-9999px'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    return ok
  } catch {
    return false
  }
}
