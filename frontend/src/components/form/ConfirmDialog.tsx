import { useEffect, useId, useRef, type ReactNode } from "react";
import "../../styles/product-management.css";

type ConfirmDialogProps = {
  title: string;
  message: string;
  confirmLabel: ReactNode;
  cancelLabel: string;
  danger?: boolean;
  confirmDisabled?: boolean;
  confirmTitle?: string;
  onConfirm: () => void;
  onCancel: () => void;
  children?: ReactNode;
};

export default function ConfirmDialog({
  title,
  message,
  confirmLabel,
  cancelLabel,
  danger = false,
  confirmDisabled = false,
  confirmTitle,
  onConfirm,
  onCancel,
  children,
}: ConfirmDialogProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const firstButtonRef = useRef<HTMLButtonElement>(null);
  const onCancelRef = useRef(onCancel);
  onCancelRef.current = onCancel;

  useEffect(() => {
    firstButtonRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCancelRef.current();
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
      <section>
        <h2 id={titleId}>{title}</h2>
        <p>{message}</p>
        {children}
        <div className="pm-dialog-actions">
          <button ref={firstButtonRef} type="button" className="secondary" onClick={onCancel}>{cancelLabel}</button>
          <button type="button" className={danger ? "danger" : "primary"} disabled={confirmDisabled} title={confirmTitle} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </section>
    </div>
  );
}
