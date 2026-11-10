import { useEffect, useId, useRef, type ReactNode } from "react";
import { ArrowRight, Check, Send, Trash2, X } from "lucide-react";
import "../../styles/product-management.css";

type ConfirmDialogProps = {
  title: string;
  message: string;
  confirmLabel: ReactNode;
  cancelLabel: string;
  danger?: boolean;
  confirmDisabled?: boolean;
  cancelDisabled?: boolean;
  confirmTitle?: string;
  className?: string;
  /** Optional icon above the title (centered layout). */
  icon?: ReactNode;
  /** Icon circle tone. Default = primary soft blue. */
  tone?: "default" | "danger" | "success";
  /** Focus the primary/confirm button instead of cancel (default false). */
  focusPrimary?: boolean;
  /** Hide cancel — single full-width confirm/close button. */
  hideCancel?: boolean;
  /** When true, Esc / backdrop click do not call onCancel (e.g. while submitting). */
  escapeDisabled?: boolean;
  /** Override icon inside the cancel button (default: X). Pass null to hide. */
  cancelIcon?: ReactNode | null;
  /** Override icon inside the confirm button. Pass null to hide. */
  confirmIcon?: ReactNode | null;
  onConfirm: () => void;
  onCancel: () => void;
  children?: ReactNode;
};

function defaultConfirmIcon(danger: boolean, tone: ConfirmDialogProps["tone"], hideCancel: boolean) {
  if (danger) return <Trash2 aria-hidden="true" />;
  if (tone === "success") return <Check aria-hidden="true" />;
  if (hideCancel) return <Check aria-hidden="true" />;
  if (tone === "danger") return <ArrowRight aria-hidden="true" />;
  return <Send aria-hidden="true" />;
}

function labelWithIcon(icon: ReactNode | null | undefined, label: ReactNode) {
  if (icon === null) return label;
  if (icon === undefined) return label;
  return (
    <>
      {icon}
      <span>{label}</span>
    </>
  );
}

export default function ConfirmDialog({
  title,
  message,
  confirmLabel,
  cancelLabel,
  danger = false,
  confirmDisabled = false,
  cancelDisabled = false,
  confirmTitle,
  className,
  icon,
  tone = "default",
  focusPrimary = false,
  hideCancel = false,
  escapeDisabled = false,
  cancelIcon,
  confirmIcon,
  onConfirm,
  onCancel,
  children,
}: ConfirmDialogProps) {
  const titleId = useId();
  const messageId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const confirmButtonRef = useRef<HTMLButtonElement>(null);
  const onCancelRef = useRef(onCancel);
  const escapeDisabledRef = useRef(escapeDisabled);
  const cancelDisabledRef = useRef(cancelDisabled);
  onCancelRef.current = onCancel;
  escapeDisabledRef.current = escapeDisabled;
  cancelDisabledRef.current = cancelDisabled;

  const canDismiss = () => !escapeDisabledRef.current && !cancelDisabledRef.current;

  const resolvedCancelIcon = cancelIcon === undefined ? <X aria-hidden="true" /> : cancelIcon;
  const resolvedConfirmIcon = confirmIcon === undefined
    ? defaultConfirmIcon(danger, tone, hideCancel)
    : confirmIcon;

  // Lock page scroll without position:fixed (that jumps the viewport).
  useEffect(() => {
    const body = document.body;
    const html = document.documentElement;
    const scrollbarGap = Math.max(0, window.innerWidth - html.clientWidth);
    const prev = {
      htmlOverflow: html.style.overflow,
      bodyOverflow: body.style.overflow,
      bodyPaddingRight: body.style.paddingRight,
    };
    html.style.overflow = "hidden";
    body.style.overflow = "hidden";
    if (scrollbarGap > 0) body.style.paddingRight = `${scrollbarGap}px`;

    const allowInsideDialog = (target: EventTarget | null) => {
      const node = target instanceof Node ? target : null;
      const panel = dialogRef.current?.querySelector("section");
      return Boolean(node && panel?.contains(node));
    };
    const blockPageScroll = (event: Event) => {
      if (allowInsideDialog(event.target)) return;
      event.preventDefault();
    };
    document.addEventListener("wheel", blockPageScroll, { passive: false });
    document.addEventListener("touchmove", blockPageScroll, { passive: false });

    return () => {
      html.style.overflow = prev.htmlOverflow;
      body.style.overflow = prev.bodyOverflow;
      body.style.paddingRight = prev.bodyPaddingRight;
      document.removeEventListener("wheel", blockPageScroll);
      document.removeEventListener("touchmove", blockPageScroll);
    };
  }, []);

  useEffect(() => {
    const target = focusPrimary || hideCancel ? confirmButtonRef.current : cancelButtonRef.current;
    target?.focus({ preventScroll: true });
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        if (canDismiss()) onCancelRef.current();
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
  }, [focusPrimary, hideCancel]);

  const rootClass = [
    "pm-dialog",
    icon ? "pm-dialog--icon" : "",
    tone !== "default" ? `pm-dialog--${tone}` : "",
    hideCancel ? "pm-dialog--single-action" : "",
    className,
  ].filter(Boolean).join(" ");

  const confirmClass = danger ? "danger" : tone === "success" ? "success" : "primary";

  return (
    <div
      className={rootClass}
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={messageId}
      ref={dialogRef}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && canDismiss()) onCancel();
      }}
    >
      <section onMouseDown={(event) => event.stopPropagation()}>
        {icon ? <div className="pm-dialog-icon" aria-hidden="true">{icon}</div> : null}
        <h2 id={titleId}>{title}</h2>
        <p id={messageId}>{message}</p>
        {children}
        <div className={`pm-dialog-actions${hideCancel ? "" : " is-equal"}`}>
          {hideCancel ? null : (
            <button
              ref={cancelButtonRef}
              type="button"
              className="secondary pm-dialog-btn-cancel"
              disabled={cancelDisabled}
              onClick={onCancel}
            >
              {labelWithIcon(resolvedCancelIcon, cancelLabel)}
            </button>
          )}
          <button
            ref={confirmButtonRef}
            type="button"
            className={`${confirmClass} pm-dialog-btn-confirm`}
            disabled={confirmDisabled}
            title={confirmTitle}
            onClick={onConfirm}
          >
            {labelWithIcon(resolvedConfirmIcon, confirmLabel)}
          </button>
        </div>
      </section>
    </div>
  );
}
