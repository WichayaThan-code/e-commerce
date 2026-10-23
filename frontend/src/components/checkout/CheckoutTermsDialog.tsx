import { useEffect, useId, useRef } from "react";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { TERMS_SECTIONS, TERMS_VERSION } from "./termsContent";
import "../../styles/product-management.css";

type Props = {
  onAccept: () => void;
  onClose: () => void;
};

/** Terms modal — same overlay / Esc / focus-trap pattern as ConfirmDialog; scrollable body. */
export default function CheckoutTermsDialog({ onAccept, onClose }: Props) {
  const { tp } = usePublicTranslation();
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) return;
      const focusable = [...dialogRef.current.querySelectorAll<HTMLElement>("button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])")]
        .filter((node) => !node.hasAttribute("disabled"));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  return (
    <div className="pm-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={dialogRef}>
      <section className="checkout-terms-dialog">
        <h2 id={titleId}>{tp("checkout.terms.dialogTitle")}</h2>
        <p className="checkout-terms-version">
          {tp("checkout.terms.version")} <span className="num">{TERMS_VERSION}</span>
        </p>
        <div className="checkout-terms-body">
          {TERMS_SECTIONS.map((section) => (
            <article key={section.titleKey}>
              <h3>{tp(section.titleKey)}</h3>
              <p>{tp(section.bodyKey)}</p>
            </article>
          ))}
        </div>
        <div className="pm-dialog-actions">
          <button ref={closeRef} type="button" className="secondary" onClick={onClose}>
            {tp("checkout.terms.close")}
          </button>
          <button type="button" className="primary" onClick={onAccept}>
            {tp("checkout.terms.accept")}
          </button>
        </div>
      </section>
    </div>
  );
}
