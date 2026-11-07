import type { ImgHTMLAttributes } from "react";
import { brandLogo } from "../brand/logoAssets";

export type BrandLogoVariant = "symbol" | "withName";

type Props = Omit<ImgHTMLAttributes<HTMLImageElement>, "src" | "alt"> & {
  /** `symbol` = bags only · `withName` = bags + ThaiShopFun wordmark */
  variant?: BrandLogoVariant;
  /** Empty when the parent link already names the brand (avoids duplicate announcements). */
  alt?: string;
};

/** Shared ThaiShopFun mark — maps to assets under frontend/assets/Images via Vite import. */
export default function BrandLogo({ variant = "withName", alt = "", className, ...rest }: Props) {
  const src = variant === "symbol" ? brandLogo.symbol : brandLogo.withName;
  // CSS uses kebab suffixes (.brand-logo--with-name / --symbol); do not pass camelCase variant raw.
  const sizeClass = variant === "symbol" ? "brand-logo--symbol" : "brand-logo--with-name";
  return (
    <img
      {...rest}
      src={src}
      alt={alt}
      className={`brand-logo ${sizeClass}${className ? ` ${className}` : ""}`}
      draggable={false}
      decoding="async"
    />
  );
}
