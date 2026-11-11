import { useEffect, useMemo, useRef, type ReactNode, type RefObject } from "react";
import Image from "rc-image";
import {
  ChevronLeft, ChevronRight, FlipHorizontal, FlipVertical, RotateCcw, RotateCw, X, ZoomIn, ZoomOut,
} from "lucide-react";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { mediaUrl, type PublicProduct } from "../../services/api";
import "./product-image-preview.css";

export type PreviewItem = {
  url: string;
  /** Index in product.images when this item maps to a gallery thumb; null for orphan URLs. */
  productIndex: number | null;
};

/** Product images in order, then unique SKU-linked images (by URL). */
export function buildProductPreviewItems(product: PublicProduct): PreviewItem[] {
  const seen = new Set<string>();
  const items: PreviewItem[] = [];
  const push = (rawUrl: string | null | undefined, productIndex: number | null) => {
    if (!rawUrl) return;
    const url = mediaUrl(rawUrl);
    if (!url || seen.has(url)) return;
    seen.add(url);
    items.push({ url, productIndex });
  };
  product.images.forEach((image, index) => push(image.url, index));
  product.skus.forEach((sku) => {
    if (!sku.imageId) return;
    const image = product.images.find((row) => row.id === sku.imageId);
    if (!image) return;
    const productIndex = product.images.findIndex((row) => row.id === image.id);
    push(image.url, productIndex >= 0 ? productIndex : null);
  });
  return items;
}

type ProductImagePreviewProps = {
  open: boolean;
  current: number;
  items: PreviewItem[];
  alt: string;
  onOpenChange: (open: boolean) => void;
  onCurrentChange: (index: number) => void;
  /** Focus target after close (e.g. main gallery button). */
  returnFocusRef?: RefObject<HTMLElement | null>;
};

function iconNode(node: ReactNode) {
  return (
    <span className="tsf-image-preview-icon" aria-hidden="true">
      {node}
    </span>
  );
}

export default function ProductImagePreview({
  open,
  current,
  items,
  alt,
  onOpenChange,
  onCurrentChange,
  returnFocusRef,
}: ProductImagePreviewProps) {
  const { tp } = usePublicTranslation();
  const wasOpen = useRef(false);
  const safeCurrent = items.length ? Math.min(Math.max(current, 0), items.length - 1) : 0;
  const srcList = useMemo(() => items.map((item) => ({ src: item.url, alt })), [items, alt]);

  const labels = useMemo(() => ({
    close: tp("shop.view.close"),
    prev: tp("shop.view.prevImage"),
    next: tp("shop.view.nextImage"),
    flipX: tp("shop.view.flipHorizontal"),
    flipY: tp("shop.view.flipVertical"),
    rotateLeft: tp("shop.view.rotateLeft"),
    rotateRight: tp("shop.view.rotateRight"),
    zoomOut: tp("shop.view.zoomOut"),
    zoomIn: tp("shop.view.zoomIn"),
  }), [tp]);

  const icons = useMemo(() => ({
    close: iconNode(<X aria-hidden="true" />),
    left: iconNode(<ChevronLeft aria-hidden="true" />),
    right: iconNode(<ChevronRight aria-hidden="true" />),
    flipX: iconNode(<FlipHorizontal aria-hidden="true" />),
    flipY: iconNode(<FlipVertical aria-hidden="true" />),
    rotateLeft: iconNode(<RotateCcw aria-hidden="true" />),
    rotateRight: iconNode(<RotateCw aria-hidden="true" />),
    zoomOut: iconNode(<ZoomOut aria-hidden="true" />),
    zoomIn: iconNode(<ZoomIn aria-hidden="true" />),
  }), []);

  // Lock page scroll while preview is open (rc-dialog also locks; keep as a safety net).
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);

  // Restore focus to the main gallery control after close.
  useEffect(() => {
    if (open) {
      wasOpen.current = true;
      return;
    }
    if (!wasOpen.current) return;
    wasOpen.current = false;
    const node = returnFocusRef?.current;
    if (!node) return;
    window.requestAnimationFrame(() => node.focus?.());
  }, [open, returnFocusRef]);

  // Wire aria-labels onto rc-image operation nodes (they render as div/button without labels).
  useEffect(() => {
    if (!open) return;
    const apply = () => {
      const set = (selector: string, label: string, role?: string) => {
        document.querySelectorAll(selector).forEach((node) => {
          const el = node as HTMLElement;
          el.setAttribute("aria-label", label);
          if (role) el.setAttribute("role", role);
          if (role === "button" && !el.hasAttribute("tabindex")) el.tabIndex = 0;
        });
      };
      set(".tsf-image-preview-close", labels.close);
      set(".tsf-image-preview-switch-left", labels.prev, "button");
      set(".tsf-image-preview-switch-right", labels.next, "button");
      set(".tsf-image-preview-operations-operation-flipX", labels.flipX, "button");
      set(".tsf-image-preview-operations-operation-flipY", labels.flipY, "button");
      set(".tsf-image-preview-operations-operation-rotateLeft", labels.rotateLeft, "button");
      set(".tsf-image-preview-operations-operation-rotateRight", labels.rotateRight, "button");
      set(".tsf-image-preview-operations-operation-zoomOut", labels.zoomOut, "button");
      set(".tsf-image-preview-operations-operation-zoomIn", labels.zoomIn, "button");
      const progress = document.querySelector(".tsf-image-preview-progress");
      if (progress) {
        progress.setAttribute("aria-live", "polite");
        progress.setAttribute("aria-atomic", "true");
      }
    };
    apply();
    const id = window.setTimeout(apply, 50);
    return () => window.clearTimeout(id);
  }, [open, safeCurrent, labels]);

  if (!items.length) return null;

  return (
    <Image.PreviewGroup
      previewPrefixCls="tsf-image-preview"
      icons={icons}
      items={srcList}
      preview={{
        visible: open,
        current: safeCurrent,
        movable: false,
        maskClosable: true,
        minScale: 1,
        maxScale: 50,
        scaleStep: 0.5,
        zIndex: 1100,
        countRender: (index, total) => tp("shop.view.imageIndex", { index, total }),
        onVisibleChange: (next) => onOpenChange(next),
        onChange: (next) => onCurrentChange(next),
      }}
    />
  );
}
