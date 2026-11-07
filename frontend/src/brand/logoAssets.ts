/**
 * ThaiShopFun brand artwork — source: frontend/assets/Images
 * (Vite imports so paths work in both dev and production builds.)
 *
 * Actual filenames in the folder (verified against artwork):
 * - thaishopfun-symbol-name.png → symbol only (bags + smile)
 * - thaishopfun-logo-name.png → horizontal logo with wordmark
 * - thaishopfun-symbol.ico → favicon source (copied to public/favicon.ico)
 * - thaishopfun-logo.ico → wordmark ICO (not used for favicon; text too small)
 */
import symbolPng from "../../assets/Images/thaishopfun-symbol-name.png";
import logoWithNamePng from "../../assets/Images/thaishopfun-logo-name.png";

export const brandLogo = {
  /** Bags + smile only */
  symbol: symbolPng,
  /** Bags + “ThaiShopFun” wordmark */
  withName: logoWithNamePng,
} as const;
