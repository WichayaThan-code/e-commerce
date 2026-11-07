import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { ChevronDown, ImagePlus, ImageOff } from "lucide-react";
import { type ProductImage } from "../../services/api";
import type { LocalImage } from "./ProductImagesField";
import AuthImage from "./AuthImage";

export type VariantImageValue = { imageId?: string; imageLocalKey?: string };

type Props = {
  value: string;
  images: ProductImage[];
  localImages: LocalImage[];
  disabled?: boolean;
  open: boolean;
  ariaLabel: string;
  onOpenChange: (open: boolean) => void;
  onChange: (next: VariantImageValue) => void;
  onBlur?: () => void;
};

function resolvePreview(
  value: string,
  images: ProductImage[],
  localImages: LocalImage[],
): { kind: "remote"; url: string } | { kind: "local"; url: string } | null {
  if (!value) return null;
  if (value.startsWith("local:")) {
    const preview = localImages.find((image) => image.key === value.slice(6))?.preview;
    return preview ? { kind: "local", url: preview } : null;
  }
  const url = images.find((image) => image.id === value)?.url;
  return url ? { kind: "remote", url } : null;
}

export default function VariantImagePicker({
  value,
  images,
  localImages,
  disabled = false,
  open,
  ariaLabel,
  onOpenChange,
  onChange,
  onBlur,
}: Props) {
  const { t } = useTranslation("merchant");
  const rootRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const listId = useId();
  const [menuPos, setMenuPos] = useState<{ top: number; left: number }>({ top: 0, left: 0 });
  const preview = resolvePreview(value, images, localImages);
  const empty = images.length === 0 && localImages.length === 0;

  useLayoutEffect(() => {
    if (!open || !rootRef.current) return;
    const place = () => {
      const rect = rootRef.current!.getBoundingClientRect();
      const width = Math.min(280, window.innerWidth - 16);
      const left = Math.min(Math.max(8, rect.left), window.innerWidth - width - 8);
      const below = rect.bottom + 6;
      const menuHeight = menuRef.current?.offsetHeight ?? 220;
      const top = below + menuHeight > window.innerHeight - 8
        ? Math.max(8, rect.top - menuHeight - 6)
        : below;
      setMenuPos({ top, left });
    };
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [open, images.length, localImages.length]);

  useEffect(() => {
    if (!open) return;
    const onPointer = (event: MouseEvent) => {
      const target = event.target as Node;
      if (rootRef.current?.contains(target) || menuRef.current?.contains(target)) return;
      onOpenChange(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onOpenChange(false);
    };
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open, onOpenChange]);

  const pick = (next: string) => {
    if (next.startsWith("local:")) onChange({ imageLocalKey: next.slice(6), imageId: undefined });
    else onChange({ imageId: next || undefined, imageLocalKey: undefined });
    onOpenChange(false);
    onBlur?.();
  };

  const menu = open
    ? createPortal(
        <div
          ref={menuRef}
          className="variant-image-menu"
          id={listId}
          role="listbox"
          aria-label={t("catalog.variants.pickImage")}
          style={{ top: menuPos.top, left: menuPos.left }}
        >
          <button
            type="button"
            role="option"
            aria-selected={!value}
            className={`variant-image-option none${!value ? " is-selected" : ""}`}
            onClick={() => pick("")}
          >
            <span className="variant-image-thumb" aria-hidden="true"><ImageOff /></span>
            <span>{t("catalog.variants.noImage")}</span>
          </button>
          {empty ? (
            <p className="variant-image-empty">{t("catalog.variants.imageEmptyHint")}</p>
          ) : (
            <div className="variant-image-grid">
              {images.map((image, index) => {
                const selected = value === image.id;
                return (
                  <button
                    key={image.id}
                    type="button"
                    role="option"
                    aria-selected={selected}
                    className={`variant-image-option${selected ? " is-selected" : ""}`}
                    title={t("catalog.variants.imageN", { n: index + 1 })}
                    onClick={() => pick(image.id)}
                  >
                    <span className="variant-image-thumb" aria-hidden="true">
                      <AuthImage path={image.url} alt="" />
                    </span>
                  </button>
                );
              })}
              {localImages.map((image, index) => {
                const key = `local:${image.key}`;
                const selected = value === key;
                return (
                  <button
                    key={image.key}
                    type="button"
                    role="option"
                    aria-selected={selected}
                    className={`variant-image-option${selected ? " is-selected" : ""}`}
                    title={t("catalog.variants.localImage", { n: index + 1 })}
                    onClick={() => pick(key)}
                  >
                    <span className="variant-image-thumb" aria-hidden="true">
                      <img src={image.preview} alt="" />
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>,
        document.body,
      )
    : null;

  return (
    <div className={`variant-image-picker${open ? " is-open" : ""}`} ref={rootRef}>
      <button
        type="button"
        className={`variant-image-trigger${preview ? " has-image" : ""}`}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        disabled={disabled}
        onClick={() => onOpenChange(!open)}
        onBlur={onBlur}
      >
        <span className="variant-image-thumb" aria-hidden="true">
          {preview?.kind === "remote" ? (
            <AuthImage path={preview.url} alt="" />
          ) : preview?.kind === "local" ? (
            <img src={preview.url} alt="" />
          ) : (
            <ImagePlus />
          )}
        </span>
        <ChevronDown className="variant-image-caret" aria-hidden="true" />
      </button>
      {menu}
    </div>
  );
}
