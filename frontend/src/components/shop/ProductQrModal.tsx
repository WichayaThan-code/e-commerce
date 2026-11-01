import { useEffect, useRef, useState } from "react";
import { QRCodeCanvas } from "qrcode.react";
import { Download, X } from "lucide-react";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import "../../styles/product-qr.css";

/** AB3: QR for the product page or a "Buy Now" link (?buy=1 → login if needed → checkout with only this item). */
export default function ProductQrModal({ productUrl, name, onClose }: { productUrl: string; name: string; onClose: () => void }) {
  const { tp } = usePublicTranslation();
  const [mode, setMode] = useState<"product" | "checkout">("product");
  const wrap = useRef<HTMLDivElement>(null);
  const url = mode === "product" ? productUrl : `${productUrl}?buy=1`;
  const local = /^(localhost|127\.|0\.0\.0\.0|\[::1\])/.test(window.location.hostname);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const download = () => {
    const canvas = wrap.current?.querySelector("canvas");
    if (!canvas) return;
    const link = document.createElement("a");
    link.href = canvas.toDataURL("image/png");
    link.download = `qr-${mode}-${(name || "product").replace(/[^\p{L}\p{N}]+/gu, "-").slice(0, 40)}.png`;
    link.click();
  };

  return (
    <div className="product-qr-backdrop" role="presentation" onClick={onClose}>
      <div className="product-qr-modal" role="dialog" aria-modal="true" aria-label={tp("shop.view.qrTitle")} onClick={(event) => event.stopPropagation()}>
        <header>
          <b>{tp("shop.view.qrTitle")}</b>
          <button type="button" aria-label={tp("shop.view.qrClose")} onClick={onClose}><X /></button>
        </header>
        <div className="product-qr-tabs" role="tablist">
          <button type="button" role="tab" aria-selected={mode === "product"} className={mode === "product" ? "active" : ""} onClick={() => setMode("product")}>{tp("shop.view.qrProduct")}</button>
          <button type="button" role="tab" aria-selected={mode === "checkout"} className={mode === "checkout" ? "active" : ""} onClick={() => setMode("checkout")}>{tp("shop.view.qrCheckout")}</button>
        </div>
        <div className="product-qr-code" ref={wrap}>
          <QRCodeCanvas value={url} size={220} marginSize={2} level="M" />
        </div>
        <p className="product-qr-hint">{mode === "product" ? tp("shop.view.qrProductHint") : tp("shop.view.qrCheckoutHint")}</p>
        {local ? <p className="product-qr-hint warn">{tp("shop.view.qrLocal")}</p> : null}
        <code className="product-qr-url">{url}</code>
        <button type="button" className="product-qr-download" onClick={download}><Download size={16} /> {tp("shop.view.qrDownload")}</button>
      </div>
    </div>
  );
}
