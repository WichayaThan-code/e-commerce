import type { CSSProperties } from "react";

/** Non-destructive focal point + zoom stored next to theme images. Default = centre / 1×. */
export type ImageCrop = { x: number; y: number; zoom: number };

export const DEFAULT_IMAGE_CROP: ImageCrop = { x: 50, y: 50, zoom: 1 };
export const IMAGE_CROP_ZOOM_MIN = 1;
export const IMAGE_CROP_ZOOM_MAX = 3;

export function clampCrop(raw: unknown): ImageCrop | undefined {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return undefined;
  const row = raw as Record<string, unknown>;
  const x = asNumber(row.x);
  const y = asNumber(row.y);
  const zoom = asNumber(row.zoom);
  if (x == null || y == null || zoom == null) return undefined;
  return {
    x: round2(clamp(x, 0, 100)),
    y: round2(clamp(y, 0, 100)),
    zoom: round2(clamp(zoom, IMAGE_CROP_ZOOM_MIN, IMAGE_CROP_ZOOM_MAX)),
  };
}

/**
 * Inline styles + CSS variables for storefront images.
 * Pair with `.shop-image-crop` (overflow hidden) and `@media` mobile vars in shop CSS.
 * @param baseScale Multiply into zoom (e.g. GENERAL hero keeps 1.04 when no crop override).
 */
export function cropStyle(
  crop?: ImageCrop | null,
  mobileCrop?: ImageCrop | null,
  baseScale = 1,
): CSSProperties {
  const desk = crop ?? DEFAULT_IMAGE_CROP;
  const mobile = mobileCrop ?? desk;
  const deskZoom = round2(desk.zoom * baseScale);
  const mobileZoom = round2(mobile.zoom * baseScale);
  return {
    objectPosition: `${desk.x}% ${desk.y}%`,
    transform: `scale(${deskZoom})`,
    transformOrigin: `${desk.x}% ${desk.y}%`,
    ["--crop-x" as string]: `${desk.x}%`,
    ["--crop-y" as string]: `${desk.y}%`,
    ["--crop-zoom" as string]: String(deskZoom),
    ["--crop-mx" as string]: `${mobile.x}%`,
    ["--crop-my" as string]: `${mobile.y}%`,
    ["--crop-mzoom" as string]: String(mobileZoom),
  };
}

/** True when the crop differs from the default centre / 1× (or has a mobile override). */
export function hasCustomCrop(crop?: ImageCrop | null, mobileCrop?: ImageCrop | null): boolean {
  if (mobileCrop) return true;
  if (!crop) return false;
  return crop.x !== 50 || crop.y !== 50 || crop.zoom !== 1;
}

function asNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function round2(value: number) {
  return Math.round(value * 100) / 100;
}
